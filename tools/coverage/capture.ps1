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

    [Parameter(Mandatory = $false)]
    [string]$TrustedReporterWorkflowSha,

    [Parameter(Mandatory = $false)]
    [string]$TrustedReporterSha,

    [Parameter(Mandatory = $false)]
    [string]$TrustedRepositoryId,

    [Parameter(Mandatory = $false)]
    [string]$TrustedCoverageAppId,

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

$trustedReporterValues = @($TrustedReporterWorkflowSha, $TrustedReporterSha, $TrustedRepositoryId, $TrustedCoverageAppId)
if (@($trustedReporterValues | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
    if ($TrustedReporterWorkflowSha -notmatch '^[0-9a-fA-F]{40}$' -or
        $TrustedReporterSha -notmatch '^[0-9a-fA-F]{40}$' -or
        $TrustedRepositoryId -notmatch '^[0-9]+$' -or
        $TrustedCoverageAppId -notmatch '^[0-9]+$') {
        Write-Error 'Trusted reporter binding requires full reporter/harness SHAs and numeric repository/App IDs.'
        exit 3
    }
}

$policyPath = Join-Path $ScriptDir "policy.json"
try {
    $policy = Read-SafeJson -Path $policyPath
    $policyValidation = Test-PolicyConfiguration -Policy $policy
    if (-not $policyValidation.IsValid) {
        [Console]::Error.WriteLine("Invalid coverage policy: $($policyValidation.Reasons -join '; ')")
        exit 3
    }
} catch {
    [Console]::Error.WriteLine("Failed to load or validate coverage policy: $_")
    exit 3
}

if (-not [System.IO.Path]::IsPathRooted($HarnessRoot) -or -not (Test-Path -LiteralPath $HarnessRoot)) {
    Write-Error "HarnessRoot must be an existing absolute path: $HarnessRoot"
    exit 2
}
$harnessScriptPaths = @($policy.harness_requirements.required_files | ForEach-Object {
    Join-Path $HarnessRoot ([string]$_).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
})
foreach ($harnessScriptPath in $harnessScriptPaths) {
    if (-not (Test-Path -LiteralPath $harnessScriptPath -PathType Leaf)) {
        Write-Error "Harness checkout is missing required file: $harnessScriptPath"
        exit 2
    }
}
$harnessRevision = (& git -C $HarnessRoot rev-parse HEAD 2>$null).Trim()
$harnessTree = (& git -C $HarnessRoot rev-parse 'HEAD^{tree}' 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or -not $harnessRevision -or -not $harnessTree) {
    Write-Error "HarnessRoot is not a readable Git checkout: $HarnessRoot"
    exit 2
}
$harnessClean = [string]::IsNullOrEmpty((& git -C $HarnessRoot status --porcelain))
$harnessFileHashes = [ordered]@{}
foreach ($harnessScriptPath in $harnessScriptPaths) {
    $relativeHarnessPath = [System.IO.Path]::GetRelativePath($HarnessRoot, $harnessScriptPath).Replace('\', '/')
    $harnessFileHashes[$relativeHarnessPath] = Get-FileSha256 -Path $harnessScriptPath
}

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

function Invoke-BoundedAdb {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $adbPath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $Arguments) { $null = $startInfo.ArgumentList.Add($argument) }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw 'Could not start adb preflight process' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try { $process.Kill($true) } catch { }
            throw "adb preflight command exceeded ${TimeoutSeconds}s: adb $($Arguments -join ' ')"
        }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "adb preflight command failed ($($process.ExitCode)): $($stderr.GetAwaiter().GetResult())"
        }
        return @($stdout.GetAwaiter().GetResult() -split '\r?\n' | Where-Object { $_.Length -gt 0 })
    } finally {
        $process.Dispose()
    }
}

try {
    $null = Invoke-BoundedAdb -Arguments @('start-server') -TimeoutSeconds 10
    for ($attempt = 0; $attempt -lt 10 -and -not $deviceOnline; $attempt++) {
        $adbDevices = Invoke-BoundedAdb -Arguments @('devices') -TimeoutSeconds 5
        foreach ($line in $adbDevices) {
            if ($line -match "^$([regex]::Escape($DeviceSerial))\s+device\b") {
                $deviceOnline = $true
                break
            }
        }
        if (-not $deviceOnline -and $attempt -lt 9) { Start-Sleep -Seconds 1 }
    }
    if ($deviceOnline) {
        $deviceModel = (Invoke-BoundedAdb -Arguments @('-s', $DeviceSerial, 'shell', 'getprop', 'ro.product.model') -TimeoutSeconds 5).Trim()
        $deviceApi = (Invoke-BoundedAdb -Arguments @('-s', $DeviceSerial, 'shell', 'getprop', 'ro.build.version.sdk') -TimeoutSeconds 5).Trim()
        $deviceAbi = (Invoke-BoundedAdb -Arguments @('-s', $DeviceSerial, 'shell', 'getprop', 'ro.product.cpu.abi') -TimeoutSeconds 5).Trim()
    }
} catch {
    Write-Warning "Could not query adb for device ${DeviceSerial}: $_"
}

