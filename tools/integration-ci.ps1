<#
.SYNOPSIS
    Mandatory integration orchestration script for coverage and quality verification.
.DESCRIPTION
    Enforces the integration contract: validates target ancestry and freshness,
    runs non-mutating local checks, captures candidate coverage, verifies thresholds
    and regression against the accepted target, and packages evidence.
.PARAMETER RepositoryRoot
    Absolute path to candidate checkout. Defaults to repository root.
.PARAMETER CandidateRef
    Proposed candidate ref or SHA. Defaults to HEAD.
.PARAMETER TargetRef
    Required integration target ref or SHA.
.PARAMETER HarnessRoot
    Required trusted harness root. Defaults to current repository root.
.PARAMETER DeviceSerial
    Required serial of an online and authorized Android test device.
.PARAMETER EvidenceRoot
    Required evidence directory outside the measured checkout.
.PARAMETER BaselineBundle
    Optional archived baseline bundle directory for comparison.
.PARAMETER RemoteName
    Git remote whose live target ref must match TargetRef. Defaults to origin.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),

    [Parameter(Mandatory = $false)]
    [string]$CandidateRef = "HEAD",

    [Parameter(Mandatory = $true)]
    [string]$TargetRef,

    [Parameter(Mandatory = $false)]
    [string]$HarnessRoot = (Split-Path -Parent $PSScriptRoot),

    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,

    [Parameter(Mandatory = $false)]
    [string]$BaselineBundle,

    [Parameter(Mandatory = $false)]
    [string]$RemoteName = 'origin'
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$CoverageDir = Join-Path $ScriptDir "coverage"
$CommonPath = Join-Path $CoverageDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-Error "coverage-common.ps1 not found at $CommonPath"
    exit 3
}

. $CommonPath

# 1. Path & Argument Validations
if (-not [System.IO.Path]::IsPathRooted($RepositoryRoot)) {
    $RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
}
if (-not [System.IO.Path]::IsPathRooted($HarnessRoot)) {
    $HarnessRoot = [System.IO.Path]::GetFullPath($HarnessRoot)
}
if (-not [System.IO.Path]::IsPathRooted($EvidenceRoot)) {
    $EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
}

