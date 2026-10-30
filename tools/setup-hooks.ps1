# setup-hooks.ps1
# Configures the repository to use the tracked .gitconfig for Git hooks with PowerShell 7.
[CmdletBinding()]
param(
    [string]$DeviceSerial,
    [string]$EvidenceRoot,
    [string]$HarnessRoot
)

$ErrorActionPreference = "Stop"

Write-Host "Configuring Git hooks (Git 2.54+ / PowerShell 7)..." -ForegroundColor Cyan

# 1. Verify Git version
$gitVersion = git --version
if ($gitVersion -match "git version (\d+\.\d+)") {
    $version = [double]$matches[1]
    if ($version -lt 2.54) {
        Write-Host "Warning: Git 2.54 or higher is recommended for config-based hooks." -ForegroundColor Yellow
        Write-Host "Current version: $gitVersion" -ForegroundColor Yellow
    }
}

# 2. Verify PowerShell 7 (pwsh) availability
if (-not (Get-Command "pwsh" -ErrorAction SilentlyContinue)) {
    Write-Warning "PowerShell 7 (pwsh) was not found in PATH. Pre-commit and pre-push hooks require pwsh."
}

# 3. Configure tracked .gitconfig include
git config --local include.path ../.gitconfig

# 4. Validate explicit local coverage inputs when a target is configured
$currentTarget = git config --get coverage.targetRef
if ([string]::IsNullOrWhiteSpace($currentTarget)) {
    Write-Host 'No coverage target is configured; feature pushes will not require coverage.' -ForegroundColor Cyan
} else {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    if (-not $HarnessRoot) { $HarnessRoot = git config --get coverage.harnessRoot }
    if (-not $HarnessRoot) { throw 'Set -HarnessRoot to a separate reviewed coverage harness checkout.' }
    $HarnessRoot = [System.IO.Path]::GetFullPath($HarnessRoot)
    if (-not (Test-Path -LiteralPath $HarnessRoot -PathType Container)) { throw "Trusted harness directory does not exist: $HarnessRoot" }
    $harnessHead = (& git -C $HarnessRoot rev-parse HEAD 2>$null).Trim()
    $harnessDirty = (& git -C $HarnessRoot status --porcelain 2>$null) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or $harnessHead -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or $harnessDirty) {
        throw 'Trusted harness must be a clean Git checkout with a pinned HEAD.'
    }
    foreach ($relative in @('tools/integration-ci.ps1','tools/coverage/policy.json','tools/coverage/coverage-common.ps1','tools/coverage/verify.ps1','tools/coverage/capture.ps1')) {
        if (-not (Test-Path -LiteralPath (Join-Path $HarnessRoot $relative) -PathType Leaf)) { throw "Trusted harness is missing $relative" }
    }

    if (-not $EvidenceRoot) { $EvidenceRoot = git config --get coverage.evidenceRoot }
    if (-not $EvidenceRoot) { $EvidenceRoot = Join-Path $env:LOCALAPPDATA 'Chimali\coverage-evidence' }
    if (-not [System.IO.Path]::IsPathRooted($EvidenceRoot)) { $EvidenceRoot = Join-Path $repoRoot $EvidenceRoot }
    $EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
    $repoPrefix = $repoRoot.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
    if ($EvidenceRoot.StartsWith($repoPrefix,[System.StringComparison]::OrdinalIgnoreCase) -or
        [string]::Equals($EvidenceRoot.TrimEnd('\','/'),$repoRoot.TrimEnd('\','/'),[System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'EvidenceRoot must be outside the product checkout.'
    }
    $null = New-Item -ItemType Directory -Path $EvidenceRoot -Force
    git config --local coverage.harnessRoot $HarnessRoot
    git config --local coverage.harnessCommit $harnessHead
    git config --local coverage.evidenceRoot $EvidenceRoot

    $targetRemote = $null
    $targetBranch = $null
    foreach ($name in @(git remote | Sort-Object { $_.Length } -Descending)) {
        if ($currentTarget.StartsWith("$name/",[System.StringComparison]::Ordinal)) {
            $targetRemote = [string]$name
            $targetBranch = $currentTarget.Substring($name.Length + 1)
            break
        }
    }
    if (-not $targetRemote -or -not $targetBranch) { throw "coverage.targetRef is not an unambiguous configured remote/branch: $currentTarget" }
    $targetRefFull = "refs/heads/$targetBranch"
    $remoteTarget = @(& git ls-remote $targetRemote $targetRefFull 2>&1)
    if ($LASTEXITCODE -ne 0 -or $remoteTarget.Count -ne 1) { throw "Could not resolve the configured target on remote '$targetRemote'." }
    $targetSha = (($remoteTarget[0] -split '\s+')[0]).Trim()
    if ($targetSha -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') { throw "Remote target did not resolve to one full commit SHA: $currentTarget" }
    . (Join-Path $HarnessRoot 'tools/coverage/coverage-common.ps1')
    $indexPath = Join-Path (Join-Path $EvidenceRoot 'baselines') 'current.json'
    if (-not (Test-Path -LiteralPath $indexPath -PathType Leaf)) { throw "Accepted coverage baseline is missing: $indexPath" }
    $baselineIndex = Read-SafeJson -Path $indexPath
    $relativeBundle = [string]$baselineIndex.bundle_path
    if ([string]::IsNullOrWhiteSpace($relativeBundle) -or [System.IO.Path]::IsPathRooted($relativeBundle) -or $relativeBundle -match '(^|[/\\])\.\.([/\\]|$)') {
        throw 'Accepted baseline bundle path must be a contained relative path.'
    }
    $baselineBundle = [System.IO.Path]::GetFullPath((Join-Path $EvidenceRoot $relativeBundle))
    $evidencePrefix = $EvidenceRoot.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $baselineBundle.StartsWith($evidencePrefix,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Accepted baseline bundle escapes EvidenceRoot.' }
    $actualBundleDigest = Get-CoverageBundleDigest -BundlePath $baselineBundle
    $baselineValidation = Test-AcceptedBaselineIndex -Index $baselineIndex -ExpectedTargetCommit $targetSha -ActualBundleDigest $actualBundleDigest
    if (-not $baselineValidation.IsValid) { throw "Accepted baseline validation failed: $($baselineValidation.Errors -join '; ')" }

    if (-not $DeviceSerial) { $DeviceSerial = git config --get coverage.deviceSerial }
    if (-not $DeviceSerial) {
        $adb = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $adb) { throw 'adb is not on PATH; pass -DeviceSerial after configuring the Android SDK.' }
        $policy = Get-Content -LiteralPath (Join-Path $HarnessRoot 'tools/coverage/policy.json') -Raw | ConvertFrom-Json
        $minimumApi = [int]$policy.device_requirements.minimum_api_level
        $approvedAbis = @($policy.device_requirements.approved_abis)
        $capable = @()
        foreach ($serial in @(& $adb.Source devices | ForEach-Object { if ($_ -match '^([^\s]+)\s+device$') { $Matches[1] } })) {
            $api = (& $adb.Source -s $serial shell getprop ro.build.version.sdk).Trim()
            $abi = (& $adb.Source -s $serial shell getprop ro.product.cpu.abi).Trim()
            if ($api -match '^\d+$' -and [int]$api -ge $minimumApi -and $abi -in $approvedAbis) { $capable += $serial }
        }
        if ($capable.Count -ne 1) { throw "Expected exactly one authorized device meeting API $minimumApi and approved ABI requirements; found $($capable.Count). Pass -DeviceSerial explicitly." }
        $DeviceSerial = $capable[0]
    }
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        $online = @(& adb devices | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device$" })
        if ($online.Count -ne 1) { throw "Device '$DeviceSerial' is not online and authorized." }
        $policy = Get-Content -LiteralPath (Join-Path $HarnessRoot 'tools/coverage/policy.json') -Raw | ConvertFrom-Json
        $api = (& adb -s $DeviceSerial shell getprop ro.build.version.sdk).Trim()
        $abi = (& adb -s $DeviceSerial shell getprop ro.product.cpu.abi).Trim()
        if ($api -notmatch '^\d+$' -or [int]$api -lt [int]$policy.device_requirements.minimum_api_level -or $abi -notin @($policy.device_requirements.approved_abis)) {
            throw "Device '$DeviceSerial' does not satisfy the trusted policy's API/ABI requirements."
        }
        git config --local coverage.deviceSerial $DeviceSerial
    } else {
        throw 'adb is not on PATH; cannot validate the selected device.'
    }
}

Write-Host "Done: Repository configured to include .gitconfig for shared hooks." -ForegroundColor Green
Write-Host "Local hook configuration does not configure or prove any remote merge gate. Hosted coverage enforcement remains unavailable until separately provisioned and verified." -ForegroundColor Yellow
Write-Host "Configure the local pre-push inputs explicitly: coverage.targetRef, coverage.deviceSerial, coverage.evidenceRoot, and coverage.harnessRoot." -ForegroundColor Cyan
Write-Host "`nGit hooks setup complete!" -ForegroundColor Cyan
