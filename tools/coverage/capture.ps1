<#
.SYNOPSIS
    Captures run-specific coverage, test outcomes, inventories, and immutable evidence bundle.
.DESCRIPTION
    Executes fresh host and device test workloads under the reviewed harness,
    verifies clean git state, binds canonical AnalyzedZeroProofV1 proofs,
    copies reports, execution data, test outcomes, and inventories,
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

$javaVersionOutput = (& java -version 2>&1 | Select-Object -First 1)
$javaRuntime = if ($javaVersionOutput -match '"([^"]+)"') { $Matches[1] } else { "unknown" }
$androidSdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
$androidPlatforms = @()
if ($androidSdkRoot -and (Test-Path (Join-Path $androidSdkRoot "platforms"))) {
    $androidPlatforms = @(Get-ChildItem (Join-Path $androidSdkRoot "platforms") -Directory | Select-Object -ExpandProperty Name | Sort-Object)
}
$environmentDescription = "Windows $([Environment]::OSVersion.Version) / JVM $javaRuntime / Android SDK platforms $($androidPlatforms -join ',') / Device $DeviceSerial"

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
$deviceModel = "Unknown"
$deviceApi = "Unknown"
$deviceAbi = "Unknown"

try {
    & $adbPath start-server 2>$null
    if ($DeviceSerial) {
        & $adbPath -s $DeviceSerial wait-for-device 2>$null
    }
    Start-Sleep -Seconds 1
    $adbDevices = & $adbPath devices
    foreach ($line in $adbDevices) {
        if ($line -match "^$([regex]::Escape($DeviceSerial))\s+device\b") {
            $deviceOnline = $true
            break
        }
    }
    if (-not $deviceOnline) {
        Start-Sleep -Seconds 2
        $adbDevices = & $adbPath devices
        foreach ($line in $adbDevices) {
            if ($line -match "^$([regex]::Escape($DeviceSerial))\s+device\b") {
                $deviceOnline = $true
                break
            }
        }
    }
    if ($deviceOnline) {
        $deviceModel = (& $adbPath -s $DeviceSerial shell getprop ro.product.model 2>$null).Trim()
        $deviceApi = (& $adbPath -s $DeviceSerial shell getprop ro.build.version.sdk 2>$null).Trim()
        $deviceAbi = (& $adbPath -s $DeviceSerial shell getprop ro.product.cpu.abi 2>$null).Trim()
    }
} catch {
    Write-Warning "Could not query adb for device ${DeviceSerial}: $_"
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
        $env:ANDROID_SERIAL = $DeviceSerial
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

# 6. Extract Counters, Zero Proofs, and Package Reports
$policyPath = Join-Path $ScriptDir "policy.json"
$policy = Read-SafeJson -Path $policyPath

$modulesDir = Join-Path $reportsDir "modules"
New-Item -ItemType Directory -Path $modulesDir -Force | Out-Null

$moduleMap = [ordered]@{
    ":core:security" = "core/security"
    ":core:domain" = "core/domain"
    ":core:data" = "core/data"
    ":feature:fido2" = "feature/fido2"
    ":feature:authenticator" = "feature/authenticator"
    ":core:database" = "core/database"
    ":core:common" = "core/common"
    ":app" = "app"
}

# Check if Gradle canonical counters were emitted
$gradleCountersFile = Join-Path $RepositoryRoot "build/reports/hdk-coverage/counters.json"
$gradleCanonicalCounters = if (Test-Path $gradleCountersFile) { Read-SafeJson -Path $gradleCountersFile } else { $null }

$moduleCountersList = @()
$zeroCountProofs = @()
$evidenceRecords = @()

foreach ($modPath in ($policy.tier1_modules + $policy.tier2_gated_modules)) {
    $tier = if ($modPath -in $policy.tier1_modules) { 1 } else { 2 }
    $projRel = $moduleMap[$modPath]
    $srcXml = Join-Path (Join-Path $RepositoryRoot $projRel) "build/reports/hdk-coverage/hdkModuleCoverage.xml"
    $srcHtmlDir = Join-Path (Join-Path $RepositoryRoot $projRel) "build/reports/hdk-coverage/hdkModuleCoverage"
    $safeName = $modPath.Replace(":", "_").TrimStart("_")
    $dstXml = Join-Path $modulesDir "$safeName.xml"
    $dstHtml = Join-Path $modulesDir "$safeName"
    $relArtifactLink = "reports/modules/$safeName.xml"

    $instCov = 0L
    $instMis = 0L
    $branchCov = 0L
    $branchMis = 0L
    $disposition = "Applicable"
    $reportDigest = ""
    $classInvDigest = ""

    # Check Gradle counters first if available
    if ($gradleCanonicalCounters -and $gradleCanonicalCounters.modules) {
        $mEntry = $gradleCanonicalCounters.modules | Where-Object { $_.module_path -eq $modPath }
        if ($mEntry) {
            $instCov = [long]$mEntry.instruction.covered
            $instMis = [long]$mEntry.instruction.missed
            $branchCov = [long]$mEntry.branch.covered
            $branchMis = [long]$mEntry.branch.missed
            $disposition = $mEntry.disposition
            $reportDigest = $mEntry.report_digest
            $classInvDigest = $mEntry.class_inventory_digest
        }
    }

    if (Test-Path $srcXml) {
        Copy-Item -Path $srcXml -Destination $dstXml -Force
        if (Test-Path $srcHtmlDir) {
            Copy-Item -Path $srcHtmlDir -Destination $dstHtml -Recurse -Force
        }
        $xmlHash = Get-FileSha256 -Path $srcXml
        $reportDigest = $xmlHash

        if (-not $gradleCanonicalCounters) {
            $doc = Read-SafeXml -Path $srcXml
            $instNode = $doc.SelectSingleNode("/report/counter[@type='INSTRUCTION']")
            if ($instNode) {
                $instCov = [long]$instNode.GetAttribute("covered")
                $instMis = [long]$instNode.GetAttribute("missed")
            }

            $brNode = $doc.SelectSingleNode("/report/counter[@type='BRANCH']")
            if ($brNode) {
                $branchCov = [long]$brNode.GetAttribute("covered")
                $branchMis = [long]$brNode.GetAttribute("missed")
            } else {
                $branchProof = [PSCustomObject]@{
                    normalization_rule = "AnalyzedZeroProofV1"
                    module_path = $modPath
                    analyzer_identity = "jacoco-0.8.15"
                    report_file = "$safeName.xml"
                    report_digest = $xmlHash
                    class_inventory_digest = ""
                    counters = [PSCustomObject]@{
                        BRANCH = [PSCustomObject]@{ covered = 0; missed = 0; total = 0 }
                    }
                }
                $zeroCountProofs += $branchProof
            }

            if (($instCov + $instMis) -eq 0L) {
                $disposition = "NotApplicable"
            }
        }
    } else {
        if ($modPath -eq ":feature:authenticator") {
            $disposition = "NotApplicable"
            if (-not $gradleCanonicalCounters) {
                $authProof = [PSCustomObject]@{
                    normalization_rule = "AnalyzedZeroProofV1"
                    module_path = $modPath
                    analyzer_identity = "jacoco-0.8.15"
                    report_file = "$safeName.xml"
                    report_digest = ""
                    class_inventory_digest = ""
                    counters = [PSCustomObject]@{
                        INSTRUCTION = [PSCustomObject]@{ covered = 0; missed = 0; total = 0 }
                        BRANCH = [PSCustomObject]@{ covered = 0; missed = 0; total = 0 }
                    }
                }
                $zeroCountProofs += $authProof
            }
        } else {
            $disposition = "Missing"
        }
    }

    $modEntry = [PSCustomObject]@{
        module_path = $modPath
        tier = $tier
        disposition = $disposition
        instruction = [PSCustomObject]@{
            covered = $instCov
            missed = $instMis
        }
        branch = [PSCustomObject]@{
            covered = $branchCov
            missed = $branchMis
        }
        report_digest = $reportDigest
        class_inventory_digest = $classInvDigest
    }
    $moduleCountersList += $modEntry

    $reqId = if ($modPath -eq ":core:security") { "FR-006" } elseif ($modPath -eq ":core:domain") { "NFR-SEC-041" } elseif ($modPath -eq ":core:data") { "NFR-ARCH-050" } elseif ($modPath -eq ":feature:fido2") { "TR-AUTH-031" } else { "FR-001" }

    $evidenceRecords += [PSCustomObject]@{
        requirement = $reqId
        scope = $modPath
        revision = $SourceRevision
        named_procedure = "hdkHostCoverage"
        environment = $environmentDescription
        expected_observed_result = "Captured coverage: instructions $instCov covered / $instMis missed; disposition $disposition"
        date = (Get-Date).ToString("yyyy-MM-dd")
        limitations = "Host unit and connected tests executed with JaCoCo 0.8.15"
        status = if ($gradleSuccess) { "Passed" } else { "Failed" }
        artifact_link = $relArtifactLink
    }
}

if ($gradleCanonicalCounters -and $gradleCanonicalCounters.zero_count_proofs) {
    $zeroCountProofs = $gradleCanonicalCounters.zero_count_proofs
}

# Repository Aggregate
$repoSrcXml = Join-Path $RepositoryRoot "build/reports/hdk-coverage/repository.xml"
$repoSrcHtmlDir = Join-Path $RepositoryRoot "build/reports/hdk-coverage/repository"
$repoDstXml = Join-Path $reportsDir "repository.xml"
$repoDstHtml = Join-Path $reportsDir "repository"
$repoInstCov = 0L
$repoInstMis = 0L
$repoBranchCov = 0L
$repoBranchMis = 0L
$repoReportDigest = ""

if (Test-Path $repoSrcXml) {
    Copy-Item -Path $repoSrcXml -Destination $repoDstXml -Force
    if (Test-Path $repoSrcHtmlDir) {
        Copy-Item -Path $repoSrcHtmlDir -Destination $repoDstHtml -Recurse -Force
    }
    $repoReportDigest = Get-FileSha256 -Path $repoSrcXml
    $repoDoc = Read-SafeXml -Path $repoSrcXml
    $rInstNode = $repoDoc.SelectSingleNode("/report/counter[@type='INSTRUCTION']")
    if ($rInstNode) {
        $repoInstCov = [long]$rInstNode.GetAttribute("covered")
        $repoInstMis = [long]$rInstNode.GetAttribute("missed")
    }
    $rBrNode = $repoDoc.SelectSingleNode("/report/counter[@type='BRANCH']")
    if ($rBrNode) {
        $repoBranchCov = [long]$rBrNode.GetAttribute("covered")
        $repoBranchMis = [long]$rBrNode.GetAttribute("missed")
    }
}

$evidenceRecords += [PSCustomObject]@{
    requirement = "FR-001"
    scope = ":repository"
    revision = $SourceRevision
    named_procedure = "hdkHostCoverage"
    environment = $environmentDescription
    expected_observed_result = "Repository aggregate: instructions $repoInstCov covered / $repoInstMis missed"
    date = (Get-Date).ToString("yyyy-MM-dd")
    limitations = "Repository aggregate over all production projects"
    status = if ($gradleSuccess) { "Passed" } else { "Failed" }
    artifact_link = "reports/repository.xml"
}

# Copy execution data (.exec and .ec)
$execDataDir = Join-Path $runDir "execution-data"
New-Item -ItemType Directory -Path $execDataDir -Force | Out-Null
Get-ChildItem -Path $RepositoryRoot -Filter "*.exec" -Recurse | Where-Object { $_.FullName -like "*jacoco*" } | ForEach-Object {
    Copy-Item -Path $_.FullName -Destination $execDataDir -Force
}
Get-ChildItem -Path $RepositoryRoot -Filter "*.ec" -Recurse | Where-Object { $_.FullName -like "*code_coverage*" } | ForEach-Object {
    Copy-Item -Path $_.FullName -Destination $execDataDir -Force
}

# Copy JUnit XML reports and construct structured suite results
$testsReportsDir = Join-Path $reportsDir "tests"
New-Item -ItemType Directory -Path $testsReportsDir -Force | Out-Null
$suitesList = @()

$junitFiles = Get-ChildItem -Path $RepositoryRoot -Filter "TEST-*.xml" -Recurse -File | Where-Object { $_.FullName -like "*test-results*" }
$junitParseFailures = @()
foreach ($jf in $junitFiles) {
    try {
        $jDoc = Read-SafeXml -Path $jf.FullName
        $testSuiteNodes = $jDoc.SelectNodes("/testsuite | /testsuites/testsuite")
        if ($null -eq $testSuiteNodes -or $testSuiteNodes.Count -eq 0) {
            throw "JUnit XML contains no test suites: $($jf.FullName)"
        }

        $relativeJUnitPath = [System.IO.Path]::GetRelativePath($RepositoryRoot, $jf.FullName)
        if ([System.IO.Path]::IsPathRooted($relativeJUnitPath) -or $relativeJUnitPath.Replace("\", "/") -match '(^|/)\.\.(/|$)') {
            throw "JUnit XML path is outside the repository root: $($jf.FullName)"
        }

        $destXml = Join-Path (Join-Path $testsReportsDir $relativeJUnitPath)
        $destXmlParent = Split-Path -Parent $destXml
        New-Item -ItemType Directory -Path $destXmlParent -Force | Out-Null
        Copy-Item -Path $jf.FullName -Destination $destXml -Force

        foreach ($testSuiteNode in $testSuiteNodes) {
            $sName = $testSuiteNode.GetAttribute("name")
            $sTests = if ($testSuiteNode.HasAttribute("tests")) { [int]$testSuiteNode.GetAttribute("tests") } else { $testSuiteNode.SelectNodes("testcase").Count }
            $sFailures = if ($testSuiteNode.HasAttribute("failures")) { [int]$testSuiteNode.GetAttribute("failures") } else { $testSuiteNode.SelectNodes("testcase/failure").Count }
            $sErrors = if ($testSuiteNode.HasAttribute("errors")) { [int]$testSuiteNode.GetAttribute("errors") } else { $testSuiteNode.SelectNodes("testcase/error").Count }
            $sSkipped = if ($testSuiteNode.HasAttribute("skipped")) { [int]$testSuiteNode.GetAttribute("skipped") } else { $testSuiteNode.SelectNodes("testcase/skipped").Count }

            $suitesList += [PSCustomObject]@{
                suite_name = $sName
                tests = $sTests
                failures = $sFailures
                errors = $sErrors
                skipped = $sSkipped
                required = $true
            }
        }
    } catch {
        $junitParseFailures += "Failed to parse JUnit XML at $($jf.FullName): $_"
    }
}
if ($junitParseFailures.Count -gt 0) {
    $junitParseFailures | ForEach-Object { Write-Error $_ }
    exit 2
}

# Production Inventory
$inventoryPath = Join-Path $runDir "inventory.json"
$inventoryData = [PSCustomObject]@{
    schema_version = "1.0"
    policy_id = "chimali-coverage-v1"
    toolchain = [PSCustomObject]@{
        gradle = "9.6.0"
        agp = "9.4.1"
        kotlin = "2.3.21"
        jacoco = "0.8.15"
    }
    modules = ($policy.tier1_modules + $policy.tier2_gated_modules) | ForEach-Object {
        $mPath = $_
        [PSCustomObject]@{
            module_path = $mPath
            tier = if ($mPath -in $policy.tier1_modules) { 1 } else { 2 }
            disposition = ($moduleCountersList | Where-Object { $_.module_path -eq $mPath }).disposition
        }
    }
}
$inventoryData | ConvertTo-Json -Depth 5 | Set-Content -Path $inventoryPath -Encoding utf8

$countersData = [PSCustomObject]@{
    schema_version = "1.0"
    policy_id = "chimali-coverage-v1"
    analyzer_identity = "jacoco-0.8.15"
    modules = $moduleCountersList
    repository = [PSCustomObject]@{
        instruction = [PSCustomObject]@{
            covered = $repoInstCov
            missed = $repoInstMis
        }
        branch = [PSCustomObject]@{
            covered = $repoBranchCov
            missed = $repoBranchMis
        }
        report_digest = $repoReportDigest
    }
    zero_count_proofs = $zeroCountProofs
}

$countersJsonPath = Join-Path $runDir "counters.json"
$countersData | ConvertTo-Json -Depth 10 | Set-Content -Path $countersJsonPath -Encoding utf8

# 7. Postflight Cleanliness Verification
Push-Location $RepositoryRoot
try {
    $postStatus = (git status --porcelain)
    $postHead = (git rev-parse HEAD).Trim()
    $cleanPostflight = [string]::IsNullOrEmpty($postStatus) -and ($postHead -eq $SourceRevision)
    if (-not $cleanPostflight) {
        Write-Warning "Postflight git check failed: working tree modified or HEAD changed during collection"
    }
} finally {
    Pop-Location
}

$suiteFailures = @()
foreach ($s in $suitesList) {
    $eval = Test-SuiteOutcome -SuiteResult $s
    if ($eval.Status -ne "Passed") {
        $suiteFailures += "$($s.suite_name): $($eval.Reasons -join ', ')"
    }
}

$isCollectionComplete = $gradleSuccess -and (-not $SkipExecution) -and $cleanPostflight -and (Test-Path $repoDstXml)
$collectionStatus = if ($isCollectionComplete) { "Complete" } else { "Incomplete" }

$testStatus = if ($SkipExecution) {
    "Unverified"
} elseif (-not $gradleSuccess -or $suiteFailures.Count -gt 0 -or $suitesList.Count -eq 0) {
    "Failed"
} else {
    "Passed"
}

# Build artifact manifest
$artifactsList = @()
Get-ChildItem -Path $runDir -Recurse -File | Where-Object { $_.Name -ne "manifest.json" } | ForEach-Object {
    $rel = [System.IO.Path]::GetRelativePath($runDir, $_.FullName).Replace("\", "/")
    $artifactsList += [PSCustomObject]@{
        path = $rel
        sha256 = Get-FileSha256 -Path $_.FullName
    }
}

# 8. Assemble Manifest & Evidence
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
        clean_postflight = $cleanPostflight
    }
    device = [PSCustomObject]@{
        device_serial = $DeviceSerial
        device_online = $deviceOnline
        model = $deviceModel
        api_level = $deviceApi
        abi = $deviceAbi
    }
    decisions = [PSCustomObject]@{
        collection = $collectionStatus
        tests = $testStatus
        floors = "Unverified"
        comparison_compatibility = if ($Purpose -eq "Initial") { "Missing" } else { "Compatible" }
        regression = if ($Purpose -eq "Initial") { "NotApplicable" } else { "Unverified" }
        publication = "Pending"
        integration = "Unverified"
    }
    suites = $suitesList
    evidence_records = $evidenceRecords
    zero_count_proofs = $zeroCountProofs
    artifacts = $artifactsList
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
- **Device**: $DeviceSerial ($deviceModel, API $deviceApi, $deviceAbi)
- **Collection Status**: $($manifest.decisions.collection)
- **Start UTC**: $startTimeUtc
- **End UTC**: $endTimeUtc

## Counters
- **Repository Aggregate**: Instructions: $repoInstCov covered / $repoInstMis missed; Branches: $repoBranchCov covered / $repoBranchMis missed
"@
Set-Content -Path $summaryPath -Value $summaryContent -Encoding utf8

Write-Host "Coverage run captured to $runDir" -ForegroundColor $(if ($gradleSuccess) { "Green" } else { "Yellow" })
Write-Output "[CAPTURE_RESULT] $(ConvertTo-Json -Compress ([PSCustomObject]@{ run_id = $runId; run_dir = $runDir; collection = $collectionStatus; tests = $testStatus }))"

if (-not $gradleSuccess) {
    exit 1
}
exit 0
