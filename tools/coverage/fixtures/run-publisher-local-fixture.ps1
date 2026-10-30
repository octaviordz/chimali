[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [ValidateSet('success', 'remote-unavailable', 'commit-failure', 'push-failure', 'readback-failure')][string]$Mode = 'success'
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$realGit = (Get-Command git.exe -ErrorAction Stop).Source
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-publisher-local-' + [guid]::NewGuid().ToString('N'))
$repoRoot = Join-Path $tempRoot 'repo'
$bundleRoot = Join-Path $tempRoot 'bundle'
$bareRemote = Join-Path $tempRoot 'archive.git'
$invokeScript = Join-Path $tempRoot 'invoke-publisher.ps1'
$receiptPath = Join-Path $tempRoot 'receipt.json'
$runId = switch ($Mode) {
    'success' { '00000000-0000-4000-8000-000000000456' }
    'push-failure' { '00000000-0000-4000-8000-000000000457' }
    'readback-failure' { '00000000-0000-4000-8000-000000000458' }
    'remote-unavailable' { '00000000-0000-4000-8000-000000000459' }
    'commit-failure' { '00000000-0000-4000-8000-000000000460' }
}
$branch = 'codex/coverage-evidence'
$testGitVariable = 'COVERAGE_TEST_REAL_GIT'
$sourceRootVariable = 'COVERAGE_TEST_SOURCE_ROOT'
$publisherVariable = 'COVERAGE_TEST_PUBLISHER'
$bundleVariable = 'COVERAGE_TEST_BUNDLE'
$branchVariable = 'COVERAGE_TEST_BRANCH'
$receiptVariable = 'COVERAGE_TEST_RECEIPT'
$tokenVariable = 'CHIMALI_COVERAGE_PUBLISH_TOKEN'
$failureModeVariable = 'COVERAGE_TEST_GIT_FAILURE_MODE'
$previousRealGit = [Environment]::GetEnvironmentVariable($testGitVariable, 'Process')
$previousSourceRoot = [Environment]::GetEnvironmentVariable($sourceRootVariable, 'Process')
$previousPublisher = [Environment]::GetEnvironmentVariable($publisherVariable, 'Process')
$previousBundle = [Environment]::GetEnvironmentVariable($bundleVariable, 'Process')
$previousBranch = [Environment]::GetEnvironmentVariable($branchVariable, 'Process')
$previousReceipt = [Environment]::GetEnvironmentVariable($receiptVariable, 'Process')
$previousToken = [Environment]::GetEnvironmentVariable($tokenVariable, 'Process')
$previousFailureMode = [Environment]::GetEnvironmentVariable($failureModeVariable, 'Process')

try {
    New-Item -ItemType Directory -Path $repoRoot,$bundleRoot -Force | Out-Null
    & $realGit init --quiet $repoRoot
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize isolated source repository' }
    & $realGit -C $repoRoot config user.name 'Coverage Fixture'
    & $realGit -C $repoRoot config user.email 'coverage-fixture@example.invalid'
    [System.IO.File]::WriteAllText((Join-Path $repoRoot 'source.txt'), 'fixture source', [System.Text.UTF8Encoding]::new($false))
    & $realGit -C $repoRoot add -- source.txt
    & $realGit -C $repoRoot commit --quiet -m 'fixture source'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create isolated source commit' }
    $sourceCommit = (& $realGit -C $repoRoot rev-parse HEAD).Trim()
    $sourceTree = (& $realGit -C $repoRoot rev-parse 'HEAD^{tree}').Trim()
    & $realGit init --bare --quiet $bareRemote
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize isolated bare archive remote' }
    & $realGit -C $repoRoot remote add origin $bareRemote
    if ($LASTEXITCODE -ne 0) { throw 'Unable to configure isolated archive remote' }

    $manifest = [PSCustomObject]@{
        run_id = $runId
        purpose = 'Initial'
        git = [PSCustomObject]@{ source_commit = $sourceCommit; source_tree = $sourceTree }
        artifacts = @()
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $bundleRoot 'manifest.json') -Encoding utf8
    [System.IO.File]::WriteAllText((Join-Path $bundleRoot 'summary.md'), 'offline publisher fixture', [System.Text.UTF8Encoding]::new($false))

    $invoker = @'
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GitArgs)
function global:git {
    $Arguments = @($args)
    if (($Arguments -join ' ') -match 'offline-fixture-token|AUTHORIZATION: basic') {
        throw 'Publisher exposed its credential in Git command arguments.'
    }
    if ($Arguments -contains 'ls-remote') {
        $expectedHeader = 'AUTHORIZATION: basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('x-access-token:offline-fixture-token'))
        if ($env:GIT_CONFIG_COUNT -ne '2' -or $env:GIT_CONFIG_KEY_0 -ne 'fixture.preserved' -or
            $env:GIT_CONFIG_VALUE_0 -ne 'yes' -or $env:GIT_CONFIG_KEY_1 -ne 'http.https://github.com/.extraheader' -or
            $env:GIT_CONFIG_VALUE_1 -cne $expectedHeader) {
            throw 'Publisher did not preserve Git configuration and provide its credential through the environment.'
        }
    }
    $forward = [System.Collections.Generic.List[string]]::new()
    for ($index = 0; $index -lt $Arguments.Count; $index++) {
        if ($Arguments[$index] -eq '-c') { $index++; continue }
        $forward.Add($Arguments[$index])
    }
    if ($forward.Count -ge 2 -and $forward[0] -eq 'remote' -and $forward[1] -eq 'get-url') {
        Write-Output 'https://github.com/octaviordz/chimali.git'
        $global:LASTEXITCODE = 0
        return
    }
    if ($env:COVERAGE_TEST_GIT_FAILURE_MODE -eq 'push-failure' -and $forward.Count -gt 0 -and $forward[0] -eq 'push') {
        Write-Output 'fixture: simulated archive push rejection'
        $global:LASTEXITCODE = 1
        return
    }
    if ($env:COVERAGE_TEST_GIT_FAILURE_MODE -eq 'commit-failure' -and $forward.Count -gt 0 -and $forward[0] -eq 'commit') {
        Write-Output 'fixture: simulated archive commit rejection'
        $global:LASTEXITCODE = 1
        return
    }
    if ($env:COVERAGE_TEST_GIT_FAILURE_MODE -eq 'remote-unavailable' -and $forward.Count -gt 0 -and $forward[0] -eq 'ls-remote') {
        Write-Output 'fixture: simulated remote unavailable'
        $global:LASTEXITCODE = 1
        return
    }
    if ($env:COVERAGE_TEST_GIT_FAILURE_MODE -eq 'readback-failure' -and $forward.Count -gt 0 -and $forward[0] -eq 'ls-remote') {
        $global:COVERAGE_TEST_LS_REMOTE_COUNT++
        if ($global:COVERAGE_TEST_LS_REMOTE_COUNT -ge 2) {
            Write-Output 'fixture: simulated remote read-back outage'
            $global:LASTEXITCODE = 1
            return
        }
    }
    & $env:COVERAGE_TEST_REAL_GIT @($forward.ToArray())
    $global:LASTEXITCODE = $LASTEXITCODE
}
'@
    $invoker += @'
$publisherPath = $env:COVERAGE_TEST_PUBLISHER
$env:GIT_CONFIG_COUNT = '1'
$env:GIT_CONFIG_KEY_0 = 'fixture.preserved'
$env:GIT_CONFIG_VALUE_0 = 'yes'
$env:GIT_CONFIG_KEY_1 = 'fixture.inactive'
$env:GIT_CONFIG_VALUE_1 = 'retained'
Push-Location $env:COVERAGE_TEST_SOURCE_ROOT
try {
    & $publisherPath -RunBundle $env:COVERAGE_TEST_BUNDLE -EvidenceBranch $env:COVERAGE_TEST_BRANCH -RemoteName origin -OutputReceiptPath $env:COVERAGE_TEST_RECEIPT
    $publisherExit = $LASTEXITCODE
    if ($env:GIT_CONFIG_COUNT -ne '1' -or $env:GIT_CONFIG_KEY_0 -ne 'fixture.preserved' -or
        $env:GIT_CONFIG_VALUE_0 -ne 'yes' -or $env:GIT_CONFIG_KEY_1 -ne 'fixture.inactive' -or
        $env:GIT_CONFIG_VALUE_1 -ne 'retained') {
        throw 'Publisher did not restore the original Git environment.'
    }
    exit $publisherExit
} finally { Pop-Location }
'@
    [System.IO.File]::WriteAllText($invokeScript, $invoker, [System.Text.UTF8Encoding]::new($false))
    [Environment]::SetEnvironmentVariable($testGitVariable, $realGit, 'Process')
    [Environment]::SetEnvironmentVariable($sourceRootVariable, $repoRoot, 'Process')
    [Environment]::SetEnvironmentVariable($publisherVariable, (Join-Path $RepositoryRoot 'tools/coverage/publish.ps1'), 'Process')
    [Environment]::SetEnvironmentVariable($bundleVariable, $bundleRoot, 'Process')
    [Environment]::SetEnvironmentVariable($branchVariable, $branch, 'Process')
    [Environment]::SetEnvironmentVariable($receiptVariable, $receiptPath, 'Process')
    [Environment]::SetEnvironmentVariable($tokenVariable, 'offline-fixture-token', 'Process')
    [Environment]::SetEnvironmentVariable($failureModeVariable, $(if ($Mode -eq 'success') { '' } else { $Mode }), 'Process')
    $global:COVERAGE_TEST_LS_REMOTE_COUNT = 0

    & pwsh -NoProfile -ExecutionPolicy Bypass -File $invokeScript
    $publishExit = $LASTEXITCODE
    if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) { throw 'Publisher returned success without a receipt' }
    $receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
    if ($Mode -ne 'success') {
        $expectedFailure = switch ($Mode) {
            'remote-unavailable' { 'remote unreachable' }
            'commit-failure' { 'commit archive evidence' }
            'push-failure' { 'Push conflict' }
            'readback-failure' { 'Remote read-back failed' }
        }
        if ($publishExit -eq 0 -or $receipt.status -cne 'Failed' -or $receipt.run_id -cne $runId -or $receipt.error -notmatch [regex]::Escape($expectedFailure)) {
            throw "Expected an explicit failed publication receipt for $Mode; exit=$publishExit receipt=$($receipt | ConvertTo-Json -Compress -Depth 6)"
        }
        if ($receipt.status -ceq 'Published' -or $receipt.retrievable -eq $true -or $receipt.content_readback_verified -eq $true) {
            throw "Failed $Mode publication claimed authoritative retrievable success"
        }
        if ($Mode -eq 'readback-failure') {
            $remoteRefs = @(& $realGit --git-dir=$bareRemote show-ref --verify "refs/heads/$branch" 2>$null)
            if ($LASTEXITCODE -ne 0 -or $remoteRefs.Count -ne 1) { throw 'Read-back failure fixture did not publish the archive commit before simulating read-back loss' }
        }
        Write-Output "Production publisher retained a Failed receipt and no success claim for simulated $Mode."
        return
    }
    if ($publishExit -ne 0) { throw "Production publisher returned $publishExit" }
    if ($receipt.run_id -cne $runId -or $receipt.status -cne 'Published' -or $receipt.retrievable -ne $true -or $receipt.content_readback_verified -ne $true) {
        throw 'Published receipt does not prove retrievable archive content'
    }
    if ($receipt.archive_ref -cne "refs/heads/$branch" -or $receipt.read_back_commit -cne $receipt.archive_commit) {
        throw 'Published receipt is not bound to the exact archive ref and commit'
    }
    $remoteSummary = (& $realGit --git-dir=$bareRemote show "$($receipt.archive_commit):runs/$runId/summary.md") -join "`n"
    if ($LASTEXITCODE -ne 0 -or $remoteSummary -cne 'offline publisher fixture') { throw 'Local archive remote does not contain the exact run content' }
    Write-Output 'Production publisher completed append-only publication and remote content read-back against an isolated local bare Git remote.'
} finally {
    [Environment]::SetEnvironmentVariable($testGitVariable, $previousRealGit, 'Process')
    [Environment]::SetEnvironmentVariable($sourceRootVariable, $previousSourceRoot, 'Process')
    [Environment]::SetEnvironmentVariable($publisherVariable, $previousPublisher, 'Process')
    [Environment]::SetEnvironmentVariable($bundleVariable, $previousBundle, 'Process')
    [Environment]::SetEnvironmentVariable($branchVariable, $previousBranch, 'Process')
    [Environment]::SetEnvironmentVariable($receiptVariable, $previousReceipt, 'Process')
    [Environment]::SetEnvironmentVariable($tokenVariable, $previousToken, 'Process')
    [Environment]::SetEnvironmentVariable($failureModeVariable, $previousFailureMode, 'Process')
    Remove-Variable -Name COVERAGE_TEST_LS_REMOTE_COUNT -Scope Global -ErrorAction SilentlyContinue
    $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-publisher-local-*') {
        throw "Refusing to remove unexpected publisher fixture path: $canonicalTemp"
    }
    if (Test-Path -LiteralPath $canonicalTemp) {
        Get-ChildItem -LiteralPath $canonicalTemp -Force -Recurse | ForEach-Object {
            if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) { $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly) }
        }
        [System.IO.Directory]::Delete($canonicalTemp, $true)
    }
}
