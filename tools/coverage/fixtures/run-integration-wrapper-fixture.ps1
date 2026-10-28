[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-integration-wrapper-' + [guid]::NewGuid().ToString('N'))
$tempRootFull = [System.IO.Path]::GetFullPath($tempRoot)
$tempRootPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$oldLocalLog = $env:COVERAGE_WRAPPER_TEST_LOCAL_LOG
$oldCaptureLog = $env:COVERAGE_WRAPPER_TEST_CAPTURE_LOG
$oldVerifyLog = $env:COVERAGE_WRAPPER_TEST_VERIFY_LOG
$oldVerifyExit = $env:COVERAGE_WRAPPER_TEST_VERIFY_EXIT
$oldSuccessfulCapture = $env:COVERAGE_WRAPPER_TEST_CAPTURE_SUCCESS
$oldMutateCandidate = $env:COVERAGE_WRAPPER_TEST_MUTATE_CANDIDATE
$oldCandidateRoot = $env:COVERAGE_WRAPPER_TEST_CANDIDATE_ROOT
$oldHarnessRoot = $env:COVERAGE_WRAPPER_TEST_HARNESS_ROOT
$oldRemotePath = $env:COVERAGE_WRAPPER_TEST_REMOTE_PATH
$oldTargetSha = $env:COVERAGE_WRAPPER_TEST_TARGET_SHA
$oldCandidateSha = $env:COVERAGE_WRAPPER_TEST_CANDIDATE_SHA

function Invoke-GitChecked {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = @(& git @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed: $($output -join ' ')" }
    return $output
}

function Write-TextFile {
    param([string]$Path, [string]$Content)
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function New-WrapperFixture {
    param([string]$FixtureRoot, [switch]$OmitLocalCi)

    $candidateRoot = Join-Path $FixtureRoot 'candidate'
    $harnessRoot = Join-Path $FixtureRoot 'harness'
    $callerRoot = Join-Path $FixtureRoot 'caller'
    $evidenceRoot = Join-Path $FixtureRoot 'evidence'
    $remotePath = Join-Path $FixtureRoot 'remote.git'
    foreach ($path in @($candidateRoot, $harnessRoot, $callerRoot, $evidenceRoot)) { New-Item -ItemType Directory -Path $path -Force | Out-Null }

    $policy = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'tools/coverage/policy.json') -Raw | ConvertFrom-Json
    foreach ($relative in @($policy.harness_requirements.required_files)) {
        if ($OmitLocalCi -and $relative -eq 'tools/local-ci.ps1') { continue }
        $destination = Join-Path $harnessRoot $relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        if ($relative -eq 'tools/local-ci.ps1') {
            Write-TextFile -Path $destination -Content @'
param([switch]$NoFix, [switch]$NoConfigurationCache)
$record = [PSCustomObject]@{ working_directory = (Get-Location).Path; no_fix = [bool]$NoFix; no_configuration_cache = [bool]$NoConfigurationCache }
$record | ConvertTo-Json -Compress | Set-Content -LiteralPath $env:COVERAGE_WRAPPER_TEST_LOCAL_LOG -Encoding utf8
exit 0
'@
        } elseif ($relative -eq 'tools/coverage/capture.ps1') {
            Write-TextFile -Path $destination -Content @'
param([string]$RepositoryRoot, [string]$SourceRevision, [string]$Purpose, [string]$TargetRevision, [string]$HarnessRoot, [string]$DeviceSerial, [string]$OutputRoot)
$record = [PSCustomObject]@{ repository_root = $RepositoryRoot; source_revision = $SourceRevision; purpose = $Purpose; target_revision = $TargetRevision; harness_root = $HarnessRoot; device_serial = $DeviceSerial; output_root = $OutputRoot; working_directory = (Get-Location).Path }
$record | ConvertTo-Json -Compress | Set-Content -LiteralPath $env:COVERAGE_WRAPPER_TEST_CAPTURE_LOG -Encoding utf8
if ($env:COVERAGE_WRAPPER_TEST_CAPTURE_SUCCESS -ne '1') { exit 2 }
. (Join-Path $PSScriptRoot 'coverage-common.ps1')
$policy = Read-SafeJson -Path (Join-Path $PSScriptRoot 'policy.json')
$hashes = [ordered]@{}
foreach ($relative in @($policy.harness_requirements.required_files)) { $hashes[$relative] = Get-FileSha256 -Path (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) $relative.Replace('/', [IO.Path]::DirectorySeparatorChar)) }
$toolchain = [PSCustomObject]@{ jacoco = 'jacoco-0.8.15'; gradle = '9.6'; android_gradle_plugin = '9.0.0'; kotlin = '2.2.0'; java = '25' }
$device = [PSCustomObject]@{ api_level = '35'; abi = 'arm64-v8a'; serial = $DeviceSerial }
$fingerprint = Get-MeasurementFingerprint -Policy $policy -Toolchain $toolchain -Device $device -HarnessFileDigests ([PSCustomObject]$hashes)
$runId = 'candidate-' + [guid]::NewGuid().ToString('N')
$runDir = Join-Path (Join-Path $OutputRoot 'runs') $runId
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
. (Join-Path $PSScriptRoot 'coverage-common.ps1')
$changedPaths = @(Get-GitChangedPaths -RepositoryRoot $RepositoryRoot -TargetRevision $TargetRevision -SourceRevision $SourceRevision)
$changedPathsFile = Join-Path $runDir 'changed-paths.txt'
Write-ChangedPathReceipt -Path $changedPathsFile -TargetCommit $TargetRevision -SourceCommit $SourceRevision -ChangedPaths $changedPaths
$manifest = [PSCustomObject]@{ run_id = $runId; policy_id = $policy.policy_id; git = [PSCustomObject]@{ source_commit = $SourceRevision; target_commit = $TargetRevision }; measurement_fingerprint = $fingerprint; toolchain = $toolchain; device = $device; harness = [PSCustomObject]@{ file_sha256 = [PSCustomObject]$hashes }; artifacts = @([PSCustomObject]@{ path = 'changed-paths.txt'; sha256 = Get-FileSha256 -Path $changedPathsFile }) }
$manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $runDir 'manifest.json') -Encoding utf8
Write-Output ('[CAPTURE_RESULT]' + (@{ run_id = $runId; run_dir = $runDir } | ConvertTo-Json -Compress))
'@
        } elseif ($relative -eq 'tools/coverage/verify.ps1') {
            Write-TextFile -Path $destination -Content @'
param([string]$RunBundle, [string]$PolicyPath, [string]$OutputDirectory, [string]$ComparisonBundle, [string]$AcceptedBaselineIndexPath, [string]$ChangedPathsFile)
$record = [PSCustomObject]@{ run_bundle = $RunBundle; policy_path = $PolicyPath; output_directory = $OutputDirectory; comparison_bundle = $ComparisonBundle; accepted_baseline_index_path = $AcceptedBaselineIndexPath; changed_paths_file = $ChangedPathsFile }
$record | ConvertTo-Json -Compress | Set-Content -LiteralPath $env:COVERAGE_WRAPPER_TEST_VERIFY_LOG -Encoding utf8
switch ($env:COVERAGE_WRAPPER_TEST_MUTATE_CANDIDATE) {
    'candidate-dirty' { Set-Content -LiteralPath (Join-Path $env:COVERAGE_WRAPPER_TEST_CANDIDATE_ROOT 'verification-race.txt') -Value 'changed during verification' }
    'harness-dirty' { Set-Content -LiteralPath (Join-Path $env:COVERAGE_WRAPPER_TEST_HARNESS_ROOT 'verification-race.txt') -Value 'changed during verification' }
    'target-advance' { & git --git-dir $env:COVERAGE_WRAPPER_TEST_REMOTE_PATH update-ref refs/heads/main $env:COVERAGE_WRAPPER_TEST_CANDIDATE_SHA; if ($LASTEXITCODE -ne 0) { exit 3 } }
}
exit [int]$env:COVERAGE_WRAPPER_TEST_VERIFY_EXIT
'@
        } else {
            $source = Join-Path $RepositoryRoot $relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
            Copy-Item -LiteralPath $source -Destination $destination -Force
        }
    }

    & git init --initial-branch=main $harnessRoot *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize harness fixture repository.' }
    Invoke-GitChecked -Arguments @('-C', $harnessRoot, 'config', 'user.name', 'Coverage Fixture') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $harnessRoot, 'config', 'user.email', 'coverage-fixture@example.invalid') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $harnessRoot, 'add', '--all') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $harnessRoot, 'commit', '-m', 'fixture harness') | Out-Null

    & git init --initial-branch=main $candidateRoot *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize candidate fixture repository.' }
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'config', 'user.name', 'Coverage Fixture') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'config', 'user.email', 'coverage-fixture@example.invalid') | Out-Null
    Write-TextFile -Path (Join-Path $candidateRoot 'base.txt') -Content 'target base'
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'add', '--all') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'commit', '-m', 'target') | Out-Null
    $target = ((Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'rev-parse', 'HEAD')) -join '').Trim()
    & git init --bare $remotePath *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize local bare remote fixture.' }
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'remote', 'add', 'fixture', $remotePath) | Out-Null
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'push', 'fixture', 'main') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'switch', '-c', 'feature') | Out-Null
    Write-TextFile -Path (Join-Path $candidateRoot 'feature.txt') -Content 'candidate change'
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'add', '--all') | Out-Null
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'commit', '-m', 'candidate') | Out-Null
    $candidate = ((Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'rev-parse', 'HEAD')) -join '').Trim()
    Invoke-GitChecked -Arguments @('-C', $candidateRoot, 'push', 'fixture', 'HEAD:refs/heads/fixture-feature') | Out-Null

    return [PSCustomObject]@{ Candidate = $candidateRoot; Harness = $harnessRoot; Caller = $callerRoot; Evidence = $evidenceRoot; Remote = $remotePath; Target = $target; CandidateSha = $candidate }
}

