<#
.SYNOPSIS
    Shared coverage utility functions, schema validators, exact ratio arithmetic,
    and canonical zero-counter proof normalization.
.DESCRIPTION
    Provides deterministic functions for XML/JSON parsing, schema validation,
    canonical zero-counter proof verification (AnalyzedZeroProofV1),
    BigInteger ratio evaluation, TRD evidence key enforcement, and decision state tracking.
#>

# Standard Exit Codes
$global:EXIT_SUCCESS = 0
$global:EXIT_VERIFICATION_FAILURE = 1
$global:EXIT_PREREQUISITE_MISSING = 2
$global:EXIT_INVALID_CONFIG = 3

$global:COVERAGE_TOOLS_DIR = $PSScriptRoot

# Exact Ten TRD Evidence Keys
$global:EXACT_TRD_KEYS = @(
    "requirement",
    "scope",
    "revision",
    "named_procedure",
    "environment",
    "expected_observed_result",
    "date",
    "limitations",
    "status",
    "artifact_link"
)

# Supported Schema Versions and Purposes
$global:SUPPORTED_SCHEMA_VERSIONS = @("1.0")
$global:SUPPORTED_PURPOSES = @("Initial", "Comparison", "Candidate")

# Decision Dimension State Taxonomy
$global:DECISION_STATES = @{
    collection = @("Started", "Complete", "Incomplete")
    tests = @("Passed", "Failed", "Unverified")
    floors = @("Passed", "Failed", "Unverified")
    comparison_compatibility = @("Compatible", "Incompatible", "Missing")
    regression = @("Passed", "Failed", "Unverified", "NotApplicable")
    publication = @("Published", "Failed", "Pending")
    integration = @("Passed", "Failed", "Unverified")
}

function Read-SafeXml {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -Path $Path)) {
        throw "XML file not found: $Path"
    }

    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
    $settings.XmlResolver = $null

    $fileStream = [System.IO.FileStream]::new($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $reader = [System.Xml.XmlReader]::Create($fileStream, $settings)
        try {
            $doc = [System.Xml.XmlDocument]::new()
            $doc.Load($reader)
            return $doc
        } finally {
            $reader.Close()
        }
    } finally {
        $fileStream.Close()
    }
}

function Read-SafeJson {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -Path $Path)) {
        throw "JSON file not found: $Path"
    }

    $raw = Get-Content -Path $Path -Raw
    try {
        return $raw | ConvertFrom-Json -Depth 100 -ErrorAction Stop
    } catch {
        return $raw | ConvertFrom-Json
    }
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path)) {
        throw "File not found for hashing: $Path"
    }
    return (Get-FileHash -Path $Path -Algorithm SHA256).Hash.ToLower()
}

function Test-ExactFloorRatio {
    param(
        [Parameter(Mandatory = $true)]$Covered,
        [Parameter(Mandatory = $true)]$Missed,
        [Parameter(Mandatory = $true)][int]$MinimumPercent
    )

    $cov = [System.Numerics.BigInteger]::Parse($Covered.ToString())
    $mis = [System.Numerics.BigInteger]::Parse($Missed.ToString())
    $tot = $cov + $mis

    if ($tot -eq [System.Numerics.BigInteger]::Zero) {
        return @{
            Applicable = $false
            Passed = $true
            Total = 0
            DisplayPercent = "N/A"
            Reason = "Denominator is zero"
        }
    }

    $lhs = [System.Numerics.BigInteger]::Multiply([System.Numerics.BigInteger]100, $cov)
    $rhs = [System.Numerics.BigInteger]::Multiply([System.Numerics.BigInteger]$MinimumPercent, $tot)

    $passed = ($lhs -ge $rhs)
    $display = [Math]::Round(([double]$cov / [double]$tot) * 100, 3)

    return @{
        Applicable = $true
        Passed = $passed
        Covered = [int64]$cov
        Total = [int64]$tot
        DisplayPercent = $display
        Reason = if ($passed) { "Meets floor $MinimumPercent%" } else { "Coverage $display% is below floor $MinimumPercent%" }
    }
}

