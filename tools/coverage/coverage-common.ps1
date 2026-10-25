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

    return Get-Content -Path $Path -Raw | ConvertFrom-Json -Depth 100
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
        $xmlPath = Join-Path $scriptDir "fixtures" $xmlRel
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
                $proofPath = Join-Path $scriptDir "fixtures" $Case.proof_file
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

    return @{
        IsValid = $valid
        Reasons = $reasons
    }
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
    return $Candidates | Sort-Object `
        @{ Expression = { [int]$_.impact }; Descending = $true }, `
        @{ Expression = { [int]$_.likelihood }; Descending = $true }, `
        @{ Expression = { [double]$_.module_deficit }; Descending = $true }, `
        @{ Expression = { $_.candidate_id }; Descending = $false }
}

function Test-BatchProgression {
    param(
        [Parameter(Mandatory = $true)][int]$CurrentRank,
        [Parameter(Mandatory = $true)][int]$AttemptedNextRank,
        [Parameter(Mandatory = $true)][string]$TestStatus,
        [Parameter(Mandatory = $false)][string]$SelectedCase
    )
    if ($CurrentRank -gt $AttemptedNextRank -and ($TestStatus -in @("Failed", "Unverified"))) {
        return @{
            Allowed = $false
            Error = "Cannot advance to lower risk rank while higher rank test $SelectedCase is Failed or Unverified"
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




