[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EventPath,
    [Parameter(Mandatory = $true)][string]$ExpectedRepository,
    [Parameter(Mandatory = $true)][string]$ExpectedWorkflowId,
    [Parameter(Mandatory = $true)][string]$DefaultBranch,
    [Parameter(Mandatory = $true)][string]$ServerUrl,
    [string]$Token,
    [string]$AuthoritativeRunPath,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
function Read-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required JSON file does not exist: $Path" }
    Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -ErrorAction Stop
}

try {
    $event = Read-JsonFile $EventPath
    $run = $event.workflow_run
    if (-not $run) { throw 'Event is missing workflow_run' }
    $expectedPath = ".github/workflows/coverage.yml@$DefaultBranch"
    if ($ExpectedRepository -notmatch '^[^/]+/[^/]+$' -or $ExpectedWorkflowId -notmatch '^[0-9]+$' -or
        [string]::IsNullOrWhiteSpace($DefaultBranch) -or $run.name -cne 'Coverage Capture' -or
        [string]$run.workflow_id -cne $ExpectedWorkflowId -or [string]$run.path -cne $expectedPath) {
        throw 'Run is not from the configured Coverage Capture workflow on the default branch'
    }
    if ([string]$event.repository.full_name -cne $ExpectedRepository -or
        [string]$run.repository.full_name -cne $ExpectedRepository -or
        [string]$run.head_repository.full_name -cne $ExpectedRepository) {
        throw 'Cross-repository or mismatched workflow run is not accepted'
    }
    if ([string]$run.event -notin @('pull_request', 'merge_group', 'push', 'repository_dispatch') -or
        [string]$run.conclusion -notin @('success', 'failure')) {
        throw 'Unsupported workflow event or incomplete run conclusion'
    }
    if ($run.event -in @('push', 'repository_dispatch') -and [string]$run.head_branch -cne $DefaultBranch) {
        throw 'Target advancement capture must run on the configured default branch'
    }
    if ([string]$run.id -notmatch '^[0-9]+$' -or [string]$run.run_attempt -notmatch '^[1-9][0-9]*$' -or
        [string]$run.head_sha -notmatch '^[0-9a-fA-F]{40,64}$') {
        throw 'Workflow run ID, attempt, or full head SHA is invalid'
    }

    if ($AuthoritativeRunPath) {
        $authoritative = Read-JsonFile $AuthoritativeRunPath
    } else {
        if ([string]::IsNullOrWhiteSpace($Token)) { throw 'Actions read token is required to verify the authoritative workflow run' }
        $apiRoot = if ($ServerUrl -eq 'https://github.com') { 'https://api.github.com' } else { "$($ServerUrl.TrimEnd('/'))/api/v3" }
        $headers = @{ Authorization = "Bearer $Token"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
        $authoritative = Invoke-RestMethod -Uri "$apiRoot/repos/$ExpectedRepository/actions/runs/$($run.id)" -Headers $headers -Method Get
    }
    foreach ($field in @('id', 'run_attempt', 'workflow_id', 'head_sha', 'event', 'conclusion', 'path', 'head_branch')) {
        if ([string]$authoritative.$field -cne [string]$run.$field) { throw "GitHub run record does not match event field '$field'" }
    }
    if ([string]$authoritative.repository.full_name -cne $ExpectedRepository) { throw "GitHub run record belongs to another repository" }

    $baseSha = $null
    $pullRequestNumber = $null
    if ($run.event -eq 'pull_request') {
        $pullRequests = @($run.pull_requests)
        if ($pullRequests.Count -ne 1 -or [string]$pullRequests[0].head.sha -cne [string]$run.head_sha -or
            [string]$pullRequests[0].head.repo.full_name -cne $ExpectedRepository -or
            [string]$pullRequests[0].base.repo.full_name -cne $ExpectedRepository -or
            [string]$pullRequests[0].base.sha -notmatch '^[0-9a-fA-F]{40,64}$') {
            throw 'Pull-request run must bind to exactly one same-repository PR with full head and base SHAs'
        }
        $baseSha = [string]$pullRequests[0].base.sha
        $pullRequestNumber = [int]$pullRequests[0].number
    }

    $result = [PSCustomObject]@{
        repository = $ExpectedRepository
        workflow_id = [string]$run.workflow_id
        run_id = [string]$run.id
        run_attempt = [string]$run.run_attempt
        event = [string]$run.event
        conclusion = [string]$run.conclusion
        head_sha = [string]$run.head_sha
        base_sha = $baseSha
        pull_request_head_sha = if ($run.event -eq 'pull_request') { [string]$run.head_sha } else { $null }
        accepted_base_sha = $baseSha
        tested_candidate_sha = [string]$run.head_sha
        check_destination_sha = [string]$run.head_sha
        pull_request_number = $pullRequestNumber
        workflow_path = [string]$run.path
        valid = $true
    }
    if ($OutputPath) { $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8 }
    $result | ConvertTo-Json -Depth 8 -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Workflow run identity rejected: $_")
    exit 1
}