function Test-ExactRegressionRatio {
    param(
        [Parameter(Mandatory = $true)]$CandidateCovered,
        [Parameter(Mandatory = $true)]$CandidateMissed,
        [Parameter(Mandatory = $true)]$BaselineCovered,
        [Parameter(Mandatory = $true)]$BaselineMissed
    )

    $cCov = [System.Numerics.BigInteger]::Parse($CandidateCovered.ToString())
    $cMis = [System.Numerics.BigInteger]::Parse($CandidateMissed.ToString())
    $cTot = $cCov + $cMis

    $bCov = [System.Numerics.BigInteger]::Parse($BaselineCovered.ToString())
    $bMis = [System.Numerics.BigInteger]::Parse($BaselineMissed.ToString())
    $bTot = $bCov + $bMis

    if ($cTot -eq [System.Numerics.BigInteger]::Zero -and $bTot -eq [System.Numerics.BigInteger]::Zero) {
        return @{
            Applicable = $false
            Passed = $true
            Reason = "Both candidate and baseline denominators are zero"
        }
    }

    if ($bTot -eq [System.Numerics.BigInteger]::Zero) {
        return @{
            Applicable = $true
            Passed = $true
            Reason = "First applicable baseline (baseline was empty/zero)"
        }
    }

    if ($cTot -eq [System.Numerics.BigInteger]::Zero) {
        return @{
            Applicable = $true
            Passed = $false
            Reason = "Candidate has zero denominator while baseline had positive denominator"
        }
    }

    $lhs = [System.Numerics.BigInteger]::Multiply($cCov, $bTot)
    $rhs = [System.Numerics.BigInteger]::Multiply($bCov, $cTot)

    $passed = ($lhs -ge $rhs)
    $cDisplay = [Math]::Round(([double]$cCov / [double]$cTot) * 100, 3)
    $bDisplay = [Math]::Round(([double]$bCov / [double]$bTot) * 100, 3)

    return @{
        Applicable = $true
        Passed = $passed
        CandidatePercent = $cDisplay
        BaselinePercent = $bDisplay
        Reason = if ($passed) { "Non-decreasing ratio: $cDisplay% >= $bDisplay%" } else { "Regression detected: $cDisplay% < $bDisplay%" }
    }
}

function Test-CoverageFloors {
    param(
        [Parameter(Mandatory = $true)]$Counters,
        [Parameter(Mandatory = $true)]$Policy
    )

    $reasons = @()
    $allPassed = $true

    $tier = if ($Counters.tier) { [int]$Counters.tier } else { 1 }
    $minInst = if ($tier -eq 1) { [int]$Policy.tier1_instruction_min_percent } else { [int]$Policy.tier2_instruction_min_percent }
    $minBranch = if ($tier -eq 1) { [int]$Policy.tier1_branch_min_percent } else { 0 }

    # Check instruction
    if ($Counters.instruction) {
        $instRes = Test-ExactFloorRatio -Covered $Counters.instruction.covered -Missed $Counters.instruction.missed -MinimumPercent $minInst
        if (-not $instRes.Passed) {
            $allPassed = $false
            $reasons += "$($Counters.module_path): instruction $($instRes.Reason)"
        }
    }

    # Check branch
    if ($Counters.branch -and $tier -eq 1) {
        $branchRes = Test-ExactFloorRatio -Covered $Counters.branch.covered -Missed $Counters.branch.missed -MinimumPercent $minBranch
        if ($branchRes.Applicable -and -not $branchRes.Passed) {
            $allPassed = $false
            $reasons += "$($Counters.module_path): branch $($branchRes.Reason)"
        }
    }

    return @{
        Passed = $allPassed
        Reasons = $reasons
    }
}

