[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$HeadSha,
    [Parameter(Mandatory = $true)][string]$BaseSha,
    [Parameter(Mandatory = $true)][string]$TreeSha,
    [Parameter(Mandatory = $true)][ValidateSet('pull_request', 'merge_group')][string]$EventName,
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
        if ($pr.state -cne 'open' -or [string]$pr.head.sha -cne $HeadSha -or [string]$pr.base.sha -cne $BaseSha -or
            [string]$pr.head.repo.full_name -cne $Repository -or [string]$pr.base.repo.full_name -cne $Repository) {
            throw 'Live pull-request head, base, state, or repository changed after capture'
        }
    } else {
        if ($TargetRef -notmatch '^refs/heads/[A-Za-z0-9._/-]+$' -or $TargetRef -match '(^|/)\.\.(/|$)') {
            throw 'Merge-group validation requires an explicit safe refs/heads target'
        }
        $branchPath = [uri]::EscapeDataString($TargetRef.Substring('refs/heads/'.Length))
        $ref = Get-LiveResponse "git/ref/heads/$branchPath" $fixture 'target_ref' $headers $apiRoot $Repository
        if ([string]$ref.object.sha -cne $BaseSha) { throw 'Configured integration target advanced after capture' }
    }

    $commit = Get-LiveResponse "commits/$HeadSha" $fixture 'head_commit' $headers $apiRoot $Repository
    if ([string]$commit.sha -cne $HeadSha -or [string]$commit.commit.tree.sha -cne $TreeSha) {
        throw 'Live candidate commit/tree does not match the measured candidate'
    }
    [PSCustomObject]@{ valid = $true; repository = $Repository; head_sha = $HeadSha; base_sha = $BaseSha; tree_sha = $TreeSha; event = $EventName } |
        ConvertTo-Json -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Live candidate binding rejected: $_")
    exit 1
}
