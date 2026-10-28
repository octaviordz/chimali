[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$HeadSha,
    [Parameter(Mandatory = $true)][string]$BaseSha,
    [Parameter(Mandatory = $true)][string]$IntegrationEventPath,
    [int]$PullRequestNumber,
    [string]$Token,
    [string]$ServerUrl = 'https://github.com',
    [string]$LiveEvidencePath
)

$ErrorActionPreference = 'Stop'
function Get-Response([string]$Key, [string]$Path, $Fixture, [hashtable]$Headers, [string]$ApiRoot, [string]$Repo) {
    if ($Fixture) {
        $value = $Fixture.$Key
        if (-not $value) { throw "Offline integration evidence is missing '$Key'" }
        return $value
    }
    Invoke-RestMethod -Uri "$ApiRoot/repos/$Repo/$Path" -Headers $Headers -Method Get
}
try {
    if ($Repository -notmatch '^[^/]+/[^/]+$' -or $HeadSha -notmatch '^[0-9a-fA-F]{40,64}$' -or $BaseSha -notmatch '^[0-9a-fA-F]{40,64}$') { throw 'Repository/head/base identity is malformed' }
    $event = Get-Content -LiteralPath $IntegrationEventPath -Raw | ConvertFrom-Json -ErrorAction Stop
    if ($PullRequestNumber -lt 1 -and [string]$event.pull_request_number -match '^[1-9][0-9]*$') { $PullRequestNumber = [int]$event.pull_request_number }
    if ($PullRequestNumber -lt 1 -and [string]$event.number -match '^[1-9][0-9]*$') { $PullRequestNumber = [int]$event.number }
    if ($PullRequestNumber -lt 1) { throw 'A pull request number is required to establish integration' }
    if ([string]$event.repository -cne $Repository -or [string]$event.head_sha -cne $HeadSha -or [string]$event.base_sha -cne $BaseSha) { throw 'Integration event request context does not match the verified repository/head/base' }

    $fixture = if ($LiveEvidencePath) { Get-Content -LiteralPath $LiveEvidencePath -Raw | ConvertFrom-Json -ErrorAction Stop } else { $null }
    if (-not $fixture -and [string]::IsNullOrWhiteSpace($Token)) { throw 'Authenticated repository API access is required to establish integration metadata' }
    $apiRoot = if ($ServerUrl -eq 'https://github.com') { 'https://api.github.com' } else { "$($ServerUrl.TrimEnd('/'))/api/v3" }
    $headers = @{ Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
    if (-not $fixture) { $headers.Authorization = "Bearer $Token" }
    $pr = Get-Response 'pull_request' "pulls/$PullRequestNumber" $fixture $headers $apiRoot $Repository
    if ($pr.state -cne 'closed' -or $pr.merged -isnot [bool] -or -not $pr.merged -or [string]::IsNullOrWhiteSpace([string]$pr.merged_at) -or
        [string]$pr.head.sha -cne $HeadSha -or [string]$pr.head.repo.full_name -cne $Repository -or [string]$pr.base.repo.full_name -cne $Repository) {
        throw 'Repository API does not confirm a completed merge of the verified head in the expected repository'
    }
    if ($event.base_ref -and [string]$pr.base.ref -cne [string]$event.base_ref) { throw 'Merged pull request target ref differs from the event context' }
    if ([string]$pr.merge_commit_sha -notmatch '^[0-9a-fA-F]{40,64}$') { throw 'Repository API did not provide the actual merge commit SHA' }
    $commit = Get-Response 'integration_commit' "commits/$($pr.merge_commit_sha)" $fixture $headers $apiRoot $Repository
    if ([string]$commit.sha -cne [string]$pr.merge_commit_sha -or [string]$commit.commit.tree.sha -notmatch '^[0-9a-fA-F]{40,64}$') { throw 'Repository API did not return verifiable merge commit/tree metadata' }
    [PSCustomObject]@{ valid = $true; event_type = 'pull_request'; merged = $true; repository = $Repository; head_sha = $HeadSha; base_sha = $BaseSha; integration_commit = [string]$commit.sha; integration_tree = [string]$commit.commit.tree.sha } | ConvertTo-Json -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Integration event rejected: $_")
    exit 1
}
