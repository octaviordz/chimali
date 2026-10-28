<#
.SYNOPSIS
    Verifies each non-deletion commit in Git's native pre-push update stream.
.DESCRIPTION
    Uses only explicit coverage.targetRef, coverage.deviceSerial,
    coverage.evidenceRoot, and coverage.harnessRoot settings (or their named
    CHIMALI_* environment equivalents). coverage.evidenceRef, when configured,
    exempts only that exact remote ref from product integration verification.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptPath = [System.IO.Path]::GetFullPath($MyInvocation.MyCommand.Definition)
$ScriptDir = Split-Path -Parent $scriptPath
$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))

function Stop-Hook {
    param([string]$Message, [int]$ExitCode = 1)
    [Console]::Error.WriteLine("Pre-push verification blocked: $Message")
    exit $ExitCode
}

function Get-ConfiguredValue {
    param([string]$Name, [string]$EnvironmentName)
    $value = (& git -C $RepoRoot config --get $Name 2>$null | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace([string]$value)) {
        $value = [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    }
    return ([string]$value).Trim()
}

function Resolve-ConfiguredPath {
    param([string]$Value, [string]$Name, [switch]$MustExist)
    if ([string]::IsNullOrWhiteSpace($Value)) { Stop-Hook "$Name is required (git config coverage.$Name or its documented CHIMALI_* environment variable)." 3 }
    $path = if ([System.IO.Path]::IsPathRooted($Value)) { $Value } else { Join-Path $RepoRoot $Value }
    $path = [System.IO.Path]::GetFullPath($path)
    if ($MustExist -and -not (Test-Path -LiteralPath $path -PathType Container)) { Stop-Hook "$Name directory does not exist: $path" 3 }
    return $path
}

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot '.git'))) {
    # Worktrees use a .git file rather than a directory.
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot '.git') -PathType Leaf)) { Stop-Hook "Could not resolve repository root from hook path '$scriptPath'." 3 }
}

$targetRef = Get-ConfiguredValue -Name 'coverage.targetRef' -EnvironmentName 'CHIMALI_INTEGRATION_TARGET'
if ([string]::IsNullOrWhiteSpace($targetRef)) { Stop-Hook 'coverage.targetRef must be configured explicitly; no inferred target is used.' 3 }
$deviceSerial = Get-ConfiguredValue -Name 'coverage.deviceSerial' -EnvironmentName 'ANDROID_SERIAL'
if ([string]::IsNullOrWhiteSpace($deviceSerial)) { Stop-Hook 'coverage.deviceSerial or ANDROID_SERIAL must name the authorized test device explicitly.' 3 }
$evidenceValue = Get-ConfiguredValue -Name 'coverage.evidenceRoot' -EnvironmentName 'CHIMALI_EVIDENCE_ROOT'
$harnessValue = Get-ConfiguredValue -Name 'coverage.harnessRoot' -EnvironmentName 'CHIMALI_HARNESS_ROOT'
$evidenceRoot = Resolve-ConfiguredPath -Value $evidenceValue -Name 'evidenceRoot'
$harnessRoot = Resolve-ConfiguredPath -Value $harnessValue -Name 'harnessRoot' -MustExist
$harnessIntegrationPath = Join-Path $harnessRoot 'tools/integration-ci.ps1'
if (-not (Test-Path -LiteralPath $harnessIntegrationPath -PathType Leaf)) { Stop-Hook "Trusted harness is missing tools/integration-ci.ps1: $harnessIntegrationPath" 3 }

