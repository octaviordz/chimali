[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Case, [Parameter(Mandatory = $true)][string]$RepositoryRoot)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixturePath = Join-Path $root 'tools/coverage/fixtures/integration-event-validation.json'
$scriptPath = Join-Path $root 'tools/coverage/validate-integration-event.ps1'
$caseData = @((Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json).cases | Where-Object id -eq $Case)
if ($caseData.Count -ne 1) { throw "Unknown or duplicate integration-event fixture case: $Case" }
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-integration-event-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
try {
    $head='a'*40; $base='b'*40; $merge='c'*40; $tree='d'*40
    $event = [ordered]@{ repository='octaviordz/chimali'; head_sha=$head; base_sha=$base; pull_request_number=17 }
    $pr = [ordered]@{
        state='closed'; merged=$true; merged_at='2026-10-27T10:00:00Z'; merge_commit_sha=$merge
        head=[ordered]@{ sha=$head; repo=[ordered]@{ full_name='octaviordz/chimali' } }
        base=[ordered]@{ ref='main'; repo=[ordered]@{ full_name='octaviordz/chimali' } }
    }
    $commit = [ordered]@{ sha=$merge; commit=[ordered]@{ tree=[ordered]@{ sha=$tree } } }
    switch ($caseData[0].mutation) {
        'not-merged' { $pr.merged=$false }
        'wrong-head' { $pr.head.sha='e'*40 }
        'stale-base-context' { $event.base_sha='e'*40 }
        'missing-merge-sha' { $pr.merge_commit_sha=$null }
        'missing-tree' { $commit.commit.tree.sha=$null }
    }
    $eventPath=Join-Path $tmp 'event.json'; $livePath=Join-Path $tmp 'api.json'
    $event | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $eventPath -Encoding utf8
    @{ pull_request=$pr; integration_commit=$commit } | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $livePath -Encoding utf8
    $output=@(& pwsh -NoProfile -ExecutionPolicy Bypass -File $scriptPath -Repository 'octaviordz/chimali' -HeadSha $head -BaseSha $base -IntegrationEventPath $eventPath -ServerUrl 'https://github.com' -LiveEvidencePath $livePath 2>&1)
    $exit=$LASTEXITCODE
    $expected=if($caseData[0].expected_valid){0}else{1}
    if($exit -ne $expected){throw "$Case expected exit $expected, got $exit`: $($output -join ' ')"}
    if($expected -eq 0){$result=($output -join "`n")|ConvertFrom-Json;if($result.integration_commit -cne $merge -or $result.integration_tree -cne $tree){throw "$Case did not return API-provided commit/tree"}}
    Write-Output "$Case passed (exit $exit)."
} finally {
    $resolved=[System.IO.Path]::GetFullPath($tmp);$tempRoot=[System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/')
    if([System.IO.Path]::GetDirectoryName($resolved) -ne $tempRoot -or [System.IO.Path]::GetFileName($resolved) -notlike 'coverage-integration-event-*'){throw "Refusing unsafe cleanup target: $resolved"}
    [System.IO.Directory]::Delete($resolved,$true)
}
