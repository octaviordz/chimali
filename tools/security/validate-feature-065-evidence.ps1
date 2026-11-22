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

function Get-CanonicalCommitTree([string]$Revision) {
    if (-not (Get-CanonicalCommitRevision $Revision)) { return $null }
    $resolved = & git -C $RepositoryRoot rev-parse --verify "$($Revision)^{tree}" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    $tree = ($resolved -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($tree) -or $tree -notmatch '^[0-9a-fA-F]{40}$') { return $null }
    return $tree
}

function Get-DeclaredCandidateRevision([string]$RevisionCell, [string]$ExpectedRevision) {
    if ([string]::IsNullOrWhiteSpace($RevisionCell) -or [string]::IsNullOrWhiteSpace($ExpectedRevision)) { return '' }
    if ($RevisionCell -match '(?i)dirty|exploratory|historical|no frozen candidate|pre-rebase') { return '' }
    $match = [regex]::Match(
        $RevisionCell.Trim(),
        '^(?:(?:freeze|frozen)\s+candidate|candidate(?:\s+commit)?)\s+`?(?<candidate>[0-9a-fA-F]{40})`?(?=$|[\s/;(])|^`?(?<candidate>[0-9a-fA-F]{40})`?$'
    )
    if (-not $match.Success) { return '' }
    $declaredRevision = $match.Groups['candidate'].Value
    if ($declaredRevision -ine $ExpectedRevision) { return '' }
    return $ExpectedRevision
}

function Get-DeclaredCandidateTree([string]$RevisionCell) {
    if ([string]::IsNullOrWhiteSpace($RevisionCell) -or $RevisionCell -match '(?i)dirty|exploratory|historical|no frozen candidate|pre-rebase') { return '' }
    $match = [regex]::Match($RevisionCell.Trim(), '(?i)(?:/|;)\s*tree\s+`?(?<tree>[0-9a-f]{40})`?(?=$|[\s/;])')
    if (-not $match.Success) { return '' }
    return $match.Groups['tree'].Value
}

if ($CandidateRevision) {
    $resolvedCandidate = Get-CanonicalCommitRevision $CandidateRevision
    if (-not $resolvedCandidate) {
        throw 'CandidateRevision must identify an existing commit in this repository.'
    }
    $candidateTree = Get-CanonicalCommitTree $resolvedCandidate
    if (-not $candidateTree) { throw 'CandidateRevision did not resolve to a Git tree.' }
}

