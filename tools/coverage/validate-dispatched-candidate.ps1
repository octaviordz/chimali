[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$CandidateSha,
    [Parameter(Mandatory = $true)][string]$AcceptedBaseSha,
    [Parameter(Mandatory = $true)][string]$ExpectedTargetRef,
    [string]$ServerUrl = 'https://github.com',
    [string]$Token,
    [string]$LiveEvidencePath,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
try {
    if ($Repository -notmatch '^[^/]+/[^/]+$' -or $CandidateSha -notmatch '^[0-9a-fA-F]{40,64}$' -or
        $AcceptedBaseSha -notmatch '^[0-9a-fA-F]{40,64}$' -or $ExpectedTargetRef -notmatch '^refs/heads/[A-Za-z0-9._/-]+$' -or
        $ExpectedTargetRef -match '(^|/)\.\.(/|$)') { throw 'Dispatched candidate repository, commit, or target identity is malformed' }
    $fixture = if ($LiveEvidencePath) { Get-Content -LiteralPath $LiveEvidencePath -Raw | ConvertFrom-Json -ErrorAction Stop } else { $null }
    if (-not $fixture -and [string]::IsNullOrWhiteSpace($Token)) { throw 'Actions read token is required to bind a dispatched candidate to a live pull request' }
    $apiRoot = if ($ServerUrl -eq 'https://github.com') { 'https://api.github.com' } else { "$($ServerUrl.TrimEnd('/'))/api/v3" }
    $headers = @{ Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
    if (-not $fixture) { $headers.Authorization = "Bearer $Token" }
    $associatedPath = "repos/$Repository/commits/$CandidateSha/pulls"
    $pullRequests = if ($fixture) { @($fixture.pull_requests) } else { @(Invoke-RestMethod -Uri "$apiRoot/$associatedPath" -Headers $headers -Method Get) }
    $branch = $ExpectedTargetRef.Substring('refs/heads/'.Length)
    $matchedPullRequests = @($pullRequests | Where-Object {
        $_.state -ceq 'open' -and [string]$_.head.sha -ceq $CandidateSha -and
        [string]$_.head.repo.full_name -ceq $Repository -and [string]$_.base.repo.full_name -ceq $Repository -and
        [string]$_.base.ref -ceq $branch -and [string]$_.base.sha -ceq $AcceptedBaseSha
    })
    if ($matchedPullRequests.Count -ne 1) { throw 'Dispatched candidate must resolve to exactly one open same-repository PR at the exact accepted target' }
    $commit = if ($fixture) { $fixture.commit } else { Invoke-RestMethod -Uri "$apiRoot/repos/$Repository/commits/$CandidateSha" -Headers $headers -Method Get }
    $treeSha = [string]$commit.commit.tree.sha
    if ([string]$commit.sha -cne $CandidateSha -or $treeSha -notmatch '^[0-9a-fA-F]{40,64}$') { throw 'Dispatched candidate commit/tree is not confirmed by the repository API' }
    $result = [PSCustomObject]@{
        valid = $true; repository = $Repository; pull_request_number = [int]$matchedPullRequests[0].number
        head_sha = $CandidateSha; base_sha = $AcceptedBaseSha; target_ref = $ExpectedTargetRef
        tree_sha = $treeSha; pull_request = $matchedPullRequests[0]
    }
    if ($OutputPath) { $result | ConvertTo-Json -Depth 24 | Set-Content -LiteralPath $OutputPath -Encoding utf8 }
    $result | ConvertTo-Json -Depth 24 -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Dispatched candidate rejected: $_")
    exit 1
}
