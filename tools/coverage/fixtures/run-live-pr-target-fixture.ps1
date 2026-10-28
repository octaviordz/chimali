[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Case, [Parameter(Mandatory = $true)][string]$RepositoryRoot)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixture = Get-Content -LiteralPath (Join-Path $root 'tools/coverage/fixtures/live-pr-target.json') -Raw | ConvertFrom-Json
$cases = @($fixture.cases | Where-Object id -eq $Case)
if ($cases.Count -ne 1) { throw "Unknown or duplicate live-PR fixture case: $Case" }
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-live-pr-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
try {
    $head = 'a' * 40; $base = 'b' * 40
    $pr = [ordered]@{
        number = 17; state = 'open'
        head = [ordered]@{ sha = $head; repo = [ordered]@{ full_name = 'octaviordz/chimali' } }
        base = [ordered]@{ sha = $base; repo = [ordered]@{ full_name = 'octaviordz/chimali' } }
    }
    switch ($cases[0].mutation) {
        'base-advanced' { $pr.base.sha = 'c' * 40 }
        'head-changed' { $pr.head.sha = 'd' * 40 }
        'closed' { $pr.state = 'closed' }
        'cross-repository' { $pr.base.repo.full_name = 'someone-else/chimali' }
        'wrong-number' { $pr.number = 18 }
    }
    $input = Join-Path $tmp 'pr.json'; $outputPath = Join-Path $tmp 'identity.json'
    $pr | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $input -Encoding utf8
    $output = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tools/coverage/validate-live-pr-target.ps1') `
        -PullRequestPath $input -ExpectedRepository 'octaviordz/chimali' -ExpectedNumber '17' `
        -ExpectedHeadSha $head -CapturedBaseSha $base -OutputPath $outputPath 2>&1)
    $actual = $LASTEXITCODE
    $expected = if ($cases[0].expected_valid) { 0 } else { 1 }
    if ($actual -ne $expected) { throw "$Case expected exit $expected, got $actual`: $($output -join ' ')" }
    if ($expected -eq 0) {
        if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) { throw "$Case did not write validated identity" }
        $identity = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
        if ($identity.base_sha -cne $base -or $identity.head_sha -cne $head) { throw "$Case returned different PR identity" }
    } elseif (Test-Path -LiteralPath $outputPath -PathType Leaf) { throw "$Case wrote success output despite rejection" }
    Write-Output "$Case passed (exit $actual)."
} finally {
    $resolved = [System.IO.Path]::GetFullPath($tmp)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/')
    if ([System.IO.Path]::GetDirectoryName($resolved) -ne $tempRoot -or [System.IO.Path]::GetFileName($resolved) -notlike 'coverage-live-pr-*') { throw "Refusing unsafe fixture cleanup target: $resolved" }
    [System.IO.Directory]::Delete($resolved, $true)
}