function Add-AcceptedBaseline {
    param($Fixture)
    . (Join-Path $RepositoryRoot 'tools/coverage/coverage-common.ps1')
    $policy = Read-SafeJson -Path (Join-Path $Fixture.Harness 'tools/coverage/policy.json')
    $hashes = [ordered]@{}
    foreach ($relative in @($policy.harness_requirements.required_files)) {
        $hashes[$relative] = Get-FileSha256 -Path (Join-Path $Fixture.Harness $relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    }
    $toolchain = [PSCustomObject]@{ jacoco = 'jacoco-0.8.15'; gradle = '9.6'; android_gradle_plugin = '9.0.0'; kotlin = '2.2.0'; java = '25' }
    $device = [PSCustomObject]@{ api_level = '35'; abi = 'arm64-v8a'; serial = 'fixture-device' }
    $fingerprint = Get-MeasurementFingerprint -Policy $policy -Toolchain $toolchain -Device $device -HarnessFileDigests ([PSCustomObject]$hashes)
    $baselineDir = Join-Path $Fixture.Evidence 'baselines/runs/baseline-run'
    New-Item -ItemType Directory -Path $baselineDir -Force | Out-Null
    $manifest = [PSCustomObject]@{ run_id = 'baseline-run'; policy_id = $policy.policy_id; git = [PSCustomObject]@{ source_commit = $Fixture.Target; target_commit = $Fixture.Target }; decisions = [PSCustomObject]@{ collection = 'Complete'; tests = 'Passed' }; suites = @([PSCustomObject]@{ suite_name = 'fixture'; required = $true; tests = 1; skipped = 0; failures = 0; errors = 0 }); measurement_fingerprint = $fingerprint; toolchain = $toolchain; device = $device; harness = [PSCustomObject]@{ file_sha256 = [PSCustomObject]$hashes } }
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $baselineDir 'manifest.json') -Encoding utf8
    $index = [PSCustomObject]@{ provenance = 'MeasuredAcceptedTarget'; target_commit = $Fixture.Target; run_id = 'baseline-run'; bundle_path = 'baselines/runs/baseline-run'; bundle_digest = Get-CoverageBundleDigest -BundlePath $baselineDir }
    $index | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $Fixture.Evidence 'baselines/current.json') -Encoding utf8
}

