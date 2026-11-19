[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Serial,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')][string]$TargetPackage,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')][string]$TestPackage,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ObserverActivity,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]{1,64}$')][string]$RunId,
    [Parameter(Mandatory)][ValidateSet('process-death', 'scheduled-attempt', 'app-switch')][string]$Case,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$adbPath = (Get-Command adb -ErrorAction Stop).Source
$targetTokenFile = 'clipboard-target.expected'
$observerTokenFile = 'clipboard-observer.expected'
$readyFile = "clipboard-target-ready-$RunId.json"
$outcomeFile = "clipboard-target-outcome-$RunId.json"
$observerResultFile = "clipboard-observer-result-$RunId.json"
$targetReady = $null
$targetReadyJson = $null
$targetOutcome = $null
$attemptOutcomeObserved = $false
$controlMatch = $null
$controlMismatch = $null
$observation = $null
$targetPidBefore = $null
$observerPid = $null
$failureCode = $null
$behaviorFailure = $false
$script:activeStage = 'device-preflight'
$cleanupSucceeded = $true
$targetWasStopped = $false
$targetBackgroundConfirmed = $false

if ($TargetPackage -eq $TestPackage) { throw 'Target and observer must be separate packages.' }
if ($ObserverActivity -notmatch '^com\.chimali\.security\.ClipboardObserverActivity$') {
    throw 'Observer activity must be the declared test-APK clipboard observer.'
}
if ($RunId.StartsWith('control-')) { throw 'Run ID uses a reserved observer-control prefix.' }

