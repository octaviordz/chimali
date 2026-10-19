param(
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'com.chimali.fido2.test'
$runnerName = 'androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.chimali.fido2.data.crypto.CredentialProcessRestartTest'
$testId = [guid]::NewGuid().ToString()
$apkPath = Join-Path $repositoryRoot 'feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk'
$adbPrefix = @()
if ($Serial) {
    $adbPrefix += @('-s', $Serial)
}

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw "adb was not found at '$AdbPath'. Pass -AdbPath or install Android platform tools."
}

Push-Location $repositoryRoot
try {
    & .\gradlew.bat :feature:fido2:assembleAndroidDeviceTest --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not assemble the device test APK.'
    }
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "The device test APK was not created at '$apkPath'."
    }

    & $AdbPath @adbPrefix install -r $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not install the device test APK.'
    }

    function Invoke-RestartTest([string]$MethodName) {
        $arguments = $adbPrefix + @(
            'shell'
            'am'
            'instrument'
            '-w'
            '-e'
            'class'
            "$testClass#$MethodName"
            '-e'
            'restartTestId'
            $testId
            "$packageName/$runnerName"
        )
        $output = & $AdbPath @arguments 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0 -or $output -notmatch 'OK \(1 test\)') {
            throw "Instrumentation phase '$MethodName' failed:`n$output"
        }
        Write-Output $output.Trim()
    }

    Invoke-RestartTest 'prepareSyntheticCredentialForRestartRecovery'

    & $AdbPath @adbPrefix shell am force-stop $packageName
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not force-stop the instrumentation app between phases.'
    }
    $remainingPid = (& $AdbPath @adbPrefix shell pidof $packageName 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -eq 0 -and $remainingPid) {
        throw "Instrumentation process is still running after force-stop (PID $remainingPid)."
    }

    Invoke-RestartTest 'recoverAndSignAfterOperatingSystemProcessRestart'
    Write-Output 'Process-restart recovery passed using a synthetic P-256 credential.'
}
finally {
    if (Test-Path -LiteralPath $AdbPath) {
        $cleanupArgs = $adbPrefix + @(
            'shell'
            'am'
            'instrument'
            '-w'
            '-e'
            'class'
            "$testClass#cleanSyntheticRestartFixture"
            '-e'
            'restartTestId'
            $testId
            "$packageName/$runnerName"
        )
        & $AdbPath @cleanupArgs *> $null
    }
    Pop-Location
}
