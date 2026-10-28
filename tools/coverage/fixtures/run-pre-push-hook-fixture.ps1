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
exit 0
'@ | Set-Content -LiteralPath (Join-Path $harnessRoot 'tools/integration-ci.ps1') -Encoding utf8

function Invoke-Hook {
    param([string]$InputText, [string]$ClearVariable)
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Process -Id $PID).Path
    $start.ArgumentList.Add('-NoProfile')
    $start.ArgumentList.Add('-File')
    $start.ArgumentList.Add($hookPath)
    $start.WorkingDirectory = $unrelatedRoot
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $vars = @{
        CHIMALI_INTEGRATION_TARGET = 'refs/heads/integration';
        ANDROID_SERIAL = 'fixture-device-001';
        CHIMALI_EVIDENCE_ROOT = $evidenceRoot;
        CHIMALI_HARNESS_ROOT = $harnessRoot;
        CHIMALI_COVERAGE_EVIDENCE_REF = 'refs/heads/coverage/archives';
        CHIMALI_COVERAGE_REMOTE = 'origin';
        HOOK_STUB_LOG = $stubLog
    }
    foreach ($entry in $vars.GetEnumerator()) { $start.Environment[$entry.Key] = $entry.Value }
    if ($ClearVariable) { $start.Environment[$ClearVariable] = '' }
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
    $mainRef = "refs/heads/feature/hook-fixture $candidateSha refs/heads/feature/hook-fixture 0000000000000000000000000000000000000000`n"
    switch ($Case) {
        'HOOK_PRODUCT_REF_ROUTES_COMMIT' {
            $result = Invoke-Hook -InputText $mainRef
            if ($result.ExitCode -ne 0) { throw "Hook returned $($result.ExitCode): $($result.Output)" }
            $record = Get-Content -LiteralPath $stubLog -Raw | ConvertFrom-Json
            if ($record.CandidateRef -cne $candidateSha -or $record.RepositoryRoot -cne [System.IO.Path]::GetFullPath($RepositoryRoot) -or $record.TargetRef -cne 'refs/heads/integration' -or $record.DeviceSerial -cne 'fixture-device-001') { throw 'Hook did not bind the configured candidate, repository, target, and device to integration-ci.' }
        }
        'HOOK_ARCHIVE_LIKE_PRODUCT_REF_ROUTES_COMMIT' {
            $line = "refs/heads/feature/coverage-evidence-hardening $candidateSha refs/heads/feature/coverage-evidence-hardening 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line
            if ($result.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $stubLog)) { throw "Archive-like product ref was not assessed: $($result.Output)" }
        }
        'HOOK_EXACT_ARCHIVE_REF_IS_EXEMPT' {
            $line = "refs/heads/archive $candidateSha refs/heads/coverage/archives 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $line
            if ($result.ExitCode -ne 0 -or (Test-Path -LiteralPath $stubLog)) { throw 'Exact configured evidence ref was not the sole exempt update.' }
        }
        'HOOK_MISSING_CONFIG_BLOCKS' {
            $result = Invoke-Hook -InputText $mainRef -ClearVariable 'CHIMALI_INTEGRATION_TARGET'
            if ($result.ExitCode -ne 3 -or $result.Output -notmatch 'targetRef') { throw "Missing explicit target did not fail closed: $($result.Output)" }
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
            $lines = "refs/heads/one $candidateSha refs/heads/one 0000000000000000000000000000000000000000`nrefs/heads/two $secondSha refs/heads/two 0000000000000000000000000000000000000000`n"
            $result = Invoke-Hook -InputText $lines
            $records = @(Get-Content -LiteralPath $stubLog | ForEach-Object { $_ | ConvertFrom-Json })
            if ($result.ExitCode -ne 0 -or $records.Count -ne 2 -or $records[0].CandidateRef -cne $candidateSha -or $records[1].CandidateRef -cne $secondSha) { throw "Each pushed update must be assessed against its own SHA. Hook output: $($result.Output)" }
        }
        default { throw "Unknown pre-push fixture case: $Case" }
    }
} finally {
    $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if (-not $canonicalTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or $canonicalTemp -eq $tempPrefix) { throw "Refusing to remove unexpected fixture path: $canonicalTemp" }
    Remove-Item -LiteralPath $canonicalTemp -Recurse -Force
}
