[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot,[ValidateSet('success','stale-archive-index')][string]$Mode = 'success')

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\','/')
. (Join-Path $RepositoryRoot 'tools/coverage/coverage-common.ps1')
$realGit = (Get-Command git.exe -ErrorAction Stop).Source
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-target-refresh-fixture-' + [guid]::NewGuid().ToString('N'))
$sourceRoot = Join-Path $tempRoot 'source'
$archiveSeed = Join-Path $tempRoot 'archive-seed'
$bareRemote = Join-Path $tempRoot 'archive.git'
$bundle = Join-Path $tempRoot 'bundle'
$harness = Join-Path $tempRoot 'harness'
$evidenceRoot = Join-Path $tempRoot 'evidence'
$receiptPath = Join-Path $tempRoot 'publication-receipt.json'
$runId = '00000000-0000-4000-8000-000000000681'
$oldRunId = '00000000-0000-4000-8000-000000000680'
$branch = 'swe/coverage-evidence'
$tokenVariable = 'CHIMALI_COVERAGE_PUBLISH_TOKEN'
$previousToken = [Environment]::GetEnvironmentVariable($tokenVariable, 'Process')

function Invoke-CheckedGit {
    param([string]$WorkingDirectory,[string[]]$Arguments,[string]$Failure)
    & $realGit -C $WorkingDirectory @Arguments
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

function global:git {
    $gitArguments = @($args)
    for ($i = 0; $i -lt $gitArguments.Count; $i++) {
        if ($gitArguments[$i] -eq '-c') { $i++; continue }
        if ($gitArguments[$i] -eq 'remote' -and $i + 2 -lt $gitArguments.Count -and $gitArguments[$i + 1] -eq 'get-url' -and $gitArguments[$i + 2] -eq 'origin') {
            Write-Output 'https://github.com/octaviordz/chimali.git'
            $global:LASTEXITCODE = 0
            return
        }
    }
    & $realGit @gitArguments
    $global:LASTEXITCODE = $LASTEXITCODE
}

function global:gh {
    $ghArguments = @($args)
    if ($ghArguments.Count -eq 2 -and $ghArguments[0] -eq 'auth' -and $ghArguments[1] -eq 'status') {
        Write-Output 'Logged in to github.com as octaviordz'
        $global:LASTEXITCODE = 0
        return
    }
    if ($ghArguments.Count -ge 2 -and $ghArguments[0] -eq 'api') {
        $endpoint = [string]$ghArguments[1]
        $response = switch -Regex ($endpoint) {
            '^user$' { @{ login = 'octaviordz' } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali$' { @{ owner = @{ login = 'octaviordz' } } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali/collaborators/octaviordz/permission$' { @{ permission = 'admin' } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali/branches/main$' { @{ commit = @{ sha = $global:RefreshTargetCommit } } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali/git/commits/' { @{ tree = @{ sha = $global:RefreshTargetTree } } | ConvertTo-Json -Compress; break }
            default { $global:LASTEXITCODE = 1; throw "Unexpected fixture GitHub API endpoint: $endpoint" }
        }
        Write-Output $response
        $global:LASTEXITCODE = 0
        return
    }
    $global:LASTEXITCODE = 1
    throw "Unexpected fixture GitHub CLI invocation: $($ghArguments -join ' ')"
}

try {
    New-Item -ItemType Directory -Path $sourceRoot,$archiveSeed,$bundle,$harness,$evidenceRoot -Force | Out-Null
    & $realGit init --quiet $sourceRoot
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize source fixture repository.' }
    Invoke-CheckedGit $sourceRoot @('config','user.name','Coverage Fixture') 'Unable to configure source author.'
    Invoke-CheckedGit $sourceRoot @('config','user.email','coverage-fixture@example.invalid') 'Unable to configure source email.'
    [System.IO.File]::WriteAllText((Join-Path $sourceRoot 'product.txt'),'unchanged accepted main tree',[System.Text.UTF8Encoding]::new($false))
    Invoke-CheckedGit $sourceRoot @('add','--','product.txt') 'Unable to stage source fixture.'
    Invoke-CheckedGit $sourceRoot @('commit','--quiet','-m','fixture current main') 'Unable to commit source fixture.'
    $targetCommit = (& $realGit -C $sourceRoot rev-parse HEAD).Trim()
    $targetTree = (& $realGit -C $sourceRoot rev-parse 'HEAD^{tree}').Trim()
    $global:RefreshTargetCommit = $targetCommit
    $global:RefreshTargetTree = $targetTree

    & $realGit init --bare --quiet $bareRemote
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize bare archive fixture remote.' }
    Invoke-CheckedGit $sourceRoot @('remote','add','origin',$bareRemote) 'Unable to configure source remote.'
    Invoke-CheckedGit $sourceRoot @('push','--quiet','origin',"${targetCommit}:refs/heads/main") 'Unable to publish fixture main ref.'

    $oldBundle = Join-Path $evidenceRoot "runs/$oldRunId"
    New-Item -ItemType Directory -Path $oldBundle -Force | Out-Null
    $oldManifest = [PSCustomObject]@{
        run_id = $oldRunId; purpose = 'Comparison'
        git = [PSCustomObject]@{ source_commit = $targetCommit; source_tree = $targetTree; target_commit = $targetCommit }
    }
    $oldManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $oldBundle 'manifest.json') -Encoding utf8
    [System.IO.File]::WriteAllText((Join-Path $oldBundle 'summary.md'),'previous accepted target',[System.Text.UTF8Encoding]::new($false))
    $oldBundleDigest = Get-CoverageBundleDigest -BundlePath $oldBundle
    $indexDirectory = Join-Path $evidenceRoot 'baselines'
    New-Item -ItemType Directory -Path $indexDirectory -Force | Out-Null
    $oldIndexPath = Join-Path $indexDirectory 'current.json'
    $oldIndex = [PSCustomObject]@{
        tested_commit = $targetCommit; integrated_commit = $targetCommit; target_tree = $targetTree; tested_tree = $targetTree; target_commit = $targetCommit
        run_id = $oldRunId; archive_branch = $branch; bundle_path = "runs/$oldRunId"; bundle_digest = $oldBundleDigest; provenance = 'MeasuredAcceptedTarget'
    }
    $oldIndex | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $oldIndexPath -Encoding utf8

    & $realGit init --quiet $archiveSeed
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize archive seeder repository.' }
    Invoke-CheckedGit $archiveSeed @('config','user.name','Coverage Fixture') 'Unable to configure archive author.'
    Invoke-CheckedGit $archiveSeed @('config','user.email','coverage-fixture@example.invalid') 'Unable to configure archive email.'
    Invoke-CheckedGit $archiveSeed @('config','core.autocrlf','false') 'Unable to preserve accepted index bytes in archive fixture.'
    Invoke-CheckedGit $archiveSeed @('checkout','--quiet','--orphan',$branch) 'Unable to create archive seed branch.'
    New-Item -ItemType Directory -Path (Join-Path $archiveSeed 'runs') -Force | Out-Null
    Copy-Item -LiteralPath $oldBundle -Destination (Join-Path $archiveSeed "runs/$oldRunId") -Recurse -Force
    New-Item -ItemType Directory -Path (Join-Path $archiveSeed 'baselines') -Force | Out-Null
    Copy-Item -LiteralPath $oldIndexPath -Destination (Join-Path $archiveSeed 'baselines/current.json')
    Invoke-CheckedGit $archiveSeed @('add','--all') 'Unable to stage old accepted baseline.'
    Invoke-CheckedGit $archiveSeed @('commit','--quiet','-m','seed accepted target') 'Unable to commit old accepted baseline.'
    Invoke-CheckedGit $archiveSeed @('remote','add','origin',$bareRemote) 'Unable to configure archive remote.'
    Invoke-CheckedGit $archiveSeed @('push','--quiet','origin',"${branch}:${branch}") 'Unable to seed archive branch.'

    & $realGit init --quiet $harness
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize independent trusted harness.' }
    Invoke-CheckedGit $harness @('config','user.name','Coverage Fixture') 'Unable to configure harness author.'
    Invoke-CheckedGit $harness @('config','user.email','coverage-fixture@example.invalid') 'Unable to configure harness email.'
    $harnessCoverage = Join-Path $harness 'tools/coverage'
    New-Item -ItemType Directory -Path $harnessCoverage -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $harnessCoverage 'policy.json'),'{}',[System.Text.UTF8Encoding]::new($false))
    $verifier = @'
param([string]$RunBundle,[string]$PolicyPath,[string]$OutputDirectory,[switch]$TargetBaselineRefresh)
$ErrorActionPreference = 'Stop'
$manifestPath = Join-Path $RunBundle 'manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$decision = [PSCustomObject]@{
    run_id = [string]$manifest.run_id
    manifest_digest = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    decision_type = 'TargetBaselineRefresh'
    baseline_refresh_ready = $true
    decisions = [PSCustomObject]@{ collection = 'Complete'; tests = 'Passed'; floors = 'Passed'; baseline_refresh = 'Eligible' }
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$decision | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'decision.json') -Encoding utf8
exit 0
'@
    [System.IO.File]::WriteAllText((Join-Path $harnessCoverage 'verify.ps1'),$verifier,[System.Text.UTF8Encoding]::new($false))
    Invoke-CheckedGit $harness @('add','--all') 'Unable to stage trusted harness fixture.'
    Invoke-CheckedGit $harness @('commit','--quiet','-m','fixture trusted verifier') 'Unable to commit trusted harness fixture.'
    $harnessCommit = (& $realGit -C $harness rev-parse HEAD).Trim()

    $manifest = [PSCustomObject]@{
        run_id = $runId; purpose = 'Comparison'; measurement_fingerprint = ('d' * 64)
        git = [PSCustomObject]@{ source_commit = $targetCommit; source_tree = $targetTree; target_commit = $targetCommit; clean_preflight = $true; clean_postflight = $true }
        harness = [PSCustomObject]@{ revision = $harnessCommit; clean = $true }
        artifacts = @()
    }
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
    [System.IO.File]::WriteAllText((Join-Path $bundle 'summary.md'),'owner-reviewed refreshed target',[System.Text.UTF8Encoding]::new($false))

    $env:CHIMALI_COVERAGE_PUBLISH_TOKEN = 'offline-fixture-token'
    $reviewOutput = @(& (Join-Path $RepositoryRoot 'tools/coverage/review-target-baseline-refresh.ps1') `
        -Repository 'octaviordz/chimali' -RunBundle $bundle -EvidenceRoot $evidenceRoot -TrustedHarnessRoot $harness `
        -AcceptedBaselineIndexPath $oldIndexPath -Decision ACCEPT -Reason 'Fixture owner reviewed exact unchanged main target' 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Review record fixture failed: $($reviewOutput -join ' ')" }
    $reviewPathLine = @($reviewOutput | Where-Object { [string]$_ -like 'Target refresh review record:*' } | Select-Object -First 1)
    if (-not $reviewPathLine) { throw 'Review script did not report its immutable review record.' }
    $reviewPath = ([string]$reviewPathLine).Substring('Target refresh review record:'.Length).Trim()

    $expectedRemoteCommit = $null
    if ($Mode -eq 'stale-archive-index') {
        $staleIndex = [PSCustomObject]@{ run_id = '00000000-0000-4000-8000-000000000679'; target_commit = $targetCommit; target_tree = $targetTree; tested_tree = $targetTree; bundle_path = "runs/$oldRunId"; bundle_digest = $oldBundleDigest; provenance = 'MeasuredAcceptedTarget' }
        $staleIndex | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $archiveSeed 'baselines/current.json') -Encoding utf8
        Invoke-CheckedGit $archiveSeed @('add','--','baselines/current.json') 'Unable to stage simulated concurrent accepted-index update.'
        Invoke-CheckedGit $archiveSeed @('commit','--quiet','-m','simulate concurrent accepted index update') 'Unable to commit simulated concurrent accepted-index update.'
        Invoke-CheckedGit $archiveSeed @('push','--quiet','origin',"${branch}:${branch}") 'Unable to publish simulated concurrent accepted-index update.'
        $expectedRemoteCommit = (& $realGit -C $archiveSeed rev-parse HEAD).Trim()
    }

    $invoke = @'
