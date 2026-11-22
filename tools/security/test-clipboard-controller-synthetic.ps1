[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runner = Join-Path $PSScriptRoot 'run-clipboard-observation.ps1'
. (Join-Path $PSScriptRoot 'clipboard-observation-classification.ps1')
if ((Get-ClipboardExecutionMode -SyntheticInput $true) -ne 'simulated' -or
    (Get-ClipboardExecutionMode -SyntheticInput $false) -ne 'physical-device') { throw 'Clipboard execution mode classification failed.' }
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('clipboard-controller-' + [guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $testRoot -Force)
try {
    $cases = @(
        @{ Name='repeat-rejected'; Case='repeat-copy'; Outcome='ClearRejected'; Clip='marker-match'; Failure=$null; Cleanup=$true; Pred='Superseded'; Expected='Observed' },
        @{ Name='repeat-interrupted'; Case='repeat-copy'; Outcome='Interrupted'; Clip='marker-match'; Failure=$null; Cleanup=$true; Pred='Superseded'; Expected='Observed' },
        @{ Name='clear-absent'; Case='scheduled-attempt'; Outcome='ClearRequestSubmitted'; Clip='absent'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Passed' },
        @{ Name='clear-marker-retained'; Case='repeat-copy'; Outcome='ClearRequestSubmitted'; Clip='marker-match'; Failure=$null; Cleanup=$true; Pred='Superseded'; Expected='Failed' },
        @{ Name='predecessor-mismatch'; Case='repeat-copy'; Outcome='ClearRequestSubmitted'; Clip='absent'; Failure=$null; Cleanup=$true; Pred='ClearRequestSubmitted'; Expected='Failed' },
        @{ Name='predecessor-missing'; Case='repeat-copy'; Outcome='ClearRequestSubmitted'; Clip='absent'; Failure=$null; Cleanup=$true; Pred=$null; ReadFailure='PrivateReceiptUnavailable'; Expected='Unverified' },
        @{ Name='predecessor-unreadable'; Case='repeat-copy'; Outcome='ClearRequestSubmitted'; Clip='absent'; Failure=$null; Cleanup=$true; Pred=$null; ReadFailure='PrivateReceiptUnreadable'; Expected='Unverified' },
        @{ Name='observer-unavailable'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip=$null; Failure='ObserverResultUnavailable'; Cleanup=$true; Pred=$null; Expected='Unverified' },
        @{ Name='process-isolation'; Case='scheduled-attempt'; Outcome='ClearRequestSubmitted'; Clip='absent'; Failure='ProcessIsolationFailed'; Cleanup=$true; Pred=$null; Expected='Unverified' },
        @{ Name='cleanup-failure'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; Failure=$null; Cleanup=$false; Pred=$null; Expected='Unverified' }
        ,@{ Name='marker-cleanup-submitted'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupOutcome='clear-request-submitted'; CleanupClip='absent'; CleanupStatus='verified-absent-after-clear-request'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Observed' }
        ,@{ Name='marker-cleanup-unavailable'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupOutcome='skipped-ownership-unavailable'; CleanupClip='inaccessible'; CleanupStatus='unverified'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Observed' }
        ,@{ Name='marker-cleanup-rejected'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupOutcome='skipped-ownership-unavailable'; CleanupClip='marker-match'; CleanupStatus='marker-still-present'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Observed' }
        ,@{ Name='marker-cleanup-replaced'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupOutcome='skipped-replaced-or-absent'; CleanupClip='replaced'; CleanupStatus='marker-replaced-after-safe-skip'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Observed' }
        ,@{ Name='marker-cleanup-unknown-outcome'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupOutcome='future-outcome'; CleanupClip='absent'; CleanupStatus='unverified'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Observed' }
        ,@{ Name='marker-cleanup-inconsistent-outcome'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupOutcome='skipped-replaced-or-absent'; CleanupClip='marker-match'; CleanupStatus='unverified'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Observed' }
        ,@{ Name='marker-cleanup-observer-failure'; Case='scheduled-attempt'; Outcome='ClearRejected'; Clip='marker-match'; TestCleanupFailureCode='ObserverResultUnavailable'; CleanupStatus='unverified'; Failure=$null; Cleanup=$true; Pred=$null; Expected='Unverified' }
    )
    foreach ($case in $cases) {
        $gen = if ($case.Case -eq 'repeat-copy') { 11 } else { 10 }
        $ready = [ordered]@{ runId=$case.Name; generation=10; scheduledElapsedMs=30000; publication='MetadataConfirmed'; targetWindowFocused=$true; copyCallStartedElapsedMs=1; publicationElapsedMs=0; copyCallReturnedElapsedMs=2 }
        $ready.publicationElapsedMs=1; $ready.scheduledElapsedMs=30001
        $readyRepeat = if ($case.Case -eq 'repeat-copy') { [ordered]@{ runId=$case.Name; generation=11; scheduledElapsedMs=30001; publication='MetadataConfirmed'; copyCallStartedElapsedMs=1; publicationElapsedMs=1; copyCallReturnedElapsedMs=2 } } else { $null }
        $receipt = [ordered]@{ runId=$case.Name; generation=$gen; scheduledElapsedMs=30001; callbackElapsedMs=30002; ownershipCheckElapsedMs=30003; clearAttemptElapsedMs=if ($case.Outcome -in @('ClearRequestSubmitted','ClearRejected')) {30004} else {$null}; terminalStatusElapsedMs=30005; outcome=$case.Outcome; cleanupStatus='terminal'; cleanupDetail='synthetic' }
        if ($case.Name -in @('predecessor-missing','predecessor-unreadable')) { $receipt.callbackElapsedMs = 2 }
        $predecessor = if ($case.Pred) { [ordered]@{ runId=$case.Name; generation=10; scheduledElapsedMs=30001; outcome=$case.Pred } } else { $null }
        $observer = if ($case.Clip) { [ordered]@{ runId=$case.Name; classification=$case.Clip; observedElapsedMs=31000; capabilityControl='passed'; cleanupOutcome='not-requested'; observerPid=4231 } } else { $null }
        $cleanupRecord = if ($case.TestCleanupOutcome) { [ordered]@{runId=$case.Name; classification=$case.CleanupClip; cleanupOutcome=$case.TestCleanupOutcome; observerPid=4231} } else { [ordered]@{runId=$case.Name; classification=$case.Clip; cleanupOutcome='skipped-ownership-unavailable'; observerPid=4231} }
        $input = [ordered]@{ RunId=$case.Name; Case=$case.Case; TargetReady=$ready; TargetReadyRepeat=$readyRepeat; TargetReceipt=$receipt; PredecessorReceipt=$predecessor; PredecessorReadFailure=$case.ReadFailure; ObserverRecord=$observer; TestCleanupRecord=$cleanupRecord; TestCleanupFailureCode=$case.TestCleanupFailureCode; InjectedFailureCode=$case.Failure; AttemptObserved=$true; CleanupSucceeded=$case.Cleanup }
        $inputPath = Join-Path $testRoot ($case.Name + '.input.json')
        $outputDir = Join-Path $testRoot $case.Name
        [IO.File]::WriteAllText($inputPath,($input|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
        $resultText = & pwsh -NoProfile -File $runner -Serial synthetic -TargetPackage com.chimali.synthetic -TestPackage com.chimali.syntheticobserver -ObserverActivity com.chimali.security.ClipboardObserverActivity -RunId $case.Name -Case $case.Case -OutputDirectory $outputDir -SyntheticInputPath $inputPath
        if ($LASTEXITCODE -ne 0) { throw "Controller runner failed for $($case.Name): $resultText" }
        $result = $resultText | ConvertFrom-Json
        $record = Get-Content -LiteralPath $result.artifact -Raw | ConvertFrom-Json
        $expectedMode = 'simulated'
        if ($record.executionMode -ne $expectedMode) { throw "Execution mode mismatch for $($case.Name): $($record.executionMode)" }
        if ($case.CleanupStatus -and $record.syntheticMarkerCleanupStatus -ne $case.CleanupStatus) { throw "Synthetic marker cleanup mismatch for $($case.Name): $($record.syntheticMarkerCleanupStatus)" }
        $expectedStatus = switch ($case.Name) {
            'repeat-rejected' { 'Observed (automatic clear rejected; clipboard independently observed as marker-match)' }
            'repeat-interrupted' { 'Observed (scheduled cleanup interrupted; clipboard independently observed as marker-match)' }
            'clear-absent' { 'Passed (scheduled cleanup request observed; clipboard independently observed as absent)' }
            'clear-marker-retained' { 'Failed (scoped clipboard behavior assertion)' }
            'predecessor-mismatch' { 'Failed (scoped clipboard behavior assertion)' }
            'cleanup-failure' { 'Unverified (synthetic cleanup failed)' }
            'marker-cleanup-observer-failure' { 'Unverified (controller or observation failure)'; break }
            { $_ -like 'marker-cleanup-*' } { 'Observed (automatic clear rejected; clipboard independently observed as marker-match)'; break }
            default { 'Unverified (controller or observation failure)' }
        }
        $expectedFailure = switch ($case.Name) {
            'clear-marker-retained' { 'ScopedClipboardBehaviorFailure' }
            'predecessor-mismatch' { 'ScopedClipboardBehaviorFailure' }
            'predecessor-missing' { 'PredecessorReceiptUnavailable' }
            'predecessor-unreadable' { 'PredecessorReceiptUnavailable' }
            'cleanup-failure' { 'SyntheticCleanupFailed' }
            'marker-cleanup-observer-failure' { 'ObserverResultUnavailable' }
            default { $case.Failure }
        }
        $expectedBehavior = $case.Name -in @('clear-marker-retained','predecessor-mismatch')
        $expectedTiming = if ($case.Name -in @('predecessor-mismatch','predecessor-missing','predecessor-unreadable')) { 'unavailable' } else { 'Verified' }
        if ($record.status -ne $expectedStatus -or $record.failureCode -ne $expectedFailure -or
            $record.behaviorFailure -ne $expectedBehavior -or $record.timingVerification -ne $expectedTiming) {
            throw "Disposition mismatch for $($case.Name): $($record | ConvertTo-Json -Compress -Depth 8)"
        }
        if (($record.targetOutcome | ConvertTo-Json -Compress -Depth 8) -ne ($receipt | ConvertTo-Json -Compress -Depth 8)) { throw "Candidate receipt changed for $($case.Name)" }
        if (($record.supersededOutcome | ConvertTo-Json -Compress -Depth 8) -ne ($predecessor | ConvertTo-Json -Compress -Depth 8)) { throw "Predecessor receipt changed for $($case.Name)" }
        if ($case.Name -notin @('predecessor-mismatch','predecessor-missing','predecessor-unreadable','observer-unavailable') -and
            ($record.observation | ConvertTo-Json -Compress -Depth 8) -ne ($observer | ConvertTo-Json -Compress -Depth 8)) { throw "Observer record changed for $($case.Name)" }
        $early = $case.Name -in @('predecessor-mismatch','predecessor-missing','predecessor-unreadable','observer-unavailable')
        if ($record.status -notlike "$($case.Expected)*" -or $record.targetOutcome.generation -ne $gen -or $record.targetOutcome.runId -ne $case.Name -or ($record.observation.classification -ne $(if ($early) {$null} else {$case.Clip})) -or $record.cleanupSucceeded -ne $case.Cleanup) { throw "Bad emitted JSON for $($case.Name): $($record.status)" }
        if ($case.Failure -and $record.failureCode -ne $case.Failure) { throw "Failure code lost for $($case.Name)" }
        if ($case.Name -eq 'predecessor-mismatch' -and ($record.failureCode -ne 'ScopedClipboardBehaviorFailure' -or $record.behaviorFailure -ne $true -or $record.timingVerification -ne 'unavailable')) { throw 'Predecessor mismatch disposition lost.' }
        if ($case.Name -in @('predecessor-missing','predecessor-unreadable') -and ($record.failureCode -ne 'PredecessorReceiptUnavailable' -or $record.behaviorFailure -ne $false -or $record.timingVerification -ne 'unavailable')) { throw 'Unavailable predecessor disposition lost.' }
        "PASS $($case.Name): $($record.status)"
    }
} finally { Remove-Item -LiteralPath $testRoot -Recurse -Force }
