[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Case,[Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixture = Get-Content -LiteralPath (Join-Path $root 'tools/coverage/fixtures/dispatched-candidate.json') -Raw | ConvertFrom-Json
$selected = @($fixture.cases | Where-Object id -eq $Case)
if ($selected.Count -ne 1) { throw "Unknown or duplicate dispatch-candidate fixture case: $Case" }
$caseData = $selected[0]
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-dispatched-candidate-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    $head = 'a' * 40; $base = 'b' * 40; $tree = 'c' * 40
    $pr = [PSCustomObject]@{
        number = 42; state = 'open'
        head = [PSCustomObject]@{ sha = $head; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
        base = [PSCustomObject]@{ ref = 'main'; sha = $base; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
    }
    $commit = [PSCustomObject]@{ sha = $head; commit = [PSCustomObject]@{ tree = [PSCustomObject]@{ sha = $tree } } }
    switch ($caseData.mutation) {
        'fork' { $pr.head.repo.full_name = 'outside/fork' }
        'base' { $pr.base.sha = 'd' * 40 }
        'closed' { $pr.state = 'closed' }
        'tree' { $commit.commit.tree.sha = 'short' }
        'duplicate' { }
    }
    $prs = @($pr)
    if ($caseData.mutation -eq 'duplicate') { $prs += $pr | ConvertTo-Json -Depth 10 | ConvertFrom-Json }
    $livePath = Join-Path $temp 'live.json'; $outputPath = Join-Path $temp 'identity.json'
    [PSCustomObject]@{ pull_requests = $prs; commit = $commit } | ConvertTo-Json -Depth 18 | Set-Content -LiteralPath $livePath -Encoding utf8
    $output = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tools/coverage/validate-dispatched-candidate.ps1') `
        -Repository 'octaviordz/chimali' -CandidateSha $head -AcceptedBaseSha $base -ExpectedTargetRef 'refs/heads/main' `
        -LiveEvidencePath $livePath -OutputPath $outputPath 2>&1)
    $exitCode = $LASTEXITCODE
    $expectedExit = if ($caseData.expected_valid) { 0 } else { 1 }
    if ($exitCode -ne $expectedExit) { throw "$Case expected exit $expectedExit, got $exitCode`: $($output -join ' ')" }
    if ($caseData.expected_valid) {
        $identity = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json -ErrorAction Stop
        if (-not $identity.valid -or $identity.pull_request_number -ne 42 -or $identity.head_sha -cne $head -or $identity.tree_sha -cne $tree) {
            throw "$Case returned incorrect live candidate identity: valid=$($identity.valid), pr=$($identity.pull_request_number), head=$($identity.head_sha), tree=$($identity.tree_sha)"
        }
    } elseif (Test-Path -LiteralPath $outputPath -PathType Leaf) { throw "$Case wrote a success record after rejecting invalid dispatch evidence" }
    Write-Output "$Case passed (validator exit $exitCode)."
} finally {
    $resolved = [System.IO.Path]::GetFullPath($temp)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if ([System.IO.Path]::GetDirectoryName($resolved) -ne $tempRoot -or [System.IO.Path]::GetFileName($resolved) -notlike 'coverage-dispatched-candidate-*') { throw "Refusing unsafe fixture cleanup target: $resolved" }
    if (Test-Path -LiteralPath $resolved) { [System.IO.Directory]::Delete($resolved, $true) }
}
