# local-ci.ps1
# This script executes the local CI pipeline for the Chimali project.
# It is intended to be run manually or triggered by a git hook.

param(
    [switch]$Clean,
    [switch]$SkipTests,
    [switch]$SkipLint,
    [switch]$NoFix,
    [switch]$NoConfigurationCache,
    [switch]$RunCoverage
)

$ErrorActionPreference = "Stop"
$StartTime = Get-Date

Write-Host "Starting Local CI Pipeline..." -ForegroundColor Cyan

$Gradle = "./gradlew"
if (-not $NoConfigurationCache) {
    $Gradle += " --configuration-cache"
}

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
    Run-Task "Clean" "$Gradle clean" $true
}

# 2. Static Analysis & Linting
if (-not $SkipLint) {
    if ($NoFix) {
        Run-Task "Ktlint Check" "$Gradle ktlintCheck"
        Run-Task "Detekt" "$Gradle detekt"
    } else {
        # Default behavior: attempt to auto-fix violations
        Run-Task "Ktlint Format" "$Gradle ktlintFormat"
        Run-Task "Detekt (Auto-fix)" "$Gradle detekt '-Pdetekt.autoCorrect=true'"
    }
    Run-Task "Lint (Release)" "$Gradle lintRelease"
}

# 3. Comprehensive Compilation Phase
# This ensures ALL modules and features compile correctly, including tests.
# We run this BEFORE unit tests to fail fast if any module is broken.
# Note: compileDebugSources covers Android, compileAndroidMain covers KMP.
Run-Task "Compile All" "$Gradle compileDebugSources compileAndroidMain compileDebugUnitTestSources compileAndroidHostTest compileDebugAndroidTestSources compileAndroidDeviceTest --continue"

# 4. Unit Tests Phase
if (-not $SkipTests) {
    Run-Task "Unit Tests" "$Gradle test"
    Run-Task "KMP Android Host Tests" "$Gradle :core:common:testAndroidHostTest :core:security:testAndroidHostTest :core:domain:testAndroidHostTest :feature:fido2:testAndroidHostTest"
}

# Coverage includes connected Android suites, so keep it opt-in for the pre-commit path.
# Run explicitly with -RunCoverage when a connected device is available.
if ($RunCoverage) {
    Run-Task "Coverage Gate (host and connected device suites)" "./gradlew --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify"
}

$TotalDuration = (Get-Date) - $StartTime
Write-Host "`nLocal CI Pipeline Completed Successfully! ($([math]::Round($TotalDuration.TotalSeconds, 2))s)" -ForegroundColor Cyan
exit 0