$normRepo = $RepositoryRoot.Replace("\", "/").TrimEnd("/")
$normEvidence = $EvidenceRoot.Replace("\", "/").TrimEnd("/")
if ($normEvidence.StartsWith($normRepo)) {
    Write-Error "EvidenceRoot cannot be located inside RepositoryRoot: $EvidenceRoot"
    exit 3
}

# 2. Resolve Candidate & Target SHAs and Ancestry
Push-Location $RepositoryRoot
try {
    $candidateSha = (git rev-parse $CandidateRef).Trim()
    $targetSha = (git rev-parse $TargetRef).Trim()

    $remoteTargetRef = ConvertTo-RemoteTargetRef -TargetRef $TargetRef -RemoteName $RemoteName
    if (-not $remoteTargetRef.IsValid) { throw $remoteTargetRef.Error }
    $targetRemoteRef = $remoteTargetRef.Ref
    $remoteTargetOutput = @(git ls-remote $RemoteName $targetRemoteRef 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Unable to resolve live remote target '$targetRemoteRef': $($remoteTargetOutput -join ' ')" }
    $remoteTargetSha = if ($remoteTargetOutput.Count -gt 0) { (($remoteTargetOutput[0] -split '\s+')[0]).Trim() } else { '' }
    if (-not $remoteTargetSha -or $remoteTargetSha -ne $targetSha) {
        throw "TargetRef '$TargetRef' is stale or missing locally (local=$targetSha, remote=$remoteTargetSha); fetch and retry against the live target."
    }

    # Verify target is ancestor of candidate
    git merge-base --is-ancestor $targetSha $candidateSha
    if ($LASTEXITCODE -ne 0) {
        Write-Error "TargetRef '$TargetRef' ($targetSha) is not an ancestor of CandidateRef '$CandidateRef' ($candidateSha)"
        exit 1
    }
} catch {
    Write-Error "Git error during ref resolution: $_"
    exit 2
} finally {
    Pop-Location
}

Write-Host "Integration verification started:`n  Candidate: $candidateSha`n  Target: $targetSha`n  Device: $DeviceSerial`n  Evidence: $EvidenceRoot" -ForegroundColor Cyan

# 3. Non-mutating Local Checks
Write-Host "`n[Step 1/3] Running non-mutating local checks (-NoFix)..." -ForegroundColor Cyan
$localCiPath = Join-Path $ScriptDir "local-ci.ps1"
if (Test-Path $localCiPath) {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $localCiPath -NoFix
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Local CI pre-checks failed with exit code $LASTEXITCODE"
        exit 1
    }
}

# 4. Canonical Coverage Capture
Write-Host "`n[Step 2/3] Capturing candidate coverage..." -ForegroundColor Cyan
$capturePath = Join-Path $CoverageDir "capture.ps1"
$captureOutput = & pwsh -NoProfile -ExecutionPolicy Bypass -File $capturePath `
    -RepositoryRoot $RepositoryRoot `
    -SourceRevision $candidateSha `
    -Purpose "Candidate" `
    -TargetRevision $targetSha `
    -HarnessRoot $HarnessRoot `
    -DeviceSerial $DeviceSerial `
    -OutputRoot $EvidenceRoot

$captureExit = $LASTEXITCODE
if ($captureExit -ne 0) {
    Write-Error "Coverage capture failed with exit code $captureExit"
    exit $captureExit
}

foreach ($line in $captureOutput) {
    Write-Host $line
}

try {
    $capturedJson = ConvertFrom-CaptureResultOutput -OutputLines $captureOutput
} catch {
    Write-Error "Failed to parse [CAPTURE_RESULT] structured output from capture.ps1: $_"
    exit 2
}

$candidateBundlePath = $capturedJson.run_dir
if (-not (Test-Path $candidateBundlePath)) {
    Write-Error "Candidate run bundle not found: $candidateBundlePath"
    exit 2
}

# Validate bundle manifest identities against candidate and target
$manifest = Read-SafeJson -Path (Join-Path $candidateBundlePath "manifest.json")
$identity = Test-CandidateManifestIdentity -Manifest $manifest -ExpectedSourceCommit $candidateSha -ExpectedTargetCommit $targetSha
if (-not $identity.IsValid) {
    Write-Error "Run bundle identity validation failed: $($identity.Errors -join '; ')"
    exit 2
}

# 5. Deterministic Verification
Write-Host "`n[Step 3/3] Verifying candidate decisions..." -ForegroundColor Cyan
$verifyPath = Join-Path $CoverageDir "verify.ps1"
$policyPath = Join-Path $CoverageDir "policy.json"
$assessmentsDir = Join-Path (Join-Path $EvidenceRoot "assessments") $manifest.run_id

$effectiveBaseline = $BaselineBundle
$localCurrent = Join-Path (Join-Path $EvidenceRoot "baselines") "current.json"
if (-not (Test-Path $localCurrent)) {
    Write-Error "No accepted baseline index exists at '$localCurrent'; publish or measure the exact target before comparison."
    exit 2
}
$curr = Read-SafeJson -Path $localCurrent
$indexValidation = Test-AcceptedBaselineIndex -Index $curr -ExpectedTargetCommit $targetSha
if (-not $indexValidation.IsValid) {
    Write-Error "Accepted baseline index validation failed: $($indexValidation.Errors -join '; ')"
    exit 2
}
if ($curr.bundle_path -match '(^|[/\\])\.\.([/\\]|$)' -or [System.IO.Path]::IsPathRooted([string]$curr.bundle_path)) {
    Write-Error "Accepted baseline index bundle_path must be a relative path contained under EvidenceRoot."
    exit 2
}
$indexedBaseline = [System.IO.Path]::GetFullPath((Join-Path $EvidenceRoot $curr.bundle_path))
$canonicalEvidence = [System.IO.Path]::GetFullPath($EvidenceRoot).TrimEnd('\', '/')
if (-not $indexedBaseline.StartsWith($canonicalEvidence + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Error "Accepted baseline bundle resolves outside EvidenceRoot."
    exit 2
}
if ($effectiveBaseline -and [System.IO.Path]::GetFullPath($effectiveBaseline) -ne $indexedBaseline) {
    Write-Error "Caller-supplied BaselineBundle does not match the independently indexed accepted baseline."
    exit 2
}
$effectiveBaseline = $indexedBaseline
if (-not (Test-Path (Join-Path $effectiveBaseline 'manifest.json'))) {
    Write-Error "Indexed accepted baseline bundle is missing: $effectiveBaseline"
    exit 2
}
$actualBaselineDigest = Get-CoverageBundleDigest -BundlePath $effectiveBaseline
$indexValidation = Test-AcceptedBaselineIndex -Index $curr -ExpectedTargetCommit $targetSha -ActualBundleDigest $actualBaselineDigest
if (-not $indexValidation.IsValid) {
    Write-Error "Accepted baseline index validation failed: $($indexValidation.Errors -join '; '); restore the indexed immutable bundle or measure the target again."
    exit 2
}
$baselineManifest = Read-SafeJson -Path (Join-Path $effectiveBaseline 'manifest.json')
$baselineIdentity = Test-TrustedComparisonManifest -CandidateManifest $manifest -BaselineManifest $baselineManifest
if (-not $baselineIdentity.IsValid -or $baselineManifest.run_id -ne $curr.run_id) {
    Write-Error "Indexed baseline failed provenance/identity validation: $($baselineIdentity.Errors -join '; ')"
    exit 2
}

$changedPathsFile = Join-Path $assessmentsDir 'changed-paths.txt'
New-Item -ItemType Directory -Path $assessmentsDir -Force | Out-Null
Push-Location $RepositoryRoot
try {
    $changedPaths = @(git diff --name-only $targetSha $candidateSha)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to compute target-to-candidate changed paths.' }
    $changedPaths | Set-Content -LiteralPath $changedPathsFile -Encoding utf8
} finally { Pop-Location }

$verifyArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $verifyPath,
    "-RunBundle", $candidateBundlePath,
    "-PolicyPath", $policyPath,
    "-OutputDirectory", $assessmentsDir
)
if ($effectiveBaseline) {
    $verifyArgs += @("-ComparisonBundle", $effectiveBaseline)
}
$verifyArgs += @("-ChangedPathsFile", $changedPathsFile)

& pwsh @verifyArgs
$verifyCode = $LASTEXITCODE

# 6. Revalidate Git Identities Immediately Before Completion
Push-Location $RepositoryRoot
try {
    $postCandidateSha = (git rev-parse $CandidateRef).Trim()
    $postTargetSha = (git rev-parse $TargetRef).Trim()
    $postRemoteOutput = @(git ls-remote $RemoteName $targetRemoteRef 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Unable to revalidate live remote target '$targetRemoteRef'." }
    $postRemoteSha = if ($postRemoteOutput.Count -gt 0) { (($postRemoteOutput[0] -split '\s+')[0]).Trim() } else { '' }
    if ($postCandidateSha -ne $candidateSha -or $postTargetSha -ne $targetSha -or $postRemoteSha -ne $targetSha) {
        Write-Error "Git head or target advanced during integration verification! Candidate: $postCandidateSha (was $candidateSha), Target: $postTargetSha (was $targetSha)"
        exit 1
    }
} finally {
    Pop-Location
}

if ($verifyCode -eq 0) {
    Write-Host "`nIntegration CI verification succeeded locally. Note: Remote hosted check and publication required for merge approval." -ForegroundColor Green
    exit 0
} else {
    Write-Error "Integration CI verification failed with exit code $verifyCode"
    exit $verifyCode
}