function Test-InventoryAccounting {
    param(
        $Inventory,
        $XmlData,
        $CanonicalProof,
        $Case
    )

    $reasons = @()
    $valid = $true

    # 1. Duplicate classes in inventory
    if ($Inventory -and $Inventory.modules) {
        foreach ($mod in $Inventory.modules) {
            if ($mod.classes) {
                $seen = @{}
                foreach ($cls in $mod.classes) {
                    if ($seen.ContainsKey($cls)) {
                        $reasons += "Duplicate class detected in inventory: $cls"
                        $valid = $false
                    } else {
                        $seen[$cls] = $true
                    }
                }
            }
        }
    }

    # 2. XML Counter & Zero-Proof validation
    if ($XmlData -or ($Case -and $Case.xml_file)) {
        $scriptDir = $global:COVERAGE_TOOLS_DIR
        $xmlRel = if ($Case -and $Case.xml_file) { $Case.xml_file } else { $XmlData }
        $xmlPath = Join-Path (Join-Path $scriptDir "fixtures") $xmlRel
        if (-not (Test-Path $xmlPath)) {
            $xmlPath = Join-Path $scriptDir $xmlRel
        }

        if (Test-Path $xmlPath) {
            $xmlDoc = Read-SafeXml -Path $xmlPath
            $actualHash = Get-FileSha256 -Path $xmlPath

            # Extract report level counters
            $presentTypes = @()
            if ($xmlDoc.report) {
                foreach ($node in $xmlDoc.report.ChildNodes) {
                    if ($node.Name -eq "counter") {
                        $presentTypes += $node.GetAttribute("type")
                    }
                }
            }

            # Check omitted counters: INSTRUCTION, BRANCH
            $expectedTypes = @("INSTRUCTION", "BRANCH")
            $proof = $null
            if ($CanonicalProof) {
                $proof = $CanonicalProof
            } elseif ($Case.proof) {
                $proof = $Case.proof
            } elseif ($Case.proof_file) {
                $proofPath = Join-Path (Join-Path $scriptDir "fixtures") $Case.proof_file
                if (-not (Test-Path $proofPath)) {
                    $proofPath = Join-Path $scriptDir $Case.proof_file
                }
                if (Test-Path $proofPath) {
                    $proof = Read-SafeJson -Path $proofPath
                }
            }

            foreach ($t in $expectedTypes) {
                if ($t -notin $presentTypes) {
                    # Omitted counter in XML!
                    if ($null -eq $proof) {
                        $reasons += "Omitted XML counter for $t without canonical zero proof"
                        $valid = $false
                        continue
                    }

                    if ($proof.normalization_rule -ne "AnalyzedZeroProofV1") {
                        $reasons += "Unsupported zero count proof rule: $($proof.normalization_rule)"
                        $valid = $false
                        continue
                    }

                    if ($proof.report_digest -ne $actualHash) {
                        $reasons += "Report digest mismatch in zero count proof"
                        $valid = $false
                        continue
                    }

                    $proofCounter = $proof.counters.$t
                    if ($null -eq $proofCounter) {
                        $reasons += "Canonical proof missing counter for $t"
                        $valid = $false
                    } elseif ($proofCounter.total -gt 0) {
                        $reasons += "Contradictory zero proof: canonical total $($proofCounter.total) is nonzero for omitted XML counter"
                        $valid = $false
                    }
                }
            }
        }
    }

    if ($Case -and $Case.canonical_binding) {
        $bindingCase = $Case.canonical_binding
        $binding = Test-CanonicalInventoryModuleBinding -InventoryModule $bindingCase.inventory_module -CounterModule $bindingCase.counter_module -InventoryDigest $bindingCase.inventory_digest -ExpectedInventoryDigest $bindingCase.expected_inventory_digest -AnalyzerIdentity $bindingCase.analyzer_identity -ReportDigest $bindingCase.report_digest
        if (-not $binding.IsValid) {
            $valid = $false
            $reasons += $binding.Reasons
        }
        if ($bindingCase.proof) {
            $proofBinding = Test-CanonicalZeroProofBinding -Proof $bindingCase.proof -InventoryModule $bindingCase.inventory_module -CounterModule $bindingCase.counter_module -InventoryDigest $bindingCase.inventory_digest -ExpectedInventoryDigest $bindingCase.expected_inventory_digest -AnalyzerIdentity $bindingCase.analyzer_identity -ReportDigest $bindingCase.report_digest
            if (-not $proofBinding.IsValid) {
                $valid = $false
                $reasons += $proofBinding.Reasons
            }
        }
    }

    return @{
        IsValid = $valid
        Reasons = $reasons
    }
}

function Get-ProductionClassInventoryDigest {
    param([Parameter(Mandatory = $true)]$InventoryModule)

    $reasons = @()
    $entries = @($InventoryModule.retained_classes)
    $classIds = @($entries | ForEach-Object { [string]$_.class_id })
    $uniqueClassIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($classId in $classIds) {
        if (-not $uniqueClassIds.Add($classId)) {
            $reasons += 'Duplicate retained class identities'
            break
        }
    }
    if ($reasons.Count -gt 0) {
        return @{ IsValid = $false; Digest = ''; Reasons = $reasons }
    }
    foreach ($entry in $entries) {
        if (-not $entry.class_id -or $entry.class_id -match '(^|/)\.\.(/|$)' -or $entry.sha256 -notmatch '^[0-9a-f]{64}$') {
            $reasons += 'Malformed retained class inventory entry'
        }
    }
    if ($reasons.Count -gt 0) { return @{ IsValid = $false; Digest = ''; Reasons = $reasons } }

    $canonicalEntries = @($entries | ForEach-Object { "$($_.class_id):$($_.sha256)" })
    [Array]::Sort($canonicalEntries, [System.StringComparer]::Ordinal)
    $canonical = [string]::Join("`n", $canonicalEntries)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $sha256.ComputeHash([System.Text.UTF8Encoding]::new($false).GetBytes($canonical))
        $digest = [System.Convert]::ToHexString($bytes).ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
    return @{ IsValid = $true; Digest = $digest; Reasons = @() }
}

