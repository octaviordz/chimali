#requires -Version 7.0
<#
.SYNOPSIS
  SEC065 heap-audit capture orchestrator.

.DESCRIPTION
  Runs one LeakCanaryMnemonicOwnerTest instrumentation case against the isolated
  com.chimali.heapaudit package, performs `am dumpheap` at each gated checkpoint
  the test publishes under files/security-heap-audit/<runId>/, acknowledges each
  capture so the test can proceed, and stores raw HPROFs plus the synthetic
  expected-marker file only inside the restricted destination directory.

  Nothing secret-bearing is written to the repository; only sanitized results
  are recorded in specs/065-security-secret-cleanup/evidence/.

.PARAMETER Destination
  Restricted local directory for raw captures (outside the repo). Must already
  exist with inheritance-disabled ACL limited to the operator and SYSTEM.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$RunId,
    [Parameter(Mandatory = $true)][string]$TestMethod,
    [Parameter(Mandatory = $true)][string]$Destination,
    [int]$PollMilliseconds = 250,
    [int]$StageTimeoutSeconds = 330
)

$ErrorActionPreference = 'Stop'
$TargetPackage = 'com.chimali.heapaudit'
$TestPackage = 'com.chimali.heapaudit.test'
$Runner = "$TestPackage/androidx.test.runner.AndroidJUnitRunner"
$AuditRelDir = "files/security-heap-audit/$RunId"

if ($RunId -notmatch '^[A-Za-z0-9_-]{1,64}$') { throw "RunId '$RunId' rejected by session regex" }
if (-not (Test-Path -LiteralPath $Destination)) { throw "Destination '$Destination' does not exist" }
$Destination = (Resolve-Path -LiteralPath $Destination).Path

function Invoke-Adb {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string[]]$AdbArgs, [switch]$Quiet, [switch]$AllowFailure)
    $out = & adb -s $Serial @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0 -and -not $AllowFailure) { throw "adb $(($AdbArgs) -join ' ') failed: $out" }
    return $out
}

function Read-AuditFile([string]$Name) {
    $content = Invoke-Adb @('shell', "run-as $TargetPackage cat '$AuditRelDir/$Name'") -Quiet
    return ($content -join "`n")
}

Write-Host "[*] Starting instrumentation: $TestMethod runId=$RunId"
$adbExe = (Get-Command adb).Source
$instrumentArgLine = '-s ' + $Serial + ' shell am instrument -w ' +
    '-e class ' + $TestMethod +
    ' -e sec065HeapRunId ' + $RunId +
    ' ' + $Runner
$instrOut = Join-Path $Destination "${RunId}_instrumentation.txt"
$proc = Start-Process -FilePath $adbExe -ArgumentList $instrumentArgLine `
    -RedirectStandardOutput $instrOut -RedirectStandardError "$instrOut.err" `
    -NoNewWindow -PassThru

$results = [ordered]@{
    runId          = $RunId
    serial         = $Serial
    testMethod     = $TestMethod
    destination    = $Destination
    stages         = @()
    markersFile    = $null
    instrumentation = $null
}

try {
    $deadline = (Get-Date).AddSeconds($StageTimeoutSeconds)
    $seen = @{}
    while ((Get-Date) -lt $deadline) {
        $proc.Refresh()
        if ($proc.HasExited) { break }
        $listing = Invoke-Adb @('shell', "run-as $TargetPackage ls '$AuditRelDir' 2>/dev/null") -Quiet -AllowFailure
        $phaseFile = $listing | Where-Object { $_ -eq 'phase' }

        # The session appends to expected-markers.txt during the run and deletes
        # the whole directory in close(); snapshot the latest content every poll.
        $markersNow = Invoke-Adb @('shell', "run-as $TargetPackage cat '$AuditRelDir/expected-markers.txt' 2>/dev/null") -Quiet -AllowFailure
        if ($markersNow) { $script:lastMarkers = $markersNow }

        if ($phaseFile) {
            $stage = (Read-AuditFile 'phase').Trim()
            if ($stage -and -not $seen.ContainsKey($stage)) {
                Write-Host "[*] Checkpoint '$stage' reached; locating target PID"
                $pidText = (Invoke-Adb @('shell', "pidof $TargetPackage") -Quiet) -join ' '
                $targetPid = ($pidText -split '\s+' | Where-Object { $_ } | Select-Object -First 1)
                if (-not $targetPid) { throw "Target PID for $TargetPackage not found at stage '$stage'" }

                # `am dumpheap <pid>` auto-names /data/local/tmp/heapdump-<ts>.prof;
                # parse the reported path, pull it, then remove the device copy.
                $dumpOut = Invoke-Adb @('shell', "am dumpheap $targetPid") -Quiet
                $dumpOut | ForEach-Object { Write-Host "    $_" }
                $deviceDump = ($dumpOut | Where-Object { $_ -match '^File:\s*(\S+)' } |
                    ForEach-Object { $Matches[1] } | Select-Object -First 1)
                if (-not $deviceDump) { throw "am dumpheap did not report an output file at stage '$stage'" }
                $dumpName = "${RunId}_${stage}.hprof"
                $hostTmp = Join-Path $env:TEMP $dumpName
                Invoke-Adb @('pull', $deviceDump, $hostTmp) -Quiet | Out-Null
                Invoke-Adb @('shell', "rm -f '$deviceDump'") -Quiet | Out-Null
                Move-Item -LiteralPath $hostTmp -Destination (Join-Path $Destination $dumpName) -Force
                $hash = (Get-FileHash -LiteralPath (Join-Path $Destination $dumpName) -Algorithm SHA256).Hash
                Write-Host "    captured $dumpName ($hash)"

                # Acknowledge so the gated test can continue.
                Invoke-Adb @('shell', "run-as $TargetPackage sh -c 'echo captured > `"$AuditRelDir/$stage.captured`"'") -Quiet | Out-Null
                $seen[$stage] = [ordered]@{ file = $dumpName; sha256 = $hash; pid = $targetPid }
                $results.stages += $seen[$stage]
            }
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    }

    $proc.Refresh()
    if (-not $proc.HasExited) { $proc.Kill() }
    $results.instrumentation = (Get-Content -LiteralPath $instrOut -Raw -ErrorAction SilentlyContinue)
    if (Test-Path "$instrOut.err") { $results.instrumentation += "`n" + (Get-Content -LiteralPath "$instrOut.err" -Raw) }

    # Persist the last in-run snapshot of the synthetic expected-marker dictionary.
    if ($script:lastMarkers) {
        $markersPath = Join-Path $Destination "${RunId}_expected-markers.txt"
        [IO.File]::WriteAllText($markersPath, ($script:lastMarkers -join "`n"),
            (New-Object System.Text.UTF8Encoding($false)))
        $results.markersFile = $markersPath
    } else {
        Write-Warning "No expected-markers snapshot captured; raw HPROFs lack their marker dictionary."
    }
} finally {
    if (-not $proc.HasExited) { try { $proc.Kill() } catch {} }
    $proc.WaitForExit()
    # Best-effort device cleanup; the test's close() removes the session dir.
    Invoke-Adb @('shell', "run-as $TargetPackage rm -rf '$AuditRelDir'") -Quiet -ErrorAction SilentlyContinue
    Invoke-Adb @('shell', "am force-stop $TargetPackage") -Quiet -ErrorAction SilentlyContinue
}

$summaryPath = Join-Path $Destination "${RunId}_capture-summary.json"
($results | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Host "[*) Done. Summary: $summaryPath"
$results | ConvertTo-Json -Depth 6

