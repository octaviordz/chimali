<#
.SYNOPSIS
    Evaluates coverage bundles against authoritative policy, floors, and regression rules.
.DESCRIPTION
    Validates manifest and counter data, calculates exact integer ratios for Tier 1
    (60% instructions, 60% branches where measurable), repository aggregate (50%),
    and Tier 2 (20%). Enforces suite outcomes, artifact integrity, zero proofs,
    retained compiled scope, and writes immutable assessment records outside the sealed bundle.
.PARAMETER RunBundle
    Absolute path to the captured run bundle directory.
.PARAMETER ComparisonBundle
    Optional absolute path to an accepted comparison baseline run bundle.
.PARAMETER AcceptedBaselineIndexPath
    Required alongside ComparisonBundle. Path to the trusted current.json baseline index under the evidence root.
.PARAMETER PolicyPath
    Absolute path to tools/coverage/policy.json.
.PARAMETER ChangedPathsFile
    Optional path to a file listing changed paths for regression scoping.
.PARAMETER OutputDirectory
    Optional path to directory where decision.json and decision.md will be written.
    Cannot be located inside the RunBundle.
.PARAMETER Force
    Allow overwriting an existing decision record in OutputDirectory.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RunBundle,

    [Parameter(Mandatory = $false)]
    [string]$ComparisonBundle,

    [Parameter(Mandatory = $false)]
    [string]$AcceptedBaselineIndexPath,

    [Parameter(Mandatory = $true)]
    [string]$PolicyPath,

    [Parameter(Mandatory = $false)]
    [string]$ChangedPathsFile,

    [Parameter(Mandatory = $false)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $false)]
    [switch]$Force
)

$ErrorActionPreference = "Stop"
function Write-CoverageError {
    param([Parameter(Mandatory = $true)][object]$Message)
    [Console]::Error.WriteLine([string]$Message)
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-CoverageError "Common utilities not found at $CommonPath"
    exit 3
}

. $CommonPath

function Write-PreflightFailureReceipt {
    param(
        [Parameter(Mandatory = $true)][string]$Reason,
        [string]$RunId = $null
    )

    $resolvedId = if ($RunId -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') { $RunId } else { "invalid-$([guid]::NewGuid().ToString())" }
    $receiptDirectory = if ($OutputDirectory) { $OutputDirectory } else { Join-Path (Join-Path (Split-Path -Parent $RunBundle) 'assessments') $resolvedId }
    try {
        if (-not [System.IO.Path]::IsPathRooted($receiptDirectory)) { throw 'Failure receipt directory must be an absolute path' }
        $canonicalRun = Get-CanonicalCoveragePath -Path $RunBundle
        $canonicalReceipt = Get-CanonicalCoveragePath -Path $receiptDirectory
        if (Test-CoveragePathWithinRoot -Path $canonicalReceipt -Root $canonicalRun) {
            throw 'Failure receipt cannot be written inside the capture bundle'
        }
        $decisionPath = Join-Path $receiptDirectory 'decision.json'
        if (Test-Path -LiteralPath $decisionPath) { throw "Failure receipt already exists and is immutable: $decisionPath" }
        New-Item -ItemType Directory -Path $receiptDirectory -Force | Out-Null
        $failure = [PSCustomObject]@{
            run_id = $resolvedId
            evaluated_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
            decisions = [PSCustomObject]@{ collection = 'Incomplete'; tests = 'Unverified'; floors = 'Unverified'; comparison_compatibility = 'Missing'; regression = 'Unverified'; publication = 'Pending'; integration = 'Failed' }
            overall_passed = $false
            unmet_reasons = @($Reason)
        }
        $failure | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $decisionPath -Encoding utf8
        Set-Content -LiteralPath (Join-Path $receiptDirectory 'decision.md') -Value "# Coverage Verification Decision`n`n- Run ID: $resolvedId`n- Overall Result: FAIL`n- Collection: Incomplete`n`n## Unmet Reasons`n- $Reason" -Encoding utf8
        Write-Host "Preflight failure recorded at $decisionPath" -ForegroundColor Yellow
    } catch {
        Write-CoverageError "Could not persist preflight failure receipt; preserving primary verification exit code. $($_.Exception.Message)"
    }
}

# Validate Paths
if (-not [System.IO.Path]::IsPathRooted($RunBundle)) {
    Write-CoverageError "RunBundle must be an absolute path: $RunBundle"
    exit 3
}
if (-not (Test-Path $RunBundle)) {
    Write-CoverageError "RunBundle directory not found: $RunBundle"
    exit 2
}
if (-not [System.IO.Path]::IsPathRooted($PolicyPath)) {
    Write-CoverageError "PolicyPath must be an absolute path: $PolicyPath"
    exit 3
}
if (-not (Test-Path $PolicyPath)) {
    Write-CoverageError "PolicyPath file not found: $PolicyPath"
    exit 3
}
$expectedPolicyPath = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir 'policy.json'))
$suppliedPolicyPath = [System.IO.Path]::GetFullPath($PolicyPath)
if (-not [string]::Equals($expectedPolicyPath, $suppliedPolicyPath, [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-CoverageError "PolicyPath must resolve to the reviewed harness policy: $expectedPolicyPath"
    exit 3
}

# 1. Load and Validate Policy
try {
    $policy = Read-SafeJson -Path $PolicyPath
    $polValidation = Test-PolicyConfiguration -Policy $policy
    if (-not $polValidation.IsValid) {
        [Console]::Error.WriteLine("Invalid policy configuration: $($polValidation.Reasons -join '; ')")
        exit 3
    }
} catch {
    [Console]::Error.WriteLine("Failed to load policy: $_")
    exit 3
}

# 2. Load and Validate Manifest
$manifestPath = Join-Path $RunBundle "manifest.json"
if (-not (Test-Path $manifestPath)) {
    Write-PreflightFailureReceipt -Reason "manifest.json not found in RunBundle: $manifestPath"
    exit 2
}

try {
    $manifest = Read-SafeJson -Path $manifestPath
    $trustedHarnessRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))
    $manifestValidation = Test-ManifestSchema -Manifest $manifest -Policy $policy -TrustedHarnessRoot $trustedHarnessRoot
    if (-not $manifestValidation.IsValid) {
        $schemaReasons = $manifestValidation.Errors -join '; '
        Write-PreflightFailureReceipt -Reason "Manifest schema validation failed: $schemaReasons" -RunId ([string]$manifest.run_id)
        exit 3
    }
} catch {
    Write-PreflightFailureReceipt -Reason "Failed to load manifest: $_"
    exit 3
}