function Test-CanonicalInventoryModuleBinding {
    param(
        [Parameter(Mandatory = $true)]$InventoryModule,
        [Parameter(Mandatory = $true)]$CounterModule,
        [Parameter(Mandatory = $true)][string]$InventoryDigest,
        [Parameter(Mandatory = $true)][string]$ExpectedInventoryDigest,
        [Parameter(Mandatory = $true)][string]$AnalyzerIdentity,
        [Parameter(Mandatory = $true)][string]$ReportDigest
    )

    $reasons = @()
    $classDigest = Get-ProductionClassInventoryDigest -InventoryModule $InventoryModule
    if (-not $classDigest.IsValid) { $reasons += $classDigest.Reasons }
    if ($InventoryDigest -ne $ExpectedInventoryDigest) { $reasons += 'Inventory file digest mismatch' }
    if ($InventoryModule.class_inventory_digest -ne $classDigest.Digest) { $reasons += 'Retained class digest mismatch' }
    if ($CounterModule.class_inventory_digest -ne $InventoryModule.class_inventory_digest) { $reasons += 'Counter class inventory digest mismatch' }
    if ($CounterModule.report_digest -ne $ReportDigest -or -not $ReportDigest) { $reasons += 'Counter report digest mismatch' }
    if ($AnalyzerIdentity -ne 'jacoco-0.8.15') { $reasons += 'Unexpected analyzer identity' }
    return @{ IsValid = ($reasons.Count -eq 0); Reasons = $reasons }
}

function Test-CanonicalZeroProofBinding {
    param(
        [Parameter(Mandatory = $true)]$Proof,
        [Parameter(Mandatory = $true)]$InventoryModule,
        [Parameter(Mandatory = $true)]$CounterModule,
        [Parameter(Mandatory = $true)][string]$InventoryDigest,
        [Parameter(Mandatory = $true)][string]$ExpectedInventoryDigest,
        [Parameter(Mandatory = $true)][string]$AnalyzerIdentity,
        [Parameter(Mandatory = $true)][string]$ReportDigest
    )

    $reasons = @()
    $moduleBinding = Test-CanonicalInventoryModuleBinding -InventoryModule $InventoryModule -CounterModule $CounterModule -InventoryDigest $InventoryDigest -ExpectedInventoryDigest $ExpectedInventoryDigest -AnalyzerIdentity $AnalyzerIdentity -ReportDigest $ReportDigest
    $reasons += $moduleBinding.Reasons
    if ($Proof.normalization_rule -ne 'AnalyzedZeroProofV1') { $reasons += 'Unsupported zero proof rule' }
    if ($Proof.analyzer_identity -ne $AnalyzerIdentity) { $reasons += 'Zero proof analyzer identity mismatch' }
    if ($Proof.inventory_digest -ne $InventoryDigest) { $reasons += 'Zero proof inventory digest mismatch' }
    if ($Proof.class_inventory_digest -ne $InventoryModule.class_inventory_digest) { $reasons += 'Zero proof class inventory digest mismatch' }
    if ($Proof.report_digest -ne $ReportDigest -or -not $ReportDigest) { $reasons += 'Zero proof report digest mismatch' }
    return @{ IsValid = ($reasons.Count -eq 0); Reasons = $reasons }
}

function Test-PolicyConfiguration {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        $Arguments
    )

    $reasons = @()
    $valid = $true

    if (-not $Policy.policy_id -or $Policy.policy_id -ne "chimali-coverage-v1") {
        $reasons += "Invalid or missing policy_id"
        $valid = $false
    }

    if ($Policy.tier1_instruction_min_percent -ne 60 -or $Policy.tier1_branch_min_percent -ne 60) {
        $reasons += "Tier 1 thresholds must be 60/60"
        $valid = $false
    }

    if ($Policy.repository_instruction_min_percent -ne 50) {
        $reasons += "Repository instruction threshold must be 50"
        $valid = $false
    }

    $expectedTier1 = @(':core:security', ':core:domain', ':core:data', ':feature:fido2', ':feature:authenticator')
    $actualTier1 = @($Policy.tier1_modules)

    $diff1 = $expectedTier1 | Where-Object { $_ -notin $actualTier1 }
    $diff2 = $actualTier1 | Where-Object { $_ -notin $expectedTier1 }
    if ($diff1 -or $diff2 -or $actualTier1.Count -ne 5) {
        $reasons += "Tier 1 modules must be exactly the five classified paths"
        $valid = $false
    }

    if ($Arguments) {
        $forbiddenArgs = @("SkipCoverage", "ForcePass", "SkipLint", "SkipTests", "LowerFloors")
        foreach ($arg in $forbiddenArgs) {
            if ($Arguments.PSObject.Properties[$arg]) {
                $reasons += "Prohibited or unknown argument: $arg"
                $valid = $false
            }
        }

        if ($Arguments.OutputRoot) {
            $out = $Arguments.OutputRoot.ToString().Replace("\", "/")
            if ($out -match "^(core/|feature/|app/|src/)") {
                $reasons += "OutputRoot cannot be located inside measured source tree"
                $valid = $false
            }
        }
    }

    return @{
        IsValid = $valid
        Reasons = $reasons
    }
}