$repositoryHead = (& git -C $RepositoryRoot rev-parse --verify 'HEAD^{commit}' 2>$null | Out-String).Trim()
if (-not (Get-CanonicalCommitRevision $repositoryHead)) {
    throw 'Validator self-check failed: repository HEAD was not recognized as a full commit.'
}
$candidateDeclaration = Get-DeclaredCandidateRevision "frozen candidate $repositoryHead/tree synthetic" $repositoryHead
if ($candidateDeclaration -ine $repositoryHead) {
    throw 'Validator self-check failed: an explicit matching candidate declaration was not recognized.'
}
$repositoryTree = Get-CanonicalCommitTree $repositoryHead
$treeDeclaration = Get-DeclaredCandidateTree "frozen candidate ``$repositoryHead`` / tree ``$repositoryTree``"
if (-not $repositoryTree -or $treeDeclaration -ine $repositoryTree) {
    throw 'Validator self-check failed: an explicit matching candidate tree declaration was not recognized.'
}
$mismatchedTree = 'b' * 40
$mismatchedTreeDeclaration = Get-DeclaredCandidateTree "frozen candidate ``$repositoryHead`` / tree ``$mismatchedTree``"
if ($mismatchedTreeDeclaration -ieq $repositoryTree) {
    throw 'Validator self-check failed: a mismatched candidate tree declaration was accepted.'
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

function Test-ClipboardControllerMarkers([string]$Path) {
    # Scan the complete artifact with bounded memory. Prefix-only detection lets
    # oversized reports hide their schema after the inspection window.
    $markers = @('"runId"', '"case"', '"targetOutcome"', '"observation"')
    $found = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $reader = [IO.StreamReader]::new($Path, [Text.Encoding]::UTF8, $true)
    try {
        $buffer = [char[]]::new(8192)
        $carry = ''
        while (($count = $reader.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $chunk = $carry + [string]::new($buffer, 0, $count)
            foreach ($marker in $markers) { if ($chunk.Contains($marker)) { $null = $found.Add($marker) } }
            if ($found.Count -eq $markers.Count) { return $true }
            $carry = $chunk.Substring([Math]::Max(0, $chunk.Length - 32))
        }
    } finally { $reader.Dispose() }
    return $false
}

function Get-DeclaredArtifactPurpose([string]$ArtifactCell, [string]$Path) {
    # Candidate evidence must bind one recognized purpose to the exact path.
    # Multiple/conflicting declarations are ambiguous and therefore invalid.
    $purposes = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($segment in ($ArtifactCell -split ';')) {
        $purposeMatch = [regex]::Match($segment, '(?i)\[(clipboard-controller|supporting-evidence)\]')
        if (-not $purposeMatch.Success) { continue }
        $pathMatches = @([regex]::Matches($segment, '`([^`]+)`') | Where-Object { $_.Groups[1].Value.Trim() -ieq $Path })
        if ($pathMatches.Count -eq 1) { $null = $purposes.Add($purposeMatch.Groups[1].Value.ToLowerInvariant()) }
    }
    if ($purposes.Count -ne 1) { return '' }
    return @($purposes)[0]
}

function Get-RecordErrors([string[]]$Cells, [string]$CurrentCandidate, [bool]$ValidateArtifacts = $true) {
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
    if ($ValidateArtifacts -and -not [string]::IsNullOrWhiteSpace($Cells[9])) {
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
        if ($Cells[8] -in @('Passed','Satisfied')) {
            foreach ($clean in (Get-ArtifactCandidates $Cells[9])) {
                try { $artifactPath = Resolve-ArtifactPath $clean } catch { continue }
                if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) { continue }
                $artifactInfo = Get-Item -LiteralPath $artifactPath
                $declaredPurpose = Get-DeclaredArtifactPurpose $Cells[9] $clean
                $declaredController = $declaredPurpose -eq 'clipboard-controller'
                $looksLikeClipboardController = Test-ClipboardControllerMarkers $artifactPath
                if ($CurrentCandidate -and -not $declaredPurpose) {
                    $errors.Add("candidate artifact purpose is missing, ambiguous, or not bound to this exact path: $clean")
                    continue
                }
                if ($CurrentCandidate -and $looksLikeClipboardController -and -not $declaredController) {
                    $errors.Add("candidate clipboard controller content is mislabeled as supporting evidence or has unknown purpose: $clean")
                    continue
                }
                $reader = [IO.StreamReader]::new($artifactPath, [Text.Encoding]::UTF8, $true)
                try {
                    $prefixBuffer = [char[]]::new(65536)
                    $prefixLength = $reader.Read($prefixBuffer, 0, $prefixBuffer.Length)
                    $artifactPrefix = [string]::new($prefixBuffer, 0, $prefixLength)
                    if ($artifactInfo.Length -le 5242880) {
                        $remaining = $reader.ReadToEnd()
                        $artifactContent = $artifactPrefix + $remaining
                    }
                } finally { $reader.Dispose() }
                if ($artifactInfo.Length -gt 5242880) {
                    if ($looksLikeClipboardController -or $declaredController) {
                        $errors.Add("oversized clipboard controller artifact cannot support an accepted device-result record: $clean")
                    }
                    continue
                }
                try { $clipboardRecord = $artifactContent | ConvertFrom-Json -ErrorAction Stop }
                catch {
                    if ($looksLikeClipboardController -or $declaredController) {
                        $errors.Add("malformed clipboard controller artifact has unknown provenance and cannot support an accepted device-result record: $clean")
                    }
                    continue
                }
                $isClipboardControllerRecord = $clipboardRecord.PSObject.Properties.Name -contains 'runId' -and
                    $clipboardRecord.PSObject.Properties.Name -contains 'case' -and
                    $clipboardRecord.PSObject.Properties.Name -contains 'targetOutcome' -and
                    $clipboardRecord.PSObject.Properties.Name -contains 'observation'
                if ($declaredController -and -not $isClipboardControllerRecord) {
                    $errors.Add("declared clipboard controller artifact is missing required schema fields and has unknown provenance: $clean")
                    continue
                }
                if ($isClipboardControllerRecord -and $clipboardRecord.executionMode -ne 'physical-device') {
                    $errors.Add("clipboard controller artifact is simulated or has unknown provenance and cannot support an accepted device-result record: $clean")
                }
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
    $digestArtifactCell = '[supporting-evidence] `' + $temporaryDigestArtifact + '` (SHA-256 `' + $expectedSyntheticDigest + '`)'
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

$simulatedClipboardArtifact = Join-Path ([IO.Path]::GetTempPath()) "renamed-controller-$([guid]::NewGuid().ToString('N')).txt"
$oversizedClipboardArtifact = $null
$malformedClipboardArtifact = $null
$lateMarkerArtifact = $null
$validLargeArtifact = $null
try {
    Set-Content -LiteralPath $simulatedClipboardArtifact -Value '{"executionMode":"simulated","runId":"synthetic","case":"manual-clear","targetOutcome":{},"observation":{}}' -NoNewline -Encoding utf8
    $physicalSubstitution = @('FR-TEST','Synthetic data','candidate-abc','Controller artifact','Test fixture','Synthetic result','2026-11-15','Simulation','Passed',$simulatedClipboardArtifact)
    $null = $invalidExamples.Add((Assert-SyntheticRejection 'renamed simulated controller content cannot pass without device claim wording' $physicalSubstitution ''))
    Set-Content -LiteralPath $simulatedClipboardArtifact -Value '{"runId":"legacy","case":"manual-clear","targetOutcome":{},"observation":{}}' -NoNewline -Encoding utf8
    $null = $invalidExamples.Add((Assert-SyntheticRejection 'legacy controller content with unknown mode cannot pass' $physicalSubstitution ''))
    $syntheticCandidate = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    $oversizedClipboardArtifact = Join-Path ([IO.Path]::GetTempPath()) "oversized-controller-$([guid]::NewGuid().ToString('N')).bin"
    $lateOversizedJson = '{"padding":"' + ('x' * (5MB + 128)) + '","executionMode":"simulated","runId":"large","case":"manual-clear","targetOutcome":{},"observation":{}}'
    [IO.File]::WriteAllText($oversizedClipboardArtifact, $lateOversizedJson, [Text.Encoding]::UTF8)
    $oversizedRecord = $physicalSubstitution.Clone()
    $oversizedRecord[2] = $syntheticCandidate
    $oversizedDigest = (Get-FileHash -LiteralPath $oversizedClipboardArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $oversizedRecord[9] = '[clipboard-controller] `' + $oversizedClipboardArtifact + '` (SHA-256 `' + $oversizedDigest + '`)'
    $oversizedErrors = @(Get-RecordErrors $oversizedRecord $syntheticCandidate)
    if (-not ($oversizedErrors -match 'oversized clipboard controller artifact')) {
        throw 'Candidate validation did not specifically reject an oversized simulated controller with markers beyond 64 KiB.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate oversized simulation with late schema markers'; Rejected = $true; Reason = ($oversizedErrors -join '; ') })
    $unlabeledOversizedRecord = $physicalSubstitution.Clone()
    $unlabeledOversizedRecord[2] = $syntheticCandidate
    $unlabeledOversizedRecord[9] = '`' + $oversizedClipboardArtifact + '` (SHA-256 `' + $oversizedDigest + '`)'
    $unlabeledOversizedErrors = @(Get-RecordErrors $unlabeledOversizedRecord $syntheticCandidate)
    if (-not ($unlabeledOversizedErrors -match 'candidate artifact purpose is missing')) {
        throw 'Unlabeled oversized candidate controller artifact was not rejected for unknown purpose.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate unlabeled oversized controller'; Rejected = $true; Reason = ($unlabeledOversizedErrors -join '; ') })
    $malformedClipboardArtifact = Join-Path ([IO.Path]::GetTempPath()) "malformed-controller-$([guid]::NewGuid().ToString('N')).dat"
    Set-Content -LiteralPath $malformedClipboardArtifact -Value '{"runId":"broken","case":"manual-clear","targetOutcome":{},"observation":{},' -NoNewline -Encoding utf8
    $malformedRecord = $physicalSubstitution.Clone()
    $malformedRecord[2] = $syntheticCandidate
    $malformedDigest = (Get-FileHash -LiteralPath $malformedClipboardArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $malformedRecord[9] = '[clipboard-controller] `' + $malformedClipboardArtifact + '` (SHA-256 `' + $malformedDigest + '`)'
    $malformedErrors = @(Get-RecordErrors $malformedRecord $syntheticCandidate)
    if (-not ($malformedErrors -match 'malformed clipboard controller artifact has unknown provenance')) {
        throw 'Candidate validation did not specifically reject malformed declared controller evidence.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate malformed declared controller'; Rejected = $true; Reason = ($malformedErrors -join '; ') })
    $missingSchemaArtifact = Join-Path ([IO.Path]::GetTempPath()) "declared-controller-no-schema-$([guid]::NewGuid().ToString('N')).json"
    Set-Content -LiteralPath $missingSchemaArtifact -Value '{"ordinary":"json"}' -NoNewline -Encoding utf8
    $missingSchemaRecord = $physicalSubstitution.Clone()
    $missingSchemaRecord[2] = $syntheticCandidate
    $missingSchemaDigest = (Get-FileHash -LiteralPath $missingSchemaArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $missingSchemaRecord[9] = '[clipboard-controller] `' + $missingSchemaArtifact + '` (SHA-256 `' + $missingSchemaDigest + '`)'
    $missingSchemaErrors = @(Get-RecordErrors $missingSchemaRecord $syntheticCandidate)
    if (-not ($missingSchemaErrors -match 'declared clipboard controller artifact is missing required schema fields')) {
        throw 'Candidate validation accepted declared clipboard-controller provenance without the controller schema.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate declared controller without schema'; Rejected = $true; Reason = ($missingSchemaErrors -join '; ') })
    $malformedNoMarkersArtifact = Join-Path ([IO.Path]::GetTempPath()) "declared-malformed-no-markers-$([guid]::NewGuid().ToString('N')).json"
    Set-Content -LiteralPath $malformedNoMarkersArtifact -Value '{"ordinary":"truncated",' -NoNewline -Encoding utf8
    $malformedNoMarkersRecord = $physicalSubstitution.Clone()
    $malformedNoMarkersRecord[2] = $syntheticCandidate
    $malformedNoMarkersDigest = (Get-FileHash -LiteralPath $malformedNoMarkersArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $malformedNoMarkersRecord[9] = '[clipboard-controller] `' + $malformedNoMarkersArtifact + '` (SHA-256 `' + $malformedNoMarkersDigest + '`)'
    $malformedNoMarkersErrors = @(Get-RecordErrors $malformedNoMarkersRecord $syntheticCandidate)
    if (-not ($malformedNoMarkersErrors -match 'malformed clipboard controller artifact has unknown provenance')) {
        throw 'Malformed declared controller artifact without schema markers was not rejected.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate malformed declared controller without markers'; Rejected = $true; Reason = ($malformedNoMarkersErrors -join '; ') })
    $lateMarkerArtifact = Join-Path ([IO.Path]::GetTempPath()) "late-controller-$([guid]::NewGuid().ToString('N')).json"
    $lateJson = '{"padding":"' + ('x' * 70000) + '","executionMode":"simulated","runId":"late","case":"manual-clear","targetOutcome":{},"observation":{}}'
    Set-Content -LiteralPath $lateMarkerArtifact -Value $lateJson -NoNewline -Encoding utf8
    $lateRecord = $physicalSubstitution.Clone()
    $lateRecord[2] = $syntheticCandidate
    $lateDigest = (Get-FileHash -LiteralPath $lateMarkerArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $lateRecord[9] = '`' + $lateMarkerArtifact + '` (SHA-256 `' + $lateDigest + '`)'
    $lateErrors = @(Get-RecordErrors $lateRecord $syntheticCandidate)
    if (-not ($lateErrors -match 'candidate artifact purpose is missing')) {
        throw 'Unlabeled candidate controller-shaped content was not rejected for unknown purpose.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate unlabeled controller fields after 64 KiB'; Rejected = $true; Reason = ($lateErrors -join '; ') })
    $validLargeArtifact = Join-Path ([IO.Path]::GetTempPath()) "large-noncontroller-$([guid]::NewGuid().ToString('N')).json"
    Set-Content -LiteralPath $validLargeArtifact -Value ('{"ordinaryArtifact":"' + ('x' * 70000) + '"}') -NoNewline -Encoding utf8
    $largeRecord = $physicalSubstitution.Clone()
    $largeRecord[2] = $syntheticCandidate
    $largeDigest = (Get-FileHash -LiteralPath $validLargeArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $largeRecord[9] = '[supporting-evidence] `' + $validLargeArtifact + '` (SHA-256 `' + $largeDigest + '`)'
    if (@(Get-RecordErrors $largeRecord $syntheticCandidate).Count -gt 0) { throw 'Valid unrelated large supporting evidence was rejected in candidate validation.' }

    $unlabeledMalformedArtifact = Join-Path ([IO.Path]::GetTempPath()) "unlabeled-malformed-controller-$([guid]::NewGuid().ToString('N')).dat"
    Set-Content -LiteralPath $unlabeledMalformedArtifact -Value '{"ordinary":"truncated",' -NoNewline -Encoding utf8
    $unlabeledMalformedRecord = $physicalSubstitution.Clone()
    $unlabeledMalformedRecord[2] = $syntheticCandidate
    $unlabeledMalformedDigest = (Get-FileHash -LiteralPath $unlabeledMalformedArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $unlabeledMalformedRecord[9] = '`' + $unlabeledMalformedArtifact + '` (SHA-256 `' + $unlabeledMalformedDigest + '`)'
    $unlabeledMalformedErrors = @(Get-RecordErrors $unlabeledMalformedRecord $syntheticCandidate)
    if (-not ($unlabeledMalformedErrors -match 'candidate artifact purpose is missing')) {
        throw 'Unlabeled malformed candidate artifact was not rejected for unknown purpose.'
    }
    $null = $invalidExamples.Add([pscustomobject]@{ Example = 'candidate unlabeled malformed report'; Rejected = $true; Reason = ($unlabeledMalformedErrors -join '; ') })

    $physicalArtifact = Join-Path ([IO.Path]::GetTempPath()) "physical-controller-$([guid]::NewGuid().ToString('N')).json"
    Set-Content -LiteralPath $physicalArtifact -Value '{"executionMode":"physical-device","runId":"physical","case":"manual-clear","targetOutcome":{},"observation":{}}' -NoNewline -Encoding utf8
    $physicalRecord = $physicalSubstitution.Clone()
    $physicalRecord[2] = $syntheticCandidate
    $physicalDigest = (Get-FileHash -LiteralPath $physicalArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $physicalRecord[9] = '[clipboard-controller] `' + $physicalArtifact + '` (SHA-256 `' + $physicalDigest + '`)'
    if (@(Get-RecordErrors $physicalRecord $syntheticCandidate).Count -gt 0) { throw 'Eligible physical controller evidence was rejected by candidate validation.' }
} finally {
    Remove-Item -LiteralPath $simulatedClipboardArtifact -Force -ErrorAction SilentlyContinue
    if ($oversizedClipboardArtifact) { Remove-Item -LiteralPath $oversizedClipboardArtifact -Force -ErrorAction SilentlyContinue }
    if ($malformedClipboardArtifact) { Remove-Item -LiteralPath $malformedClipboardArtifact -Force -ErrorAction SilentlyContinue }
    if ($missingSchemaArtifact) { Remove-Item -LiteralPath $missingSchemaArtifact -Force -ErrorAction SilentlyContinue }
    if ($malformedNoMarkersArtifact) { Remove-Item -LiteralPath $malformedNoMarkersArtifact -Force -ErrorAction SilentlyContinue }
    if ($lateMarkerArtifact) { Remove-Item -LiteralPath $lateMarkerArtifact -Force -ErrorAction SilentlyContinue }
    if ($unlabeledMalformedArtifact) { Remove-Item -LiteralPath $unlabeledMalformedArtifact -Force -ErrorAction SilentlyContinue }
    if ($validLargeArtifact) { Remove-Item -LiteralPath $validLargeArtifact -Force -ErrorAction SilentlyContinue }
    if ($physicalArtifact) { Remove-Item -LiteralPath $physicalArtifact -Force -ErrorAction SilentlyContinue }
}

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
    # Candidate validation checks artifact availability and digests only for rows that
    # explicitly name the supplied frozen candidate. Historical evidence remains
    # structurally parsed without requiring old, transient build outputs to survive.
    $rowErrors = @(Get-RecordErrors $cells $rowCandidate (-not $CandidateRevision -or [bool]$rowCandidate))
    if ($rowCandidate) {
        $declaredTree = Get-DeclaredCandidateTree $cells[2]
        if (-not $candidateTree -or $declaredTree -ine $candidateTree) {
            $rowErrors += 'candidate revision cell does not declare the exact Git tree for the supplied commit'
        }
    }
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
    $report.Add("- Candidate tree verified: ``$candidateTree``; evidence rows must declare this exact tree. Worktree equality is not asserted; this does not prove the dirty checkout equals the candidate commit.")
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