function Invoke-AdbProcess {
    param([string[]]$Arguments, [AllowNull()][string]$StandardInputText = $null)
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $adbPath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $null -ne $StandardInputText
    foreach ($argument in $Arguments) { [void]$startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    if ($null -ne $StandardInputText) {
        $process.StandardInput.Write($StandardInputText)
        $process.StandardInput.Close()
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; StdOut = $stdout; StdErr = $stderr }
}

function Invoke-DeviceShell {
    param([string[]]$Command, [AllowNull()][string]$StandardInputText = $null)
    Invoke-AdbProcess -Arguments (@('-s', $Serial, 'shell') + $Command) -StandardInputText $StandardInputText
}

function Write-PrivateRunFile {
    param([string]$Package, [string]$FileName, [string]$Value)
    $directory = Invoke-DeviceShell -Command @('run-as', $Package, 'mkdir', '-p', 'files')
    if ($directory.ExitCode -ne 0) { throw 'Private synthetic run-state directory could not be prepared.' }
    $command = "run-as $Package sh -c 'cat > files/$FileName'"
    $result = Invoke-AdbProcess -Arguments @('-s', $Serial, 'shell', $command) -StandardInputText $Value
    if ($result.ExitCode -ne 0) { throw 'Private synthetic run-state handoff failed.' }
}

function Read-PrivateRunFile {
    param([string]$Package, [string]$FileName)
    $result = Invoke-DeviceShell -Command @('run-as', $Package, 'cat', "files/$FileName")
    if ($result.ExitCode -ne 0) { return $null }
    $result.StdOut.Trim()
}

function Remove-PrivateRunFile {
    param([string]$Package, [string]$FileName)
    $result = Invoke-DeviceShell -Command @('run-as', $Package, 'rm', '-f', "files/$FileName")
    $result.ExitCode -eq 0
}

function Get-PackagePid {
    param([string]$Package)
    $result = Invoke-DeviceShell -Command @('ps', '-A', '-o', 'PID,NAME')
    if ($result.ExitCode -ne 0) { return $null }
    foreach ($line in ($result.StdOut -split "`r?`n")) {
        if ($line -match '^\s*(\d+)\s+([^\s]+)') {
            $processName = $Matches[2]
            if ($processName -eq $Package -or $processName.StartsWith("$Package`:")) {
                return [int]$Matches[1]
            }
        }
    }
    return $null
}

function Start-Observer {
    param([string]$Mode)
    $script:activeStage = "observer-clear-$Mode-result"
    $oldResult = Invoke-DeviceShell -Command @('run-as', $TestPackage, 'rm', '-f', "files/$observerResultFile")
    if ($oldResult.ExitCode -ne 0) { throw 'Prior observer result could not be removed.' }
    $script:activeStage = "observer-launch-$Mode"
    $component = "$TestPackage/$ObserverActivity"
    $start = Invoke-DeviceShell -Command @(
        'am', 'start', '-W', '-n', $component,
        '--es', 'com.chimali.security.extra.RUN_ID', $RunId,
        '--es', 'com.chimali.security.extra.MODE', $Mode
    )
    if ($start.ExitCode -ne 0 -or $start.StdOut -notmatch 'Status: ok') { throw 'Observer activity launch failed.' }
    $script:activeStage = "observer-wait-$Mode-result"
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        $json = Read-PrivateRunFile -Package $TestPackage -FileName $observerResultFile
        if ($json) {
            try {
                $record = $json | ConvertFrom-Json -ErrorAction Stop
                if ($record.runId -eq $RunId -and ($record.capabilityControl -ne 'not-run' -or $record.cleanupOutcome -ne 'not-requested')) { return $record }
                if ($record.runId -eq $RunId -and $Mode -eq 'observe') { return $record }
            } catch { }
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Observer result was unavailable.'
}

function Get-SanitizedPids {
    $observerName = "$TestPackage`:clipboardObserver"
    [pscustomobject]@{
        Target = Get-PackagePid -Package $TargetPackage
        Observer = Get-PackagePid -Package $observerName
    }
}

function Wait-TargetBackground {
    param([string]$Package)
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $activities = Invoke-DeviceShell -Command @('dumpsys', 'activity', 'activities')
        if ($activities.ExitCode -eq 0 -and
            $activities.StdOut -match '(?:topResumedActivity|mResumedActivity)[:=]\s*ActivityRecord\{[^}]*\s([A-Za-z0-9_.]+)/' -and
            $Matches[1] -ne $Package) {
            return $true
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    return $false
}

try {
    $deviceState = Invoke-AdbProcess -Arguments @('-s', $Serial, 'get-state')
    if ($deviceState.ExitCode -ne 0 -or $deviceState.StdOut.Trim() -ne 'device') { throw 'Device is unavailable.' }
    foreach ($package in @($TargetPackage, $TestPackage)) {
        $packagePath = Invoke-DeviceShell -Command @('pm', 'path', $package)
        if ($packagePath.ExitCode -ne 0 -or $packagePath.StdOut -notmatch 'package:') { throw 'Required package is not installed.' }
    }

    # run-as is required by the contract. A non-debuggable target makes the run Unverified.
    foreach ($package in @($TargetPackage, $TestPackage)) {
        $identity = Invoke-DeviceShell -Command @('run-as', $package, 'id')
        if ($identity.ExitCode -ne 0) { throw 'Required package does not permit controlled run-as access.' }
    }

    [void](New-Item -ItemType Directory -Path $OutputDirectory -Force)
    $script:activeStage = 'private-token-handoff'
    $tokenBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    $expectedToken = [Convert]::ToHexString($tokenBytes).ToLowerInvariant()
    [Array]::Clear($tokenBytes, 0, $tokenBytes.Length)
    Write-PrivateRunFile -Package $TargetPackage -FileName $targetTokenFile -Value $expectedToken
    Write-PrivateRunFile -Package $TestPackage -FileName $observerTokenFile -Value $expectedToken
    $observerReset = Invoke-DeviceShell -Command @('am', 'force-stop', $TestPackage)
    if ($observerReset.ExitCode -ne 0) { throw 'Observer process could not be reset before controls.' }

    $script:activeStage = 'positive-capability-control'
    $controlMatch = Start-Observer -Mode 'control-match'
    if ($controlMatch.capabilityControl -ne 'marker-match') { throw 'Observer positive control failed.' }
    $script:activeStage = 'mismatch-capability-control'
    $controlMismatch = Start-Observer -Mode 'control-mismatch'
    if ($controlMismatch.capabilityControl -ne 'replaced') { throw 'Observer mismatch control failed.' }

    $script:activeStage = 'target-synthetic-copy-launch'
    $targetComponent = "$TargetPackage/com.chimali.security.ClipboardTargetCopyActivity"
    $targetStart = Invoke-DeviceShell -Command @(
        'am', 'start', '-W', '-n', $targetComponent,
        '--es', 'com.chimali.security.extra.RUN_ID', $RunId
    )
    if ($targetStart.ExitCode -ne 0 -or $targetStart.StdOut -notmatch 'Status: ok') { throw 'Target synthetic-copy activity launch failed.' }
    $script:activeStage = 'target-synthetic-copy-ready-barrier'
    $targetReadyJson = $null
    $readyDeadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $targetReadyJson = Read-PrivateRunFile -Package $TargetPackage -FileName $readyFile
        if ($targetReadyJson) { break }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $readyDeadline)
    if (-not $targetReadyJson) { throw 'Target-ready receipt was unavailable.' }
    $targetReady = $targetReadyJson | ConvertFrom-Json -ErrorAction Stop
    $copyCallStarted = [long]$targetReady.copyCallStartedElapsedMs
    $publicationElapsed = [long]$targetReady.publicationElapsedMs
    $copyCallReturned = [long]$targetReady.copyCallReturnedElapsedMs
    $scheduledElapsed = [long]$targetReady.scheduledElapsedMs
    if ($targetReady.runId -ne $RunId -or $targetReady.publication -ne 'MetadataConfirmed' -or
        $targetReady.targetWindowFocused -ne $true -or $copyCallStarted -le 0 -or
        $copyCallStarted -gt $publicationElapsed -or $publicationElapsed -gt $copyCallReturned -or
        $scheduledElapsed - $publicationElapsed -ne 30000) {
        throw 'Target-ready receipt did not confirm a focused synthetic candidate with a valid 30-second schedule.'
    }
    $targetPidBefore = Get-PackagePid -Package $TargetPackage
    if (-not $targetPidBefore) { throw 'Target process was not observable after its ready barrier.' }

    if ($Case -eq 'process-death') {
        $script:activeStage = 'target-process-stop'
        $stop = Invoke-DeviceShell -Command @('am', 'force-stop', $TargetPackage)
        if ($stop.ExitCode -ne 0) { throw 'Target process stop failed.' }
        $targetWasStopped = $true
        if (Get-PackagePid -Package $TargetPackage) { throw 'Target process remained alive after force-stop.' }
        $script:activeStage = 'post-stop-observation'
    } else {
        if ($Case -eq 'app-switch') {
            $script:activeStage = 'target-app-switch'
            $homeKeyResult = Invoke-DeviceShell -Command @('input', 'keyevent', 'KEYCODE_HOME')
            if ($homeKeyResult.ExitCode -ne 0) { throw 'Home key injection failed during app switch.' }
            if (-not (Wait-TargetBackground -Package $TargetPackage)) { throw 'Target foreground state remained after app switch.' }
            $targetBackgroundConfirmed = $true
        }
        $script:activeStage = 'scheduled-attempt-wait'
        $targetOutcomeJson = $null
        $attemptDeadline = [DateTime]::UtcNow.AddSeconds(45)
        do {
            $targetOutcomeJson = Read-PrivateRunFile -Package $TargetPackage -FileName $outcomeFile
            if ($targetOutcomeJson) { break }
            Start-Sleep -Milliseconds 200
        } while ([DateTime]::UtcNow -lt $attemptDeadline)
        if (-not $targetOutcomeJson -and $Case -eq 'app-switch') {
            $targetOutcome = [pscustomobject]@{
                runId = $RunId
                generation = $targetReady.generation
                scheduledElapsedMs = $targetReady.scheduledElapsedMs
                terminalStatusElapsedMs = $null
                outcome = 'NoTerminalOutcomeObserved'
            }
        } else {
            if (-not $targetOutcomeJson) { throw 'Scheduled cleanup outcome was unavailable.' }
            $targetOutcome = $targetOutcomeJson | ConvertFrom-Json -ErrorAction Stop
            if ($targetOutcome.runId -ne $RunId -or $targetOutcome.generation -ne $targetReady.generation -or
                [long]$targetOutcome.terminalStatusElapsedMs -lt [long]$targetReady.scheduledElapsedMs) {
                throw 'Scheduled cleanup outcome did not match the receipt deadline.'
            }
            if ($targetOutcome.outcome -notin @('ClearRequestSubmitted', 'SkippedReplaced', 'SkippedOwnershipUnavailable')) {
                throw 'Scheduled cleanup outcome was not a terminal automatic result.'
            }
            if ($Case -eq 'scheduled-attempt' -and $targetOutcome.outcome -ne 'ClearRequestSubmitted') {
                throw 'Focused matching candidate did not submit the scheduled clear request.'
            }
            $attemptOutcomeObserved = $true
        }
        $script:activeStage = if ($attemptOutcomeObserved) { 'scheduled-attempt-observation' } else { 'background-interruption-observation' }
    }
    $observation = Start-Observer -Mode 'observe'
    $testCleanup = $null
    if ($observation.classification -eq 'marker-match') {
        $script:activeStage = 'synthetic-clipboard-cleanup'
        $testCleanup = Start-Observer -Mode 'clear-if-owned'
    }
    if ($Case -eq 'app-switch' -and $targetOutcome.outcome -eq 'SkippedReplaced' -and
        $observation.classification -ne 'replaced') {
        throw 'Ownership replacement skip did not match the independent clipboard observation.'
    }
    $pids = Get-SanitizedPids
    $observerPid = [int]$observation.observerPid
    if (($Case -eq 'process-death' -and $pids.Target) -or
        ($Case -in @('scheduled-attempt', 'app-switch') -and -not $pids.Target) -or
        -not $pids.Observer -or $pids.Observer -ne $observerPid) {
        throw 'Target/observer process-isolation check failed.'
    }

    if ($Case -in @('scheduled-attempt', 'app-switch') -and $targetOutcome.outcome -eq 'ClearRequestSubmitted' -and
        $observation.classification -notin @('absent', 'marker-match', 'replaced', 'inaccessible')) {
        throw 'Scheduled cleanup observation was outside the classification contract.'
    }

    $caseStatus = if ($Case -eq 'process-death') {
        'Passed (process-death observer case)'
    } elseif ($Case -eq 'app-switch' -and $targetOutcome.outcome -eq 'SkippedOwnershipUnavailable') {
        'Passed (background ownership unavailable; automatic cleanup safely skipped)'
    } elseif ($Case -eq 'app-switch' -and $targetOutcome.outcome -eq 'SkippedReplaced' -and
        $observation.classification -eq 'replaced') {
        'Passed (background clipboard replacement detected; automatic cleanup skipped)'
    } elseif ($Case -eq 'app-switch' -and -not $attemptOutcomeObserved -and $observation.classification -eq 'marker-match') {
        'Passed (background interruption; synthetic clipboard marker remained, no cleanup outcome was observed)'
    } elseif ($Case -eq 'app-switch' -and -not $attemptOutcomeObserved) {
        'Unverified (background interruption; clipboard outcome did not match the synthetic marker)'
    } elseif ($Case -eq 'app-switch') {
        'Passed (background app-switch cleanup observation case)'
    } else {
        'Passed (scheduled cleanup observation case)'
    }
    $record = [ordered]@{
        runId = $RunId
        fixtureId = $RunId
        case = $Case
        targetPackage = $TargetPackage
        testPackage = $TestPackage
        targetPidBeforeCase = $targetPidBefore
        targetPidAfterStop = if ($targetWasStopped) { $pids.Target } else { $null }
        targetPidDuringObservation = $pids.Target
        observerPid = $observerPid
        targetReady = $targetReady
        targetOutcome = $targetOutcome
        attemptOutcomeObserved = $attemptOutcomeObserved
        matchControl = $controlMatch
        mismatchControl = $controlMismatch
        observation = $observation
        testCleanup = $testCleanup
        targetForceStopped = $targetWasStopped
        targetBackgroundConfirmed = $targetBackgroundConfirmed
        status = $caseStatus
    }
} catch {
    $failureCode = switch -Regex ($_.Exception.Message) {
        'Target-ready receipt did not confirm|Focused matching candidate did not submit|Scheduled cleanup outcome did not match|Scheduled cleanup outcome was not a terminal' {
            $behaviorFailure = $true
            'ScopedClipboardBehaviorFailure'
            break
        }
        'Home key injection' { 'HomeKeyInjectionFailed'; break }
        'Target foreground state' { 'TargetBackgroundNotConfirmed'; break }
        'null-valued expression' { if ($script:activeStage -eq 'target-app-switch') { 'TargetBackgroundProbeFailed' } else { 'ObserverPreflightFailed' }; break }
        'run-as|controlled run-as' { 'ControlledPackageAccessUnavailable'; break }
        'Prior observer result' { 'PriorResultCleanupFailed'; break }
        'Observer activity launch' { 'ObserverLaunchFailed'; break }
        'Observer result was unavailable' { 'ObserverResultUnavailable'; break }
        'Observer positive control' { 'ObserverPositiveControlFailed'; break }
        default { 'ObserverPreflightFailed' }
    }
    $record = [ordered]@{
        runId = $RunId
        fixtureId = $RunId
        case = $Case
        targetPackage = $TargetPackage
        testPackage = $TestPackage
        failedAt = $script:activeStage
        matchControl = $controlMatch
        mismatchControl = $controlMismatch
        targetReady = $targetReady
        targetOutcome = $targetOutcome
        attemptOutcomeObserved = $attemptOutcomeObserved
        targetForceStopped = $targetWasStopped
        failureCode = $failureCode
        status = if ($behaviorFailure) { 'Failed (scoped clipboard behavior assertion)' } else { 'Unverified' }
    }
} finally {
    if ($targetWasStopped -eq $false) {
        $targetPid = Get-PackagePid -Package $TargetPackage
        if ($targetPid) {
            $stop = Invoke-DeviceShell -Command @('am', 'force-stop', $TargetPackage)
            if ($stop.ExitCode -ne 0) { $cleanupSucceeded = $false }
        }
    }
    $observerStop = Invoke-DeviceShell -Command @('am', 'force-stop', $TestPackage)
    if ($observerStop.ExitCode -ne 0) { $cleanupSucceeded = $false }
    foreach ($entry in @(
        @($TargetPackage, $targetTokenFile),
        @($TestPackage, $observerTokenFile),
        @($TargetPackage, $readyFile),
        @($TargetPackage, $outcomeFile),
        @($TestPackage, $observerResultFile)
    )) {
        if (-not (Remove-PrivateRunFile -Package $entry[0] -FileName $entry[1])) { $cleanupSucceeded = $false }
    }
    if ($null -ne $expectedToken) { $expectedToken = $null }
}

$record.cleanupSucceeded = $cleanupSucceeded
$jsonOutput = $record | ConvertTo-Json -Depth 8
$outputPath = Join-Path $OutputDirectory "clipboard-observation-$RunId.json"
[IO.File]::WriteAllText($outputPath, $jsonOutput, [Text.UTF8Encoding]::new($false))
$digest = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath).Hash.ToLowerInvariant()
[pscustomobject]@{ status = $record.status; artifact = $outputPath; sha256 = $digest } | ConvertTo-Json -Compress