function Test-ManifestSchema {
    param([Parameter(Mandatory = $true)]$Manifest)

    $errors = @()

    if (-not $Manifest.schema_version -or $Manifest.schema_version -notin $global:SUPPORTED_SCHEMA_VERSIONS) {
        $errors += "Unsupported schema version: $($Manifest.schema_version)"
    }

    if (-not $Manifest.purpose -or $Manifest.purpose -notin $global:SUPPORTED_PURPOSES) {
        $errors += "Invalid purpose: $($Manifest.purpose)"
    }

    if (-not $Manifest.run_id -or ($Manifest.run_id -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')) {
        $errors += "Invalid or missing run_id: $($Manifest.run_id)"
    }

    if ($Manifest.decisions) {
        foreach ($dim in $global:DECISION_STATES.Keys) {
            $val = $Manifest.decisions.$dim
            if ($val -and ($val -notin $global:DECISION_STATES[$dim])) {
                $errors += "Invalid $dim decision status: $val"
            }
        }
    }

    if ($Manifest.evidence_records) {
        foreach ($rec in $Manifest.evidence_records) {
            foreach ($key in $global:EXACT_TRD_KEYS) {
                if ($null -eq $rec.$key -or $rec.$key -eq "") {
                    $errors += "Missing required TRD evidence key: $key"
                }
            }
            if ($rec.artifact_link) {
                $link = $rec.artifact_link.ToString()
                if ($link -match "\.\." -or [System.IO.Path]::IsPathRooted($link)) {
                    $errors += "Malformed or escaping artifact path: $link"
                }
            }
        }
    }

    if ($Manifest.artifacts) {
        foreach ($art in $Manifest.artifacts) {
            if (-not $art.path) {
                $errors += "Artifact entry missing path"
            } elseif ($art.path -match "\.\." -or [System.IO.Path]::IsPathRooted($art.path)) {
                $errors += "Malformed or escaping artifact path: $($art.path)"
            }
        }
    }

    if ($Manifest.suites) {
        foreach ($s in $Manifest.suites) {
            if (-not $s.suite_name) {
                $errors += "Suite result missing suite_name"
            }
            if ($s.tests -and [int]$s.tests -lt 0) {
                $errors += "Suite $($s.suite_name) has negative tests count"
            }
        }
    }

    if ($Manifest.module_results) {
        $modulePaths = @()
        foreach ($m in $Manifest.module_results) {
            if ($m.module_path) {
                if ($m.module_path -in $modulePaths) {
                    $errors += "Duplicate module entry: $($m.module_path)"
                } else {
                    $modulePaths += $m.module_path
                }
            }
        }
    }

    if ($Manifest.zero_count_proofs) {
        foreach ($proof in $Manifest.zero_count_proofs) {
            if ($proof.normalization_rule -ne "AnalyzedZeroProofV1") {
                $errors += "Unsupported zero count proof rule: $($proof.normalization_rule)"
            }
        }
    }

    if ($Manifest.trusted_reporter_binding) {
        $binding = $Manifest.trusted_reporter_binding
        $reqBindingKeys = @("workflow_commit", "reporter_sha", "repository_id", "app_id")
        foreach ($bKey in $reqBindingKeys) {
            if ($null -eq $binding.$bKey -or $binding.$bKey -eq "") {
                $errors += "Missing required field in TrustedReporterBinding: $bKey"
            }
        }
    }

    return @{
        IsValid = ($errors.Count -eq 0)
        Errors = $errors
    }
}

function Test-CapturePreflight {
    param(
        [Parameter(Mandatory = $true)]$Preflight
    )

    $errors = @()

    if ($Preflight.clean_tree -eq $false) {
        $errors += "Working tree is dirty; clean tree required for coverage capture"
    }

    if ($Preflight.expected_head -and $Preflight.actual_head -and ($Preflight.expected_head -ne $Preflight.actual_head)) {
        $errors += "SourceRevision does not match current repository HEAD"
    }

    if ($Preflight.directory_exists -eq $true) {
        $errors += "Run directory already exists; cannot overwrite existing run bundle"
    }

    if ($Preflight.purpose -and $Preflight.purpose -ne "Initial" -and [string]::IsNullOrEmpty($Preflight.target_revision)) {
        $errors += "TargetRevision is required when Purpose is not Initial"
    }

    if ($Preflight.device_serial -and $Preflight.device_state -and $Preflight.device_state -ne "device") {
        $errors += "Specified device $($Preflight.device_serial) is not online and authorized"
    }

    return @{
        IsValid = ($errors.Count -eq 0)
        Errors = $errors
    }
}

function Test-SuiteOutcome {
    param(
        [Parameter(Mandatory = $true)]$SuiteResult
    )

    $reasons = @()
    $status = "Passed"
    $executed = [int]$SuiteResult.tests - [int]$SuiteResult.skipped
    if ($executed -lt 0) { $executed = 0 }

    if ($SuiteResult.required) {
        if ([int]$SuiteResult.tests -eq 0 -or $executed -eq 0) {
            $status = "Failed"
            $reasons += "Required suite produced zero actual executions"
        }
        if ([int]$SuiteResult.failures -gt 0) {
            $status = "Failed"
            $reasons += "Required suite has test failures: $($SuiteResult.failures)"
        }
        if ([int]$SuiteResult.errors -gt 0) {
            $status = "Failed"
            $reasons += "Required suite has test errors: $($SuiteResult.errors)"
        }
        if ([int]$SuiteResult.skipped -gt 0) {
            $status = "Failed"
            $reasons += "Required suite has skipped testcases: $($SuiteResult.skipped) skipped"
        }
    } else {
        if ([int]$SuiteResult.failures -gt 0 -or [int]$SuiteResult.errors -gt 0) {
            $status = "Failed"
            $reasons += "Optional suite failed with errors or failures"
        }
    }

    return @{
        Status = $status
        Executed = $executed
        Reasons = $reasons
        Passed = ($status -eq "Passed")
    }
}

function Test-CollectionState {
    param(
        [Parameter(Mandatory = $true)]$Run
    )

    $reasons = @()
    $status = "Complete"
    $baselineEligible = $false

    if (-not $Run.execution_files_present -or ($Run.PSObject.Properties['reports_generated'] -and -not $Run.reports_generated)) {
        $status = "Incomplete"
        $reasons += "Missing or empty current-run JaCoCo execution data"
    }

    if ($Run.PSObject.Properties['probe_compatible'] -and -not $Run.probe_compatible) {
        $status = "Incomplete"
        $reasons += "Incompatible execution probe variant: expected $($Run.class_variant)"
    }

    if ($status -eq "Complete" -and $Run.tests_passed) {
        # Valid comparison baseline requires complete collection, passed required tests, compatible measurement
        $baselineEligible = $true
    }

    return @{
        CollectionStatus = $status
        BaselineEligible = $baselineEligible
        Reasons = $reasons
    }
}

function Test-IntegrationInvocation {
    param(
        [Parameter(Mandatory = $true)]$Invocation
    )

    $errors = @()

    if ($Invocation.arguments) {
        $forbidden = @("SkipTests", "SkipCoverage", "SkipLint", "LowerFloors")
        foreach ($arg in $forbidden) {
            if ($Invocation.arguments.PSObject.Properties[$arg]) {
                $errors += "Prohibited or unknown argument: $arg"
            }
        }
    }

    if ($Invocation.remote_target_sha -and $Invocation.resolved_target_sha) {
        if ($Invocation.remote_target_sha -ne $Invocation.resolved_target_sha) {
            $errors += "TargetRef resolved to stale SHA; does not match current remote target"
        }
    }

    if ($Invocation.PSObject.Properties['is_ancestor'] -and -not $Invocation.is_ancestor) {
        $errors += "Target commit is not an ancestor of candidate"
    }

    if ($Invocation.pushed_ref_sha -and $Invocation.assessed_candidate_sha) {
        if ($Invocation.pushed_ref_sha -ne $Invocation.assessed_candidate_sha) {
            $errors += "Pushed commit does not match assessed coverage candidate"
        }
    }

    $intStatus = if ($Invocation.hosted_check_present) { "Passed" } else { "Unverified" }

    return @{
        IsValid = ($errors.Count -eq 0)
        IntegrationStatus = $intStatus
        Errors = $errors
    }
}

function Test-ComparisonCase {
    param(
        [Parameter(Mandatory = $true)]$Case
    )

    $errors = @()
    $validBaseline = $true
    $compatible = $true
    $ratioPassed = $true

    if ($Case.baseline) {
        if ($Case.baseline.PSObject.Properties['tests_passed'] -and -not $Case.baseline.tests_passed) {
            $validBaseline = $false
            $errors += "Target run has failing tests; ineligible as comparison baseline"
        }

        if ($Case.baseline.measurement_fingerprint -and $Case.candidate.measurement_fingerprint) {
            if ($Case.baseline.measurement_fingerprint -ne $Case.candidate.measurement_fingerprint) {
                $compatible = $false
                $errors += "Measurement compatibility fingerprint mismatch"
            }
        }

        if ($Case.baseline.instruction -and $Case.candidate.instruction) {
            $reg = Test-ExactRegressionRatio -CandidateCovered $Case.candidate.instruction.covered -CandidateMissed $Case.candidate.instruction.missed -BaselineCovered $Case.baseline.instruction.covered -BaselineMissed $Case.baseline.instruction.missed
            if ($reg.Applicable -and -not $reg.Passed) {
                $ratioPassed = $false
                $errors += $reg.Reason
            }
        }
    }

    $allPassed = ($errors.Count -eq 0)

    return @{
        Passed = $allPassed
        BaselineValid = $validBaseline
        Compatible = $compatible
        Errors = $errors
    }
}

function Get-RemoteRefCommit {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Output,
        [Parameter(Mandatory = $true)][string]$ExpectedRef
    )

    foreach ($line in $Output) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line.Trim() -split '\s+'
        if ($parts.Count -eq 2 -and $parts[1] -ceq $ExpectedRef -and $parts[0] -match '^[0-9a-fA-F]{40}$') {
            return $parts[0].ToLowerInvariant()
        }
    }
    return $null
}

