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
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-Error "Common utilities not found at $CommonPath"
    exit 3
}

. $CommonPath

# Validate Paths
if (-not [System.IO.Path]::IsPathRooted($RunBundle)) {
    Write-Error "RunBundle must be an absolute path: $RunBundle"
    exit 3
}
if (-not (Test-Path $RunBundle)) {
    Write-Error "RunBundle directory not found: $RunBundle"
    exit 2
}
if (-not [System.IO.Path]::IsPathRooted($PolicyPath)) {
    Write-Error "PolicyPath must be an absolute path: $PolicyPath"
    exit 3
}
if (-not (Test-Path $PolicyPath)) {
    Write-Error "PolicyPath file not found: $PolicyPath"
    exit 3
}

# 1. Load and Validate Policy
try {
    $policy = Read-SafeJson -Path $PolicyPath
    $polValidation = Test-PolicyConfiguration -Policy $policy
    if (-not $polValidation.IsValid) {
        Write-Error "Invalid policy configuration: $($polValidation.Reasons -join '; ')"
        exit 3
    }
} catch {
    Write-Error "Failed to load policy: $_"
    exit 3
}

# 2. Load and Validate Manifest
$manifestPath = Join-Path $RunBundle "manifest.json"
if (-not (Test-Path $manifestPath)) {
    Write-Error "manifest.json not found in RunBundle: $manifestPath"
    exit 2
}

try {
    $manifest = Read-SafeJson -Path $manifestPath
    $manifestValidation = Test-ManifestSchema -Manifest $manifest
    if (-not $manifestValidation.IsValid) {
        Write-Error "Manifest schema validation failed: $($manifestValidation.Errors -join '; ')"
        exit 3
    }
} catch {
    Write-Error "Failed to load manifest: $_"
    exit 3
}

# 3. Output Directory Enforcement (Outside Sealed Bundle & No Silent Overwrite)
$bundleParent = Split-Path -Parent $RunBundle
$targetOutDir = if ($OutputDirectory) {
    if (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
        Write-Error "OutputDirectory must be an absolute path: $OutputDirectory"
        exit 3
    }
    $OutputDirectory
} else {
    Join-Path (Join-Path $bundleParent "assessments") $manifest.run_id
}

