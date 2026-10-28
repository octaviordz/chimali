[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixture = Get-Content -LiteralPath (Join-Path $root 'tools/coverage/fixtures/artifact-download-binding.json') -Raw | ConvertFrom-Json
$selected = @($fixture.cases | Where-Object id -eq $Case)
if ($selected.Count -ne 1) { throw "Unknown or duplicate artifact fixture case: $Case" }
$caseData = $selected[0]
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-artifact-binding-fixture-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
try {
    $payload = Join-Path $tempRoot 'payload'
    New-Item -ItemType Directory -Path $payload | Out-Null
    Set-Content -LiteralPath (Join-Path $payload 'manifest.json') -Value '{"run_id":"fixture"}' -Encoding utf8
    $archivePath = Join-Path $tempRoot 'artifact.zip'
    Compress-Archive -LiteralPath (Join-Path $payload 'manifest.json') -DestinationPath $archivePath
    $digest = 'sha256:' + (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $runId = '12345'; $attempt = '2'; $head = 'a' * 40; $repoId = '123456'; $artifactName = 'coverage-bundle-12345-2'
    $artifact = [PSCustomObject]@{
        id = 789; name = $artifactName; expired = $false; digest = $digest
        workflow_run = [PSCustomObject]@{ id = 12345; repository_id = 123456; head_sha = $head }
    }
    switch ($caseData.mutation) {
        'digest' { $artifact.digest = 'sha256:' + ('f' * 64) }
        'run' { $artifact.workflow_run.id = 54321 }
        'head' { $artifact.workflow_run.head_sha = 'b' * 40 }
        'repo' { $artifact.workflow_run.repository_id = 654321 }
        'expired' { $artifact.expired = $true }
        'duplicate' { $metadata = [PSCustomObject]@{ artifacts = @($artifact, $artifact.PSObject.Copy()) } }
        default { }
    }
    if (-not $metadata) { $metadata = [PSCustomObject]@{ artifacts = @($artifact) } }
    $metadataPath = Join-Path $tempRoot 'metadata.json'
    $metadata | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $metadataPath -Encoding utf8
    $outputPath = Join-Path $tempRoot 'extracted'
    $scriptPath = Join-Path $root 'tools/coverage/download-verified-artifact.ps1'
    $null = & pwsh -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
        -Repository 'octaviordz/chimali' -RunId $runId -RunAttempt $attempt -HeadSha $head `
        -RepositoryId $repoId -ArtifactName $artifactName -OutputDirectory $outputPath `
        -MetadataPath $metadataPath -ArchivePath $archivePath 2>&1
    $actualExit = $LASTEXITCODE
    $expectedExit = if ($caseData.expected_valid) { 0 } else { 1 }
    if ($actualExit -ne $expectedExit) { throw "$Case expected downloader exit $expectedExit; got $actualExit" }
    if ($caseData.expected_valid -and -not (Test-Path -LiteralPath (Join-Path $outputPath 'manifest.json') -PathType Leaf)) {
        throw "$Case did not extract the verified archive contents"
    }
    Write-Output "$Case passed (downloader exit $actualExit)."
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    if ([System.IO.Directory]::Exists($resolvedTemp)) {
        if (-not $resolvedTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-artifact-binding-fixture-*') {
            throw "Refusing unsafe artifact fixture cleanup target: $resolvedTemp"
        }
        [System.IO.Directory]::Delete($resolvedTemp, $true)
    }
}
