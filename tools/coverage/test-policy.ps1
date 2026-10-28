<#
.SYNOPSIS
    Test harness for coverage policy, schema, floor, inventory, and lifecycle fixtures.
.DESCRIPTION
    Runs deterministic JSON fixtures under tools/coverage/fixtures/ using PowerShell 7
    and built-in .NET XML/JSON/BigInteger types without external dependencies.
#>
[CmdletBinding()]
param(
    [string]$FixturePattern = "*.json",
    [string]$SpecificCase = $null,
    [switch]$IncludeRuntimeFixtures,
    [string]$CurrentSchemaBundle,
    [string]$FixtureRoot
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-Error "Shared utility script not found: $CommonPath"
    exit 3
}

. $CommonPath

$FixturesDir = if ([string]::IsNullOrWhiteSpace($FixtureRoot)) { Join-Path $ScriptDir "fixtures" } else { [System.IO.Path]::GetFullPath($FixtureRoot) }
if (-not (Test-Path -LiteralPath $FixturesDir -PathType Container)) {
    Write-Error "FixtureRoot must be an existing directory: $FixturesDir"
    exit 2
}
$fixtureFiles = Get-ChildItem -LiteralPath $FixturesDir -Filter $FixturePattern -File | Where-Object {
    $_.Name -ne "package.json" -and $_.Name -ne "schema-fixture.json" -and
    ($IncludeRuntimeFixtures -or $_.Name -notin @('probe-compatibility.json', 'unmapped-production-module.json')) -and
    ($_.Name -ne 'production-verifier-entry.json' -or -not [string]::IsNullOrWhiteSpace($CurrentSchemaBundle))
}

if ($fixtureFiles.Count -eq 0) {
    Write-Error "No fixture files found matching '$FixturePattern' in $FixturesDir"
    exit 1
}

$totalPassed = 0
$totalFailed = 0
$results = @()

