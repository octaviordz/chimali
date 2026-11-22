function Get-ClipboardExecutionMode {
    param([bool]$SyntheticInput)
    if ($SyntheticInput) { return 'simulated' }
    return 'physical-device'
}

function Get-ClipboardObservationCaseStatus {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Case,
        [AllowNull()][string]$Outcome,
        [AllowNull()][string]$Observation,
        [bool]$AttemptObserved
    )

    if ($Case -eq 'clipboard-replaced') {
        if (-not $AttemptObserved) { return 'Unverified (clipboard replacement; no cleanup outcome observed)' }
        if ($Outcome -ne 'SkippedReplaced' -or $Observation -ne 'replaced') {
            return 'Failed (replacement ownership skip was not independently confirmed)'
        }
        return 'Passed (clipboard replacement detected; automatic cleanup skipped)'
    }

    if ($Outcome -eq 'ClearRejected') {
        return "Observed (automatic clear rejected; clipboard independently observed as $Observation)"
    }
    if ($Outcome -eq 'Interrupted') {
        return "Observed (scheduled cleanup interrupted; clipboard independently observed as $Observation)"
    }

    if ($Case -eq 'scheduled-attempt') {
        if (-not $AttemptObserved) { return 'Unverified (scheduled cleanup outcome not observed)' }
        if ($Outcome -ne 'ClearRequestSubmitted') { return "Failed (unexpected scheduled outcome: $Outcome)" }
        return "Passed (scheduled cleanup request observed; clipboard independently observed as $Observation)"
    }

    if ($Case -eq 'app-switch') {
        if (-not $AttemptObserved) {
            return 'Unverified (background interruption; no terminal cleanup outcome was observed)'
        }
        if ($Outcome -eq 'SkippedOwnershipUnavailable') { return 'Passed (background ownership unavailable; automatic cleanup safely skipped)' }
        if ($Outcome -eq 'SkippedReplaced' -and $Observation -eq 'replaced') { return 'Passed (background clipboard replacement detected; automatic cleanup skipped)' }
        if ($Outcome -eq 'ClearRequestSubmitted') { return "Passed (background app-switch cleanup request observed; clipboard independently observed as $Observation)" }
        return "Failed (unexpected app-switch cleanup outcome: $Outcome)"
    }

    if ($Case -eq 'locked-sleep') {
        if (-not $AttemptObserved) { return 'Unverified (screen-off; no cleanup outcome observed)' }
        if ($Outcome -eq 'ClearRequestSubmitted') { return "Passed (locked-sleep scheduled cleanup request observed; clipboard independently observed as $Observation)" }
        if ($Outcome -in @('SkippedOwnershipUnavailable', 'SkippedReplaced')) { return "Passed (locked-sleep cleanup safely skipped with outcome $Outcome)" }
        return "Failed (unexpected locked-sleep cleanup outcome: $Outcome)"
    }

    if ($Case -eq 'repeat-copy') {
        if (-not $AttemptObserved) { return 'Unverified (repeated-copy cleanup outcome not observed)' }
        if ($Outcome -eq 'ClearRequestSubmitted' -and $Observation -eq 'absent') { return 'Passed (repeated-copy cleanup request and independent absence observed)' }
        if ($Outcome -eq 'ClearRequestSubmitted') { return "Failed (repeated-copy clear submitted but clipboard independently observed as $Observation)" }
        if ($Outcome -in @('ClearRejected', 'Interrupted')) { return "Observed (repeated-copy cleanup $Outcome; clipboard independently observed as $Observation)" }
        return "Failed (unexpected repeated-copy cleanup outcome: $Outcome)"
    }

    return "Unverified (no classification rule for case $Case)"
}

function Test-ClipboardReceiptIdentity {
    param(
        [AllowNull()]$Receipt,
        [string]$RunId,
        [long]$Generation,
        [long]$ScheduledElapsedMs
    )
    return $null -ne $Receipt -and $Receipt.runId -eq $RunId -and
        $Receipt.generation -eq $Generation -and $Receipt.scheduledElapsedMs -eq $ScheduledElapsedMs
}