$repoPrefix = $RepoRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$evidenceNormalized = $evidenceRoot.TrimEnd('\', '/')
if ([string]::Equals($evidenceNormalized, $RepoRoot.TrimEnd('\', '/'), [System.StringComparison]::OrdinalIgnoreCase) -or
    $evidenceNormalized.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    Stop-Hook "coverage.evidenceRoot must be outside the product checkout: $evidenceRoot" 3
}
if (-not (Test-Path -LiteralPath $evidenceRoot -PathType Container)) { New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null }

$evidenceRef = Get-ConfiguredValue -Name 'coverage.evidenceRef' -EnvironmentName 'CHIMALI_COVERAGE_EVIDENCE_REF'
if (-not [string]::IsNullOrWhiteSpace($evidenceRef)) {
    & git -C $RepoRoot check-ref-format $evidenceRef 2>$null
    $evidenceRefValid = $LASTEXITCODE -eq 0
    if (-not $evidenceRef.StartsWith('refs/heads/', [System.StringComparison]::Ordinal) -or -not $evidenceRefValid) {
        Stop-Hook "coverage.evidenceRef must be one exact full refs/heads/... ref: $evidenceRef" 3
    }
}

$updates = [System.Collections.Generic.List[object]]::new()
while ($null -ne ($line = [Console]::In.ReadLine())) {
    if ([string]::IsNullOrWhiteSpace($line)) { Stop-Hook 'Malformed empty pre-push update line.' 3 }
    $parts = @($line.Trim() -split '\s+')
    if ($parts.Count -ne 4) { Stop-Hook "Malformed pre-push update (expected four fields): $line" 3 }
    $updates.Add([PSCustomObject]@{ LocalRef = $parts[0]; LocalSha = $parts[1]; RemoteRef = $parts[2]; RemoteSha = $parts[3] })
}
if ($updates.Count -eq 0) { exit 0 }

$zeroShaPattern = '^(0{40}|0{64})$'
$shaPattern = '^([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$'
$remoteName = Get-ConfiguredValue -Name 'coverage.remoteName' -EnvironmentName 'CHIMALI_COVERAGE_REMOTE'
if ([string]::IsNullOrWhiteSpace($remoteName)) { $remoteName = 'origin' }
$pwshCommand = Get-Command pwsh -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pwshCommand) { Stop-Hook 'PowerShell 7 (pwsh) is required.' 3 }

foreach ($update in $updates) {
    if ($update.LocalSha -notmatch $shaPattern -or $update.RemoteSha -notmatch $shaPattern) {
        Stop-Hook "Malformed object ID in update '$($update.LocalRef)' -> '$($update.RemoteRef)'." 3
    }
    if ($update.LocalSha -match $zeroShaPattern) { continue }
    if ($update.LocalRef -ne '(delete)') {
        & git -C $RepoRoot check-ref-format $update.LocalRef 2>$null
        if ($LASTEXITCODE -ne 0) { Stop-Hook "Invalid local ref name '$($update.LocalRef)'." 3 }
    }
    & git -C $RepoRoot check-ref-format $update.RemoteRef 2>$null
    $remoteRefValid = $LASTEXITCODE -eq 0
    if (-not $update.RemoteRef.StartsWith('refs/', [System.StringComparison]::Ordinal) -or -not $remoteRefValid) {
        Stop-Hook "Invalid remote ref name '$($update.RemoteRef)'." 3
    }

    if (-not [string]::IsNullOrWhiteSpace($evidenceRef) -and $update.RemoteRef -ceq $evidenceRef) {
        Write-Host "Configured evidence ref update '$($update.RemoteRef)' is exempt from product integration checks." -ForegroundColor DarkGray
        continue
    }

    $commitSpec = "$($update.LocalSha)^{commit}"
    & git -C $RepoRoot cat-file -e $commitSpec 2>$null
    if ($LASTEXITCODE -ne 0) { Stop-Hook "Pushed object $($update.LocalSha) is not a locally resolvable commit." 2 }

    Write-Host "Verifying pushed commit $($update.LocalSha) from $($update.LocalRef) to $($update.RemoteRef) against $targetRef..." -ForegroundColor Cyan
    & $pwshCommand.Source -NoProfile -ExecutionPolicy Bypass -File $harnessIntegrationPath `
        -RepositoryRoot $RepoRoot `
        -CandidateRef $update.LocalSha `
        -TargetRef $targetRef `
        -HarnessRoot $harnessRoot `
        -DeviceSerial $deviceSerial `
        -EvidenceRoot $evidenceRoot `
        -RemoteName $remoteName
    $integrationExit = $LASTEXITCODE
    if ($integrationExit -ne 0) {
        Stop-Hook "Integration check rejected pushed commit $($update.LocalSha) from $($update.LocalRef) to $($update.RemoteRef) (exit $integrationExit)." $integrationExit
    }
}

Write-Host 'Pre-push verification passed for all pushed refs.' -ForegroundColor Green
exit 0
