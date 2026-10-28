[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$TargetRef,
    [Parameter(Mandatory = $true)][string]$PreviousTargetSha,
    [Parameter(Mandatory = $true)][string]$CurrentTargetSha,
    [Parameter(Mandatory = $true)][string]$CoverageAppId,
    [Parameter(Mandatory = $true)][string]$AppToken,
    [Parameter(Mandatory = $true)][string]$ActionsToken,
    [switch]$RequestCaptures,
    [string]$ServerUrl = 'https://github.com',
    [string]$LiveEvidencePath,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

function Get-ApiRoot([string]$Value) {
    if ($Value -eq 'https://github.com') { return 'https://api.github.com' }
    return "$($Value.TrimEnd('/'))/api/v3"
}

function Invoke-GitHubRequest([string]$Method, [string]$Uri, [hashtable]$Headers, $Body, [string]$Label) {
    if ($script:Fixture) {
        if ($Method -eq 'Get' -and $Label -eq 'pull-requests') { return @($script:Fixture.pull_requests) }
        if ($Method -eq 'Get' -and $Label -eq 'checks') {
            $head = [regex]::Match($Uri, '/commits/([0-9a-fA-F]{40,64})/check-runs').Groups[1].Value
            return [PSCustomObject]@{ check_runs = @($script:Fixture.existing_checks | Where-Object { [string]$_.head_sha -ceq $head }) }
        }
        $script:Actions.Add([PSCustomObject]@{ method = $Method; label = $Label; uri = $Uri; body = $Body })
        return [PSCustomObject]@{ id = 9000 + $script:Actions.Count }
    }
    $parameters = @{ Uri = $Uri; Method = $Method; Headers = $Headers }
    if ($null -ne $Body) { $parameters.Body = ($Body | ConvertTo-Json -Depth 16 -Compress); $parameters.ContentType = 'application/json' }
    return Invoke-RestMethod @parameters
}

try {
    if ($Repository -notmatch '^[^/]+/[^/]+$' -or $TargetRef -notmatch '^refs/heads/[A-Za-z0-9._/-]+$' -or $TargetRef -match '(^|/)\.\.(/|$)' -or
        $PreviousTargetSha -notmatch '^[0-9a-fA-F]{40,64}$' -or $CurrentTargetSha -notmatch '^[0-9a-fA-F]{40,64}$' -or
        $CoverageAppId -notmatch '^[0-9]+$' -or [string]::IsNullOrWhiteSpace($AppToken) -or [string]::IsNullOrWhiteSpace($ActionsToken)) {
        throw 'Target recapture request has incomplete or malformed repository, target, commit, App, or token identity'
    }
    $targetBranch = $TargetRef.Substring('refs/heads/'.Length)
    $script:Actions = [System.Collections.Generic.List[object]]::new()
    $script:Fixture = if ($LiveEvidencePath) { Get-Content -LiteralPath $LiveEvidencePath -Raw | ConvertFrom-Json -ErrorAction Stop } else { $null }
    $apiRoot = Get-ApiRoot $ServerUrl
    $readHeaders = @{ Authorization = "Bearer $ActionsToken"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
    $appHeaders = @{ Authorization = "Bearer $AppToken"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }
    $resultRows = @()
    $page = 1
    do {
        $pullsUri = "$apiRoot/repos/$Repository/pulls?state=open&base=$([uri]::EscapeDataString($targetBranch))&per_page=100&page=$page"
        $pulls = @(Invoke-GitHubRequest -Method Get -Uri $pullsUri -Headers $readHeaders -Body $null -Label 'pull-requests')
        foreach ($pull in $pulls) {
            if ([string]$pull.state -cne 'open' -or [string]$pull.base.ref -cne $targetBranch -or
                [string]$pull.head.repo.full_name -cne $Repository -or [string]$pull.base.repo.full_name -cne $Repository -or
                [string]$pull.head.sha -notmatch '^[0-9a-fA-F]{40,64}$') { continue }
            $headSha = [string]$pull.head.sha
            $baseMatchesAcceptedTarget = [string]$pull.base.sha -ceq $CurrentTargetSha
            $checksUri = "$apiRoot/repos/$Repository/commits/$headSha/check-runs?check_name=coverage-integration&per_page=100"
            $checkResponse = Invoke-GitHubRequest -Method Get -Uri $checksUri -Headers $readHeaders -Body $null -Label 'checks'
            $existingChecks = @($checkResponse.check_runs)
            $appChecks = @($existingChecks | Where-Object { [string]$_.name -ceq 'coverage-integration' -and [string]$_.app.id -ceq $CoverageAppId })
            $payload = [ordered]@{
                name = 'coverage-integration'
                head_sha = $headSha
                status = 'completed'
                conclusion = 'failure'
                external_id = "target-advanced:$CurrentTargetSha"
                completed_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
                output = [ordered]@{
                    title = 'Coverage target advanced; fresh capture requested'
                    summary = if ($RequestCaptures -and $baseMatchesAcceptedTarget) {
                        "The protected target advanced from $PreviousTargetSha to $CurrentTargetSha. A fresh capture was requested for pull request #$($pull.number) against the newly accepted target."
                    } elseif (-not $RequestCaptures) {
                        "The protected target advanced from $PreviousTargetSha to $CurrentTargetSha, but the target coverage run did not produce an accepted baseline. This App check is failed until coverage succeeds for the target and a fresh PR capture completes."
                    } else {
                        "The protected target advanced from $PreviousTargetSha to $CurrentTargetSha. Pull request #$($pull.number) must first incorporate the new target; capture will be requested when its base reaches the accepted target."
                    }
                }
            }
            if ($appChecks.Count -gt 0) {
                $updatePayload = [ordered]@{}
                foreach ($key in $payload.Keys) { if ($key -cne 'head_sha') { $updatePayload[$key] = $payload[$key] } }
                foreach ($appCheck in $appChecks) {
                    $checkId = [string]$appCheck.id
                    $null = Invoke-GitHubRequest -Method Patch -Uri "$apiRoot/repos/$Repository/check-runs/$checkId" -Headers $appHeaders -Body $updatePayload -Label 'invalidate-check'
                }
            } else {
                $null = Invoke-GitHubRequest -Method Post -Uri "$apiRoot/repos/$Repository/check-runs" -Headers $appHeaders -Body $payload -Label 'invalidate-check'
            }
            $captureRequested = $RequestCaptures -and $baseMatchesAcceptedTarget
            if ($captureRequested) {
                $dispatch = [ordered]@{
                    event_type = 'coverage-target-advanced'
                    client_payload = [ordered]@{
                        pull_request_number = [int]$pull.number
                        pull_request_head_sha = $headSha
                        accepted_base_sha = $CurrentTargetSha
                        target_ref = $TargetRef
                    }
                }
                $null = Invoke-GitHubRequest -Method Post -Uri "$apiRoot/repos/$Repository/dispatches" -Headers $readHeaders -Body $dispatch -Label 'request-capture'
            }
            $resultRows += [PSCustomObject]@{ pull_request_number = [int]$pull.number; head_sha = $headSha; stale_check = 'Failed'; capture_requested = $captureRequested }
        }
        $page++
    } while ($pulls.Count -eq 100)

    $result = [PSCustomObject]@{
        valid = $true; repository = $Repository; target_ref = $TargetRef
        previous_target_sha = $PreviousTargetSha; current_target_sha = $CurrentTargetSha
        processed_pull_requests = $resultRows.Count; pull_requests = $resultRows; actions = @($script:Actions)
    }
    if ($OutputPath) { $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding utf8 }
    $result | ConvertTo-Json -Depth 12 -Compress
    exit 0
} catch {
    [Console]::Error.WriteLine("Target-push invalidation/recapture failed: $_")
    exit 1
}