function Test-ClipboardSupersededReceiptIdentity {
    [CmdletBinding()]
    param(
        [AllowNull()]$Receipt,
        [Parameter(Mandatory)][string]$RunId,
        [Parameter(Mandatory)][long]$Generation,
        [Parameter(Mandatory)][long]$ScheduledElapsedMs
    )
    return (Test-ClipboardReceiptIdentity -Receipt $Receipt -RunId $RunId `
            -Generation $Generation -ScheduledElapsedMs $ScheduledElapsedMs) -and
        $Receipt.outcome -eq 'Superseded'
}

function Get-ClipboardPredecessorReceiptDisposition {
    param(
        [AllowNull()]$Receipt,
        [Parameter(Mandatory)][string]$RunId,
        [Parameter(Mandatory)][long]$Generation,
        [Parameter(Mandatory)][long]$ScheduledElapsedMs,
        [AllowNull()][string]$ReadFailure
    )
    if ($ReadFailure -or $null -eq $Receipt) {
        return [ordered]@{ available = $false; matches = $false; failureCode = 'PredecessorReceiptUnavailable'; behaviorFailure = $false }
    }
    $matches = Test-ClipboardSupersededReceiptIdentity -Receipt $Receipt -RunId $RunId `
        -Generation $Generation -ScheduledElapsedMs $ScheduledElapsedMs
    return [ordered]@{
        available = $true
        matches = $matches
        failureCode = if ($matches) { $null } else { 'PredecessorReceiptMismatch' }
        behaviorFailure = -not $matches
    }
}

function Invoke-ClipboardControllerAudit {
    param(
        [Parameter(Mandatory)][string]$Case,
        [AllowNull()]$TargetReady,
        [AllowNull()]$TargetReadyRepeat,
        [AllowNull()]$TargetReceipt,
        [AllowNull()]$PredecessorReceipt,
        [AllowNull()][string]$PredecessorReadFailure,
        [AllowNull()]$ObserverRecord,
        [AllowNull()][string]$FailureCode,
        [bool]$AttemptObserved
    )
    $candidateReady = if ($Case -eq 'repeat-copy') { $TargetReadyRepeat } else { $TargetReady }
    $candidateValid = $null -ne $candidateReady -and
        (Test-ClipboardReceiptIdentity -Receipt $TargetReceipt -RunId $candidateReady.runId `
            -Generation ([long]$candidateReady.generation) -ScheduledElapsedMs ([long]$candidateReady.scheduledElapsedMs))
    $predecessor = [ordered]@{ available=$true; matches=$true; failureCode=$null; behaviorFailure=$false }
    if ($Case -eq 'repeat-copy') {
        $predecessor = Get-ClipboardPredecessorReceiptDisposition -Receipt $PredecessorReceipt `
            -RunId $TargetReady.runId -Generation ([long]$TargetReady.generation) `
            -ScheduledElapsedMs ([long]$TargetReady.scheduledElapsedMs) -ReadFailure $PredecessorReadFailure
    }
    $timing = 'unverified'
    $audit = $null
    if ($candidateValid -and $predecessor.available -and $predecessor.matches -and $null -ne $TargetReceipt) {
        $timing = Get-ClipboardTimingDisposition -ScheduledElapsedMs ([long]$candidateReady.scheduledElapsedMs) `
            -CallbackElapsedMs $TargetReceipt.callbackElapsedMs -OwnershipCheckElapsedMs $TargetReceipt.ownershipCheckElapsedMs `
            -ClearAttemptElapsedMs $TargetReceipt.clearAttemptElapsedMs -Outcome $TargetReceipt.outcome
        if ($timing -eq 'Unavailable') { $timing = 'unverified' }
        $audit = New-ClipboardObservationAuditDisposition -Case $Case -TargetReceipt $TargetReceipt `
            -Outcome $TargetReceipt.outcome -ObserverRecord $ObserverRecord `
            -Observation $(if ($ObserverRecord) {$ObserverRecord.classification} else {$null}) `
            -AttemptObserved $AttemptObserved -ScheduledElapsedMs ([long]$candidateReady.scheduledElapsedMs) `
            -CallbackElapsedMs $TargetReceipt.callbackElapsedMs -OwnershipCheckElapsedMs $TargetReceipt.ownershipCheckElapsedMs `
            -ClearAttemptElapsedMs $TargetReceipt.clearAttemptElapsedMs -ObservationFailure $FailureCode
        $timing = $audit.timingVerification
    }
    $behaviorFailure = [bool](-not $candidateValid -or $predecessor.behaviorFailure -or ($audit -and $audit.behaviorFailure))
    $failure = if (-not $candidateValid) {'TargetReceiptIdentityMismatch'} elseif (-not $predecessor.available) {$predecessor.failureCode} elseif (-not $predecessor.matches) {$predecessor.failureCode} elseif ($audit -and $audit.behaviorFailure) {'ScopedClipboardBehaviorFailure'} else {$FailureCode}
    $status = if (-not $candidateValid) {'Failed (scheduled cleanup receipt identity mismatch)'}
        elseif (-not $predecessor.available) {'Unverified (predecessor receipt unavailable)'}
        elseif (-not $predecessor.matches) {'Failed (repeat-copy predecessor receipt identity mismatch)'}
        elseif ($FailureCode -and -not $behaviorFailure) {'Unverified (controller or observation failure)'}
        elseif ($audit) {$audit.status} else {'Unverified (controller outcome unavailable)'}
    return [ordered]@{
        targetOutcome=$TargetReceipt; supersededOutcome=$PredecessorReceipt
        observation=$ObserverRecord; observationFailure=if ($FailureCode -like 'Observer*') {$FailureCode} else {$null}
        timingVerification=$timing; failureCode=$failure; behaviorFailure=$behaviorFailure; status=$status
        clearRejectedAttemptObserved=[bool]($AttemptObserved -and $TargetReceipt.outcome -eq 'ClearRejected')
        interruptedAttemptObserved=[bool]($AttemptObserved -and $TargetReceipt.outcome -eq 'Interrupted')
    }
}

function Complete-ClipboardObservationRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Record,
        [Parameter(Mandatory)][bool]$CleanupSucceeded
    )

    $Record.cleanupSucceeded = $CleanupSucceeded
    $status = [string]$Record.status
    $behaviorFailure = [bool]$Record.behaviorFailure
    if ($status.StartsWith('Failed', [StringComparison]::Ordinal)) { $behaviorFailure = $true }
    if ($behaviorFailure) {
        if (-not $status.StartsWith('Failed', [StringComparison]::Ordinal)) {
            $Record.status = 'Failed (scoped clipboard behavior assertion)'
        }
        if (-not $Record.failureCode) { $Record.failureCode = 'ScopedClipboardBehaviorFailure' }
        $Record.behaviorFailure = $true
    } elseif (-not $CleanupSucceeded) {
        $Record.status = 'Unverified (synthetic cleanup failed)'
        if (-not $Record.failureCode) { $Record.failureCode = 'SyntheticCleanupFailed' }
        $Record.behaviorFailure = $false
    } elseif ($status.StartsWith('Passed', [StringComparison]::Ordinal) -or
              $status.StartsWith('Observed', [StringComparison]::Ordinal)) {
        # A caught controller/observer/isolation failure may arrive after the
        # clipboard outcome looked successful. Such records must be downgraded
        # by the caller before finalization; this guard prevents accidental
        # success from a record carrying a failure code.
        if ($Record.failureCode -or $Record.observationFailure) {
            $Record.status = 'Unverified (controller or observation failure)'
            $Record.behaviorFailure = $false
        }
    }

    return $Record
}