# 3. Output Directory Enforcement (Outside Sealed Bundle & No Silent Overwrite)
$bundleParent = Split-Path -Parent $RunBundle
$targetOutDir = if ($OutputDirectory) {
    if (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
        Write-CoverageError "OutputDirectory must be an absolute path: $OutputDirectory"
        exit 3
    }
    $OutputDirectory
} else {
    Join-Path (Join-Path $bundleParent "assessments") $manifest.run_id
}

$canonicalBundle = Get-CanonicalCoveragePath -Path $RunBundle
$canonicalOut = Get-CanonicalCoveragePath -Path $targetOutDir
if (Test-CoveragePathWithinRoot -Path $canonicalOut -Root $canonicalBundle) {
    Write-CoverageError "Assessment receipts cannot be written inside sealed capture bundle: $RunBundle"
    exit 3
}

if (-not (Test-Path $targetOutDir)) {
    New-Item -ItemType Directory -Path $targetOutDir -Force | Out-Null
}

$decisionJsonPath = Join-Path $targetOutDir "decision.json"
if (Test-Path $decisionJsonPath) {
    Write-CoverageError "Assessment receipt already exists at $decisionJsonPath; assessment receipts are immutable"
    exit 3
}

$reasons = @()
$bundleEvidenceValid = $true

# 4. Verify Artifact Paths & Digests
if ($manifest.artifacts) {
    foreach ($art in $manifest.artifacts) {
        if (-not $art.path) {
            $bundleEvidenceValid = $false
            $reasons += "Manifest artifact entry missing path"
            continue
        }
        if ($art.path -match "\.\." -or [System.IO.Path]::IsPathRooted($art.path)) {
            $bundleEvidenceValid = $false
            $reasons += "Manifest artifact path is escaping or rooted: $($art.path)"
            continue
        }
        $artFullPath = Join-Path $RunBundle $art.path
        if (-not (Test-Path $artFullPath)) {
            $bundleEvidenceValid = $false
            $reasons += "Manifest artifact not found on disk: $($art.path)"
            continue
        }
        if ($art.sha256) {
            $computedHash = Get-FileSha256 -Path $artFullPath
            if ($computedHash -ne $art.sha256.ToLower()) {
                $bundleEvidenceValid = $false
                $reasons += "Manifest artifact digest mismatch for $($art.path): expected $($art.sha256), observed $computedHash"
            }
        } else {
            $bundleEvidenceValid = $false
            $reasons += "Manifest artifact is missing its required digest: $($art.path)"
        }
    }
}

