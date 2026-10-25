<#
.SYNOPSIS
    Evaluates coverage bundles against authoritative policy, floors, and regression rules.
.DESCRIPTION
    Validates manifest and counter data, calculates exact integer ratios for Tier 1
    (60% instructions, 60% branches where measurable), repository aggregate (50%),
    and Tier 2 (20%). Produces decision.json and decision.md.
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
    [string]$OutputDirectory
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

$targetOutDir = if ($OutputDirectory) {
    if (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
        Write-Error "OutputDirectory must be an absolute path: $OutputDirectory"
        exit 3
    }
    $OutputDirectory
} else {
    $RunBundle
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

# 3. Load Canonical Counters
$countersPath = Join-Path $RunBundle "counters.json"
$countersData = $null
if (Test-Path $countersPath) {
    try {
        $countersData = Read-SafeJson -Path $countersPath
    } catch {
        Write-Error "Failed to read counters.json: $_"
        exit 2
    }
}

$reasons = @()
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

    $instCov = $modCounters.instruction.covered
    $instMis = $modCounters.instruction.missed
    $instTot = [System.Numerics.BigInteger]::Parse($instCov.ToString()) + [System.Numerics.BigInteger]::Parse($instMis.ToString())

    if ($instTot -eq [System.Numerics.BigInteger]::Zero) {
        # Check if verified empty
        $isEvidencedEmpty = ($modCounters.disposition -eq "NotApplicable")
        $moduleDecisions += [PSCustomObject]@{
            Module = $modPath
            Tier = 1
            Disposition = "NotApplicable"
            Status = if ($isEvidencedEmpty) { "Passed" } else { "Failed" }
            Reason = if ($isEvidencedEmpty) { "Verified no executable production instructions" } else { "Empty instructions without valid NotApplicable disposition" }
        }
        if (-not $isEvidencedEmpty) {
            $floorPassed = $false
            $reasons += "${modPath}: empty instructions without valid NotApplicable disposition"
        }
        continue
    }

    # Evaluate Tier 1 Instruction Floor (60%)
    $instEval = Test-ExactFloorRatio -Covered $instCov -Missed $instMis -MinimumPercent $policy.tier1_instruction_min_percent
    if (-not $instEval.Passed) {
        $floorPassed = $false
        $reasons += "${modPath}: instruction ${instEval.Reason}"
    }

    # Evaluate Tier 1 Branch Floor (60% where measurable)
    $branchCov = $modCounters.branch.covered
    $branchMis = $modCounters.branch.missed
    $branchEval = Test-ExactFloorRatio -Covered $branchCov -Missed $branchMis -MinimumPercent $policy.tier1_branch_min_percent

    if ($branchEval.Applicable) {
        if (-not $branchEval.Passed) {
            $floorPassed = $false
            $reasons += "${modPath}: branch ${branchEval.Reason}"
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
                $reasons += "${modPath}: Tier 2 instruction ${instEval.Reason}"
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
        $reasons += "Repository instruction aggregate ${repoEval.Reason}"
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
                $reasons += "${mod}: instruction ${regInst.Reason}"
            }
            $regBranch = Test-ExactRegressionRatio -CandidateCovered $cMod.branch.covered -CandidateMissed $cMod.branch.missed -BaselineCovered $bMod.branch.covered -BaselineMissed $bMod.branch.missed
            if ($regBranch.Applicable -and -not $regBranch.Passed) {
                $regressionPassed = $false
                $reasons += "${mod}: branch ${regBranch.Reason}"
            }
        }
    }
    if (-not $regressionPassed) {
        $regressionStatus = "Failed"
    }
}

# Determine overall decisions
$finalFloorsStatus = if ($floorPassed) { "Passed" } else { "Failed" }
$finalTestsStatus = if ($manifest.decisions -and $manifest.decisions.tests) { $manifest.decisions.tests } else { "Unverified" }
$finalCollectionStatus = if ($manifest.decisions -and $manifest.decisions.collection) { $manifest.decisions.collection } else { "Complete" }

$overallSuccess = ($finalFloorsStatus -eq "Passed" -and $finalTestsStatus -eq "Passed" -and ($regressionStatus -eq "Passed" -or $regressionStatus -eq "NotApplicable"))

$decisionObject = [PSCustomObject]@{
    run_id = $manifest.run_id
    policy_id = $policy.policy_id
    evaluated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    decisions = [PSCustomObject]@{
        collection = $finalCollectionStatus
        tests = $finalTestsStatus
        floors = $finalFloorsStatus
        comparison_compatibility = $comparisonCompatibility
        regression = $regressionStatus
        publication = if ($manifest.decisions.publication) { $manifest.decisions.publication } else { "Pending" }
        integration = if ($overallSuccess) { "Unverified" } else { "Failed" } # Local verifier leaves integration Unverified until hosted check
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
$decisionJsonPath = Join-Path $targetOutDir "decision.json"
$decisionObject | ConvertTo-Json -Depth 10 | Set-Content -Path $decisionJsonPath -Encoding utf8

# Write decision.md
$decisionMdPath = Join-Path $targetOutDir "decision.md"
$mdContent = @"
# Coverage Verification Decision

- **Run ID**: $($manifest.run_id)
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
