[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixturePath = Join-Path $root 'tools/coverage/fixtures/live-candidate-binding.json'
$scriptPath = Join-Path $root 'tools/coverage/validate-live-candidate.ps1'
$fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
$selected = @($fixture.cases | Where-Object id -eq $Case)
if ($selected.Count -ne 1) { throw "Unknown or duplicate live-binding fixture case: $Case" }
$caseData = $selected[0]
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-live-candidate-fixture-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

try {
    $head = 'a' * 40
    $base = 'b' * 40
    $tree = 'c' * 40
    $livePr = [PSCustomObject]@{
        state = 'open'; head = [PSCustomObject]@{ sha = $head; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
        base = [PSCustomObject]@{ sha = $base; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
    }
    $targetRef = [PSCustomObject]@{ object = [PSCustomObject]@{ sha = $base } }
    $headCommit = [PSCustomObject]@{ sha = $head; commit = [PSCustomObject]@{ tree = [PSCustomObject]@{ sha = $tree } } }
    switch ($caseData.mutation) {
        'pr-head' { $livePr.head.sha = 'd' * 40 }
        'pr-base' { $livePr.base.sha = 'e' * 40 }
        'closed' { $livePr.state = 'closed' }
        'repo' { $livePr.head.repo.full_name = 'octaviordz/fork' }
        'tree' { $headCommit.commit.tree.sha = 'f' * 40 }
        'target-advanced' { $targetRef.object.sha = 'e' * 40 }
        'none' { }
        default { throw "Unsupported live-binding mutation '$($caseData.mutation)'" }
    }
    $evidencePath = Join-Path $tempRoot 'live.json'
    [PSCustomObject]@{ pull_request = $livePr; target_ref = $targetRef; head_commit = $headCommit } |
        ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $evidencePath -Encoding utf8
    $args = @('-Repository', 'octaviordz/chimali', '-HeadSha', $head, '-BaseSha', $base, '-TreeSha', $tree,
        '-EventName', [string]$caseData.event, '-ServerUrl', 'https://github.com', '-LiveEvidencePath', $evidencePath)
    if ($caseData.event -eq 'pull_request') { $args += @('-PullRequestNumber', '7') }
    if ($caseData.target_ref) { $args += @('-TargetRef', [string]$caseData.target_ref) }
    $null = & pwsh -NoProfile -ExecutionPolicy Bypass -File $scriptPath @args 2>&1
    $actualExit = $LASTEXITCODE
    $expectedExit = if ($caseData.expected_valid) { 0 } else { 1 }
    if ($actualExit -ne $expectedExit) { throw "$Case expected validator exit $expectedExit; got $actualExit" }
    Write-Output "$Case passed (validator exit $actualExit)."
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    if ([System.IO.Directory]::Exists($resolvedTemp)) {
        if (-not $resolvedTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-live-candidate-fixture-*') {
            throw "Refusing unsafe live-binding fixture cleanup target: $resolvedTemp"
        }
        [System.IO.Directory]::Delete($resolvedTemp, $true)
    }
}
