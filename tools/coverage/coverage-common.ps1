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
    $document = $null
    try {
        $document = [System.Text.Json.JsonDocument]::Parse($raw)
        $validateNode = {
            param([System.Text.Json.JsonElement]$Node, [string]$JsonPath)
            if ($Node.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
                $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
                foreach ($property in $Node.EnumerateObject()) {
                    if (-not $names.Add($property.Name)) {
                        throw "Duplicate JSON property '$($property.Name)' at $JsonPath"
                    }
                    & $validateNode $property.Value "$JsonPath.$($property.Name)"
                }
            } elseif ($Node.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
                $index = 0
                foreach ($item in $Node.EnumerateArray()) {
                    & $validateNode $item "$JsonPath[$index]"
                    $index++
                }
            }
        }
        & $validateNode $document.RootElement '$'
        return $raw | ConvertFrom-Json -Depth 100 -ErrorAction Stop
    } catch {
        throw "Invalid or ambiguous JSON in '$Path': $($_.Exception.Message)"
    } finally {
        if ($null -ne $document) { $document.Dispose() }
    }
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path)) {
        throw "File not found for hashing: $Path"
    }
    return (Get-FileHash -Path $Path -Algorithm SHA256).Hash.ToLower()
}

function Get-CoverageBundleDigest {
    param([Parameter(Mandatory = $true)][string]$BundlePath)
    if (-not (Test-Path -LiteralPath $BundlePath -PathType Container)) { throw "Bundle directory not found: $BundlePath" }
    $root = [System.IO.Path]::GetFullPath($BundlePath)
    $content = ''
    foreach ($file in (Get-ChildItem -LiteralPath $root -Recurse -File | Sort-Object FullName)) {
        $relative = [System.IO.Path]::GetRelativePath($root, $file.FullName).Replace('\', '/')
        $content += "${relative}:$(Get-FileSha256 -Path $file.FullName);"
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($content))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function ConvertTo-CoveragePercentDisplay {
    param(
        [Parameter(Mandatory = $true)][System.Numerics.BigInteger]$Covered,
        [Parameter(Mandatory = $true)][System.Numerics.BigInteger]$Total
    )
    $scaledNumerator = [System.Numerics.BigInteger]::Multiply([System.Numerics.BigInteger]100000, $Covered)
    $whole = [System.Numerics.BigInteger]::Divide($scaledNumerator, $Total)
    $remainder = [System.Numerics.BigInteger]::Remainder($scaledNumerator, $Total)
    if ([System.Numerics.BigInteger]::Multiply([System.Numerics.BigInteger]2, $remainder) -ge $Total) {
        $whole = [System.Numerics.BigInteger]::Add($whole, [System.Numerics.BigInteger]::One)
    }
    $display = [decimal]$whole / [decimal]1000
    return $display.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Test-NonnegativeIntegerValue {
    param($Value)
    $isIntegerType = $Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or $Value -is [int64] -or $Value -is [System.Numerics.BigInteger]
    return $isIntegerType -and ([string]$Value -match '^(0|[1-9][0-9]*)$')
}

function Get-CanonicalCoveragePath {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not [System.IO.Path]::IsPathRooted($Path)) { throw "Coverage path must be absolute: $Path" }
    $canonical = [System.IO.Path]::GetFullPath($Path)
    $root = [System.IO.Path]::GetPathRoot($canonical)
    $canonical = $canonical.Replace([System.IO.Path]::AltDirectorySeparatorChar, [System.IO.Path]::DirectorySeparatorChar)
    while ($canonical.Length -gt $root.Length -and ($canonical.EndsWith('\') -or $canonical.EndsWith('/'))) {
        $canonical = $canonical.Substring(0, $canonical.Length - 1)
    }
    return $canonical
}

function Test-CoveragePathWithinRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Root
    )
    $canonicalPath = Get-CanonicalCoveragePath -Path $Path
    $canonicalRoot = Get-CanonicalCoveragePath -Path $Root
    $comparison = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    if ([string]::Equals($canonicalPath, $canonicalRoot, $comparison)) { return $true }
    $rootPrefix = $canonicalRoot + [System.IO.Path]::DirectorySeparatorChar
    return $canonicalPath.StartsWith($rootPrefix, $comparison)
}

function ConvertTo-CoverageBigInteger {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Label
    )
    $text = [string]$Value
    if ($text -notmatch '^(0|[1-9][0-9]*)$') {
        throw "$Label must be a nonnegative integer"
    }
    try {
        return [System.Numerics.BigInteger]::Parse($text, [Globalization.CultureInfo]::InvariantCulture)
    } catch {
        throw "$Label is outside the supported integer representation: $($_.Exception.Message)"
    }
}

function Test-ReportLevelCounterSyntax {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlDocument]$XmlDocument,
        [Parameter(Mandatory = $true)][string]$Scope
    )
    if ($XmlDocument.DocumentElement.LocalName -ne 'report') { throw "$Scope report root is not <report>" }
    $allowedTypes = @('INSTRUCTION', 'BRANCH', 'LINE', 'COMPLEXITY', 'METHOD', 'CLASS')
    $seenTypes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($node in @($XmlDocument.SelectNodes('/report/counter'))) {
        $type = $node.GetAttribute('type')
        if ($type -cnotin $allowedTypes) { throw "$Scope report contains unsupported report-level counter type '$type'" }
        if (-not $seenTypes.Add($type)) { throw "$Scope report must contain exactly one report-level $type counter (found duplicate)" }
        $null = ConvertTo-CoverageBigInteger -Value $node.GetAttribute('covered') -Label "$Scope $type XML covered"
        $null = ConvertTo-CoverageBigInteger -Value $node.GetAttribute('missed') -Label "$Scope $type XML missed"
    }
}

function Get-ReportLevelCoverageCounters {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlDocument]$XmlDocument,
        [Parameter(Mandatory = $true)][string]$Scope,
        [switch]$RequireNonzeroInstructions
    )
    Test-ReportLevelCounterSyntax -XmlDocument $XmlDocument -Scope $Scope
    $instructionNodes = @($XmlDocument.SelectNodes('/report/counter[@type="INSTRUCTION"]'))
    $branchNodes = @($XmlDocument.SelectNodes('/report/counter[@type="BRANCH"]'))
    if ($instructionNodes.Count -ne 1) { throw "$Scope report must contain exactly one report-level INSTRUCTION counter (found $($instructionNodes.Count))" }
    if ($branchNodes.Count -ne 1) { throw "$Scope report must contain exactly one report-level BRANCH counter (found $($branchNodes.Count))" }
    $instructionCovered = ConvertTo-CoverageBigInteger -Value $instructionNodes[0].GetAttribute('covered') -Label "$Scope instruction covered"
    $instructionMissed = ConvertTo-CoverageBigInteger -Value $instructionNodes[0].GetAttribute('missed') -Label "$Scope instruction missed"
    $branchCovered = ConvertTo-CoverageBigInteger -Value $branchNodes[0].GetAttribute('covered') -Label "$Scope branch covered"
    $branchMissed = ConvertTo-CoverageBigInteger -Value $branchNodes[0].GetAttribute('missed') -Label "$Scope branch missed"
    if ($RequireNonzeroInstructions -and ($instructionCovered + $instructionMissed) -eq [System.Numerics.BigInteger]::Zero) {
        throw "$Scope instruction aggregate denominator is zero"
    }
    return [PSCustomObject]@{
        instruction = [PSCustomObject]@{ covered = $instructionCovered; missed = $instructionMissed; total = $instructionCovered + $instructionMissed }
        branch = [PSCustomObject]@{ covered = $branchCovered; missed = $branchMissed; total = $branchCovered + $branchMissed }
    }
}

