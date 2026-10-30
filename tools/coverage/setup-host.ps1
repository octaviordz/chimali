[CmdletBinding()]
param(
    [string]$AndroidSdkRoot,
    [string]$DeviceSerial,
    [switch]$SkipDeviceCheck,
    [switch]$InstallAndroidPackages
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$profilePath = Join-Path $PSScriptRoot 'host-profile.json'
$profile = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
$coveragePolicy = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'policy.json') -Raw | ConvertFrom-Json

if ($PSVersionTable.PSVersion.Major -lt $profile.powershell.minimum_major) {
    throw "PowerShell $($profile.powershell.minimum_major)+ is required; found $($PSVersionTable.PSVersion)."
}

$daemonProperties = @{}
Get-Content -LiteralPath (Join-Path $repoRoot 'gradle\gradle-daemon-jvm.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $daemonProperties[$Matches[1]] = $Matches[2] }
}
if ($daemonProperties.toolchainVendor -ne $profile.java.gradle_vendor -or
    $daemonProperties.toolchainVersion -ne [string]$profile.java.version) {
    throw 'The checked-in Gradle daemon JVM criteria differ from tools/coverage/host-profile.json.'
}

if (-not $AndroidSdkRoot) {
    if ($env:ANDROID_HOME) { $AndroidSdkRoot = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $AndroidSdkRoot = $env:ANDROID_SDK_ROOT }
    else {
        $localProperties = Join-Path $repoRoot 'local.properties'
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($sdkLine) {
                $AndroidSdkRoot = ($sdkLine -replace '^sdk\.dir=', '' -replace '\\:', ':' -replace '\\\\', '\')
            }
        }
        if (-not $AndroidSdkRoot) { $AndroidSdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    }
}
$AndroidSdkRoot = [System.IO.Path]::GetFullPath($AndroidSdkRoot)
if (-not (Test-Path -LiteralPath $AndroidSdkRoot -PathType Container)) {
    throw "Android SDK root does not exist: $AndroidSdkRoot"
}

$sdkManager = Join-Path $AndroidSdkRoot "cmdline-tools\$($profile.android_sdk.command_line_tools)\bin\sdkmanager.bat"
if ($InstallAndroidPackages) {
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        throw "Install mode requires Android SDK Command-line Tools $($profile.android_sdk.command_line_tools): $sdkManager"
    }
    $packages = @(
        'platform-tools',
        "platforms;$($profile.android_sdk.platform)",
        "build-tools;$($profile.android_sdk.build_tools)"
    )
    & $sdkManager --sdk_root=$AndroidSdkRoot --install @packages
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager install failed with exit code $LASTEXITCODE." }
}

function Get-SdkPackageRevision([string]$PropertiesPath) {
    if (-not (Test-Path -LiteralPath $PropertiesPath)) { return $null }
    $match = Get-Content -LiteralPath $PropertiesPath | Where-Object { $_ -match '^Pkg\.Revision=' } | Select-Object -First 1
    if ($match -and $match -match '^Pkg\.Revision=(.+)$') { return $Matches[1].Trim() }
    return $null
}

$sdkPackages = @(
    @{ Name = "Command-line Tools $($profile.android_sdk.command_line_tools)"; Path = "cmdline-tools\$($profile.android_sdk.command_line_tools)\source.properties"; Revision = [string]$profile.android_sdk.command_line_tools },
    @{ Name = "Platform $($profile.android_sdk.platform)"; Path = "platforms\$($profile.android_sdk.platform)\source.properties"; Revision = [string]$profile.android_sdk.platform_revision },
    @{ Name = "Build Tools $($profile.android_sdk.build_tools)"; Path = "build-tools\$($profile.android_sdk.build_tools)\source.properties"; Revision = [string]$profile.android_sdk.build_tools },
    @{ Name = "Platform Tools $($profile.android_sdk.platform_tools)"; Path = 'platform-tools\source.properties'; Revision = [string]$profile.android_sdk.platform_tools }
)
foreach ($package in $sdkPackages) {
    $actual = Get-SdkPackageRevision (Join-Path $AndroidSdkRoot $package.Path)
    if ($actual -ne $package.Revision) {
        throw "$($package.Name) revision mismatch or missing: expected '$($package.Revision)', found '$actual'. Run with -InstallAndroidPackages where SDK licenses are accepted."
    }
}

$gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$jdkCandidates = @()
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$managedJdks = Join-Path $gradleUserHome 'jdks'
if (Test-Path -LiteralPath $managedJdks) {
    $jdkCandidates += Get-ChildItem -LiteralPath $managedJdks -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
}
$jdkHome = $null
function Find-PinnedJdk([string[]]$Candidates) {
    foreach ($candidate in ($Candidates | Select-Object -Unique)) {
        $javaExe = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaExe)) { continue }
        $properties = (& $javaExe -XshowSettings:properties -version 2>&1 | Out-String)
        if ($properties -match "java\.specification\.version\s*=\s*$([regex]::Escape([string]$profile.java.version))\b" -and
            $properties -match [regex]::Escape([string]$profile.java.runtime_vendor)) {
            return $candidate
        }
    }
    return $null
}
$jdkHome = Find-PinnedJdk $jdkCandidates
if (-not $jdkHome) {
    # Gradle can provision the pinned daemon JDK from its committed Foojay criteria,
    # provided the launcher itself has a compatible Java 17+ runtime.
    $gradleBootstrapOutput = (& (Join-Path $repoRoot 'gradlew.bat') --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle could not provision the pinned daemon JDK. Install a Java 17+ launcher runtime, then retry.`n$gradleBootstrapOutput"
    }
    if (Test-Path -LiteralPath $managedJdks) {
        $jdkCandidates += Get-ChildItem -LiteralPath $managedJdks -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    }
    $jdkHome = Find-PinnedJdk $jdkCandidates
}
if (-not $jdkHome) {
    throw "Could not find the pinned $($profile.java.runtime_vendor) Java $($profile.java.version) runtime in JAVA_HOME or GRADLE_USER_HOME/jdks ('$gradleUserHome')."
}

# Scope environment changes to this PowerShell process and its child processes.
$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $AndroidSdkRoot
$env:PATH = "$jdkHome\bin;$AndroidSdkRoot\platform-tools;$AndroidSdkRoot\cmdline-tools\$($profile.android_sdk.command_line_tools)\bin;$env:PATH"

$gradleVersionLine = Get-Content -LiteralPath (Join-Path $repoRoot 'gradle\wrapper\gradle-wrapper.properties') |
    Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1
if (-not $gradleVersionLine -or $gradleVersionLine -notmatch 'gradle-([0-9.]+)-') {
    throw 'Could not read the pinned Gradle wrapper version.'
}
$expectedGradle = $Matches[1]
$gradleOutput = (& (Join-Path $repoRoot 'gradlew.bat') --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $gradleOutput -notmatch "Gradle $([regex]::Escape($expectedGradle))\b" -or
    $gradleOutput -notmatch "Daemon JVM:\s+Compatible with Java $($profile.java.version), JetBrains,") {
    throw "Gradle wrapper/JVM verification failed. Expected Gradle $expectedGradle and $($profile.java.gradle_vendor) Java $($profile.java.version).`n$gradleOutput"
}

if (-not $SkipDeviceCheck) {
    $adb = Join-Path $AndroidSdkRoot 'platform-tools\adb.exe'
    & $adb start-server | Out-Null
    $deviceLines = @(& $adb devices -l)
    $readySerials = @($deviceLines | ForEach-Object {
        if ($_ -match '^([^\s]+)\s+device(?:\s|$)') { $Matches[1] }
    })
    $minimumApi = [Math]::Max([int]$profile.device.minimum_api, [int]$coveragePolicy.device_requirements.minimum_api_level)
    $approvedAbis = @($coveragePolicy.device_requirements.approved_abis)
    if (-not $DeviceSerial) {
        $capableSerials = @()
        foreach ($serial in $readySerials) {
            $apiText = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
            $abi = (& $adb -s $serial shell getprop ro.product.cpu.abi).Trim()
            if ($apiText -match '^\d+$' -and [int]$apiText -ge $minimumApi -and $abi -in $approvedAbis) {
                $capableSerials += $serial
            }
        }
        if ($capableSerials.Count -eq 0) {
            throw "No online authorized device meets API $minimumApi and ABI $($approvedAbis -join ', '). Attach one or pass -DeviceSerial explicitly. adb devices reported:`n$($deviceLines -join "`n")"
        }
        if ($capableSerials.Count -gt 1) {
            throw "Multiple devices meet API $minimumApi and ABI requirements ($($capableSerials -join ', ')); pass -DeviceSerial explicitly."
        }
        $DeviceSerial = $capableSerials[0]
    }
    if ($DeviceSerial -notin $readySerials) {
        throw "Android device '$DeviceSerial' is not online and authorized. adb devices reported:`n$($deviceLines -join "`n")"
    }
    $deviceApi = (& $adb -s $DeviceSerial shell getprop ro.build.version.sdk).Trim()
    $deviceAbi = (& $adb -s $DeviceSerial shell getprop ro.product.cpu.abi).Trim()
    if ([int]$deviceApi -lt $minimumApi) {
        throw "Android device API $deviceApi is below required API $minimumApi."
    }
    if ($deviceAbi -notin $approvedAbis) {
        throw "Android device ABI '$deviceAbi' is not approved; expected one of $($approvedAbis -join ', ')."
    }
}

Write-Host "Host environment configured for this PowerShell process:" -ForegroundColor Green
Write-Host "  JAVA_HOME=$env:JAVA_HOME"
Write-Host "  Java=$(((& java -version 2>&1 | Select-Object -First 1) -join '').Trim())"
Write-Host "  Gradle=$expectedGradle"
Write-Host "  Android SDK=$AndroidSdkRoot"
Write-Host "  Android platform=$($profile.android_sdk.platform); build-tools=$($profile.android_sdk.build_tools); platform-tools=$($profile.android_sdk.platform_tools)"
if (-not $SkipDeviceCheck) { Write-Host "  Device=$DeviceSerial (API $deviceApi)" }
Write-Host 'Environment changes apply to this PowerShell process and child processes only.'
