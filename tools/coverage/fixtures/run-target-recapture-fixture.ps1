[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-target-recapture-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    function Invoke-TargetRecaptureFixture([bool]$RequestCaptures, [string]$OutputName) {
        $outputPath = Join-Path $temp $OutputName
        $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $root 'tools/coverage/request-target-recaptures.ps1'),
            '-Repository', 'octaviordz/chimali', '-TargetRef', 'refs/heads/main', '-PreviousTargetSha', ('c' * 40), '-CurrentTargetSha', ('d' * 40),
            '-CoverageAppId', '7654321', '-AppToken', 'fixture-app-token', '-ActionsToken', 'fixture-actions-token',
            '-LiveEvidencePath', (Join-Path $root 'tools/coverage/fixtures/target-recapture.json'), '-OutputPath', $outputPath)
        if ($RequestCaptures) { $arguments += '-RequestCaptures' }
        $resultOutput = @(& pwsh @arguments 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "Target recapture production handler rejected valid offline evidence: $($resultOutput -join ' ')" }
        return Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json -ErrorAction Stop
    }

    $result = Invoke-TargetRecaptureFixture $true 'accepted-target.json'
    if ($result.processed_pull_requests -ne 2 -or $result.pull_requests[0].pull_request_number -ne 42 -or
        $result.pull_requests[0].capture_requested -ne $true -or $result.pull_requests[1].pull_request_number -ne 44 -or
        $result.pull_requests[1].capture_requested -ne $false -or @($result.actions).Count -ne 3) {
        throw 'Target recapture handler did not invalidate all same-repository PRs and dispatch only the accepted-base PR'
    }
    $checkActions = @($result.actions | Where-Object label -eq 'invalidate-check')
    $check = $checkActions[0]
    $createdStaleCheck = $checkActions[1]
    $dispatch = @($result.actions | Where-Object label -eq 'request-capture')[0]
    if ($checkActions.Count -ne 2 -or $check.method -cne 'Patch' -or $check.body.name -cne 'coverage-integration' -or $check.body.head_sha -or
        $check.body.conclusion -cne 'failure' -or $check.body.external_id -cne "target-advanced:$('d' * 40)" -or
        $createdStaleCheck.method -cne 'Post' -or $createdStaleCheck.body.head_sha -cne ('c' * 40) -or
        $dispatch.body.event_type -cne 'coverage-target-advanced' -or
        $dispatch.body.client_payload.pull_request_head_sha -cne ('a' * 40) -or
        $dispatch.body.client_payload.accepted_base_sha -cne ('d' * 40)) {
        throw 'App invalidation or recapture dispatch did not preserve the PR and target identity'
    }

    $failedTarget = Invoke-TargetRecaptureFixture $false 'failed-target.json'
    if ($failedTarget.processed_pull_requests -ne 2 -or @($failedTarget.actions).Count -ne 2 -or
        @($failedTarget.actions | Where-Object label -eq 'invalidate-check').Count -ne 2 -or
        @($failedTarget.actions | Where-Object label -eq 'request-capture').Count -ne 0 -or
        @($failedTarget.pull_requests | Where-Object { $_.capture_requested }).Count -ne 0 -or
        [string]$failedTarget.actions[0].body.output.summary -notmatch 'did not produce an accepted baseline') {
        throw 'Failed target coverage did not invalidate stale App checks without requesting PR captures'
    }
    Write-Output 'Target recapture production handler invalidated stale App checks in both outcomes and dispatched only after the target produced an accepted baseline.'
} finally {
    $resolved = [System.IO.Path]::GetFullPath($temp)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if ([System.IO.Path]::GetDirectoryName($resolved) -ne $tempRoot -or [System.IO.Path]::GetFileName($resolved) -notlike 'coverage-target-recapture-*') { throw "Refusing unsafe fixture cleanup target: $resolved" }
    if (Test-Path -LiteralPath $resolved) { [System.IO.Directory]::Delete($resolved, $true) }
}
