[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [string]$FeatureRelativePath = 'specs/065-security-secret-cleanup',
    [string]$ReportRelativePath = 'specs/065-security-secret-cleanup/evidence/evidence-validation.md',
    [string]$CandidateRevision
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$featurePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $FeatureRelativePath))
$indexPath = Join-Path $featurePath 'evidence/index.md'
$reportPath = Join-Path $RepositoryRoot $ReportRelativePath
$hashManifestPath = Join-Path $featurePath 'evidence/evidence-validation.hashes.txt'
$allowedStatuses = @('Passed', 'Failed', 'Deferred', 'Unverified', 'Satisfied', 'Needs correction', 'Pending approval')

function Get-EvidenceCells([string]$Line) {
    $parts = $Line.Split('|')
    if ($parts.Count -lt 3) { return @() }
    return @($parts[1..($parts.Count - 2)] | ForEach-Object { $_.Trim() })
}

function Resolve-ArtifactPath([string]$Value) {
    $candidate = $Value.Trim().Trim('`') -replace '\s*\(SHA-256.*$', ''
    if ($candidate.StartsWith('evidence/', [StringComparison]::OrdinalIgnoreCase)) {
        return [IO.Path]::GetFullPath((Join-Path $featurePath $candidate))
    }
    if ([IO.Path]::IsPathRooted($candidate)) { return [IO.Path]::GetFullPath($candidate) }
    return [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $candidate))
}

function Get-ArtifactCandidates([string]$ArtifactCell) {
    $paths = [Collections.Generic.List[string]]::new()
    foreach ($segment in ($ArtifactCell -split ';')) {
        if ($segment -match '\]\(https?://[^)]+\)') { continue }
        $codeSpans = [regex]::Matches($segment, '`([^`]+)`')
        if ($codeSpans.Count -gt 0) {
            foreach ($span in $codeSpans) {
                $value = $span.Groups[1].Value.Trim()
                if ($value -match '[/\\]' -and $value -notmatch '^[0-9a-fA-F]{32,}$') { $paths.Add($value) }
            }
            continue
        }
        $value = $segment.Trim()
        if ($value -match '^(https?://|N/?A$|none$|not retained$|restricted$|unavailable$)') { continue }
        if ($value -match '[/\\]') { $paths.Add($value) }
    }
    return $paths
}

function Get-RecordErrors([string[]]$Cells, [string]$CurrentCandidate) {
    $errors = [Collections.Generic.List[string]]::new()
    if ($Cells.Count -ne 10) {
        $errors.Add("expected 10 fields, received $($Cells.Count)")
        return $errors
    }
    for ($index = 0; $index -lt 10; $index++) {
        if ([string]::IsNullOrWhiteSpace($Cells[$index])) { $errors.Add("field $($index + 1) is empty") }
    }
    $parsedDate = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($Cells[6], 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$parsedDate)) {
        $errors.Add('date is not a valid ISO YYYY-MM-DD calendar date')
    }
    if ($Cells[8] -notin $allowedStatuses) { $errors.Add("status '$($Cells[8])' is outside the TRD taxonomy") }
    if ($Cells[8] -eq 'Satisfied' -and $Cells[3] -match '(?i)(:.*:test\w*|connected\w*AndroidTest|adb\b|instrumentation|physical device|heap dump|HPROF)') {
        $errors.Add('documentary Satisfied status cannot certify a runtime procedure')
    }
    if ($Cells[8] -eq 'Passed' -and $Cells[5] -match '(?i)empty workload|no workload|0 tests executed|no tests discovered|0 samples collected') {
        $errors.Add('empty workload cannot pass')
    }
    if ($CurrentCandidate -and $Cells[2] -notmatch [regex]::Escape($CurrentCandidate)) {
        $errors.Add('revision does not identify the required frozen candidate')
    }
    if (-not [string]::IsNullOrWhiteSpace($Cells[9])) {
        foreach ($clean in (Get-ArtifactCandidates $Cells[9])) {
            try { $artifactPath = Resolve-ArtifactPath $clean }
            catch { $errors.Add("artifact path is invalid: $clean"); continue }
            if (-not (Test-Path -LiteralPath $artifactPath)) { $errors.Add("artifact is inaccessible: $clean") }
        }
    }
    return $errors
}

function Assert-SyntheticRejection([string]$Name, [string[]]$Cells, [string]$CurrentCandidate) {
    $result = @(Get-RecordErrors $Cells $CurrentCandidate)
    if ($result.Count -eq 0) { throw "Synthetic invalid record was accepted: $Name" }
    return [pscustomobject]@{ Example = $Name; Rejected = $true; Reason = ($result -join '; ') }
}