function New-ClipboardObservationOutputRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Fields,
        [Parameter(Mandatory)][bool]$CleanupSucceeded
    )

    # This is the single final record-construction path for both controller
    # outcomes and synthetic contract cases. Copy into an ordered record and
    # make nullable output-contract fields explicit before final disposition.
    $record = [ordered]@{}
    foreach ($key in $Fields.Keys) { $record[$key] = $Fields[$key] }
    foreach ($key in @('targetReady','targetReadyRepeat','targetReadyUnconfirmed','targetReadyReject',
            'targetOutcome','supersededOutcome','observation','observationFailure','failureCode')) {
        if (-not $record.Contains($key)) { $record[$key] = $null }
    }
    if (-not $record.Contains('timingVerification')) { $record.timingVerification = 'unavailable' }
    if (-not $record.Contains('behaviorFailure')) { $record.behaviorFailure = $false }
    return Complete-ClipboardObservationRecord -Record $record -CleanupSucceeded $CleanupSucceeded
}

function ConvertTo-ClipboardObservationRecordJson {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Record)
    return ConvertTo-Json -InputObject $Record -Depth 8
}

function Write-ClipboardObservationJsonArtifact {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Record,[Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$RunId,[bool]$CleanupSucceeded=$true)
    $finalRecord = New-ClipboardObservationOutputRecord -Fields $Record -CleanupSucceeded $CleanupSucceeded
    $path = Join-Path $OutputDirectory "clipboard-observation-$RunId.json"
    [IO.File]::WriteAllText($path,(ConvertTo-ClipboardObservationRecordJson -Record $finalRecord),[Text.UTF8Encoding]::new($false))
    return [pscustomobject]@{ Record=$finalRecord; Path=$path; Sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant() }
}