# The sealed manifest must list every retained file, not just files it elects to hash.
$manifestArtifactPaths = @($manifest.artifacts | ForEach-Object { ([string]$_.path).Replace('\', '/') })
$diskArtifactPaths = @(Get-ChildItem -LiteralPath $RunBundle -File -Recurse | Where-Object {
        [System.IO.Path]::GetFullPath($_.FullName) -ne [System.IO.Path]::GetFullPath($manifestPath)
    } | ForEach-Object {
        [System.IO.Path]::GetRelativePath($RunBundle, $_.FullName).Replace('\', '/')
    })
foreach ($diskPath in $diskArtifactPaths) {
    if ($diskPath -notin $manifestArtifactPaths) {
        $bundleEvidenceValid = $false
        $reasons += "Retained file is omitted from the sealed artifact manifest: $diskPath"
    }
}
foreach ($listedPath in $manifestArtifactPaths) {
    if ($listedPath -notin $diskArtifactPaths) {
        $bundleEvidenceValid = $false
        $reasons += "Manifest artifact is missing from the sealed bundle: $listedPath"
    }
}
$nonemptyExecutionArtifacts = @($manifest.artifacts | Where-Object { $_.path -match '^execution-data/.+\.(exec|ec)$' -and (Test-Path -LiteralPath (Join-Path $RunBundle $_.path)) -and (Get-Item -LiteralPath (Join-Path $RunBundle $_.path)).Length -gt 0 })
if ($nonemptyExecutionArtifacts.Count -eq 0) {
    $bundleEvidenceValid = $false
    $reasons += 'No nonempty current-run JaCoCo execution-data artifact is retained'
}
$executionEvidence = @($manifest.execution_files)
if ($executionEvidence.Count -eq 0) {
    $bundleEvidenceValid = $false
} else {
}
$executionMapValidation = Test-ExecutionFileAttribution -ExecutionFiles $manifest.execution_files -Artifacts $manifest.artifacts
if (-not $executionMapValidation.IsValid) {
    $bundleEvidenceValid = $false
    $reasons += $executionMapValidation.Errors
}
# 5. Verify Test Suites and Execution Outcomes (Do not trust manifest Passed blindly)
$suitesPassed = $true
if ($manifest.suites -and $manifest.suites.Count -gt 0) {
    foreach ($s in $manifest.suites) {
        $sOutcome = Test-SuiteOutcome -SuiteResult $s
        if ($sOutcome.Status -ne "Passed") {
            $suitesPassed = $false
            $reasons += "Test suite $($s.suite_name): $($sOutcome.Reasons -join '; ')"
        }
    }
}

# Rebuild the suite totals from retained JUnit XML instead of trusting the manifest summary.
$actualSuites = @{}
$junitRoot = Join-Path $RunBundle 'reports/tests'
$junitFiles = if (Test-Path -LiteralPath $junitRoot) { @(Get-ChildItem -LiteralPath $junitRoot -Filter '*.xml' -File -Recurse) } else { @() }
if ($junitFiles.Count -eq 0) {
    $suitesPassed = $false
    $bundleEvidenceValid = $false
    $reasons += 'No retained JUnit XML files are available for independent suite verification'
}
foreach ($junitFile in $junitFiles) {
    $relativeReportPath = [System.IO.Path]::GetRelativePath($junitRoot, $junitFile.FullName).Replace('\', '/')
    $parsedJUnit = Get-StrictJUnitOutcome -Path $junitFile.FullName -ReportIdentity $relativeReportPath
    if (-not $parsedJUnit.IsValid) {
        $suitesPassed = $false
        $bundleEvidenceValid = $false
        $reasons += "Malformed retained JUnit XML $($junitFile.FullName): $($parsedJUnit.Errors -join '; ')"
        continue
    }
    foreach ($suite in $parsedJUnit.Suites) {
        if ($actualSuites.ContainsKey([string]$suite.suite_identity)) {
            $suitesPassed = $false
            $bundleEvidenceValid = $false
            $reasons += "Duplicate retained JUnit suite identity: $($suite.suite_identity)"
        } else {
            $actualSuites[[string]$suite.suite_identity] = $suite
        }
    }
}

$recordedSuites = @{}
foreach ($suite in @($manifest.suites)) {
    $identity = [string]$suite.suite_identity
    if ([string]::IsNullOrWhiteSpace($identity) -or $recordedSuites.ContainsKey($identity)) {
        $suitesPassed = $false
        $bundleEvidenceValid = $false
        $reasons += 'Manifest JUnit suite identity is missing or duplicated'
        continue
    }
    $recordedSuites[$identity] = $suite
}
foreach ($identity in (@($actualSuites.Keys) + @($recordedSuites.Keys) | Sort-Object -Unique)) {
    if (-not $actualSuites.ContainsKey($identity) -or -not $recordedSuites.ContainsKey($identity)) {
        $suitesPassed = $false
        $bundleEvidenceValid = $false
        $reasons += "Required suite record/JUnit report is missing on one side for '$identity'"
        continue
    }
    foreach ($metric in @('tests', 'executed', 'successful', 'failed', 'failures', 'errors', 'skipped')) {
        if ([System.Numerics.BigInteger]::Parse([string]$actualSuites[$identity].$metric, [Globalization.CultureInfo]::InvariantCulture) -ne
            [System.Numerics.BigInteger]::Parse([string]$recordedSuites[$identity].$metric, [Globalization.CultureInfo]::InvariantCulture)) {
            $suitesPassed = $false
            $bundleEvidenceValid = $false
            $reasons += "JUnit and manifest disagree for suite '$identity' $metric count"
        }
    }
}

$finalTestsStatus = if (-not $suitesPassed) {
    "Failed"
} elseif ($manifest.decisions -and $manifest.decisions.tests) {
    $manifest.decisions.tests
} else {
    "Unverified"
}

if ($finalTestsStatus -ne "Passed") {
    $reasons += "Required tests are not passed: status is $finalTestsStatus"
}

# 6. Verify Collection Completeness
$finalCollectionStatus = if ($manifest.decisions -and $manifest.decisions.collection) {
    $manifest.decisions.collection
} else {
    "Incomplete"
}

$cleanlinessProved = $manifest.git -and
    $manifest.git.clean_preflight -is [bool] -and $manifest.git.clean_preflight -and
    $manifest.git.clean_postflight -is [bool] -and $manifest.git.clean_postflight -and
    $manifest.harness -and $manifest.harness.clean -is [bool] -and $manifest.harness.clean
if (-not $cleanlinessProved) {
    $finalCollectionStatus = "Incomplete"
    $reasons += "Source and reviewed harness cleanliness were not proved before and after collection"
}

if (-not $bundleEvidenceValid) {
    $finalCollectionStatus = 'Incomplete'
    $reasons += 'Collection is incomplete: one or more required bundle artifacts are missing or corrupt'
}

if ($finalCollectionStatus -ne "Complete") {
    $reasons += "Collection is incomplete: status is $finalCollectionStatus"
    foreach ($collectionError in @($manifest.collection_errors)) {
        if (-not [string]::IsNullOrWhiteSpace([string]$collectionError)) { $reasons += "Capture collection error: $collectionError" }
    }
}

# 7. Load and Verify Canonical Counters against Reports & Proofs
$countersPath = Join-Path $RunBundle "counters.json"
$countersData = $null
if (Test-Path $countersPath) {
    try {
        $countersData = Read-SafeJson -Path $countersPath
    } catch {
        $bundleEvidenceValid = $false
        $reasons += "Failed to read counters.json: $_"
    }
} else {
    $reasons += "counters.json missing from run bundle"
}

$canonicalInventoryValid = $true
$repositoryCountersValid = $true
$inventoryFileDigest = ''
$requiredCounterPaths = @($policy.tier1_modules + $policy.tier2_gated_modules)
if ($countersData) {
    $runtimeBinding = Test-MeasurementRuntimeBinding -Counters $countersData -Manifest $manifest -Policy $policy
    if (-not $runtimeBinding.IsValid) {
        $canonicalInventoryValid = $false
        $reasons += @($runtimeBinding.Errors | ForEach-Object { "Run measurement runtime: $_" })
    }
    $runCounterValidation = Test-ComparisonModuleAccounting -Modules @($countersData.modules) -RequiredModulePaths $requiredCounterPaths -Label 'Run'
    if (-not $runCounterValidation.IsValid) {
        $canonicalInventoryValid = $false
        $reasons += $runCounterValidation.Errors
    }
    $repositoryEvidence = Test-ComparisonRepositoryEvidence -BundlePath $RunBundle -Manifest $manifest -Counters $countersData
    if (-not $repositoryEvidence.IsValid) {
        $canonicalInventoryValid = $false
        $repositoryCountersValid = $false
        $reasons += @($repositoryEvidence.Errors | ForEach-Object { "Run repository evidence: $_" })
    }
}
$inventoryPath = Join-Path $RunBundle 'inventory.json'
$inventoryData = $null
if (-not (Test-Path -LiteralPath $inventoryPath)) {
    $canonicalInventoryValid = $false
    $reasons += 'Retained production inventory is missing from the run bundle'
} else {
    try {
        $inventoryData = Read-SafeJson -Path $inventoryPath
    } catch {
        $canonicalInventoryValid = $false
        $reasons += "Retained production inventory is invalid JSON: $_"
    }
}
$effectiveTier2Paths = @($policy.tier2_gated_modules)
$tier2OwnerSelectionValid = $true
$selectionChangedPaths = @()
$selectionChangedPathsProvided = $false
try {
    if ($ChangedPathsFile) {
        $canonicalChangedPaths = [System.IO.Path]::GetFullPath($ChangedPathsFile)
        $expectedChangedPaths = [System.IO.Path]::GetFullPath((Join-Path $RunBundle 'changed-paths.txt'))
        if ($canonicalChangedPaths -cne $expectedChangedPaths -or -not (Test-Path -LiteralPath $expectedChangedPaths -PathType Leaf)) {
            throw 'Changed-path selection must use the sealed changed-paths.txt artifact in the run bundle'
        }
        $changedPathArtifact = @($manifest.artifacts | Where-Object { $_.path -ceq 'changed-paths.txt' })
        if ($changedPathArtifact.Count -ne 1 -or (Get-FileSha256 -Path $expectedChangedPaths) -cne [string]$changedPathArtifact[0].sha256) {
            throw 'Sealed changed-paths.txt is missing, duplicated, or has an invalid artifact digest'
        }
        $receiptValidation = Read-ChangedPathReceipt -Path $expectedChangedPaths `
            -ExpectedTargetCommit ([string]$manifest.git.target_commit) -ExpectedSourceCommit ([string]$manifest.git.source_commit)
        if (-not $receiptValidation.IsValid) { throw ($receiptValidation.Errors -join '; ') }
        $selectionChangedPaths = @($receiptValidation.Paths)
        $selectionChangedPathsProvided = $true
    }
    $affectedTier2 = @(Get-AffectedTier2Modules -Policy $policy -Inventory $inventoryData -ChangedPaths $selectionChangedPaths -ChangedPathsProvided $selectionChangedPathsProvided)
    $effectiveTier2Paths = @($effectiveTier2Paths + $affectedTier2 | Select-Object -Unique | Sort-Object -CaseSensitive)
} catch {
    $tier2OwnerSelectionValid = $false
    $floorPassed = $false
    $reasons += "Unable to derive affected Tier 2 owners from sealed production evidence: $_"
}
if ($inventoryData -and $countersData) {
    $inventoryFileDigest = Get-FileSha256 -Path $inventoryPath
    if ($countersData.inventory_digest -ne $inventoryFileDigest -or
        $manifest.production_inventory_digest -ne $inventoryFileDigest) {
        $canonicalInventoryValid = $false
        $reasons += 'Inventory file digest does not match manifest and canonical counters'
    }
    if ($inventoryData.analyzer_identity -ne $countersData.analyzer_identity) {
        $canonicalInventoryValid = $false
        $reasons += 'Inventory analyzer identity does not match canonical counters'
    }

    $moduleSetBinding = Test-ComparisonModuleSetBinding -InventoryModules @($inventoryData.modules) `
        -CounterModules @($countersData.modules) -RequiredModulePaths @($policy.tier1_modules + $effectiveTier2Paths) `
        -Label 'Run'
    if (-not $moduleSetBinding.IsValid) {
        $canonicalInventoryValid = $false
        $reasons += $moduleSetBinding.Errors
    }
    $inventoryStructure = Test-ComparisonInventoryStructure -InventoryModules @($inventoryData.modules) -Policy $policy -Label 'Run'
    if (-not $inventoryStructure.IsValid) {
        $canonicalInventoryValid = $false
        $reasons += $inventoryStructure.Errors
    }

    foreach ($inventoryModule in $inventoryData.modules) {
        $classEntries = @($inventoryModule.retained_classes)
        $classDigestResult = Get-ProductionClassInventoryDigest -InventoryModule $inventoryModule
        if (-not $classDigestResult.IsValid -or $inventoryModule.class_inventory_digest -ne $classDigestResult.Digest) {
            $canonicalInventoryValid = $false
            foreach ($digestReason in $classDigestResult.Reasons) { $reasons += "$($inventoryModule.module_path): $digestReason" }
            if ($inventoryModule.class_inventory_digest -ne $classDigestResult.Digest) {
                $reasons += "$($inventoryModule.module_path): retained class digest does not match listed classes"
            }
        }
        if (($inventoryModule.disposition -eq 'NotApplicable') -and $classEntries.Count -gt 0) {
            $canonicalInventoryValid = $false
            $reasons += "$($inventoryModule.module_path): NotApplicable disposition has retained executable classes"
        }
        if (($inventoryModule.disposition -eq 'Applicable') -and $classEntries.Count -eq 0) {
            $canonicalInventoryValid = $false
            $reasons += "$($inventoryModule.module_path): Applicable disposition has no retained executable classes"
        }
    }

    $requiredModulePaths = @($policy.tier1_modules + $effectiveTier2Paths | Select-Object -Unique)
    $productionAccounting = Test-ComparisonModuleAccounting -Modules @($countersData.modules) -RequiredModulePaths $requiredModulePaths -Label 'Run production inventory'
    if (-not $productionAccounting.IsValid) {
        $canonicalInventoryValid = $false
        $reasons += $productionAccounting.Errors
    }
    foreach ($inventoryRecord in @($inventoryData.modules)) {
        $requiredModule = [string]$inventoryRecord.module_path
        $counterModule = $countersData.modules | Where-Object { $_.module_path -eq $requiredModule } | Select-Object -First 1
        $inventoryModule = $inventoryRecord
        if (-not $counterModule -or -not $inventoryModule) {
            $canonicalInventoryValid = $false
            $reasons += "${requiredModule}: counter or retained inventory entry is missing"
            continue
        }
        if ($counterModule.disposition -ne $inventoryModule.disposition) {
            $canonicalInventoryValid = $false
            $reasons += "${requiredModule}: inventory and canonical counter dispositions disagree"
        }
        $compatibilityEvidence = Test-ExecutionCompatibilityEvidence -InventoryModule $inventoryModule -CounterModule $counterModule `
            -AnalyzerIdentity ([string]$countersData.analyzer_identity) -Label $requiredModule
        if (-not $compatibilityEvidence.IsValid) {
            $canonicalInventoryValid = $false
            $reasons += $compatibilityEvidence.Errors
        }
        $sourceBinding = Test-ExecutionCompatibilityAttribution -CompatibilityReceipt $counterModule.execution_compatibility `
            -ManifestExecutionFiles $manifest.execution_files -Scope $requiredModule `
            -ExpectedVariant ([string]$inventoryModule.selected_variant) -Workload $manifest.workload `
            -ExpectedRunId ([string]$manifest.run_id) -ExpectedDeviceSerial ([string]$manifest.device.device_serial) -Label $requiredModule
        if (-not $sourceBinding.IsValid) {
            $canonicalInventoryValid = $false
            $reasons += $sourceBinding.Errors
        }
        $safeName = $requiredModule.Replace(':', '_').TrimStart('_')
        $xmlPath = Join-Path (Join-Path $RunBundle 'reports/modules') "$safeName.xml"
        if (-not $counterModule.report_digest -or -not (Test-Path -LiteralPath $xmlPath)) {
            $canonicalInventoryValid = $false
            $reasons += "${requiredModule}: report or report digest is missing"
        } else {
            try {
                $moduleXml = Read-SafeXml -Path $xmlPath
                $proofRows = @($countersData.zero_count_proofs | Where-Object { $_.module_path -eq $requiredModule })
                if ($proofRows.Count -eq 0) { $proofRows = @($manifest.zero_count_proofs | Where-Object { $_.module_path -eq $requiredModule }) }
                $reportBinding = Test-ModuleReportCounterEvidence -XmlDocument $moduleXml -InventoryModule $inventoryModule -CounterModule $counterModule `
                    -InventoryDigest $inventoryFileDigest -ExpectedInventoryDigest ([string]$countersData.inventory_digest) `
                    -AnalyzerIdentity ([string]$countersData.analyzer_identity) -ReportDigest (Get-FileSha256 -Path $xmlPath) `
                    -ZeroCountProofs $proofRows -Label $requiredModule
                if (-not $reportBinding.IsValid) {
                    $canonicalInventoryValid = $false
                    $reasons += @($reportBinding.Errors | ForEach-Object { "${requiredModule}: $_" })
                }
            } catch {
                $canonicalInventoryValid = $false
                $reasons += "${requiredModule}: report-level counters are invalid or disagree with canonical evidence: $_"
            }
        }
    }
    [object[]]$repositoryInputs = @()
    if ($countersData.repository -and $null -ne $countersData.repository.execution_files) {
        $repositoryInputs = @($countersData.repository.execution_files)
    }
    $repositoryExecutionBinding = Test-RepositoryExecutionAttribution `
        -RepositoryExecutionFiles $repositoryInputs -ManifestExecutionFiles @($manifest.execution_files) `
        -Workload $manifest.workload -ExpectedRunId ([string]$manifest.run_id) -ExpectedDeviceSerial ([string]$manifest.device.device_serial)
    if (-not $repositoryExecutionBinding.IsValid) {
        $canonicalInventoryValid = $false
        $reasons += $repositoryExecutionBinding.Errors
    }
}
if (-not $canonicalInventoryValid) {
    $finalCollectionStatus = 'Incomplete'
    $reasons += 'Collection is incomplete: canonical production inventory/report bindings failed'
}

$floorPassed = $true
$moduleDecisions = @()

# Verify Tier 1 Modules
foreach ($modPath in $policy.tier1_modules) {
    $modCounters = $null
    if ($countersData -and $countersData.modules) {
        $modCounters = $countersData.modules | Where-Object { $_.module_path -eq $modPath }
    }

    if (-not $modCounters) {
        $floorPassed = $false
        $reasons += "${modPath}: missing counter data in run bundle"
        $moduleDecisions += [PSCustomObject]@{
            Module = $modPath
            Tier = 1
            Disposition = "Missing"
            Status = "Failed"
            Reason = "Missing counter data"
        }
        continue
    }

    $moduleCounterCheck = Test-ComparisonModuleAccounting -Modules @($modCounters) -RequiredModulePaths @($modPath) -Label 'Run'
    if (-not $moduleCounterCheck.IsValid) {
        $floorPassed = $false
        $reasons += $moduleCounterCheck.Errors
        $moduleDecisions += [PSCustomObject]@{
            Module = $modPath
            Tier = 1
            Disposition = if ($modCounters.disposition) { $modCounters.disposition } else { 'Invalid' }
            Status = 'Failed'
            Reason = 'Invalid raw counter evidence'
        }
        continue
    }

    # Locate Module XML report if present in RunBundle
    $cleanModName = $modPath.Replace(":", "_").TrimStart("_")
    $modXmlCandidatePaths = @(
        (Join-Path (Join-Path $RunBundle "reports") "$cleanModName.xml"),
        (Join-Path (Join-Path (Join-Path $RunBundle "reports") "modules") "$cleanModName.xml"),
        (Join-Path (Join-Path $RunBundle "reports") (Join-Path $cleanModName "jacoco.xml"))
    )
    $modXmlPath = $null
    foreach ($candidate in $modXmlCandidatePaths) {
        if (Test-Path $candidate) {
            $modXmlPath = $candidate
            break
        }
    }

    # If XML report exists, verify report level counters match counters.json
    $xmlDoc = $null
    $xmlHash = $null
    if ($modXmlPath) {
        try {
            $xmlDoc = Read-SafeXml -Path $modXmlPath
            $xmlHash = Get-FileSha256 -Path $modXmlPath
        } catch {
            $canonicalInventoryValid = $false
            $reasons += "${modPath}: failed to read XML report at ${modXmlPath}: $_"
        }
    }

    $instCov = $modCounters.instruction.covered
    $instMis = $modCounters.instruction.missed
    $instTot = [System.Numerics.BigInteger]::Parse($instCov.ToString()) + [System.Numerics.BigInteger]::Parse($instMis.ToString())

    $branchCov = $modCounters.branch.covered
    $branchMis = $modCounters.branch.missed
    $branchTot = [System.Numerics.BigInteger]::Parse($branchCov.ToString()) + [System.Numerics.BigInteger]::Parse($branchMis.ToString())

    # Verify XML agreement if report exists
    if ($xmlDoc -and $xmlDoc.report) {
        $xmlInstNode = $xmlDoc.report.SelectSingleNode("counter[@type='INSTRUCTION']")
        if ($xmlInstNode) {
            try {
                $xCov = ConvertTo-CoverageBigInteger -Value $xmlInstNode.GetAttribute('covered') -Label "$modPath XML instruction covered"
                $xMis = ConvertTo-CoverageBigInteger -Value $xmlInstNode.GetAttribute('missed') -Label "$modPath XML instruction missed"
                if ($xCov -lt 0 -or $xMis -lt 0 -or $xCov -ne $instCov -or $xMis -ne $instMis) {
                    throw 'negative or mismatching XML instruction counters'
                }
            } catch {
                $floorPassed = $false
                $reasons += "${modPath}: instruction counters in counters.json do not match valid XML values (JSON: $instCov/$instMis): $_"
            }
        } elseif ($instTot -gt 0) {
            $floorPassed = $false
            $reasons += "${modPath}: positive instruction counters have no corresponding report-level XML counter"
        }

        $xmlBranchNode = $xmlDoc.report.SelectSingleNode("counter[@type='BRANCH']")
        if ($xmlBranchNode) {
            try {
                $xCov = ConvertTo-CoverageBigInteger -Value $xmlBranchNode.GetAttribute('covered') -Label "$modPath XML branch covered"
                $xMis = ConvertTo-CoverageBigInteger -Value $xmlBranchNode.GetAttribute('missed') -Label "$modPath XML branch missed"
                if ($xCov -lt 0 -or $xMis -lt 0 -or $xCov -ne $branchCov -or $xMis -ne $branchMis) {
                    throw 'negative or mismatching XML branch counters'
                }
            } catch {
                $floorPassed = $false
                $reasons += "${modPath}: branch counters in counters.json do not match valid XML values (JSON: $branchCov/$branchMis): $_"
            }
        } elseif ($branchTot -gt 0) {
            $floorPassed = $false
            $reasons += "${modPath}: positive branch counters have no corresponding report-level XML counter"
        }
    }

    # Check for empty instruction module (requires canonical proof AnalyzedZeroProofV1)
    if ($instTot -eq [System.Numerics.BigInteger]::Zero) {
        # Find proof
        $proof = $null
        if ($manifest.zero_count_proofs) {
            $proof = $manifest.zero_count_proofs | Where-Object { $_.module_path -eq $modPath }
        }
        if (-not $proof -and $countersData.zero_count_proofs) {
            $proof = $countersData.zero_count_proofs | Where-Object { $_.module_path -eq $modPath }
        }

        $inventoryModule = if ($inventoryData) { $inventoryData.modules | Where-Object { $_.module_path -eq $modPath } | Select-Object -First 1 } else { $null }
        $proofBinding = if ($proof -and $inventoryModule -and $xmlHash) {
            Test-CanonicalZeroProofBinding -Proof $proof -InventoryModule $inventoryModule -CounterModule $modCounters -InventoryDigest $inventoryFileDigest -ExpectedInventoryDigest $countersData.inventory_digest -AnalyzerIdentity $countersData.analyzer_identity -ReportDigest $xmlHash
        } else { $null }
        $proofValid = ($null -ne $proof) -and ($null -ne $proofBinding) -and $proofBinding.IsValid -and ($proof.counters.INSTRUCTION.total -eq 0)

        if ($proofValid) {
            $moduleDecisions += [PSCustomObject]@{
                Module = $modPath
                Tier = 1
                Disposition = "NotApplicable"
                Status = "Passed"
                Reason = "Verified no executable production instructions (AnalyzedZeroProofV1)"
            }
        } else {
            $floorPassed = $false
            $reasons += "${modPath}: empty instructions without valid AnalyzedZeroProofV1 proof"
            $moduleDecisions += [PSCustomObject]@{
                Module = $modPath
                Tier = 1
                Disposition = "NotApplicable"
                Status = "Failed"
                Reason = "Empty instructions without valid AnalyzedZeroProofV1 proof"
            }
        }
        continue
    }

    # Evaluate Tier 1 Instruction Floor (60%)
    $instEval = Test-ExactFloorRatio -Covered $instCov -Missed $instMis -MinimumPercent $policy.tier1_instruction_min_percent
    if (-not $instEval.Passed) {
        $floorPassed = $false
        $reasons += "${modPath}: instruction $($instEval.Reason)"
    }

    # Evaluate Tier 1 Branch Floor (60% where measurable)
    $branchEval = Test-ExactFloorRatio -Covered $branchCov -Missed $branchMis -MinimumPercent $policy.tier1_branch_min_percent
    if ($branchEval.Applicable) {
        if (-not $branchEval.Passed) {
            $floorPassed = $false
            $reasons += "${modPath}: branch $($branchEval.Reason)"
        }
    } else {
        # Branchless module - check if proof is required for omitted counter
        if ($xmlDoc -and $xmlDoc.report -and (-not $xmlDoc.report.SelectSingleNode("counter[@type='BRANCH']"))) {
            $proof = $null
            if ($manifest.zero_count_proofs) {
                $proof = $manifest.zero_count_proofs | Where-Object { $_.module_path -eq $modPath }
            }
            if (-not $proof -and $countersData.zero_count_proofs) {
                $proof = $countersData.zero_count_proofs | Where-Object { $_.module_path -eq $modPath }
            }
            $inventoryModule = if ($inventoryData) { $inventoryData.modules | Where-Object { $_.module_path -eq $modPath } | Select-Object -First 1 } else { $null }
            $proofBinding = if ($proof -and $inventoryModule -and $xmlHash) {
                Test-CanonicalZeroProofBinding -Proof $proof -InventoryModule $inventoryModule -CounterModule $modCounters -InventoryDigest $inventoryFileDigest -ExpectedInventoryDigest $countersData.inventory_digest -AnalyzerIdentity $countersData.analyzer_identity -ReportDigest $xmlHash
            } else { $null }
            $proofValid = ($null -ne $proof) -and ($null -ne $proofBinding) -and $proofBinding.IsValid -and ($proof.counters.BRANCH.total -eq 0)
            if (-not $proofValid) {
                $floorPassed = $false
                $reasons += "${modPath}: omitted XML branch counter without valid AnalyzedZeroProofV1 proof"
            }
        }
    }

    $modPassed = $instEval.Passed -and (-not $branchEval.Applicable -or $branchEval.Passed)
    $moduleDecisions += [PSCustomObject]@{
        Module = $modPath
        Tier = 1
        Disposition = "Applicable"
        Instruction = @{ Covered = $instCov; Missed = $instMis; Percent = $instEval.DisplayPercent; Passed = $instEval.Passed }
        Branch = @{
            Covered = $branchCov
            Missed = $branchMis
            Applicable = $branchEval.Applicable
            Percent = $branchEval.DisplayPercent
            Passed = if ($branchEval.Applicable) { $branchEval.Passed } else { $true }
        }
        Status = if ($modPassed) { "Passed" } else { "Failed" }
    }
}

# Verify configured and changed Tier 2 owners (20% instruction floor)
foreach ($modPath in $effectiveTier2Paths) {
    $modRows = if ($countersData -and $countersData.modules) { @($countersData.modules | Where-Object { $_.module_path -eq $modPath }) } else { @() }
    if ($modRows.Count -ne 1) {
        $floorPassed = $false
        $reasons += "${modPath}: affected Tier 2 counter evidence is missing or duplicated"
        $moduleDecisions += [PSCustomObject]@{ Module = $modPath; Tier = 2; Disposition = 'Missing'; Status = 'Failed'; Reason = 'Affected Tier 2 counter evidence is missing or duplicated' }
        continue
    }
    $modCounters = $modRows[0]
    $moduleCounterCheck = Test-ComparisonModuleAccounting -Modules @($modCounters) -RequiredModulePaths @($modPath) -Label 'Run'
    if (-not $moduleCounterCheck.IsValid) {
        $floorPassed = $false
        $reasons += $moduleCounterCheck.Errors
        $moduleDecisions += [PSCustomObject]@{ Module = $modPath; Tier = 2; Disposition = 'Invalid'; Status = 'Failed'; Reason = 'Invalid raw counter evidence' }
        continue
    }
    if ($modCounters.disposition -eq 'NotApplicable') {
        $moduleDecisions += [PSCustomObject]@{ Module = $modPath; Tier = 2; Disposition = 'NotApplicable'; Instruction = @{ Covered = $modCounters.instruction.covered; Missed = $modCounters.instruction.missed; Percent = 'N/A'; Passed = $true }; Branch = @{ Covered = $modCounters.branch.covered; Missed = $modCounters.branch.missed; Percent = 'Reported' }; Status = 'Passed' }
        continue
    }
    $instCov = $modCounters.instruction.covered
    $instMis = $modCounters.instruction.missed
    $instEval = Test-ExactFloorRatio -Covered $instCov -Missed $instMis -MinimumPercent $policy.tier2_instruction_min_percent
    if (-not $instEval.Applicable -or -not $instEval.Passed) {
        $floorPassed = $false
        $reasons += "${modPath}: Tier 2 instruction $($instEval.Reason)"
    }
    $moduleDecisions += [PSCustomObject]@{
        Module = $modPath
        Tier = 2
        Disposition = 'Applicable'
        Instruction = @{ Covered = $instCov; Missed = $instMis; Percent = $instEval.DisplayPercent; Passed = $instEval.Passed }
        Branch = @{ Covered = $modCounters.branch.covered; Missed = $modCounters.branch.missed; Percent = 'Reported' }
        Status = if ($instEval.Applicable -and $instEval.Passed) { 'Passed' } else { 'Failed' }
    }
}

# Verify Repository Aggregate (50% instruction floor)
$repoEval = $null
if ($countersData -and $countersData.repository -and $repositoryCountersValid) {
    $repoCov = $countersData.repository.instruction.covered
    $repoMis = $countersData.repository.instruction.missed
    $repoEval = Test-ExactFloorRatio -Covered $repoCov -Missed $repoMis -MinimumPercent $policy.repository_instruction_min_percent
    if (-not $repoEval.Passed) {
        $floorPassed = $false
        $reasons += "Repository instruction aggregate $($repoEval.Reason)"
    }
} else {
    $floorPassed = $false
    if (-not $countersData -or -not $countersData.repository) { $reasons += "Repository aggregate counter data missing" }
}

# Regression Check (if ComparisonBundle supplied)
$regressionPassed = $true
$regressionStatus = "NotApplicable"
$comparisonCompatibility = "Missing"

if ($manifest.purpose -eq "Comparison" -or $manifest.purpose -eq "Candidate") {
    if (-not $ComparisonBundle) {
        $regressionPassed = $false
        $regressionStatus = "Failed"
        $comparisonCompatibility = "Missing"
        $reasons += "Baseline comparison bundle is required for purpose '$($manifest.purpose)' but was not provided"
    }
}

if ($ComparisonBundle) {
    if (-not (Test-Path $ComparisonBundle)) {
        Write-CoverageError "ComparisonBundle directory not found: $ComparisonBundle"
        exit 2
    }

    $compManifestPath = Join-Path $ComparisonBundle "manifest.json"
    $compManifest = if (Test-Path $compManifestPath) { Read-SafeJson -Path $compManifestPath } else { $null }

    if (-not $AcceptedBaselineIndexPath -or -not (Test-Path -LiteralPath $AcceptedBaselineIndexPath -PathType Leaf)) {
        $comparisonCompatibility = "Incompatible"
        $regressionPassed = $false
        $reasons += 'Comparison requires the trusted accepted-baseline index file; an arbitrary caller-supplied bundle is insufficient'
    } else {
        try {
            $acceptedIndex = Read-SafeJson -Path $AcceptedBaselineIndexPath
            $actualBaselineDigest = Get-CoverageBundleDigest -BundlePath $ComparisonBundle
            $indexCheck = Test-AcceptedBaselineIndex -Index $acceptedIndex -ExpectedTargetCommit ([string]$manifest.git.target_commit) -ActualBundleDigest $actualBaselineDigest
            $indexParent = Split-Path -Parent ([System.IO.Path]::GetFullPath($AcceptedBaselineIndexPath))
            $evidenceRoot = Split-Path -Parent $indexParent
            $canonicalIndexPath = [System.IO.Path]::GetFullPath($AcceptedBaselineIndexPath)
            $expectedIndexPath = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $evidenceRoot 'baselines') 'current.json'))
            $indexedBundle = if ($acceptedIndex.bundle_path -and -not [System.IO.Path]::IsPathRooted([string]$acceptedIndex.bundle_path) -and [string]$acceptedIndex.bundle_path -notmatch '(^|[/\\])\.\.([/\\]|$)') {
                [System.IO.Path]::GetFullPath((Join-Path $evidenceRoot ([string]$acceptedIndex.bundle_path)))
            } else { '' }
            if (-not $indexCheck.IsValid -or $canonicalIndexPath -ne $expectedIndexPath -or [string]::IsNullOrWhiteSpace($indexedBundle) -or $indexedBundle -ne [System.IO.Path]::GetFullPath($ComparisonBundle) -or ($compManifest -and $acceptedIndex.run_id -ne $compManifest.run_id)) {
                $comparisonCompatibility = 'Incompatible'
                $regressionPassed = $false
                $reasons += @($indexCheck.Errors)
                $reasons += 'Accepted baseline index is not the evidence-root baselines/current.json record for this exact bundle path and run'
            }
        } catch {
            $comparisonCompatibility = 'Incompatible'
            $regressionPassed = $false
            $reasons += "Accepted baseline index is invalid: $_"
        }
    }

    if (-not $compManifest) {
        $comparisonCompatibility = "Incompatible"
        $regressionPassed = $false
        $reasons += "Comparison baseline manifest is missing"
    } else {
        $baselineValidation = Test-TrustedComparisonManifest -CandidateManifest $manifest -BaselineManifest $compManifest -AcceptedBaselineIndex $acceptedIndex -Policy $policy
        if (-not $baselineValidation.IsValid) {
            $comparisonCompatibility = "Incompatible"
            $regressionPassed = $false
            $reasons += $baselineValidation.Errors
        }
    }

    $compCountersPath = Join-Path $ComparisonBundle "counters.json"
    if (-not (Test-Path $compCountersPath)) {
        $comparisonCompatibility = "Incompatible"
        $regressionPassed = $false
        $reasons += "Baseline counters.json is missing: $compCountersPath"
        $compCounters = $null
    } else {
        try {
            $compCounters = Read-SafeJson -Path $compCountersPath
        } catch {
            $comparisonCompatibility = "Incompatible"
            $regressionPassed = $false
            $reasons += "Baseline counters.json is malformed: $_"
            $compCounters = $null
        }
    }

    if (-not $compCounters -or -not $compCounters.analyzer_identity -or -not $countersData.analyzer_identity -or $compCounters.analyzer_identity -ne $countersData.analyzer_identity) {
        $comparisonCompatibility = "Incompatible"
        $regressionPassed = $false
        $reasons += "Baseline and candidate analyzer identities must be present and equal"
    }

    if ($compCounters -and $compManifest) {
        $baselineEvidence = Test-ComparisonBundleEvidence -BundlePath $ComparisonBundle -Manifest $compManifest -Policy $policy -TrustedHarnessRoot $trustedHarnessRoot
        if (-not $baselineEvidence.IsValid) {
            $comparisonCompatibility = "Incompatible"
            $regressionPassed = $false
            $reasons += $baselineEvidence.Errors
        } else {
            $compCounters = $baselineEvidence.Counters
        }
    }

    if ($comparisonCompatibility -eq "Missing") {
        $comparisonCompatibility = "Compatible"
    }

    # Derive affected Tier 1 modules based on the verified changed-path set.
    $affectedTier1 = Get-AffectedTier1Modules -Policy $policy -ChangedPaths $selectionChangedPaths -ChangedPathsProvided $selectionChangedPathsProvided

    $regressionStatus = if ($regressionPassed) { "Passed" } else { "Failed" }

    # Evaluate regression on affected Tier 1 modules
    foreach ($mod in $affectedTier1) {
        $cMod = $countersData.modules | Where-Object { $_.module_path -eq $mod }
        $bMod = $compCounters.modules | Where-Object { $_.module_path -eq $mod }
        if (-not $cMod) {
            $regressionPassed = $false
            $reasons += "${mod}: affected module missing in candidate counters"
            continue
        }
        if (-not $bMod) {
            $regressionPassed = $false
            $comparisonCompatibility = "Incompatible"
            $reasons += "${mod}: affected module is missing from baseline counters; only an explicit bound zero-scope record can establish first applicability"
            continue
        }

        $regInst = Test-ExactRegressionRatio -CandidateCovered $cMod.instruction.covered -CandidateMissed $cMod.instruction.missed -BaselineCovered $bMod.instruction.covered -BaselineMissed $bMod.instruction.missed
        if ($regInst.Applicable -and -not $regInst.Passed) {
            $regressionPassed = $false
            $reasons += "${mod}: instruction $($regInst.Reason)"
        }
        $regBranch = Test-ExactRegressionRatio -CandidateCovered $cMod.branch.covered -CandidateMissed $cMod.branch.missed -BaselineCovered $bMod.branch.covered -BaselineMissed $bMod.branch.missed
        if ($regBranch.Applicable -and -not $regBranch.Passed) {
            $regressionPassed = $false
            $reasons += "${mod}: branch $($regBranch.Reason)"
        }
    }

    if (-not $regressionPassed) {
        $regressionStatus = "Failed"
    }
}

