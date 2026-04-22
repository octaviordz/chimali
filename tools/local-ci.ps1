# local-ci.ps1
# This script executes the local CI pipeline for the Chimali project.
# It is intended to be run manually or triggered by a git hook.

param(
    [switch]$SkipClean,
    [switch]$SkipTests,
    [switch]$SkipLint
)

$ErrorActionPreference = "Stop"
$StartTime = Get-Date

Write-Host "Starting Local CI Pipeline..." -ForegroundColor Cyan

function Run-Task($Name, $Command) {
    Write-Host "`nRunning $Name..." -ForegroundColor Yellow
    $taskStart = Get-Date
    try {
        Invoke-Expression $Command
        $duration = (Get-Date) - $taskStart
        Write-Host "PASS: $Name passed ($([math]::Round($duration.TotalSeconds, 2))s)" -ForegroundColor Green
    } catch {
        $duration = (Get-Date) - $taskStart
        Write-Host "FAIL: $Name failed ($([math]::Round($duration.TotalSeconds, 2))s)" -ForegroundColor Red
        exit 1
    }
}

# 1. Clean (Optional)
if (-not $SkipClean) {
    Run-Task "Clean" "./gradlew clean"
}

# 2. Static Analysis & Linting
if (-not $SkipLint) {
    Run-Task "Ktlint Check" "./gradlew ktlintCheck"
    Run-Task "Detekt" "./gradlew detekt"
}

# 3. Unit Tests
if (-not $SkipTests) {
    Run-Task "Unit Tests" "./gradlew test"
}

$TotalDuration = (Get-Date) - $StartTime
Write-Host "`nLocal CI Pipeline Completed Successfully! ($([math]::Round($TotalDuration.TotalSeconds, 2))s)" -ForegroundColor Cyan
exit 0
