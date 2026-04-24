# local-ci.ps1
# This script executes the local CI pipeline for the Chimali project.
# It is intended to be run manually or triggered by a git hook.

param(
    [switch]$Clean,
    [switch]$SkipTests,
    [switch]$SkipLint
)

$ErrorActionPreference = "Stop"
$StartTime = Get-Date

Write-Host "Starting Local CI Pipeline..." -ForegroundColor Cyan

function Run-Task($Name, $Command, [bool]$IgnoreFailure = $false) {
    Write-Host "`nRunning $Name..." -ForegroundColor Yellow
    $taskStart = Get-Date
    Invoke-Expression $Command
    $exitCode = $LASTEXITCODE
    $duration = (Get-Date) - $taskStart
    if ($exitCode -ne 0) {
        if ($IgnoreFailure) {
            Write-Host "WARNING: $Name failed ($([math]::Round($duration.TotalSeconds, 2))s) but continuing..." -ForegroundColor Magenta
            return
        }
        Write-Host "FAIL: $Name failed ($([math]::Round($duration.TotalSeconds, 2))s)" -ForegroundColor Red
        exit 1
    }
    Write-Host "PASS: $Name passed ($([math]::Round($duration.TotalSeconds, 2))s)" -ForegroundColor Green
}

# 1. Clean (Optional, Opt-in)
if ($Clean) {
    # We ignore failures in Clean because file locks on Windows (from Android Studio) 
    # are common and shouldn't block the rest of the CI checks.
    Run-Task "Clean" "./gradlew clean" $true
}

# 2. Static Analysis & Linting
if (-not $SkipLint) {
    Run-Task "Ktlint Check" "./gradlew ktlintCheck"
    Run-Task "Detekt" "./gradlew detekt"
}

# 3. Compilation & Unit Tests
if (-not $SkipTests) {
    # Comprehensive compilation check (Production + Unit Tests + Instrumented Tests)
    # This catches errors across all module types (KMP and standard Android)
    Run-Task "Compile All" "./gradlew compileDebugSources compileAndroidMain compileDebugUnitTestSources compileAndroidHostTestSources compileDebugAndroidTestSources compileAndroidDeviceTestSources --continue"
    
    Run-Task "Unit Tests" "./gradlew test"
}

$TotalDuration = (Get-Date) - $StartTime
Write-Host "`nLocal CI Pipeline Completed Successfully! ($([math]::Round($TotalDuration.TotalSeconds, 2))s)" -ForegroundColor Cyan
exit 0
