[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PullRequestPath,
    [Parameter(Mandatory = $true)][string]$ExpectedRepository,
    [Parameter(Mandatory = $true)][string]$ExpectedNumber,
    [Parameter(Mandatory = $true)][string]$ExpectedHeadSha,
    [Parameter(Mandatory = $true)][string]$CapturedBaseSha,
    [switch]$AllowHeadShaChangePendingTreeVerification,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
try {
    if ($ExpectedRepository -notmatch '^[^/]+/[^/]+$' -or
        $ExpectedNumber -notmatch '^[1-9][0-9]*$' -or
        $ExpectedHeadSha -notmatch '^[0-9a-fA-F]{40,64}$' -or
        $CapturedBaseSha -notmatch '^[0-9a-fA-F]{40,64}$') {
        throw 'Expected PR identity contains an invalid repository, number, head SHA, or captured base SHA'
    }
    if (-not (Test-Path -LiteralPath $PullRequestPath -PathType Leaf)) { throw "Live pull request record is missing: $PullRequestPath" }
    $pr = Get-Content -LiteralPath $PullRequestPath -Raw | ConvertFrom-Json -ErrorAction Stop
    $liveHeadSha = [string]$pr.head.sha
    if ($liveHeadSha -notmatch '^[0-9a-fA-F]{40,64}$' -or
        ($liveHeadSha -cne $ExpectedHeadSha -and -not $AllowHeadShaChangePendingTreeVerification) -or
        [string]$pr.number -cne $ExpectedNumber -or [string]$pr.state -cne 'open' -or
        [string]$pr.base.sha -cne $CapturedBaseSha -or
        [string]$pr.head.repo.full_name -cne $ExpectedRepository -or
        [string]$pr.base.repo.full_name -cne $ExpectedRepository) {
        throw 'The live PR head/base or repository changed after capture; a fresh capture is required'
    }
    $result = [PSCustomObject]@{
        valid = $true
        pull_request_number = $ExpectedNumber
        captured_head_sha = $ExpectedHeadSha
        head_sha = $liveHeadSha
        base_sha = [string]$pr.base.sha
        repository = $ExpectedRepository
    }
    if ($OutputPath) { $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8 }
    $result | ConvertTo-Json -Depth 8 -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Live PR target rejected: $_")
    exit 1
}
