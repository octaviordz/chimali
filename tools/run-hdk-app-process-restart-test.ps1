param(
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$targetPackage = 'com.chimali.shrunk'
$testPackage = 'com.chimali.shrunk.test'
$runnerName = 'androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.chimali.ProductionAppProcessRestartTest'
$testId = [guid]::NewGuid().ToString()
$targetApk = Join-Path $repositoryRoot 'app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk'
$testApk = Join-Path $repositoryRoot 'app/build/outputs/apk/androidTest/shrunkDebug/app-shrunkDebug-androidTest.apk'
$adbPrefix = @()
if ($Serial) {
    $adbPrefix += @('-s', $Serial)
}

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw "adb was not found at '$AdbPath'. Pass -AdbPath or install Android platform tools."
}

function Uninstall-DisposablePackage([string]$PackageName) {
    $installedPackages = & $AdbPath @adbPrefix shell pm list packages $PackageName 2>$null | Out-String
    if ($installedPackages -match "(?m)^package:$([regex]::Escape($PackageName))\s*$") {
        & $AdbPath @adbPrefix uninstall $PackageName
        if ($LASTEXITCODE -ne 0) {
            throw "Could not remove the disposable test package '$PackageName'."
        }
    }
}

function Invoke-AppProcessRestartTest([string]$MethodName) {
    $arguments = $adbPrefix + @(
        'shell'
        'am'
        'instrument'
        '-w'
        '-e'
        'class'
        "$testClass#$MethodName"
        '-e'
        'appProcessRestartTestId'
        $testId
        "$testPackage/$runnerName"
    )
    $output = & $AdbPath @arguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $output -notmatch 'OK \(1 test\)') {
        throw "Production app-process phase '$MethodName' failed:`n$output"
    }
    Write-Output $output.Trim()
}

Push-Location $repositoryRoot
try {
    & .\gradlew.bat :app:assembleShrunkDebug :app:assembleShrunkDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not assemble the disposable app and instrumentation APKs.'
    }
    if (-not (Test-Path -LiteralPath $targetApk) -or -not (Test-Path -LiteralPath $testApk)) {
        throw 'The app-process restart APK outputs were not created.'
    }

    # Only the isolated .shrunk test application and matching test APK are touched.
    Uninstall-DisposablePackage $testPackage
    Uninstall-DisposablePackage $targetPackage
    & $AdbPath @adbPrefix install -r $targetApk
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not install the disposable shrunkDebug target application.'
    }
    & $AdbPath @adbPrefix install -r $testApk
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not install the app instrumentation APK.'
    }

    Invoke-AppProcessRestartTest 'registerSyntheticCredentialInProductionAppProcess'

    & $AdbPath @adbPrefix shell am force-stop $targetPackage
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not force-stop the production target application package.'
    }
    $remainingPid = (& $AdbPath @adbPrefix shell pidof $targetPackage 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -eq 0 -and $remainingPid) {
        throw "The production app process is still running after force-stop (PID $remainingPid)."
    }

    Invoke-AppProcessRestartTest 'recoverAndSignInFreshProductionAppProcess'
    Write-Output 'Production app-process recovery passed with a synthetic P-256 credential.'
} finally {
    if (Test-Path -LiteralPath $AdbPath) {
        & $AdbPath @adbPrefix shell am force-stop $targetPackage *> $null
        Uninstall-DisposablePackage $testPackage
        Uninstall-DisposablePackage $targetPackage
    }
    Pop-Location
}
