[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$HeadSha,
    [Parameter(Mandatory = $true)][string]$BaseSha,
    [Parameter(Mandatory = $true)][string]$TreeSha,
    [string]$CheckDestinationSha,
    [Parameter(Mandatory = $true)][ValidateSet('pull_request', 'merge_group', 'push')][string]$EventName,
    [int]$PullRequestNumber,
    [string]$TargetRef,
    [string]$ServerUrl = 'https://github.com',
    [string]$Token,
    [string]$LiveEvidencePath
)

$ErrorActionPreference = 'Stop'
function Assert-FullSha([string]$Value, [string]$Name) {
    if ($Value -notmatch '^[0-9a-fA-F]{40,64}$') { throw "$Name must be a full Git object SHA" }
}
function Get-LiveResponse([string]$RelativePath, $Fixture, [string]$FixtureKey, [hashtable]$Headers, [string]$ApiRoot, [string]$Repo) {
    if ($Fixture) {
        $response = $Fixture.$FixtureKey
        if (-not $response) { throw "Offline live evidence is missing '$FixtureKey'" }
        return $response
    }
    return Invoke-RestMethod -Uri "$ApiRoot/repos/$Repo/$RelativePath" -Headers $Headers -Method Get
}

try {
    if ($Repository -notmatch '^[^/]+/[^/]+$') { throw 'Repository must be owner/name' }
    Assert-FullSha $HeadSha 'HeadSha'
    Assert-FullSha $BaseSha 'BaseSha'
    Assert-FullSha $TreeSha 'TreeSha'
    if ([string]::IsNullOrWhiteSpace($CheckDestinationSha)) { $CheckDestinationSha = $HeadSha }
    Assert-FullSha $CheckDestinationSha 'CheckDestinationSha'
    $fixture = if ($LiveEvidencePath) { Get-Content -LiteralPath $LiveEvidencePath -Raw | ConvertFrom-Json -ErrorAction Stop } else { $null }
    $apiRoot = if ($ServerUrl -eq 'https://github.com') { 'https://api.github.com' } else { "$($ServerUrl.TrimEnd('/'))/api/v3" }
    $headers = @{ Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
    if (-not $fixture) {
        if ([string]::IsNullOrWhiteSpace($Token)) { throw 'Actions read token is required to validate current candidate metadata' }
        $headers.Authorization = "Bearer $Token"
    }

    if ($EventName -eq 'pull_request') {
        if ($PullRequestNumber -lt 1) { throw 'A validated pull request number is required' }
        $pr = Get-LiveResponse "pulls/$PullRequestNumber" $fixture 'pull_request' $headers $apiRoot $Repository
        $livePullRequestHead = [string]$pr.head.sha
        Assert-FullSha $livePullRequestHead 'Live pull-request head SHA'
        if ($pr.state -cne 'open' -or [string]$pr.base.sha -cne $BaseSha -or
            [string]$pr.head.repo.full_name -cne $Repository -or [string]$pr.base.repo.full_name -cne $Repository) {
            throw 'Live pull-request base, state, or repository changed after capture'
        }
        if ($CheckDestinationSha -cne $HeadSha -and $CheckDestinationSha -cne $livePullRequestHead) {
            throw 'Check destination is not bound to either the captured or current PR head'
        }
        $comparison = Get-LiveResponse "compare/$BaseSha...$livePullRequestHead" $fixture 'comparison' $headers $apiRoot $Repository
        if ([string]$comparison.status -notin @('ahead', 'identical') -or
            [int]$comparison.behind_by -ne 0 -or [string]$comparison.base_commit.sha -cne $BaseSha -or
            [string]$comparison.merge_base_commit.sha -cne $BaseSha) {
            throw 'Pull-request head does not contain the exact accepted base; capture must test an up-to-date integrated product tree'
        }
    } else {
        if ($TargetRef -notmatch '^refs/heads/[A-Za-z0-9._/-]+$' -or $TargetRef -match '(^|/)\.\.(/|$)') {
            throw 'Merge-group validation requires an explicit safe refs/heads target'
        }
        $branchPath = [uri]::EscapeDataString($TargetRef.Substring('refs/heads/'.Length))
        $ref = Get-LiveResponse "git/ref/heads/$branchPath" $fixture 'target_ref' $headers $apiRoot $Repository
        $expectedRefSha = if ($EventName -eq 'push') { $HeadSha } else { $BaseSha }
        if ([string]$ref.object.sha -cne $expectedRefSha) { throw 'Configured integration target advanced or differs from the captured target-push commit' }
    }

    $commit = Get-LiveResponse "commits/$HeadSha" $fixture 'head_commit' $headers $apiRoot $Repository
    if ([string]$commit.sha -cne $HeadSha -or [string]$commit.commit.tree.sha -cne $TreeSha) {
        throw 'Live tested-candidate commit/tree does not match the measured product tree'
    }
    if ($EventName -eq 'pull_request') {
        $liveHeadCommit = if ($livePullRequestHead -ceq $HeadSha) { $commit } else { Get-LiveResponse "commits/$livePullRequestHead" $fixture 'pull_request_head_commit' $headers $apiRoot $Repository }
        if ([string]$liveHeadCommit.sha -cne $livePullRequestHead -or [string]$liveHeadCommit.commit.tree.sha -cne $TreeSha) {
            throw 'Current PR head does not preserve the exact tested product tree'
        }
    }
    if ($EventName -eq 'merge_group' -and @($commit.parents | ForEach-Object { [string]$_.sha }) -notcontains $BaseSha) {
        throw 'Merge-group candidate is not a direct integration of the exact accepted base'
    }
    if ($EventName -eq 'push') {
        $comparison = Get-LiveResponse "compare/$BaseSha...$HeadSha" $fixture 'comparison' $headers $apiRoot $Repository
        if ([string]$comparison.status -ne 'ahead' -or [int]$comparison.behind_by -ne 0 -or
            [string]$comparison.base_commit.sha -cne $BaseSha -or [string]$comparison.merge_base_commit.sha -cne $BaseSha) {
            throw 'Target push does not advance directly from the exact accepted base; the push event must be recaptured against the current accepted baseline'
        }
    }
    [PSCustomObject]@{
        valid = $true; repository = $Repository; event = $EventName
        pull_request_head_sha = if ($EventName -eq 'pull_request') { $livePullRequestHead } else { $null }
        accepted_base_sha = $BaseSha; tested_candidate_sha = $HeadSha; tested_candidate_tree = $TreeSha
        check_destination_sha = if ($EventName -eq 'pull_request') { $livePullRequestHead } else { $HeadSha }
    } |
        ConvertTo-Json -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Live candidate binding rejected: $_")
    exit 1
}
