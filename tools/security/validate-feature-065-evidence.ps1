[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [string]$FeatureRelativePath = 'specs/065-security-secret-cleanup',
    [string]$ReportRelativePath = 'specs/065-security-secret-cleanup/evidence/evidence-validation.md',
    [string]$CandidateRevision
)

$ErrorActionPreference = 'Stop'
if ($CandidateRevision -and $CandidateRevision -notmatch '^[0-9a-fA-F]{40}$') {
    throw 'CandidateRevision must be the full 40-character commit hash.'
}
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)

function Get-CanonicalCommitRevision([string]$Revision) {
    if ([string]::IsNullOrWhiteSpace($Revision) -or $Revision -notmatch '^[0-9a-fA-F]{40}$') { return $null }
    $resolved = & git -C $RepositoryRoot rev-parse --verify "$($Revision)^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    $canonical = ($resolved -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($canonical) -or $canonical -ine $Revision) { return $null }
    return $canonical
}

function Get-DeclaredCandidateRevision([string]$RevisionCell, [string]$ExpectedRevision) {
    if ([string]::IsNullOrWhiteSpace($RevisionCell) -or [string]::IsNullOrWhiteSpace($ExpectedRevision)) { return '' }
    if ($RevisionCell -match '(?i)dirty|exploratory|historical|no frozen candidate|pre-rebase') { return '' }
    $match = [regex]::Match(
        $RevisionCell.Trim(),
        '^(?:(?:freeze|frozen)\s+candidate|candidate(?:\s+commit)?)\s+(?<candidate>[0-9a-fA-F]{40})(?=$|[\s/;(])|^(?<candidate>[0-9a-fA-F]{40})$'
    )
    if (-not $match.Success) { return '' }
    $declaredRevision = $match.Groups['candidate'].Value
    if ($declaredRevision -ine $ExpectedRevision) { return '' }
    return $ExpectedRevision
}

if ($CandidateRevision) {
    $resolvedCandidate = Get-CanonicalCommitRevision $CandidateRevision
    if (-not $resolvedCandidate) {
        throw 'CandidateRevision must identify an existing commit in this repository.'
    }
}

$repositoryHead = (& git -C $RepositoryRoot rev-parse --verify 'HEAD^{commit}' 2>$null | Out-String).Trim()
if (-not (Get-CanonicalCommitRevision $repositoryHead)) {
    throw 'Validator self-check failed: repository HEAD was not recognized as a full commit.'
}
$candidateDeclaration = Get-DeclaredCandidateRevision "frozen candidate $repositoryHead/tree synthetic" $repositoryHead
if ($candidateDeclaration -ine $repositoryHead) {
    throw 'Validator self-check failed: an explicit matching candidate declaration was not recognized.'
}
if (Get-DeclaredCandidateRevision "historical evidence references candidate $repositoryHead" $repositoryHead) {
    throw 'Validator self-check failed: a historical mention was accepted as candidate evidence.'
}
if (Get-DeclaredCandidateRevision "dirty exploratory worktree HEAD $repositoryHead" $repositoryHead) {
    throw 'Validator self-check failed: an exploratory revision was accepted as candidate evidence.'
}
$fabricatedCommit = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
if (Get-CanonicalCommitRevision $fabricatedCommit) {
    throw 'Validator self-check failed: a fabricated full-length revision was accepted as a commit.'
}
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

