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
    [string]$BaselineBundle
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
& pwsh -NoProfile -ExecutionPolicy Bypass -File $capturePath `
    -RepositoryRoot $RepositoryRoot `
    -SourceRevision $candidateSha `
    -Purpose "Candidate" `
    -TargetRevision $targetSha `
    -HarnessRoot $HarnessRoot `
    -DeviceSerial $DeviceSerial `
    -OutputRoot $EvidenceRoot

if ($LASTEXITCODE -ne 0) {
    Write-Error "Coverage capture failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# Find newly created run bundle
$candidateBundle = Get-ChildItem -Path $EvidenceRoot -Directory | Sort-Object CreationTime -Descending | Select-Object -First 1
if (-not $candidateBundle) {
    Write-Error "Candidate run bundle not found in $EvidenceRoot"
    exit 2
}

# 5. Deterministic Verification
Write-Host "`n[Step 3/3] Verifying candidate decisions..." -ForegroundColor Cyan
$verifyPath = Join-Path $CoverageDir "verify.ps1"
$policyPath = Join-Path $CoverageDir "policy.json"

$verifyArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $verifyPath,
    "-RunBundle", $candidateBundle.FullName,
    "-PolicyPath", $policyPath
)
if ($BaselineBundle) {
    $verifyArgs += @("-ComparisonBundle", $BaselineBundle)
}

& pwsh @verifyArgs
$verifyCode = $LASTEXITCODE

if ($verifyCode -eq 0) {
    Write-Host "`nIntegration CI verification succeeded locally. Note: Remote hosted check and publication required for merge approval." -ForegroundColor Green
    exit 0
} else {
    Write-Error "Integration CI verification failed with exit code $verifyCode"
    exit $verifyCode
}