function Invoke-ClipboardSyntheticControllerRun {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Scenario,[Parameter(Mandatory)][string]$OutputDirectory)
    $audit = Invoke-ClipboardControllerAudit -Case $Scenario.Case -TargetReady $Scenario.TargetReady `
        -TargetReadyRepeat $Scenario.TargetReadyRepeat -TargetReceipt $Scenario.TargetReceipt `
        -PredecessorReceipt $Scenario.PredecessorReceipt -PredecessorReadFailure $Scenario.PredecessorReadFailure `
        -ObserverRecord $Scenario.ObserverRecord -FailureCode $Scenario.InjectedFailureCode `
        -AttemptObserved ([bool]$Scenario.AttemptObserved)
    $fields = [ordered]@{
        runId=$Scenario.RunId; fixtureId=$Scenario.RunId; case=$Scenario.Case
        targetReady=$Scenario.TargetReady; targetReadyRepeat=$Scenario.TargetReadyRepeat
        targetOutcome=$audit.targetOutcome; supersededOutcome=$audit.supersededOutcome
        observation=$audit.observation; observationFailure=$audit.observationFailure
        attemptOutcomeObserved=[bool]$Scenario.AttemptObserved
        timingVerification=$audit.timingVerification; failureCode=$audit.failureCode
        behaviorFailure=$audit.behaviorFailure; status=$audit.status
        clearRejectedAttemptObserved=$audit.clearRejectedAttemptObserved
        interruptedAttemptObserved=$audit.interruptedAttemptObserved
    }
    return Write-ClipboardObservationJsonArtifact -Record $fields -OutputDirectory $OutputDirectory `
        -RunId $Scenario.RunId -CleanupSucceeded ([bool]$Scenario.CleanupSucceeded)
}

function New-ClipboardObservationAuditDisposition {
    param(
        [string]$Case,
        [AllowNull()]$TargetReceipt,
        [AllowNull()]$Outcome,
        [AllowNull()]$ObserverRecord,
        [AllowNull()]$Observation,
        [bool]$AttemptObserved,
        [AllowNull()][Nullable[long]]$ScheduledElapsedMs,
        [AllowNull()][Nullable[long]]$CallbackElapsedMs,
        [AllowNull()][Nullable[long]]$OwnershipCheckElapsedMs,
        [AllowNull()][Nullable[long]]$ClearAttemptElapsedMs,
        [AllowNull()][string]$ObservationFailure
    )

    $timing = 'unavailable'
    if ($null -ne $ScheduledElapsedMs -and $null -ne $Outcome) {
        $timing = Get-ClipboardTimingDisposition -ScheduledElapsedMs $ScheduledElapsedMs `
            -CallbackElapsedMs $CallbackElapsedMs -OwnershipCheckElapsedMs $OwnershipCheckElapsedMs `
            -ClearAttemptElapsedMs $ClearAttemptElapsedMs -Outcome $Outcome
        if ($timing -eq 'Unavailable') { $timing = 'unverified' }
    }

    $observationLabel = if ($null -eq $Observation) { 'unavailable' } else { [string]$Observation }
    $status = Get-ClipboardObservationCaseStatus -Case $Case -Outcome $Outcome `
        -Observation $observationLabel -AttemptObserved $AttemptObserved
    $behaviorFailure = $false
    if ($timing -eq 'Failed') {
        $status = 'Failed (clipboard cleanup execution timestamps violated the deadline or event ordering)'
        $behaviorFailure = $true
    } elseif ($timing -eq 'unverified') {
        $status = "Unverified (cleanup execution timing is incomplete; $status)"
    }
    if ($ObservationFailure) {
        if ($timing -eq 'Failed') {
            $status = "Failed (cleanup timing violation; independent observation unavailable: $ObservationFailure)"
            $behaviorFailure = $true
        } else {
            $status = "Unverified (independent observation unavailable: $ObservationFailure)"
            $behaviorFailure = $false
        }
    }
    if ($status.StartsWith('Failed', [StringComparison]::Ordinal)) { $behaviorFailure = $true }

    return [ordered]@{
        targetOutcome = if ($null -ne $TargetReceipt) { $TargetReceipt } else { $Outcome }
        observation = if ($null -ne $ObserverRecord) { $ObserverRecord } else { $Observation }
        observationFailure = $ObservationFailure
        attemptOutcomeObserved = $AttemptObserved
        timingVerification = $timing
        status = $status
        behaviorFailure = $behaviorFailure
        clearRejectedAttemptObserved = [bool]($AttemptObserved -and $Outcome -eq 'ClearRejected')
        interruptedAttemptObserved = [bool]($AttemptObserved -and $Outcome -eq 'Interrupted')
    }
}