# Determine overall decisions
$finalFloorsStatus = if ($floorPassed) { "Passed" } else { "Failed" }
$isCollectionComplete = ($finalCollectionStatus -eq "Complete")
$isTestsPassed = ($finalTestsStatus -eq "Passed")
$isRegressionPassed = ($regressionStatus -eq "Passed" -or $regressionStatus -eq "NotApplicable")

# Reject incomplete run even if counters exceed floors
$overallSuccess = ($finalFloorsStatus -eq "Passed" -and $isCollectionComplete -and $isTestsPassed -and $isRegressionPassed)

$manifestDigest = Get-FileSha256 -Path $manifestPath

$decisionObject = [PSCustomObject]@{
    run_id = $manifest.run_id
    policy_id = $policy.policy_id
    manifest_digest = $manifestDigest
    evaluated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    decisions = [PSCustomObject]@{
        collection = $finalCollectionStatus
        tests = $finalTestsStatus
        floors = $finalFloorsStatus
        comparison_compatibility = $comparisonCompatibility
        regression = $regressionStatus
        publication = if ($manifest.decisions -and $manifest.decisions.publication) { $manifest.decisions.publication } else { "Pending" }
        integration = if ($overallSuccess) { "Unverified" } else { "Failed" }
    }
    overall_passed = $overallSuccess
    unmet_reasons = $reasons
    modules = $moduleDecisions
    repository = if ($repoEval) {
        [PSCustomObject]@{
            InstructionPercent = $repoEval.DisplayPercent
            Passed = $repoEval.Passed
            Reason = $repoEval.Reason
        }
    } else { $null }
}