function Invoke-WrapperFixture {
    param($Fixture, [string]$FixtureRoot, [int]$VerifyExit = 0, [switch]$SuccessfulCapture, [switch]$MutateCandidateAfterVerify, [string]$Mutation = '')
    $pwsh = (Get-Command pwsh -CommandType Application | Select-Object -First 1).Source
    $env:COVERAGE_WRAPPER_TEST_LOCAL_LOG = Join-Path $FixtureRoot 'local-check.json'
    $env:COVERAGE_WRAPPER_TEST_CAPTURE_LOG = Join-Path $FixtureRoot 'capture-call.json'
    $env:COVERAGE_WRAPPER_TEST_VERIFY_LOG = Join-Path $FixtureRoot 'verify-call.json'
    $env:COVERAGE_WRAPPER_TEST_VERIFY_EXIT = [string]$VerifyExit
    $env:COVERAGE_WRAPPER_TEST_CAPTURE_SUCCESS = if ($SuccessfulCapture) { '1' } else { '0' }
    $env:COVERAGE_WRAPPER_TEST_CANDIDATE_ROOT = $Fixture.Candidate
    $env:COVERAGE_WRAPPER_TEST_HARNESS_ROOT = $Fixture.Harness
    $env:COVERAGE_WRAPPER_TEST_REMOTE_PATH = $Fixture.Remote
    $env:COVERAGE_WRAPPER_TEST_TARGET_SHA = $Fixture.Target
    $env:COVERAGE_WRAPPER_TEST_CANDIDATE_SHA = $Fixture.CandidateSha
    $env:COVERAGE_WRAPPER_TEST_MUTATE_CANDIDATE = if ($Mutation) { $Mutation } elseif ($MutateCandidateAfterVerify) { 'candidate-dirty' } else { '' }
    Push-Location $Fixture.Caller
    try {
        $output = @(& $pwsh -NoProfile -File (Join-Path $Fixture.Harness 'tools/integration-ci.ps1') `
            -RepositoryRoot $Fixture.Candidate -CandidateRef 'HEAD' -TargetRef 'main' -HarnessRoot $Fixture.Harness `
            -DeviceSerial 'fixture-device' -EvidenceRoot $Fixture.Evidence -RemoteName 'fixture' 2>&1)
        $code = $LASTEXITCODE
    } finally { Pop-Location }
    return [PSCustomObject]@{ Code = $code; Output = ($output -join "`n") }
}

