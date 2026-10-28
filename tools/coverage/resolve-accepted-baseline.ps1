[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$CandidateBundle,
    [Parameter(Mandatory = $true)][string]$ExpectedTargetCommit
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'coverage-common.ps1')

try {
    foreach ($entry in @(@{ Value = $EvidenceRoot; Name = 'EvidenceRoot' }, @{ Value = $CandidateBundle; Name = 'CandidateBundle' })) {
        if (-not [System.IO.Path]::IsPathRooted($entry.Value)) { throw "$($entry.Name) must be an absolute path" }
    }
    if ($ExpectedTargetCommit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') { throw 'ExpectedTargetCommit must be a full commit SHA' }
    $root = [System.IO.Path]::GetFullPath($EvidenceRoot).TrimEnd('\', '/')
    $candidateRoot = [System.IO.Path]::GetFullPath($CandidateBundle).TrimEnd('\', '/')
    $indexPath = Join-Path (Join-Path $root 'baselines') 'current.json'
    if (-not (Test-Path -LiteralPath $indexPath -PathType Leaf)) { throw 'Accepted baseline index is missing from the trusted evidence checkout' }
    $index = Read-SafeJson -Path $indexPath
    $relativeBundle = [string]$index.bundle_path
    if ([string]::IsNullOrWhiteSpace($relativeBundle) -or [System.IO.Path]::IsPathRooted($relativeBundle) -or $relativeBundle -match '(^|[/\\])\.\.([/\\]|$)') {
        throw 'Accepted baseline bundle_path is not a contained relative path'
    }
    $baselineRoot = [System.IO.Path]::GetFullPath((Join-Path $root $relativeBundle))
    $rootPrefix = $root + [System.IO.Path]::DirectorySeparatorChar
    if (-not $baselineRoot.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Accepted baseline bundle escapes the trusted evidence checkout' }
    $baselineManifestPath = Join-Path $baselineRoot 'manifest.json'
    $candidateManifestPath = Join-Path $candidateRoot 'manifest.json'
    if (-not (Test-Path -LiteralPath $baselineManifestPath -PathType Leaf) -or -not (Test-Path -LiteralPath $candidateManifestPath -PathType Leaf)) {
        throw 'Candidate or accepted baseline manifest is missing'
    }
    $baselineManifest = Read-SafeJson -Path $baselineManifestPath
    $candidateManifest = Read-SafeJson -Path $candidateManifestPath
    if ([string]$index.run_id -cne [string]$baselineManifest.run_id) { throw 'Accepted baseline index run_id does not match its bundle manifest' }
    if ([string]$baselineManifest.git.source_commit -cne $ExpectedTargetCommit) { throw 'Accepted baseline source commit does not match the exact candidate target' }
    if ([string]$candidateManifest.purpose -cne 'Candidate' -or [string]$candidateManifest.git.target_commit -cne $ExpectedTargetCommit) {
        throw 'Candidate bundle is not bound to the exact expected target commit'
    }
    $bundleDigest = Get-CoverageBundleDigest -BundlePath $baselineRoot
    $indexValidation = Test-AcceptedBaselineIndex -Index $index -ExpectedTargetCommit $ExpectedTargetCommit -ActualBundleDigest $bundleDigest
    if (-not $indexValidation.IsValid) { throw ($indexValidation.Errors -join '; ') }

    $changedArtifacts = @($candidateManifest.artifacts | Where-Object { [string]$_.path -ceq 'changed-paths.txt' })
    if ($changedArtifacts.Count -ne 1) { throw 'Candidate bundle must bind exactly one changed-paths.txt artifact' }
    $changedPathsPath = Join-Path $candidateRoot 'changed-paths.txt'
    if (-not (Test-Path -LiteralPath $changedPathsPath -PathType Leaf) -or
        [string]$changedArtifacts[0].sha256 -cne (Get-FileSha256 -Path $changedPathsPath)) {
        throw 'Candidate changed-paths.txt is missing or does not match its sealed artifact digest'
    }
    [PSCustomObject]@{
        comparison_bundle = $baselineRoot
        accepted_baseline_index = $indexPath
        changed_paths = $changedPathsPath
        target_commit = $ExpectedTargetCommit
        run_id = [string]$index.run_id
        bundle_digest = $bundleDigest
    } | ConvertTo-Json -Depth 6 -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Accepted baseline resolution failed: $_")
    exit 2
}