function Assert-ClipboardObservationClassificationCases {
    $cases = @(
        @{ Case = 'scheduled-attempt'; Outcome = 'ClearRequestSubmitted'; Observation = 'absent'; AttemptObserved = $true; Expected = 'Passed' },
        @{ Case = 'scheduled-attempt'; Outcome = 'ClearRejected'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Observed' },
        @{ Case = 'scheduled-attempt'; Outcome = 'Interrupted'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Observed' },
        @{ Case = 'clipboard-replaced'; Outcome = 'SkippedReplaced'; Observation = 'replaced'; AttemptObserved = $true; Expected = 'Passed' },
        @{ Case = 'clipboard-replaced'; Outcome = 'ClearRejected'; Observation = 'replaced'; AttemptObserved = $true; Expected = 'Failed' },
        @{ Case = 'clipboard-replaced'; Outcome = 'SkippedReplaced'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Failed' },
        @{ Case = 'app-switch'; Outcome = 'SkippedReplaced'; Observation = 'replaced'; AttemptObserved = $true; Expected = 'Passed' },
        @{ Case = 'app-switch'; Outcome = 'SkippedReplaced'; Observation = 'inaccessible'; AttemptObserved = $true; Expected = 'Failed' },
        @{ Case = 'app-switch'; Outcome = 'ClearRejected'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Observed' },
        @{ Case = 'app-switch'; Outcome = $null; Observation = 'marker-match'; AttemptObserved = $false; Expected = 'Unverified' },
        @{ Case = 'locked-sleep'; Outcome = 'ClearRejected'; Observation = 'inaccessible'; AttemptObserved = $true; Expected = 'Observed' },
        @{ Case = 'repeat-copy'; Outcome = 'Interrupted'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Observed' },
        @{ Case = 'repeat-copy'; Outcome = 'ClearRejected'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Observed' },
        @{ Case = 'repeat-copy'; Outcome = 'ClearRequestSubmitted'; Observation = 'marker-match'; AttemptObserved = $true; Expected = 'Failed' },
        @{ Case = 'repeat-copy'; Outcome = 'ClearRequestSubmitted'; Observation = 'absent'; AttemptObserved = $true; Expected = 'Passed' },
        @{ Case = 'locked-sleep'; Outcome = $null; Observation = 'inaccessible'; AttemptObserved = $false; Expected = 'Unverified' }
    )
    foreach ($item in $cases) {
        $actual = Get-ClipboardObservationCaseStatus -Case $item.Case -Outcome $item.Outcome `
            -Observation $item.Observation -AttemptObserved $item.AttemptObserved
        if (-not $actual.StartsWith($item.Expected, [StringComparison]::Ordinal)) {
            throw "Clipboard classification self-check failed for $($item.Case)/$($item.Outcome): $actual"
        }
    }

    $validReceipt = [pscustomobject]@{ runId = 'synthetic'; generation = 7; scheduledElapsedMs = 30000 }
    if (-not (Test-ClipboardReceiptIdentity -Receipt $validReceipt -RunId 'synthetic' -Generation 7 -ScheduledElapsedMs 30000) -or
        (Test-ClipboardReceiptIdentity -Receipt $validReceipt -RunId 'other' -Generation 7 -ScheduledElapsedMs 30000) -or
        (Test-ClipboardReceiptIdentity -Receipt $validReceipt -RunId 'synthetic' -Generation 8 -ScheduledElapsedMs 30000) -or
        (Test-ClipboardReceiptIdentity -Receipt $validReceipt -RunId 'synthetic' -Generation 7 -ScheduledElapsedMs 30001)) {
        throw 'Clipboard receipt identity self-check failed.'
    }
}

function Get-ClipboardTimingDisposition {
    param(
        [long]$ScheduledElapsedMs,
        [AllowNull()][Nullable[long]]$CallbackElapsedMs,
        [AllowNull()][Nullable[long]]$OwnershipCheckElapsedMs,
        [AllowNull()][Nullable[long]]$ClearAttemptElapsedMs,
        [string]$Outcome
    )
    $clearOutcomes = @('ClearRequestSubmitted', 'ClearRejected')
    $skipOutcomes = @('SkippedReplaced', 'SkippedOwnershipUnavailable')
    if ($Outcome -notin ($clearOutcomes + $skipOutcomes + @('Interrupted'))) { return 'Unavailable' }
    if ($Outcome -in $skipOutcomes -and $null -ne $ClearAttemptElapsedMs) { return 'Failed' }
    if ($Outcome -ne 'Interrupted' -and $null -ne $CallbackElapsedMs -and $CallbackElapsedMs -lt $ScheduledElapsedMs) { return 'Failed' }
    if ($null -ne $CallbackElapsedMs -and $null -ne $OwnershipCheckElapsedMs -and $OwnershipCheckElapsedMs -lt $CallbackElapsedMs) { return 'Failed' }
    if ($null -ne $OwnershipCheckElapsedMs -and $null -ne $ClearAttemptElapsedMs -and $ClearAttemptElapsedMs -lt $OwnershipCheckElapsedMs) { return 'Failed' }
    if ($Outcome -in $clearOutcomes -and
        ($null -eq $CallbackElapsedMs -or $null -eq $OwnershipCheckElapsedMs -or $null -eq $ClearAttemptElapsedMs)) { return 'Unavailable' }
    if ($Outcome -in $skipOutcomes -and
        ($null -eq $CallbackElapsedMs -or $null -eq $OwnershipCheckElapsedMs -or $null -ne $ClearAttemptElapsedMs)) { return 'Unavailable' }
    if ($Outcome -eq 'Interrupted' -and $null -eq $CallbackElapsedMs) { return 'Unavailable' }
    return 'Verified'
}

function Assert-ClipboardObservationTimingCases {
    $cases = @(
        @{ Scheduled = 30000; Callback = 30000; Check = 30001; Clear = 30002; Outcome = 'ClearRequestSubmitted'; Expected = 'Verified' },
        @{ Scheduled = 30000; Callback = 29999; Check = 30000; Clear = 30001; Outcome = 'ClearRequestSubmitted'; Expected = 'Failed' },
        @{ Scheduled = 30000; Callback = 29999; Check = 30000; Clear = $null; Outcome = 'Interrupted'; Expected = 'Verified' },
        @{ Scheduled = 30000; Callback = $null; Check = $null; Clear = $null; Outcome = 'ClearRequestSubmitted'; Expected = 'Unavailable' },
        @{ Scheduled = 30000; Callback = 30000; Check = $null; Clear = $null; Outcome = 'ClearRequestSubmitted'; Expected = 'Unavailable' },
        @{ Scheduled = 30000; Callback = 30000; Check = 30001; Clear = $null; Outcome = 'ClearRequestSubmitted'; Expected = 'Unavailable' },
        @{ Scheduled = 30000; Callback = 30000; Check = 30001; Clear = 30002; Outcome = 'SkippedReplaced'; Expected = 'Failed' }
    )
    foreach ($item in $cases) {
        $actual = Get-ClipboardTimingDisposition -ScheduledElapsedMs $item.Scheduled `
            -CallbackElapsedMs $item.Callback -OwnershipCheckElapsedMs $item.Check `
            -ClearAttemptElapsedMs $item.Clear -Outcome $item.Outcome
        if ($actual -ne $item.Expected) { throw "Clipboard timing self-check failed for $($item.Outcome): $actual" }
    }
}
