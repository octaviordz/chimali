[CmdletBinding()]
param(
    [string]$Serial,
    [string]$RunId = [guid]::NewGuid().ToString()
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$featureRoot = Join-Path $repositoryRoot 'specs/060-fido2-latency-measurement'
$artifactDirectory = Join-Path $featureRoot 'artifacts'
$artifactPath = Join-Path $artifactDirectory "android-measurement-$RunId.json"
$testClass = 'com.chimali.fido2.performance.AndroidSimulatedLatencyAssessmentTest'
$testPackage = 'com.chimali.fido2.test'
$targetPackage = 'com.chimali.fido2'
$helperModule = Join-Path $PSScriptRoot 'Fido2AndroidLatency.Helpers.psm1'
Import-Module $helperModule -Force
$mutex = [System.Threading.Mutex]::new($false, 'Local\ChimaliFeature060AndroidLatency')
$ownsMutex = $false

function Get-SourceFingerprint {
    $head = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($head)) {
        throw 'Could not resolve the repository revision for source provenance.'
    }
    $roots = @(
        'feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain',
        'feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto',
        'feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2',
        'feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth',
        'feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport',
        'feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/BaselineMeasurements.kt',
        'feature/fido2/src/androidSharedTest/kotlin/com/chimali/fido2/performance',
        'feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/performance',
        'feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/performance',
        'feature/fido2/build.gradle.kts',
        'gradle/libs.versions.toml',
        'specs/060-fido2-latency-measurement/spec.md',
        'specs/060-fido2-latency-measurement/contracts/measurement-contract.md'
    )
    $files = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $roots) {
        $path = Join-Path $repositoryRoot $entry
        if (Test-Path -LiteralPath $path -PathType Container) {
            Get-ChildItem -LiteralPath $path -File -Recurse | ForEach-Object { $files.Add($_.FullName) }
        } elseif (Test-Path -LiteralPath $path -PathType Leaf) {
            $files.Add((Resolve-Path -LiteralPath $path).Path)
        }
    }
    $paths = @($files | Sort-Object -Unique { [System.IO.Path]::GetRelativePath($repositoryRoot, $_).Replace('\', '/') })
    [Array]::Sort($paths, [System.StringComparer]::Ordinal)
    $hash = [System.Security.Cryptography.IncrementalHash]::CreateHash([System.Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        foreach ($path in $paths) {
            $relative = [System.IO.Path]::GetRelativePath($repositoryRoot, $path).Replace('\', '/')
            $hash.AppendData([System.Text.Encoding]::UTF8.GetBytes($relative))
            $hash.AppendData([byte[]]@(0))
            $hash.AppendData([System.IO.File]::ReadAllBytes($path))
            $hash.AppendData([byte[]]@(0))
        }
        $digest = [Convert]::ToHexString($hash.GetHashAndReset()).ToLowerInvariant()
    } finally {
        $hash.Dispose()
    }
    return "$head+worktree-source-sha256:$digest"
}

function Get-OnlineDevices {
    $rows = & adb devices
    if ($LASTEXITCODE -ne 0) { throw 'adb devices failed.' }
    @($rows | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' } | ForEach-Object { ($_ -split '\s+')[0] })
}

function Save-IncompleteArtifact([string]$Reason, [string]$Fingerprint, [string]$Run) {
    New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
    $partial = $null
    try {
        $partial = (& adb -s $Serial exec-out run-as $testPackage cat "files/fido2-latency-$Run.json" 2>$null | Out-String).Trim()
    } catch { $partial = $null }
    $partialReport = $null
    $collected = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'feature/fido2/build/outputs') `
        -Filter "fido2-latency-$Run.json" -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($collected) {
        try { $partialReport = Get-Content -Raw -LiteralPath $collected.FullName | ConvertFrom-Json -AsHashtable } catch { $partialReport = $null }
    }
    if ($partial) {
        try { if (-not $partialReport) { $partialReport = $partial | ConvertFrom-Json -AsHashtable } } catch { $partialReport = $null }
    }
    $document = New-Feature060IncompleteCollectorReport -RunId $Run -Reason $Reason `
        -SourceFingerprint $Fingerprint -PartialDeviceReport $partialReport
    $document | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $artifactPath -Encoding utf8
}

try {
    try {
        $ownsMutex = $mutex.WaitOne(0)
    } catch [System.Threading.AbandonedMutexException] {
        $ownsMutex = $true
    }
    if (-not $ownsMutex) { throw 'Another Feature 060 Android assessment is already running.' }

    $online = Get-OnlineDevices
    if ($Serial) {
        if ($Serial -notin $online) { throw 'The selected Android device is not connected and authorized.' }
    } elseif ($online.Count -eq 1) {
        $Serial = $online[0]
    } elseif ($online.Count -eq 0) {
        throw 'No authorized Android device is connected.'
    } else {
        throw 'More than one Android device is connected; pass -Serial to select exactly one.'
    }

    $fingerprint = Get-SourceFingerprint
    New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
    if (Test-Path -LiteralPath $artifactPath) { throw "Run artifact already exists: $artifactPath" }
    & (Join-Path $repositoryRoot 'gradlew.bat') ':feature:fido2:connectedAndroidDeviceTest' `
        "-Pandroid.testInstrumentationRunnerArguments.class=$testClass" `
        "-Pandroid.testInstrumentationRunnerArguments.sourceFingerprint=$fingerprint" `
        "-Pandroid.testInstrumentationRunnerArguments.runId=$RunId" `
        "-Pandroid.injected.device.serial=$Serial" '--no-configuration-cache'
    if ($LASTEXITCODE -ne 0) { throw "Android instrumentation task failed with exit code $LASTEXITCODE." }

    $collectedReport = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'feature/fido2/build/outputs') `
        -Filter "fido2-latency-$RunId.json" -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $collectedReport) { throw 'AndroidX test storage did not extract the device report.' }
    $report = Get-Content -Raw -LiteralPath $collectedReport.FullName | ConvertFrom-Json -AsHashtable
    if ($report.run_id -ne $RunId) { throw 'The extracted report run ID does not match the requested run.' }
    if ($report.assessment.workload.source_revision -ne $fingerprint) { throw 'The device report source fingerprint does not match this checkout.' }
    if ($report.test_package -ne $testPackage) { throw 'The device report test-package identity does not match the expected Feature 060 package.' }
    $testApk = Join-Path $repositoryRoot 'feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk'
    if (-not (Test-Path -LiteralPath $testApk)) { throw 'The built Android instrumentation package could not be found.' }
    $apkHash = (Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $identity = Test-Feature060DeviceReportIdentity -Report $report -RunId $RunId `
        -SourceFingerprint $fingerprint -TestPackage $testPackage -InstalledApkSha256 $apkHash
    if (-not $identity.Valid) { throw 'The report run, source fingerprint, test-package identity or built APK digest did not match.' }
    $report.collector = [ordered]@{
        source_fingerprint_verified = $true
        test_package_verified = $true
        test_apk_digest_verified = $true
        collected_at_utc = [DateTime]::UtcNow.ToString('O')
        limitations = @('Android device serial is intentionally not retained.')
    }
    $report | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $artifactPath -Encoding utf8
    Write-Host "Android assessment: $($report.disposition)"
    Write-Host "Run: $RunId"
    Write-Host "Report: $artifactPath"
    if ($report.disposition -eq 'FAIL') { Write-Warning 'The device performance assessment failed its diagnostic threshold; collection completed successfully.' }
} catch {
    $reason = $_.Exception.Message
    if (-not $fingerprint) { $fingerprint = 'unavailable' }
    try { Save-IncompleteArtifact $reason $fingerprint $RunId } catch { Write-Error "Could not retain the incomplete collector record: $($_.Exception.Message)" }
    throw
} finally {
    if ($ownsMutex) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