foreach ($file in $fixtureFiles) {
    Write-Host "Running fixtures in $($file.Name)..." -ForegroundColor Cyan
    $content = Get-Content $file.FullName -Raw | ConvertFrom-Json

    if (-not $content.cases) {
        Write-Host "  No cases defined in $($file.Name)" -ForegroundColor Yellow
        continue
    }

    foreach ($case in $content.cases) {
        if ($SpecificCase -and $case.id -ne $SpecificCase) {
            continue
        }

        $casePassed = $false
        $errorMsg = $null

        try {
            switch -Wildcard ($file.Name) {
                "junit-workload.json" {
                    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-junit-workload-' + [guid]::NewGuid().ToString('N'))
                    try {
                        $hostDir = Join-Path $tempRoot 'core/domain/build/test-results/testDebugUnitTest'
                        $deviceDir = Join-Path $tempRoot 'core/common/build/outputs/androidTest-results/connected/androidMain'
                        $staleDir = Join-Path $tempRoot 'feature/removed/build/test-results/testDebugUnitTest'
                        New-Item -ItemType Directory -Path $hostDir,$deviceDir,$staleDir -Force | Out-Null
                        Set-Content -LiteralPath (Join-Path $hostDir 'TEST-host.xml') -Value '<testsuite name="host" tests="1" />'
                        Set-Content -LiteralPath (Join-Path $deviceDir 'TEST-device.xml') -Value '<testsuite name="device" tests="1" />'
                        Set-Content -LiteralPath (Join-Path $staleDir 'TEST-stale.xml') -Value '<testsuite name="stale" tests="99" />'
                        $workload = [PSCustomObject]@{
                            host_test_tasks = @([PSCustomObject]@{ task_path = ':core:domain:testDebugUnitTest'; disposition = 'Applicable'; executed = $true; junit_xml_directory = 'core/domain/build/test-results/testDebugUnitTest' })
                            required_device_tasks = @([PSCustomObject]@{ task_path = ':core:common:connectedAndroidDeviceTest'; executed = $true; junit_xml_directory = 'core/common/build/outputs/androidTest-results/connected/androidMain' })
                            optional_device_tasks = @()
                        }
                        if ($case.mode -eq 'missing-file') { Remove-Item -LiteralPath (Join-Path $deviceDir 'TEST-device.xml') }
                        if ($case.mode -eq 'missing-directory') { Remove-Item -LiteralPath $deviceDir -Recurse -Force }
                        if ($case.mode -eq 'traversal') { $workload.required_device_tasks[0].junit_xml_directory = '../outside/test-results' }
                        $result = Get-TaskOwnedJUnitReports -RepositoryRoot $tempRoot -Workload $workload
                        if ($case.binding_mode) {
                            $artifactPaths = @(
                                'reports/tests/core/domain/build/test-results/testDebugUnitTest/TEST-host.xml',
                                'reports/tests/core/common/build/outputs/androidTest-results/connected/androidMain/TEST-device.xml'
                            )
                            if ($case.binding_mode -eq 'unowned') { $artifactPaths += 'reports/tests/feature/removed/build/test-results/testDebugUnitTest/TEST-stale.xml' }
                            if ($case.binding_mode -eq 'missing-device') { $artifactPaths = @($artifactPaths | Where-Object { $_ -notlike '*core/common/*' }) }
                            $binding = Test-WorkloadJUnitArtifactBinding -Workload $workload -ArtifactPaths $artifactPaths
                            if ($case.expected_valid -and $binding.IsValid) { $casePassed = $true }
                            elseif (-not $case.expected_valid -and -not $binding.IsValid -and ($binding.Errors | Where-Object { $_ -like "*$($case.expected_error)*" })) { $casePassed = $true }
                            else { $errorMsg = "JUnit artifact binding unexpected result; valid=$($binding.IsValid) errors=$($binding.Errors -join '; ')" }
                        } elseif ($case.mode -eq 'success') {
                            $names = @($result.Files | ForEach-Object { $_.Name } | Sort-Object)
                            if ($result.Errors.Count -eq 0 -and $names.Count -eq 2 -and 'TEST-stale.xml' -notin $names) { $casePassed = $true }
                            else { $errorMsg = "Expected only the two workload-owned reports; got files=$($names -join ',') errors=$($result.Errors -join '; ')" }
                        } elseif ($result.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }) { $casePassed = $true }
                        else { $errorMsg = "Expected an error matching '$($case.expected_error)', got: $($result.Errors -join '; ')" }
                    } finally {
                        if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
                    }
                }
                "artifact-download-binding.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-artifact-download-binding-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production artifact binding case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "unmapped-production-module.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-unmapped-production-module-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Runtime production inventory ownership case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "trusted-reporter-binding.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-trusted-reporter-binding-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production reporter-binding case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "report-final-binding.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-report-final-binding-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production final reporter-binding case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "report-live-conclusion.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-report-live-conclusion-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production live-conclusion case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "live-candidate-binding.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-live-candidate-binding-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production live-binding case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "integration-event-validation.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-integration-event-validation-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production integration-event case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "workflow-run-binding.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-workflow-run-binding-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production workflow identity case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "live-pr-target.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-live-pr-target-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline production live PR freshness case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "production-verifier-entry.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-verifier-counter-fixture.ps1'
                    $repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))
                    $fixtureOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $fixtureScript `
                        -RepositoryRoot $repositoryRoot -SourceBundle $CurrentSchemaBundle -Case ([string]$case.id) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Current-schema production verifier case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "test-policy-dispatcher.json" {
                    if ($case.id -eq 'DISPATCH_001_UNKNOWN_SUITE_FAILS') {
                        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-unknown-suite-' + [guid]::NewGuid().ToString('N'))
                        New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
                        try {
                            $unknownFixturePath = Join-Path $tempRoot 'unknown-suite.json'
                            @{ cases = @(@{ id = 'UNKNOWN_SUITE_001'; description = 'unknown test suite must not be skipped'; expected_valid = $true }) } |
                                ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $unknownFixturePath -Encoding utf8
                            $childOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $MyInvocation.MyCommand.Path `
                                -FixtureRoot $tempRoot -FixturePattern 'unknown-suite.json' 2>&1)
                            $childExit = $LASTEXITCODE
                            $childText = $childOutput -join ' '
                            if ($childExit -eq 1 -and $childText -match 'No handler registered for unknown-suite\.json' -and $childText -match '1 failed') {
                                $casePassed = $true
                            } else { $errorMsg = "Unknown fixture suite did not fail closed: exit=$childExit output=$childText" }
                        } finally {
                            $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
                            $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                            if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-unknown-suite-*') {
                                throw "Refusing unsafe unknown-suite fixture cleanup path: $canonicalTemp"
                            }
                            if (Test-Path -LiteralPath $canonicalTemp) { [System.IO.Directory]::Delete($canonicalTemp, $true) }
                        }
                    } elseif ($case.id -eq 'DISPATCH_002_ZERO_SELECTION_FAILS') {
                        $childOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $MyInvocation.MyCommand.Path `
                            -FixturePattern 'floors.json' -SpecificCase 'DISPATCH_NO_SUCH_CASE' 2>&1)
                        $childExit = $LASTEXITCODE
                        $childText = $childOutput -join ' '
                        if ($childExit -eq 1 -and $childText -match 'Zero test cases were selected or executed') { $casePassed = $true }
                        else { $errorMsg = "Zero-selected-case invocation did not fail closed: exit=$childExit output=$childText" }
                    } else {
                        $errorMsg = "Unknown dispatcher self-test '$($case.id)'"
                    }
                }
                "accepted-baseline-resolution.json" {
                    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-baseline-resolution-' + [guid]::NewGuid().ToString('N'))
                    $evidenceRoot = Join-Path $tempRoot 'evidence'
                    $baselineRoot = Join-Path $evidenceRoot 'runs/accepted'
                    $candidateRoot = Join-Path $tempRoot 'candidate'
                    $target = 'b' * 40
                    if ($case.mutation -eq 'target') { $target = 'c' * 40 }
                    New-Item -ItemType Directory -Path (Join-Path $evidenceRoot 'baselines'),$baselineRoot,$candidateRoot -Force | Out-Null
                    try {
                        $baselineRun = '00000000-0000-4000-8000-000000000901'
                        [System.IO.File]::WriteAllText((Join-Path $baselineRoot 'payload.txt'), 'accepted baseline fixture', [System.Text.UTF8Encoding]::new($false))
                        $baselineManifest = [PSCustomObject]@{ run_id = $baselineRun; purpose = 'Comparison'; git = [PSCustomObject]@{ source_commit = $target } }
                        $baselineManifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $baselineRoot 'manifest.json') -Encoding utf8
                        $bundleDigest = Get-CoverageBundleDigest -BundlePath $baselineRoot
                        $index = [PSCustomObject]@{ provenance = 'MeasuredAcceptedTarget'; target_commit = if ($case.mutation -eq 'target') { 'd' * 40 } else { $target }; run_id = $baselineRun; bundle_path = if ($case.mutation -eq 'traversal') { '../escape' } else { 'runs/accepted' }; bundle_digest = if ($case.mutation -eq 'digest') { 'f' * 64 } else { $bundleDigest } }
                        $indexPath = Join-Path (Join-Path $evidenceRoot 'baselines') 'current.json'
                        $index | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $indexPath -Encoding utf8
                        $changedPaths = Join-Path $candidateRoot 'changed-paths.txt'
                        [System.IO.File]::WriteAllText($changedPaths, "core/domain/src/commonMain/kotlin/Changed.kt`n", [System.Text.UTF8Encoding]::new($false))
                        $candidateArtifacts = if ($case.mutation -eq 'changed-paths') { @() } else { @([PSCustomObject]@{ path = 'changed-paths.txt'; sha256 = Get-FileSha256 -Path $changedPaths }) }
                        $candidateManifest = [PSCustomObject]@{ run_id = '00000000-0000-4000-8000-000000000902'; purpose = 'Candidate'; git = [PSCustomObject]@{ source_commit = ('a' * 40); target_commit = $target }; artifacts = $candidateArtifacts }
                        $candidateManifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $candidateRoot 'manifest.json') -Encoding utf8
                        $resolver = Join-Path $ScriptDir 'resolve-accepted-baseline.ps1'
                        $resolverOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $resolver -EvidenceRoot $evidenceRoot -CandidateBundle $candidateRoot -ExpectedTargetCommit $target 2>&1)
                        $resolverExit = $LASTEXITCODE
                        if ($case.expected_valid) {
                            $resolved = ($resolverOutput -join "`n") | ConvertFrom-Json -ErrorAction Stop
                            if ($resolverExit -eq 0 -and $resolved.run_id -ceq $baselineRun -and $resolved.target_commit -ceq $target -and
                                [System.IO.Path]::GetFullPath($resolved.comparison_bundle) -ceq [System.IO.Path]::GetFullPath($baselineRoot) -and
                                [System.IO.Path]::GetFullPath($resolved.accepted_baseline_index) -ceq [System.IO.Path]::GetFullPath($indexPath) -and
                                [System.IO.Path]::GetFullPath($resolved.changed_paths) -ceq [System.IO.Path]::GetFullPath($changedPaths)) { $casePassed = $true }
                            else { $errorMsg = "Valid baseline resolution returned unexpected output: exit=$resolverExit output=$($resolverOutput -join ' ')" }
                        } elseif ($resolverExit -eq 2 -and ($resolverOutput -join ' ') -match [regex]::Escape([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected resolver rejection '$($case.expected_error)'; exit=$resolverExit output=$($resolverOutput -join ' ')" }
                    } finally {
                        if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
                    }
                }
                "integration-wrapper.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-integration-wrapper-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline integration wrapper case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "report-lifecycle.json" {
                    if ($case.id -eq 'REPORT_002_FAILED_VERIFICATION_REMAINS_UNVERIFIED') {
                        $fixtureScript = Join-Path $FixturesDir 'run-report-lifecycle-fixture.ps1'
                        $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                        $fixtureExit = $LASTEXITCODE
                        if ($fixtureExit -eq 0) { $casePassed = $true }
                        else { $errorMsg = "Production reporter lifecycle case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                        break
                    }
                    if ($case.id -eq 'REPORT_003_STALE_LIVE_TARGET_PUBLISHES_FAILURE') {
                        $fixtureScript = Join-Path $FixturesDir 'run-report-stale-live-fixture.ps1'
                        $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                        $fixtureExit = $LASTEXITCODE
                        if ($fixtureExit -eq 0) { $casePassed = $true }
                        else { $errorMsg = "Production reporter stale-target case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                        break
                    }
                    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-report-lifecycle-' + [guid]::NewGuid().ToString('N'))
                    $bundle = Join-Path $tempRoot 'run'
                    $runId = '00000000-0000-4000-8000-000000000123'
                    $assessmentDir = Join-Path (Join-Path $tempRoot 'assessments') $runId
                    New-Item -ItemType Directory -Path $bundle -Force | Out-Null
                    New-Item -ItemType Directory -Path $assessmentDir -Force | Out-Null
                    try {
                        $head = 'a' * 40
                        $base = 'b' * 40
                        $manifest = [PSCustomObject]@{ run_id = $runId; purpose = 'Candidate'; git = [PSCustomObject]@{ source_commit = $head; target_commit = $base } }
                        $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
                        $decisionPath = Join-Path $assessmentDir 'decision.json'
                        $staleDecision = '{"overall_passed":true,"run_id":"stale"}'
                        [System.IO.File]::WriteAllText($decisionPath, $staleDecision, [System.Text.UTF8Encoding]::new($false))
                        $reportScript = Join-Path $ScriptDir 'report.ps1'
                        $reportOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $reportScript -RunBundle $bundle -Repository 'octaviordz/chimali' -HeadSha $head -BaseSha $base -DryRun 2>&1)
                        $reportExit = $LASTEXITCODE
                        $retainedDecision = [System.IO.File]::ReadAllText($decisionPath)
                        if ($reportExit -eq 3 -and $retainedDecision -ceq $staleDecision -and (($reportOutput -join ' ') -match 'Refusing to reuse an existing assessment')) { $casePassed = $true }
                        else { $errorMsg = "Expected stale assessment rejection with exit 3 and no overwrite; exit=$reportExit, output=$($reportOutput -join ' '), decision=$retainedDecision" }
                    } finally {
                        $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
                        $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                        if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-report-lifecycle-*') { throw "Refusing to remove unexpected report fixture path: $canonicalTemp" }
                        [System.IO.Directory]::Delete($canonicalTemp, $true)
                    }
                }
                "publisher-lifecycle.json" {
                    if ($case.id -eq 'PUBLISH_001_FORGED_PASSING_ASSESSMENT_REJECTED') {
                        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-publisher-lifecycle-' + [guid]::NewGuid().ToString('N'))
                        $bundle = Join-Path $tempRoot 'run'
                        $baseline = Join-Path $tempRoot 'baseline'
                        New-Item -ItemType Directory -Path $bundle -Force | Out-Null
                        New-Item -ItemType Directory -Path $baseline -Force | Out-Null
                        try {
                        $runId = '00000000-0000-4000-8000-000000000321'
                        $head = 'a' * 40; $base = 'b' * 40; $tree = 'c' * 40
                        $manifest = [PSCustomObject]@{ run_id = $runId; purpose = 'Candidate'; git = [PSCustomObject]@{ source_commit = $head; target_commit = $base; source_tree = $tree } }
                        $manifestPath = Join-Path $bundle 'manifest.json'
                        $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8
                        $manifestDigest = Get-FileSha256 -Path $manifestPath
                        $changedPathsPath = Join-Path $bundle 'changed-paths.txt'
                        [System.IO.File]::WriteAllText($changedPathsPath, '', [System.Text.UTF8Encoding]::new($false))
                        $assessmentPath = Join-Path $tempRoot 'forged-decision.json'
                        $assessment = [PSCustomObject]@{ run_id = $runId; overall_passed = $true; manifest_digest = $manifestDigest; decisions = [PSCustomObject]@{ collection = 'Complete'; tests = 'Passed'; floors = 'Passed'; comparison_compatibility = 'Compatible'; regression = 'Passed' } }
                        $assessment | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $assessmentPath -Encoding utf8
                        $eventPath = Join-Path $tempRoot 'forged-integration-event.json'
                        $event = [PSCustomObject]@{ event_type = 'pull_request'; merged = $true; repository = 'octaviordz/chimali'; head_sha = $head; base_sha = $base; integration_commit = ('d' * 40); integration_tree = $tree }
                        $event | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $eventPath -Encoding utf8
                        $indexPath = Join-Path $tempRoot 'current.json'
                        '{}' | Set-Content -LiteralPath $indexPath -Encoding utf8
                        $receiptPath = Join-Path $tempRoot 'publication-receipt.json'
                        $publishScript = Join-Path $ScriptDir 'publish.ps1'
                        $publishOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $publishScript -RunBundle $bundle -OutputReceiptPath $receiptPath -PromoteAsAcceptedTarget -IntegratedCommit ('d' * 40) -IntegratedTree $tree -AssessmentPath $assessmentPath -IntegrationEventPath $eventPath -ExpectedRepository 'octaviordz/chimali' -ExpectedHeadSha $head -ExpectedBaseSha $base -ComparisonBundle $baseline -AcceptedBaselineIndexPath $indexPath -ChangedPathsFile $changedPathsPath 2>&1)
                        $publishExit = $LASTEXITCODE
                        $outputText = $publishOutput -join ' '
                        $failureReceipt = if (Test-Path -LiteralPath $receiptPath -PathType Leaf) { Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json } else { $null }
                        $expectedBundleDigest = Get-CoverageBundleDigest -BundlePath $bundle
                        if ($publishExit -ne 0 -and $outputText -match 'Independent trusted verification did not pass' -and
                            $outputText -notmatch 'CHIMALI_COVERAGE_PUBLISH_TOKEN is required' -and
                            $failureReceipt.status -ceq 'Failed' -and $failureReceipt.retrievable -eq $false -and
                            $failureReceipt.run_id -ceq $runId -and $failureReceipt.manifest_digest -ceq $manifestDigest -and
                            $failureReceipt.bundle_digest -ceq $expectedBundleDigest -and
                            $failureReceipt.verification_decision.run_id -ceq $runId -and $failureReceipt.verification_decision.overall_passed -eq $false -and
                            $failureReceipt.verification_exit_code -ne 0) { $casePassed = $true }
                        else { $errorMsg = "Expected real trusted-verifier rejection to retain a Failed receipt bound to run/manifest/bundle; exit=$publishExit, receipt=$($failureReceipt | ConvertTo-Json -Compress -Depth 5), output=$outputText" }
                        } finally {
                            $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
                            $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                            if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-publisher-lifecycle-*') { throw "Refusing to remove unexpected publisher fixture path: $canonicalTemp" }
                            [System.IO.Directory]::Delete($canonicalTemp, $true)
                        }
                    } elseif ($case.id -eq 'PUBLISH_002_LOCAL_ARCHIVE_READBACK_SUCCESS') {
                        $fixtureScript = Join-Path $FixturesDir 'run-publisher-local-fixture.ps1'
                        $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                        $fixtureExit = $LASTEXITCODE
                        if ($fixtureExit -eq 0) { $casePassed = $true }
                        else { $errorMsg = "Local publisher production fixture failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                    } elseif ($case.id -in @('PUBLISH_004_ARCHIVE_PUSH_FAILURE_REMAINS_FAILED', 'PUBLISH_005_REMOTE_READBACK_FAILURE_REMAINS_FAILED', 'PUBLISH_006_REMOTE_UNAVAILABLE_RETAINS_FAILURE', 'PUBLISH_007_ARCHIVE_COMMIT_FAILURE_RETAINS_FAILURE')) {
                        $fixtureScript = Join-Path $FixturesDir 'run-publisher-local-fixture.ps1'
                        $mode = switch ($case.id) {
                            'PUBLISH_004_ARCHIVE_PUSH_FAILURE_REMAINS_FAILED' { 'push-failure' }
                            'PUBLISH_005_REMOTE_READBACK_FAILURE_REMAINS_FAILED' { 'readback-failure' }
                            'PUBLISH_006_REMOTE_UNAVAILABLE_RETAINS_FAILURE' { 'remote-unavailable' }
                            'PUBLISH_007_ARCHIVE_COMMIT_FAILURE_RETAINS_FAILURE' { 'commit-failure' }
                        }
                        $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) -Mode $mode 2>&1)
                        $fixtureExit = $LASTEXITCODE
                        if ($fixtureExit -eq 0) { $casePassed = $true }
                        else { $errorMsg = "Production publisher failure lifecycle ($mode) failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                    } elseif ($case.id -in @('PUBLISH_008_VALID_PROMOTION_ADVANCES_INDEX', 'PUBLISH_009_UNSUPPORTED_EVENT_REJECTED_BEFORE_ARCHIVE', 'PUBLISH_010_STALE_BASE_REJECTED_BEFORE_ARCHIVE')) {
                        $fixtureScript = Join-Path $FixturesDir 'run-publisher-promotion-fixture.ps1'
                        $mode = switch ($case.id) {
                            'PUBLISH_008_VALID_PROMOTION_ADVANCES_INDEX' { 'success' }
                            'PUBLISH_009_UNSUPPORTED_EVENT_REJECTED_BEFORE_ARCHIVE' { 'unsupported-event' }
                            'PUBLISH_010_STALE_BASE_REJECTED_BEFORE_ARCHIVE' { 'stale-base' }
                        }
                        $fixtureOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) -Mode $mode 2>&1)
                        $fixtureExit = $LASTEXITCODE
                        if ($fixtureExit -eq 0) { $casePassed = $true }
                        else { $errorMsg = "Production promotion lifecycle ($mode) failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                    } elseif ($case.id -eq 'PUBLISH_003_INCOMPLETE_PROMOTION_INPUTS_REJECTED') {
                        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-publisher-promotion-inputs-' + [guid]::NewGuid().ToString('N'))
                        $bundle = Join-Path $tempRoot 'run'
                        $receiptPath = Join-Path $tempRoot 'publication-receipt.json'
                        New-Item -ItemType Directory -Path $bundle -Force | Out-Null
                        try {
                            $manifest = [PSCustomObject]@{ run_id = '00000000-0000-4000-8000-000000000322'; purpose = 'Candidate'; git = [PSCustomObject]@{ source_commit = ('a' * 40); source_tree = ('b' * 40) } }
                            $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
                            $publishOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ScriptDir 'publish.ps1') -RunBundle $bundle -OutputReceiptPath $receiptPath -PromoteAsAcceptedTarget 2>&1)
                            $publishExit = $LASTEXITCODE
                            $outputText = $publishOutput -join ' '
                            if ($publishExit -eq 1 -and $outputText -match 'Promotion requires an assessment' -and -not (Test-Path -LiteralPath $receiptPath)) { $casePassed = $true }
                            else { $errorMsg = "Expected incomplete promotion request to fail before creating a receipt; exit=$publishExit, receiptExists=$(Test-Path -LiteralPath $receiptPath), output=$outputText" }
                        } finally {
                            $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
                            $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                            if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-publisher-promotion-inputs-*') { throw "Refusing unsafe publisher fixture cleanup target: $canonicalTemp" }
                            if (Test-Path -LiteralPath $canonicalTemp) { [System.IO.Directory]::Delete($canonicalTemp, $true) }
                        }
                    } else {
                        $errorMsg = "No production fixture implementation for publisher case '$($case.id)'"
                    }
                }
                "pre-push-hook.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-pre-push-hook-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.id) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Offline pre-push hook case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "report-counters.json" {
                    try {
                        $xml = [System.Xml.XmlDocument]::new()
                        $xml.LoadXml([string]$case.xml)
                        $metrics = Get-ReportLevelCoverageCounters -XmlDocument $xml -Scope 'Fixture' -RequireNonzeroInstructions
                        if ($case.expected_error) { $errorMsg = "Expected report error '$($case.expected_error)' but parsing succeeded" }
                        elseif ($metrics.instruction.covered.ToString() -ceq [string]$case.expected_instruction_covered -and
                            $metrics.instruction.missed.ToString() -ceq [string]$case.expected_instruction_missed -and
                            $metrics.branch.covered.ToString() -ceq [string]$case.expected_branch_covered -and
                            $metrics.branch.missed.ToString() -ceq [string]$case.expected_branch_missed) { $casePassed = $true }
                        else { $errorMsg = 'Parsed report counters did not match expected exact values' }
                    } catch {
                        if ($case.expected_error -and $_.Exception.Message -like "*$($case.expected_error)*") { $casePassed = $true }
                        else { $errorMsg = "Unexpected report error: $($_.Exception.Message)" }
                    }
                }
                "module-report-evidence.json" {
                    try {
                        $xml = [System.Xml.XmlDocument]::new()
                        $xml.LoadXml([string]$case.xml)
                        $inventoryModule = [PSCustomObject]@{ module_path = ':core:common'; retained_classes = @([PSCustomObject]@{ class_id = 'fixture.Class'; sha256 = ('b' * 64) }); class_inventory_digest = '' }
                        $inventoryModule.class_inventory_digest = (Get-ProductionClassInventoryDigest -InventoryModule $inventoryModule).Digest
                        $counterModule = [PSCustomObject]@{
                            module_path = ':core:common'; report_digest = ('a' * 64); class_inventory_digest = $inventoryModule.class_inventory_digest
                            instruction = [PSCustomObject]@{ covered = 60; missed = 40; total = 100 }
                            branch = [PSCustomObject]@{ covered = 7; missed = 3; total = 10 }
                        }
                        $evidence = Test-ModuleReportCounterEvidence -XmlDocument $xml -InventoryModule $inventoryModule -CounterModule $counterModule `
                            -InventoryDigest ('c' * 64) -ExpectedInventoryDigest ('c' * 64) -AnalyzerIdentity 'jacoco-0.8.15' -ReportDigest ('a' * 64) -Label 'Fixture'
                        if ($evidence.IsValid -eq [bool]$case.expected_valid) {
                            if (-not $case.expected_error -or (($evidence.Errors -join '; ').Contains([string]$case.expected_error))) { $casePassed = $true }
                            else { $errorMsg = "Expected report evidence error '$($case.expected_error)', got $($evidence.Errors -join '; ')" }
                        } else { $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($evidence.IsValid): $($evidence.Errors -join '; ')" }
                    } catch { $errorMsg = "Unexpected module report evidence exception: $_" }
                }
                "big-integers.json" {
                    try {
                        $actual = ConvertTo-CoverageBigInteger -Value $case.value -Label $case.label
                        if ($case.expected_error) { $errorMsg = "Expected conversion error '$($case.expected_error)' but conversion succeeded" }
                        elseif ($actual.ToString([Globalization.CultureInfo]::InvariantCulture) -ceq [string]$case.expected_value) { $casePassed = $true }
                        else { $errorMsg = "Expected $($case.expected_value), got $actual" }
                    } catch {
                        if ($case.expected_error -and $_.Exception.Message -like "*$($case.expected_error)*") { $casePassed = $true }
                        else { $errorMsg = "Unexpected conversion error: $($_.Exception.Message)" }
                    }
                }
                "json-strictness.json" {
                    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-json-strictness-' + [guid]::NewGuid().ToString('N'))
                    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
                    try {
                        $jsonPath = Join-Path $tempRoot 'input.json'
                        [System.IO.File]::WriteAllText($jsonPath, [string]$case.raw_json, [System.Text.UTF8Encoding]::new($false))
                        try {
                            $parsedJson = Read-SafeJson -Path $jsonPath
                            if ($case.expected_error) {
                                $errorMsg = "Expected strict JSON rejection '$($case.expected_error)', but parsing succeeded"
                            } elseif ([string]$parsedJson.value -ceq [string]$case.expected_value) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Expected parsed value '$($case.expected_value)', got '$($parsedJson.value)'"
                            }
                        } catch {
                            if ($case.expected_error -and $_.Exception.Message -like "*$($case.expected_error)*") { $casePassed = $true }
                            else { $errorMsg = "Unexpected strict JSON error: $($_.Exception.Message)" }
                        }
                    } finally {
                        $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
                        $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                        if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-json-strictness-*') { throw "Refusing to remove unexpected JSON fixture path: $canonicalTemp" }
                        [System.IO.Directory]::Delete($canonicalTemp, $true)
                    }
                }
                "device-capabilities.json" {
                    $capabilityEval = Test-DeviceCapabilities -Policy $case.policy -ApiLevel ([string]$case.api_level) -Abi ([string]$case.abi)
                    if ($case.expected_valid) {
                        if ($capabilityEval.IsValid) { $casePassed = $true }
                        else { $errorMsg = "Expected supported device, got errors: $($capabilityEval.Errors -join '; ')" }
                    } elseif (-not $capabilityEval.IsValid) {
                        if ($case.expected_error) {
                            $matched = $capabilityEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) { $casePassed = $true }
                            else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($capabilityEval.Errors -join '; ')" }
                        } else { $casePassed = $true }
                    } else { $errorMsg = 'Expected unsupported device, but capability validation succeeded' }
                }
                "schema.json" {
                    $coveragePolicy = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                    if ($case.junit_mutation) {
                        $validTemplate = @($content.cases | Where-Object { $_.id -eq 'SCHEMA_001_VALID_INITIAL' })[0].manifest
                        $manifestToValidate = $validTemplate | ConvertTo-Json -Depth 100 | ConvertFrom-Json
                    } else {
                        $manifestToValidate = $case.manifest | ConvertTo-Json -Depth 100 | ConvertFrom-Json
                    }
                    $trustedHarnessRoot = $null
                    if ($case.collection_error_mutation) {
                        $validTemplate = @($content.cases | Where-Object { $_.id -eq 'SCHEMA_001_VALID_INITIAL' })[0].manifest
                        $manifestToValidate = $validTemplate | ConvertTo-Json -Depth 100 | ConvertFrom-Json
                        $manifestToValidate.decisions.collection = 'Incomplete'
                        switch ([string]$case.collection_error_mutation) {
                            'missing' { $manifestToValidate.PSObject.Properties.Remove('collection_errors') }
                            'empty' { $manifestToValidate | Add-Member -Force -NotePropertyName collection_errors -NotePropertyValue @() }
                            'blank' { $manifestToValidate | Add-Member -Force -NotePropertyName collection_errors -NotePropertyValue @('  ') }
                            'wrong-type' { $manifestToValidate | Add-Member -Force -NotePropertyName collection_errors -NotePropertyValue 'a scalar error' }
                            'valid' { $manifestToValidate | Add-Member -Force -NotePropertyName collection_errors -NotePropertyValue @('Repository report counters disagree with canonical JaCoCo analysis') }
                        }
                    } elseif ($case.verify_trusted_harness) {
                        $validTemplate = @($content.cases | Where-Object { $_.id -eq 'SCHEMA_001_VALID_INITIAL' })[0].manifest
                        $manifestToValidate = $validTemplate | ConvertTo-Json -Depth 100 | ConvertFrom-Json
                        $trustedHarnessRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))
                        $actualHarnessHashes = [ordered]@{}
                        foreach ($harnessPath in @($coveragePolicy.harness_requirements.required_files)) {
                            $fullHarnessPath = Join-Path $trustedHarnessRoot $harnessPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
                            $actualHarnessHashes[$harnessPath] = Get-FileSha256 -Path $fullHarnessPath
                        }
                        $manifestToValidate.harness.file_sha256 = [PSCustomObject]$actualHarnessHashes
                        $manifestToValidate.measurement_fingerprint = Get-MeasurementFingerprint -Policy $coveragePolicy -Toolchain $manifestToValidate.toolchain -Device $manifestToValidate.device -HarnessFileDigests $manifestToValidate.harness.file_sha256
                        if ($case.tamper_trusted_harness) {
                            $firstHarnessPath = [string]$coveragePolicy.harness_requirements.required_files[0]
                            $manifestToValidate.harness.file_sha256.$firstHarnessPath = '0000000000000000000000000000000000000000000000000000000000000000'
                        }
                    }
                    if ($manifestToValidate.workload) {
                        $junitDirectoryByTask = @{
                            ':feature:fido2' = 'feature/fido2/build/outputs/androidTest-results/connected/androidMain'
                            ':core:common' = 'core/common/build/outputs/androidTest-results/connected/androidMain'
                            ':feature:settings' = 'feature/settings/build/outputs/androidTest-results/connected/debug'
                        }
                        foreach ($hostTask in $manifestToValidate.workload.host_test_tasks) {
                            $hostTask | Add-Member -Force -NotePropertyName junit_xml_directory -NotePropertyValue 'core/domain/build/test-results/testAndroidHostTest'
                        }
                        foreach ($deviceTask in $manifestToValidate.workload.required_device_tasks) {
                            $deviceTask | Add-Member -Force -NotePropertyName junit_xml_directory -NotePropertyValue $junitDirectoryByTask[[string]$deviceTask.module_path]
                        }
                        foreach ($deviceTask in $manifestToValidate.workload.optional_device_tasks) {
                            $deviceTask | Add-Member -Force -NotePropertyName junit_xml_directory -NotePropertyValue 'app/build/outputs/androidTest-results/connected/shrunkDebug'
                        }
                        $hostJUnitArtifact = 'reports/tests/core/domain/build/test-results/testAndroidHostTest/TEST-fixture.xml'
                        if ($null -ne $manifestToValidate.artifacts) {
                            $manifestToValidate.artifacts = @($manifestToValidate.artifacts | Where-Object { $_.path -notlike 'reports/tests/*.xml' })
                            foreach ($directory in @($junitDirectoryByTask.Values) + @('core/domain/build/test-results/testAndroidHostTest')) {
                                $manifestToValidate.artifacts += [PSCustomObject]@{ path = "reports/tests/$directory/TEST-fixture.xml"; sha256 = ('f' * 64) }
                            }
                        }
                        foreach ($evidenceRecord in $manifestToValidate.evidence_records) {
                            if ([string]$evidenceRecord.artifact_link -like 'reports/tests/*') { $evidenceRecord.artifact_link = $hostJUnitArtifact }
                        }
                    }
                    switch ([string]$case.junit_mutation) {
                        'missing-host-directory' { $manifestToValidate.workload.host_test_tasks[0].PSObject.Properties.Remove('junit_xml_directory') }
                        'unowned-report' { $manifestToValidate.artifacts += [PSCustomObject]@{ path = 'reports/tests/feature/removed/build/test-results/testDebugUnitTest/TEST-stale.xml'; sha256 = ('e' * 64) } }
                        'missing-device-report' { $manifestToValidate.artifacts = @($manifestToValidate.artifacts | Where-Object { $_.path -notlike 'reports/tests/core/common/build/outputs/androidTest-results/*' }) }
                    }
                    if ($case.expected_valid -and $manifestToValidate.harness.file_sha256) {
                        $manifestToValidate.measurement_fingerprint = Get-MeasurementFingerprint -Policy $coveragePolicy -Toolchain $manifestToValidate.toolchain -Device $manifestToValidate.device -HarnessFileDigests $manifestToValidate.harness.file_sha256
                    }
                    $validation = Test-ManifestSchema -Manifest $manifestToValidate -Policy $coveragePolicy -TrustedHarnessRoot $trustedHarnessRoot
                    if ($case.expected_valid) {
                        if ($validation.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid, but got errors: $($validation.Errors -join '; ')"
                        }
                    } else {
                        if (-not $validation.IsValid) {
                            if ($case.expected_error) {
                                $matched = $validation.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', but got: $($validation.Errors -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid manifest, but validation succeeded"
                        }
                    }
                }
                "floors.json" {
                    try {
                        $eval = if ($case.counters.repository) {
                            Test-ExactFloorRatio -Covered $case.counters.repository.covered -Missed $case.counters.repository.missed -MinimumPercent 50
                        } else {
                            Test-CoverageFloors -Counters $case.counters -Policy $case.policy
                        }
                        if ($eval.Passed -eq $case.expected_pass) { $casePassed = $true }
                        else { $errorMsg = "Expected pass=$($case.expected_pass), got pass=$($eval.Passed). Reasons: $($eval.Reasons -join '; ')" }
                    } catch {
                        if ($case.expected_error -and $_.Exception.Message -like "*$($case.expected_error)*") { $casePassed = $true }
                        else { $errorMsg = "Unexpected counter validation error: $($_.Exception.Message)" }
                    }
                }
                "module-accounting.json" {
                    $moduleRecords = @($case.modules | ForEach-Object {
                        [PSCustomObject]@{
                            module_path = [string]$_.module_path
                            disposition = [string]$_.disposition
                            report_digest = [string]$_.report_digest
                            class_inventory_digest = [string]$_.class_inventory_digest
                            instruction = [PSCustomObject]@{ covered = $_.instruction.covered; missed = $_.instruction.missed; total = $_.instruction.total }
                            branch = [PSCustomObject]@{ covered = $_.branch.covered; missed = $_.branch.missed; total = $_.branch.total }
                        }
                    })
                    $validation = Test-ComparisonModuleAccounting -Modules $moduleRecords -RequiredModulePaths @([string]$case.required_module) -Label 'Fixture'
                    if ($case.expected_valid) {
                        if ($validation.IsValid) { $casePassed = $true }
                        else { $errorMsg = "Expected valid module accounting, got: $($validation.Errors -join '; ')" }
                    } elseif (-not $validation.IsValid) {
                        if ($case.expected_error -and @($validation.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }).Count -eq 0) {
                            $errorMsg = "Expected error matching '$($case.expected_error)', got: $($validation.Errors -join '; ')"
                        } else { $casePassed = $true }
                    } else { $errorMsg = 'Expected invalid module accounting, but validation succeeded' }
                }
                "inventory.json" {
                    $invEval = Test-InventoryAccounting -Inventory $case.inventory -XmlData $case.xml_file -CanonicalProof $case.proof -Case $case
                    if ($case.expected_valid) {
                        if ($invEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid, got errors: $($invEval.Reasons -join '; ')"
                        }
                    } else {
                        if (-not $invEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $invEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', got: $($invEval.Reasons -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid inventory, but validation succeeded"
                        }
                    }
                }
                "policy.json" {
                    $policyToCheck = $case.policy
                    if ($case.mutations) {
                        $policyToCheck = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                        foreach ($mutation in $case.mutations.PSObject.Properties) {
                            $segments = ([string]$mutation.Name).Split('.')
                            $target = $policyToCheck
                            if ($segments.Length -gt 1) { foreach ($segment in $segments[0..($segments.Length - 2)]) { $target = $target.$segment } }
                            $leaf = $segments[-1]
                            if ($mutation.Value -ceq '__REMOVE__') {
                                $target.PSObject.Properties.Remove($leaf)
                            } else {
                                $target | Add-Member -NotePropertyName $leaf -NotePropertyValue $mutation.Value -Force
                            }
                        }
                    }
                    $polEval = Test-PolicyConfiguration -Policy $policyToCheck -Arguments $case.arguments
                    if ($case.expected_valid) {
                        if ($polEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid, got errors: $($polEval.Reasons -join '; ')"
                        }
                    } else {
                        if (-not $polEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $polEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', got: $($polEval.Reasons -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid policy, but validation succeeded"
                        }
                    }
                }
                "policy-entry.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-policy-entry-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -Case ([string]$case.runner_case) -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "Canonical policy entry-point case failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "freshness.json" {
                    $freshEval = Test-CapturePreflight -Preflight $case.preflight
                    if ($case.expected_valid) {
                        if ($freshEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid preflight, got errors: $($freshEval.Errors -join '; ')"
                        }
                    } else {
                        if (-not $freshEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $freshEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', got: $($freshEval.Errors -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid preflight, but validation succeeded"
                        }
                    }
                }
                "suites.json" {
                    $suiteEval = Test-SuiteOutcome -SuiteResult $case.suite_result
                    $statusOk = ($suiteEval.Status -eq $case.expected_status)
                    $execOk = ($suiteEval.Executed -eq $case.expected_executed)
                    if ($statusOk -and $execOk) {
                        if ($case.expected_error) {
                            $matched = $suiteEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Expected error matching '$($case.expected_error)', got: $($suiteEval.Reasons -join '; ')"
                            }
                        } else {
                            $casePassed = $true
                        }
                    } else {
                        $errorMsg = "Expected status=$($case.expected_status), executed=$($case.expected_executed); got status=$($suiteEval.Status), executed=$($suiteEval.Executed)"
                    }
                }
                "collection.json" {
                    $collEval = Test-CollectionState -Run $case.collection_run
                    $statusOk = ($collEval.CollectionStatus -eq $case.expected_collection_status)
                    $eligOk = ($collEval.BaselineEligible -eq $case.expected_baseline_eligible)
                    if ($statusOk -and $eligOk) {
                        if ($case.expected_error) {
                            $matched = $collEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Expected error matching '$($case.expected_error)', got: $($collEval.Reasons -join '; ')"
                            }
                        } else {
                            $casePassed = $true
                        }
                    } else {
                        $errorMsg = "Expected status=$($case.expected_collection_status), eligible=$($case.expected_baseline_eligible); got status=$($collEval.CollectionStatus), eligible=$($collEval.BaselineEligible)"
                    }
                }
                "integration.json" {
                    $intEval = Test-IntegrationInvocation -Invocation $case.invocation
                    if ($case.PSObject.Properties['expected_valid']) {
                        if ($case.expected_valid) {
                            if ($intEval.IsValid) { $casePassed = $true }
                            else { $errorMsg = "Expected valid, got errors: $($intEval.Errors -join '; ')" }
                        } else {
                            if (-not $intEval.IsValid) {
                                if ($case.expected_error) {
                                    $matched = $intEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                    if ($matched) { $casePassed = $true }
                                    else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($intEval.Errors -join '; ')" }
                                } else { $casePassed = $true }
                            } else { $errorMsg = "Expected invalid invocation, but validation succeeded" }
                        }
                    } elseif ($case.PSObject.Properties['expected_integration_status']) {
                        if ($intEval.IntegrationStatus -eq $case.expected_integration_status) { $casePassed = $true }
                        else { $errorMsg = "Expected integration status $($case.expected_integration_status), got $($intEval.IntegrationStatus)" }
                    }
                }
                "capture-result.json" {
                    try {
                        $captureResult = ConvertFrom-CaptureResultOutput -OutputLines $case.output_lines
                        if ($case.expected_valid) {
                            if ($captureResult.run_id -eq $case.expected_run_id -and
                                $captureResult.run_dir -eq $case.expected_run_dir) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Capture result identity mismatch: run_id=$($captureResult.run_id), run_dir=$($captureResult.run_dir)"
                            }
                        } else {
                            $errorMsg = "Expected capture result parsing to fail, but it succeeded"
                        }
                    } catch {
                        if (-not $case.expected_valid -and $_.Exception.Message.Contains([string]$case.expected_error)) {
                            $casePassed = $true
                        } else {
                            $errorMsg = $_.Exception.Message
                        }
                    }
                }
                "manifest-identity.json" {
                    $identity = Test-CandidateManifestIdentity -Manifest $case.manifest -ExpectedSourceCommit $case.expected_source_commit -ExpectedTargetCommit $case.expected_target_commit
                    if ($identity.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($identity.Errors -join '; ').Contains([string]$case.expected_error)) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected identity error '$($case.expected_error)', got: $($identity.Errors -join '; ')"
                        }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($identity.IsValid). Errors: $($identity.Errors -join '; ')"
                    }
                }
                "comparison.json" {
                    $compEval = if ($case.PSObject.Properties['required_module_paths']) {
                        Test-ComparisonModuleAccounting -Modules $case.module_records -RequiredModulePaths @($case.required_module_paths) -Label 'Baseline'
                    } else {
                        Test-ComparisonCase -Case $case
                    }
                    if ($case.PSObject.Properties['expected_passed']) {
                        $observedPassed = if ($case.PSObject.Properties['required_module_paths']) { $compEval.IsValid } else { $compEval.Passed }
                        if ($observedPassed -eq $case.expected_passed) {
                            if ($case.expected_error) {
                                $matched = $compEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected an error matching '$($case.expected_error)', got: $($compEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else { $errorMsg = "Expected passed=$($case.expected_passed), got $observedPassed. Errors: $($compEval.Errors -join '; ')" }
                    } elseif ($case.PSObject.Properties['expected_baseline_valid']) {
                        if ($compEval.BaselineValid -eq $case.expected_baseline_valid) { $casePassed = $true }
                        else { $errorMsg = "Expected baseline valid=$($case.expected_baseline_valid), got $($compEval.BaselineValid)" }
                    } elseif ($case.PSObject.Properties['expected_compatible']) {
                        if ($compEval.Compatible -eq $case.expected_compatible) { $casePassed = $true }
                        else { $errorMsg = "Expected compatible=$($case.expected_compatible), got $($compEval.Compatible)" }
                    }
                }
                "publication.json" {
                    $pubEval = Test-PublicationCase -Case $case
                    if ($case.expected_valid) {
                        if ($pubEval.IsValid) { $casePassed = $true }
                        else { $errorMsg = "Expected valid publication, got errors: $($pubEval.Errors -join '; ')" }
                    } else {
                        if (-not $pubEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $pubEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($pubEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else { $errorMsg = "Expected invalid publication, but validation succeeded" }
                    }
                }
                "execution-files.json" {
                    $executionMap = Test-ExecutionFileAttribution -ExecutionFiles $case.execution_files -Artifacts $case.artifacts
                    if ($executionMap.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($executionMap.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected execution-file error '$($case.expected_error)', got: $($executionMap.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($executionMap.IsValid). Errors: $($executionMap.Errors -join '; ')"
                    }
                }
                "execution-compatibility.json" {
                    $compatibility = Test-ExecutionCompatibilityEvidence -InventoryModule $case.inventory_module -CounterModule $case.counter_module `
                        -AnalyzerIdentity 'jacoco-0.8.15' -Label ([string]$case.inventory_module.module_path)
                    if ($compatibility.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($compatibility.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected compatibility error '$($case.expected_error)', got: $($compatibility.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($compatibility.IsValid). Errors: $($compatibility.Errors -join '; ')"
                    }
                }
                "execution-attribution.json" {
                    $attribution = Test-ExecutionCompatibilityAttribution -CompatibilityReceipt $case.compatibility_receipt `
                        -ManifestExecutionFiles $case.manifest_execution_files -Scope ([string]$case.scope) `
                        -ExpectedVariant ([string]$case.expected_variant) -Label ([string]$case.scope)
                    if ($attribution.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($attribution.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected attribution error '$($case.expected_error)', got: $($attribution.Errors -join '; ')" }
                    } else { $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($attribution.IsValid). Errors: $($attribution.Errors -join '; ')" }
                }
                "comparison-module-reports.json" {
                    $reportBinding = Test-ComparisonModuleReportArtifact -ModulePath ([string]$case.module_path) `
                        -ExpectedDigest ([string]$case.report_digest) -Artifacts $case.artifacts -Label 'Fixture baseline'
                    if ($reportBinding.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($reportBinding.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected module report artifact error '$($case.expected_error)', got: $($reportBinding.Errors -join '; ')" }
                    } else { $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($reportBinding.IsValid). Errors: $($reportBinding.Errors -join '; ')" }
                }
                "probe-compatibility.json" {
                    $fixtureScript = Join-Path $FixturesDir 'run-probe-mismatch-fixture.ps1'
                    $fixtureOutput = @(& pwsh -NoProfile -File $fixtureScript -RepositoryRoot ([System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))) 2>&1)
                    $fixtureExit = $LASTEXITCODE
                    if ($fixtureExit -eq 0) { $casePassed = $true }
                    else { $errorMsg = "JaCoCo runtime probe compatibility fixture failed with exit $fixtureExit`: $($fixtureOutput -join ' ')" }
                }
                "selection.json" {
                    $receiptRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-selection-fixture-' + [guid]::NewGuid().ToString('N'))
                    try {
                        if ($case.batch_state.receipts) {
                            New-Item -ItemType Directory -Path $receiptRoot -Force | Out-Null
                            foreach ($entry in @(
                                @{ Name = 'focused_result'; Type = 'focused-pass'; Status = 'Passed' },
                                @{ Name = 'repair_result'; Type = 'repair'; Status = 'Completed' },
                                @{ Name = 'reranking'; Type = 'reranking'; Status = 'Complete' }
                            )) {
                                $receipt = $case.batch_state.receipts.($entry.Name)
                                if (-not $receipt -or -not $receipt.artifact_path) { continue }
                                $relative = ([string]$receipt.artifact_path).Replace('\', '/')
                                if ([System.IO.Path]::IsPathRooted($relative) -or $relative -match '(^|/)\.\.(/|$)') { throw 'Selection fixture receipt path must be a safe relative path' }
                                $artifactPath = Join-Path $receiptRoot $relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
                                New-Item -ItemType Directory -Path (Split-Path -Parent $artifactPath) -Force | Out-Null
                                $receiptStatus = if ($receipt.status) { [string]$receipt.status } else { $entry.Status }
                                $artifact = [PSCustomObject]@{ receipt_type = $entry.Type; status = $receiptStatus; candidate_id = [string]$case.batch_state.selected_case }
                                $artifact | ConvertTo-Json -Compress | Set-Content -LiteralPath $artifactPath -Encoding utf8
                                $receipt.artifact_path = $artifactPath
                                $receipt.sha256 = Get-FileSha256 -Path $artifactPath
                            }
                            $measurement = $case.batch_state.receipts.measurement
                            if ($measurement) {
                                $bundlePath = Join-Path $receiptRoot 'measurement/bundle'
                                New-Item -ItemType Directory -Path $bundlePath -Force | Out-Null
                                $manifest = [PSCustomObject]@{
                                    run_id = [string]$measurement.run_id
                                    purpose = 'Initial'
                                    git = [PSCustomObject]@{ source_commit = [string]$measurement.candidate_sha; clean_preflight = [bool]$measurement.clean; clean_postflight = [bool]$measurement.clean }
                                    harness = [PSCustomObject]@{ clean = [bool]$measurement.clean }
                                    decisions = [PSCustomObject]@{ collection = 'Complete'; tests = 'Passed' }
                                    suites = @([PSCustomObject]@{ suite_name = 'selection-fixture'; required = $true; tests = 1; skipped = 0; failures = 0; errors = 0 })
                                }
                                switch ([string]$case.measurement_manifest_mutation) {
                                    'wrong-source' { $manifest.git.source_commit = '0000000000000000000000000000000000000000' }
                                    'dirty-postflight' { $manifest.git.clean_postflight = $false }
                                    'missing-required-suite' { $manifest.suites = @() }
                                    'failed-tests' { $manifest.decisions.tests = 'Failed' }
                                }
                                $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $bundlePath 'manifest.json') -Encoding utf8
                                foreach ($field in @(
                                    @{ Name = 'bundle_path'; Value = $bundlePath },
                                    @{ Name = 'manifest_path'; Value = (Join-Path $bundlePath 'manifest.json') },
                                    @{ Name = 'bundle_digest'; Value = (Get-CoverageBundleDigest -BundlePath $bundlePath) }
                                )) {
                                    if ($measurement.PSObject.Properties[$field.Name]) { $measurement.($field.Name) = $field.Value }
                                    else { $measurement | Add-Member -NotePropertyName $field.Name -NotePropertyValue $field.Value }
                                }
                            }
                            if ($case.tamper_receipt_sha256 -and $case.batch_state.receipts.focused_result) { $case.batch_state.receipts.focused_result.sha256 = ('0' * 64) }
                            if ($case.tamper_measurement_digest -and $measurement) { $measurement.bundle_digest = ('0' * 64) }
                        }
                        $selEval = Test-SelectionCase -Case $case
                    } finally {
                        if (Test-Path -LiteralPath $receiptRoot) { Remove-Item -LiteralPath $receiptRoot -Recurse -Force }
                    }
                    if ($case.expected_error) {
                        $matched = $selEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                        if ($matched) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected error matching '$($case.expected_error)', got: $($selEval.Errors -join '; ')"
                        }
                    } else {
                        if ($selEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Selection case mismatch: $($selEval.Errors -join '; ')"
                        }
                    }
                }
                "baselines.json" {
                    $baseEval = Test-BaselineCase -Case $case
                    if ($baseEval.Reusable -eq $case.expected_reusable) {
                        if ($case.expected_error) {
                            $matched = $baseEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) { $casePassed = $true }
                            else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($baseEval.Errors -join '; ')" }
                        } else {
                            $casePassed = $true
                        }
                    } else {
                        $errorMsg = "Expected reusable=$($case.expected_reusable), got $($baseEval.Reusable)"
                    }
                }
                "trusted-comparison.json" {
                    $coveragePolicy = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                    if ($case.expected_valid) {
                        $case.candidate.measurement_fingerprint = Get-MeasurementFingerprint -Policy $coveragePolicy -Toolchain $case.candidate.toolchain -Device $case.candidate.device -HarnessFileDigests $case.candidate.harness.file_sha256
                        $case.baseline.measurement_fingerprint = Get-MeasurementFingerprint -Policy $coveragePolicy -Toolchain $case.baseline.toolchain -Device $case.baseline.device -HarnessFileDigests $case.baseline.harness.file_sha256
                    }
                    $comparison = Test-TrustedComparisonManifest -CandidateManifest $case.candidate -BaselineManifest $case.baseline -Policy $coveragePolicy
                    if ($comparison.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($comparison.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected error '$($case.expected_error)', got: $($comparison.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($comparison.IsValid). Errors: $($comparison.Errors -join '; ')"
                    }
                }
                "comparison-bundle-junit.json" {
                    $coveragePolicy = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                    $tempBundle = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-baseline-junit-' + [guid]::NewGuid().ToString('N'))
                    $testReportDir = Join-Path $tempBundle 'reports/tests'
                    New-Item -ItemType Directory -Path $testReportDir -Force | Out-Null
                    try {
                        $reportRelativePath = if ($case.missing_report) { 'reports/tests/TEST-missing.xml' } else { 'reports/tests/TEST-fixture.xml' }
                        $reportPath = Join-Path $tempBundle $reportRelativePath
                        $reportDigest = ('0' * 64)
                        if (-not $case.missing_report) {
                            [System.IO.File]::WriteAllText($reportPath, [string]$case.xml, [System.Text.UTF8Encoding]::new($false))
                            $reportDigest = Get-FileSha256 -Path $reportPath
                        }
                        $syntheticManifest = [PSCustomObject]@{
                            artifacts = @([PSCustomObject]@{ path = $reportRelativePath; sha256 = $reportDigest })
                            suites = @($case.suite)
                        }
                        $bundleCheck = Test-ComparisonBundleEvidence -BundlePath $tempBundle -Manifest $syntheticManifest -Policy $coveragePolicy
                        $matched = @($bundleCheck.Errors | Where-Object { $_ -like "*$($case.expected_error)*" })
                        if ($matched.Count -gt 0) { $casePassed = $true }
                        else { $errorMsg = "Expected baseline bundle rejection '$($case.expected_error)', got: $($bundleCheck.Errors -join '; ')" }
                    } finally {
                        $canonicalTemp = [System.IO.Path]::GetFullPath($tempBundle)
                        $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                        if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-baseline-junit-*') {
                            throw "Refusing to remove unexpected comparison fixture path: $canonicalTemp"
                        }
                        [System.IO.Directory]::Delete($canonicalTemp, $true)
                    }
                }
                "comparison-repository.json" {
                    $tempBundle = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-repository-evidence-' + [guid]::NewGuid().ToString('N'))
                    $reportDir = Join-Path $tempBundle 'reports'
                    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
                    try {
                        $reportPath = Join-Path $reportDir 'repository.xml'
                        $xmlCovered = if ($case.xml_instruction_covered -ne $null) { [string]$case.xml_instruction_covered } else { '8' }
                        $xmlMissed = if ($case.xml_instruction_missed -ne $null) { [string]$case.xml_instruction_missed } else { '2' }
                        $xmlBranchCovered = if ($case.xml_branch_covered -ne $null) { [string]$case.xml_branch_covered } else { '3' }
                        $xml = "<report name=`"fixture`"><counter type=`"INSTRUCTION`" missed=`"$xmlMissed`" covered=`"$xmlCovered`"/><counter type=`"BRANCH`" missed=`"1`" covered=`"$xmlBranchCovered`"/></report>"
                        if (-not $case.missing_report) { [System.IO.File]::WriteAllText($reportPath, $xml, [System.Text.UTF8Encoding]::new($false)) }
                        $reportDigest = if (Test-Path -LiteralPath $reportPath) { Get-FileSha256 -Path $reportPath } else { '0' * 64 }
                        $counterCovered = if ($case.counter_instruction_covered -ne $null) { [string]$case.counter_instruction_covered } else { '8' }
                        $counterMissed = if ($case.counter_instruction_missed -ne $null) { [string]$case.counter_instruction_missed } else { '2' }
                        $counterTotal = if ($case.counter_instruction_total -ne $null) { [string]$case.counter_instruction_total } else { '10' }
                        if ($case.bad_counter_report_digest) { $reportDigest = 'a' * 64 }
                        $artifactDigest = if (Test-Path -LiteralPath $reportPath) { Get-FileSha256 -Path $reportPath } else { '0' * 64 }
                        $artifacts = if ($case.missing_artifact) { @() } else { @([PSCustomObject]@{ path = 'reports/repository.xml'; sha256 = $artifactDigest }) }
                        $manifest = [PSCustomObject]@{ artifacts = $artifacts }
                        $counters = [PSCustomObject]@{ repository = [PSCustomObject]@{
                            report_digest = $reportDigest
                            instruction = [PSCustomObject]@{
                                covered = [System.Numerics.BigInteger]::Parse($counterCovered, [Globalization.CultureInfo]::InvariantCulture)
                                missed = [System.Numerics.BigInteger]::Parse($counterMissed, [Globalization.CultureInfo]::InvariantCulture)
                                total = [System.Numerics.BigInteger]::Parse($counterTotal, [Globalization.CultureInfo]::InvariantCulture)
                            }
                            branch = [PSCustomObject]@{ covered = [System.Numerics.BigInteger]3; missed = [System.Numerics.BigInteger]1; total = [System.Numerics.BigInteger]4 }
                        } }
                        if ($case.missing_canonical_repository) { $counters.repository = $null }
                        if ($case.missing_canonical_repository) {
                            $repoXmlDocument = Read-SafeXml -Path $reportPath
                            $binding = Test-CanonicalRepositoryReportBinding -XmlDocument $repoXmlDocument -RepositoryCounters $null `
                                -ReportDigest $reportDigest -ExpectedReportDigest $reportDigest -Label 'Fixture repository'
                            $result = @{ IsValid = $binding.IsValid; Errors = $binding.Errors }
                        } else {
                            $result = Test-ComparisonRepositoryEvidence -BundlePath $tempBundle -Manifest $manifest -Counters $counters
                        }
                        if ($result.IsValid -eq $case.expected_valid) {
                            if (-not $case.expected_error -or ($result.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                            else { $errorMsg = "Expected repository evidence error '$($case.expected_error)', got: $($result.Errors -join '; ')" }
                        } else { $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($result.IsValid): $($result.Errors -join '; ')" }
                    } finally {
                        $canonicalTemp = [System.IO.Path]::GetFullPath($tempBundle)
                        $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
                        if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-repository-evidence-*') { throw "Refusing to remove unexpected comparison fixture path: $canonicalTemp" }
                        [System.IO.Directory]::Delete($canonicalTemp, $true)
                    }
                }
                "comparison-module-binding.json" {
                    $required = @(':core:security', ':core:domain', ':core:data', ':feature:fido2', ':feature:authenticator')
                    $inventoryModules = @($case.inventory_modules | ForEach-Object { [PSCustomObject]@{ module_path = [string]$_ } })
                    $counterModules = @($case.counter_modules | ForEach-Object { [PSCustomObject]@{ module_path = [string]$_ } })
                    $binding = Test-ComparisonModuleSetBinding -InventoryModules $inventoryModules -CounterModules $counterModules -RequiredModulePaths $required -Label 'Baseline'
                    $matched = @($binding.Errors | Where-Object { $_ -like "*$($case.expected_error)*" })
                    if ($binding.IsValid -eq [bool]$case.expected_valid -and (-not $case.expected_error -or $matched.Count -gt 0)) {
                        $casePassed = $true
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid) and error '$($case.expected_error)', got valid=$($binding.IsValid): $($binding.Errors -join '; ')"
                    }
                }
                "comparison-inventory-structure.json" {
                    $coveragePolicy = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                    $inventoryModules = @($case.inventory_modules | ForEach-Object {
                        [PSCustomObject]@{
                            module_path = [string]$_.module_path
                            tier = $_.tier
                            project_directory = [string]$_.project_directory
                        }
                    })
                    $binding = Test-ComparisonInventoryStructure -InventoryModules $inventoryModules -Policy $coveragePolicy -Label 'Baseline'
                    $matched = @($binding.Errors | Where-Object { $_ -like "*$($case.expected_error)*" })
                    if ($binding.IsValid -eq [bool]$case.expected_valid -and (-not $case.expected_error -or $matched.Count -gt 0)) {
                        $casePassed = $true
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid) and error '$($case.expected_error)', got valid=$($binding.IsValid): $($binding.Errors -join '; ')"
                    }
                }
                "accepted-baseline-index.json" {
                    $indexEval = Test-AcceptedBaselineIndex -Index $case.index -ExpectedTargetCommit $case.expected_target -ActualBundleDigest $case.actual_digest
                    if ($indexEval.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($indexEval.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected index error '$($case.expected_error)', got: $($indexEval.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($indexEval.IsValid). Errors: $($indexEval.Errors -join '; ')"
                    }
                }
                "changed-paths.json" {
                    $coveragePolicy = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                    $pathsProvided = [bool]$case.paths_provided
                    [string[]]$changed = @()
                    if ($case.paths) { $changed = @($case.paths) }
                    $affectedModules = @(Get-AffectedTier1Modules -Policy $coveragePolicy -ChangedPaths $changed -ChangedPathsProvided $pathsProvided)
                    $expectedModules = @($case.expected_modules)
                    $tier2Matches = $true
                    if ($case.PSObject.Properties['expected_tier2_modules']) {
                        $tier2Rows = @(
                            [PSCustomObject]@{ module_path = ':core:database'; tier = 2; project_directory = 'core/database' },
                            [PSCustomObject]@{ module_path = ':core:common'; tier = 2; project_directory = 'core/common' },
                            [PSCustomObject]@{ module_path = ':app'; tier = 2; project_directory = 'app' },
                            [PSCustomObject]@{ module_path = ':core:bluetooth'; tier = 2; project_directory = 'core/bluetooth' },
                            [PSCustomObject]@{ module_path = ':core:ui'; tier = 2; project_directory = 'core/ui' }
                        )
                        if ($case.omit_configured_owner) { $tier2Rows = @($tier2Rows | Where-Object module_path -ne ':app') }
                        if ($case.duplicate_owner) { $tier2Rows += [PSCustomObject]@{ module_path = ':core:bluetooth'; tier = 2; project_directory = 'core/bluetooth' } }
                        if ($case.unsafe_owner_directory) { ($tier2Rows | Where-Object module_path -eq ':core:bluetooth' | Select-Object -First 1).project_directory = '../outside' }
                        $tier2Inventory = [PSCustomObject]@{ modules = $tier2Rows }
                        try {
                            $affectedTier2 = @(Get-AffectedTier2Modules -Policy $coveragePolicy -Inventory $tier2Inventory -ChangedPaths $changed -ChangedPathsProvided $pathsProvided)
                            $expectedTier2 = @($case.expected_tier2_modules)
                            $tier2Matches = (($affectedTier2 -join ',') -ceq ($expectedTier2 -join ','))
                            if (-not $tier2Matches) { $errorMsg = "Expected Tier 2 modules [$($expectedTier2 -join ',')], got [$($affectedTier2 -join ',')]" }
                            if ($case.expect_error) { $errorMsg = 'Expected Tier 2 ownership error, but selection succeeded'; $tier2Matches = $false }
                        } catch {
                            if ($case.expect_error -and $_.Exception.Message -like "*$($case.expect_error)*") { $tier2Matches = $true }
                            else { $errorMsg = "Unexpected Tier 2 ownership error: $($_.Exception.Message)"; $tier2Matches = $false }
                        }
                    }
                    if (($affectedModules -join ',') -ceq ($expectedModules -join ',') -and $tier2Matches) { $casePassed = $true }
                    elseif (-not $errorMsg) { $errorMsg = "Expected modules [$($expectedModules -join ',')], got [$($affectedModules -join ',')]" }
                }
                "promotion-identity.json" {
                    $promotion = Test-PromotionIdentity -Manifest $case.manifest -IntegratedTree $case.integrated_tree -IntegratedCommit $case.integrated_commit
                    if ($promotion.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($promotion.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected promotion error '$($case.expected_error)', got: $($promotion.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($promotion.IsValid). Errors: $($promotion.Errors -join '; ')"
                    }
                }
                "promotion.json" {
                    $promotion = Test-PromotionReadiness -Manifest $case.manifest -Decision $case.decision -IntegrationEvent $case.integration_event -Repository $case.repository -HeadSha $case.head_sha -BaseSha $case.base_sha
                    if ($promotion.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($promotion.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected readiness error '$($case.expected_error)', got: $($promotion.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected promotion ready=$($case.expected_valid), got ready=$($promotion.IsValid). Errors: $($promotion.Errors -join '; ')"
                    }
                }
                "remote-target-ref.json" {
                    $targetRef = ConvertTo-RemoteTargetRef -TargetRef $case.target_ref -RemoteName $case.remote_name
                    if ($targetRef.IsValid -eq $case.expected_valid -and $targetRef.Ref -eq $case.expected_ref) {
                        if (-not $case.expected_error -or $targetRef.Error -eq $case.expected_error) { $casePassed = $true }
                        else { $errorMsg = "Expected error '$($case.expected_error)', got '$($targetRef.Error)'" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid) ref='$($case.expected_ref)', got valid=$($targetRef.IsValid) ref='$($targetRef.Ref)'"
                    }
                }
                "archive-lifecycle.json" {
                    $archEval = Test-ArchiveLifecycleCase -Case $case
                    if ($case.PSObject.Properties['expected_success']) {
                        if ($archEval.Success -eq $case.expected_success) {
                            if ($case.expected_error) {
                                $matched = $archEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($archEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else {
                            $errorMsg = "Expected success=$($case.expected_success), got $($archEval.Success)"
                        }
                    } elseif ($case.PSObject.Properties['expected_promotion_allowed']) {
                        if ($archEval.PromotionAllowed -eq $case.expected_promotion_allowed) {
                            if ($case.expected_error) {
                                $matched = $archEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($archEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else {
                            $errorMsg = "Expected promotion_allowed=$($case.expected_promotion_allowed), got $($archEval.PromotionAllowed)"
                        }
                    }
                }
                default {
                    $errorMsg = "No handler registered for $($file.Name); fixture suites must fail rather than skip"
                }
            }
        } catch {
            $errorMsg = "Exception during test execution: $_"
        }

        if ($casePassed) {
            $totalPassed++
            Write-Host "  [PASS] $($case.id) - $($case.description)" -ForegroundColor Green
            $results += [PSCustomObject]@{
                ID = $case.id
                Status = "PASS"
                Description = $case.description
                Notes = ""
            }
        } else {
            $totalFailed++
            Write-Host "  [FAIL] $($case.id) - $($case.description)" -ForegroundColor Red
            Write-Host "         Reason: $errorMsg" -ForegroundColor DarkRed
            $results += [PSCustomObject]@{
                ID = $case.id
                Status = "FAIL"
                Description = $case.description
                Notes = $errorMsg
            }
        }
    }
}

Write-Host "`nTest Summary: $totalPassed passed, $totalFailed failed" -ForegroundColor $(if ($totalFailed -eq 0 -and $totalPassed -gt 0) { "Green" } else { "Red" })

if (($totalPassed + $totalFailed) -eq 0) {
    Write-Error "Zero test cases were selected or executed (SpecificCase='$SpecificCase', Pattern='$FixturePattern')"
    exit 1
}

if ($totalFailed -gt 0) {
    exit 1
}
exit 0
