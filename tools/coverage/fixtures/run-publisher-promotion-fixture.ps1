[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
[ValidateSet('success', 'unsupported-event', 'stale-base', 'target-push')][string]$Mode = 'success'
)

$ErrorActionPreference = 'Stop'
$repository = [System.IO.Path]::GetFullPath($RepositoryRoot)
$realGit = (Get-Command git.exe -ErrorAction Stop).Source
$realPwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-promotion-publisher-' + [guid]::NewGuid().ToString('N'))
$repoRoot = Join-Path $tempRoot 'repo'
$bareRemote = Join-Path $tempRoot 'remote.git'
$bundle = Join-Path $tempRoot 'bundle'
$harness = Join-Path $tempRoot 'harness/tools/coverage'
$receiptPath = Join-Path $tempRoot 'publication-receipt.json'
$decisionPath = Join-Path $tempRoot 'decision.json'
$eventPath = Join-Path $tempRoot 'event.json'
$verifiedEventPath = Join-Path $tempRoot 'verified-event.json'
$invokeScript = Join-Path $tempRoot 'invoke-publisher.ps1'
$branch = 'swe/coverage-evidence'
$runId = '00000000-0000-4000-8000-000000000461'
$envNames = @('COVERAGE_TEST_REAL_GIT', 'COVERAGE_TEST_PROMOTION_DECISION', 'COVERAGE_TEST_PROMOTION_EVENT', 'CHIMALI_COVERAGE_PUBLISH_TOKEN')
$previousEnv = @{}
foreach ($name in $envNames) { $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

function Invoke-CheckedGit {
    param([string]$WorkingDirectory, [string[]]$Arguments, [string]$Failure)
    & $realGit -C $WorkingDirectory @Arguments
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

try {
    New-Item -ItemType Directory -Path $repoRoot,$bundle,$harness -Force | Out-Null
    & $realGit init --quiet $repoRoot
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize isolated promotion source repository' }
    Invoke-CheckedGit $repoRoot @('config','user.name','Coverage Fixture') 'Unable to configure fixture author name'
    Invoke-CheckedGit $repoRoot @('config','user.email','coverage-fixture@example.invalid') 'Unable to configure fixture author email'
    [System.IO.File]::WriteAllText((Join-Path $repoRoot 'product.txt'), 'tested product tree', [System.Text.UTF8Encoding]::new($false))
    Invoke-CheckedGit $repoRoot @('add','--','product.txt') 'Unable to stage tested product tree'
    Invoke-CheckedGit $repoRoot @('commit','--quiet','-m','fixture tested candidate') 'Unable to commit tested candidate'
    $testedCommit = (& $realGit -C $repoRoot rev-parse HEAD).Trim()
    $testedTree = (& $realGit -C $repoRoot rev-parse 'HEAD^{tree}').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to identify tested candidate commit and tree' }
    Invoke-CheckedGit $repoRoot @('commit','--allow-empty','--quiet','-m','fixture integration commit') 'Unable to create descendant integration commit'
    $integratedCommit = (& $realGit -C $repoRoot rev-parse HEAD).Trim()
    $integratedTree = (& $realGit -C $repoRoot rev-parse 'HEAD^{tree}').Trim()
    if ($LASTEXITCODE -ne 0 -or $integratedTree -cne $testedTree) { throw 'Fixture integration commit does not retain the tested product tree' }
    $baseCommit = (& $realGit -C $repoRoot rev-parse "$testedCommit^{}").Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to identify expected target commit' }
    & $realGit init --bare --quiet (Join-Path $tempRoot 'remote.git')
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize isolated source remote' }
    $sourceRemote = Join-Path $tempRoot 'remote.git'
    Invoke-CheckedGit $repoRoot @('remote','add','origin',$sourceRemote) 'Unable to configure isolated source remote'
    Invoke-CheckedGit $repoRoot @('push','--quiet','origin',"${integratedCommit}:refs/heads/main") 'Unable to publish fixture integration objects'

    # Seed the archive branch with a prior accepted index so the fixture can
    # prove both atomic advancement on success and immutability on rejection.
    & $realGit -C $repoRoot checkout --quiet --orphan $branch
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create isolated archive branch' }
    & $realGit -C $repoRoot rm -rf --quiet . 2>$null
    $baselineDirectory = Join-Path $repoRoot 'baselines'
    New-Item -ItemType Directory -Path $baselineDirectory -Force | Out-Null
    $oldIndex = [PSCustomObject]@{ run_id = '00000000-0000-4000-8000-000000000099'; target_commit = ('9' * 40); provenance = 'FixturePriorIndex' }
    $oldIndex | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $baselineDirectory 'current.json') -Encoding utf8
    $historicalRunId = '00000000-0000-4000-8000-000000000098'
    $historicalRunDirectory = Join-Path (Join-Path $repoRoot 'runs') $historicalRunId
    New-Item -ItemType Directory -Path $historicalRunDirectory -Force | Out-Null
    $historicalBytes = [byte[]](0, 13, 10, 26, 255, 1, 2, 3)
    [System.IO.File]::WriteAllBytes((Join-Path $historicalRunDirectory 'raw.bin'), $historicalBytes)
    [PSCustomObject]@{ run_id = $historicalRunId; archive_branch = 'codex/coverage-evidence'; immutable = $true } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $historicalRunDirectory 'publication.json') -Encoding utf8
    $historicalReceipt = [PSCustomObject]@{ run_id = $historicalRunId; archive_branch = 'codex/coverage-evidence'; archive_ref = 'refs/heads/codex/coverage-evidence'; bundle_digest = 'a' * 64 }
    $historicalReceipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $historicalRunDirectory 'receipt.json') -Encoding utf8
    Invoke-CheckedGit $repoRoot @('add','-A') 'Unable to stage initial fixture archive index'
    Invoke-CheckedGit $repoRoot @('commit','--quiet','-m','fixture prior accepted index') 'Unable to commit initial fixture archive index'
    Invoke-CheckedGit $repoRoot @('push','--quiet','origin',"HEAD:refs/heads/$branch") 'Unable to seed remote archive ref'
    $initialArchiveCommit = (& $realGit -C $repoRoot rev-parse HEAD).Trim()
    Invoke-CheckedGit $repoRoot @('push','--quiet','origin',"$initialArchiveCommit`:refs/heads/codex/coverage-evidence") 'Unable to seed historical archive reference'
    $historicalBlob = (& $realGit -C $repoRoot hash-object (Join-Path $historicalRunDirectory 'raw.bin')).Trim()
    $historicalReceiptBlob = (& $realGit --git-dir=$bareRemote rev-parse "refs/heads/$branch`:runs/$historicalRunId/receipt.json").Trim()
    & $realGit -C $repoRoot checkout --quiet --detach $integratedCommit
    if ($LASTEXITCODE -ne 0) { throw 'Unable to return source checkout to the integrated commit' }

    [System.IO.File]::WriteAllText((Join-Path $bundle 'summary.md'), 'synthetic publisher promotion fixture', [System.Text.UTF8Encoding]::new($false))
    $manifest = [PSCustomObject]@{
        run_id = $runId; purpose = 'Candidate'
        git = [PSCustomObject]@{ source_commit = $testedCommit; source_tree = $testedTree; target_commit = $baseCommit }
        artifacts = @([PSCustomObject]@{
            path = 'summary.md'; sha256 = (Get-FileHash -LiteralPath (Join-Path $bundle 'summary.md') -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
    $eventBase = if ($Mode -eq 'stale-base') { 'e' * 40 } else { $baseCommit }
    $event = [PSCustomObject]@{ repository = 'octaviordz/chimali'; head_sha = $testedCommit; base_sha = $eventBase; pull_request_number = 17 }
    $event | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $eventPath -Encoding utf8
    $verifiedEventType = if ($Mode -eq 'unsupported-event') { 'push' } elseif ($Mode -eq 'target-push') { 'target_push' } else { 'pull_request' }
    $verifiedEvent = [PSCustomObject]@{
        valid = $true; event_type = $verifiedEventType; merged = ($verifiedEventType -eq 'pull_request'); integration_confirmed = ($verifiedEventType -eq 'target_push'); repository = 'octaviordz/chimali'
        head_sha = $testedCommit; base_sha = $baseCommit; integration_commit = $integratedCommit; integration_tree = $integratedTree
    }
    $verifiedEvent | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $verifiedEventPath -Encoding utf8
    $manifestDigest = (Get-FileHash -LiteralPath (Join-Path $bundle 'manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $decision = [PSCustomObject]@{
        run_id = $runId; manifest_digest = $manifestDigest; overall_passed = $true
        decisions = [PSCustomObject]@{ collection = 'Complete'; tests = 'Passed'; floors = 'Passed'; comparison_compatibility = 'Compatible'; regression = 'Passed' }
    }
    $decision | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $decisionPath -Encoding utf8
    '{}' | Set-Content -LiteralPath (Join-Path $tempRoot 'comparison-bundle.json') -Encoding utf8
    '{}' | Set-Content -LiteralPath (Join-Path $tempRoot 'accepted-index.json') -Encoding utf8
    . (Join-Path $repository 'tools/coverage/coverage-common.ps1')
    Write-ChangedPathReceipt -Path (Join-Path $tempRoot 'changed-paths.txt') -TargetCommit $baseCommit -SourceCommit $testedCommit -ChangedPaths @('product.txt')

    Copy-Item -LiteralPath (Join-Path $repository 'tools/coverage/publish.ps1') -Destination (Join-Path $harness 'publish.ps1')
    Copy-Item -LiteralPath (Join-Path $repository 'tools/coverage/coverage-common.ps1') -Destination (Join-Path $harness 'coverage-common.ps1')
    Copy-Item -LiteralPath (Join-Path $repository 'tools/coverage/policy.json') -Destination (Join-Path $harness 'policy.json')
    $verifyDouble = @'
param([string]$RunBundle,[string]$PolicyPath,[string]$OutputDirectory,[string]$ComparisonBundle,[string]$AcceptedBaselineIndexPath,[string]$ChangedPathsFile)
try {
  foreach($path in @($RunBundle,$PolicyPath,$ComparisonBundle,$AcceptedBaselineIndexPath,$ChangedPathsFile)) {
    if(-not (Test-Path -LiteralPath $path)) { throw "Trusted verifier input is missing: $path" }
  }
  $manifest=Get-Content -LiteralPath (Join-Path $RunBundle 'manifest.json') -Raw|ConvertFrom-Json
  $decision=Get-Content -LiteralPath $env:COVERAGE_TEST_PROMOTION_DECISION -Raw|ConvertFrom-Json
  $manifestDigest=(Get-FileHash -LiteralPath (Join-Path $RunBundle 'manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
  if($decision.run_id -cne $manifest.run_id -or $decision.manifest_digest -cne $manifestDigest) { throw 'Decision double is not bound to the exact run manifest' }
  if(-not (Get-Content -LiteralPath $ChangedPathsFile -Raw).Contains('product.txt')) { throw 'Verifier did not receive the sealed changed path' }
  New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
  Copy-Item -LiteralPath $env:COVERAGE_TEST_PROMOTION_DECISION -Destination (Join-Path $OutputDirectory 'decision.json') -Force
  exit 0
} catch { [Console]::Error.WriteLine($_); exit 1 }
'@
    $eventDouble = @'
param([string]$Repository,[string]$HeadSha,[string]$BaseSha,[string]$IntegrationEventPath,[int]$PullRequestNumber,[string]$Token,[string]$ServerUrl,[string]$LiveEvidencePath)
try {
  $request=Get-Content -LiteralPath $IntegrationEventPath -Raw|ConvertFrom-Json
  if($request.repository -cne $Repository -or $request.head_sha -cne $HeadSha -or $request.base_sha -cne $BaseSha) { throw 'Integration event request context does not match the verified repository/head/base' }
  Get-Content -LiteralPath $env:COVERAGE_TEST_PROMOTION_EVENT -Raw
  exit 0
} catch { [Console]::Error.WriteLine($_); exit 1 }
'@
    [System.IO.File]::WriteAllText((Join-Path $harness 'verify.ps1'), $verifyDouble, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $harness 'validate-integration-event.ps1'), $eventDouble, [System.Text.UTF8Encoding]::new($false))
    $invoker = @'
param([string]$RepositoryPath,[string]$PublisherPath,[string]$BundlePath,[string]$ReceiptPath,[string]$Root,[string]$Head,[string]$Base,[string]$EventPath,[string]$ComparisonPath,[string]$IndexPath,[string]$ChangedPath,[string]$RunId)
function global:git {
    $arguments=@($args);$forward=[System.Collections.Generic.List[string]]::new()
    for($i=0;$i -lt $arguments.Count;$i++){if($arguments[$i] -eq '-c'){$i++;continue};$forward.Add($arguments[$i])}
    if($forward.Count -ge 3 -and $forward[0] -eq 'remote' -and $forward[1] -eq 'get-url'){Write-Output 'https://github.com/octaviordz/chimali.git';$global:LASTEXITCODE=0;return}
    & $env:COVERAGE_TEST_REAL_GIT @($forward.ToArray());$global:LASTEXITCODE=$LASTEXITCODE
}
Push-Location $RepositoryPath
try {
  & $PublisherPath -RunBundle $BundlePath -OutputReceiptPath $ReceiptPath -PromoteAsAcceptedTarget -AssessmentPath $env:COVERAGE_TEST_PROMOTION_DECISION -IntegrationEventPath $EventPath -ExpectedRepository 'octaviordz/chimali' -ExpectedHeadSha $Head -ExpectedBaseSha $Base -PullRequestNumber 17 -ComparisonBundle $ComparisonPath -AcceptedBaselineIndexPath $IndexPath -ChangedPathsFile $ChangedPath
  exit $LASTEXITCODE
} finally { Pop-Location }
'@
    [System.IO.File]::WriteAllText($invokeScript, $invoker, [System.Text.UTF8Encoding]::new($false))
    [Environment]::SetEnvironmentVariable('COVERAGE_TEST_REAL_GIT', $realGit, 'Process')
    [Environment]::SetEnvironmentVariable('COVERAGE_TEST_PROMOTION_DECISION', $decisionPath, 'Process')
    [Environment]::SetEnvironmentVariable('COVERAGE_TEST_PROMOTION_EVENT', $verifiedEventPath, 'Process')
    [Environment]::SetEnvironmentVariable('CHIMALI_COVERAGE_PUBLISH_TOKEN', 'offline-fixture-token', 'Process')
    $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$invokeScript,
        '-RepositoryPath',$repoRoot,'-PublisherPath',(Join-Path $harness 'publish.ps1'),'-BundlePath',$bundle,'-ReceiptPath',$receiptPath,
        '-Root',$repository,'-Head',$testedCommit,'-Base',$baseCommit,'-EventPath',$eventPath,
        '-ComparisonPath',(Join-Path $tempRoot 'comparison-bundle.json'),'-IndexPath',(Join-Path $tempRoot 'accepted-index.json'),
        '-ChangedPath',(Join-Path $tempRoot 'changed-paths.txt'),'-RunId',$runId)
    & $realPwsh @arguments
    $publishExit = $LASTEXITCODE
    $archiveRef = "refs/heads/$branch"
    $publishedCommit = (& $realGit --git-dir=$bareRemote rev-parse --verify $archiveRef 2>$null).Trim()
    if ($Mode -in @('success', 'target-push')) {
        if ($publishExit -ne 0 -or -not (Test-Path -LiteralPath $receiptPath -PathType Leaf) -or $publishedCommit -eq $initialArchiveCommit) {
            throw "Expected production publisher promotion to succeed; exit=$publishExit; archive=$publishedCommit"
        }
        $receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
        $indexJson = (& $realGit --git-dir=$bareRemote show "$archiveRef`:baselines/current.json") -join "`n"
        $index = $indexJson | ConvertFrom-Json
        if ($receipt.status -cne 'Published' -or $receipt.promoted_as_accepted_target -ne $true -or
            $receipt.run_id -cne $runId -or $receipt.integrated_commit -cne $integratedCommit -or
            $index.run_id -cne $runId -or $index.target_commit -cne $integratedCommit -or
            $index.tested_tree -cne $testedTree -or $index.bundle_path -cne "runs/$runId") {
            throw 'Published promotion receipt and accepted index do not bind the tested candidate and integrated commit.'
        }
        $archivedManifest = (& $realGit --git-dir=$bareRemote show "$archiveRef`:runs/$runId/manifest.json") -join "`n"
        if ($LASTEXITCODE -ne 0 -or ($archivedManifest | ConvertFrom-Json).run_id -cne $runId) { throw 'Promotion archive is missing the exact measured run manifest.' }
        $publicationRecordJson = (& $realGit --git-dir=$bareRemote show "$($archiveRef):assessments/$runId/publication.json") -join [Environment]::NewLine
        if ($LASTEXITCODE -ne 0) { throw 'Promotion archive is missing the immutable retrievable report record.' }
        $publicationRecord = $publicationRecordJson | ConvertFrom-Json
        if ($publicationRecord.run_id -cne $runId -or $publicationRecord.bundle_digest -cne $receipt.bundle_digest -or
            $publicationRecord.archive_branch -cne $branch -or $publicationRecord.archive_ref -cne $receipt.archive_ref -or
            $publicationRecord.report_url -cne $receipt.report_url) {
            throw 'Archived report record does not bind the exact run, archive ref, digest, and report URL.'
        }
        $retainedBlob = (& $realGit --git-dir=$bareRemote rev-parse "$($archiveRef):runs/$historicalRunId/raw.bin").Trim()
        $retainedReceiptBlob = (& $realGit --git-dir=$bareRemote rev-parse "$($archiveRef):runs/$historicalRunId/receipt.json").Trim()
        $retainedReceipt = (& $realGit --git-dir=$bareRemote show "$($archiveRef):runs/$historicalRunId/receipt.json") -join [Environment]::NewLine
        $retainedLegacyRef = (& $realGit --git-dir=$bareRemote rev-parse --verify 'refs/heads/codex/coverage-evidence').Trim()
        $legacyReceipt = $retainedReceipt | ConvertFrom-Json
        if ($retainedBlob -cne $historicalBlob -or $retainedReceiptBlob -cne $historicalReceiptBlob -or
            $legacyReceipt.archive_branch -cne 'codex/coverage-evidence' -or $legacyReceipt.archive_ref -cne 'refs/heads/codex/coverage-evidence' -or
            $retainedLegacyRef -cne $initialArchiveCommit) {
            throw "Promotion did not preserve historical identity (blob $retainedBlob/$historicalBlob, receipt $retainedReceiptBlob/$historicalReceiptBlob, branch $($legacyReceipt.archive_branch)/$($legacyReceipt.archive_ref), ref $retainedLegacyRef/$initialArchiveCommit)."
        }
        Write-Output 'Production publisher verified the candidate, archived the run, advanced the accepted index atomically, and read back the integrated tree.'
        return
    }
    if ($publishExit -eq 0 -or (Test-Path -LiteralPath $receiptPath) -or $publishedCommit -cne $initialArchiveCommit) {
        throw "Expected $Mode promotion rejection before receipt or index effects; exit=$publishExit; receipt=$(Test-Path -LiteralPath $receiptPath); archive=$publishedCommit"
    }
    Write-Output "Production publisher rejected $Mode before writing a receipt or changing the accepted index."
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process') }
    $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-promotion-publisher-*') {
        throw "Refusing unsafe promotion fixture cleanup target: $canonicalTemp"
    }
    if (Test-Path -LiteralPath $canonicalTemp) {
        Get-ChildItem -LiteralPath $canonicalTemp -Force -Recurse | ForEach-Object {
            if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) { $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly) }
        }
        [System.IO.Directory]::Delete($canonicalTemp, $true)
    }
}