try {
    New-Item -ItemType Directory -Path $tempRootFull -Force | Out-Null
    switch ($Case) {
        'CLEANUP_PREVIOUS' {
            $previousRoots = @(Get-ChildItem -LiteralPath $tempRootPrefix.TrimEnd([System.IO.Path]::DirectorySeparatorChar) -Directory -Filter 'coverage-integration-wrapper-*' -Force)
            foreach ($previous in $previousRoots) {
                $previousFull = [System.IO.Path]::GetFullPath($previous.FullName)
                if ([System.IO.Path]::GetDirectoryName($previousFull) -ne $tempRootPrefix.TrimEnd([System.IO.Path]::DirectorySeparatorChar) -or
                    [System.IO.Path]::GetFileName($previousFull) -notmatch '^coverage-integration-wrapper-[0-9a-f]{32}$') {
                    throw "Refusing to remove unverified prior fixture root: $previousFull"
                }
                Get-ChildItem -LiteralPath $previousFull -Recurse -File -Force | ForEach-Object { $_.Attributes = [System.IO.FileAttributes]::Normal }
                Remove-Item -LiteralPath $previousFull -Recurse -Force
            }
        }
        'INTEGRATION_WRAPPER_001_SEPARATE_ROOTS_AND_LOCAL_CHECK_ARGS' {
            $fixture = New-WrapperFixture -FixtureRoot $tempRootFull
            $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $tempRootFull
            if ($run.Code -ne 2) { throw "Expected capture's unavailable-evidence exit 2, got $($run.Code): $($run.Output)" }
            $local = Get-Content -LiteralPath (Join-Path $tempRootFull 'local-check.json') -Raw | ConvertFrom-Json
            $capture = Get-Content -LiteralPath (Join-Path $tempRootFull 'capture-call.json') -Raw | ConvertFrom-Json
            if ($local.working_directory -ne $fixture.Candidate -or -not $local.no_fix -or -not $local.no_configuration_cache) { throw 'Local checks did not run from the candidate root with both required switches.' }
            if ($capture.repository_root -ne $fixture.Candidate -or $capture.harness_root -ne $fixture.Harness -or $capture.working_directory -ne $fixture.Caller -or $capture.source_revision -ne $fixture.CandidateSha -or $capture.target_revision -ne $fixture.Target -or $capture.output_root -ne $fixture.Evidence) { throw "Capture arguments differed. Expected $($fixture | ConvertTo-Json -Compress); got $($capture | ConvertTo-Json -Compress)" }
        }
        'INTEGRATION_WRAPPER_002_MISSING_REQUIRED_LOCAL_CHECK' {
            $fixture = New-WrapperFixture -FixtureRoot $tempRootFull -OmitLocalCi
            $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $tempRootFull
            if ($run.Code -ne 3 -or $run.Output -notlike '*missing required enforcement file*local-ci.ps1*') { throw "Missing local-ci did not fail as invalid configuration: exit $($run.Code); $($run.Output)" }
        }
        'INTEGRATION_WRAPPER_003_DIRTY_HARNESS' {
            $fixture = New-WrapperFixture -FixtureRoot $tempRootFull
            Write-TextFile -Path (Join-Path $fixture.Harness 'untracked-dirty-marker.txt') -Content 'dirty'
            $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $tempRootFull
            if ($run.Code -ne 3 -or $run.Output -notlike '*clean Git checkout*') { throw "Dirty harness did not fail as invalid configuration: exit $($run.Code); $($run.Output)" }
        }
        'INTEGRATION_WRAPPER_004_STALE_LIVE_TARGET' {
            $fixture = New-WrapperFixture -FixtureRoot $tempRootFull
            Invoke-GitChecked -Arguments @('--git-dir', $fixture.Remote, 'update-ref', 'refs/heads/main', $fixture.CandidateSha) | Out-Null
            $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $tempRootFull
            if ($run.Code -ne 2 -or $run.Output -notlike '*stale or missing*') { throw "Stale live target did not fail with unavailable evidence: exit $($run.Code); $($run.Output)" }
            if (Test-Path -LiteralPath (Join-Path $tempRootFull 'local-check.json')) { throw 'Local checks ran after the live target was found stale.' }
        }
        'INTEGRATION_WRAPPER_005_DIRTY_CANDIDATE_REACHES_CAPTURE_FROM_HARNESS' {
            $fixture = New-WrapperFixture -FixtureRoot $tempRootFull
            Write-TextFile -Path (Join-Path $fixture.Candidate 'dirty-marker.txt') -Content 'dirty'
            $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $tempRootFull
            if ($run.Code -ne 2) { throw "Expected capture failure to propagate exit 2 for the dirty candidate, got $($run.Code): $($run.Output)" }
            $capture = Get-Content -LiteralPath (Join-Path $tempRootFull 'capture-call.json') -Raw | ConvertFrom-Json
            if ($capture.repository_root -ne $fixture.Candidate -or $capture.harness_root -ne $fixture.Harness) { throw 'Dirty candidate was not passed with the selected HarnessRoot.' }
        }
        'INTEGRATION_WRAPPER_006_VERIFIER_EXIT_CODE_PROPAGATION' {
            foreach ($verifyExit in @(0, 1, 2, 3)) {
                $caseRoot = Join-Path $tempRootFull "verify-$verifyExit"
                New-Item -ItemType Directory -Path $caseRoot -Force | Out-Null
                $fixture = New-WrapperFixture -FixtureRoot $caseRoot
                Add-AcceptedBaseline -Fixture $fixture
                $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $caseRoot -VerifyExit $verifyExit -SuccessfulCapture
                if ($run.Code -ne $verifyExit) { throw "Verifier exit $verifyExit was not propagated; got $($run.Code): $($run.Output)" }
                $verify = Get-Content -LiteralPath (Join-Path $caseRoot 'verify-call.json') -Raw | ConvertFrom-Json
                $candidateManifestPath = Get-ChildItem -LiteralPath (Join-Path $fixture.Evidence 'runs') -Filter manifest.json -Recurse -File | Select-Object -First 1 -ExpandProperty FullName
                $candidateManifest = Get-Content -LiteralPath $candidateManifestPath -Raw | ConvertFrom-Json
                if ($verify.comparison_bundle -ne (Join-Path $fixture.Evidence 'baselines/runs/baseline-run') -or $verify.accepted_baseline_index_path -ne (Join-Path $fixture.Evidence 'baselines/current.json') -or $verify.run_bundle -ne (Split-Path -Parent (Join-Path $fixture.Evidence "runs/$($candidateManifest.run_id)/manifest.json"))) { throw "Verifier did not receive the exact candidate and accepted baseline paths for exit $verifyExit." }
            }
        }
        'INTEGRATION_WRAPPER_007_REJECTS_CANDIDATE_DIRTYING_DURING_VERIFY' {
            foreach ($mutation in @('candidate-dirty', 'harness-dirty', 'target-advance')) {
                $caseRoot = Join-Path $tempRootFull $mutation
                New-Item -ItemType Directory -Path $caseRoot -Force | Out-Null
                $fixture = New-WrapperFixture -FixtureRoot $caseRoot
                Add-AcceptedBaseline -Fixture $fixture
                $run = Invoke-WrapperFixture -Fixture $fixture -FixtureRoot $caseRoot -SuccessfulCapture -Mutation $mutation
                if ($run.Code -ne 2) { throw "$mutation during verification was not rejected with exit 2: $($run.Output)" }
                if ($mutation -in @('candidate-dirty', 'harness-dirty') -and $run.Output -notlike '*became dirty or changed during integration verification*') { throw "$mutation did not report the final identity/cleanliness rejection: $($run.Output)" }
                if ($mutation -eq 'target-advance' -and $run.Output -notlike '*advanced during integration verification*') { throw 'Live target advancement did not report the final freshness rejection.' }
            }
        }
        default { throw "Unknown integration wrapper fixture case '$Case'" }
    }
    Write-Output "PASS $Case"
    exit 0
} catch {
    [Console]::Error.WriteLine("FAIL $Case`: $($_.Exception.Message)")
    exit 1
} finally {
    $env:COVERAGE_WRAPPER_TEST_LOCAL_LOG = $oldLocalLog
    $env:COVERAGE_WRAPPER_TEST_CAPTURE_LOG = $oldCaptureLog
    $env:COVERAGE_WRAPPER_TEST_VERIFY_LOG = $oldVerifyLog
    $env:COVERAGE_WRAPPER_TEST_VERIFY_EXIT = $oldVerifyExit
    $env:COVERAGE_WRAPPER_TEST_CAPTURE_SUCCESS = $oldSuccessfulCapture
    $env:COVERAGE_WRAPPER_TEST_MUTATE_CANDIDATE = $oldMutateCandidate
    $env:COVERAGE_WRAPPER_TEST_CANDIDATE_ROOT = $oldCandidateRoot
    $env:COVERAGE_WRAPPER_TEST_HARNESS_ROOT = $oldHarnessRoot
    $env:COVERAGE_WRAPPER_TEST_REMOTE_PATH = $oldRemotePath
    $env:COVERAGE_WRAPPER_TEST_TARGET_SHA = $oldTargetSha
    $env:COVERAGE_WRAPPER_TEST_CANDIDATE_SHA = $oldCandidateSha
    if ([System.IO.Directory]::Exists($tempRootFull)) {
        if (-not $tempRootFull.StartsWith($tempRootPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or [System.IO.Path]::GetFileName($tempRootFull) -notlike 'coverage-integration-wrapper-*') {
            throw "Refusing to remove unexpected integration fixture path: $tempRootFull"
        }
        Get-ChildItem -LiteralPath $tempRootFull -Recurse -File -Force | ForEach-Object { $_.Attributes = [System.IO.FileAttributes]::Normal }
        Remove-Item -LiteralPath $tempRootFull -Recurse -Force
    }
}
