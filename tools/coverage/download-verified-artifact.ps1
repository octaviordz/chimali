[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$RunId,
    [Parameter(Mandatory = $true)][string]$RunAttempt,
    [Parameter(Mandatory = $true)][string]$HeadSha,
    [Parameter(Mandatory = $true)][string]$RepositoryId,
    [Parameter(Mandatory = $true)][string]$ArtifactName,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$ServerUrl = 'https://github.com',
    [string]$Token,
    [string]$MetadataPath,
    [string]$ArchivePath
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'coverage-common.ps1')
if ($Repository -notmatch '^[^/]+/[^/]+$' -or $RunId -notmatch '^[0-9]+$' -or
    $RunAttempt -notmatch '^[1-9][0-9]*$' -or $HeadSha -notmatch '^[0-9a-fA-F]{40,64}$' -or
    $RepositoryId -notmatch '^[0-9]+$') { throw 'Artifact binding requires exact repository, run, attempt, head, and repository identities' }

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-artifact-download-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
try {
    if ($MetadataPath) {
        $metadata = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json -ErrorAction Stop
        $artifact = @($metadata.artifacts | Where-Object name -eq $ArtifactName)
        if ($artifact.Count -ne 1) { throw "Expected exactly one artifact named '$ArtifactName'; found $($artifact.Count)" }
        $artifact = $artifact[0]
    } else {
        if ([string]::IsNullOrWhiteSpace($Token)) { throw 'Actions read token is required to resolve run artifacts' }
        $apiRoot = if ($ServerUrl -eq 'https://github.com') { 'https://api.github.com' } else { "$($ServerUrl.TrimEnd('/'))/api/v3" }
        $headers = @{ Authorization = "Bearer $Token"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
        $metadata = Invoke-RestMethod -Uri "$apiRoot/repos/$Repository/actions/runs/$RunId/artifacts?per_page=100" -Headers $headers -Method Get
        $matches = @($metadata.artifacts | Where-Object name -eq $ArtifactName)
        if ($matches.Count -ne 1) { throw "Expected exactly one artifact named '$ArtifactName' for run $RunId; found $($matches.Count)" }
        $artifact = $matches[0]
    }
    if ($artifact.expired -ne $false -or [string]$artifact.digest -notmatch '^sha256:[0-9a-fA-F]{64}$' -or
        [string]$artifact.id -notmatch '^[0-9]+$' -or
        [string]$artifact.workflow_run.id -cne $RunId -or
        [string]$artifact.workflow_run.head_sha -cne $HeadSha -or
        [string]$artifact.workflow_run.repository_id -cne $RepositoryId -or
        [string]$artifact.name -cne $ArtifactName) {
        throw 'Artifact metadata is expired or is not bound to the exact workflow run/repository/head'
    }

    $zipPath = Join-Path $tempRoot 'artifact.zip'
    if ($ArchivePath) {
        Copy-Item -LiteralPath $ArchivePath -Destination $zipPath
    } else {
        $downloadUri = "$apiRoot/repos/$Repository/actions/artifacts/$($artifact.id)/zip"
        $response = Invoke-WebRequest -Uri $downloadUri -Headers @{ Authorization = "Bearer $Token"; Accept = 'application/vnd.github+json' } `
            -MaximumRedirection 0 -SkipHttpErrorCheck
        if ([int]$response.StatusCode -notin @(301, 302, 303, 307, 308) -or -not $response.Headers.Location) {
            throw "Artifact archive endpoint did not return a signed download redirect (HTTP $($response.StatusCode))"
        }
        Invoke-WebRequest -Uri ([uri]$response.Headers.Location) -OutFile $zipPath
    }
    $actualDigest = 'sha256:' + (Get-FileSha256 -Path $zipPath)
    if ($actualDigest -cne [string]$artifact.digest) { throw 'Downloaded artifact archive digest does not match GitHub artifact metadata' }

    if (Test-Path -LiteralPath $OutputDirectory) { throw "Refusing to extract into an existing artifact directory: $OutputDirectory" }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    Expand-Archive -LiteralPath $zipPath -DestinationPath $OutputDirectory -Force
    $manifests = @(Get-ChildItem -LiteralPath $OutputDirectory -Filter manifest.json -File -Recurse)
    if ($manifests.Count -ne 1) { throw "Verified archive must contain exactly one manifest.json; found $($manifests.Count)" }
    [PSCustomObject]@{ artifact_id = [string]$artifact.id; artifact_name = $ArtifactName; digest = $actualDigest; bundle_path = $manifests[0].Directory.FullName } |
        ConvertTo-Json -Depth 5 -Compress
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    if ([System.IO.Directory]::Exists($resolvedTemp)) {
        if (-not $resolvedTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-artifact-download-*') {
            throw "Refusing unsafe artifact temp cleanup target: $resolvedTemp"
        }
        [System.IO.Directory]::Delete($resolvedTemp, $true)
    }
}