$validSynthetic = @('FR-TEST', 'Synthetic record', 'candidate-abc', 'SyntheticProcedure.test', 'Host fixture', 'One case passed', '2026-11-15', 'Synthetic only', 'Passed', 'evidence/index.md')
$invalidExamples = @(
    (Assert-SyntheticRejection 'missing required field' @($validSynthetic | ForEach-Object -Begin { $n = 0 } -Process { if ($n++ -ne 5) { $_ } }) ''),
    (Assert-SyntheticRejection 'non-taxonomy status' @($validSynthetic[0..7] + @('Passed (test)') + $validSynthetic[9]) ''),
    (Assert-SyntheticRejection 'empty workload marked Passed' @($validSynthetic[0..4] + @('Empty workload: zero tests executed') + $validSynthetic[6..9]) ''),
    (Assert-SyntheticRejection 'documentary status on runtime procedure' @($validSynthetic[0..2] + @(':app:connectedReleaseAndroidTest') + $validSynthetic[4..7] + @('Satisfied') + $validSynthetic[9]) ''),
    (Assert-SyntheticRejection 'stale candidate identity' $validSynthetic 'different-candidate')
)

$allLines = Get-Content -LiteralPath $indexPath
$recordLines = @($allLines | Where-Object { $_ -match '^\|.*\|$' -and $_ -notmatch '^\|\s*-+' })
if ($recordLines.Count -lt 2) { throw 'Evidence index does not contain a header and records.' }
$records = @($recordLines | Select-Object -Skip 1)
$errorsByRow = [Collections.Generic.List[string]]::new()
$statusCounts = @{}
$artifactManifest = [Collections.Generic.List[string]]::new()
$artifactCount = 0

for ($rowIndex = 0; $rowIndex -lt $records.Count; $rowIndex++) {
    $cells = Get-EvidenceCells $records[$rowIndex]
    $rowErrors = @(Get-RecordErrors $cells $CandidateRevision)
    if ($cells.Count -eq 10) {
        $statusCounts[$cells[8]] = 1 + [int]$statusCounts[$cells[8]]
        foreach ($clean in (Get-ArtifactCandidates $cells[9])) {
            try { $artifactPath = Resolve-ArtifactPath $clean } catch { continue }
            if (-not (Test-Path -LiteralPath $artifactPath)) { continue }
            if (Test-Path -LiteralPath $artifactPath -PathType Leaf) {
                if ([IO.Path]::GetFullPath($artifactPath) -eq [IO.Path]::GetFullPath($reportPath) -or
                    [IO.Path]::GetFullPath($artifactPath) -eq [IO.Path]::GetFullPath($hashManifestPath)) { continue }
                $hash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
                $relative = [IO.Path]::GetRelativePath($RepositoryRoot, $artifactPath).Replace('\', '/')
                $artifactManifest.Add("| $relative | $hash |")
                $artifactCount++
            }
        }
    }
    if ($rowErrors.Count -gt 0) { $errorsByRow.Add("Row $($rowIndex + 1): $($rowErrors -join '; ')") }
}

$date = Get-Date -Format 'yyyy-MM-dd'
$scriptHash = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
$report = [Collections.Generic.List[string]]::new()
$report.Add('# Feature 065 evidence-index validation')
$report.Add('')
$report.Add("- Date: $date")
$report.Add('- Candidate: dirty exploratory worktree; no frozen product candidate supplied.')
$report.Add("- Validator SHA-256: ``$scriptHash``")
$report.Add("- Records parsed: $($records.Count)")
$report.Add("- Artifact file hashes recorded: $artifactCount")
$report.Add("- Real-record errors: $($errorsByRow.Count)")
$report.Add("- Synthetic invalid-record examples rejected: $($invalidExamples.Count)/$($invalidExamples.Count)")
$report.Add('')
$report.Add('## Status counts')
$report.Add('')
$report.Add('| Status | Count |')
$report.Add('|---|---:|')
foreach ($status in ($statusCounts.Keys | Sort-Object)) { $report.Add("| $status | $($statusCounts[$status]) |") }
$report.Add('')
$report.Add('## Synthetic negative examples')
$report.Add('')
$report.Add('| Invalid record | Rejected | Reason |')
$report.Add('|---|---|---|')
foreach ($example in $invalidExamples) { $report.Add("| $($example.Example) | Yes | $($example.Reason) |") }
$report.Add('')
$report.Add('## Artifact hash manifest')
$report.Add('')
$report.Add('| Repository artifact | SHA-256 |')
$report.Add('|---|---|')
foreach ($entry in ($artifactManifest | Sort-Object -Unique)) { $report.Add($entry) }
$report.Add('')
$report.Add('## Findings')
$report.Add('')
if ($errorsByRow.Count -eq 0) {
    $report.Add("All parsed rows satisfy this run's structural, status, date, workload, and artifact-access checks. Candidate freshness is not established because the worktree is not frozen.")
} else {
    foreach ($entry in $errorsByRow) { $report.Add("- $entry") }
    $report.Add('')
    $report.Add('This report is a remediation aid, not a T037 pass. Any stale evidence or missing linked artifact must be corrected or explicitly recorded as Unverified before acceptance.')
}

$reportDirectory = Split-Path -Parent $reportPath
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
Set-Content -LiteralPath $reportPath -Value $report -Encoding utf8
Write-Output "Records=$($records.Count) Errors=$($errorsByRow.Count) ArtifactHashes=$artifactCount SyntheticRejections=$($invalidExamples.Count) Report=$reportPath"
if ($errorsByRow.Count -gt 0) { exit 1 }
