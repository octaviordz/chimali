[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$CandidateBundle,
    [Parameter(Mandatory = $true)][string]$ComparisonBundle,
    [Parameter(Mandatory = $true)][string]$AcceptedBaselineIndexPath,
    [Parameter(Mandatory = $true)][string]$ChangedPathsFile
)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath($RepositoryRoot)
$bundle = [IO.Path]::GetFullPath($CandidateBundle)
$comparison = [IO.Path]::GetFullPath($ComparisonBundle)
$indexPath = [IO.Path]::GetFullPath($AcceptedBaselineIndexPath)
$changedPaths = [IO.Path]::GetFullPath($ChangedPathsFile)
$commonScript = Join-Path $repository 'tools/coverage/coverage-common.ps1'
if (-not (Test-Path -LiteralPath $commonScript -PathType Leaf)) { throw "Trusted coverage helpers are missing: $commonScript" }
. $commonScript
$reportScript = Join-Path $repository 'tools/coverage/report.ps1'
$realGit = (Get-Command git.exe -ErrorAction Stop).Source
$realPwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('coverage-report-success-protocol-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$reportRoot = Join-Path $tempRoot 'report-repository'
$bare = Join-Path $tempRoot 'archive.git'
$wrapperRoot = Join-Path $tempRoot 'git-wrapper'
$livePath = Join-Path $tempRoot 'live-pr.json'
$apiRecordPath = Join-Path $tempRoot 'api-double.json'
$driverPath = Join-Path $tempRoot 'invoke-report.ps1'
$checkId = 7812345
$envNames = @('COVERAGE_REPORT_TEST_API_RECORD', 'COVERAGE_REPORT_TEST_PEM', 'PATH')
$previousEnv = @{}
foreach ($name in $envNames) { $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

function Invoke-CheckedGit([string]$Directory, [string[]]$Arguments, [string]$Failure) {
    & $realGit -C $Directory @Arguments
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

try {
    foreach ($path in @($reportScript, $bundle, $comparison, $indexPath, $changedPaths)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Required trusted reporter input is missing: $path" }
    }
    $manifest = Get-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Raw | ConvertFrom-Json -ErrorAction Stop
    if ($manifest.purpose -cne 'Candidate' -or -not $manifest.trusted_reporter_binding) {
        throw 'Success protocol requires a Candidate bundle with trusted reporter identity binding'
    }
    $runId = [string]$manifest.run_id
    $head = [string]$manifest.git.source_commit
    $base = [string]$manifest.git.target_commit
    $tree = [string]$manifest.git.source_tree
    $liveHead = 'd' * 40
    $workflowSha = [string]$manifest.trusted_reporter_binding.workflow_commit
    $reporterSha = [string]$manifest.trusted_reporter_binding.reporter_sha
    $repositoryId = [string]$manifest.trusted_reporter_binding.repository_id
    $appId = [string]$manifest.trusted_reporter_binding.app_id
    if ($manifest.git.clean_preflight -ne $true -or $manifest.git.clean_postflight -ne $true -or
        [string]$manifest.harness.revision -notmatch '^[0-9a-f]{40,64}$') {
        throw 'Candidate must be a clean capture with a pinned trusted harness'
    }

    New-Item -ItemType Directory -Path $tempRoot,$reportRoot,$wrapperRoot -Force | Out-Null
    & $realGit init --quiet $reportRoot
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize isolated reporter Git repository' }
    Invoke-CheckedGit $reportRoot @('config','user.name','Coverage Report Fixture') 'Unable to configure fixture author name'
    Invoke-CheckedGit $reportRoot @('config','user.email','coverage-report@example.invalid') 'Unable to configure fixture author email'
    [IO.File]::WriteAllText((Join-Path $reportRoot 'archive-seed.txt'), 'offline report protocol', [Text.UTF8Encoding]::new($false))
    Invoke-CheckedGit $reportRoot @('add','--','archive-seed.txt') 'Unable to stage isolated archive seed'
    Invoke-CheckedGit $reportRoot @('commit','--quiet','-m','offline reporter archive seed') 'Unable to commit isolated archive seed'
    & $realGit init --bare --quiet $bare
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize isolated bare archive' }
    Invoke-CheckedGit $reportRoot @('remote','add','origin',$bare) 'Unable to configure isolated archive remote'

    $gitWrapper = @"
@echo off
if /i "%~1"=="remote" if /i "%~2"=="get-url" (
  echo https://github.com/octaviordz/chimali.git
  exit /b 0
)
"$realGit" %*
exit /b %ERRORLEVEL%
"@
    [IO.File]::WriteAllText((Join-Path $wrapperRoot 'git.cmd'), $gitWrapper, [Text.UTF8Encoding]::new($false))

    $live = [PSCustomObject]@{
        pull_request = [PSCustomObject]@{
            number = 17; state = 'open'
            head = [PSCustomObject]@{ sha = $liveHead; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
            base = [PSCustomObject]@{ sha = $base; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
        }
        head_commit = [PSCustomObject]@{ sha = $head; commit = [PSCustomObject]@{ tree = [PSCustomObject]@{ sha = $tree } } }
        pull_request_head_commit = [PSCustomObject]@{ sha = $liveHead; commit = [PSCustomObject]@{ tree = [PSCustomObject]@{ sha = $tree } } }
        comparison = [PSCustomObject]@{
            status = 'ahead'; ahead_by = 2; behind_by = 0
            base_commit = [PSCustomObject]@{ sha = $base }
            merge_base_commit = [PSCustomObject]@{ sha = $base }
        }
    }
    $live | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $livePath -Encoding utf8

    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        $pemBody = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey(), [Base64FormattingOptions]::InsertLineBreaks)
        $env:COVERAGE_REPORT_TEST_PEM = "-----BEGIN PRIVATE KEY-----`n$pemBody`n-----END PRIVATE KEY-----"
    } finally { $rsa.Dispose() }

    $driver = @'
param([string]$Repository,[string]$Bundle,[string]$Comparison,[string]$Index,[string]$Changed,[string]$Live,[string]$Head,[string]$Base,[string]$WorkflowSha,[string]$ReporterSha,[string]$RepositoryId,[string]$AppId,[string]$ServerUrl,[string]$ReportRoot)
$global:coverageApiRequests = [System.Collections.Generic.List[object]]::new()
function global:Invoke-RestMethod {
    param([string]$Uri,[string]$Method,[hashtable]$Headers,[string]$Body,[string]$ContentType)
    if ($Uri -match '/app/installations/[^/]+/access_tokens$' -and $Method -eq 'Post') {
        $global:coverageApiRequests.Add([PSCustomObject]@{kind='installation-token';uri=$Uri})
        $global:coverageApiRequests | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $env:COVERAGE_REPORT_TEST_API_RECORD -Encoding utf8
        return [PSCustomObject]@{token='offline-report-token'}
    }
    if ($Uri -match '/repos/octaviordz/chimali/check-runs$' -and $Method -eq 'Post') {
        $payload = $Body | ConvertFrom-Json -ErrorAction Stop
        $global:coverageApiRequests.Add([PSCustomObject]@{kind='check-run';uri=$Uri;payload=$payload})
        $global:coverageApiRequests | ConvertTo-Json -Depth 18 | Set-Content -LiteralPath $env:COVERAGE_REPORT_TEST_API_RECORD -Encoding utf8
        return [PSCustomObject]@{id=7812345;html_url='https://github.com/octaviordz/chimali/checks/7812345'}
    }
    throw "Unexpected API request in report success fixture: $Method $Uri"
}
Push-Location $ReportRoot
try {
    & (Join-Path $Repository 'tools/coverage/report.ps1') -RunBundle $Bundle -Repository 'octaviordz/chimali' `
      -HeadSha $Head -BaseSha $Base -AppId $AppId -AppPrivateKey $env:COVERAGE_REPORT_TEST_PEM -InstallationId '98765' `
      -ComparisonBundle $Comparison -AcceptedBaselineIndexPath $Index -ChangedPathsFile $Changed `
      -LiveEventName pull_request -PullRequestNumber 17 -LiveEvidencePath $Live -ServerUrl $ServerUrl `
      -ExpectedReporterWorkflowSha $WorkflowSha -ExpectedReporterSha $ReporterSha `
      -ExpectedRepositoryId $RepositoryId -ExpectedCoverageAppId $AppId `
      -ArtifactId '12345' -ArtifactDigest ('sha256:' + ('9' * 64))
    exit $LASTEXITCODE
} finally { Pop-Location }
'@
    [IO.File]::WriteAllText($driverPath, $driver, [Text.UTF8Encoding]::new($false))
    $env:COVERAGE_REPORT_TEST_API_RECORD = $apiRecordPath
    $env:PATH = "$wrapperRoot;$env:PATH"
    & $realPwsh -NoProfile -ExecutionPolicy Bypass -File $driverPath `
        -Repository $repository -Bundle $bundle -Comparison $comparison -Index $indexPath -Changed $changedPaths `
        -Live $livePath -Head $head -Base $base -WorkflowSha $workflowSha -ReporterSha $reporterSha `
        -RepositoryId $repositoryId -AppId $appId -ServerUrl 'http://coverage-api.invalid' -ReportRoot $reportRoot
    $reportExit = $LASTEXITCODE
    if (-not (Test-Path -LiteralPath $apiRecordPath -PathType Leaf)) { throw "Reporter made no API requests; exit=$reportExit" }
    $requests = @(Get-Content -LiteralPath $apiRecordPath -Raw | ConvertFrom-Json -ErrorAction Stop)
    if ($requests.Count -ne 2 -or $requests[0].kind -cne 'installation-token' -or $requests[1].kind -cne 'check-run') {
        throw "Reporter did not make exactly the expected token/check API calls; exit=$reportExit requestCount=$($requests.Count)"
    }
    $assessmentDirectory = Join-Path (Join-Path (Split-Path -Parent $bundle) 'assessments') $runId
    $decision = Get-Content -LiteralPath (Join-Path $assessmentDirectory 'decision.json') -Raw | ConvertFrom-Json -ErrorAction Stop
    $publicationFiles = @(Get-ChildItem -LiteralPath $assessmentDirectory -Filter 'publication-receipt-*.json' -File)
    $reporterFiles = @(Get-ChildItem -LiteralPath $assessmentDirectory -Filter 'reporter-receipt-*.json' -File)
    if ($reportExit -ne 0 -or $decision.overall_passed -ne $true -or $publicationFiles.Count -ne 1 -or $reporterFiles.Count -ne 1) {
        throw "Production report did not complete the passing lifecycle: exit=$reportExit pass=$($decision.overall_passed) publication=$($publicationFiles.Count) reporter=$($reporterFiles.Count)"
    }
    $publication = Get-Content -LiteralPath $publicationFiles[0].FullName -Raw | ConvertFrom-Json
    $reporter = Get-Content -LiteralPath $reporterFiles[0].FullName -Raw | ConvertFrom-Json
    $payload = $requests[1].payload
    if ($publication.status -cne 'Published' -or $publication.publication_purpose -cne 'Candidate' -or
        $publication.content_readback_verified -ne $true -or $publication.bundle_digest -ne (Get-CoverageBundleDigest -BundlePath $bundle) -or
        $reporter.status -cne 'Published' -or $reporter.conclusion -cne 'success' -or $reporter.check_id -ne $checkId -or
        $reporter.pull_request_head_sha -cne $liveHead -or $reporter.accepted_base_sha -cne $base -or
        $reporter.tested_candidate_sha -cne $head -or $reporter.tested_candidate_tree -cne $tree -or
        $reporter.check_destination_sha -cne $liveHead -or -not $reporter.live_candidate_binding.valid -or
        $reporter.live_candidate_binding.pull_request_head_sha -cne $liveHead -or
        $reporter.run_id -cne $runId -or $payload.conclusion -cne 'success' -or $payload.head_sha -cne $liveHead -or
        $payload.output.summary -match 'changed after capture') {
        throw 'Published archive/check did not bind the passing success to the verified candidate run and head'
    }
    $archiveCommit = (& $realGit --git-dir=$bare rev-parse refs/heads/swe/coverage-evidence).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $archiveCommit -or $publication.archive_commit -cne $archiveCommit -or $publication.read_back_commit -cne $archiveCommit) {
        throw 'Success report did not retain the exact archive commit read-back'
    }
    Write-Output "Production report success protocol passed: run=$runId check=$checkId archive=$archiveCommit"
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process') }
    $resolved = [IO.Path]::GetFullPath($tempRoot)
    if ([IO.Directory]::Exists($resolved)) {
        if (-not $resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolved) -notlike 'coverage-report-success-protocol-*') {
            throw "Refusing unsafe reporter fixture cleanup target: $resolved"
        }
        foreach ($file in [IO.Directory]::EnumerateFiles($resolved, '*', [IO.SearchOption]::AllDirectories)) {
            $attributes = [IO.File]::GetAttributes($file)
            if (($attributes -band [IO.FileAttributes]::ReadOnly) -ne 0) {
                [IO.File]::SetAttributes($file, $attributes -band (-bnot [IO.FileAttributes]::ReadOnly))
            }
        }
        [IO.Directory]::Delete($resolved, $true)
    }
}
