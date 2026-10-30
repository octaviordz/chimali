[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Case,
    [Parameter(Mandatory)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$hookPath = Join-Path $RepositoryRoot 'tools/coverage/pre-push-hook.ps1'
$candidateSha = (& git -C $RepositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $candidateSha -notmatch '^[0-9a-f]{40,64}$') { throw 'Fixture requires a committed candidate.' }
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-prepush-fixture-' + [guid]::NewGuid().ToString('N'))
$harnessRoot = Join-Path $tempRoot 'trusted-harness'
$unrelatedRoot = Join-Path $tempRoot 'unrelated-working-directory'
$evidenceRoot = Join-Path $tempRoot 'evidence'
$stubLog = Join-Path $tempRoot 'integration-calls.jsonl'
$null = New-Item -ItemType Directory -Path (Join-Path $harnessRoot 'tools') -Force
$null = New-Item -ItemType Directory -Path $unrelatedRoot -Force

@'
[CmdletBinding()]
param([string]$RepositoryRoot, [string]$CandidateRef, [string]$TargetRef, [string]$HarnessRoot, [string]$DeviceSerial, [string]$EvidenceRoot, [string]$RemoteName)
$record = [ordered]@{ RepositoryRoot = $RepositoryRoot; CandidateRef = $CandidateRef; TargetRef = $TargetRef; HarnessRoot = $HarnessRoot; DeviceSerial = $DeviceSerial; EvidenceRoot = $EvidenceRoot; RemoteName = $RemoteName }
Add-Content -LiteralPath $env:HOOK_STUB_LOG -Value ($record | ConvertTo-Json -Compress)
exit ([int]$env:HOOK_STUB_EXIT)
'@ | Set-Content -LiteralPath (Join-Path $harnessRoot 'tools/integration-ci.ps1') -Encoding utf8
& git -C $harnessRoot init | Out-Null
& git -C $harnessRoot config user.name 'Coverage Fixture'
& git -C $harnessRoot config user.email 'coverage-fixture@example.invalid'
& git -C $harnessRoot add tools/integration-ci.ps1
& git -C $harnessRoot -c core.hooksPath= commit --no-gpg-sign -m 'Create trusted harness fixture' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not commit the isolated trusted harness fixture.' }
$stubHarnessCommit = (& git -C $harnessRoot rev-parse HEAD).Trim()

function Invoke-Hook {
    param([string]$InputText, [string]$ClearVariable, [string]$Remote = 'origin', [string]$TargetRef = 'origin/integration', [int]$StubExit = 0, [switch]$NoCoverageInputs)
    $invokeHookPath = $hookPath
    if ($ClearVariable -eq 'CHIMALI_INTEGRATION_TARGET') {
        $noTargetRoot = Join-Path $tempRoot 'no-target-repository'
        $noTargetHook = Join-Path $noTargetRoot 'tools/coverage/pre-push-hook.ps1'
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $noTargetHook) -Force
        Copy-Item -LiteralPath $hookPath -Destination $noTargetHook
        & git -C $noTargetRoot init | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not initialize isolated no-target hook repository.' }
        $invokeHookPath = $noTargetHook
    }
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Process -Id $PID).Path
    $start.ArgumentList.Add('-NoProfile')
    $start.ArgumentList.Add('-File')
    $start.ArgumentList.Add($invokeHookPath)
    $start.ArgumentList.Add($Remote)
    $start.ArgumentList.Add('https://example.invalid/chimali.git')
    $start.WorkingDirectory = $unrelatedRoot
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $vars = @{
        GIT_CONFIG_COUNT = '4';
        GIT_CONFIG_KEY_0 = 'coverage.targetRef';
        GIT_CONFIG_VALUE_0 = $TargetRef;
        GIT_CONFIG_KEY_1 = 'remote.origin.url';
        GIT_CONFIG_VALUE_1 = 'https://example.invalid/chimali.git';
        GIT_CONFIG_KEY_2 = 'remote.other.url';
        GIT_CONFIG_VALUE_2 = 'https://example.invalid/chimali.git';
        GIT_CONFIG_KEY_3 = 'remote.origin/team.url';
        GIT_CONFIG_VALUE_3 = 'https://team.invalid/chimali.git';
        CHIMALI_INTEGRATION_TARGET = $TargetRef;
        ANDROID_SERIAL = 'fixture-device-001';
        CHIMALI_EVIDENCE_ROOT = $evidenceRoot;
        CHIMALI_HARNESS_ROOT = $harnessRoot;
        CHIMALI_HARNESS_COMMIT = $stubHarnessCommit;
        CHIMALI_COVERAGE_EVIDENCE_REF = 'refs/heads/coverage/archives';
        CHIMALI_COVERAGE_REMOTE = 'origin';
        HOOK_STUB_LOG = $stubLog
        HOOK_STUB_EXIT = [string]$StubExit
    }
    foreach ($entry in $vars.GetEnumerator()) { $start.Environment[$entry.Key] = $entry.Value }
    if ($ClearVariable -eq 'CHIMALI_INTEGRATION_TARGET') {
        foreach ($name in @('GIT_CONFIG_COUNT','GIT_CONFIG_KEY_0','GIT_CONFIG_VALUE_0','GIT_CONFIG_KEY_1','GIT_CONFIG_VALUE_1','GIT_CONFIG_KEY_2','GIT_CONFIG_VALUE_2','GIT_CONFIG_KEY_3','GIT_CONFIG_VALUE_3')) { $start.Environment.Remove($name) | Out-Null }
        $start.Environment['CHIMALI_INTEGRATION_TARGET'] = ''
    }
    if ($ClearVariable -and $ClearVariable -ne 'CHIMALI_INTEGRATION_TARGET') { $start.Environment[$ClearVariable] = '' }
    if ($NoCoverageInputs) {
        foreach ($name in @('ANDROID_SERIAL','CHIMALI_EVIDENCE_ROOT','CHIMALI_HARNESS_ROOT')) { $start.Environment[$name] = '' }
    }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start hook process.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write($InputText)
    $process.StandardInput.Close()
    $process.WaitForExit()
    [PSCustomObject]@{ ExitCode = $process.ExitCode; Output = $stdoutTask.GetAwaiter().GetResult() + $stderrTask.GetAwaiter().GetResult() }
}

