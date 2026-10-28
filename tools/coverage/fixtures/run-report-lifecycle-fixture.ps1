[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath($RepositoryRoot)
$reportScript = Join-Path $repository 'tools/coverage/report.ps1'
if (-not (Test-Path -LiteralPath $reportScript -PathType Leaf)) { throw "Trusted report entry point is missing: $reportScript" }

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('coverage-report-lifecycle-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$bundle = Join-Path $tempRoot 'run'
$runId = [guid]::NewGuid().ToString()
$head = 'a' * 40
$base = 'b' * 40
try {
    New-Item -ItemType Directory -Path $bundle -Force | Out-Null
    $manifest = [PSCustomObject]@{
        run_id = $runId
        purpose = 'Candidate'
        git = [PSCustomObject]@{ source_commit = $head; target_commit = $base }
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8

    $output = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $reportScript `
        -RunBundle $bundle -Repository 'octaviordz/chimali' -HeadSha $head -BaseSha $base -DryRun 2>&1)
    $reportExit = $LASTEXITCODE
    $assessmentDir = Join-Path (Join-Path $tempRoot 'assessments') $runId
    $reporterReceipts = @(Get-ChildItem -LiteralPath $assessmentDir -Filter 'reporter-receipt-*.json' -File -ErrorAction SilentlyContinue)
    if ($reportExit -ne 1) { throw "Dry-run reporter should exit 1 when verification cannot pass and credentials are absent; got $reportExit. $($output -join ' ')" }
    if ($reporterReceipts.Count -ne 1) { throw "Expected one unverified reporter receipt; found $($reporterReceipts.Count). $($output -join ' ')" }
    $receipt = Get-Content -LiteralPath $reporterReceipts[0].FullName -Raw | ConvertFrom-Json
    if ($receipt.run_id -cne $runId -or $receipt.head_sha -cne $head -or $receipt.base_sha -cne $base -or
        $receipt.status -cne 'DryRun' -or $receipt.conclusion -cne 'unverified' -or $receipt.check_id) {
        throw "Failed independent verification did not remain unverified in the reporter receipt: $($receipt | ConvertTo-Json -Compress -Depth 6)"
    }
    if (($output -join ' ') -notmatch 'check-run cannot be authorized via API') {
        throw "Reporter did not explicitly leave the check unauthorized in dry-run: $($output -join ' ')"
    }
    Write-Output 'Production report entry point retained an Unverified DryRun receipt after failed verification and made no authorized success claim.'
} finally {
    if ([IO.Directory]::Exists($tempRoot)) {
        $resolvedTemp = [IO.Path]::GetFullPath($tempRoot)
        if (-not $resolvedTemp.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-report-lifecycle-*') {
            throw "Refusing unsafe report fixture cleanup target: $resolvedTemp"
        }
        [IO.Directory]::Delete($resolvedTemp, $true)
    }
}
