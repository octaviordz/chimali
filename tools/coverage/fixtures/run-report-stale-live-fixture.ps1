[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath($RepositoryRoot)
$realGit = (Get-Command git.exe -ErrorAction Stop).Source
$realPwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('coverage-report-stale-live-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$bundle = Join-Path $tempRoot 'run'
$reportRoot = Join-Path $tempRoot 'report-repository'
$bare = Join-Path $tempRoot 'archive.git'
$wrapperRoot = Join-Path $tempRoot 'git-wrapper'
$livePath = Join-Path $tempRoot 'live-pr.json'
$apiPath = Join-Path $tempRoot 'api-record.json'
$driverPath = Join-Path $tempRoot 'invoke-report.ps1'
$runId = [guid]::NewGuid().ToString()
$head = 'a' * 40
$capturedBase = 'b' * 40
$liveBase = 'c' * 40
$tree = 'd' * 40
$workflowSha = 'e' * 40
$reporterSha = 'f' * 40
$repositoryId = '12345678'
$appId = '7890'
$checkId = 7812345
$envNames = @('COVERAGE_REPORT_API_RECORD', 'COVERAGE_REPORT_FIXTURE_PEM', 'PATH')
$previousEnv = @{}
foreach ($name in $envNames) { $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

function Invoke-CheckedGit([string]$Directory, [string[]]$Arguments, [string]$Failure) {
    & $realGit -C $Directory @Arguments
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

try {
    New-Item -ItemType Directory -Path $bundle,$reportRoot,$wrapperRoot -Force | Out-Null
    & $realGit init --quiet $reportRoot
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize the isolated reporter repository' }
    Invoke-CheckedGit $reportRoot @('config','user.name','Coverage Reporter Fixture') 'Unable to configure reporter fixture author'
    Invoke-CheckedGit $reportRoot @('config','user.email','coverage-reporter@example.invalid') 'Unable to configure reporter fixture email'
    [IO.File]::WriteAllText((Join-Path $reportRoot 'source.txt'), 'offline stale target fixture', [Text.UTF8Encoding]::new($false))
    Invoke-CheckedGit $reportRoot @('add','--','source.txt') 'Unable to stage reporter fixture source'
    Invoke-CheckedGit $reportRoot @('commit','--quiet','-m','fixture source') 'Unable to commit reporter fixture source'
    & $realGit init --bare --quiet $bare
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize local bare archive' }
    Invoke-CheckedGit $reportRoot @('remote','add','origin',$bare) 'Unable to configure local bare archive'
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

    $manifest = [PSCustomObject]@{
        run_id = $runId
        schema_version = '1.0'
        purpose = 'Candidate'
        git = [PSCustomObject]@{ source_commit = $head; source_tree = $tree; target_commit = $capturedBase }
        trusted_reporter_binding = [PSCustomObject]@{
            workflow_commit = $workflowSha; reporter_sha = $reporterSha; repository_id = $repositoryId; app_id = $appId
        }
    }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
    $live = [PSCustomObject]@{
        pull_request = [PSCustomObject]@{
            number = 17; state = 'open'
            head = [PSCustomObject]@{ sha = $head; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
            base = [PSCustomObject]@{ sha = $liveBase; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
        }
        head_commit = [PSCustomObject]@{ sha = $head; commit = [PSCustomObject]@{ tree = [PSCustomObject]@{ sha = $tree } } }
    }
    $live | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $livePath -Encoding utf8

    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        $pemBody = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey(), [Base64FormattingOptions]::InsertLineBreaks)
        $env:COVERAGE_REPORT_FIXTURE_PEM = "-----BEGIN PRIVATE KEY-----`n$pemBody`n-----END PRIVATE KEY-----"
    } finally { $rsa.Dispose() }

    $driver = @'
param([string]$Repository,[string]$Bundle,[string]$Live,[string]$RunId,[string]$Head,[string]$Base,[string]$WorkflowSha,[string]$ReporterSha,[string]$RepositoryId,[string]$AppId,[string]$CheckId,[string]$ServerUrl,[string]$ReportRoot,[string]$ApiRecord)
$global:coverageApiRequests = [System.Collections.Generic.List[object]]::new()
function global:Invoke-RestMethod {
    param([string]$Uri,[string]$Method,[hashtable]$Headers,[string]$Body,[string]$ContentType)
    if ($Uri -match '/app/installations/[^/]+/access_tokens$' -and $Method -eq 'Post') {
        $global:coverageApiRequests.Add([PSCustomObject]@{kind='installation-token';uri=$Uri})
        $global:coverageApiRequests | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $env:COVERAGE_REPORT_API_RECORD -Encoding utf8
        return [PSCustomObject]@{token='offline-reporter-token'}
    }
    if ($Uri -match '/repos/octaviordz/chimali/check-runs$' -and $Method -eq 'Post') {
        $payload = $Body | ConvertFrom-Json -ErrorAction Stop
        $global:coverageApiRequests.Add([PSCustomObject]@{kind='check-run';uri=$Uri;payload=$payload})
        $global:coverageApiRequests | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $env:COVERAGE_REPORT_API_RECORD -Encoding utf8
        return [PSCustomObject]@{id=7812345;html_url='https://github.com/octaviordz/chimali/checks/7812345'}
    }
    throw "Unexpected API request in stale-target fixture: $Method $Uri"
}
Push-Location $ReportRoot
try {
    & (Join-Path $Repository 'tools/coverage/report.ps1') -RunBundle $Bundle -Repository 'octaviordz/chimali' `
      -HeadSha $Head -BaseSha $Base -AppId $AppId -AppPrivateKey $env:COVERAGE_REPORT_FIXTURE_PEM -InstallationId '98765' `
      -LiveEventName pull_request -PullRequestNumber 17 -LiveEvidencePath $Live -ServerUrl $ServerUrl `
      -ExpectedReporterWorkflowSha $WorkflowSha -ExpectedReporterSha $ReporterSha `
      -ExpectedRepositoryId $RepositoryId -ExpectedCoverageAppId $AppId `
      -ArtifactId '12345' -ArtifactDigest ('sha256:' + ('9' * 64))
    exit $LASTEXITCODE
} finally { Pop-Location }
'@
    [IO.File]::WriteAllText($driverPath, $driver, [Text.UTF8Encoding]::new($false))
    $env:COVERAGE_REPORT_API_RECORD = $apiPath
    $env:PATH = "$wrapperRoot;$env:PATH"
    & $realPwsh -NoProfile -ExecutionPolicy Bypass -File $driverPath `
        -Repository $repository -Bundle $bundle -Live $livePath -RunId $runId -Head $head -Base $capturedBase `
        -WorkflowSha $workflowSha -ReporterSha $reporterSha -RepositoryId $repositoryId -AppId $appId `
        -CheckId $checkId -ServerUrl 'http://coverage-api.invalid' -ReportRoot $reportRoot -ApiRecord $apiPath
    $reportExit = $LASTEXITCODE
    $requests = @(Get-Content -LiteralPath $apiPath -Raw | ConvertFrom-Json -ErrorAction Stop)
    if ($requests.Count -ne 2 -or $requests[0].kind -cne 'installation-token' -or $requests[1].kind -cne 'check-run') {
        throw "Stale live candidate did not reach authenticated failure-check creation; exit=$reportExit requests=$($requests.Count)"
    }
    $payload = $requests[1].payload
    if ($reportExit -ne 1 -or $payload.conclusion -cne 'failure' -or $payload.head_sha -cne $head -or
        $payload.output.summary -notmatch 'changed after capture') {
        throw "Stale target did not publish a reasoned failure check for the trusted captured head: exit=$reportExit payload=$($payload | ConvertTo-Json -Depth 8 -Compress)"
    }
    Write-Output 'Production reporter published an App failure check with the stale-base reason instead of exiting before invalidating prior success.'
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process') }
    $resolved = [IO.Path]::GetFullPath($tempRoot)
    if ([IO.Directory]::Exists($resolved)) {
        if (-not $resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolved) -notlike 'coverage-report-stale-live-*') {
            throw "Refusing unsafe stale live reporter fixture cleanup target: $resolved"
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
