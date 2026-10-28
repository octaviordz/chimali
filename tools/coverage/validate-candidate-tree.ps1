[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryPath,
    [Parameter(Mandatory = $true)][string]$SourceSha,
    [Parameter(Mandatory = $true)][string]$AcceptedBaseSha,
    [Parameter(Mandatory = $true)][ValidateSet('pull_request', 'merge_group', 'push')][string]$EventName
)

$ErrorActionPreference = 'Stop'
try {
    $repository = [IO.Path]::GetFullPath($RepositoryPath)
    foreach ($entry in @(@{ Name = 'SourceSha'; Value = $SourceSha }, @{ Name = 'AcceptedBaseSha'; Value = $AcceptedBaseSha })) {
        if ([string]$entry.Value -notmatch '^[0-9a-fA-F]{40,64}$') { throw "$($entry.Name) must be a full commit SHA" }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $repository '.git'))) { throw 'RepositoryPath is not a Git checkout' }
    $resolvedSource = (& git -C $repository rev-parse --verify "$SourceSha^{commit}").Trim()
    if ($LASTEXITCODE -ne 0 -or $resolvedSource -cne $SourceSha) { throw 'SourceSha is not present as the exact commit in the candidate checkout' }
    $resolvedBase = (& git -C $repository rev-parse --verify "$AcceptedBaseSha^{commit}").Trim()
    if ($LASTEXITCODE -ne 0 -or $resolvedBase -cne $AcceptedBaseSha) { throw 'AcceptedBaseSha is not present as the exact commit in the candidate checkout' }
    & git -C $repository merge-base --is-ancestor $AcceptedBaseSha $SourceSha
    if ($LASTEXITCODE -ne 0) { throw 'Tested candidate does not contain the exact accepted base; refresh the PR branch or merge group' }
    if ($EventName -eq 'merge_group') {
        $parents = @((& git -C $repository show -s --format=%P $SourceSha).Trim() -split '\s+' | Where-Object { $_ })
        if ($LASTEXITCODE -ne 0 -or $parents -notcontains $AcceptedBaseSha) {
            throw 'Merge-group candidate must directly integrate the exact accepted base as a parent'
        }
    }
    $testedTree = (& git -C $repository rev-parse "$SourceSha^{tree}").Trim()
    if ($LASTEXITCODE -ne 0 -or $testedTree -notmatch '^[0-9a-fA-F]{40,64}$') { throw 'Unable to resolve the tested candidate product tree' }
    [PSCustomObject]@{
        valid = $true
        pull_request_head_sha = if ($EventName -eq 'pull_request') { $SourceSha } else { $null }
        accepted_base_sha = $AcceptedBaseSha
        tested_candidate_sha = $SourceSha
        tested_candidate_tree = $testedTree
        check_destination_sha = $SourceSha
    } | ConvertTo-Json -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Candidate tree rejected: $_")
    exit 1
}