function Test-PublicationCase {
    param(
        [Parameter(Mandatory = $true)]$Case
    )

    $errors = @()

    if ($Case.publication) {
        if ($Case.publication.expected_digest -and $Case.publication.actual_digest) {
            if ($Case.publication.expected_digest -ne $Case.publication.actual_digest) {
                $errors += "Artifact SHA256 digest mismatch: expected $($Case.publication.expected_digest)"
            }
        }
        if ($Case.publication.remote_path_exists) {
            $errors += "Cannot overwrite immutable run record: runs/$($Case.publication.run_id) already exists"
        }
        if ($Case.publication.remote_ref_output) {
            $remoteCommit = Get-RemoteRefCommit -Output @($Case.publication.remote_ref_output) -ExpectedRef $Case.publication.expected_ref
            if (-not $remoteCommit) {
                $errors += "Remote read-back ref missing or malformed: $($Case.publication.expected_ref)"
            } elseif ($Case.publication.expected_commit -and $remoteCommit -ne $Case.publication.expected_commit) {
                $errors += "Remote read-back commit did not match the published commit"
            }
        }
    }

    if ($Case.check_run) {
        if ($Case.check_run.app_slug -and $Case.check_run.app_slug -ne "chimali-coverage-reporter") {
            $errors += "coverage-integration check produced by unauthorized app (app_slug: $($Case.check_run.app_slug)); must originate from dedicated Coverage App"
        }
    }

    if ($Case.reporter_event) {
        if ($Case.reporter_event.capture_conclusion -eq "skipped") {
            $errors += "Capture workflow concluded as skipped; complete required evidence was not produced"
        }
    }

    return @{
        IsValid = ($errors.Count -eq 0)
        Errors = $errors
    }
}