function Get-ExpectedArtifactDigests([string]$ArtifactCell) {
    $declarations = [Collections.Generic.List[object]]::new()
    $pattern = '`(?<path>[^`]+)`\s*\(SHA-256\s+`(?<hash>[0-9a-fA-F]{64})`\)'
    foreach ($match in [regex]::Matches($ArtifactCell, $pattern)) {
        $declarations.Add([pscustomobject]@{
            Path = $match.Groups['path'].Value.Trim()
            Hash = $match.Groups['hash'].Value.ToLowerInvariant()
        })
    }
    return $declarations
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
    if ($CurrentCandidate -and (
            $Cells[2] -match '(?i)dirty exploratory|no frozen candidate|exploratory worktree' -or
            $Cells[2] -notmatch "(?<![0-9a-fA-F])$([regex]::Escape($CurrentCandidate))(?![0-9a-fA-F])")) {
        $errors.Add('revision is exploratory or does not identify the exact required frozen candidate')
    }
    if ($CurrentCandidate -and $Cells[8] -in @('Passed', 'Satisfied')) {
        $declaredDigests = @(Get-ExpectedArtifactDigests $Cells[9])
        $pinnedPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($declaration in $declaredDigests) {
            try { $pinnedPath = [IO.Path]::GetFullPath((Resolve-ArtifactPath $declaration.Path)) }
            catch { continue }
            $null = $pinnedPaths.Add($pinnedPath)
        }
        $candidateArtifacts = [Collections.Generic.List[string]]::new()
        foreach ($clean in (Get-ArtifactCandidates $Cells[9])) {
            try { $artifactPath = Resolve-ArtifactPath $clean } catch { continue }
            if (Test-Path -LiteralPath $artifactPath -PathType Leaf) {
                $candidateArtifacts.Add([IO.Path]::GetFullPath($artifactPath))
            }
        }
        if ($pinnedPaths.Count -eq 0) {
            $errors.Add('candidate Passed/Satisfied record has no retained expected artifact digest')
        }
        foreach ($artifactPath in $candidateArtifacts) {
            if (-not $pinnedPaths.Contains($artifactPath)) {
                $errors.Add("candidate artifact has no retained expected digest: $artifactPath")
            }
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($Cells[9])) {
        foreach ($clean in (Get-ArtifactCandidates $Cells[9])) {
            try { $artifactPath = Resolve-ArtifactPath $clean }
            catch { $errors.Add("artifact path is invalid: $clean"); continue }
            if (-not (Test-Path -LiteralPath $artifactPath)) { $errors.Add("artifact is inaccessible: $clean") }
        }
        foreach ($declaration in (Get-ExpectedArtifactDigests $Cells[9])) {
            try { $artifactPath = Resolve-ArtifactPath $declaration.Path }
            catch { $errors.Add("artifact digest path is invalid: $($declaration.Path)"); continue }
            if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
                $errors.Add("artifact digest target is inaccessible: $($declaration.Path)")
                continue
            }
            $actualHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actualHash -ne $declaration.Hash) {
                $errors.Add("artifact digest mismatch at the declared path: $($declaration.Path)")
            }
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
$invalidExamples = [Collections.Generic.List[object]]::new()
$null = $invalidExamples.Add((Assert-SyntheticRejection 'missing required field' @($validSynthetic | ForEach-Object -Begin { $n = 0 } -Process { if ($n++ -ne 5) { $_ } }) ''))
$null = $invalidExamples.Add((Assert-SyntheticRejection 'non-taxonomy status' @($validSynthetic[0..7] + @('Passed (test)') + $validSynthetic[9]) ''))
$null = $invalidExamples.Add((Assert-SyntheticRejection 'empty workload marked Passed' @($validSynthetic[0..4] + @('Empty workload: zero tests executed') + $validSynthetic[6..9]) ''))
$null = $invalidExamples.Add((Assert-SyntheticRejection 'documentary status on runtime procedure' @($validSynthetic[0..2] + @(':app:connectedReleaseAndroidTest') + $validSynthetic[4..7] + @('Satisfied') + $validSynthetic[9]) ''))
$null = $invalidExamples.Add((Assert-SyntheticRejection 'stale candidate identity' $validSynthetic 'different-candidate'))

$temporaryDigestArtifact = Join-Path ([IO.Path]::GetTempPath()) "feature-065-evidence-$([guid]::NewGuid().ToString('N')).txt"
try {
    Set-Content -LiteralPath $temporaryDigestArtifact -Value 'synthetic artifact before tampering' -NoNewline -Encoding utf8
    $expectedSyntheticDigest = (Get-FileHash -LiteralPath $temporaryDigestArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $digestArtifactCell = '`' + $temporaryDigestArtifact + '` (SHA-256 `' + $expectedSyntheticDigest + '`)' 
    $validDigestSynthetic = $validSynthetic.Clone()
    $validDigestSynthetic[9] = $digestArtifactCell
    $syntheticCandidate = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    $validDigestSynthetic[2] = $syntheticCandidate
    if (@(Get-RecordErrors $validDigestSynthetic $syntheticCandidate).Count -gt 0) {
        throw 'Synthetic candidate artifact with a matching declared digest was rejected.'
    }
    Set-Content -LiteralPath $temporaryDigestArtifact -Value 'same path, changed synthetic content' -NoNewline -Encoding utf8
    $null = $invalidExamples.Add((Assert-SyntheticRejection 'changed artifact at the same declared path' $validDigestSynthetic $syntheticCandidate))
} finally {
    if (Test-Path -LiteralPath $temporaryDigestArtifact) { Remove-Item -LiteralPath $temporaryDigestArtifact -Force }
}
$unPinnedCandidateSynthetic = $validSynthetic.Clone()
$unPinnedCandidateSynthetic[2] = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
$null = $invalidExamples.Add((Assert-SyntheticRejection 'candidate Passed record without expected digest' $unPinnedCandidateSynthetic 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'))
$exploratorySynthetic = $validSynthetic.Clone()
$exploratorySynthetic[2] = 'dirty exploratory worktree; no frozen candidate'
$null = $invalidExamples.Add((Assert-SyntheticRejection 'exploratory record offered as candidate proof' $exploratorySynthetic 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'))

$allLines = Get-Content -LiteralPath $indexPath
$recordLines = @($allLines | Where-Object { $_ -match '^\|.*\|$' -and $_ -notmatch '^\|\s*-+' })
if ($recordLines.Count -lt 2) { throw 'Evidence index does not contain a header and records.' }
$records = @($recordLines | Select-Object -Skip 1)
$errorsByRow = [Collections.Generic.List[string]]::new()
$statusCounts = @{}
$artifactManifest = [Collections.Generic.List[string]]::new()
$artifactCount = 0
$candidateEvidenceRows = 0
$candidateReleaseClaims = 0

for ($rowIndex = 0; $rowIndex -lt $records.Count; $rowIndex++) {
    $cells = Get-EvidenceCells $records[$rowIndex]
    $rowCandidate = ''
    if ($CandidateRevision -and $cells.Count -eq 10) {
        $rowCandidate = Get-DeclaredCandidateRevision $cells[2] $CandidateRevision
        if ($rowCandidate) {
            $candidateEvidenceRows++
            if ($cells[8] -in @('Passed', 'Satisfied')) { $candidateReleaseClaims++ }
        }
    }
    $rowErrors = @(Get-RecordErrors $cells $rowCandidate)
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
if ($CandidateRevision -and $candidateEvidenceRows -eq 0) {
    $errorsByRow.Add("No evidence records identify the supplied candidate commit $CandidateRevision; historical and exploratory rows remain retained but are not candidate proof.")
}
if ($CandidateRevision -and $candidateReleaseClaims -eq 0) {
    $errorsByRow.Add("No Passed or Satisfied evidence records identify the supplied candidate commit $CandidateRevision.")
}

$date = Get-Date -Format 'yyyy-MM-dd'
$scriptHash = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
$report = [Collections.Generic.List[string]]::new()
$report.Add('# Feature 065 evidence-index validation')
$report.Add('')
$report.Add("- Date: $date")
if ($CandidateRevision) {
    $report.Add("- Candidate commit validated in this repository: ``$CandidateRevision``.")
    $report.Add('- Historical/exploratory records: retained and structurally validated, but excluded from candidate proof unless their revision names the supplied full commit and is not marked exploratory.')
    $report.Add("- Candidate evidence records: $candidateEvidenceRows; Passed/Satisfied candidate claims: $candidateReleaseClaims.")
    $report.Add('- Worktree matching: not asserted; candidate evidence rows are checked against the supplied commit token, but this report does not prove the dirty worktree equals that commit.')
} else {
    $report.Add('- Candidate: dirty exploratory worktree; no frozen product candidate supplied.')
}
$report.Add("- Validator SHA-256: ``$scriptHash``")
$report.Add("- Records parsed: $($records.Count)")
$report.Add("- Artifact file hashes recorded: $artifactCount")
$report.Add("- Real-record errors: $($errorsByRow.Count)")
$report.Add("- Synthetic invalid-record examples rejected: $($invalidExamples.Count)/$($invalidExamples.Count)")
$report.Add('- Candidate commit/declaration self-checks: existing repository HEAD accepted; fabricated full-length commit rejected; explicit candidate declaration accepted; historical mention and exploratory declaration excluded.')
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
    if ($CandidateRevision) {
        $report.Add("Candidate rows satisfy this run's structural, status, date, workload, artifact-access, candidate-identity, and expected-digest checks for the supplied commit token. Historical/exploratory rows are preserved but excluded from candidate proof. Independent review and reviewer access remain separate requirements.")
    } else {
        $report.Add("All parsed rows satisfy this run's structural, status, date, workload, and artifact-access checks. Candidate freshness is not established because the worktree is not frozen.")
    }
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