try {
    Remove-Item -LiteralPath $stubLog -Force -ErrorAction SilentlyContinue
    $mainRef = "refs/heads/feature/hook-fixture $candidateSha refs/heads/integration 0000000000000000000000000000000000000000`n"
    switch ($Case) {
        'HOOK_PRODUCT_REF_ROUTES_COMMIT' {
            $result = Invoke-Hook -InputText $mainRef
            if ($result.ExitCode -ne 0) { throw "Hook returned $($result.ExitCode): $($result.Output)" }
            $record = Get-Content -LiteralPath $stubLog -Raw | ConvertFrom-Json
            if ($record.CandidateRef -cne $candidateSha -or $record.RepositoryRoot -cne [System.IO.Path]::GetFullPath($RepositoryRoot) -or $record.TargetRef -cne 'origin/integration' -or $record.DeviceSerial -cne 'fixture-device-001') { throw 'Hook did not bind the configured candidate, repository, target, and device to integration-ci.' }
        }
        'HOOK_ARCHIVE_LIKE_PRODUCT_REF_ROUTES_COMMIT' {
            $line = "refs/heads/feature/coverage-evidence-hardening $candidateSha refs/heads/feature/coverage-evidence-hardening 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line -NoCoverageInputs
            if ($result.ExitCode -ne 0 -or (Test-Path -LiteralPath $stubLog)) { throw "Non-target feature ref unexpectedly required coverage: $($result.Output)" }
        }
        'HOOK_EXACT_ARCHIVE_REF_IS_EXEMPT' {
            $line = "refs/heads/archive $candidateSha refs/heads/coverage/archives 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line
            if ($result.ExitCode -ne 0 -or (Test-Path -LiteralPath $stubLog)) { throw 'Exact configured evidence ref was not the sole exempt update.' }
        }
        'HOOK_MISSING_CONFIG_BLOCKS' {
            $result = Invoke-Hook -InputText "malformed`n" -ClearVariable 'CHIMALI_INTEGRATION_TARGET'
            if ($result.ExitCode -ne 0 -or (Test-Path -LiteralPath $stubLog)) { throw "Unset target must return before parsing or invoking coverage: $($result.Output)" }
        }
        'HOOK_MISSING_DEVICE_BLOCKS' {
            $result = Invoke-Hook -InputText $mainRef -ClearVariable 'ANDROID_SERIAL'
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'deviceSerial') { throw "Missing explicit device did not fail closed: $($result.Output)" }
        }
        'HOOK_MISSING_EVIDENCE_ROOT_BLOCKS' {
            $result = Invoke-Hook -InputText $mainRef -ClearVariable 'CHIMALI_EVIDENCE_ROOT'
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'evidenceRoot') { throw "Missing evidence root did not fail closed: $($result.Output)" }
        }
        'HOOK_MISSING_HARNESS_BLOCKS' {
            $result = Invoke-Hook -InputText $mainRef -ClearVariable 'CHIMALI_HARNESS_ROOT'
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'harnessRoot') { throw "Missing trusted harness did not fail closed: $($result.Output)" }
        }
        'HOOK_MALFORMED_INPUT_BLOCKS' {
            $result = Invoke-Hook -InputText "refs/heads/bad $candidateSha refs/heads/bad`n"
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'Malformed pre-push update') { throw "Malformed input did not fail closed: $($result.Output)" }
        }
        'HOOK_MULTIPLE_REFS_EACH_ASSESSED' {
            $secondSha = (& git -C $RepositoryRoot rev-parse HEAD~1 2>$null)
            if ($LASTEXITCODE -ne 0 -or $secondSha -notmatch '^[0-9a-f]{40,64}$') { $secondSha = $candidateSha }
            $lines = "refs/heads/one $candidateSha refs/heads/integration 0000000000000000000000000000000000000000`nrefs/heads/two $secondSha refs/heads/other 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $lines
            $records = @(Get-Content -LiteralPath $stubLog | ForEach-Object { $_ | ConvertFrom-Json })
            if ($result.ExitCode -ne 0 -or $records.Count -ne 1 -or $records[0].CandidateRef -cne $candidateSha) { throw "Only the configured target update may invoke coverage. Hook output: $($result.Output)" }
        }
        'HOOK_EMPTY_STREAM_ALLOWED' {
            $result = Invoke-Hook -InputText '' -NoCoverageInputs
            if ($result.ExitCode -ne 0 -or (Test-Path -LiteralPath $stubLog)) { throw 'An empty update stream should return without coverage prerequisites.' }
        }
        'HOOK_OTHER_REMOTE_ALLOWED' {
            $result = Invoke-Hook -InputText $mainRef -Remote 'other' -ClearVariable 'CHIMALI_COVERAGE_REMOTE' -NoCoverageInputs
            if ($result.ExitCode -ne 0 -or (Test-Path -LiteralPath $stubLog)) { throw "A same-named ref on a different remote unexpectedly required coverage: $($result.Output)" }
        }
        'HOOK_SLASH_BRANCH_EXACT_MATCH' {
            $line = "refs/heads/feature/hook-fixture $candidateSha refs/heads/feature/integration 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line -TargetRef 'origin/feature/integration'
            if ($result.ExitCode -ne 0) { throw "Slash-containing configured target failed: $($result.Output)" }
            $record = Get-Content -LiteralPath $stubLog -Raw | ConvertFrom-Json
            if ($record.TargetRef -cne 'origin/feature/integration' -or $record.CandidateRef -cne $candidateSha) { throw 'Slash-containing target was not matched by its exact destination ref.' }
        }
        'HOOK_MATCH_FAILURE_ABORTS_PUSH' {
            $line = "refs/heads/feature/hook-fixture $candidateSha refs/heads/integration 0000000000000000000000000000000000000000`nrefs/heads/feature/other $candidateSha refs/heads/feature/other 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line -StubExit 7
            $records = @(Get-Content -LiteralPath $stubLog | ForEach-Object { $_ | ConvertFrom-Json })
            if ($result.ExitCode -ne 7 -or $records.Count -ne 1 -or $records[0].TargetRef -cne 'origin/integration' -or $result.Output -notmatch 'abort the whole push|exit 7') { throw "A failing matching update did not abort the complete push: $($result.Output)" }
        }
        'HOOK_TARGET_DELETION_BLOCKED' {
            $line = "(delete) 0000000000000000000000000000000000000000 refs/heads/integration $candidateSha`n"
            $result = Invoke-Hook -InputText $line
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'Deletion of configured\s+coverage\s+target') { throw "Configured target deletion was not blocked (exit $($result.ExitCode)): $($result.Output)" }
        }
        'HOOK_EVIDENCE_REF_CANNOT_EXEMPT_TARGET' {
            $line = "refs/heads/archive $candidateSha refs/heads/coverage/archives 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line -TargetRef 'origin/coverage/archives'
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'cannot exempt the configured product target') { throw "Archive exemption bypassed the matching product target: $($result.Output)" }
        }
        'HOOK_AMBIGUOUS_TARGET_REMOTE_BLOCKED' {
            $result = Invoke-Hook -InputText $mainRef -TargetRef 'origin/team/integration'
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'coverage.targetRef is ambiguous') { throw "Ambiguous target remote did not fail closed: $($result.Output)" }
        }
        default { throw "Unknown pre-push fixture case: $Case" }
    }
} finally {
    $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if (-not $canonicalTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or $canonicalTemp -eq $tempPrefix) { throw "Refusing to remove unexpected fixture path: $canonicalTemp" }
    Remove-Item -LiteralPath $canonicalTemp -Recurse -Force
}
