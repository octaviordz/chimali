[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$repository = [System.IO.Path]::GetFullPath($RepositoryRoot)
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-initial-baseline-reject-' + [guid]::NewGuid().ToString('N'))
$bundle = Join-Path $tempRoot 'run'
$receipt = Join-Path $tempRoot 'receipt.json'
try {
    New-Item -ItemType Directory -Path $bundle -Force | Out-Null
    $manifest = [PSCustomObject]@{
        run_id = '00000000-0000-4000-8000-000000000061'
        purpose = 'Initial'
        policy_id = 'chimali-coverage-v1'
        git = [PSCustomObject]@{ source_commit = 'a' * 40; source_tree = 'b' * 40; target_commit = 'a' * 40 }
        artifacts = @()
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
    $output = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repository 'tools/coverage/publish.ps1') `
        -RunBundle $bundle -OutputReceiptPath $receipt -AcceptInitialBaseline 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 3 -or ($output -join ' ') -notmatch 'exact ref lab/or/main' -or (Test-Path -LiteralPath $receipt)) {
        throw "Publisher did not reject an unbound/synthetic initial run before archive or index effects (exit $exitCode): $($output -join ' ')"
    }
    Write-Output 'PASS initial baseline publisher rejects a missing exact source ref before any publication.'
} finally {
    $canonicalTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $canonicalRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/')
    if ([System.IO.Path]::GetDirectoryName($canonicalTemp) -ne $canonicalRoot -or [System.IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-initial-baseline-reject-*') {
        throw "Refusing unsafe initial baseline fixture cleanup target: $canonicalTemp"
    }
    if (Test-Path -LiteralPath $canonicalTemp) { [System.IO.Directory]::Delete($canonicalTemp, $true) }
}
