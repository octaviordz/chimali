[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Case, [Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$CoverageRoot = Join-Path $RepositoryRoot 'tools/coverage'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-cli-exit-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

function Assert-ExitCode {
    param([string]$Label, [string]$Script, [string[]]$Arguments, [int]$Expected)
    $output = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $Script @Arguments 2>&1)
    $actual = $LASTEXITCODE
    if ($actual -ne $Expected) {
        throw "$Label expected exit $Expected, got $actual. Output: $($output -join ' ')"
    }
}

try {
    $absentBundle = Join-Path $tempRoot 'absent-run'
    $emptyBundle = Join-Path $tempRoot 'empty-run'
    New-Item -ItemType Directory -Path $emptyBundle | Out-Null
    $policy = Join-Path $CoverageRoot 'policy.json'

    switch ($Case) {
        'CLI_EXIT_001_VERIFY_INVALID_RELATIVE_BUNDLE' {
            Assert-ExitCode $Case (Join-Path $CoverageRoot 'verify.ps1') @('-RunBundle','relative-run','-PolicyPath',$policy) 3
        }
        'CLI_EXIT_002_VERIFY_MISSING_BUNDLE' {
            Assert-ExitCode $Case (Join-Path $CoverageRoot 'verify.ps1') @('-RunBundle',$absentBundle,'-PolicyPath',$policy) 2
        }
        'CLI_EXIT_003_PUBLISH_MISSING_BUNDLE' {
            Assert-ExitCode $Case (Join-Path $CoverageRoot 'publish.ps1') @('-RunBundle',$absentBundle) 2
        }
        'CLI_EXIT_004_REPORT_MISSING_MANIFEST' {
            Assert-ExitCode $Case (Join-Path $CoverageRoot 'report.ps1') @('-RunBundle',$emptyBundle,'-Repository','owner/repo','-HeadSha',('a' * 40),'-BaseSha',('b' * 40),'-DryRun') 2
        }
        'CLI_EXIT_005_CAPTURE_INVALID_REPOSITORY_PATH' {
            Assert-ExitCode $Case (Join-Path $CoverageRoot 'capture.ps1') @('-RepositoryRoot','relative-repository','-SourceRevision',('a' * 40),'-Purpose','Initial','-HarnessRoot',$RepositoryRoot,'-DeviceSerial','fixture-device','-OutputRoot',$tempRoot) 3
        }
        default { throw "Unknown CLI exit taxonomy case '$Case'" }
    }

    Write-Output "$Case passed with exact production entry-point exit code."
} finally {
    $canonical = [System.IO.Path]::GetFullPath($tempRoot)
    $tempParent = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/')
    if ([System.IO.Path]::GetDirectoryName($canonical) -ne $tempParent -or [System.IO.Path]::GetFileName($canonical) -notlike 'coverage-cli-exit-*') {
        throw "Refusing unsafe CLI fixture cleanup path: $canonical"
    }
    if (Test-Path -LiteralPath $canonical) { [System.IO.Directory]::Delete($canonical, $true) }
}
