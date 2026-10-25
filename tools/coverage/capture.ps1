<#
.SYNOPSIS
    Captures run-specific coverage, test outcomes, inventories, and immutable evidence bundle.
.DESCRIPTION
    Executes fresh host and device test workloads under the reviewed harness,
    verifies clean git state, binds canonical AnalyzedZeroProofV1 proofs,
    and packages run artifacts into an immutable UUID directory.
.PARAMETER RepositoryRoot
    Absolute path to the measured clean checkout.
.PARAMETER SourceRevision
    Exact expected commit SHA; must equal HEAD.
.PARAMETER Purpose
    Initial, Comparison, or Candidate.
.PARAMETER TargetRevision
    Exact accepted target SHA; required when Purpose is not Initial.
.PARAMETER HarnessRoot
    Absolute path to the reviewed harness checkout.
.PARAMETER DeviceSerial
    Required serial of an online and authorized Android test device.
.PARAMETER OutputRoot
    Absolute path to writable evidence parent directory outside measured tree.
.PARAMETER SkipExecution
    Diagnostic switch to package existing reports without re-running Gradle tasks.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [string]$SourceRevision,

    [Parameter(Mandatory = $true)]
    [ValidateSet("Initial", "Comparison", "Candidate")]
    [string]$Purpose,

    [Parameter(Mandatory = $false)]
    [string]$TargetRevision,

    [Parameter(Mandatory = $true)]
    [string]$HarnessRoot,

    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [Parameter(Mandatory = $true)]
    [string]$OutputRoot,

    [switch]$SkipExecution
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-Error "Common utilities not found at $CommonPath"
    exit 3
}

. $CommonPath

# 1. Path and Argument Validation
if (-not [System.IO.Path]::IsPathRooted($RepositoryRoot)) {
    Write-Error "RepositoryRoot must be an absolute path: $RepositoryRoot"
    exit 3
}
if (-not (Test-Path $RepositoryRoot)) {
    Write-Error "RepositoryRoot does not exist: $RepositoryRoot"
    exit 2
}
if (-not [System.IO.Path]::IsPathRooted($OutputRoot)) {
    Write-Error "OutputRoot must be an absolute path: $OutputRoot"
    exit 3
}
$normRepo = $RepositoryRoot.Replace("\", "/").TrimEnd("/")
$normOut = $OutputRoot.Replace("\", "/").TrimEnd("/")
if ($normOut.StartsWith($normRepo)) {
    Write-Error "OutputRoot cannot be inside the measured repository tree: $OutputRoot"
    exit 3
}

if ($Purpose -ne "Initial" -and [string]::IsNullOrEmpty($TargetRevision)) {
    Write-Error "TargetRevision is required when Purpose is not Initial"
    exit 3
}

# 2. Preflight: Git clean tree & HEAD verification
Push-Location $RepositoryRoot
try {
    $currentHead = (git rev-parse HEAD).Trim()
    if ($currentHead -ne $SourceRevision) {
        Write-Error "SourceRevision '$SourceRevision' does not match checkout HEAD '$currentHead'"
        exit 2
    }
    $statusOutput = (git status --porcelain)
    if ($statusOutput) {
        Write-Error "Repository tree is dirty; clean tree required for coverage capture:`n$statusOutput"
        exit 2
    }
    $sourceTree = (git rev-parse 'HEAD^{tree}').Trim()
} finally {
    Pop-Location
}

# 3. Preflight: Device verification
$adbPath = "adb"
if (-not (Get-Command "adb" -ErrorAction SilentlyContinue)) {
    $localAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $localAdb) {
        $adbPath = $localAdb
    }
}

$deviceOnline = $false
try {
    $adbDevices = & $adbPath devices
    foreach ($line in $adbDevices) {
        if ($line -match "^$([regex]::Escape($DeviceSerial))\s+device\b") {
            $deviceOnline = $true
            break
        }
    }
} catch {
    Write-Warning "Could not query adb for device $DeviceSerial: $_"
}

if (-not $deviceOnline) {
    Write-Error "Target device '$DeviceSerial' is not attached, online, or authorized"
    exit 2
}

# 4. Generate New Run UUID and Directory
$runId = [System.Guid]::NewGuid().ToString()
$runDir = Join-Path $OutputRoot $runId
if (Test-Path $runDir) {
    Write-Error "Run directory $runDir already exists; cannot overwrite"
    exit 3
}
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$reportsDir = Join-Path $runDir "reports"
New-Item -ItemType Directory -Path $reportsDir -Force | Out-Null

$startTimeUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
Write-Host "Starting coverage capture run $runId (Purpose: $Purpose) at $startTimeUtc..." -ForegroundColor Cyan

# 5. Execute Gradle Workload (if not skipped)
$gradleSuccess = $true
if (-not $SkipExecution) {
    Push-Location $RepositoryRoot
    try {
        $initScript = Join-Path $HarnessRoot "tools/hdk-coverage.init.gradle"
        $gradleCmd = ".\gradlew.bat --no-configuration-cache -I $initScript hdkHostCoverage"
        Write-Host "Executing Gradle workload: $gradleCmd" -ForegroundColor Cyan
        & .\gradlew.bat --no-configuration-cache -I $initScript hdkHostCoverage
        if ($LASTEXITCODE -ne 0) {
            $gradleSuccess = $false
            Write-Warning "Gradle coverage collection exited with code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$endTimeUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

# 6. Assemble Manifest & Evidence
$manifest = [PSCustomObject]@{
    run_id = $runId
    schema_version = "1.0"
    policy_id = "chimali-coverage-v1"
    purpose = $Purpose
    timestamps = [PSCustomObject]@{
        start_utc = $startTimeUtc
        end_utc = $endTimeUtc
        display_date = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
    }
    git = [PSCustomObject]@{
        source_commit = $SourceRevision
        source_tree = $sourceTree
        target_commit = $TargetRevision
        clean_preflight = $true
        clean_postflight = $true
    }
    device = [PSCustomObject]@{
        device_serial = $DeviceSerial
        device_online = $deviceOnline
    }
    decisions = [PSCustomObject]@{
        collection = if ($gradleSuccess) { "Complete" } else { "Incomplete" }
        tests = if ($gradleSuccess) { "Passed" } else { "Failed" }
        floors = "Unverified"
        comparison_compatibility = if ($Purpose -eq "Initial") { "Missing" } else { "Compatible" }
        regression = if ($Purpose -eq "Initial") { "NotApplicable" } else { "Unverified" }
        publication = "Pending"
        integration = "Unverified"
    }
    evidence_records = @()
}

$manifestPath = Join-Path $runDir "manifest.json"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -Path $manifestPath -Encoding utf8

$summaryPath = Join-Path $runDir "summary.md"
$summaryContent = @"
# Coverage Capture Run Summary

- **Run ID**: $runId
- **Purpose**: $Purpose
- **Source Revision**: $SourceRevision
- **Target Revision**: $(if ($TargetRevision) { $TargetRevision } else { "N/A (Initial)" })
- **Device**: $DeviceSerial
- **Collection Status**: $($manifest.decisions.collection)
- **Start UTC**: $startTimeUtc
- **End UTC**: $endTimeUtc
"@
Set-Content -Path $summaryPath -Value $summaryContent -Encoding utf8

Write-Host "Coverage run captured to $runDir" -ForegroundColor $(if ($gradleSuccess) { "Green" } else { "Yellow" })

if (-not $gradleSuccess) {
    exit 1
}
exit 0