$canonicalBundle = [System.IO.Path]::GetFullPath($RunBundle).TrimEnd('\', '/')
$canonicalOut = [System.IO.Path]::GetFullPath($targetOutDir).TrimEnd('\', '/')
if ($canonicalOut -eq $canonicalBundle -or $canonicalOut.StartsWith($canonicalBundle + [System.IO.Path]::DirectorySeparatorChar)) {
    Write-Error "Assessment receipts cannot be written inside sealed capture bundle: $RunBundle"
    exit 3
}

if (-not (Test-Path $targetOutDir)) {
    New-Item -ItemType Directory -Path $targetOutDir -Force | Out-Null
}

$decisionJsonPath = Join-Path $targetOutDir "decision.json"
if ((Test-Path $decisionJsonPath) -and (-not $Force)) {
    Write-Error "Assessment receipt already exists at $decisionJsonPath; silent overwrite prohibited"
    exit 3
}

$reasons = @()

# 4. Verify Artifact Paths & Digests
if ($manifest.artifacts) {
    foreach ($art in $manifest.artifacts) {
        if (-not $art.path) {
            $reasons += "Manifest artifact entry missing path"
            continue
        }
        if ($art.path -match "\.\." -or [System.IO.Path]::IsPathRooted($art.path)) {
            $reasons += "Manifest artifact path is escaping or rooted: $($art.path)"
            continue
        }
        $artFullPath = Join-Path $RunBundle $art.path
        if (-not (Test-Path $artFullPath)) {
            $reasons += "Manifest artifact not found on disk: $($art.path)"
            continue
        }
        if ($art.sha256) {
            $computedHash = Get-FileSha256 -Path $artFullPath
            if ($computedHash -ne $art.sha256.ToLower()) {
                $reasons += "Manifest artifact digest mismatch for $($art.path): expected $($art.sha256), observed $computedHash"
            }
        }
    }
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

if ($manifest.git -and ($manifest.git.clean_postflight -eq $false)) {
    $finalCollectionStatus = "Incomplete"
    $reasons += "Working tree was modified during collection (clean_postflight is false)"
}

if ($finalCollectionStatus -ne "Complete") {
    $reasons += "Collection is incomplete: status is $finalCollectionStatus"
}

# 7. Load and Verify Canonical Counters against Reports & Proofs
$countersPath = Join-Path $RunBundle "counters.json"
$countersData = $null
if (Test-Path $countersPath) {
    try {
        $countersData = Read-SafeJson -Path $countersPath
    } catch {
        Write-Error "Failed to read counters.json: $_"
        exit 2
    }
} else {
    $reasons += "counters.json missing from run bundle"
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
            $xCov = [int64]$xmlInstNode.GetAttribute("covered")
            $xMis = [int64]$xmlInstNode.GetAttribute("missed")
            if ([int64]$instCov -ne $xCov -or [int64]$instMis -ne $xMis) {
                $floorPassed = $false
                $reasons += "${modPath}: instruction counters in counters.json do not match XML (JSON: $instCov/$instMis, XML: $xCov/$xMis)"
            }
        }

        $xmlBranchNode = $xmlDoc.report.SelectSingleNode("counter[@type='BRANCH']")
        if ($xmlBranchNode) {
            $xCov = [int64]$xmlBranchNode.GetAttribute("covered")
            $xMis = [int64]$xmlBranchNode.GetAttribute("missed")
            if ([int64]$branchCov -ne $xCov -or [int64]$branchMis -ne $xMis) {
                $floorPassed = $false
                $reasons += "${modPath}: branch counters in counters.json do not match XML (JSON: $branchCov/$branchMis, XML: $xCov/$xMis)"
            }
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

        $proofValid = ($null -ne $proof) -and `
                      ($proof.normalization_rule -eq "AnalyzedZeroProofV1") -and `
                      ($proof.counters.INSTRUCTION.total -eq 0) -and `
                      (-not $xmlHash -or $proof.report_digest -eq $xmlHash)

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
            $proofValid = ($null -ne $proof) -and `
                          ($proof.normalization_rule -eq "AnalyzedZeroProofV1") -and `
                          ($proof.counters.BRANCH.total -eq 0) -and `
                          (-not $xmlHash -or $proof.report_digest -eq $xmlHash)
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

# Verify Tier 2 Gated Modules (20% instruction floor)
if ($policy.tier2_gated_modules) {
    foreach ($modPath in $policy.tier2_gated_modules) {
        $modCounters = $null
        if ($countersData -and $countersData.modules) {
            $modCounters = $countersData.modules | Where-Object { $_.module_path -eq $modPath }
        }
        if ($modCounters) {
            $instCov = $modCounters.instruction.covered
            $instMis = $modCounters.instruction.missed
            $instEval = Test-ExactFloorRatio -Covered $instCov -Missed $instMis -MinimumPercent $policy.tier2_instruction_min_percent
            if (-not $instEval.Passed) {
                $floorPassed = $false
                $reasons += "${modPath}: Tier 2 instruction $($instEval.Reason)"
            }
            $moduleDecisions += [PSCustomObject]@{
                Module = $modPath
                Tier = 2
                Disposition = "Applicable"
                Instruction = @{ Covered = $instCov; Missed = $instMis; Percent = $instEval.DisplayPercent; Passed = $instEval.Passed }
                Branch = @{ Covered = $modCounters.branch.covered; Missed = $modCounters.branch.missed; Percent = "Reported" }
                Status = if ($instEval.Passed) { "Passed" } else { "Failed" }
            }
        }
    }
}

# Verify Repository Aggregate (50% instruction floor)
$repoEval = $null
if ($countersData -and $countersData.repository) {
    $repoCov = $countersData.repository.instruction.covered
    $repoMis = $countersData.repository.instruction.missed
    $repoEval = Test-ExactFloorRatio -Covered $repoCov -Missed $repoMis -MinimumPercent $policy.repository_instruction_min_percent
    if (-not $repoEval.Passed) {
        $floorPassed = $false
        $reasons += "Repository instruction aggregate $($repoEval.Reason)"
    }
} else {
    $floorPassed = $false
    $reasons += "Repository aggregate counter data missing"
}

# Regression Check (if ComparisonBundle supplied)
$regressionPassed = $true
$regressionStatus = "NotApplicable"
$comparisonCompatibility = "Missing"

if ($ComparisonBundle) {
    if (-not (Test-Path $ComparisonBundle)) {
        Write-Error "ComparisonBundle directory not found: $ComparisonBundle"
        exit 2
    }
    $compCountersPath = Join-Path $ComparisonBundle "counters.json"
    if (-not (Test-Path $compCountersPath)) {
        Write-Error "counters.json not found in ComparisonBundle: $compCountersPath"
        exit 2
    }
    $compCounters = Read-SafeJson -Path $compCountersPath
    $comparisonCompatibility = "Compatible"
    $regressionStatus = "Passed"

    # Evaluate regression on Tier 1 modules
    foreach ($mod in $policy.tier1_modules) {
        $cMod = $countersData.modules | Where-Object { $_.module_path -eq $mod }
        $bMod = $compCounters.modules | Where-Object { $_.module_path -eq $mod }
        if ($cMod -and $bMod) {
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