function Sort-TestSelectionCandidates {
    param(
        [Parameter(Mandatory = $true)][array]$Candidates
    )
    $augmented = foreach ($c in $Candidates) {
        $num = [System.Numerics.BigInteger]::Zero
        $den = [System.Numerics.BigInteger]::One
        if ($c.module_deficit_numerator -and $c.module_deficit_denominator) {
            $num = [System.Numerics.BigInteger]::Parse($c.module_deficit_numerator.ToString())
            $den = [System.Numerics.BigInteger]::Parse($c.module_deficit_denominator.ToString())
        } elseif ($null -ne $c.module_deficit) {
            $decVal = [decimal]$c.module_deficit
            $num = [System.Numerics.BigInteger]([Math]::Round($decVal * [decimal]1000000))
            $den = [System.Numerics.BigInteger]1000000
        }
        [PSCustomObject]@{
            Candidate = $c
            Impact = [int]$c.impact
            Likelihood = [int]$c.likelihood
            DeficitNum = $num
            DeficitDen = $den
            CandidateId = [string]$c.candidate_id
        }
    }

    $list = [System.Collections.Generic.List[object]]::new($augmented)
    $list.Sort({
        param($a, $b)
        if ($a.Impact -ne $b.Impact) {
            return $b.Impact.CompareTo($a.Impact)
        }
        if ($a.Likelihood -ne $b.Likelihood) {
            return $b.Likelihood.CompareTo($a.Likelihood)
        }
        $prodA = $a.DeficitNum * $b.DeficitDen
        $prodB = $b.DeficitNum * $a.DeficitDen
        if ($prodA -ne $prodB) {
            return $prodB.CompareTo($prodA)
        }
        return [string]::Compare($a.CandidateId, $b.CandidateId, [System.StringComparison]::Ordinal)
    })

    return @($list | ForEach-Object { $_.Candidate })
}