if (-not $deviceOnline) {
    Write-Error "Target device '$DeviceSerial' is not attached, online, or authorized"
    exit 2
}
$deviceCapabilities = Test-DeviceCapabilities -Policy $policy -ApiLevel $deviceApi -Abi $deviceAbi
if (-not $deviceCapabilities.IsValid) {
    Write-Error "Target device '$DeviceSerial' does not meet coverage capability requirements: $($deviceCapabilities.Errors -join '; ')"
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

$changedPathsFile = $null
if (-not [string]::IsNullOrWhiteSpace($TargetRevision)) {
    $changedPathsFile = Join-Path $runDir 'changed-paths.txt'
    Push-Location $RepositoryRoot
    try {
        $changedPaths = @(git diff --name-only $TargetRevision $SourceRevision)
        if ($LASTEXITCODE -ne 0) { Write-Error 'Could not derive changed paths from the exact target and source commits'; exit 2 }
        $changedPathsContent = [string]::Join([Environment]::NewLine, $changedPaths)
        [System.IO.File]::WriteAllText($changedPathsFile, $changedPathsContent, [System.Text.UTF8Encoding]::new($false))
    } finally { Pop-Location }
}

# 5. Execute Gradle Workload (if not skipped)
$gradleSuccess = $true
if (-not $SkipExecution) {
    Push-Location $RepositoryRoot
    try {
        $env:ANDROID_SERIAL = $DeviceSerial
        $initScript = Join-Path $HarnessRoot "tools/hdk-coverage.init.gradle"
        $gradleArgs = @('--no-configuration-cache', '-I', $initScript)
        $gradleArgs += @('--project-prop', "coverageHarnessRoot=$HarnessRoot")
        if ($changedPathsFile) { $gradleArgs += @('--project-prop', "coverageChangedPathsFile=$changedPathsFile") }
        $gradleArgs += @('hdkHostCoverage', 'hdkCoverageAnalyze')
        Write-Host "Executing Gradle workload with the pinned coverage harness and exact changed-path set..." -ForegroundColor Cyan
        & .\gradlew.bat @gradleArgs
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
$modulesDir = Join-Path $reportsDir "modules"
New-Item -ItemType Directory -Path $modulesDir -Force | Out-Null

# Check if Gradle canonical counters were emitted
$gradleCountersFile = Join-Path $RepositoryRoot "build/reports/hdk-coverage/counters.json"
$gradleCanonicalCounters = if (Test-Path $gradleCountersFile) { Read-SafeJson -Path $gradleCountersFile } else { $null }
$gradleInventoryFile = Join-Path $RepositoryRoot "build/reports/hdk-coverage/production-inventory.json"
$gradleProductionInventory = if (Test-Path $gradleInventoryFile) { Read-SafeJson -Path $gradleInventoryFile } else { $null }
$modulePathsToCapture = @($policy.tier1_modules + $policy.tier2_gated_modules)
if ($gradleCanonicalCounters -and $gradleCanonicalCounters.modules) {
    $modulePathsToCapture += @($gradleCanonicalCounters.modules | ForEach-Object { [string]$_.module_path })
}
$modulePathsToCapture = @($modulePathsToCapture | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique | Sort-Object -CaseSensitive)
$canonicalEvidenceErrors = @()
$canonicalEvidenceValid = ($null -ne $gradleCanonicalCounters) -and ($null -ne $gradleProductionInventory)
if (-not $gradleCanonicalCounters) { $canonicalEvidenceErrors += "Gradle canonical counters are missing: $gradleCountersFile" }
if (-not $gradleProductionInventory) { $canonicalEvidenceErrors += "Gradle production inventory is missing: $gradleInventoryFile" }
if ($gradleCanonicalCounters) {
    $workloadEvidence = $gradleCanonicalCounters.workload
    foreach ($requiredDeviceModule in $policy.required_workload.required_device_test_modules) {
        $records = @($workloadEvidence.required_device_tasks | Where-Object { $_.module_path -eq $requiredDeviceModule })
        if ($records.Count -ne 1 -or -not $records[0].executed -or $records[0].skipped -or $records[0].failed) {
            $canonicalEvidenceErrors += "Required device workload is missing or did not pass for $requiredDeviceModule"
        }
    }
    if (-not $workloadEvidence.host_test_tasks -or @($workloadEvidence.host_test_tasks).Count -eq 0 -or
        @($workloadEvidence.host_test_tasks | Where-Object {
            $_.failed -or ($_.disposition -eq 'Applicable' -and (-not $_.executed -or $_.skipped)) -or
            ($_.disposition -eq 'NotApplicable' -and $_.skip_message -ne 'NO-SOURCE')
        }).Count -gt 0) {
        $canonicalEvidenceErrors += 'Host test workload evidence is missing or contains a task that did not complete successfully'
    }
}
if ($gradleCanonicalCounters -and $gradleProductionInventory) {
    $inventoryFileDigest = Get-FileSha256 -Path $gradleInventoryFile
    if ($gradleCanonicalCounters.inventory_digest -ne $inventoryFileDigest) {
        $canonicalEvidenceErrors += "Canonical counters inventory_digest does not match production-inventory.json"
    }
    $counterAccounting = Test-ComparisonModuleAccounting -Modules @($gradleCanonicalCounters.modules) -RequiredModulePaths $modulePathsToCapture -Label 'Captured production scope'
    $canonicalEvidenceErrors += $counterAccounting.Errors
    foreach ($requiredModule in $modulePathsToCapture) {
        $counterModule = $gradleCanonicalCounters.modules | Where-Object { $_.module_path -eq $requiredModule } | Select-Object -First 1
        $inventoryModule = $gradleProductionInventory.modules | Where-Object { $_.module_path -eq $requiredModule } | Select-Object -First 1
        if (-not $counterModule -or -not $inventoryModule) {
            $canonicalEvidenceErrors += "Canonical counters or retained production inventory missing module $requiredModule"
            continue
        }
        if ($counterModule.class_inventory_digest -ne $inventoryModule.class_inventory_digest) {
            $canonicalEvidenceErrors += "Class inventory digest mismatch for $requiredModule"
        }
        $projectDirectory = [string]$inventoryModule.project_directory
        if ([string]::IsNullOrWhiteSpace($projectDirectory) -or [System.IO.Path]::IsPathRooted($projectDirectory) -or $projectDirectory.Replace('\', '/') -match '(^|/)\.\.(/|$)') {
            $canonicalEvidenceErrors += "Production inventory has no safe Gradle project directory for $requiredModule"
        }
    }
    foreach ($proof in @($gradleCanonicalCounters.zero_count_proofs)) {
        $proofCounterModule = $gradleCanonicalCounters.modules | Where-Object { $_.module_path -eq $proof.module_path } | Select-Object -First 1
        $proofInventoryModule = $gradleProductionInventory.modules | Where-Object { $_.module_path -eq $proof.module_path } | Select-Object -First 1
        if ($proof.analyzer_identity -ne $gradleCanonicalCounters.analyzer_identity -or
            $proof.inventory_digest -ne $inventoryFileDigest -or
            -not $proofCounterModule -or -not $proofInventoryModule -or
            $proof.class_inventory_digest -ne $proofInventoryModule.class_inventory_digest -or
            $proof.report_digest -ne $proofCounterModule.report_digest) {
            $canonicalEvidenceErrors += "Zero-count proof for $($proof.module_path) is not bound to its analyzer, report, and production inventory"
        }
    }
}

$moduleCountersList = @()
$zeroCountProofs = @()
$evidenceRecords = @()

foreach ($modPath in $modulePathsToCapture) {
    $inventoryRows = if ($gradleProductionInventory) { @($gradleProductionInventory.modules | Where-Object { $_.module_path -eq $modPath }) } else { @() }
    $tier = if ($inventoryRows.Count -eq 1) { [int]$inventoryRows[0].tier } elseif ($modPath -in $policy.tier1_modules) { 1 } else { 2 }
    if ($tier -notin @(1, 2) -or ($modPath -in $policy.tier1_modules -and $tier -ne 1) -or ($modPath -notin $policy.tier1_modules -and $tier -ne 2)) {
        $canonicalEvidenceErrors += "Production inventory tier classification is invalid for $modPath"
        $canonicalEvidenceValid = $false
    }
    $projRel = if ($inventoryRows.Count -eq 1) { [string]$inventoryRows[0].project_directory } else { $null }
    $projectRootFull = $null
    if (-not [string]::IsNullOrWhiteSpace($projRel) -and -not [System.IO.Path]::IsPathRooted($projRel) -and $projRel.Replace('\', '/') -notmatch '(^|/)\.\.(/|$)') {
        $projectRootFull = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $projRel))
        $repositoryPrefix = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
        if (-not $projectRootFull.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $projectRootFull -PathType Container)) {
            $projectRootFull = $null
        } else {
            $resolvedProjectRoot = (Resolve-Path -LiteralPath $projectRootFull).Path
            if (-not $resolvedProjectRoot.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { $projectRootFull = $null }
        }
    }
    if ($inventoryRows.Count -ne 1 -or -not $projectRootFull) {
        $canonicalEvidenceErrors += "Cannot resolve a unique safe Gradle project directory for $modPath from production inventory"
        $canonicalEvidenceValid = $false
        $srcXml = Join-Path $runDir '.missing-production-project.xml'
        $srcHtmlDir = Join-Path $runDir '.missing-production-project-html'
    } else {
        $srcXml = Join-Path $projectRootFull "build/reports/hdk-coverage/hdkModuleCoverage.xml"
        $srcHtmlDir = Join-Path $projectRootFull "build/reports/hdk-coverage/hdkModuleCoverage"
    }
    $safeName = $modPath.Replace(":", "_").TrimStart("_")
    $dstXml = Join-Path $modulesDir "$safeName.xml"
    $dstHtml = Join-Path $modulesDir "$safeName"
    $relArtifactLink = "reports/modules/$safeName.xml"

    $instCov = [System.Numerics.BigInteger]::Zero
    $instMis = [System.Numerics.BigInteger]::Zero
    $branchCov = [System.Numerics.BigInteger]::Zero
    $branchMis = [System.Numerics.BigInteger]::Zero
    $disposition = "Applicable"
    $reportDigest = ""
    $classInvDigest = ""
    $mEntry = $null

    # Check Gradle counters first if available
    if ($gradleCanonicalCounters -and $gradleCanonicalCounters.modules) {
        $mEntry = $gradleCanonicalCounters.modules | Where-Object { $_.module_path -eq $modPath }
        if ($mEntry) {
            try {
                $instCov = ConvertTo-CoverageBigInteger -Value $mEntry.instruction.covered -Label "$modPath instruction covered"
                $instMis = ConvertTo-CoverageBigInteger -Value $mEntry.instruction.missed -Label "$modPath instruction missed"
                $branchCov = ConvertTo-CoverageBigInteger -Value $mEntry.branch.covered -Label "$modPath branch covered"
                $branchMis = ConvertTo-CoverageBigInteger -Value $mEntry.branch.missed -Label "$modPath branch missed"
            } catch {
                $canonicalEvidenceErrors += $_.Exception.Message
                $canonicalEvidenceValid = $false
            }
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
        if ($mEntry -and $mEntry.report_digest -ne $xmlHash) {
            $canonicalEvidenceErrors += "Canonical report digest mismatch for $modPath"
        }
        $reportDigest = $xmlHash

        if (-not $gradleCanonicalCounters -or -not $mEntry) {
            $canonicalEvidenceErrors += "Canonical JaCoCo analysis is required; XML-derived counters are not accepted for $modPath"
            $disposition = 'Missing'
        }
    } else {
        if ($modPath -eq ":feature:authenticator") {
            $disposition = "NotApplicable"
            if (-not $gradleCanonicalCounters) { $canonicalEvidenceErrors += 'Canonical JaCoCo analysis is required for authenticator zero-scope disposition' }
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
            total = [System.Numerics.BigInteger]::Parse(([string]$instCov), [Globalization.CultureInfo]::InvariantCulture) + [System.Numerics.BigInteger]::Parse(([string]$instMis), [Globalization.CultureInfo]::InvariantCulture)
        }
        branch = [PSCustomObject]@{
            covered = $branchCov
            missed = $branchMis
            total = [System.Numerics.BigInteger]::Parse(([string]$branchCov), [Globalization.CultureInfo]::InvariantCulture) + [System.Numerics.BigInteger]::Parse(([string]$branchMis), [Globalization.CultureInfo]::InvariantCulture)
        }
        report_digest = $reportDigest
        class_inventory_digest = $classInvDigest
        execution_files = if ($mEntry) { @($mEntry.execution_files) } else { @() }
        execution_compatibility = if ($mEntry) { $mEntry.execution_compatibility } else { $null }
    }
    if ($mEntry -and $inventoryRows.Count -eq 1) {
        $compatibilityEvidence = Test-ExecutionCompatibilityEvidence -InventoryModule $inventoryRows[0] -CounterModule $modEntry `
            -AnalyzerIdentity ([string]$gradleCanonicalCounters.analyzer_identity) -Label "Captured $modPath"
        if (-not $compatibilityEvidence.IsValid) {
            $canonicalEvidenceErrors += $compatibilityEvidence.Errors
            $canonicalEvidenceValid = $false
        }
    }
    if ($mEntry -and (Test-Path -LiteralPath $srcXml -PathType Leaf) -and $inventoryRows.Count -eq 1 -and $gradleProductionInventory) {
        try {
            $moduleReport = Read-SafeXml -Path $srcXml
            $reportEvidence = Test-ModuleReportCounterEvidence -XmlDocument $moduleReport -InventoryModule $inventoryRows[0] -CounterModule $modEntry `
                -InventoryDigest $inventoryFileDigest -ExpectedInventoryDigest ([string]$gradleCanonicalCounters.inventory_digest) `
                -AnalyzerIdentity ([string]$gradleCanonicalCounters.analyzer_identity) -ReportDigest $reportDigest `
                -ZeroCountProofs @($gradleCanonicalCounters.zero_count_proofs) -Label "Captured $modPath"
            if (-not $reportEvidence.IsValid) {
                $canonicalEvidenceErrors += $reportEvidence.Errors
                $canonicalEvidenceValid = $false
            }
        } catch {
            $canonicalEvidenceErrors += "Captured $modPath report counter validation failed: $_"
            $canonicalEvidenceValid = $false
        }
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

if ($gradleCanonicalCounters) {
    foreach ($counterModule in $gradleCanonicalCounters.modules) {
        $safeName = $counterModule.module_path.Replace(":", "_").TrimStart("_")
        $copiedXml = Join-Path $modulesDir "$safeName.xml"
        if (-not $counterModule.report_digest -or -not (Test-Path -LiteralPath $copiedXml)) {
            $canonicalEvidenceErrors += "Canonical report or report digest is missing for $($counterModule.module_path)"
        } elseif ((Get-FileSha256 -Path $copiedXml) -ne $counterModule.report_digest) {
            $canonicalEvidenceErrors += "Bundled report does not match canonical report digest for $($counterModule.module_path)"
        }
    }
}
foreach ($evidenceError in $canonicalEvidenceErrors) { Write-Warning $evidenceError }
if ($canonicalEvidenceErrors.Count -gt 0) { $canonicalEvidenceValid = $false }

if ($gradleCanonicalCounters -and $gradleCanonicalCounters.zero_count_proofs) {
    $zeroCountProofs = $gradleCanonicalCounters.zero_count_proofs
}

# Repository Aggregate
$repoSrcXml = Join-Path $RepositoryRoot "build/reports/hdk-coverage/repository.xml"
$repoSrcHtmlDir = Join-Path $RepositoryRoot "build/reports/hdk-coverage/repository"
$repoDstXml = Join-Path $reportsDir "repository.xml"
$repoDstHtml = Join-Path $reportsDir "repository"
$repoInstCov = [System.Numerics.BigInteger]::Zero
$repoInstMis = [System.Numerics.BigInteger]::Zero
$repoBranchCov = [System.Numerics.BigInteger]::Zero
$repoBranchMis = [System.Numerics.BigInteger]::Zero
$repoReportDigest = ""

if (Test-Path $repoSrcXml) {
    Copy-Item -Path $repoSrcXml -Destination $repoDstXml -Force
    if (Test-Path $repoSrcHtmlDir) {
        Copy-Item -Path $repoSrcHtmlDir -Destination $repoDstHtml -Recurse -Force
    }
    $repoReportDigest = Get-FileSha256 -Path $repoSrcXml
    try {
        $repoDoc = Read-SafeXml -Path $repoSrcXml
        $repoCounterMetrics = Get-ReportLevelCoverageCounters -XmlDocument $repoDoc -Scope 'Repository' -RequireNonzeroInstructions
        $repoInstCov = $repoCounterMetrics.instruction.covered
        $repoInstMis = $repoCounterMetrics.instruction.missed
        $repoBranchCov = $repoCounterMetrics.branch.covered
        $repoBranchMis = $repoCounterMetrics.branch.missed
        if (-not $gradleCanonicalCounters -or -not $gradleCanonicalCounters.repository) {
            $canonicalEvidenceErrors += 'Canonical Gradle repository counters are missing'
            $canonicalEvidenceValid = $false
        } else {
            $repositoryBinding = Test-CanonicalRepositoryReportBinding -XmlDocument $repoDoc `
                -RepositoryCounters $gradleCanonicalCounters.repository -ReportDigest $repoReportDigest `
                -ExpectedReportDigest ([string]$gradleCanonicalCounters.repository.report_digest) -Label 'Captured repository'
            if (-not $repositoryBinding.IsValid) {
                $canonicalEvidenceErrors += $repositoryBinding.Errors
                $canonicalEvidenceValid = $false
            }
        }
    } catch {
        $canonicalEvidenceErrors += $_.Exception.Message
        $canonicalEvidenceValid = $false
    }
} else {
    $canonicalEvidenceErrors += "Repository aggregate report is missing: $repoSrcXml"
    $canonicalEvidenceValid = $false
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

# Preserve only execution inputs explicitly reported by the canonical JaCoCo analysis.
# Scope directories and content-addressed names prevent same-basename producers from overwriting.
$execDataDir = Join-Path $runDir "execution-data"
New-Item -ItemType Directory -Path $execDataDir -Force | Out-Null
$executionFilesEvidence = @()
$executionInputs = @()
if (-not $gradleCanonicalCounters) {
    $canonicalEvidenceErrors += 'Cannot retain execution files because canonical JaCoCo counters are missing'
} else {
foreach ($counterModule in @($gradleCanonicalCounters.modules)) {
    if ($null -eq $counterModule.PSObject.Properties['execution_files']) {
        $canonicalEvidenceErrors += "Canonical execution-file attribution is missing for $($counterModule.module_path)"
        continue
    }
    foreach ($executionFile in @($counterModule.execution_files)) {
        $executionInputs += [PSCustomObject]@{ scope = [string]$counterModule.module_path; file = $executionFile }
    }
}
if (-not $gradleCanonicalCounters.repository -or $null -eq $gradleCanonicalCounters.repository.PSObject.Properties['execution_files']) {
    $canonicalEvidenceErrors += 'Canonical repository execution-file attribution is missing'
} else {
    foreach ($executionFile in @($gradleCanonicalCounters.repository.execution_files)) {
        $executionInputs += [PSCustomObject]@{ scope = ':repository'; file = $executionFile }
    }
}
}
foreach ($executionInput in $executionInputs) {
    $sourcePath = [System.IO.Path]::GetFullPath([string]$executionInput.file.path)
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        $canonicalEvidenceErrors += "Attributed execution input is missing for $($executionInput.scope): $sourcePath"
        continue
    }
    $sourceHash = Get-FileSha256 -Path $sourcePath
    if ($sourceHash -cne [string]$executionInput.file.sha256) {
        $canonicalEvidenceErrors += "Attributed execution input changed after JaCoCo analysis for $($executionInput.scope): $sourcePath"
        continue
    }
    $extension = [IO.Path]::GetExtension($sourcePath).ToLowerInvariant()
    if ($extension -notin @('.ec', '.exec')) {
        $canonicalEvidenceErrors += "Unsupported attributed execution-file type for $($executionInput.scope): $sourcePath"
        continue
    }
    $sourceRelative = [System.IO.Path]::GetRelativePath($RepositoryRoot, $sourcePath).Replace('\', '/')
    if ([System.IO.Path]::IsPathRooted($sourceRelative) -or $sourceRelative -eq '..' -or $sourceRelative.StartsWith('../', [System.StringComparison]::Ordinal)) {
        $canonicalEvidenceErrors += "Attributed execution input is outside RepositoryRoot: $sourcePath"
        continue
    }
    $scopeName = if ($executionInput.scope -eq ':repository') { 'repository' } else { $executionInput.scope.Replace(':', '/').TrimStart('/') }
    $kind = if ($extension -eq '.ec') { 'device' } else { 'host' }
    $sourceIdentity = [System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes("$($executionInput.scope)`n$sourceRelative"))
    $sourceIdentityHex = [Convert]::ToHexString($sourceIdentity).ToLowerInvariant()
    $storedName = "$($sourceHash.Substring(0, 16))-$($sourceIdentityHex.Substring(0, 8))-$(Split-Path -Leaf $sourcePath)"
    $bundleRelative = "execution-data/$scopeName/$kind/$storedName"
    $bundlePath = Join-Path $runDir ($bundleRelative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    New-Item -ItemType Directory -Path (Split-Path -Parent $bundlePath) -Force | Out-Null
    Copy-Item -LiteralPath $sourcePath -Destination $bundlePath
    $executionFilesEvidence += [PSCustomObject]@{
        scope = $executionInput.scope
        kind = $kind
        source_path = $sourceRelative
        source_sha256 = $sourceHash
        bundle_path = $bundleRelative
        bundle_sha256 = Get-FileSha256 -Path $bundlePath
    }
}
foreach ($counterModule in @($moduleCountersList)) {
    if (-not $counterModule.execution_compatibility) { continue }
    $sourceBinding = Test-ExecutionCompatibilityAttribution -CompatibilityReceipt $counterModule.execution_compatibility `
        -ManifestExecutionFiles $executionFilesEvidence -Scope ([string]$counterModule.module_path) `
        -ExpectedVariant ([string]$gradleProductionInventory.modules.Where({ $_.module_path -eq $counterModule.module_path }).selected_variant) `
        -Label "Captured $($counterModule.module_path)"
    if (-not $sourceBinding.IsValid) { $canonicalEvidenceErrors += $sourceBinding.Errors; $canonicalEvidenceValid = $false }
}
foreach ($evidenceError in $canonicalEvidenceErrors) { Write-Warning $evidenceError }
if ($canonicalEvidenceErrors.Count -gt 0) { $canonicalEvidenceValid = $false }

# Copy JUnit XML reports and construct structured suite results
$testsReportsDir = Join-Path $reportsDir "tests"
New-Item -ItemType Directory -Path $testsReportsDir -Force | Out-Null
$suitesList = @()

$ownedJUnitResult = if ($gradleCanonicalCounters -and $gradleCanonicalCounters.workload) {
    Get-TaskOwnedJUnitReports -RepositoryRoot $RepositoryRoot -Workload $gradleCanonicalCounters.workload
} else {
    @{ Files = @(); Errors = @('Gradle workload metadata is unavailable for task-owned JUnit collection') }
}
$junitFiles = @($ownedJUnitResult.Files)
$canonicalEvidenceErrors += @($ownedJUnitResult.Errors)
if ($ownedJUnitResult.Errors.Count -gt 0) { $canonicalEvidenceValid = $false }
$junitParseFailures = @()
$junitParseFailureRecords = @()
foreach ($jf in $junitFiles) {
    try {
        $jDoc = Read-SafeXml -Path $jf.FullName
        $testSuiteNodes = $jDoc.SelectNodes("/testsuite | /testsuites/testsuite")
        if ($null -eq $testSuiteNodes -or $testSuiteNodes.Count -eq 0) {
            # Some connected Android tasks produce a valid zero-test aggregate
            # report when the module has no device-side test sources. Retain
            # that task-owned report as evidence without inventing a suite.
            $emptyAggregate = $jDoc.DocumentElement
            if ($emptyAggregate -and $emptyAggregate.Name -eq 'testsuites' -and
                $emptyAggregate.GetAttribute('tests') -eq '0' -and
                $emptyAggregate.GetAttribute('failures') -eq '0' -and
                $emptyAggregate.GetAttribute('errors') -eq '0' -and
                $emptyAggregate.GetAttribute('skipped') -eq '0' -and
                $emptyAggregate.ChildNodes.Count -eq 0) {
                $relativeJUnitPath = [System.IO.Path]::GetRelativePath($RepositoryRoot, $jf.FullName)
                if ([System.IO.Path]::IsPathRooted($relativeJUnitPath) -or $relativeJUnitPath.Replace("\", "/") -match '(^|/)\.\.(/|$)') {
                    throw "JUnit XML path is outside the repository root: $($jf.FullName)"
                }
                $destXml = Join-Path $testsReportsDir $relativeJUnitPath
                New-Item -ItemType Directory -Path (Split-Path -Parent $destXml) -Force | Out-Null
                Copy-Item -LiteralPath $jf.FullName -Destination $destXml -Force
                continue
            }
            throw "JUnit XML contains no test suites: $($jf.FullName)"
        }

        $relativeJUnitPath = [System.IO.Path]::GetRelativePath($RepositoryRoot, $jf.FullName)
        if ([System.IO.Path]::IsPathRooted($relativeJUnitPath) -or $relativeJUnitPath.Replace("\", "/") -match '(^|/)\.\.(/|$)') {
            throw "JUnit XML path is outside the repository root: $($jf.FullName)"
        }

        $destXml = Join-Path $testsReportsDir $relativeJUnitPath
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
        $relativeJUnitPath = [System.IO.Path]::GetRelativePath($RepositoryRoot, $jf.FullName).Replace('\', '/')
        $failureDigest = Get-FileSha256 -Path $jf.FullName
        $failureCopyRelative = "parse-failures/$($failureDigest.Substring(0, 16))-$($jf.Name)"
        $failureCopyPath = Join-Path $testsReportsDir $failureCopyRelative
        New-Item -ItemType Directory -Path (Split-Path -Parent $failureCopyPath) -Force | Out-Null
        Copy-Item -LiteralPath $jf.FullName -Destination $failureCopyPath
        $junitParseFailures += "Failed to parse JUnit XML at ${relativeJUnitPath}: $_"
        $junitParseFailureRecords += [PSCustomObject]@{
            source_path = $relativeJUnitPath
            report_path = "reports/tests/$failureCopyRelative"
            report_sha256 = $failureDigest
            error = [string]$_.Exception.Message
            captured_at_utc = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        }
    }
}
if ($junitParseFailures.Count -gt 0) {
    $junitParseFailures | ForEach-Object { Write-Warning $_ }
    $parseFailurePath = Join-Path $testsReportsDir 'collection-errors.json'
    $junitParseFailureRecords | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $parseFailurePath -Encoding utf8
    $canonicalEvidenceValid = $false
}

# Production Inventory
$inventoryPath = Join-Path $runDir "inventory.json"
if ($gradleProductionInventory -and (Test-Path -LiteralPath $gradleInventoryFile)) {
    Copy-Item -LiteralPath $gradleInventoryFile -Destination $inventoryPath
}

$countersData = [PSCustomObject]@{
    schema_version = "1.0"
    policy_id = "chimali-coverage-v1"
    analyzer_identity = "jacoco-0.8.15"
    inventory_digest = if ($gradleCanonicalCounters) { $gradleCanonicalCounters.inventory_digest } else { "" }
    modules = $moduleCountersList
    repository = [PSCustomObject]@{
        instruction = [PSCustomObject]@{
            covered = $repoInstCov
            missed = $repoInstMis
            total = [System.Numerics.BigInteger]::Parse(([string]$repoInstCov), [Globalization.CultureInfo]::InvariantCulture) + [System.Numerics.BigInteger]::Parse(([string]$repoInstMis), [Globalization.CultureInfo]::InvariantCulture)
        }
        branch = [PSCustomObject]@{
            covered = $repoBranchCov
            missed = $repoBranchMis
            total = [System.Numerics.BigInteger]::Parse(([string]$repoBranchCov), [Globalization.CultureInfo]::InvariantCulture) + [System.Numerics.BigInteger]::Parse(([string]$repoBranchMis), [Globalization.CultureInfo]::InvariantCulture)
        }
        report_digest = $repoReportDigest
    }
    zero_count_proofs = $zeroCountProofs
}

$captureCounterValidation = Test-ComparisonModuleAccounting -Modules @($countersData.modules) -RequiredModulePaths @($policy.tier1_modules + $policy.tier2_gated_modules) -Label 'Captured run'
if (-not $captureCounterValidation.IsValid) {
    $canonicalEvidenceValid = $false
    $canonicalEvidenceErrors += @($captureCounterValidation.Errors)
}
$repositoryInstructionTotal = ConvertTo-CoverageBigInteger -Value $countersData.repository.instruction.total -Label 'Captured repository instruction total'
if ($repositoryInstructionTotal -eq [System.Numerics.BigInteger]::Zero) {
    $canonicalEvidenceValid = $false
    $canonicalEvidenceErrors += 'Captured repository instruction aggregate denominator is zero'
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

$isCollectionComplete = $gradleSuccess -and (-not $SkipExecution) -and $cleanPostflight -and (Test-Path $repoDstXml) -and $canonicalEvidenceValid -and $suitesList.Count -gt 0 -and $junitParseFailures.Count -eq 0
$collectionStatus = if ($isCollectionComplete) { "Complete" } else { "Incomplete" }

$testStatus = if ($SkipExecution) {
    "Unverified"
} elseif (-not $gradleSuccess -or $suiteFailures.Count -gt 0 -or $suitesList.Count -eq 0 -or $junitParseFailures.Count -gt 0) {
    "Failed"
} else {
    "Passed"
}

$collectionErrors = @($canonicalEvidenceErrors + $junitParseFailures + $suiteFailures | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Select-Object -Unique)
if ($SkipExecution) { $collectionErrors += 'Execution was skipped; pre-existing report data is diagnostic and cannot establish a complete capture' }
if (-not $gradleSuccess) { $collectionErrors += 'Gradle coverage workload failed' }
if (-not $cleanPostflight) { $collectionErrors += 'Repository was dirty or HEAD changed after coverage collection' }
if ($suitesList.Count -eq 0) { $collectionErrors += 'No JUnit test suite results were captured' }
$collectionErrors = @($collectionErrors | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Select-Object -Unique)

# Finalize the human summary before sealing the artifact manifest so its digest is retained.
$summaryPath = Join-Path $runDir "summary.md"
$summaryContent = @"
# Coverage Capture Run Summary

- **Run ID**: $runId
- **Purpose**: $Purpose
- **Source Revision**: $SourceRevision
- **Target Revision**: $(if ($TargetRevision) { $TargetRevision } else { "N/A (Initial)" })
- **Device**: $DeviceSerial ($deviceModel, API $deviceApi, $deviceAbi)
- **Collection Status**: $collectionStatus
- **Start UTC**: $startTimeUtc
- **End UTC**: $endTimeUtc

## Counters
- **Repository Aggregate**: Instructions: $repoInstCov covered / $repoInstMis missed; Branches: $repoBranchCov covered / $repoBranchMis missed
"@
Set-Content -Path $summaryPath -Value $summaryContent -Encoding utf8

# Build artifact manifest after all report and summary files are finalized.
$artifactsList = @()
Get-ChildItem -Path $runDir -Recurse -File | Where-Object { $_.Name -ne "manifest.json" } | ForEach-Object {
    $rel = [System.IO.Path]::GetRelativePath($runDir, $_.FullName).Replace("\", "/")
    $artifactsList += [PSCustomObject]@{
        path = $rel
        sha256 = Get-FileSha256 -Path $_.FullName
    }
}

# 8. Assemble Manifest & Evidence
$wrapperProperties = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle/wrapper/gradle-wrapper.properties') -Raw
$catalogVersions = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle/libs.versions.toml') -Raw
$gradleVersion = if ($wrapperProperties -match '(?m)^distributionUrl=.*gradle-([0-9]+(?:\.[0-9]+){1,2})-') { $Matches[1] } else { 'unknown' }
$agpVersion = if ($catalogVersions -match '(?m)^agp\s*=\s*"([^"]+)"') { $Matches[1] } else { 'unknown' }
$kotlinVersion = if ($catalogVersions -match '(?m)^kotlin\s*=\s*"([^"]+)"') { $Matches[1] } else { 'unknown' }
$toolchainIdentity = [PSCustomObject]@{
    java = $javaRuntime
    gradle = $gradleVersion
    android_gradle_plugin = $agpVersion
    kotlin = $kotlinVersion
    jacoco = '0.8.15'
}
$measurementDevice = [PSCustomObject]@{ api_level = $deviceApi; abi = $deviceAbi }
$measurementFingerprint = Get-MeasurementFingerprint -Policy $policy -Toolchain $toolchainIdentity -Device $measurementDevice -HarnessFileDigests ([PSCustomObject]$harnessFileHashes)
$manifest = [PSCustomObject]@{
    run_id = $runId
    schema_version = "1.0"
    policy_id = "chimali-coverage-v1"
    measurement_fingerprint = $measurementFingerprint
    toolchain = $toolchainIdentity
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
    harness = [PSCustomObject]@{
        revision = $harnessRevision
        tree = $harnessTree
        clean = $harnessClean
        file_sha256 = [PSCustomObject]$harnessFileHashes
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
    collection_errors = $collectionErrors
    suites = $suitesList
    workload = if ($gradleCanonicalCounters) { $gradleCanonicalCounters.workload } else { $null }
    execution_files = $executionFilesEvidence
    production_inventory_digest = if ($gradleCanonicalCounters) { $gradleCanonicalCounters.inventory_digest } else { "" }
    trusted_reporter_binding = if (@($trustedReporterValues | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
        [PSCustomObject]@{
            workflow_commit = $TrustedReporterWorkflowSha.ToLowerInvariant()
            reporter_sha = $TrustedReporterSha.ToLowerInvariant()
            repository_id = $TrustedRepositoryId
            app_id = $TrustedCoverageAppId
        }
    } else { $null }
    evidence_records = $evidenceRecords
    zero_count_proofs = $zeroCountProofs
    artifacts = $artifactsList
}

$manifestPath = Join-Path $runDir "manifest.json"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -Path $manifestPath -Encoding utf8

Write-Host "Coverage run captured to $runDir" -ForegroundColor $(if ($gradleSuccess) { "Green" } else { "Yellow" })
Write-Output "[CAPTURE_RESULT] $(ConvertTo-Json -Compress ([PSCustomObject]@{ run_id = $runId; run_dir = $runDir; collection = $collectionStatus; tests = $testStatus }))"

if (-not $gradleSuccess) {
    exit 1
}
exit 0
