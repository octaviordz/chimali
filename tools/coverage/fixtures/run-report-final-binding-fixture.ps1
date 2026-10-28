[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $RepositoryRoot 'tools/coverage/coverage-common.ps1')

$manifestDigest = 'a' * 64
$bundleDigest = 'b' * 64
$candidate = @{ IsValid = $true; Errors = @() }
$reporter = @{ IsValid = $true; Errors = @() }
$harness = @{ IsValid = $true; Errors = @() }
$receipt = @{ manifest_digest = $manifestDigest; bundle_digest = $bundleDigest }
$currentManifestDigest = $manifestDigest
$currentBundleDigest = $bundleDigest

switch ($Case) {
    'REPORT_FINAL_001_ALL_BINDINGS_STABLE' { }
    'REPORT_FINAL_002_MANIFEST_CHANGED' { $currentManifestDigest = 'c' * 64 }
    'REPORT_FINAL_003_BUNDLE_CHANGED' { $currentBundleDigest = 'd' * 64 }
    'REPORT_FINAL_004_CANDIDATE_IDENTITY_CHANGED' { $candidate = @{ IsValid = $false; Errors = @('candidate binding changed') } }
    'REPORT_FINAL_005_REPORTER_IDENTITY_CHANGED' { $reporter = @{ IsValid = $false; Errors = @('reporter binding changed') } }
    'REPORT_FINAL_006_HARNESS_CHANGED' { $harness = @{ IsValid = $false; Errors = @('trusted harness changed') } }
    'REPORT_FINAL_007_PUBLISHED_DIGEST_CHANGED' { $receipt.bundle_digest = 'e' * 64 }
    default { throw "Unknown final report binding fixture case: $Case" }
}

$result = Test-ReportFinalBinding `
    -VerifiedManifestDigest $manifestDigest `
    -CurrentManifestDigest $currentManifestDigest `
    -VerifiedBundleDigest $bundleDigest `
    -CurrentBundleDigest $currentBundleDigest `
    -CandidateIdentity $candidate `
    -ReporterBinding $reporter `
    -ManifestValidation $harness `
    -PublicationReceipt $receipt

$expectedValid = $Case -eq 'REPORT_FINAL_001_ALL_BINDINGS_STABLE'
if ($result.IsValid -ne $expectedValid) {
    throw "Unexpected final binding result: valid=$($result.IsValid) errors=$($result.Errors -join '; ')"
}
if (-not $expectedValid -and $result.Errors.Count -eq 0) {
    throw 'A mutated final success binding was accepted without an error'
}
Write-Output "$Case passed"