param([string]$RepositoryRoot,[string]$SourceRoot,[string]$Bundle,[string]$EvidenceRoot,[string]$Harness,[string]$Index,[string]$Review,[string]$Receipt,[string]$RealGit)
$ErrorActionPreference = 'Stop'
$global:COVERAGE_REFRESH_REAL_GIT = $RealGit
function global:git {
    $gitArguments = @($args)
    for ($i = 0; $i -lt $gitArguments.Count; $i++) {
        if ($gitArguments[$i] -eq '-c') { $i++; continue }
        if ($gitArguments[$i] -eq 'remote' -and $i + 2 -lt $gitArguments.Count -and $gitArguments[$i + 1] -eq 'get-url' -and $gitArguments[$i + 2] -eq 'origin') {
            Write-Output 'https://github.com/octaviordz/chimali.git'; $global:LASTEXITCODE = 0; return
        }
    }
    & $global:COVERAGE_REFRESH_REAL_GIT @gitArguments
    $global:LASTEXITCODE = $LASTEXITCODE
}
function global:gh {
    $ghArguments = @($args)
    if ($ghArguments.Count -eq 2 -and $ghArguments[0] -eq 'auth' -and $ghArguments[1] -eq 'status') { Write-Output 'Logged in'; $global:LASTEXITCODE = 0; return }
    if ($ghArguments.Count -ge 2 -and $ghArguments[0] -eq 'api') {
        $response = switch -Regex ([string]$ghArguments[1]) {
            '^user$' { @{ login = 'octaviordz' } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali$' { @{ owner = @{ login = 'octaviordz' } } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali/collaborators/octaviordz/permission$' { @{ permission = 'admin' } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali/branches/main$' { @{ commit = @{ sha = $env:COVERAGE_REFRESH_TARGET_COMMIT } } | ConvertTo-Json -Compress; break }
            '^repos/octaviordz/chimali/git/commits/' { @{ tree = @{ sha = $env:COVERAGE_REFRESH_TARGET_TREE } } | ConvertTo-Json -Compress; break }
            default { $global:LASTEXITCODE = 1; throw "Unexpected fixture API endpoint: $($ghArguments[1])" }
        }
        Write-Output $response; $global:LASTEXITCODE = 0; return
    }
    $global:LASTEXITCODE = 1; throw "Unexpected fixture GitHub CLI call: $($ghArguments -join ' ')"
}
$env:COVERAGE_REFRESH_TARGET_COMMIT = $env:COVERAGE_REFRESH_TARGET_COMMIT
$env:COVERAGE_REFRESH_TARGET_TREE = $env:COVERAGE_REFRESH_TARGET_TREE
Push-Location $SourceRoot
try {
    & (Join-Path $RepositoryRoot 'tools/coverage/publish.ps1') -RunBundle $Bundle -EvidenceBranch 'swe/coverage-evidence' -RemoteName origin `
        -OutputReceiptPath $Receipt -RefreshAcceptedTarget -TargetRefreshReviewRecordPath $Review -EvidenceRoot $EvidenceRoot -Repository 'octaviordz/chimali' `
        -AcceptedBaselineIndexPath $Index -VerificationHarnessRoot $Harness
    exit $LASTEXITCODE
} finally { Pop-Location }
'@
    $invokePath = Join-Path $tempRoot 'invoke-refresh-publisher.ps1'
    [System.IO.File]::WriteAllText($invokePath,$invoke,[System.Text.UTF8Encoding]::new($false))
    $env:COVERAGE_REFRESH_TARGET_COMMIT = $targetCommit
    $env:COVERAGE_REFRESH_TARGET_TREE = $targetTree
    $publisherOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $invokePath -RepositoryRoot $RepositoryRoot -SourceRoot $sourceRoot -Bundle $bundle -EvidenceRoot $evidenceRoot `
        -Harness $harness -Index $oldIndexPath -Review $reviewPath -Receipt $receiptPath -RealGit $realGit 2>&1)
    $publisherExit = $LASTEXITCODE
    if ($Mode -eq 'stale-archive-index') {
        $remoteTip = @(& $realGit "--git-dir=$bareRemote" rev-parse "refs/heads/$branch" 2>$null)
        if ($publisherExit -eq 0 -or ($publisherOutput -join ' ') -notmatch 'Archived accepted index changed since owner review' -or
            (Test-Path -LiteralPath $receiptPath) -or $remoteTip.Count -ne 1 -or [string]$remoteTip[0] -cne $expectedRemoteCommit) {
            throw "Stale archive index was not refused without mutation: exit=$publisherExit; tip=$($remoteTip -join ','); output=$($publisherOutput -join ' ')"
        }
        Write-Output 'Production publisher rejected a concurrent accepted-index advance before publication and left the remote archive untouched.'
        return
    }
    if ($publisherExit -ne 0) { throw "Target refresh publisher fixture exited $publisherExit`: $($publisherOutput -join ' ')" }
    if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) { throw 'Refresh publisher did not create a receipt.' }
    $receipt = Read-SafeJson -Path $receiptPath
    if ($receipt.status -cne 'Published' -or $receipt.content_readback_verified -ne $true -or
        $receipt.promoted_as_accepted_target -ne $true -or $receipt.hosted_check -cne 'Unverified' -or
        $receipt.refresh_method -cne 'OwnerReviewedSameTreeMeasurementRefresh' -or $receipt.integrated_commit -cne $targetCommit -or $receipt.integrated_tree -cne $targetTree) {
        throw 'Refresh publication receipt does not bind the owner-reviewed target refresh without a hosted success claim.'
    }
    $remoteIndexOutput = @(& $realGit "--git-dir=$bareRemote" show "refs/heads/$branch`:baselines/current.json" 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Published archive has no readable accepted index.' }
    $remoteIndex = ($remoteIndexOutput -join [Environment]::NewLine) | ConvertFrom-Json -Depth 30
    if ($remoteIndex.run_id -cne $runId -or $remoteIndex.prior_run_id -cne $oldRunId -or
        $remoteIndex.prior_index_digest -cne (Get-FileSha256 -Path (Join-Path $evidenceRoot 'baselines/current.json')) -or
        $remoteIndex.refresh_method -cne 'OwnerReviewedSameTreeMeasurementRefresh') {
        throw 'Published accepted index does not identify this refresh and its reviewed predecessor.'
    }
    $archivedOldManifest = @(& $realGit "--git-dir=$bareRemote" show "refs/heads/$branch`:runs/$oldRunId/manifest.json" 2>&1)
    if ($LASTEXITCODE -ne 0 -or ($archivedOldManifest -join [Environment]::NewLine) -notmatch [regex]::Escape($oldRunId)) {
        throw 'Refresh did not preserve the prior accepted run.'
    }
    Write-Output 'Production publisher refreshed the accepted index for the exact unchanged target, preserved the prior run and owner review, and verified archive read-back.'
} finally {
    [Environment]::SetEnvironmentVariable($tokenVariable,$previousToken,'Process')
    Remove-Item Function:\global:git,Function:\global:gh -ErrorAction SilentlyContinue
    Remove-Variable -Name RefreshTargetCommit,RefreshTargetTree -Scope Global -ErrorAction SilentlyContinue
    $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/')
    if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -cne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-target-refresh-fixture-*') {
        throw "Refusing to remove unexpected target-refresh fixture path: $canonicalTemp"
    }
    if (Test-Path -LiteralPath $canonicalTemp) {
        Get-ChildItem -LiteralPath $canonicalTemp -Force -Recurse | ForEach-Object {
            if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) { $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly) }
        }
        [System.IO.Directory]::Delete($canonicalTemp,$true)
    }
}
