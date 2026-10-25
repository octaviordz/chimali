<#
.SYNOPSIS
    Git pre-push hook for verifying proposed candidates before push.
.DESCRIPTION
    Reads stdin for ref updates (<local_ref> <local_sha> <remote_ref> <remote_sha>),
    resolves explicit integration target and online device, and invokes integration-ci.ps1.
    Fails closed with actionable guidance if prerequisites are missing.
#>
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Split-Path -Parent $ScriptDir

# 1. Read pre-push updates from standard input
$stdinLines = @($input)

if ($stdinLines.Count -eq 0) {
    # No ref updates provided (e.g. dry-run or empty push)
    exit 0
}

# 2. Resolve explicit integration target
$targetRef = (git config --get coverage.targetRef)
if ([string]::IsNullOrWhiteSpace($targetRef)) {
    $targetRef = $env:CHIMALI_INTEGRATION_TARGET
}
if ([string]::IsNullOrWhiteSpace($targetRef)) {
    # Check if origin/main exists
    git rev-parse --verify origin/main 2>$null
    if ($LASTEXITCODE -eq 0) {
        $targetRef = "origin/main"
    }
}

if ([string]::IsNullOrWhiteSpace($targetRef)) {
    Write-Error "Pre-push verification blocked: No integration target configured.`nSet via: git config coverage.targetRef <target-branch-or-sha>`nExample: git config coverage.targetRef origin/main"
    exit 1
}

# 3. Resolve device serial
$deviceSerial = (git config --get coverage.deviceSerial)
if ([string]::IsNullOrWhiteSpace($deviceSerial)) {
    $deviceSerial = $env:ANDROID_SERIAL
}
if ([string]::IsNullOrWhiteSpace($deviceSerial)) {
    # Auto-detect online adb device
    $adbDevices = @(adb devices 2>$null | Select-String -Pattern "(\S+)\s+device\b")
    if ($adbDevices.Count -gt 0) {
        $deviceSerial = $adbDevices[0].Matches.Groups[1].Value
    }
}

if ([string]::IsNullOrWhiteSpace($deviceSerial)) {
    Write-Error "Pre-push verification blocked: No online authorized Android test device found.`nConnect a device, start an emulator, or specify serial via: git config coverage.deviceSerial <serial>"
    exit 1
}

# 4. Resolve evidence root outside checkout
$evidenceRoot = (git config --get coverage.evidenceRoot)
if ([string]::IsNullOrWhiteSpace($evidenceRoot)) {
    $evidenceRoot = $env:CHIMALI_EVIDENCE_ROOT
}
if ([string]::IsNullOrWhiteSpace($evidenceRoot)) {
    $evidenceRoot = Join-Path ([System.IO.Path]::GetTempPath()) "chimali-coverage-runs"
}

if (-not (Test-Path $evidenceRoot)) {
    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
}

$integrationCiPath = Join-Path $ScriptDir "../integration-ci.ps1"
if (-not (Test-Path $integrationCiPath)) {
    Write-Error "integration-ci.ps1 not found at $integrationCiPath"
    exit 1
}

# 5. Process ref updates
foreach ($line in $stdinLines) {
    $parts = $line.Trim().Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
    if ($parts.Length -lt 4) { continue }

    $localRef = $parts[0]
    $localSha = $parts[1]
    $remoteRef = $parts[2]
    $remoteSha = $parts[3]

    # Deletion of remote ref
    if ($localSha -match '^0+$') {
        continue
    }

    # Evidence branch push (contains only archive artifacts, skip product CI)
    if ($remoteRef -like "*coverage-evidence*" -or $localRef -like "*coverage-evidence*") {
        Write-Host "Evidence branch push detected ($remoteRef); skipping integration gate." -ForegroundColor DarkGray
        continue
    }

    Write-Host "Verifying candidate $localSha for push to $remoteRef (Target: $targetRef)..." -ForegroundColor Cyan

    & pwsh -NoProfile -ExecutionPolicy Bypass -File $integrationCiPath `
        -RepositoryRoot $RepoRoot `
        -CandidateRef $localSha `
        -TargetRef $targetRef `
        -DeviceSerial $deviceSerial `
        -EvidenceRoot $evidenceRoot

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Pre-push integration check failed for candidate $localSha against target $targetRef (exit code $LASTEXITCODE)"
        exit $LASTEXITCODE
    }
}

Write-Host "Pre-push verification passed for all pushed refs." -ForegroundColor Green
exit 0