function Test-CanonicalRepositoryReportBinding {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlDocument]$XmlDocument,
        [Parameter(Mandatory = $true)][AllowNull()]$RepositoryCounters,
        [Parameter(Mandatory = $true)][string]$ReportDigest,
        [Parameter(Mandatory = $true)][string]$ExpectedReportDigest,
        [string]$Label = 'Repository'
    )

    $errors = @()
    if ($ReportDigest -notmatch '^[0-9a-fA-F]{64}$' -or $ExpectedReportDigest -notmatch '^[0-9a-fA-F]{64}$' -or $ReportDigest -cne $ExpectedReportDigest) {
        $errors += "$Label report digest does not match canonical counters"
    }
    if (-not $RepositoryCounters) {
        $errors += "$Label canonical repository counters are missing"
        return @{ IsValid = $false; Errors = $errors }
    }

    try {
        $xmlCounters = Get-ReportLevelCoverageCounters -XmlDocument $XmlDocument -Scope $Label -RequireNonzeroInstructions
        foreach ($metric in @('instruction', 'branch')) {
            $counter = $RepositoryCounters.$metric
            if (-not $counter) { throw "$Label canonical $metric counters are missing" }
            foreach ($field in @('covered', 'missed', 'total')) {
                if (-not (Test-NonnegativeIntegerValue -Value $counter.$field)) { throw "$Label canonical $metric $field must be a nonnegative integer" }
            }
            $covered = ConvertTo-CoverageBigInteger -Value $counter.covered -Label "$Label canonical $metric covered"
            $missed = ConvertTo-CoverageBigInteger -Value $counter.missed -Label "$Label canonical $metric missed"
            $total = ConvertTo-CoverageBigInteger -Value $counter.total -Label "$Label canonical $metric total"
            if ($covered + $missed -ne $total) { throw "$Label canonical $metric total does not equal covered + missed" }
            if ($metric -eq 'instruction' -and $total -eq [System.Numerics.BigInteger]::Zero) { throw "$Label instruction aggregate denominator is zero" }
            if ($xmlCounters.$metric.covered -ne $covered -or $xmlCounters.$metric.missed -ne $missed) {
                throw "$Label $metric counts disagree between JaCoCo XML and canonical counters"
            }
        }
    } catch { $errors += $_.Exception.Message }

    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ExactFloorRatio {
    param(
        [Parameter(Mandatory = $true)]$Covered,
        [Parameter(Mandatory = $true)]$Missed,
        [Parameter(Mandatory = $true)][int]$MinimumPercent
    )

    foreach ($item in @(@{ Name = 'covered'; Value = $Covered }, @{ Name = 'missed'; Value = $Missed })) {
        if (-not (Test-NonnegativeIntegerValue -Value $item.Value)) { throw "Coverage $($item.Name) count must be a nonnegative integer" }
    }
    if ($MinimumPercent -lt 0 -or $MinimumPercent -gt 100) { throw 'MinimumPercent must be between 0 and 100' }
    $cov = [System.Numerics.BigInteger]::Parse($Covered.ToString(), [Globalization.CultureInfo]::InvariantCulture)
    $mis = [System.Numerics.BigInteger]::Parse($Missed.ToString(), [Globalization.CultureInfo]::InvariantCulture)
    $tot = $cov + $mis

    if ($tot -eq [System.Numerics.BigInteger]::Zero) {
        return @{
            Applicable = $false
            Passed = $false
            Total = [System.Numerics.BigInteger]::Zero
            DisplayPercent = "N/A"
            Reason = "Denominator is zero"
        }
    }

    $lhs = [System.Numerics.BigInteger]::Multiply([System.Numerics.BigInteger]100, $cov)
    $rhs = [System.Numerics.BigInteger]::Multiply([System.Numerics.BigInteger]$MinimumPercent, $tot)

    $passed = ($lhs -ge $rhs)
    $display = ConvertTo-CoveragePercentDisplay -Covered $cov -Total $tot

    return @{
        Applicable = $true
        Passed = $passed
        Covered = $cov
        Total = $tot
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

    $candidateCounts = Test-ExactFloorRatio -Covered $CandidateCovered -Missed $CandidateMissed -MinimumPercent 0
    $cCov = [System.Numerics.BigInteger]::Parse($CandidateCovered.ToString(), [Globalization.CultureInfo]::InvariantCulture)
    $cMis = [System.Numerics.BigInteger]::Parse($CandidateMissed.ToString(), [Globalization.CultureInfo]::InvariantCulture)
    $cTot = $cCov + $cMis

    $baselineCounts = Test-ExactFloorRatio -Covered $BaselineCovered -Missed $BaselineMissed -MinimumPercent 0
    $bCov = [System.Numerics.BigInteger]::Parse($BaselineCovered.ToString(), [Globalization.CultureInfo]::InvariantCulture)
    $bMis = [System.Numerics.BigInteger]::Parse($BaselineMissed.ToString(), [Globalization.CultureInfo]::InvariantCulture)
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
    $cDisplay = ConvertTo-CoveragePercentDisplay -Covered $cCov -Total $cTot
    $bDisplay = ConvertTo-CoveragePercentDisplay -Covered $bCov -Total $bTot

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
    if ($Counters.instruction -and $null -ne $Counters.instruction.covered -and $null -ne $Counters.instruction.missed) {
        $instRes = Test-ExactFloorRatio -Covered $Counters.instruction.covered -Missed $Counters.instruction.missed -MinimumPercent $minInst
        if (-not $instRes.Passed) {
            $allPassed = $false
            $reasons += "$($Counters.module_path): instruction $($instRes.Reason)"
        }
    } else {
        $allPassed = $false
        $reasons += "$($Counters.module_path): instruction counters are missing"
    }

    if ($tier -eq 1 -and (-not $Counters.branch -or $null -eq $Counters.branch.covered -or $null -eq $Counters.branch.missed)) {
        $allPassed = $false
        $reasons += "$($Counters.module_path): Tier 1 branch counters are missing"
    } elseif ($Counters.branch -and $tier -eq 1) {
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
    foreach ($metric in @('INSTRUCTION', 'BRANCH')) {
        $proofCounter = if ($Proof.counters) { $Proof.counters.$metric } else { $null }
        $canonicalCounter = $CounterModule.$($metric.ToLowerInvariant())
        if (-not $proofCounter -or -not $canonicalCounter) {
            $reasons += "Zero proof or canonical counter record is missing $metric values"
            continue
        }
        try {
            $proofCovered = ConvertTo-CoverageBigInteger -Value $proofCounter.covered -Label "Zero proof $metric covered"
            $proofMissed = ConvertTo-CoverageBigInteger -Value $proofCounter.missed -Label "Zero proof $metric missed"
            $proofTotal = ConvertTo-CoverageBigInteger -Value $proofCounter.total -Label "Zero proof $metric total"
            $canonicalCovered = ConvertTo-CoverageBigInteger -Value $canonicalCounter.covered -Label "Canonical $metric covered"
            $canonicalMissed = ConvertTo-CoverageBigInteger -Value $canonicalCounter.missed -Label "Canonical $metric missed"
            if ($proofCovered -lt 0 -or $proofMissed -lt 0 -or $proofTotal -ne ($proofCovered + $proofMissed)) {
                $reasons += "Zero proof has invalid or inconsistent $metric counts"
            }
            if ($proofCovered -ne $canonicalCovered -or $proofMissed -ne $canonicalMissed) {
                $reasons += "Zero proof $metric counts do not match canonical counters"
            }
        } catch {
            $reasons += "Zero proof has malformed $metric counts"
        }
    }
    return @{ IsValid = ($reasons.Count -eq 0); Reasons = $reasons }
}

function Test-ModuleReportCounterEvidence {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlDocument]$XmlDocument,
        [Parameter(Mandatory = $true)]$InventoryModule,
        [Parameter(Mandatory = $true)]$CounterModule,
        [Parameter(Mandatory = $true)][string]$InventoryDigest,
        [Parameter(Mandatory = $true)][string]$ExpectedInventoryDigest,
        [Parameter(Mandatory = $true)][string]$AnalyzerIdentity,
        [Parameter(Mandatory = $true)][string]$ReportDigest,
        [Parameter(Mandatory = $false)]$ZeroCountProofs = @(),
        [string]$Label = 'Module'
    )

    $errors = @()
    if ($XmlDocument.DocumentElement.LocalName -ne 'report') { return @{ IsValid = $false; Errors = @("$Label report root is not <report>") } }
    try { Test-ReportLevelCounterSyntax -XmlDocument $XmlDocument -Scope $Label } catch { $errors += $_.Exception.Message }
    if ($CounterModule.report_digest -cne $ReportDigest) { $errors += "$Label report digest does not match canonical counters" }
    $moduleBinding = Test-CanonicalInventoryModuleBinding -InventoryModule $InventoryModule -CounterModule $CounterModule -InventoryDigest $InventoryDigest -ExpectedInventoryDigest $ExpectedInventoryDigest -AnalyzerIdentity $AnalyzerIdentity -ReportDigest $ReportDigest
    $errors += $moduleBinding.Reasons
    foreach ($metricName in @('INSTRUCTION', 'BRANCH')) {
        $key = $metricName.ToLowerInvariant()
        $counter = $CounterModule.$key
        if (-not $counter) { $errors += "$Label $key canonical counters are missing"; continue }
        try {
            $covered = ConvertTo-CoverageBigInteger -Value $counter.covered -Label "$Label $key canonical covered"
            $missed = ConvertTo-CoverageBigInteger -Value $counter.missed -Label "$Label $key canonical missed"
            $total = ConvertTo-CoverageBigInteger -Value $counter.total -Label "$Label $key canonical total"
            if ($covered + $missed -ne $total) { throw "$Label $key canonical total does not equal covered + missed" }
            $nodes = @($XmlDocument.SelectNodes("/report/counter[@type='$metricName']"))
            if ($nodes.Count -gt 1) { throw "$Label report has duplicate $metricName counters" }
            if ($nodes.Count -eq 0) {
                if ($total -ne [System.Numerics.BigInteger]::Zero) { throw "$Label omitted a nonzero $metricName counter" }
                $proofRows = @($ZeroCountProofs | Where-Object { $_.module_path -ceq $CounterModule.module_path })
                if ($proofRows.Count -ne 1) { throw "$Label omitted zero $metricName counter without exactly one canonical proof (found $($proofRows.Count))" }
                $proofBinding = Test-CanonicalZeroProofBinding -Proof $proofRows[0] -InventoryModule $InventoryModule -CounterModule $CounterModule -InventoryDigest $InventoryDigest -ExpectedInventoryDigest $ExpectedInventoryDigest -AnalyzerIdentity $AnalyzerIdentity -ReportDigest $ReportDigest
                if (-not $proofBinding.IsValid) { throw ($proofBinding.Reasons -join '; ') }
            } else {
                $xmlCovered = ConvertTo-CoverageBigInteger -Value $nodes[0].GetAttribute('covered') -Label "$Label $metricName XML covered"
                $xmlMissed = ConvertTo-CoverageBigInteger -Value $nodes[0].GetAttribute('missed') -Label "$Label $metricName XML missed"
                if ($xmlCovered -ne $covered -or $xmlMissed -ne $missed) { throw "$Label $metricName XML counts disagree with canonical covered/missed counts" }
            }
        } catch { $errors += $_.Exception.Message }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ExecutionCompatibilityEvidence {
    param(
        [Parameter(Mandatory = $true)]$InventoryModule,
        [Parameter(Mandatory = $true)]$CounterModule,
        [Parameter(Mandatory = $true)][string]$AnalyzerIdentity,
        [string]$Label = 'Module'
    )
    $errors = @()
    $receipt = $CounterModule.execution_compatibility
    if (-not $receipt) { return @{ IsValid = $false; Errors = @("$Label execution compatibility receipt is missing") } }
    if ($receipt.analyzer_identity -cne $AnalyzerIdentity) { $errors += "$Label execution compatibility analyzer identity mismatch" }
    if ($receipt.report_digest -cne $CounterModule.report_digest -or [string]$receipt.report_digest -notmatch '^[0-9a-fA-F]{64}$') { $errors += "$Label execution compatibility report digest mismatch" }
    if ($receipt.selected_variant -cne $InventoryModule.selected_variant) { $errors += "$Label execution compatibility selected variant mismatch" }
    if ($receipt.class_inventory_digest -cne $InventoryModule.class_inventory_digest -or $CounterModule.class_inventory_digest -cne $InventoryModule.class_inventory_digest) {
        $errors += "$Label execution compatibility class inventory binding mismatch"
    }
    $expectedCount = @($InventoryModule.retained_classes).Count
    foreach ($field in @('retained_class_count', 'analyzed_class_count', 'unmatched_class_count')) {
        if (-not (Test-NonnegativeIntegerValue -Value $receipt.$field)) { $errors += "$Label execution compatibility $field is not a nonnegative integer" }
    }
    if ((Test-NonnegativeIntegerValue -Value $receipt.retained_class_count) -and [System.Numerics.BigInteger]::Parse(([string]$receipt.retained_class_count), [Globalization.CultureInfo]::InvariantCulture) -ne $expectedCount) {
        $errors += "$Label execution compatibility retained class count does not match inventory"
    }
    $analyzedIds = @($receipt.analyzed_class_ids | ForEach-Object { [string]$_ })
    if ((Test-NonnegativeIntegerValue -Value $receipt.analyzed_class_count) -and [System.Numerics.BigInteger]::Parse(([string]$receipt.analyzed_class_count), [Globalization.CultureInfo]::InvariantCulture) -ne $analyzedIds.Count) {
        $errors += "$Label execution compatibility analyzed class count does not match the JaCoCo analysis class list"
    }
    if (@($analyzedIds | Select-Object -Unique).Count -ne $analyzedIds.Count) { $errors += "$Label execution compatibility contains duplicate analyzed class identities" }
    $retainedIds = @($InventoryModule.retained_classes | ForEach-Object { ([string]$_.class_id -replace '\.class$', '') })
    foreach ($classId in $analyzedIds) {
        if ($classId -notin $retainedIds) { $errors += "$Label execution compatibility analyzed class is absent from retained inventory: $classId" }
    }
    if ((Test-NonnegativeIntegerValue -Value $receipt.unmatched_class_count) -and [System.Numerics.BigInteger]::Parse(([string]$receipt.unmatched_class_count), [Globalization.CultureInfo]::InvariantCulture) -ne 0) {
        $errors += "$Label execution compatibility contains unmatched classes"
    }
    if (@($receipt.unmatched_class_ids).Count -ne 0) { $errors += "$Label execution compatibility contains unmatched class IDs" }
    $expectedStatus = if ($expectedCount -eq 0) { 'NotApplicable' } else { 'Compatible' }
    $expectedDisposition = if ($expectedCount -eq 0) { 'NotApplicable' } else { 'Applicable' }
    if ($receipt.status -cne $expectedStatus -or $CounterModule.disposition -cne $expectedDisposition) { $errors += "$Label execution compatibility status disagrees with retained inventory" }
    if ($expectedCount -gt 0 -and $analyzedIds.Count -eq 0) { $errors += "$Label execution compatibility has no JaCoCo analyzed production classes" }

    $counterFiles = if ($null -eq $CounterModule.execution_files) { @() } else { @($CounterModule.execution_files) }
    $receiptFiles = if ($null -eq $receipt.execution_files) { @() } else { @($receipt.execution_files) }
    if ($counterFiles.Count -ne $receiptFiles.Count) { $errors += "$Label execution compatibility file list differs from canonical execution attribution" }
    else {
        for ($i = 0; $i -lt $counterFiles.Count; $i++) {
            if ([string]$counterFiles[$i].path -cne [string]$receiptFiles[$i].path -or
                [string]$counterFiles[$i].repository_path -cne [string]$receiptFiles[$i].repository_path -or
                [string]$counterFiles[$i].sha256 -cne [string]$receiptFiles[$i].sha256 -or
                [string]$counterFiles[$i].run_id -cne [string]$receiptFiles[$i].run_id -or
                [string]$counterFiles[$i].device_serial -cne [string]$receiptFiles[$i].device_serial -or
                (@($counterFiles[$i].task_paths | ForEach-Object { [string]$_ } | Sort-Object -Unique) -join "`n") -cne
                (@($receiptFiles[$i].task_paths | ForEach-Object { [string]$_ } | Sort-Object -Unique) -join "`n")) {
                $errors += "$Label execution compatibility file binding differs from canonical execution attribution"
                break
            }
            if ([string]$counterFiles[$i].sha256 -notmatch '^[0-9a-fA-F]{64}$') { $errors += "$Label execution compatibility contains invalid execution file digest" }
            $expectedKind = if ([string]$counterFiles[$i].path -match '\.ec$') { 'device' } else { 'host' }
            if ($receiptFiles[$i].kind -cne $expectedKind -or $receiptFiles[$i].selected_variant -cne $InventoryModule.selected_variant) {
                $errors += "$Label execution compatibility input kind or approved variant binding is invalid"
            }
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ExecutionCompatibilityAttribution {
    param(
        [Parameter(Mandatory = $true)][AllowNull()]$CompatibilityReceipt,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()]$ManifestExecutionFiles,
        [Parameter(Mandatory = $true)][string]$Scope,
        [Parameter(Mandatory = $true)][string]$ExpectedVariant,
        $Workload,
        [string]$ExpectedRunId,
        [string]$ExpectedDeviceSerial,
        [string]$Label = 'Module'
    )
    $errors = @()
    $receiptFiles = if ($null -eq $CompatibilityReceipt -or $null -eq $CompatibilityReceipt.execution_files) { @() } else { @($CompatibilityReceipt.execution_files) }
    $manifestFiles = if ($null -eq $ManifestExecutionFiles) { @() } else { @($ManifestExecutionFiles | Where-Object { [string]$_.scope -ceq $Scope }) }
    if ($receiptFiles.Count -ne $manifestFiles.Count) { $errors += "$Label compatibility execution inputs do not match manifest attribution count" }
    foreach ($executionInput in $receiptFiles) {
        $path = ([string]$executionInput.repository_path).Replace('\', '/')
        if ([string]::IsNullOrWhiteSpace($path) -or [System.IO.Path]::IsPathRooted($path) -or $path -match '(^|/)\.\.(/|$)') {
            $errors += "$Label compatibility execution input has an invalid repository-relative source path"
            continue
        }
        $matches = @($manifestFiles | Where-Object {
            ([string]$_.source_path).Replace('\', '/') -ceq $path -and
            [string]$_.source_sha256 -ceq [string]$executionInput.sha256 -and
            [string]$_.kind -ceq [string]$executionInput.kind
        })
        if ([string]$executionInput.selected_variant -cne $ExpectedVariant) { $errors += "$Label compatibility input is not bound to approved variant $ExpectedVariant" }
        if ($matches.Count -ne 1) { $errors += "$Label compatibility execution input is not bound to exactly one manifest source artifact: $path" }
        elseif ($Workload -and ($executionInput.run_id -cne $ExpectedRunId -or $matches[0].run_id -cne $ExpectedRunId)) { $errors += "$Label execution input is not bound to this run" }
        elseif ($Workload -and ($executionInput.device_serial -cne $ExpectedDeviceSerial -or $matches[0].device_serial -cne $ExpectedDeviceSerial)) { $errors += "$Label execution input is not bound to the measured device" }
        elseif ($Workload) {
            $expectedTasks = @(Get-ExpectedExecutionTaskPaths -Workload $Workload -Scope $Scope -Kind ([string]$executionInput.kind))
            $receiptTasks = @($executionInput.task_paths | ForEach-Object { [string]$_ } | Sort-Object -Unique)
            $manifestTasks = @($matches[0].task_paths | ForEach-Object { [string]$_ } | Sort-Object -Unique)
            if (($expectedTasks -join "`n") -cne ($receiptTasks -join "`n") -or ($expectedTasks -join "`n") -cne ($manifestTasks -join "`n")) {
                $errors += "$Label execution input task attribution does not match the required workload inventory"
            }
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Get-ExpectedExecutionTaskPaths {
    param(
        [Parameter(Mandatory = $true)]$Workload,
        [Parameter(Mandatory = $true)][string]$Scope,
        [Parameter(Mandatory = $true)][ValidateSet('host', 'device')][string]$Kind
    )
    $records = @()
    if ($Kind -eq 'host') {
        $records = @($Workload.host_test_tasks | Where-Object {
            $_.executed -eq $true -and ($Scope -eq ':repository' -or ([string]$_.task_path).StartsWith("${Scope}:", [StringComparison]::Ordinal))
        })
    } else {
        $records = @(@($Workload.required_device_tasks) + @($Workload.optional_device_tasks) | Where-Object {
            $_.executed -eq $true -and ($Scope -eq ':repository' -or [string]$_.module_path -ceq $Scope)
        })
    }
    return @($records | ForEach-Object { [string]$_.task_path } | Sort-Object -Unique)
}

function Test-RepositoryExecutionAttribution {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$RepositoryExecutionFiles,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$ManifestExecutionFiles,
        $Workload,
        [string]$ExpectedRunId,
        [string]$ExpectedDeviceSerial
    )
    $errors = @()
    $manifestFiles = @($ManifestExecutionFiles | Where-Object { [string]$_.scope -ceq ':repository' })
    if ($RepositoryExecutionFiles.Count -eq 0) { $errors += 'Repository analysis has no retained execution inputs' }
    if ($RepositoryExecutionFiles.Count -ne $manifestFiles.Count) {
        $errors += 'Repository analysis execution inputs do not match manifest attribution count'
    }
    foreach ($inputFile in $RepositoryExecutionFiles) {
        $relativePath = ([string]$inputFile.repository_path).Replace('\', '/')
        $kind = if ([string]$inputFile.path -match '(?i)\.ec$') { 'device' } else { 'host' }
        if ([string]::IsNullOrWhiteSpace($relativePath) -or [System.IO.Path]::IsPathRooted($relativePath) -or $relativePath -match '(^|/)\.\.(/|$)') {
            $errors += 'Repository analysis execution input has an invalid repository-relative source path'
            continue
        }
        $matchingSources = @($manifestFiles | Where-Object {
            ([string]$_.source_path).Replace('\', '/') -ceq $relativePath -and
            [string]$_.source_sha256 -ceq [string]$inputFile.sha256 -and
            [string]$_.kind -ceq $kind
        })
        $validDigest = [string]$inputFile.sha256 -match '^[0-9a-fA-F]{64}$'
        if (-not $validDigest -or $matchingSources.Count -ne 1) {
            $errors += "Repository analysis execution input is not bound to exactly one retained source artifact: $relativePath"
        } elseif ($Workload -and ($inputFile.run_id -cne $ExpectedRunId -or $matchingSources[0].run_id -cne $ExpectedRunId)) {
            $errors += "Repository execution input is not bound to this run: $relativePath"
        } elseif ($Workload -and ($inputFile.device_serial -cne $ExpectedDeviceSerial -or $matchingSources[0].device_serial -cne $ExpectedDeviceSerial)) {
            $errors += "Repository execution input is not bound to the measured device: $relativePath"
        } elseif ($Workload) {
            $expectedTasks = @(Get-ExpectedExecutionTaskPaths -Workload $Workload -Scope ':repository' -Kind $kind)
            $counterTasks = @($inputFile.task_paths | ForEach-Object { [string]$_ } | Sort-Object -Unique)
            $manifestTasks = @($matchingSources[0].task_paths | ForEach-Object { [string]$_ } | Sort-Object -Unique)
            if (($expectedTasks -join "`n") -cne ($counterTasks -join "`n") -or ($expectedTasks -join "`n") -cne ($manifestTasks -join "`n")) {
                $errors += "Repository execution input task attribution does not match the required workload inventory: $relativePath"
            }
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-PolicyConfiguration {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        $Arguments
    )

    $reasons = @()
    $expectedTier1 = @(':core:security', ':core:domain', ':core:data', ':feature:fido2', ':feature:authenticator')
    $expectedTier2 = @(':core:database', ':core:common', ':app')
    $expectedRoots = @('src/commonMain/kotlin', 'src/androidMain/kotlin', 'src/main/kotlin', 'src/main/java')
$expectedHarnessFiles = @('tools/coverage/capture.ps1', 'tools/coverage/coverage-common.ps1', 'tools/integration-ci.ps1', 'tools/local-ci.ps1', 'tools/hdk-coverage.init.gradle', 'tools/coverage/policy.json', 'tools/coverage/validate-policy.ps1', 'tools/coverage/verify.ps1', '.github/workflows/coverage.yml', '.github/workflows/coverage-report.yml', 'tools/coverage/report.ps1', 'tools/coverage/validate-candidate-tree.ps1', 'tools/coverage/validate-live-candidate.ps1', 'tools/coverage/validate-live-pr-target.ps1', 'tools/coverage/validate-workflow-run.ps1', 'tools/coverage/validate-integration-event.ps1', 'tools/coverage/resolve-accepted-baseline.ps1', 'tools/coverage/publish.ps1', 'tools/coverage/request-target-recaptures.ps1', 'tools/coverage/validate-dispatched-candidate.ps1')
    $expectedVariants = [ordered]@{
        ':core:security' = 'androidMain'; ':core:domain' = 'androidMain'; ':core:data' = 'release'
        ':core:bluetooth' = 'release'; ':core:database' = 'release'; ':core:common' = 'androidMain'
        ':core:ui' = 'release'; ':feature:vault' = 'release'; ':feature:editor' = 'release'
        ':feature:fido2' = 'androidMain'; ':feature:authenticator' = 'release'
        ':feature:onboarding' = 'release'; ':feature:settings' = 'release'; ':app' = 'shrunkDebug'
    }
    $expectedTopLevel = @('$schema', 'schema_version', 'policy_id', 'description', 'tier1_modules', 'tier1_instruction_min_percent', 'tier1_branch_min_percent', 'repository_instruction_min_percent', 'tier2_instruction_min_percent', 'tier2_gated_modules', 'branch_improvement_goal_percent', 'metrics', 'counter_normalization', 'measurement_fingerprint', 'production_inventory_rules', 'production_scope', 'generated_exclusion_rules', 'required_workload', 'device_requirements', 'harness_requirements', 'runtime_requirements', 'regression_rule')

    $checkKeys = {
        param($Object, [string[]]$Expected, [string]$Label)
        $localReasons = @()
        if ($null -eq $Object) { return "$Label is missing" }
        $actual = @($Object.PSObject.Properties.Name | Sort-Object -CaseSensitive)
        $wanted = @($Expected | Sort-Object -CaseSensitive)
        foreach ($key in $wanted) { if ($key -notin $actual) { $localReasons += "$Label is missing required field '$key'" } }
        foreach ($key in $actual) { if ($key -notin $wanted) { $localReasons += "$Label contains unknown field '$key'" } }
        return $localReasons
    }
    $checkOrderedStrings = {
        param($Actual, [string[]]$Expected, [string]$Label)
        $localReasons = @()
        $values = @($Actual | ForEach-Object { [string]$_ })
        if (@($values | Select-Object -Unique).Count -ne $values.Count) { $localReasons += "$Label contains duplicate entries" }
        if (($values -join "`n") -cne ($Expected -join "`n")) {
            if ($Label -eq 'tier1_modules') { $localReasons += 'Tier 1 modules must be exactly the five classified paths' }
            elseif ($Label -eq 'tier2_gated_modules') { $localReasons += 'Tier 2 modules must be exactly the three classified paths' }
            else { $localReasons += "$Label does not match the reviewed value" }
        }
        return $localReasons
    }
    $isInteger = {
        param($Value)
        return ($Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or $Value -is [int64] -or $Value -is [System.Numerics.BigInteger])
    }

    $reasons += @(& $checkKeys $Policy $expectedTopLevel 'Policy')
    if ($Policy.schema_version -cne '1.0') { $reasons += 'Unsupported or missing policy schema_version' }
    if ($Policy.policy_id -cne 'chimali-coverage-v1') { $reasons += 'Invalid or missing policy_id' }
    foreach ($threshold in @(
        @{ Name = 'tier1_instruction_min_percent'; Expected = 60 },
        @{ Name = 'tier1_branch_min_percent'; Expected = 60 },
        @{ Name = 'repository_instruction_min_percent'; Expected = 50 },
        @{ Name = 'tier2_instruction_min_percent'; Expected = 20 },
        @{ Name = 'branch_improvement_goal_percent'; Expected = 65 }
    )) {
        $value = $Policy.($threshold.Name)
        if (-not (& $isInteger $value) -or $value -ne $threshold.Expected) {
            $reasons += "Policy $($threshold.Name) must be the reviewed integer value $($threshold.Expected)"
        }
    }
    if ($Policy.tier1_instruction_min_percent -ne 60 -or $Policy.tier1_branch_min_percent -ne 60) { $reasons += 'Tier 1 thresholds must be 60/60' }
    if ($Policy.repository_instruction_min_percent -ne 50) { $reasons += 'Repository instruction threshold must be 50' }

    $reasons += @(& $checkOrderedStrings $Policy.tier1_modules $expectedTier1 'tier1_modules')
    $reasons += @(& $checkOrderedStrings $Policy.tier2_gated_modules $expectedTier2 'tier2_gated_modules')
    $reasons += @(& $checkKeys $Policy.metrics @('primary', 'diagnostic', 'arithmetic') 'metrics')
    $reasons += @(& $checkOrderedStrings $Policy.metrics.primary @('INSTRUCTION', 'BRANCH') 'metrics.primary')
    $reasons += @(& $checkOrderedStrings $Policy.metrics.diagnostic @('LINE') 'metrics.diagnostic')
    if ($Policy.metrics.arithmetic -cne 'arbitrary_precision_integer') { $reasons += 'metrics.arithmetic must be arbitrary_precision_integer' }
    if ($Policy.counter_normalization -cne 'AnalyzedZeroProofV1') { $reasons += 'counter_normalization must be AnalyzedZeroProofV1' }
    $reasons += @(& $checkKeys $Policy.measurement_fingerprint @('algorithm', 'included', 'excluded') 'measurement_fingerprint')
    if ($Policy.measurement_fingerprint.algorithm -cne 'sha256_canonical_json_v1') { $reasons += 'measurement_fingerprint.algorithm must be sha256_canonical_json_v1' }
    $reasons += @(& $checkOrderedStrings $Policy.measurement_fingerprint.included @('metrics', 'counter_normalization', 'production_inventory_rules', 'production_scope', 'generated_exclusion_rules', 'required_workload', 'device_requirements', 'harness.file_sha256', 'toolchain.java', 'toolchain.gradle', 'toolchain.android_gradle_plugin', 'toolchain.kotlin', 'toolchain.jacoco', 'runtime_requirements', 'toolchain.runtime', 'device.api_level', 'device.abi') 'measurement_fingerprint.included')
    $reasons += @(& $checkOrderedStrings $Policy.measurement_fingerprint.excluded @('source_contents', 'test_contents', 'test_counts', 'test_names', 'class_bytes') 'measurement_fingerprint.excluded')

    $reasons += @(& $checkKeys $Policy.production_inventory_rules @('source_sets', 'excluded_source_sets', 'fail_on_unmapped_classes') 'production_inventory_rules')
    $reasons += @(& $checkOrderedStrings $Policy.production_inventory_rules.source_sets @('commonMain', 'androidMain', 'main') 'production_inventory_rules.source_sets')
    $reasons += @(& $checkOrderedStrings $Policy.production_inventory_rules.excluded_source_sets @('iosMain', 'macrobenchmark') 'production_inventory_rules.excluded_source_sets')
    if ($Policy.production_inventory_rules.fail_on_unmapped_classes -isnot [bool] -or -not $Policy.production_inventory_rules.fail_on_unmapped_classes) { $reasons += 'production_inventory_rules.fail_on_unmapped_classes must be true' }
    $reasons += @(& $checkKeys $Policy.production_scope @('selection_mode', 'compilations', 'source_roots', 'variants') 'production_scope')
    if ($Policy.production_scope.selection_mode -cne 'configured_compiler_outputs') { $reasons += 'production_scope.selection_mode must be configured_compiler_outputs' }
    $configuredRoots = @($Policy.production_scope.source_roots | ForEach-Object { ([string]$_).Replace('\', '/') })
    if (@($configuredRoots | Select-Object -Unique).Count -ne $configuredRoots.Count) { $reasons += 'production_scope.source_roots contains duplicate entries' }
    foreach ($requiredRoot in $expectedRoots) { if ($requiredRoot -notin $configuredRoots) { $reasons += "production_scope.source_roots is missing required root '$requiredRoot'" } }
    foreach ($configuredRoot in $configuredRoots) {
        if ([string]::IsNullOrWhiteSpace($configuredRoot) -or $configuredRoot -notmatch '^src/[A-Za-z0-9_.-]+/(kotlin|java)$' -or $configuredRoot -match '(^|/)\.\.(/|$)') {
            $reasons += "production_scope.source_roots contains an invalid source root '$configuredRoot'"
        }
    }
    $configuredCompilations = @($Policy.production_scope.compilations.PSObject.Properties.Name)
    foreach ($compilation in @('shrunkDebug', 'androidMain', 'release')) {
        if ($compilation -notin $configuredCompilations) { $reasons += "production_scope.compilations is missing required compilation '$compilation'" }
    }
    foreach ($compilationProperty in $Policy.production_scope.compilations.PSObject.Properties) {
        $compilationName = [string]$compilationProperty.Name
        $compilationConfig = $compilationProperty.Value
        $reasons += @(& $checkKeys $compilationConfig @('compiler_tasks', 'test_tasks') "production_scope.compilations.$compilationName")
        foreach ($taskField in @('compiler_tasks', 'test_tasks')) {
            $taskNames = @($compilationConfig.$taskField | ForEach-Object { [string]$_ })
            if (@($taskNames | Select-Object -Unique).Count -ne $taskNames.Count) { $reasons += "production_scope.compilations.$compilationName.$taskField contains duplicate entries" }
            foreach ($taskName in $taskNames) {
                if ($taskName -notmatch '^[A-Za-z][A-Za-z0-9_-]*$') { $reasons += "production_scope.compilations.$compilationName.$taskField contains invalid task name '$taskName'" }
            }
        }
        if (@($compilationConfig.compiler_tasks).Count -eq 0) { $reasons += "production_scope.compilations.$compilationName.compiler_tasks must not be empty" }
        if (@($compilationConfig.test_tasks).Count -eq 0) { $reasons += "production_scope.compilations.$compilationName.test_tasks must not be empty" }
    }
    $configuredVariants = $Policy.production_scope.variants
    $configuredModulePaths = @($configuredVariants.PSObject.Properties.Name)
    foreach ($module in $expectedVariants.Keys) {
        if ($module -notin $configuredModulePaths) { $reasons += "production_scope.variants is missing required module '$module'" }
        elseif ($configuredVariants.$module -cne $expectedVariants[$module]) { $reasons += "production_scope.variants.$module must be $($expectedVariants[$module])" }
    }
    foreach ($module in (@($Policy.tier1_modules) + @($Policy.tier2_gated_modules))) {
        if ($module -notin $configuredModulePaths) { $reasons += "production_scope.variants is missing classified module '$module'" }
    }
    foreach ($module in $configuredModulePaths) {
        if ($module -notmatch '^(:[A-Za-z][A-Za-z0-9_-]*)+$') { $reasons += "production_scope.variants contains invalid Gradle module path '$module'"; continue }
        if ([string]$configuredVariants.$module -notin $configuredCompilations) { $reasons += "production_scope.variants.$module references an unconfigured compilation '$($configuredVariants.$module)'" }
    }

    $reasons += @(& $checkKeys $Policy.generated_exclusion_rules @('exclude_sqldelight', 'allow_handwritten_exclusions') 'generated_exclusion_rules')
    if ($Policy.generated_exclusion_rules.exclude_sqldelight -isnot [bool] -or -not $Policy.generated_exclusion_rules.exclude_sqldelight) { $reasons += 'SQLDelight generated-code exclusion must remain enabled' }
    if ($Policy.generated_exclusion_rules.allow_handwritten_exclusions -isnot [bool] -or $Policy.generated_exclusion_rules.allow_handwritten_exclusions) { $reasons += 'Handwritten production-code exclusions are prohibited' }
    $reasons += @(& $checkKeys $Policy.required_workload @('host_unit_tests', 'connected_device_tests', 'required_device_test_modules', 'optional_device_test_modules', 'device_test_tasks', 'no_source_host_test_proof') 'required_workload')
    if ($Policy.required_workload.host_unit_tests -isnot [bool] -or -not $Policy.required_workload.host_unit_tests -or $Policy.required_workload.connected_device_tests -isnot [bool] -or -not $Policy.required_workload.connected_device_tests) { $reasons += 'Host and connected-device workloads are both mandatory' }
    $reasons += @(& $checkOrderedStrings $Policy.required_workload.required_device_test_modules @(':feature:fido2', ':core:common', ':feature:settings') 'required_workload.required_device_test_modules')
    $reasons += @(& $checkOrderedStrings $Policy.required_workload.optional_device_test_modules @(':app') 'required_workload.optional_device_test_modules')
    $expectedDeviceTaskModules = @($Policy.required_workload.required_device_test_modules) + @($Policy.required_workload.optional_device_test_modules)
    $actualDeviceTaskModules = @($Policy.required_workload.device_test_tasks.PSObject.Properties.Name)
    if ((@($actualDeviceTaskModules | Sort-Object -CaseSensitive) -join "`n") -cne (@($expectedDeviceTaskModules | Sort-Object -CaseSensitive) -join "`n")) { $reasons += 'required_workload.device_test_tasks must name exactly every required and optional device module' }
    foreach ($deviceModule in $expectedDeviceTaskModules) {
        if ([string]::IsNullOrWhiteSpace([string]$Policy.required_workload.device_test_tasks.$deviceModule) -or [string]$Policy.required_workload.device_test_tasks.$deviceModule -notmatch '^connected[A-Za-z0-9]+AndroidTest$|^connectedAndroidDeviceTest$') {
            $reasons += "required_workload.device_test_tasks has an invalid exact task name for $deviceModule"
        }
    }
    if ($Policy.required_workload.no_source_host_test_proof -cne 'GradleNoSourceWithZeroCompiledTestClasses') { $reasons += 'required_workload.no_source_host_test_proof must require Gradle NO-SOURCE plus zero compiled test classes' }

    $reasons += @(& $checkKeys $Policy.device_requirements @('serial_source', 'must_be_online_and_authorized', 'minimum_api_level', 'approved_abis') 'device_requirements')
    if ($Policy.device_requirements.serial_source -cne 'explicit_parameter' -or $Policy.device_requirements.must_be_online_and_authorized -isnot [bool] -or -not $Policy.device_requirements.must_be_online_and_authorized) { $reasons += 'Device selection must use an explicit online, authorized device' }
    if (-not (& $isInteger $Policy.device_requirements.minimum_api_level) -or $Policy.device_requirements.minimum_api_level -ne 35) { $reasons += 'Device minimum_api_level must be integer 35' }
    $reasons += @(& $checkOrderedStrings $Policy.device_requirements.approved_abis @('arm64-v8a', 'x86_64') 'device_requirements.approved_abis')
    $reasons += @(& $checkKeys $Policy.harness_requirements @('must_be_clean', 'must_match_source_revision', 'required_files') 'harness_requirements')
    if ($Policy.harness_requirements.must_be_clean -isnot [bool] -or -not $Policy.harness_requirements.must_be_clean -or $Policy.harness_requirements.must_match_source_revision -isnot [bool] -or -not $Policy.harness_requirements.must_match_source_revision) { $reasons += 'Harness must be clean and match the measured source revision' }
    $reasons += @(& $checkOrderedStrings $Policy.harness_requirements.required_files $expectedHarnessFiles 'harness_requirements.required_files')
    $reasons += @(& $checkKeys $Policy.runtime_requirements @('gradle_daemon_java_version', 'gradle_daemon_vendor', 'kotlin_execution_strategy', 'java_compiler_version', 'kotlin_compiler_java_version', 'host_test_java_version', 'jacoco_engine') 'runtime_requirements')
    if ($Policy.runtime_requirements.gradle_daemon_java_version -cne '21' -or $Policy.runtime_requirements.gradle_daemon_vendor -cne 'JetBrains' -or
        $Policy.runtime_requirements.kotlin_execution_strategy -cne 'in-process' -or $Policy.runtime_requirements.java_compiler_version -cne '21' -or
        $Policy.runtime_requirements.kotlin_compiler_java_version -cne '21' -or $Policy.runtime_requirements.host_test_java_version -cne '21' -or
        $Policy.runtime_requirements.jacoco_engine -cne 'jacoco-0.8.15') { $reasons += 'runtime_requirements differs from the approved Gradle/compiler/test JVM and JaCoCo identities' }
    if ($Policy.regression_rule -cne 'nondecreasing_applicable_ratios') { $reasons += 'Unsupported regression rule' }

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

    return @{ IsValid = ($reasons.Count -eq 0); Reasons = $reasons }
}

function Test-DeviceCapabilities {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$ApiLevel,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Abi
    )

    $errors = @()
    if ($ApiLevel -notmatch '^(0|[1-9][0-9]*)$') {
        $errors += "Device API level is missing or malformed: '$ApiLevel'"
    } elseif ([System.Numerics.BigInteger]::Parse($ApiLevel, [Globalization.CultureInfo]::InvariantCulture) -lt [System.Numerics.BigInteger]::Parse(([string]$Policy.device_requirements.minimum_api_level), [Globalization.CultureInfo]::InvariantCulture)) {
        $errors += "Device API level $ApiLevel is below minimum $($Policy.device_requirements.minimum_api_level)"
    }
    if ($Abi -notin @($Policy.device_requirements.approved_abis)) {
        $errors += "Device ABI '$Abi' is not approved"
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Get-MeasurementFingerprint {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        [Parameter(Mandatory = $true)]$Toolchain,
        [Parameter(Mandatory = $true)]$Device,
        [AllowNull()]
        [Parameter(Mandatory = $true)]$HarnessFileDigests
    )

    $harnessProfile = [ordered]@{}
    foreach ($harnessPath in @($Policy.harness_requirements.required_files)) {
        $digest = [string]$HarnessFileDigests.$harnessPath
        if ($digest -notmatch '^[0-9a-fA-F]{64}$') { return '' }
        $harnessProfile[$harnessPath] = $digest.ToLowerInvariant()
    }
    $profile = [ordered]@{
        metrics = $Policy.metrics
        counter_normalization = $Policy.counter_normalization
        production_inventory_rules = $Policy.production_inventory_rules
        production_scope = $Policy.production_scope
        generated_exclusion_rules = $Policy.generated_exclusion_rules
        required_workload = $Policy.required_workload
        device_requirements = $Policy.device_requirements
        runtime_requirements = $Policy.runtime_requirements
        harness_file_sha256 = $harnessProfile
        analyzer = [string]$Toolchain.jacoco
        gradle = [string]$Toolchain.gradle
        android_gradle_plugin = [string]$Toolchain.android_gradle_plugin
        kotlin = [string]$Toolchain.kotlin
        java = [string]$Toolchain.java
        toolchain_runtime = $Toolchain.runtime
        device_api = [string]$Device.api_level
        device_abi = [string]$Device.abi
    }
    $canonicalJson = ConvertTo-Json -InputObject $profile -Depth 30 -Compress
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($canonicalJson)
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Test-MeasurementRuntime {
    param(
        [Parameter(Mandatory = $true)]$Runtime,
        [Parameter(Mandatory = $true)]$Policy
    )

    $errors = @()
    $requirements = $Policy.runtime_requirements
    if ($null -eq $Runtime -or $Runtime.schema_version -cne '1') {
        return @{ IsValid = $false; Errors = @('Measured runtime identity is missing or has an unsupported schema') }
    }
    $daemon = $Runtime.gradle_daemon
    foreach ($field in @('java_version', 'java_runtime_version', 'java_vendor', 'vm_name', 'architecture')) {
        if ([string]::IsNullOrWhiteSpace([string]$daemon.$field) -or [string]$daemon.$field -match '(?i)^unknown$') {
            $errors += "Gradle daemon runtime is missing a known $field"
        }
    }
    if ([string]$daemon.java_version -cne [string]$requirements.gradle_daemon_java_version -or
        [string]$daemon.java_vendor -notmatch [string]$requirements.gradle_daemon_vendor) {
        $errors += 'Gradle daemon JVM does not match the approved runtime requirement'
    }
    if ([string]$Runtime.kotlin_execution_strategy -cne [string]$requirements.kotlin_execution_strategy) {
        $errors += 'Kotlin compiler execution strategy does not match the approved in-process requirement'
    }

    $expectedCompilerTasks = @()
    $expectedHostTasks = @()
    foreach ($moduleProperty in $Policy.production_scope.variants.PSObject.Properties) {
        $modulePath = [string]$moduleProperty.Name
        $compilation = $Policy.production_scope.compilations.($moduleProperty.Value)
        $expectedCompilerTasks += @($compilation.compiler_tasks | ForEach-Object { "$modulePath`:$($_)" })
        $expectedHostTasks += @($compilation.test_tasks | ForEach-Object { "$modulePath`:$($_)" })
    }
    $expectedCompilerTasks = @($expectedCompilerTasks | Sort-Object -Unique)
    $expectedHostTasks = @($expectedHostTasks | Sort-Object -Unique)

    $javaCompilers = @($Runtime.java_compilers)
    $kotlinCompilers = @($Runtime.kotlin_compilers)
    $compilerRecords = @($javaCompilers) + @($kotlinCompilers)
    $compilerPaths = @($compilerRecords | ForEach-Object { [string]$_.task_path } | Sort-Object -Unique)
    if (($compilerPaths -join "`n") -cne ($expectedCompilerTasks -join "`n") -or $compilerRecords.Count -ne $expectedCompilerTasks.Count) {
        $errors += 'Measured compiler JVM records do not exactly cover the configured compiler task inventory'
    }
    foreach ($compiler in $javaCompilers) {
        if ([string]$compiler.java_version -cne [string]$requirements.java_compiler_version -or
            [string]$compiler.java_runtime_version -notmatch "^$([regex]::Escape([string]$requirements.java_compiler_version))(\.|$)" -or
            [string]::IsNullOrWhiteSpace([string]$compiler.java_vendor)) {
            $errors += "Java compiler task has an unknown or unapproved runtime: $($compiler.task_path)"
        }
    }
    foreach ($compiler in $kotlinCompilers) {
        if ([string]$compiler.execution_strategy -cne [string]$requirements.kotlin_execution_strategy -or
            [string]$compiler.java_version -cne [string]$requirements.kotlin_compiler_java_version -or
            [string]$compiler.java_runtime_version -notmatch "^$([regex]::Escape([string]$requirements.kotlin_compiler_java_version))(\.|$)" -or
            [string]::IsNullOrWhiteSpace([string]$compiler.java_vendor) -or [string]::IsNullOrWhiteSpace([string]$compiler.jvm_target)) {
            $errors += "Kotlin compiler task has an unknown or unapproved runtime: $($compiler.task_path)"
        }
    }

    $hostTests = @($Runtime.host_test_jvms)
    $hostTestPaths = @($hostTests | ForEach-Object { [string]$_.task_path } | Sort-Object -Unique)
    if (($hostTestPaths -join "`n") -cne ($expectedHostTasks -join "`n") -or $hostTests.Count -ne $expectedHostTasks.Count) {
        $errors += 'Measured host-test JVM records do not exactly cover the configured host-test task inventory'
    }
    foreach ($hostTest in $hostTests) {
        if ([string]$hostTest.java_version -cne [string]$requirements.host_test_java_version -or
            [string]$hostTest.java_runtime_version -notmatch "^$([regex]::Escape([string]$requirements.host_test_java_version))(\.|$)" -or
            [string]::IsNullOrWhiteSpace([string]$hostTest.java_vendor) -or
            [string]$hostTest.identity_source -notin @('GradleDaemon', 'JavaLauncher')) {
            $errors += "Host-test task has an unknown or unapproved JVM: $($hostTest.task_path)"
        }
    }
    $jacoco = $Runtime.jacoco_analyzer
    if ([string]$jacoco.engine -cne [string]$requirements.jacoco_engine -or
        [string]$jacoco.java_version -cne [string]$requirements.gradle_daemon_java_version -or
        [string]$jacoco.java_runtime_version -cne [string]$daemon.java_runtime_version -or
        [string]$jacoco.java_vendor -cne [string]$daemon.java_vendor) {
        $errors += 'JaCoCo analyzer identity is missing or differs from the measured Gradle daemon runtime'
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-MeasurementRuntimeBinding {
    param(
        [Parameter(Mandatory = $true)]$Counters,
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)]$Policy
    )
    $errors = @()
    $counterRuntime = $Counters.measurement_runtime
    $manifestRuntime = $Manifest.toolchain.runtime
    if ($null -eq $counterRuntime -or $null -eq $manifestRuntime) {
        return @{ IsValid = $false; Errors = @('Measured runtime identity is missing from counters or manifest') }
    }
    $validation = Test-MeasurementRuntime -Runtime $counterRuntime -Policy $Policy
    $errors += $validation.Errors
    $counterJson = ConvertTo-Json -InputObject $counterRuntime -Depth 30 -Compress
    $manifestJson = ConvertTo-Json -InputObject $manifestRuntime -Depth 30 -Compress
    if ($counterJson -cne $manifestJson) { $errors += 'Manifest JVM/compiler runtime identity differs from canonical counters' }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ExecutionFileAttribution {
    param(
        [Parameter(Mandatory = $true)][AllowNull()][AllowEmptyCollection()]$ExecutionFiles,
        [Parameter(Mandatory = $true)]$Artifacts
    )
    $errors = @()
    $records = @($ExecutionFiles)
    $artifactRecords = @($Artifacts)
    if ($records.Count -eq 0) { $errors += 'Canonical source-to-bundle execution-file attribution is missing' }
    $mappedPaths = @()
    $recordIdentities = @{}
    foreach ($record in $records) {
        $source = [string]$record.source_path
        $bundle = ([string]$record.bundle_path).Replace('\', '/')
        $kind = [string]$record.kind
        $scope = [string]$record.scope
        if ([string]::IsNullOrWhiteSpace($scope) -or [string]::IsNullOrWhiteSpace($source) -or
            $source -match '(^|[/\\])\.\.([/\\]|$)' -or [System.IO.Path]::IsPathRooted($source) -or
            $bundle -notmatch '^execution-data/.+\.(exec|ec)$' -or
            ($kind -eq 'host' -and -not $bundle.EndsWith('.exec', [System.StringComparison]::OrdinalIgnoreCase)) -or
            ($kind -eq 'device' -and -not $bundle.EndsWith('.ec', [System.StringComparison]::OrdinalIgnoreCase)) -or
            $kind -notin @('host', 'device') -or
            $record.source_sha256 -notmatch '^[0-9a-f]{64}$' -or
            $record.bundle_sha256 -cne $record.source_sha256) {
            $errors += 'Execution-file attribution has invalid scope, relative paths, kind, or source/bundle digest binding'
            continue
        }
        $identity = "$scope`n$source`n$bundle"
        if ($recordIdentities.ContainsKey($identity)) { $errors += "Duplicate execution-file attribution: $bundle"; continue }
        $recordIdentities[$identity] = $true
        $matches = @($artifactRecords | Where-Object { ([string]$_.path).Replace('\', '/') -ceq $bundle })
        if ($matches.Count -ne 1 -or $matches[0].sha256 -cne $record.bundle_sha256) {
            $errors += "Execution-file attribution does not bind exactly one sealed artifact: $bundle"
            continue
        }
        $mappedPaths += $bundle
    }
    $expectedPaths = @($artifactRecords | Where-Object { $_.path -match '^execution-data/.+\.(exec|ec)$' } | ForEach-Object { ([string]$_.path).Replace('\', '/') } | Sort-Object -Unique)
    $mappedPaths = @($mappedPaths | Sort-Object -Unique)
    if (($expectedPaths -join "`n") -cne ($mappedPaths -join "`n")) {
        $errors += 'Retained execution-data artifacts do not exactly match the canonical source attribution map'
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Get-TaskOwnedJUnitReports {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]$Workload
    )

    $errors = @()
    $filesByPath = @{}
    $records = @()
    $records += @($Workload.host_test_tasks | Where-Object { $_.disposition -eq 'Applicable' -and $_.executed -eq $true })
    $records += @($Workload.required_device_tasks | Where-Object { $_.executed -eq $true })
    $records += @($Workload.optional_device_tasks | Where-Object { $_.executed -eq $true })
    $rootPath = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $rootPrefix = $rootPath + [System.IO.Path]::DirectorySeparatorChar

    foreach ($record in $records) {
        $relativeDirectory = [string]$record.junit_xml_directory
        if ([string]::IsNullOrWhiteSpace($relativeDirectory) -or [System.IO.Path]::IsPathRooted($relativeDirectory) -or
            $relativeDirectory.Replace('\', '/') -match '(^|/)\.\.(/|$)') {
            $errors += "Executed workload has no safe task-owned JUnit output directory: $($record.task_path)"
            continue
        }

        try {
            $fullDirectory = [System.IO.Path]::GetFullPath((Join-Path $rootPath $relativeDirectory))
        } catch {
            $errors += "Task-owned JUnit output directory is invalid for $($record.task_path): $relativeDirectory"
            continue
        }
        if (-not $fullDirectory.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $errors += "Task-owned JUnit output directory escapes the repository: $relativeDirectory"
            continue
        }
        if (-not (Test-Path -LiteralPath $fullDirectory -PathType Container)) {
            $errors += "Executed workload JUnit output directory is missing: $($record.task_path) ($relativeDirectory)"
            continue
        }

        $ownedFiles = @(Get-ChildItem -LiteralPath $fullDirectory -Filter 'TEST-*.xml' -File -Recurse)
        if ($ownedFiles.Count -eq 0) {
            $errors += "Executed workload produced no task-owned JUnit XML: $($record.task_path) ($relativeDirectory)"
            continue
        }
        $actualTests = 0
        foreach ($ownedFile in $ownedFiles) {
            $outcome = Get-StrictJUnitOutcome -Path $ownedFile.FullName -ReportIdentity $ownedFile.FullName
            if (-not $outcome.IsValid) {
                $errors += "Task-owned JUnit report is invalid for $($record.task_path): $($outcome.Errors -join '; ')"
                continue
            }
            $actualTests += [int]$outcome.Totals.tests
        }
        $requiredDevicePaths = @($Workload.required_device_tasks | ForEach-Object { [string]$_.task_path })
        $requiredTask = ($record.disposition -eq 'Applicable') -or ([string]$record.task_path -in $requiredDevicePaths)
        if ($requiredTask -and $actualTests -le 0) {
            $errors += "Required workload task produced zero testcase executions: $($record.task_path)"
            continue
        }
        foreach ($file in $ownedFiles) { $filesByPath[$file.FullName] = $file }
    }

    return @{
        Files = @($filesByPath.Values | Sort-Object -Property FullName)
        Errors = $errors
    }
}

function Test-WorkloadTaskInventory {
    param(
        [Parameter(Mandatory = $true)]$Workload,
        [Parameter(Mandatory = $true)]$Policy
    )

    $errors = @()
    $expectedHostPaths = @()
    foreach ($modulePath in @($Policy.production_scope.variants.PSObject.Properties.Name)) {
        $variant = [string]$Policy.production_scope.variants.$modulePath
        $compilation = $Policy.production_scope.compilations.$variant
        if (-not $compilation -or @($compilation.test_tasks).Count -eq 0) {
            $errors += "Trusted policy has no configured host test tasks for $modulePath ($variant)"
            continue
        }
        foreach ($taskName in @($compilation.test_tasks)) { $expectedHostPaths += "${modulePath}:$taskName" }
    }
    $actualHostPaths = @($Workload.host_test_tasks | ForEach-Object { [string]$_.task_path })
    if (@($actualHostPaths | Sort-Object -Unique).Count -ne $actualHostPaths.Count) { $errors += 'Host workload contains duplicate task records' }
    $actualHostKey = (@($actualHostPaths | Sort-Object -CaseSensitive) -join "`n")
    $expectedHostKey = (@($expectedHostPaths | Sort-Object -CaseSensitive) -join "`n")
    if ($actualHostKey -cne $expectedHostKey) {
        $missing = @($expectedHostPaths | Where-Object { $_ -notin $actualHostPaths })
        $extra = @($actualHostPaths | Where-Object { $_ -notin $expectedHostPaths })
        $errors += "Host workload task inventory differs from trusted policy (missing: $($missing -join ', '); extra: $($extra -join ', '))"
    }

    foreach ($category in @(
        @{ name = 'required_device_tasks'; modules = @($Policy.required_workload.required_device_test_modules); required = $true },
        @{ name = 'optional_device_tasks'; modules = @($Policy.required_workload.optional_device_test_modules); required = $false }
    )) {
        $records = @($Workload.($category.name))
        $recordModules = @($records | ForEach-Object { [string]$_.module_path })
        $recordModuleKey = (@($recordModules | Sort-Object -CaseSensitive) -join "`n")
        $configuredModuleKey = (@($category.modules | Sort-Object -CaseSensitive) -join "`n")
        if (@($recordModules | Sort-Object -Unique).Count -ne $recordModules.Count -or $recordModuleKey -cne $configuredModuleKey) {
            $errors += "$($category.name) does not exactly match the trusted configured module set"
        }
        foreach ($record in $records) {
            $modulePath = [string]$record.module_path
            $taskPath = [string]$record.task_path
            $expectedTaskName = [string]$Policy.required_workload.device_test_tasks.$modulePath
            if ([string]::IsNullOrWhiteSpace($modulePath) -or $modulePath -notin $category.modules -or
                $taskPath -cne "${modulePath}:$expectedTaskName") {
                $errors += "$($category.name) contains an unowned or unconfigured task: $taskPath"
            }
            $junitDirectory = ([string]$record.junit_xml_directory).Replace('\', '/').TrimEnd('/')
            if (-not [string]::IsNullOrWhiteSpace($junitDirectory)) {
                $projectDirectory = $modulePath.TrimStart(':').Replace(':', '/')
                if ($category.required) {
                    if (-not $junitDirectory.StartsWith("$projectDirectory/build/outputs/androidTest-results/connected/", [System.StringComparison]::OrdinalIgnoreCase)) {
                        $errors += "Required device task JUnit root is not owned by $modulePath`: $junitDirectory"
                    }
                } elseif (-not $junitDirectory.StartsWith("$projectDirectory/build/outputs/androidTest-results/connected/", [System.StringComparison]::OrdinalIgnoreCase)) {
                    $errors += "Optional device task JUnit root is not owned by $modulePath`: $junitDirectory"
                }
            }
        }
    }
    foreach ($record in @($Workload.host_test_tasks)) {
        $taskPath = [string]$record.task_path
        $junitDirectory = ([string]$record.junit_xml_directory).Replace('\', '/').TrimEnd('/')
        if ($record.disposition -eq 'Applicable' -and -not [string]::IsNullOrWhiteSpace($junitDirectory)) {
            $separator = $taskPath.LastIndexOf(':')
            $modulePath = if ($separator -gt 0) { $taskPath.Substring(0, $separator) } else { '' }
            $taskName = if ($separator -ge 0) { $taskPath.Substring($separator + 1) } else { '' }
            $expectedRoot = "$($modulePath.TrimStart(':').Replace(':', '/'))/build/test-results/$taskName"
            if ($junitDirectory -cne $expectedRoot) { $errors += "Host task JUnit root does not match its configured task $taskPath`: $junitDirectory" }
        }
        if ($record.disposition -eq 'NotApplicable') {
            $proof = $record.test_scope_proof
            if ($record.skip_message -cne 'NO-SOURCE' -or -not $proof -or $proof.task_path -cne $taskPath -or $proof.gradle_no_source -ne $true -or
                ($proof.compiled_test_class_count -isnot [int] -and $proof.compiled_test_class_count -isnot [long]) -or $proof.compiled_test_class_count -ne 0) {
                $errors += "Non-applicable host task lacks the trusted empty-scope proof required by policy: $taskPath"
            }
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Get-StrictJUnitOutcome {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ReportIdentity
    )
    try {
        $document = Read-SafeXml -Path $Path
        $root = $document.DocumentElement
        if (-not $root -or $root.LocalName -notin @('testsuite', 'testsuites')) { throw 'JUnit root must be <testsuite> or <testsuites>' }
        $suiteNodes = @($document.SelectNodes('/testsuite | /testsuites/testsuite'))
        if ($suiteNodes.Count -eq 0) {
            if ($root.LocalName -eq 'testsuites' -and $root.ChildNodes.Count -eq 0 -and
                $root.GetAttribute('tests') -eq '0' -and $root.GetAttribute('failures') -eq '0' -and
                $root.GetAttribute('errors') -eq '0' -and $root.GetAttribute('skipped') -eq '0') {
                return @{ IsValid = $true; IsEmpty = $true; Suites = @(); Totals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }; Errors = @() }
            }
            throw 'JUnit report contains no testsuite nodes and is not a canonical empty aggregate'
        }
        $suites = @()
        $totals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
        for ($index = 0; $index -lt $suiteNodes.Count; $index++) {
            $node = $suiteNodes[$index]
            $name = $node.GetAttribute('name')
            if ([string]::IsNullOrWhiteSpace($name)) { throw "JUnit testsuite $index has no name" }
            $testCases = @($node.SelectNodes('./testcase'))
            foreach ($testCase in $testCases) {
                $outcomeCount = @(@('failure', 'error', 'skipped') | ForEach-Object { $testCase.SelectSingleNode("./$_") } | Where-Object { $null -ne $_ }).Count
                if ($outcomeCount -gt 1) { throw "JUnit testcase '$($testCase.GetAttribute('name'))' declares conflicting failure/error/skip outcomes" }
            }
            $counts = @{
                tests = $testCases.Count
                failures = @($testCases | Where-Object { $null -ne $_.SelectSingleNode('./failure') }).Count
                errors = @($testCases | Where-Object { $null -ne $_.SelectSingleNode('./error') }).Count
                skipped = @($testCases | Where-Object { $null -ne $_.SelectSingleNode('./skipped') }).Count
            }
            $counts.executed = $counts.tests - $counts.skipped
            $counts.successful = $counts.tests - $counts.failures - $counts.errors - $counts.skipped
            foreach ($metric in @('tests', 'failures', 'errors', 'skipped')) {
                if ($node.HasAttribute($metric)) {
                    $declared = [System.Numerics.BigInteger]::Parse($node.GetAttribute($metric), [Globalization.CultureInfo]::InvariantCulture)
                    if ($declared -lt 0 -or $declared -ne $counts[$metric]) { throw "JUnit suite $name declared $metric=$declared but testcase nodes derive $($counts[$metric])" }
                }
                $totals[$metric] += $counts[$metric]
            }
            $suites += [PSCustomObject]@{
                suite_identity = "$ReportIdentity|$index|$name"
                suite_name = $name
                tests = $counts.tests
                executed = $counts.executed
                successful = $counts.successful
                failed = $counts.failures
                failures = $counts.failures
                errors = $counts.errors
                skipped = $counts.skipped
            }
        }
        if ($root.LocalName -eq 'testsuites') {
            foreach ($metric in @('tests', 'failures', 'errors', 'skipped')) {
                if ($root.HasAttribute($metric)) {
                    $declared = [System.Numerics.BigInteger]::Parse($root.GetAttribute($metric), [Globalization.CultureInfo]::InvariantCulture)
                    if ($declared -lt 0 -or $declared -ne $totals[$metric]) { throw "JUnit testsuites declared $metric=$declared but testcase nodes derive $($totals[$metric])" }
                }
            }
        }
        $totals.executed = $totals.tests - $totals.skipped
        $totals.successful = $totals.tests - $totals.failures - $totals.errors - $totals.skipped
        return @{ IsValid = $true; IsEmpty = $false; Suites = $suites; Totals = $totals; Errors = @() }
    } catch {
        return @{ IsValid = $false; IsEmpty = $false; Suites = @(); Totals = $null; Errors = @([string]$_.Exception.Message) }
    }
}

function Test-WorkloadJUnitArtifactBinding {
    param(
        [Parameter(Mandatory = $true)]$Workload,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$ArtifactPaths
    )

    $errors = @()
    $activeRecords = @()
    $activeRecords += @($Workload.host_test_tasks | Where-Object { $_.disposition -eq 'Applicable' -and $_.executed -eq $true })
    $activeRecords += @($Workload.required_device_tasks | Where-Object { $_.executed -eq $true })
    $activeRecords += @($Workload.optional_device_tasks | Where-Object { $_.executed -eq $true })
    $roots = @()

    foreach ($record in $activeRecords) {
        $relativeDirectory = ([string]$record.junit_xml_directory).Replace('\', '/').TrimEnd('/')
        if ([string]::IsNullOrWhiteSpace($relativeDirectory) -or $relativeDirectory.StartsWith('/') -or
            $relativeDirectory -match '(^|/)\.\.(/|$)') {
            $errors += "Executed workload has no safe task-owned JUnit artifact directory: $($record.task_path)"
            continue
        }
        $root = "reports/tests/$relativeDirectory/"
        $roots += $root
        if (-not @($ArtifactPaths | Where-Object { $_.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase) -and $_ -match '\.xml$' }).Count) {
            $errors += "No retained JUnit artifact is bound to executed workload $($record.task_path)"
        }
    }

    foreach ($artifactPath in @($ArtifactPaths | Where-Object { $_ -like 'reports/tests/*.xml' })) {
        $normalizedArtifact = ([string]$artifactPath).Replace('\', '/')
        if (-not @($roots | Where-Object { $normalizedArtifact.StartsWith($_, [System.StringComparison]::OrdinalIgnoreCase) }).Count) {
            $errors += "Retained JUnit artifact is not owned by a recorded workload: $artifactPath"
        }
    }

    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-WorkloadSuiteExecutionCounts {
    param(
        [Parameter(Mandatory = $true)]$Workload,
        [Parameter(Mandatory = $true)]$Suites
    )

    $errors = @()
    $requiredRecords = @($Workload.host_test_tasks | Where-Object { $_.disposition -eq 'Applicable' }) + @($Workload.required_device_tasks)
    foreach ($record in $requiredRecords) {
        $root = ([string]$record.junit_xml_directory).Replace('\', '/').TrimEnd('/')
        if ([string]::IsNullOrWhiteSpace($root)) { continue }
        $ownedSuites = @($Suites | Where-Object { ([string]$_.suite_identity).StartsWith("$root/", [System.StringComparison]::OrdinalIgnoreCase) })
        $testCount = [System.Numerics.BigInteger]::Zero
        foreach ($suite in $ownedSuites) {
            try { $testCount += [System.Numerics.BigInteger]::Parse([string]$suite.tests, [Globalization.CultureInfo]::InvariantCulture) }
            catch { $errors += "Workload suite has invalid test count for $($record.task_path)" }
        }
        if ($ownedSuites.Count -eq 0 -or $testCount -le [System.Numerics.BigInteger]::Zero) {
            $errors += "Required workload task has no actual testcase outcomes: $($record.task_path)"
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ManifestSchema {
    param([Parameter(Mandatory = $true)]$Manifest, $Policy, [string]$TrustedHarnessRoot)

    $errors = @()

    $hasProperty = {
        param($Object, [string]$Name)
        return $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
    }
    $isSha256 = { param($Value) $Value -is [string] -and $Value -match '^[0-9a-fA-F]{64}$' }
    $isRevision = { param($Value) $Value -is [string] -and $Value -match '^[0-9a-fA-F]{40}$' }

    if (-not $Manifest.schema_version -or $Manifest.schema_version -notin $global:SUPPORTED_SCHEMA_VERSIONS) {
        $errors += "Unsupported schema version: $($Manifest.schema_version)"
    }

    if (-not $Manifest.purpose -or $Manifest.purpose -notin $global:SUPPORTED_PURPOSES) {
        $errors += "Invalid purpose: $($Manifest.purpose)"
    }

    if (-not $Manifest.run_id -or ($Manifest.run_id -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')) {
        $errors += "Invalid or missing run_id: $($Manifest.run_id)"
    }

    if (-not $Manifest.policy_id -or $Manifest.policy_id -ne 'chimali-coverage-v1') {
        $errors += 'Missing or unsupported policy_id'
    }
    $toolchainFields = @('java', 'gradle', 'android_gradle_plugin', 'kotlin', 'jacoco')
    $missingToolchainField = $false
    foreach ($field in $toolchainFields) { if ([string]::IsNullOrWhiteSpace([string]$Manifest.toolchain.$field)) { $missingToolchainField = $true } }
    if ($Manifest.measurement_fingerprint -notmatch '^[0-9a-f]{64}$' -or -not $Manifest.toolchain -or $missingToolchainField) {
        $errors += 'Missing or malformed canonical measurement fingerprint/toolchain identity'
    } elseif ($Policy -and $Manifest.device) {
        $expectedMeasurementFingerprint = Get-MeasurementFingerprint -Policy $Policy -Toolchain $Manifest.toolchain -Device $Manifest.device -HarnessFileDigests $Manifest.harness.file_sha256
        if ($Manifest.measurement_fingerprint -cne $expectedMeasurementFingerprint) {
            $errors += 'Measurement fingerprint does not match trusted policy, toolchain, and device capability profile'
        }
    }
    if ($Manifest.decisions.collection -eq 'Complete' -and $Policy) {
        $runtimeValidation = Test-MeasurementRuntime -Runtime $Manifest.toolchain.runtime -Policy $Policy
        $errors += @($runtimeValidation.Errors)
        if ([string]$Manifest.toolchain.java -cne [string]$Manifest.toolchain.runtime.gradle_daemon.java_runtime_version) {
            $errors += 'Canonical toolchain Java identity does not match the measured Gradle daemon JVM runtime'
        }
    }

    try {
        $startedAt = [DateTimeOffset]::Parse($Manifest.timestamps.start_utc, [Globalization.CultureInfo]::InvariantCulture)
        $endedAt = [DateTimeOffset]::Parse($Manifest.timestamps.end_utc, [Globalization.CultureInfo]::InvariantCulture)
        if ($endedAt -lt $startedAt) { $errors += 'Capture end timestamp precedes start timestamp' }
        if (-not $Manifest.timestamps.display_date) { $errors += 'Missing display timestamp' }
    } catch {
        $errors += 'Missing or invalid capture timestamps'
    }

    if (-not (& $isRevision $Manifest.git.source_commit) -or -not (& $isRevision $Manifest.git.source_tree)) {
        $errors += 'Missing or invalid source revision/tree identity'
    }
    if ($Manifest.purpose -in @('Comparison', 'Candidate') -and -not (& $isRevision $Manifest.git.target_commit)) {
        $errors += 'Missing or invalid target revision identity'
    }
    foreach ($cleanField in @('clean_preflight', 'clean_postflight')) {
        if (-not (& $hasProperty $Manifest.git $cleanField) -or $Manifest.git.$cleanField -isnot [bool]) {
            $errors += "Missing or invalid git cleanliness field: $cleanField"
        }
    }

    if (-not (& $isRevision $Manifest.harness.revision) -or -not (& $isRevision $Manifest.harness.tree) -or $Manifest.harness.clean -isnot [bool]) {
        $errors += 'Missing or invalid reviewed harness revision/tree/clean identity'
    }
    $requiredHarnessFiles = @('tools/coverage/capture.ps1', 'tools/coverage/coverage-common.ps1', 'tools/integration-ci.ps1', 'tools/local-ci.ps1', 'tools/hdk-coverage.init.gradle', 'tools/coverage/policy.json', 'tools/coverage/validate-policy.ps1', 'tools/coverage/verify.ps1', '.github/workflows/coverage.yml', '.github/workflows/coverage-report.yml', 'tools/coverage/report.ps1', 'tools/coverage/validate-candidate-tree.ps1', 'tools/coverage/validate-live-candidate.ps1', 'tools/coverage/validate-live-pr-target.ps1', 'tools/coverage/validate-workflow-run.ps1', 'tools/coverage/validate-integration-event.ps1', 'tools/coverage/resolve-accepted-baseline.ps1', 'tools/coverage/publish.ps1', 'tools/coverage/request-target-recaptures.ps1', 'tools/coverage/validate-dispatched-candidate.ps1')
    foreach ($harnessFile in $requiredHarnessFiles) {
        $hash = if ($Manifest.harness.file_sha256) { $Manifest.harness.file_sha256.$harnessFile } else { $null }
        if (-not (& $isSha256 $hash)) { $errors += "Missing or invalid harness file digest: $harnessFile" }
        if ($TrustedHarnessRoot) {
            $trustedFile = Join-Path $TrustedHarnessRoot ($harnessFile.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
            if (-not (Test-Path -LiteralPath $trustedFile -PathType Leaf)) {
                $errors += "Trusted harness is missing required file: $harnessFile"
            } elseif ((Get-FileSha256 -Path $trustedFile) -cne ([string]$hash).ToLowerInvariant()) {
                $errors += "Manifest harness digest does not match the trusted harness file: $harnessFile"
            }
        }
    }

    if (-not $Manifest.device.device_serial -or -not $Manifest.device.model -or
        -not $Manifest.device.api_level -or -not $Manifest.device.abi -or $Manifest.device.device_online -isnot [bool]) {
        $errors += 'Missing device identity/capability record'
    }
    if ($Policy -and $Manifest.device.api_level -match '^\d+$') {
        if ([int]$Manifest.device.api_level -lt [int]$Policy.device_requirements.minimum_api_level) {
            $errors += 'Measured device API level is below the reviewed minimum'
        }
        if ($Manifest.device.abi -notin $Policy.device_requirements.approved_abis) {
            $errors += 'Measured device ABI is not in the reviewed approved ABI set'
        }
    }
    if ($Manifest.decisions -and $Manifest.decisions.collection -eq 'Complete' -and $Manifest.device.device_online -ne $true) {
        $errors += 'Complete collection requires an online authorized device'
    }

    if ($Manifest.decisions) {
        foreach ($dim in $global:DECISION_STATES.Keys) {
            $val = $Manifest.decisions.$dim
            if ($val -and ($val -notin $global:DECISION_STATES[$dim])) {
                $errors += "Invalid $dim decision status: $val"
            }
        }
    }
    if (& $hasProperty $Manifest 'collection_errors') {
        if ($Manifest.collection_errors -isnot [array]) {
            $errors += 'collection_errors must be an array of nonempty strings'
        } else {
            foreach ($collectionError in $Manifest.collection_errors) {
                if ($collectionError -isnot [string] -or [string]::IsNullOrWhiteSpace($collectionError)) {
                    $errors += 'collection_errors must contain only nonempty strings'
                }
            }
        }
    }
    if ($Manifest.decisions -and $Manifest.decisions.collection -eq 'Incomplete' -and
        (-not (& $hasProperty $Manifest 'collection_errors') -or @($Manifest.collection_errors).Count -eq 0)) {
        $errors += 'Incomplete collection must retain at least one collection_errors reason'
    }

    if (-not $Manifest.evidence_records -or @($Manifest.evidence_records).Count -eq 0) {
        $errors += 'Missing required ten-field evidence records'
    } else {
        foreach ($rec in $Manifest.evidence_records) {
            $recordKeys = @($rec.PSObject.Properties | ForEach-Object { $_.Name })
            if ($recordKeys.Count -ne $global:EXACT_TRD_KEYS.Count -or
                @($recordKeys | Where-Object { $_ -notin $global:EXACT_TRD_KEYS }).Count -gt 0) {
                $errors += 'Evidence record must contain exactly the ten required TRD keys'
            }
            foreach ($key in $global:EXACT_TRD_KEYS) {
                if (-not (& $hasProperty $rec $key) -or $null -eq $rec.$key -or [string]::IsNullOrWhiteSpace([string]$rec.$key)) {
                    $errors += "Missing required TRD evidence key: $key"
                }
                if ($key -eq 'revision' -and -not (& $isRevision $rec.revision)) {
                    $errors += 'Evidence record has invalid revision identity'
                }
                if ($key -eq 'revision' -and $rec.revision -ne $Manifest.git.source_commit) {
                    $errors += 'Evidence record revision does not match the captured source revision'
                }
                if ($key -eq 'status' -and $rec.status -notin @('Passed', 'Failed', 'Unverified')) {
                    $errors += "Invalid evidence status: $($rec.status)"
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

    if (-not $Manifest.artifacts -or @($Manifest.artifacts).Count -eq 0) {
        $errors += 'Missing complete artifact manifest'
    } else {
        $artifactPaths = @()
        foreach ($art in $Manifest.artifacts) {
            if (-not $art.path) {
                $errors += "Artifact entry missing path"
            } elseif ($art.path -match '(^|[/\\])\.\.([/\\]|$)' -or [System.IO.Path]::IsPathRooted($art.path) -or $art.path -match '^[a-zA-Z]:') {
                $errors += "Malformed or escaping artifact path: $($art.path)"
            } elseif ($art.path -in $artifactPaths) {
                $errors += "Duplicate artifact path: $($art.path)"
            }
            if (-not (& $isSha256 $art.sha256)) { $errors += "Missing or invalid artifact digest: $($art.path)" }
            if ($art.path) { $artifactPaths += [string]$art.path }
        }

        $requiredArtifacts = @('counters.json', 'inventory.json', 'summary.md', 'reports/repository.xml')
        foreach ($modulePath in @(':core:security', ':core:domain', ':core:data', ':feature:fido2', ':feature:authenticator', ':core:database', ':core:common', ':app')) {
            $safeName = $modulePath.Replace(':', '_').TrimStart('_')
            $requiredArtifacts += "reports/modules/$safeName.xml"
        }
        foreach ($requiredArtifact in $requiredArtifacts) {
            if ($requiredArtifact -notin $artifactPaths) { $errors += "Required artifact missing from manifest: $requiredArtifact" }
        }
        if (-not ($artifactPaths | Where-Object { $_ -match '^reports/tests/.+\.xml$' })) { $errors += 'No retained JUnit XML artifact is listed' }
        if (-not ($artifactPaths | Where-Object { $_ -match '^execution-data/.+\.(exec|ec)$' })) { $errors += 'No retained execution-data artifact is listed' }
        foreach ($rec in @($Manifest.evidence_records)) {
            if ($rec.artifact_link -and $rec.artifact_link -notin $artifactPaths) {
                $errors += "Evidence artifact reference is not in the artifact manifest: $($rec.artifact_link)"
            }
        }
    }

    if (-not $Manifest.suites -or @($Manifest.suites).Count -eq 0) {
        $errors += 'Missing expected suite records'
    } else {
        $suiteIdentities = @()
        foreach ($s in $Manifest.suites) {
            if (-not $s.suite_name) {
                $errors += "Suite result missing suite_name"
            }
            if (-not (& $hasProperty $s 'suite_identity') -or [string]::IsNullOrWhiteSpace([string]$s.suite_identity)) {
                $errors += "Suite $($s.suite_name) is missing its task/report-specific identity"
            } elseif ([string]$s.suite_identity -in $suiteIdentities) {
                $errors += "Suite identity is duplicated: $($s.suite_identity)"
            } else {
                $suiteIdentities += [string]$s.suite_identity
            }
            foreach ($countField in @('tests', 'failures', 'errors', 'skipped')) {
                $countValue = $s.$countField
                if (-not (& $hasProperty $s $countField) -or $countValue -isnot [int] -and $countValue -isnot [long] -or $countValue -lt 0) {
                    $errors += "Suite $($s.suite_name) has missing/invalid $countField count"
                }
            }
            foreach ($outcomeField in @('executed', 'successful', 'failed')) {
                $outcomeValue = $s.$outcomeField
                if (-not (& $hasProperty $s $outcomeField) -or $outcomeValue -isnot [int] -and $outcomeValue -isnot [long] -or $outcomeValue -lt 0) {
                    $errors += "Suite $($s.suite_name) has missing/invalid $outcomeField outcome count"
                }
            }
            if (($s.executed -is [int] -or $s.executed -is [long]) -and ($s.tests -is [int] -or $s.tests -is [long]) -and ($s.skipped -is [int] -or $s.skipped -is [long]) -and $s.executed -ne ($s.tests - $s.skipped)) {
                $errors += "Suite $($s.suite_name) executed count does not reconcile with tests and skipped"
            }
            if (($s.successful -is [int] -or $s.successful -is [long]) -and ($s.tests -is [int] -or $s.tests -is [long]) -and $s.successful -ne ($s.tests - $s.failures - $s.errors - $s.skipped)) {
                $errors += "Suite $($s.suite_name) successful count does not reconcile with testcase outcomes"
            }
            if (($s.failed -is [int] -or $s.failed -is [long]) -and ($s.failures -is [int] -or $s.failures -is [long]) -and $s.failed -ne $s.failures) {
                $errors += "Suite $($s.suite_name) failed count does not equal failures"
            }
            if (-not (& $hasProperty $s 'required') -or $s.required -isnot [bool]) { $errors += "Suite $($s.suite_name) is missing its required disposition" }
        }
        if (-not (@($Manifest.suites | Where-Object { $_.required -eq $true }).Count)) { $errors += 'No required test suite is recorded' }
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

    if (-not $Manifest.workload -or @($Manifest.workload.host_test_tasks).Count -eq 0) {
        $errors += 'Missing required host/device workload task evidence'
    } else {
        if ($Policy) {
            $taskInventory = Test-WorkloadTaskInventory -Workload $Manifest.workload -Policy $Policy
            $errors += $taskInventory.Errors
        }
        foreach ($hostTask in $Manifest.workload.host_test_tasks) {
            $hostSucceeded = $hostTask.disposition -eq 'Applicable' -and $hostTask.executed -and -not $hostTask.skipped -and -not $hostTask.failed
            $scopeProof = $hostTask.test_scope_proof
            $hostNotApplicable = $hostTask.disposition -eq 'NotApplicable' -and $hostTask.skipped -and $hostTask.skip_message -eq 'NO-SOURCE' -and -not $hostTask.failed -and
                $scopeProof -and $scopeProof.task_path -ceq [string]$hostTask.task_path -and $scopeProof.gradle_no_source -eq $true -and
                ($scopeProof.compiled_test_class_count -is [int] -or $scopeProof.compiled_test_class_count -is [long]) -and $scopeProof.compiled_test_class_count -eq 0
            if ([string]::IsNullOrWhiteSpace([string]$hostTask.task_path) -or $hostTask.executed -isnot [bool] -or (-not $hostSucceeded -and -not $hostNotApplicable)) {
                $errors += 'Host workload task is missing or did not complete successfully'
            }
        }
        $requiredDeviceModules = if ($Policy) { @($Policy.required_workload.required_device_test_modules) } else { @(':feature:fido2', ':core:common', ':feature:settings') }
        foreach ($modulePath in $requiredDeviceModules) {
            $deviceTasks = @($Manifest.workload.required_device_tasks | Where-Object { $_.module_path -eq $modulePath })
            if ($deviceTasks.Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$deviceTasks[0].task_path) -or
                $deviceTasks[0].executed -isnot [bool] -or -not $deviceTasks[0].executed -or $deviceTasks[0].skipped -or $deviceTasks[0].failed) {
                $errors += "Required device workload task is missing or did not complete successfully for $modulePath"
            }
        }
        $junitBinding = Test-WorkloadJUnitArtifactBinding -Workload $Manifest.workload -ArtifactPaths @($Manifest.artifacts | ForEach-Object { [string]$_.path })
        $errors += $junitBinding.Errors
        $suiteExecution = Test-WorkloadSuiteExecutionCounts -Workload $Manifest.workload -Suites @($Manifest.suites)
        $errors += $suiteExecution.Errors
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

function ConvertFrom-CaptureResultOutput {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$OutputLines
    )

    $marker = "[CAPTURE_RESULT]"
    $payloads = @(
        foreach ($line in $OutputLines) {
            $text = [string]$line
            $index = $text.IndexOf($marker, [System.StringComparison]::Ordinal)
            if ($index -ge 0) {
                $text.Substring($index + $marker.Length).Trim()
            }
        }
    )

    if ($payloads.Count -ne 1) {
        throw "Expected exactly one $marker record; found $($payloads.Count)"
    }

    try {
        $result = $payloads[0] | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Capture result marker contains invalid JSON: $_"
    }

    if ([string]::IsNullOrWhiteSpace([string]$result.run_id) -or
        [string]::IsNullOrWhiteSpace([string]$result.run_dir)) {
        throw "Capture result JSON must contain non-empty run_id and run_dir"
    }

    return $result
}

function Test-CandidateManifestIdentity {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ExpectedSourceCommit,
        [Parameter(Mandatory = $true)][string]$ExpectedTargetCommit
    )

    $errors = @()
    $sourceCommit = [string]$Manifest.git.source_commit
    $targetCommit = [string]$Manifest.git.target_commit

    if ([string]::IsNullOrWhiteSpace($sourceCommit) -or $sourceCommit -ne $ExpectedSourceCommit) {
        $errors += "Run bundle git.source_commit ('$sourceCommit') does not match candidate SHA ($ExpectedSourceCommit)"
    }
    if ([string]::IsNullOrWhiteSpace($targetCommit) -or $targetCommit -ne $ExpectedTargetCommit) {
        $errors += "Run bundle git.target_commit ('$targetCommit') does not match target SHA ($ExpectedTargetCommit)"
    }

    return @{
        IsValid = ($errors.Count -eq 0)
        SourceCommit = $sourceCommit
        TargetCommit = $targetCommit
        Errors = $errors
    }
}

function Test-TrustedReporterBinding {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$WorkflowCommit,
        [Parameter(Mandatory = $true)][string]$ReporterSha,
        [Parameter(Mandatory = $true)][string]$RepositoryId,
        [Parameter(Mandatory = $true)][string]$AppId
    )
    $errors = @()
    $binding = $Manifest.trusted_reporter_binding
    if (-not $binding) { return @{ IsValid = $false; Errors = @('Capture manifest is missing trusted_reporter_binding') } }
    foreach ($entry in @(
        @{ Key = 'workflow_commit'; Expected = $WorkflowCommit; Pattern = '^[0-9a-fA-F]{40}$' },
        @{ Key = 'reporter_sha'; Expected = $ReporterSha; Pattern = '^[0-9a-fA-F]{40}$' },
        @{ Key = 'repository_id'; Expected = $RepositoryId; Pattern = '^[0-9]+$' },
        @{ Key = 'app_id'; Expected = $AppId; Pattern = '^[0-9]+$' }
    )) {
        $actual = [string]$binding.($entry.Key)
        if ($actual -notmatch $entry.Pattern -or $actual -cne [string]$entry.Expected) {
            $errors += "Trusted reporter binding '$($entry.Key)' does not match the protected reporter configuration"
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-TrustedComparisonManifest {
    param(
        [Parameter(Mandatory = $true)]$CandidateManifest,
        [Parameter(Mandatory = $true)]$BaselineManifest,
        $AcceptedBaselineIndex,
        $Policy
    )

    $errors = @()
    $candidateTarget = [string]$CandidateManifest.git.target_commit
    $baselineSource = [string]$BaselineManifest.git.source_commit
    if ([string]::IsNullOrWhiteSpace($candidateTarget) -or [string]::IsNullOrWhiteSpace($baselineSource)) {
        $errors += 'Candidate target and baseline source commits must be present'
    } elseif ($baselineSource -ne $candidateTarget) {
        $testedTree = [string]$AcceptedBaselineIndex.tested_tree
        $targetTree = [string]$AcceptedBaselineIndex.target_tree
        $promotionBound = $AcceptedBaselineIndex -and
            $AcceptedBaselineIndex.provenance -ceq 'ArchivedAcceptedRun' -and
            [string]$AcceptedBaselineIndex.run_id -ceq [string]$BaselineManifest.run_id -and
            [string]$AcceptedBaselineIndex.tested_commit -ceq $baselineSource -and
            [string]$AcceptedBaselineIndex.integrated_commit -ceq $candidateTarget -and
            [string]$AcceptedBaselineIndex.target_commit -ceq $candidateTarget -and
            $testedTree -match '^[0-9a-fA-F]{40,64}$' -and
            $targetTree -match '^[0-9a-fA-F]{40,64}$' -and
            [string]$BaselineManifest.git.source_tree -ceq $testedTree -and
            $testedTree -ceq $targetTree
        if (-not $promotionBound) {
            $errors += "Baseline source commit ('$baselineSource') differs from candidate target ('$candidateTarget') without a trusted same-tree promotion binding"
        }
    }
    if ($BaselineManifest.decisions.collection -ne 'Complete') {
        $errors += "Baseline collection must be explicitly Complete"
    }
    if ($BaselineManifest.decisions.tests -ne 'Passed') {
        $errors += "Baseline tests must be explicitly Passed"
    }
    if (-not $BaselineManifest.policy_id -or $BaselineManifest.policy_id -ne $CandidateManifest.policy_id) {
        $errors += "Baseline and candidate policy_id values must be present and equal"
    }
    foreach ($run in @(@{ Label = 'Candidate'; Manifest = $CandidateManifest }, @{ Label = 'Baseline'; Manifest = $BaselineManifest })) {
        $manifest = $run.Manifest
        if (-not $manifest.measurement_fingerprint -or -not $manifest.toolchain) {
            $errors += "$($run.Label) measurement compatibility fingerprint/toolchain is missing"
        } elseif ($Policy -and $manifest.device) {
            $expectedFingerprint = Get-MeasurementFingerprint -Policy $Policy -Toolchain $manifest.toolchain -Device $manifest.device -HarnessFileDigests $manifest.harness.file_sha256
            if ($manifest.measurement_fingerprint -cne $expectedFingerprint) {
                $errors += "$($run.Label) measurement fingerprint does not match the trusted policy and recorded measurement profile"
            }
        }
    }
    if ($BaselineManifest.measurement_fingerprint -cne $CandidateManifest.measurement_fingerprint) {
        $errors += 'Baseline and candidate measurement compatibility fingerprints differ'
    }
    if (-not $BaselineManifest.suites -or @($BaselineManifest.suites).Count -eq 0) {
        $errors += "Baseline must contain required suite outcome records"
    } else {
        foreach ($suite in $BaselineManifest.suites) {
            $outcome = Test-SuiteOutcome -SuiteResult $suite
            if ($outcome.Status -ne 'Passed') {
                $errors += "Baseline suite '$($suite.suite_name)' is not Passed: $($outcome.Reasons -join '; ')"
            }
        }
    }

    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ComparisonModuleAccounting {
    param(
        [Parameter(Mandatory = $true)]$Modules,
        [Parameter(Mandatory = $true)][string[]]$RequiredModulePaths,
        [string]$Label = 'Baseline'
    )

    $errors = @()
    $modulePaths = @($Modules | ForEach-Object { [string]$_.module_path })
    if (@($modulePaths | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
        $errors += "$Label contains a module counter record with a missing module identity"
    }
    if (@($modulePaths | Select-Object -Unique).Count -ne $modulePaths.Count) {
        $errors += "$Label contains duplicate module counter identities"
    }
    $pathsToValidate = @($RequiredModulePaths + $modulePaths | Select-Object -Unique)
    foreach ($modulePath in $pathsToValidate) {
        $matches = @($Modules | Where-Object { $_.module_path -eq $modulePath })
        if ($matches.Count -ne 1) {
            $errors += "$Label must contain exactly one counter record for $modulePath (found $($matches.Count))"
            continue
        }
        $module = $matches[0]
        if ($module.disposition -notin @('Applicable', 'NotApplicable')) {
            $errors += "$Label $modulePath has missing or unsupported disposition"
        }
        if ($module.report_digest -notmatch '^[0-9a-fA-F]{64}$' -or $module.class_inventory_digest -notmatch '^[0-9a-fA-F]{64}$') {
            $errors += "$Label $modulePath is missing report or retained-class inventory binding"
        }
        $moduleTotals = @{}
        foreach ($metric in @('instruction', 'branch')) {
            $counter = $module.$metric
            if (-not $counter) {
                $errors += "$Label $modulePath is missing $metric counter evidence"
                continue
            }
            foreach ($countField in @('covered', 'missed')) {
                if (-not (Test-NonnegativeIntegerValue -Value $counter.$countField)) {
                    $errors += "$Label $modulePath $metric $countField must be a nonnegative JSON integer"
                }
            }
            try {
                $covered = [System.Numerics.BigInteger]::Parse(([string]$counter.covered), [Globalization.CultureInfo]::InvariantCulture)
                $missed = [System.Numerics.BigInteger]::Parse(([string]$counter.missed), [Globalization.CultureInfo]::InvariantCulture)
                if ($covered -lt 0 -or $missed -lt 0) { throw 'negative counter' }
                $total = if (Test-NonnegativeIntegerValue -Value $counter.total) { [System.Numerics.BigInteger]::Parse(([string]$counter.total), [Globalization.CultureInfo]::InvariantCulture) } else { [System.Numerics.BigInteger]::MinusOne }
                if ($total -lt 0 -or $total -ne ($covered + $missed)) {
                    throw 'inconsistent total'
                }
                $moduleTotals[$metric] = $total
            } catch {
                $errors += "$Label $modulePath has invalid $metric counters: $_"
            }
        }
        if ($module.disposition -eq 'NotApplicable' -and
            (($moduleTotals.ContainsKey('instruction') -and $moduleTotals.instruction -ne 0) -or
             ($moduleTotals.ContainsKey('branch') -and $moduleTotals.branch -ne 0))) {
            $errors += "$Label $modulePath is NotApplicable but has nonzero executable counters"
        }
        if ($module.disposition -eq 'Applicable' -and $moduleTotals.ContainsKey('instruction') -and $moduleTotals.instruction -eq 0) {
            $errors += "$Label $modulePath is Applicable but has no executable instruction denominator"
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ComparisonModuleSetBinding {
    param(
        [Parameter(Mandatory = $true)]$InventoryModules,
        [Parameter(Mandatory = $true)]$CounterModules,
        [Parameter(Mandatory = $true)][string[]]$RequiredModulePaths,
        [string]$Label = 'Baseline'
    )

    $errors = @()
    $inventoryPaths = @($InventoryModules | ForEach-Object { [string]$_.module_path })
    $counterPaths = @($CounterModules | ForEach-Object { [string]$_.module_path })
    foreach ($setName in @(@{ Name = 'production inventory'; Paths = $inventoryPaths }, @{ Name = 'canonical counters'; Paths = $counterPaths })) {
        if (@($setName.Paths | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) { $errors += "$Label $($setName.Name) contains a missing module identity" }
        if (@($setName.Paths | Select-Object -Unique).Count -ne $setName.Paths.Count) { $errors += "$Label $($setName.Name) contains duplicate module identities" }
    }
    foreach ($modulePath in $counterPaths) {
        if ($modulePath -notin $inventoryPaths) { $errors += "$Label counter module $modulePath is absent from production inventory" }
    }
    foreach ($modulePath in $inventoryPaths) {
        if ($modulePath -notin $counterPaths) { $errors += "$Label production inventory module $modulePath has no canonical counter record" }
    }
    foreach ($modulePath in $RequiredModulePaths) {
        if ($modulePath -notin $inventoryPaths) { $errors += "$Label production inventory is missing required module $modulePath" }
        if ($modulePath -notin $counterPaths) { $errors += "$Label canonical counters are missing required module $modulePath" }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ComparisonInventoryStructure {
    param(
        [Parameter(Mandatory = $true)]$InventoryModules,
        [Parameter(Mandatory = $true)]$Policy,
        [string]$Label = 'Baseline'
    )

    $errors = @()
    $tier1Paths = @($Policy.tier1_modules | ForEach-Object { [string]$_ })
    $configuredModulePaths = @($Policy.production_scope.variants.PSObject.Properties.Name | ForEach-Object { [string]$_ } | Sort-Object -CaseSensitive)
    $actualModulePaths = @($InventoryModules | ForEach-Object { [string]$_.module_path } | Sort-Object -CaseSensitive)
    if (@($actualModulePaths | Select-Object -Unique).Count -ne $actualModulePaths.Count -or
        (@($actualModulePaths | Select-Object -Unique) -join "`n") -cne (@($configuredModulePaths | Select-Object -Unique) -join "`n")) {
        $missing = @($configuredModulePaths | Where-Object { $_ -notin $actualModulePaths })
        $extra = @($actualModulePaths | Where-Object { $_ -notin $configuredModulePaths })
        $errors += "$Label inventory module ownership differs from reviewed policy (missing: $($missing -join ', '); extra: $($extra -join ', '))"
    }
    foreach ($module in $InventoryModules) {
        $modulePath = [string]$module.module_path
        if ($modulePath -notmatch '^:([A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*)$') {
            $errors += "$Label inventory module '$modulePath' is not a canonical Gradle project path"
            continue
        }

        $expectedTier = if ($modulePath -in $tier1Paths) { 1 } else { 2 }
        if (-not (Test-NonnegativeIntegerValue -Value $module.tier) -or [System.Numerics.BigInteger]::Parse([string]$module.tier, [Globalization.CultureInfo]::InvariantCulture) -ne $expectedTier) {
            $errors += "$Label inventory module $modulePath has tier '$($module.tier)'; reviewed classification requires Tier $expectedTier"
        }

        $expectedDirectory = $modulePath.TrimStart(':').Replace(':', '/')
        $actualDirectory = ([string]$module.project_directory).Replace('\\', '/').Trim('/')
        if ([string]::IsNullOrWhiteSpace($actualDirectory) -or $actualDirectory -cne $expectedDirectory) {
            $errors += "$Label inventory module $modulePath has project_directory '$actualDirectory'; reviewed mapping requires '$expectedDirectory'"
        }

        $expectedVariant = [string]$Policy.production_scope.variants.$modulePath
        if ([string]::IsNullOrWhiteSpace($expectedVariant) -or [string]$module.selected_variant -cne $expectedVariant) {
            $errors += "$Label inventory module $modulePath selected variant '$($module.selected_variant)' is not the reviewed variant '$expectedVariant'"
        }
        $compilation = $Policy.production_scope.compilations.$expectedVariant
        $expectedCompilerTasks = @($compilation.compiler_tasks | ForEach-Object { "${modulePath}:$_" } | Sort-Object -CaseSensitive)
        $actualCompilerTasks = @($module.compiler_tasks | ForEach-Object { [string]$_ } | Sort-Object -CaseSensitive)
        if ($actualCompilerTasks.Count -eq 0 -or (@($actualCompilerTasks | Select-Object -Unique) -join "`n") -cne (@($expectedCompilerTasks | Select-Object -Unique) -join "`n")) {
            $errors += "$Label inventory module $modulePath compiler task ownership differs from reviewed variant $expectedVariant"
        }

        $sourceRoots = @($module.source_roots | ForEach-Object { ([string]$_).Replace('\', '/') })
        if (@($sourceRoots | Select-Object -Unique).Count -ne $sourceRoots.Count -or
            @($sourceRoots | Where-Object { $_ -notin @($Policy.production_scope.source_roots | ForEach-Object { ([string]$_).Replace('\', '/') }) -or [System.IO.Path]::IsPathRooted($_) -or $_ -match '(^|/)\.\.(/|$)' }).Count -gt 0) {
            $errors += "$Label inventory module $modulePath contains source roots outside reviewed production_scope.source_roots"
        }

        $outputDirectories = @($module.output_directories | ForEach-Object { ([string]$_).Replace('\', '/') })
        if ($outputDirectories.Count -eq 0 -or @($outputDirectories | Select-Object -Unique).Count -ne $outputDirectories.Count -or
            @($outputDirectories | Where-Object { -not $_.StartsWith('build/', [System.StringComparison]::OrdinalIgnoreCase) -or [System.IO.Path]::IsPathRooted($_) -or $_ -match '(^|/)\.\.(/|$)' }).Count -gt 0) {
            $errors += "$Label inventory module $modulePath has compiler outputs outside its project build boundary"
        }

        $excludedPatterns = @($module.excluded_class_patterns | ForEach-Object { [string]$_ })
        if (@($excludedPatterns | Select-Object -Unique).Count -ne $excludedPatterns.Count -or
            @($excludedPatterns | Where-Object { $_ -notmatch '^[A-Za-z0-9_./$-]+(?:\$\*)?\.class$' -or $_ -match '(^|/)\.\.(/|$)' }).Count -gt 0) {
            $errors += "$Label inventory module $modulePath has unsafe or duplicate generated exclusion patterns"
        }
        if ($excludedPatterns.Count -gt 0 -and $Policy.generated_exclusion_rules.exclude_sqldelight -ne $true) {
            $errors += "$Label inventory module $modulePath uses generated exclusions disabled by reviewed policy"
        }
        $exclusionSources = @($module.excluded_class_sources)
        $sourcePatterns = @()
        $sourcePathsSeen = @()
        foreach ($source in $exclusionSources) {
            $sourcePath = ([string]$source.source_path).Replace('\', '/')
            if (-not $sourcePath.StartsWith('build/generated/sqldelight/code/', [System.StringComparison]::OrdinalIgnoreCase) -or $sourcePath -match '(^|/)\.\.(/|$)' -or [System.IO.Path]::IsPathRooted($sourcePath)) {
                $errors += "$Label inventory module $modulePath generated exclusion source is outside the reviewed SQLDelight output"
                continue
            }
            if ($sourcePath -in $sourcePathsSeen) { $errors += "$Label inventory module $modulePath duplicates a generated exclusion source" }
            $sourcePathsSeen += $sourcePath
            if ([string]$source.source_sha256 -notmatch '^[0-9a-fA-F]{64}$') { $errors += "$Label inventory module $modulePath generated exclusion source has no content digest" }
            $relativeSource = $sourcePath.Substring('build/generated/sqldelight/code/'.Length)
            $segments = @($relativeSource.Split('/') | Where-Object { $_ -ne '' })
            $patterns = @($source.class_patterns | ForEach-Object { [string]$_ })
            if (-not $sourcePath.EndsWith('.kt', [System.StringComparison]::OrdinalIgnoreCase) -or $segments.Count -lt 4) {
                $errors += "$Label inventory module $modulePath generated exclusion source is not a supported Kotlin output"
                continue
            }
            $classPath = ($segments | Select-Object -Skip 2) -join '/'
            $classPath = $classPath.Substring(0, $classPath.Length - 3)
            $expectedPatterns = @("$classPath.class", "$classPath`$*.class")
            $actualPatternKey = (@($patterns | Sort-Object -CaseSensitive) -join "`n")
            $expectedPatternKey = (@($expectedPatterns | Sort-Object -CaseSensitive) -join "`n")
            if ($actualPatternKey -cne $expectedPatternKey) {
                $errors += "$Label inventory module $modulePath exclusion source does not map to its exact generated class patterns"
            }
            $sourcePatterns += $patterns
        }
        # Multiple generated source variants can produce the same SQLDelight
        # class pattern. JaCoCo exclusions are a set, so compare distinct
        # patterns while retaining and validating every source record above.
        $distinctSourcePatterns = @($sourcePatterns | Sort-Object -CaseSensitive -Unique)
        $distinctExcludedPatterns = @($excludedPatterns | Sort-Object -CaseSensitive -Unique)
        if (($distinctSourcePatterns -join "`n") -cne ($distinctExcludedPatterns -join "`n")) {
            $errors += "$Label inventory module $modulePath generated exclusions do not exactly match their retained generated source evidence"
        }
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ComparisonModuleReportArtifact {
    param(
        [Parameter(Mandatory = $true)][string]$ModulePath,
        [Parameter(Mandatory = $true)][string]$ExpectedDigest,
        [Parameter(Mandatory = $true)]$Artifacts,
        [string]$Label = 'Baseline'
    )
    $errors = @()
    if ($ModulePath -notmatch '^:([A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*)$') {
        return @{ IsValid = $false; Path = $null; Errors = @("$Label module path is not canonical for report binding: $ModulePath") }
    }
    $safeName = $ModulePath.Replace(':', '_').TrimStart('_')
    $expectedPath = "reports/modules/$safeName.xml"
    $matches = @($Artifacts | Where-Object { ([string]$_.path).Replace('\', '/') -ceq $expectedPath })
    if ($matches.Count -ne 1) {
        $errors += "$Label $ModulePath must retain exactly one module report at $expectedPath (found $($matches.Count))"
    } elseif ([string]$matches[0].sha256 -cne $ExpectedDigest -or $ExpectedDigest -notmatch '^[0-9a-fA-F]{64}$') {
        $errors += "$Label $ModulePath report artifact digest does not match canonical counters"
    }
    return @{ IsValid = ($errors.Count -eq 0); Path = if ($matches.Count -eq 1) { $expectedPath } else { $null }; Errors = $errors }
}

function Test-ComparisonBundleEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$BundlePath,
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)]$Policy,
        [string]$TrustedHarnessRoot
    )

    $errors = @()
    $bundle = [System.IO.Path]::GetFullPath($BundlePath)
    $manifestCheck = Test-ManifestSchema -Manifest $Manifest -Policy $Policy -TrustedHarnessRoot $TrustedHarnessRoot
    if (-not $manifestCheck.IsValid) { $errors += @($manifestCheck.Errors | ForEach-Object { "Baseline manifest: $_" }) }
    if ($Manifest.git.clean_preflight -isnot [bool] -or -not $Manifest.git.clean_preflight -or
        $Manifest.git.clean_postflight -isnot [bool] -or -not $Manifest.git.clean_postflight -or
        $Manifest.harness.clean -isnot [bool] -or -not $Manifest.harness.clean) {
        $errors += 'Baseline does not prove clean source and reviewed harness state throughout capture'
    }

    $artifactRecords = @($Manifest.artifacts)
    $listed = @()
    $seenPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($artifact in $artifactRecords) {
        $relative = ([string]$artifact.path).Replace('\', '/')
        if ([string]::IsNullOrWhiteSpace($relative) -or [System.IO.Path]::IsPathRooted($relative) -or $relative -match '(^|/)\.\.(/|$)') {
            $errors += "Baseline has unsafe artifact path '$relative'"
            continue
        }
        if (-not $seenPaths.Add($relative)) { $errors += "Baseline lists a duplicate artifact path: $relative"; continue }
        $listed += $relative
        $full = Join-Path $bundle $relative
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { $errors += "Baseline artifact is missing: $relative"; continue }
        if ($artifact.sha256 -notmatch '^[0-9a-fA-F]{64}$' -or (Get-FileSha256 -Path $full) -ne $artifact.sha256.ToLowerInvariant()) {
            $errors += "Baseline artifact digest is missing or incorrect: $relative"
        }
    }
    $manifestPath = [System.IO.Path]::GetFullPath((Join-Path $bundle 'manifest.json'))
    $disk = @(Get-ChildItem -LiteralPath $bundle -File -Recurse | Where-Object { [System.IO.Path]::GetFullPath($_.FullName) -ne $manifestPath } | ForEach-Object { [IO.Path]::GetRelativePath($bundle, $_.FullName).Replace('\', '/') })
    foreach ($path in $disk) { if ($path -notin $listed) { $errors += "Baseline disk artifact is unlisted: $path" } }
    foreach ($path in $listed) { if ($path -notin $disk) { $errors += "Baseline listed artifact is absent: $path" } }
    $executionFileBinding = Test-ExecutionFileAttribution -ExecutionFiles $Manifest.execution_files -Artifacts $artifactRecords
    if (-not $executionFileBinding.IsValid) { $errors += @($executionFileBinding.Errors | ForEach-Object { "Baseline $_" }) }

    # A manifest's Passed label and suite totals are not sufficient baseline
    # evidence. Reconstruct totals from the retained JUnit reports and require
    # exact agreement before allowing the bundle to serve as a regression base.
    $testReportArtifacts = @($artifactRecords | Where-Object { ([string]$_.path).Replace('\', '/') -match '^reports/tests/.+\.xml$' })
    if ($testReportArtifacts.Count -eq 0) {
        $errors += 'Baseline has no retained JUnit report artifacts'
    }
    $actualSuiteTotals = @{}
    foreach ($artifact in $testReportArtifacts) {
        try {
            $junit = Read-SafeXml -Path (Join-Path $bundle ([string]$artifact.path))
            $suiteNodes = @($junit.SelectNodes('/testsuite | /testsuites/testsuite'))
            if ($suiteNodes.Count -eq 0) {
                # Preserve valid zero-test task reports as task evidence while
                # excluding them from suite totals. Reject every other empty
                # or ambiguous suite-less XML document.
                $emptyAggregate = $junit.DocumentElement
                if ($emptyAggregate -and $emptyAggregate.Name -eq 'testsuites' -and
                    $emptyAggregate.GetAttribute('tests') -eq '0' -and
                    $emptyAggregate.GetAttribute('failures') -eq '0' -and
                    $emptyAggregate.GetAttribute('errors') -eq '0' -and
                    $emptyAggregate.GetAttribute('skipped') -eq '0' -and
                    $emptyAggregate.ChildNodes.Count -eq 0) {
                    continue
                }
                throw 'JUnit report contains no testsuite nodes and is not a valid zero-test aggregate'
            }
            foreach ($node in $suiteNodes) {
                $suiteName = $node.GetAttribute('name')
                if ([string]::IsNullOrWhiteSpace($suiteName)) { throw 'JUnit testsuite has no name' }
                if (-not $actualSuiteTotals.ContainsKey($suiteName)) {
                    $actualSuiteTotals[$suiteName] = @{ tests = [System.Numerics.BigInteger]::Zero; failures = [System.Numerics.BigInteger]::Zero; errors = [System.Numerics.BigInteger]::Zero; skipped = [System.Numerics.BigInteger]::Zero }
                }
                $testCases = @($node.SelectNodes('testcase'))
                $derivedSuiteCounts = @{
                    tests = [System.Numerics.BigInteger]$testCases.Count
                    failures = [System.Numerics.BigInteger]@($node.SelectNodes('testcase/failure')).Count
                    errors = [System.Numerics.BigInteger]@($node.SelectNodes('testcase/error')).Count
                    skipped = [System.Numerics.BigInteger]@($node.SelectNodes('testcase/skipped')).Count
                }
                foreach ($metric in @('tests', 'failures', 'errors', 'skipped')) {
                    if ($node.HasAttribute($metric)) {
                        $declared = [System.Numerics.BigInteger]::Parse($node.GetAttribute($metric), [Globalization.CultureInfo]::InvariantCulture)
                        if ($declared -ne $derivedSuiteCounts[$metric]) { throw "JUnit suite $suiteName declared $metric=$declared but contains $($derivedSuiteCounts[$metric])" }
                    }
                    $value = $derivedSuiteCounts[$metric]
                    if ($value -lt 0) { throw "JUnit suite $suiteName has negative $metric count" }
                    $actualSuiteTotals[$suiteName][$metric] += $value
                }
            }
        } catch {
            $errors += "Baseline JUnit artifact '$($artifact.path)' is invalid: $_"
        }
    }
    $manifestSuiteTotals = @{}
    foreach ($suite in @($Manifest.suites)) {
        $suiteName = [string]$suite.suite_name
        if ([string]::IsNullOrWhiteSpace($suiteName)) { $errors += 'Baseline suite record is missing suite_name'; continue }
        if (-not $manifestSuiteTotals.ContainsKey($suiteName)) {
            $manifestSuiteTotals[$suiteName] = @{ tests = [System.Numerics.BigInteger]::Zero; failures = [System.Numerics.BigInteger]::Zero; errors = [System.Numerics.BigInteger]::Zero; skipped = [System.Numerics.BigInteger]::Zero }
        }
        foreach ($metric in @('tests', 'failures', 'errors', 'skipped')) {
            try {
                $manifestSuiteTotals[$suiteName][$metric] += [System.Numerics.BigInteger]::Parse(([string]$suite.$metric), [Globalization.CultureInfo]::InvariantCulture)
            } catch { $errors += "Baseline suite '$suiteName' has invalid $metric count" }
        }
        $suiteOutcome = Test-SuiteOutcome -SuiteResult $suite
        if ($suiteOutcome.Status -ne 'Passed') { $errors += "Baseline suite '$suiteName' is not Passed: $($suiteOutcome.Reasons -join '; ')" }
    }
    if (@($Manifest.suites | Where-Object { $_.required -eq $true }).Count -eq 0) { $errors += 'Baseline has no required suite record' }
    foreach ($suiteName in (@($actualSuiteTotals.Keys) + @($manifestSuiteTotals.Keys) | Sort-Object -Unique)) {
        if (-not $actualSuiteTotals.ContainsKey($suiteName) -or -not $manifestSuiteTotals.ContainsKey($suiteName)) {
            $errors += "Baseline JUnit report/suite record is missing on one side for '$suiteName'"
            continue
        }
        foreach ($metric in @('tests', 'failures', 'errors', 'skipped')) {
            if ($actualSuiteTotals[$suiteName][$metric] -ne $manifestSuiteTotals[$suiteName][$metric]) {
                $errors += "Baseline JUnit and manifest disagree for suite '$suiteName' $metric count"
            }
        }
    }

    $strictActualSuites = @{}
    foreach ($artifact in $testReportArtifacts) {
        $reportPath = ([string]$artifact.path).Replace('\', '/')
        $reportIdentity = $reportPath.Substring('reports/tests/'.Length)
        $parsedReport = Get-StrictJUnitOutcome -Path (Join-Path $bundle $reportPath) -ReportIdentity $reportIdentity
        if (-not $parsedReport.IsValid) {
            $errors += "Baseline JUnit artifact '$reportPath' failed strict outcome parsing: $($parsedReport.Errors -join '; ')"
            continue
        }
        foreach ($suite in $parsedReport.Suites) {
            if ($strictActualSuites.ContainsKey([string]$suite.suite_identity)) {
                $errors += "Baseline has a duplicate task/suite identity: $($suite.suite_identity)"
            } else {
                $strictActualSuites[[string]$suite.suite_identity] = $suite
            }
        }
    }
    $strictRecordedSuites = @{}
    foreach ($suite in @($Manifest.suites)) {
        $identity = [string]$suite.suite_identity
        if ([string]::IsNullOrWhiteSpace($identity) -or $strictRecordedSuites.ContainsKey($identity)) {
            $errors += 'Baseline manifest suite identity is missing or duplicated'
        } else {
            $strictRecordedSuites[$identity] = $suite
        }
    }
    foreach ($identity in (@($strictActualSuites.Keys) + @($strictRecordedSuites.Keys) | Sort-Object -Unique)) {
        if (-not $strictActualSuites.ContainsKey($identity) -or -not $strictRecordedSuites.ContainsKey($identity)) {
            $errors += "Baseline strict JUnit outcome is missing on one side for '$identity'"
            continue
        }
        foreach ($metric in @('tests', 'executed', 'successful', 'failed', 'failures', 'errors', 'skipped')) {
            if ([System.Numerics.BigInteger]::Parse([string]$strictActualSuites[$identity].$metric, [Globalization.CultureInfo]::InvariantCulture) -ne
                [System.Numerics.BigInteger]::Parse([string]$strictRecordedSuites[$identity].$metric, [Globalization.CultureInfo]::InvariantCulture)) {
                $errors += "Baseline strict JUnit and manifest disagree for '$identity' $metric"
            }
        }
    }

    $counterArtifacts = @($artifactRecords | Where-Object { ([string]$_.path).Replace('\', '/') -eq 'counters.json' })
    $inventoryArtifacts = @($artifactRecords | Where-Object { ([string]$_.path).Replace('\', '/') -eq 'inventory.json' })
    if ($counterArtifacts.Count -ne 1 -or $inventoryArtifacts.Count -ne 1) {
        $errors += 'Baseline must retain exactly one counters.json and inventory.json artifact'
        return @{ IsValid = $false; Errors = $errors; Counters = $null }
    }
    try {
        $counters = Read-SafeJson -Path (Join-Path $bundle 'counters.json')
        $inventory = Read-SafeJson -Path (Join-Path $bundle 'inventory.json')
    } catch {
        $errors += "Baseline canonical counters or inventory is malformed: $_"
        return @{ IsValid = $false; Errors = $errors; Counters = $null }
    }
    $inventoryDigest = Get-FileSha256 -Path (Join-Path $bundle 'inventory.json')
    $runtimeBinding = Test-MeasurementRuntimeBinding -Counters $counters -Manifest $Manifest -Policy $Policy
    if (-not $runtimeBinding.IsValid) { $errors += @($runtimeBinding.Errors | ForEach-Object { "Baseline measurement runtime: $_" }) }
    if ($inventoryDigest -ne $counters.inventory_digest -or $Manifest.production_inventory_digest -ne $inventoryDigest) { $errors += 'Baseline inventory file digest does not match canonical counters and manifest' }
    if ($counters.analyzer_identity -ne 'jacoco-0.8.15') { $errors += 'Baseline analyzer identity is missing or unsupported' }
    if ($inventory.analyzer_identity -ne $counters.analyzer_identity) { $errors += 'Baseline inventory analyzer identity does not match canonical counters' }
    if ($counters.schema_version -ne '1.0' -or $counters.policy_id -ne $Manifest.policy_id -or $Manifest.policy_id -ne $Policy.policy_id) {
        $errors += 'Baseline counter, manifest, and reviewed policy identities are incompatible'
    }
    $inventoryPaths = @($inventory.modules | ForEach-Object { [string]$_.module_path })
    $requiredModulePaths = @(@($Policy.tier1_modules) + $inventoryPaths | Sort-Object -Unique)
    $moduleSetBinding = Test-ComparisonModuleSetBinding -InventoryModules $inventory.modules -CounterModules $counters.modules -RequiredModulePaths @($Policy.tier1_modules) -Label 'Baseline'
    $errors += $moduleSetBinding.Errors
    $inventoryStructure = Test-ComparisonInventoryStructure -InventoryModules $inventory.modules -Policy $Policy -Label 'Baseline'
    $errors += $inventoryStructure.Errors
    $moduleAccounting = Test-ComparisonModuleAccounting -Modules $counters.modules -RequiredModulePaths $requiredModulePaths -Label 'Baseline'
    $errors += $moduleAccounting.Errors
    [object[]]$repositoryInputs = @()
    if ($counters.repository -and $null -ne $counters.repository.execution_files) {
        $repositoryInputs = @($counters.repository.execution_files)
    }
    $repositoryExecutionBinding = Test-RepositoryExecutionAttribution `
        -RepositoryExecutionFiles $repositoryInputs -ManifestExecutionFiles @($Manifest.execution_files) `
        -Workload $Manifest.workload -ExpectedRunId ([string]$Manifest.run_id) -ExpectedDeviceSerial ([string]$Manifest.device.device_serial)
    if (-not $repositoryExecutionBinding.IsValid) { $errors += $repositoryExecutionBinding.Errors }

    foreach ($modulePath in $requiredModulePaths) {
        $moduleRows = @($counters.modules | Where-Object { $_.module_path -eq $modulePath })
        $inventoryRows = @($inventory.modules | Where-Object { $_.module_path -eq $modulePath })
        if ($moduleRows.Count -ne 1) { continue }
        if ($inventoryRows.Count -ne 1) { $errors += "Baseline inventory must contain exactly one record for $modulePath"; continue }
        $module = $moduleRows[0]
        $inventoryModule = $inventoryRows[0]
        $compatibilityEvidence = Test-ExecutionCompatibilityEvidence -InventoryModule $inventoryModule -CounterModule $module `
            -AnalyzerIdentity ([string]$counters.analyzer_identity) -Label "Baseline $modulePath"
        if (-not $compatibilityEvidence.IsValid) { $errors += $compatibilityEvidence.Errors }
        $sourceBinding = Test-ExecutionCompatibilityAttribution -CompatibilityReceipt $module.execution_compatibility `
            -ManifestExecutionFiles $Manifest.execution_files -Scope $modulePath `
            -ExpectedVariant ([string]$inventoryModule.selected_variant) -Workload $Manifest.workload `
            -ExpectedRunId ([string]$Manifest.run_id) -ExpectedDeviceSerial ([string]$Manifest.device.device_serial) -Label "Baseline $modulePath"
        if (-not $sourceBinding.IsValid) { $errors += $sourceBinding.Errors }
        $reportBinding = Test-CanonicalInventoryModuleBinding -InventoryModule $inventoryModule -CounterModule $module -InventoryDigest $inventoryDigest -ExpectedInventoryDigest $counters.inventory_digest -AnalyzerIdentity $counters.analyzer_identity -ReportDigest $module.report_digest
        $errors += @($reportBinding.Reasons | ForEach-Object { "Baseline ${modulePath}: $_" })
        $reportArtifactBinding = Test-ComparisonModuleReportArtifact -ModulePath $modulePath -ExpectedDigest ([string]$module.report_digest) -Artifacts $artifactRecords -Label 'Baseline'
        $errors += $reportArtifactBinding.Errors
        if (-not $reportArtifactBinding.IsValid) { continue }
        try {
            $xml = Read-SafeXml -Path (Join-Path $bundle $reportArtifactBinding.Path)
            if ($xml.DocumentElement.LocalName -ne 'report') { throw 'invalid report root' }
            $proofRows = @($counters.zero_count_proofs | Where-Object { $_.module_path -eq $modulePath })
            foreach ($metric in @('instruction', 'branch')) {
                if ($null -eq $module.$metric.total) { throw "canonical $metric total is missing" }
            }
            $requiresProof = ([System.Numerics.BigInteger]::Parse(([string]$module.instruction.total), [Globalization.CultureInfo]::InvariantCulture) -eq 0)
            foreach ($metric in @('INSTRUCTION', 'BRANCH')) {
                $counter = $module.$($metric.ToLowerInvariant())
                $nodes = @($xml.SelectNodes("/report/counter[@type='$metric']"))
                if ($nodes.Count -gt 1) { throw "duplicate $metric counters" }
                if ($nodes.Count -eq 0) {
                    if ([System.Numerics.BigInteger]::Parse(([string]$counter.total), [Globalization.CultureInfo]::InvariantCulture) -ne 0) { throw "omitted nonzero $metric counter" }
                    $requiresProof = $true
                } else {
                    $covered = ConvertTo-CoverageBigInteger -Value $nodes[0].GetAttribute('covered') -Label "Baseline $modulePath $metric covered"
                    $missed = ConvertTo-CoverageBigInteger -Value $nodes[0].GetAttribute('missed') -Label "Baseline $modulePath $metric missed"
                    if ($covered -ne [System.Numerics.BigInteger]::Parse(([string]$counter.covered), [Globalization.CultureInfo]::InvariantCulture) -or $missed -ne [System.Numerics.BigInteger]::Parse(([string]$counter.missed), [Globalization.CultureInfo]::InvariantCulture)) { throw "$metric totals disagree with the retained report" }
                }
            }
            if ($requiresProof) {
                if ($proofRows.Count -ne 1) { throw "expected exactly one bound zero-count proof, found $($proofRows.Count)" }
                $proofBinding = Test-CanonicalZeroProofBinding -Proof $proofRows[0] -InventoryModule $inventoryModule -CounterModule $module -InventoryDigest $inventoryDigest -ExpectedInventoryDigest $counters.inventory_digest -AnalyzerIdentity $counters.analyzer_identity -ReportDigest $module.report_digest
                if (-not $proofBinding.IsValid) { throw ($proofBinding.Reasons -join '; ') }
            }
        } catch { $errors += "Baseline $modulePath report evidence is invalid: $_" }
    }
    $repositoryEvidence = Test-ComparisonRepositoryEvidence -BundlePath $bundle -Manifest $Manifest -Counters $counters
    $errors += $repositoryEvidence.Errors
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors; Counters = $counters }
}

function Test-ComparisonRepositoryEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$BundlePath,
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)]$Counters
    )

    $errors = @()
    $repository = $Counters.repository
    if (-not $repository) {
        return @{ IsValid = $false; Errors = @('Baseline repository counters are missing') }
    }
    $reportArtifacts = @($Manifest.artifacts | Where-Object { ([string]$_.path).Replace('\\', '/') -ceq 'reports/repository.xml' })
    if ($reportArtifacts.Count -ne 1) {
        return @{ IsValid = $false; Errors = @("Baseline must retain exactly one reports/repository.xml artifact (found $($reportArtifacts.Count))") }
    }
    $reportPath = Join-Path $BundlePath 'reports/repository.xml'
    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
        return @{ IsValid = $false; Errors = @('Baseline repository JaCoCo report is missing') }
    }
    $reportDigest = Get-FileSha256 -Path $reportPath
    if ([string]$repository.report_digest -notmatch '^[0-9a-fA-F]{64}$' -or
        [string]$reportArtifacts[0].sha256 -cne $reportDigest -or
        [string]$repository.report_digest -cne $reportDigest) {
        $errors += 'Baseline repository report digest does not match its retained artifact and canonical counters'
    }
    try {
        $xml = Read-SafeXml -Path $reportPath
        $canonicalBinding = Test-CanonicalRepositoryReportBinding -XmlDocument $xml -RepositoryCounters $repository `
            -ReportDigest $reportDigest -ExpectedReportDigest ([string]$repository.report_digest) -Label 'Baseline repository'
        $errors += $canonicalBinding.Errors
    } catch {
        $errors += "Baseline repository report evidence is invalid: $_"
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Test-AcceptedBaselineIndex {
    param(
        [Parameter(Mandatory = $true)]$Index,
        [Parameter(Mandatory = $true)][string]$ExpectedTargetCommit,
        [Parameter(Mandatory = $false)][string]$ActualBundleDigest
    )

    $errors = @()
    if ($Index.provenance -notin @('ArchivedAcceptedRun', 'MeasuredAcceptedTarget')) { $errors += "Unsupported accepted baseline provenance '$($Index.provenance)'" }
    if ($ExpectedTargetCommit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') { $errors += 'Expected accepted baseline target must be a full Git commit ID' }
    if ([string]$Index.target_commit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or $Index.target_commit -ne $ExpectedTargetCommit) { $errors += "Accepted baseline target does not match '$ExpectedTargetCommit'" }
    if ([string]::IsNullOrWhiteSpace([string]$Index.run_id)) { $errors += 'Accepted baseline index is missing run_id' }
    if ([string]::IsNullOrWhiteSpace([string]$Index.bundle_path) -or [System.IO.Path]::IsPathRooted([string]$Index.bundle_path) -or $Index.bundle_path -match '(^|[/\\])\.\.([/\\]|$)') { $errors += 'Accepted baseline bundle_path must be a contained relative path' }
    if ([string]$Index.bundle_digest -notmatch '^[0-9a-fA-F]{64}$') { $errors += 'Accepted baseline bundle_digest must be a full SHA-256 digest' }
    if ($ActualBundleDigest -and ([string]$ActualBundleDigest -notmatch '^[0-9a-fA-F]{64}$' -or [string]$Index.bundle_digest -cne [string]$ActualBundleDigest)) { $errors += 'Accepted baseline bundle digest does not match its index' }

    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function ConvertTo-RemoteTargetRef {
    param(
        [Parameter(Mandatory = $true)][string]$TargetRef,
        [Parameter(Mandatory = $true)][string]$RemoteName
    )

    if ($TargetRef -match '^[0-9a-fA-F]{40,64}$') {
        return @{ IsValid = $false; Ref = $null; Error = 'TargetRef must name a live remote branch/ref; a commit SHA alone is insufficient.' }
    }
    if ($TargetRef.StartsWith("refs/remotes/$RemoteName/")) {
        return @{ IsValid = $true; Ref = 'refs/heads/' + $TargetRef.Substring("refs/remotes/$RemoteName/".Length); Error = $null }
    }
    if ($TargetRef.StartsWith("$RemoteName/")) {
        return @{ IsValid = $true; Ref = 'refs/heads/' + $TargetRef.Substring("$RemoteName/".Length); Error = $null }
    }
    if ($TargetRef.StartsWith('refs/')) {
        return @{ IsValid = $true; Ref = $TargetRef; Error = $null }
    }
    return @{ IsValid = $true; Ref = "refs/heads/$TargetRef"; Error = $null }
}

function Get-AffectedTier1Modules {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        [Parameter(Mandatory = $true)][AllowNull()][AllowEmptyCollection()][string[]]$ChangedPaths,
        [Parameter(Mandatory = $true)][bool]$ChangedPathsProvided
    )

    if (-not $ChangedPathsProvided) { return @($Policy.tier1_modules) }
    $sharedChange = $false
    $affected = @()
    foreach ($line in $ChangedPaths) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $path = $line.Trim().Replace('\', '/')
        if ($path -match '^(build\.gradle\.kts|settings\.gradle\.kts|gradle/|tools/|\.github/|\.agents/|\.specify/|buildSrc/)' -or $path -in @('gradle.properties', 'gradlew', 'gradlew.bat')) {
            $sharedChange = $true
            break
        }
        foreach ($module in $Policy.tier1_modules) {
            $prefix = $module.Replace(':', '/').TrimStart('/') + '/'
            if ($path.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase) -and $module -notin $affected) { $affected += $module }
        }
    }
    if ($sharedChange) { return @($Policy.tier1_modules) }
    return @($affected)
}

function Get-AffectedTier2Modules {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        [Parameter(Mandatory = $true)]$Inventory,
        [Parameter(Mandatory = $true)][AllowNull()][AllowEmptyCollection()][string[]]$ChangedPaths,
        [Parameter(Mandatory = $true)][bool]$ChangedPathsProvided
    )

    $errors = @()
    $tier1 = @($Policy.tier1_modules | ForEach-Object { [string]$_ })
    $owners = @($Inventory.modules | Where-Object { $_.tier -eq 2 -and $_.module_path -notin $tier1 })
    $ownerPaths = @($owners | ForEach-Object { [string]$_.module_path })
    if (@($ownerPaths | Select-Object -Unique).Count -ne $ownerPaths.Count) { throw 'Production inventory contains duplicate Tier 2 module owners' }
    $ownerDirectories = @()
    foreach ($owner in $owners) {
        $ownerPath = [string]$owner.module_path
        $projectDirectory = ([string]$owner.project_directory).Replace('\', '/').Trim('/')
        if ($ownerPath -notmatch '^(:[A-Za-z0-9_.-]+)+$' -or [string]::IsNullOrWhiteSpace($projectDirectory) -or
            $projectDirectory -match '(^|/)\.\.(/|$)' -or $projectDirectory.StartsWith('/') -or $projectDirectory -match '^[a-zA-Z]:') {
            throw "Tier 2 inventory has an invalid owner path or unsafe project_directory for '$ownerPath'"
        }
        $ownerDirectories += $projectDirectory
    }
    if (@($ownerDirectories | Select-Object -Unique).Count -ne $ownerDirectories.Count) { throw 'Production inventory contains duplicate Tier 2 project directories' }
    foreach ($configured in @($Policy.tier2_gated_modules)) {
        if ($configured -notin $ownerPaths) {
            $errors += "Configured Tier 2 owner '$configured' is missing from production inventory"
        }
    }
    if ($errors.Count -gt 0) { throw ($errors -join '; ') }
    if ($owners.Count -eq 0) { return @() }

    $sharedChangePatterns = @('^(build\.gradle\.kts|settings\.gradle\.kts|gradle/|tools/|\.github/|\.agents/|\.specify/|buildSrc/)', '^(gradle\.properties|gradlew|gradlew\.bat|\.gitconfig)$')
    $affected = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    if (-not $ChangedPathsProvided) {
        foreach ($owner in $owners) { $null = $affected.Add([string]$owner.module_path) }
        return @($affected | Sort-Object -CaseSensitive)
    }

    foreach ($line in $ChangedPaths) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $path = $line.Trim().Replace('\', '/')
        if ($sharedChangePatterns | Where-Object { $path -match $_ }) {
            foreach ($owner in $owners) { $null = $affected.Add([string]$owner.module_path) }
            break
        }
        foreach ($owner in $owners) {
            $projectDirectory = ([string]$owner.project_directory).Replace('\', '/').Trim('/')
            if ($path.Equals($projectDirectory, [System.StringComparison]::OrdinalIgnoreCase) -or
                $path.StartsWith($projectDirectory + '/', [System.StringComparison]::OrdinalIgnoreCase)) {
                $null = $affected.Add([string]$owner.module_path)
            }
        }
    }
    return @($affected | Sort-Object -CaseSensitive)
}

function Test-PromotionIdentity {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$IntegratedTree,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$IntegratedCommit
    )

    $errors = @()
    $testedCommit = [string]$Manifest.git.source_commit
    $testedTree = [string]$Manifest.git.source_tree
    if ($testedCommit -notmatch '^[0-9a-fA-F]{40,64}$') { $errors += 'manifest.git.source_commit must be a full commit SHA' }
    if ($testedTree -notmatch '^[0-9a-fA-F]{40,64}$') { $errors += 'manifest.git.source_tree must be a full tree SHA' }
    if ([string]::IsNullOrWhiteSpace($IntegratedTree) -or $IntegratedTree -ne $testedTree) { $errors += 'Integrated tree does not match tested manifest.git.source_tree' }
    if ([string]::IsNullOrWhiteSpace($IntegratedCommit) -or $IntegratedCommit -notmatch '^[0-9a-fA-F]{40,64}$') { $errors += 'Integrated commit must be a full commit SHA and must be explicitly supplied' }
    return @{ IsValid = ($errors.Count -eq 0); TestedCommit = $testedCommit; TestedTree = $testedTree; IntegratedCommit = $IntegratedCommit; Errors = $errors }
}

function Test-PromotionReadiness {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)]$Decision,
        [Parameter(Mandatory = $true)]$IntegrationEvent,
        [Parameter(Mandatory = $true)][string]$Repository,
        [Parameter(Mandatory = $true)][string]$HeadSha,
        [Parameter(Mandatory = $true)][string]$BaseSha
    )
    $errors = @()
    if ($Decision.run_id -ne $Manifest.run_id -or $Decision.manifest_digest -notmatch '^[0-9a-fA-F]{64}$') { $errors += 'Assessment run identity or manifest digest is invalid' }
    if ($Decision.overall_passed -isnot [bool] -or -not $Decision.overall_passed -or
        $Decision.decisions.collection -ne 'Complete' -or $Decision.decisions.tests -ne 'Passed' -or
        $Decision.decisions.floors -ne 'Passed' -or
        $Decision.decisions.comparison_compatibility -ne 'Compatible' -or
        $Decision.decisions.regression -ne 'Passed') {
        $errors += 'Independent coverage assessment is not complete, passing, and promotion-eligible'
    }
    $integrationConfirmed = if ($IntegrationEvent.event_type -ceq 'target_push') {
        $IntegrationEvent.integration_confirmed -is [bool] -and [bool]$IntegrationEvent.integration_confirmed
    } else {
        $IntegrationEvent.event_type -ceq 'pull_request' -and $IntegrationEvent.merged -is [bool] -and [bool]$IntegrationEvent.merged
    }
    if (-not $integrationConfirmed) { $errors += 'A verified completed pull-request merge or current target-push integration event is required for promotion' }
    if ($IntegrationEvent.repository -cne $Repository -or $IntegrationEvent.head_sha -cne $HeadSha -or $IntegrationEvent.base_sha -cne $BaseSha) { $errors += 'Integration event repository/head/base does not match the verified run identity' }
    if ($Manifest.git.source_commit -cne $HeadSha -or $Manifest.git.target_commit -cne $BaseSha) { $errors += 'Verified run head/base does not match manifest source/target commits' }
    if ($IntegrationEvent.integration_commit -notmatch '^[0-9a-fA-F]{40,64}$' -or $IntegrationEvent.integration_tree -notmatch '^[0-9a-fA-F]{40,64}$' -or $IntegrationEvent.integration_tree -cne $Manifest.git.source_tree) { $errors += 'Integrated commit/tree must be explicit and retain the exact tested tree' }
    return @{ IsValid = ($errors.Count -eq 0); IntegratedCommit = [string]$IntegrationEvent.integration_commit; IntegratedTree = [string]$IntegrationEvent.integration_tree; Errors = $errors }
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

    if ($Case.success_gate) {
        $successGate = Test-ReportSuccessEligibility -Decision $Case.success_gate.decision -Receipt $Case.success_gate.receipt -ExpectedRunId $Case.success_gate.run_id -ExpectedManifestDigest $Case.success_gate.manifest_digest -ExpectedBundleDigest $Case.success_gate.bundle_digest
        $errors += $successGate.Errors
    }

    return @{
        IsValid = ($errors.Count -eq 0)
        Errors = $errors
    }
}

function Test-ReportSuccessEligibility {
    param(
        [Parameter(Mandatory = $true)]$Decision,
        [Parameter(Mandatory = $true)]$Receipt,
        [Parameter(Mandatory = $true)][string]$ExpectedRunId,
        [Parameter(Mandatory = $true)][string]$ExpectedManifestDigest,
        [Parameter(Mandatory = $true)][string]$ExpectedBundleDigest
    )
    $errors = @()
    if ($Decision.run_id -cne $ExpectedRunId -or $Decision.overall_passed -isnot [bool] -or -not $Decision.overall_passed -or
        $Decision.decisions.collection -ne 'Complete' -or $Decision.decisions.tests -ne 'Passed' -or
        $Decision.decisions.floors -ne 'Passed' -or $Decision.decisions.comparison_compatibility -ne 'Compatible' -or
        $Decision.decisions.regression -ne 'Passed') {
        $errors += 'Independent assessment is not complete, passing, compatible, and regression-safe'
    }
    if ($Decision.manifest_digest -cne $ExpectedManifestDigest -or $ExpectedManifestDigest -notmatch '^[0-9a-f]{64}$') {
        $errors += 'Independent assessment is not bound to the current sealed manifest digest'
    }
    if ($Receipt.run_id -cne $ExpectedRunId -or $Receipt.status -cne 'Published' -or $Receipt.retrievable -isnot [bool] -or -not $Receipt.retrievable) {
        $errors += 'Coverage evidence publication is not successfully retrievable'
    }
    if ($Receipt.bundle_digest -cne $ExpectedBundleDigest -or $Receipt.bundle_digest -notmatch '^[0-9a-f]{64}$') {
        $errors += 'Publication receipt does not bind the exact verified bundle digest'
    }
    if ($Receipt.content_readback_verified -isnot [bool] -or -not $Receipt.content_readback_verified) {
        $errors += 'Publication receipt does not prove archived bundle content was read back and verified'
    }
    if ($Receipt.publication_purpose -cne 'Candidate') {
        $errors += 'Publication receipt purpose is not Candidate'
    }
    if ($Receipt.archive_branch -cne 'codex/coverage-evidence' -or $Receipt.archive_ref -cne 'refs/heads/codex/coverage-evidence') {
        $errors += 'Publication receipt does not identify the trusted coverage evidence branch'
    }
    if ($Receipt.archive_commit -notmatch '^[0-9a-f]{40,64}$' -or $Receipt.read_back_commit -cne $Receipt.archive_commit -or
        $Receipt.archive_ref -cne "refs/heads/$($Receipt.archive_branch)" -or $Receipt.archived_bundle_path -cne "runs/$ExpectedRunId") {
        $errors += 'Publication receipt lacks exact remote read-back and immutable bundle location evidence'
    }
    return @{ IsEligible = ($errors.Count -eq 0); Errors = $errors }
}

function Test-ReportFinalBinding {
    param(
        [Parameter(Mandatory = $true)][string]$VerifiedManifestDigest,
        [Parameter(Mandatory = $true)][string]$CurrentManifestDigest,
        [Parameter(Mandatory = $true)][string]$VerifiedBundleDigest,
        [Parameter(Mandatory = $true)][string]$CurrentBundleDigest,
        [Parameter(Mandatory = $true)]$CandidateIdentity,
        [Parameter(Mandatory = $true)]$ReporterBinding,
        [Parameter(Mandatory = $true)]$ManifestValidation,
        [Parameter(Mandatory = $true)]$PublicationReceipt
    )
    $errors = @()
    if ($CurrentManifestDigest -cne $VerifiedManifestDigest -or $CurrentManifestDigest -notmatch '^[0-9a-f]{64}$') {
        $errors += 'The verified capture manifest changed after assessment'
    }
    if ($CurrentBundleDigest -cne $VerifiedBundleDigest -or $CurrentBundleDigest -notmatch '^[0-9a-f]{64}$') {
        $errors += 'The verified capture bundle changed after assessment'
    }
    if (-not $CandidateIdentity.IsValid) { $errors += @($CandidateIdentity.Errors) }
    if (-not $ReporterBinding.IsValid) { $errors += @($ReporterBinding.Errors) }
    if (-not $ManifestValidation.IsValid) { $errors += @($ManifestValidation.Errors) }
    if ($PublicationReceipt.manifest_digest -cne $VerifiedManifestDigest -or
        $PublicationReceipt.bundle_digest -cne $VerifiedBundleDigest) {
        $errors += 'Publication receipt does not bind the verified final manifest and bundle digests'
    }
    return @{ IsValid = ($errors.Count -eq 0); Errors = $errors }
}

function Resolve-ReportLiveConclusion {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('success', 'failure')][string]$CurrentConclusion,
        [Parameter(Mandatory = $true)]$LiveCandidateValidation
    )
    $errors = @()
    $conclusion = $CurrentConclusion
    if (-not $LiveCandidateValidation.IsValid) {
        $errors = @($LiveCandidateValidation.Errors | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if ($errors.Count -eq 0) { $errors = @('Live candidate metadata could not be validated') }
        $conclusion = 'failure'
    }
    return @{
        Conclusion = $conclusion
        CanCreateCheck = $true
        Errors = $errors
    }
}

function Sort-TestSelectionCandidates {
    param(
        [Parameter(Mandatory = $true)][array]$Candidates,
        [Parameter(Mandatory = $true)]$Policy
    )
    if (-not (Test-NonnegativeIntegerValue -Value $Policy.tier1_instruction_min_percent) -or
        -not (Test-NonnegativeIntegerValue -Value $Policy.tier1_branch_min_percent) -or
        -not (Test-NonnegativeIntegerValue -Value $Policy.tier2_instruction_min_percent)) {
        throw 'Selection policy has invalid tier coverage floors'
    }
    $knownModules = @($Policy.tier1_modules) + @($Policy.tier2_gated_modules) + @($Policy.production_scope.variants.PSObject.Properties.Name)
    $knownModules = @($knownModules | ForEach-Object { [string]$_ } | Select-Object -Unique)
    $candidateIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $augmented = foreach ($c in $Candidates) {
        if ($null -eq $c -or $c -isnot [System.Management.Automation.PSCustomObject]) { throw 'Selection candidate must be a JSON object' }
        if ($c.PSObject.Properties['module_deficit'] -or $c.PSObject.Properties['module_deficit_numerator'] -or $c.PSObject.Properties['module_deficit_denominator']) {
            throw "Candidate '$($c.candidate_id)' must not supply a derived module deficit; provide raw instruction and branch counters"
        }
        if ([string]$c.candidate_id -notmatch '^COV-[0-9]{3}$' -or [string]$c.module -notmatch '^:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*$') {
            throw 'Selection candidate requires a stable COV-NNN identifier and a Gradle module path'
        }
        if (-not $candidateIds.Add([string]$c.candidate_id)) { throw "Selection candidate identifier '$($c.candidate_id)' is duplicated" }
        if ($c.module -notin $knownModules) { throw "Candidate '$($c.candidate_id)' references module '$($c.module)' outside the trusted production inventory" }
        if (-not (Test-NonnegativeIntegerValue -Value $c.impact) -or [int]$c.impact -lt 1 -or [int]$c.impact -gt 4 -or
            -not (Test-NonnegativeIntegerValue -Value $c.likelihood) -or [int]$c.likelihood -lt 1 -or [int]$c.likelihood -gt 3) {
            throw "Candidate '$($c.candidate_id)' has an invalid impact or likelihood rank"
        }
        if ($null -eq $c.requirement_ids -or @($c.requirement_ids).Count -eq 0 -or
            @($c.requirement_ids | Where-Object { [string]$_ -notmatch '^(FR|NFR|TR|SC)-[A-Z0-9]+(?:-[A-Z0-9]+)*$' }).Count -gt 0 -or
            [string]::IsNullOrWhiteSpace([string]$c.risk_rationale) -or
            [string]::IsNullOrWhiteSpace([string]$c.initial_outcome) -or
            [string]::IsNullOrWhiteSpace([string]$c.expected_outcome) -or
            $null -eq $c.forbidden_effects -or @($c.forbidden_effects).Count -eq 0 -or
            @($c.forbidden_effects | Where-Object { [string]::IsNullOrWhiteSpace([string]$_) }).Count -gt 0) {
            throw "Candidate '$($c.candidate_id)' requires stable requirements, risk rationale, initial/expected outcomes, and forbidden effects"
        }
        $placement = $c.test_placement
        $placementPath = [string]$placement.file
        if ($null -eq $placement -or $placementPath -notmatch '^[A-Za-z0-9_./-]+\.kt$' -or $placementPath.StartsWith('/') -or
            $placementPath -match '(^|/)\.\.(/|$)' -or [string]::IsNullOrWhiteSpace([string]$placement.class) -or
            [string]::IsNullOrWhiteSpace([string]$placement.test_name) -or $placement.decision -notin @('Extend', 'New')) {
            throw "Candidate '$($c.candidate_id)' requires exact safe test-file, class, test-name, and Extend/New placement metadata"
        }
        $maxNumerator = [System.Numerics.BigInteger]::Zero
        $maxDenominator = [System.Numerics.BigInteger]::One
        $applicableMetricCount = 0
        $isTier1 = [string]$c.module -cin @($Policy.tier1_modules | ForEach-Object { [string]$_ })
        $metrics = if ($isTier1) { @('instruction', 'branch') } else { @('instruction') }
        foreach ($metric in @('instruction', 'branch')) {
            $counter = $c.$metric
            if (-not $counter -or -not (Test-NonnegativeIntegerValue -Value $counter.covered) -or -not (Test-NonnegativeIntegerValue -Value $counter.missed) -or -not (Test-NonnegativeIntegerValue -Value $counter.total)) {
                throw "Candidate '$($c.candidate_id)' is missing valid raw $metric covered/missed/total counts"
            }
            $covered = ConvertTo-CoverageBigInteger -Value $counter.covered -Label "$($c.candidate_id) $metric covered"
            $missed = ConvertTo-CoverageBigInteger -Value $counter.missed -Label "$($c.candidate_id) $metric missed"
            $total = ConvertTo-CoverageBigInteger -Value $counter.total -Label "$($c.candidate_id) $metric total"
            if ($total -ne ($covered + $missed)) { throw "Candidate '$($c.candidate_id)' has inconsistent $metric total" }
            if ($metric -eq 'instruction' -and $total -eq 0) { throw "Candidate '$($c.candidate_id)' has no applicable instruction denominator" }
            if ($metric -notin $metrics -or $total -eq 0) { continue }
            $applicableMetricCount++
            $floor = if ($isTier1 -and $metric -eq 'branch') { [int]$Policy.tier1_branch_min_percent } elseif ($isTier1) { [int]$Policy.tier1_instruction_min_percent } else { [int]$Policy.tier2_instruction_min_percent }
            $gapNumerator = ([System.Numerics.BigInteger]$floor * $total) - ([System.Numerics.BigInteger]100 * $covered)
            if ($gapNumerator -lt 0) { $gapNumerator = [System.Numerics.BigInteger]::Zero }
            if (($gapNumerator * $maxDenominator) -gt ($maxNumerator * $total)) {
                $maxNumerator = $gapNumerator
                $maxDenominator = $total
            }
        }
        if ($applicableMetricCount -eq 0) { throw "Candidate '$($c.candidate_id)' has no applicable Tier 1 metric denominator" }
        [PSCustomObject]@{
            Candidate = $c
            Impact = [int]$c.impact
            Likelihood = [int]$c.likelihood
            DeficitNum = $maxNumerator
            DeficitDen = $maxDenominator
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

function Test-SelectionReceiptArtifact {
    param(
        [Parameter(Mandatory = $true)]$Receipt,
        [Parameter(Mandatory = $true)][string]$ExpectedType,
        [Parameter(Mandatory = $true)][string]$ExpectedStatus,
        [Parameter(Mandatory = $true)][string]$SelectedCase
    )
    $path = [string]$Receipt.artifact_path
    if ([string]::IsNullOrWhiteSpace($path) -or -not [System.IO.Path]::IsPathRooted($path) -or
        -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "$ExpectedType receipt artifact is missing or not an absolute file path"
    }
    $actualDigest = Get-FileSha256 -Path $path
    if ([string]$Receipt.sha256 -notmatch '^[0-9a-fA-F]{64}$' -or $actualDigest -cne ([string]$Receipt.sha256).ToLowerInvariant()) {
        throw "$ExpectedType receipt artifact SHA-256 mismatch"
    }
    $artifact = Read-SafeJson -Path $path
    if ($artifact.receipt_type -cne $ExpectedType -or $artifact.status -cne $ExpectedStatus -or $artifact.candidate_id -cne $SelectedCase) {
        throw "$ExpectedType receipt artifact does not bind the expected status and candidate"
    }
}

function Test-SelectionMeasurementArtifact {
    param(
        [Parameter(Mandatory = $true)]$Measurement,
        [Parameter(Mandatory = $true)][string]$SelectedCase
    )
    $bundlePath = [string]$Measurement.bundle_path
    $manifestPath = [string]$Measurement.manifest_path
    if ([string]::IsNullOrWhiteSpace($bundlePath) -or -not [System.IO.Path]::IsPathRooted($bundlePath) -or
        [string]::IsNullOrWhiteSpace($manifestPath) -or -not [System.IO.Path]::IsPathRooted($manifestPath) -or
        -not (Test-Path -LiteralPath $bundlePath -PathType Container) -or -not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw 'Fresh measurement bundle and manifest must be existing absolute paths'
    }
    $canonicalBundle = [System.IO.Path]::GetFullPath($bundlePath).TrimEnd('\', '/')
    $canonicalManifest = [System.IO.Path]::GetFullPath($manifestPath)
    if ($canonicalManifest -cne [System.IO.Path]::GetFullPath((Join-Path $canonicalBundle 'manifest.json'))) {
        throw 'Fresh measurement manifest must be the manifest.json sealed in its bundle'
    }
    $bundleDigest = Get-CoverageBundleDigest -BundlePath $canonicalBundle
    if ([string]$Measurement.bundle_digest -notmatch '^[0-9a-fA-F]{64}$' -or $bundleDigest -cne ([string]$Measurement.bundle_digest).ToLowerInvariant()) {
        throw 'Fresh measurement bundle digest mismatch'
    }
    $manifest = Read-SafeJson -Path $canonicalManifest
    if ($manifest.run_id -cne $Measurement.run_id -or
        $manifest.git.source_commit -cne $Measurement.candidate_sha -or
        $manifest.git.clean_preflight -isnot [bool] -or -not $manifest.git.clean_preflight -or
        $manifest.git.clean_postflight -isnot [bool] -or -not $manifest.git.clean_postflight -or
        $manifest.harness.clean -isnot [bool] -or -not $manifest.harness.clean -or
        $manifest.decisions.collection -cne 'Complete' -or $manifest.decisions.tests -cne 'Passed') {
        throw "Fresh measurement manifest does not prove a clean, complete, passing run for $SelectedCase at the candidate revision"
    }
    $requiredSuites = @($manifest.suites | Where-Object { $_.required -eq $true })
    if ($requiredSuites.Count -eq 0 -or @($requiredSuites | Where-Object { (Test-SuiteOutcome -SuiteResult $_).Status -ne 'Passed' }).Count -gt 0) {
        throw 'Fresh measurement manifest has missing or failing required test suites'
    }
}

function Test-BatchProgression {
    param(
        [Parameter(Mandatory = $true)][int]$CurrentRank,
        [Parameter(Mandatory = $true)][int]$AttemptedNextRank,
        [Parameter(Mandatory = $false)][int]$CurrentLikelihood = 1,
        [Parameter(Mandatory = $false)][int]$AttemptedNextLikelihood = 1,
        [Parameter(Mandatory = $true)][string]$TestStatus,
        [Parameter(Mandatory = $false)][string]$SelectedCase,
        [Parameter(Mandatory = $false)]$Receipts
    )
    if ($CurrentRank -lt 1 -or $CurrentRank -gt 4 -or $AttemptedNextRank -lt 1 -or $AttemptedNextRank -gt 4 -or
        $CurrentLikelihood -lt 1 -or $CurrentLikelihood -gt 3 -or $AttemptedNextLikelihood -lt 1 -or $AttemptedNextLikelihood -gt 3) {
        return @{ Allowed = $false; Error = 'Progression ranks must use supported impact 1-4 and likelihood 1-3 values' }
    }
    $lowerRiskProgression = ($CurrentRank -gt $AttemptedNextRank) -or ($CurrentRank -eq $AttemptedNextRank -and $CurrentLikelihood -gt $AttemptedNextLikelihood)
    if ($lowerRiskProgression) {
        if ($TestStatus -in @("Failed", "Unverified")) {
            return @{
                Allowed = $false
                Error = "Cannot advance to lower risk rank while higher rank test $SelectedCase is Failed or Unverified"
            }
        }
        $receiptErrors = @()
        if ($TestStatus -ne 'Passed') { $receiptErrors += 'current higher-risk test status must be Passed' }
        if (-not $Receipts -or $Receipts.focused_result.status -ne 'Passed' -or [string]::IsNullOrWhiteSpace([string]$Receipts.focused_result.artifact_path) -or ([string]$Receipts.focused_result.sha256) -notmatch '^[0-9a-fA-F]{64}$') {
            $receiptErrors += 'focused-pass receipt is missing or unbound'
        } else {
            try { Test-SelectionReceiptArtifact -Receipt $Receipts.focused_result -ExpectedType 'focused-pass' -ExpectedStatus 'Passed' -SelectedCase $SelectedCase } catch { $receiptErrors += $_.Exception.Message }
        }
        if (-not $Receipts -or $Receipts.repair_required -isnot [bool]) {
            $receiptErrors += 'repair requirement disposition is missing'
        } elseif ($Receipts.repair_required) {
            if ($Receipts.repair_result.status -ne 'Completed' -or [string]::IsNullOrWhiteSpace([string]$Receipts.repair_result.artifact_path) -or ([string]$Receipts.repair_result.sha256) -notmatch '^[0-9a-fA-F]{64}$') { $receiptErrors += 'required repair receipt is missing or unbound' }
            else { try { Test-SelectionReceiptArtifact -Receipt $Receipts.repair_result -ExpectedType 'repair' -ExpectedStatus 'Completed' -SelectedCase $SelectedCase } catch { $receiptErrors += $_.Exception.Message } }
        } elseif ($Receipts.repair_result.status -ne 'NotRequired') {
            $receiptErrors += 'repair disposition must explicitly state NotRequired when no repair was needed'
        }
        if (-not $Receipts -or $Receipts.measurement.clean -isnot [bool] -or -not $Receipts.measurement.clean -or
            [string]::IsNullOrWhiteSpace([string]$Receipts.measurement.run_id) -or [string]$Receipts.measurement.candidate_sha -notmatch '^[0-9a-fA-F]{40,64}$') {
            $receiptErrors += 'clean fresh measurement receipt is missing or invalid'
        } else {
            try { Test-SelectionMeasurementArtifact -Measurement $Receipts.measurement -SelectedCase $SelectedCase } catch { $receiptErrors += $_.Exception.Message }
        }
        if (-not $Receipts -or $Receipts.reranking.performed -isnot [bool] -or -not $Receipts.reranking.performed -or
            [string]::IsNullOrWhiteSpace([string]$Receipts.reranking.artifact_path) -or ([string]$Receipts.reranking.sha256) -notmatch '^[0-9a-fA-F]{64}$') {
            $receiptErrors += 'post-measurement reranking receipt is missing or unbound'
        } else {
            try { Test-SelectionReceiptArtifact -Receipt $Receipts.reranking -ExpectedType 'reranking' -ExpectedStatus 'Complete' -SelectedCase $SelectedCase } catch { $receiptErrors += $_.Exception.Message }
        }
        if ($receiptErrors.Count -gt 0) {
            return @{ Allowed = $false; Error = "Progression blocked for ${SelectedCase}: $($receiptErrors -join '; ')" }
        }
    }
    return @{
        Allowed = $true
        Error = $null
    }
}

function Test-SelectionCase {
    param(
        [Parameter(Mandatory = $true)]$Case,
        [Parameter(Mandatory = $true)]$Policy
    )

    $errors = @()

    if ($Case.candidates) {
        try {
            $sorted = Sort-TestSelectionCandidates -Candidates $Case.candidates -Policy $Policy
            if ($sorted[0].candidate_id -ne $Case.expected_first) {
                $errors += "Expected first candidate to be '$($Case.expected_first)', but got '$($sorted[0].candidate_id)'"
            }
        } catch {
            $errors += $_.Exception.Message
        }
    }

    if ($Case.batch_state) {
        $bs = $Case.batch_state
        $prog = Test-BatchProgression -CurrentRank $bs.current_rank -AttemptedNextRank $bs.attempted_next_rank -CurrentLikelihood $bs.current_likelihood -AttemptedNextLikelihood $bs.attempted_next_likelihood -TestStatus $bs.test_status -SelectedCase $bs.selected_case -Receipts $bs.receipts
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