function Test-BatchProgression {
    param(
        [Parameter(Mandatory = $true)][int]$CurrentRank,
        [Parameter(Mandatory = $true)][int]$AttemptedNextRank,
        [Parameter(Mandatory = $true)][string]$TestStatus,
        [Parameter(Mandatory = $false)][string]$SelectedCase,
        [Parameter(Mandatory = $false)]$Receipts
    )
    if ($CurrentRank -gt $AttemptedNextRank) {
        if ($TestStatus -in @("Failed", "Unverified")) {
            return @{
                Allowed = $false
                Error = "Cannot advance to lower risk rank while higher rank test $SelectedCase is Failed or Unverified"
            }
        }
        if ($Receipts) {
            if (-not $Receipts.focused_assertion_passed -or -not $Receipts.clean_measurement_run_id -or -not $Receipts.reranking_performed) {
                return @{
                    Allowed = $false
                    Error = "Progression blocked: candidate $SelectedCase requires focused assertion, clean measurement, and reranking receipts"
                }
            }
        }
    }
    return @{
        Allowed = $true
        Error = $null
    }
}

function Test-SelectionCase {
    param(
        [Parameter(Mandatory = $true)]$Case
    )

    $errors = @()

    if ($Case.candidates) {
        $sorted = Sort-TestSelectionCandidates -Candidates $Case.candidates

        if ($sorted[0].candidate_id -ne $Case.expected_first) {
            $errors += "Expected first candidate to be '$($Case.expected_first)', but got '$($sorted[0].candidate_id)'"
        }
    }

    if ($Case.batch_state) {
        $bs = $Case.batch_state
        $prog = Test-BatchProgression -CurrentRank $bs.current_rank -AttemptedNextRank $bs.attempted_next_rank -TestStatus $bs.test_status -SelectedCase $bs.selected_case
        if ($prog.Allowed -ne $Case.expected_progression_allowed) {
            $errors += "Progression allowance mismatch"
        }
        if (-not $prog.Allowed) {
            $errors += $prog.Error
        }
    }

    if ($Case.evaluation) {
        $ev = $Case.evaluation
        $shortfall = ($ev.individual_floors_passed -and ($ev.repository_instruction_percent -lt $ev.required_floor))
        if ($shortfall -ne $Case.expected_aggregate_shortfall) {
            $errors += "Aggregate shortfall mismatch"
        }
    }

    return @{
        IsValid = ($errors.Count -eq 0)
        Errors = $errors
    }
}

function Test-BaselineCase {
    param(
        [Parameter(Mandatory = $true)]$Case
    )

    $errors = @()
    $reusable = $true

    $rec = $Case.baseline_record
    if ($rec) {
        $validProv = @("ArchivedAcceptedRun", "MeasuredAcceptedTarget")
        if ($rec.provenance -notin $validProv) {
            $reusable = $false
            $errors += "Unverified baseline provenance: $($rec.provenance); must originate from ArchivedAcceptedRun or MeasuredAcceptedTarget"
        }

        if ($Case.candidate -and $Case.candidate.target_commit -and ($rec.target_commit -ne $Case.candidate.target_commit)) {
            $reusable = $false
            $errors += "Baseline record target commit does not match candidate target"
        }

        if ($Case.candidate -and $Case.candidate.measurement_fingerprint -and ($rec.measurement_fingerprint -ne $Case.candidate.measurement_fingerprint)) {
            $reusable = $false
            $errors += "Measurement compatibility fingerprint mismatch"
        }
    }

    return @{
        Reusable = $reusable
        Errors = $errors
    }
}

function Test-ArchiveLifecycleCase {
    param(
        [Parameter(Mandatory = $true)]$Case
    )

    $errors = @()
    $success = $true
    $promotionAllowed = $true

    if ($Case.index_update) {
        $up = $Case.index_update
        if ($up.expected_parent_index -ne $up.actual_remote_parent) {
            $success = $false
            $errors += "Concurrent archive index conflict detected; rebase required before index advance"
        }
    }

    if ($Case.promotion) {
        $prom = $Case.promotion
        if ($prom.candidate_tested_tree -ne $prom.integrated_tree) {
            $promotionAllowed = $false
            $errors += "Integrated tree does not match tested candidate tree; fresh complete coverage capture required"
        }
    }

    return @{
        Success = $success
        PromotionAllowed = $promotionAllowed
        Errors = $errors
    }
}