# Write decision.json
$decisionObject | ConvertTo-Json -Depth 10 | Set-Content -Path $decisionJsonPath -Encoding utf8

# Write decision.md
$decisionMdPath = Join-Path $targetOutDir "decision.md"
$mdContent = @"
# Coverage Verification Decision

- **Run ID**: $($manifest.run_id)
- **Manifest Digest**: $manifestDigest
- **Policy**: $($policy.policy_id)
- **Evaluated**: $($decisionObject.evaluated_at)
- **Overall Result**: $(if ($overallSuccess) { "✓ PASS" } else { "✗ FAIL" })

## Decisions
| Dimension | Status |
| --- | --- |
| Collection | $($decisionObject.decisions.collection) |
| Tests | $($decisionObject.decisions.tests) |
| Floors | $($decisionObject.decisions.floors) |
| Compatibility | $($decisionObject.decisions.comparison_compatibility) |
| Regression | $($decisionObject.decisions.regression) |
| Publication | $($decisionObject.decisions.publication) |
| Integration | $($decisionObject.decisions.integration) |

## Module Results
$($moduleDecisions | ForEach-Object { "- **$($_.Module)** (Tier $($_.Tier)): $($_.Status)" } | Out-String)

$(if ($reasons.Count -gt 0) { "## Unmet Reasons`n" + ($reasons | ForEach-Object { "- $_" } | Out-String) })
"@
Set-Content -Path $decisionMdPath -Value $mdContent -Encoding utf8

Write-Host "Verification complete. Decision written to $decisionJsonPath and $decisionMdPath" -ForegroundColor $(if ($overallSuccess) { "Green" } else { "Yellow" })

if (-not $overallSuccess) {
    exit 1
}
exit 0
