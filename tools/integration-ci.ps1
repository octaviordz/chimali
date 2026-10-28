<#
.SYNOPSIS
    Mandatory integration orchestration script for coverage and quality verification.
.DESCRIPTION
    Enforces the integration contract: validates target ancestry and freshness,
    runs non-mutating local checks, captures candidate coverage, verifies thresholds
    and regression against the accepted target, and packages evidence.
.PARAMETER RepositoryRoot
    Absolute path to candidate checkout. Defaults to repository root.
.PARAMETER CandidateRef
    Proposed candidate ref or SHA. Defaults to HEAD.
.PARAMETER TargetRef
    Required integration target ref or SHA.
.PARAMETER HarnessRoot
    Required trusted harness root. Defaults to current repository root.
.PARAMETER DeviceSerial
    Required serial of an online and authorized Android test device.
.PARAMETER EvidenceRoot
    Required evidence directory outside the measured checkout.
.PARAMETER BaselineBundle
    Optional archived baseline bundle directory for comparison.
.PARAMETER RemoteName
    Git remote whose live target ref must match TargetRef. Defaults to origin.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),

    [Parameter(Mandatory = $false)]
    [string]$CandidateRef = "HEAD",

    [Parameter(Mandatory = $true)]
    [string]$TargetRef,

    [Parameter(Mandatory = $false)]
    [string]$HarnessRoot = (Split-Path -Parent $PSScriptRoot),

    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,

    [Parameter(Mandatory = $false)]
    [string]$BaselineBundle,

    [Parameter(Mandatory = $false)]
    [string]$RemoteName = 'origin'
)

$ErrorActionPreference = "Stop"
foreach ($rootArgument in @(@{ Name = 'RepositoryRoot'; Value = $RepositoryRoot }, @{ Name = 'HarnessRoot'; Value = $HarnessRoot }, @{ Name = 'EvidenceRoot'; Value = $EvidenceRoot })) {
    if ([string]::IsNullOrWhiteSpace([string]$rootArgument.Value)) {
        [Console]::Error.WriteLine("$($rootArgument.Name) cannot be empty.")
        exit 3
    }
}
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$HarnessRoot = [System.IO.Path]::GetFullPath($HarnessRoot)
$EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
$pwshCommand = Get-Command pwsh -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pwshCommand) {
    [Console]::Error.WriteLine('PowerShell 7 (pwsh) is required to run the trusted integration pipeline.')
    exit 3
}
$PwshPath = $pwshCommand.Source
$CoverageDir = Join-Path $HarnessRoot "tools/coverage"
$CommonPath = Join-Path $CoverageDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    [Console]::Error.WriteLine("coverage-common.ps1 not found at $CommonPath")
    exit 3
}

. $CommonPath

$policyPath = Join-Path $CoverageDir 'policy.json'
$policyValidatorPath = Join-Path $CoverageDir 'validate-policy.ps1'
if (-not (Test-Path -LiteralPath $policyPath -PathType Leaf) -or -not (Test-Path -LiteralPath $policyValidatorPath -PathType Leaf)) {
    [Console]::Error.WriteLine('HarnessRoot is missing its canonical policy or policy validator.')
    exit 3
}
& $PwshPath -NoProfile -File $policyValidatorPath -PolicyPath $policyPath
if ($LASTEXITCODE -ne 0) {
    [Console]::Error.WriteLine("Harness policy validation failed with exit code $LASTEXITCODE")
    exit 3
}
$harnessPolicy = Read-SafeJson -Path $policyPath
foreach ($relativePath in @($harnessPolicy.harness_requirements.required_files)) {
    $requiredPath = Join-Path $HarnessRoot ([string]$relativePath).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        [Console]::Error.WriteLine("HarnessRoot is missing required enforcement file: $requiredPath")
        exit 3
    }
}
$harnessRevision = (& git -C $HarnessRoot rev-parse HEAD 2>$null).Trim()
$harnessRevisionExit = $LASTEXITCODE
$harnessStatus = (& git -C $HarnessRoot status --porcelain 2>$null) -join "`n"
if ($harnessRevisionExit -ne 0 -or $LASTEXITCODE -ne 0 -or -not $harnessRevision -or -not [string]::IsNullOrWhiteSpace($harnessStatus)) {
    [Console]::Error.WriteLine("HarnessRoot must be a clean Git checkout with a resolvable pinned HEAD: $HarnessRoot")
    exit 3
}

# 1. Path & Argument Validations
if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
    [Console]::Error.WriteLine("RepositoryRoot does not exist or is not a directory: $RepositoryRoot")
    exit 2
}
if (-not (Test-Path -LiteralPath $HarnessRoot -PathType Container)) {
    [Console]::Error.WriteLine("HarnessRoot does not exist or is not a directory: $HarnessRoot")
    exit 3
}
if ((Test-Path -LiteralPath $EvidenceRoot) -and -not (Test-Path -LiteralPath $EvidenceRoot -PathType Container)) {
    [Console]::Error.WriteLine("EvidenceRoot exists but is not a directory: $EvidenceRoot")
    exit 3
}
$normRepo = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$normEvidence = [System.IO.Path]::GetFullPath($EvidenceRoot)
if ([string]::Equals($normEvidence.TrimEnd('\', '/'), $normRepo.TrimEnd('\', '/'), [System.StringComparison]::OrdinalIgnoreCase) -or
    $normEvidence.StartsWith($normRepo, [System.StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine("EvidenceRoot cannot be located inside RepositoryRoot: $EvidenceRoot")
    exit 3
}

# 2. Resolve Candidate & Target SHAs and Ancestry
Push-Location $RepositoryRoot
try {
    $candidateSha = (git rev-parse $CandidateRef).Trim()
    $targetSha = (git rev-parse $TargetRef).Trim()

    $remoteTargetRef = ConvertTo-RemoteTargetRef -TargetRef $TargetRef -RemoteName $RemoteName
    if (-not $remoteTargetRef.IsValid) { throw $remoteTargetRef.Error }
    $targetRemoteRef = $remoteTargetRef.Ref
    $remoteTargetOutput = @(git ls-remote $RemoteName $targetRemoteRef 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Unable to resolve live remote target '$targetRemoteRef': $($remoteTargetOutput -join ' ')" }
    $remoteTargetSha = if ($remoteTargetOutput.Count -gt 0) { (($remoteTargetOutput[0] -split '\s+')[0]).Trim() } else { '' }
    if (-not $remoteTargetSha -or $remoteTargetSha -ne $targetSha) {
        throw "TargetRef '$TargetRef' is stale or missing locally (local=$targetSha, remote=$remoteTargetSha); fetch and retry against the live target."
    }

    # Verify target is ancestor of candidate
    git merge-base --is-ancestor $targetSha $candidateSha
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine("TargetRef '$TargetRef' ($targetSha) is not an ancestor of CandidateRef '$CandidateRef' ($candidateSha)")
        exit 1
    }
} catch {
    [Console]::Error.WriteLine("Git error during ref resolution: $_")
    exit 2
} finally {
    Pop-Location
}

Write-Host "Integration verification started:`n  Candidate: $candidateSha`n  Target: $targetSha`n  Device: $DeviceSerial`n  Evidence: $EvidenceRoot" -ForegroundColor Cyan

# 3. Non-mutating Local Checks
Write-Host "`n[Step 1/3] Running non-mutating local checks (-NoFix)..." -ForegroundColor Cyan
$localCiPath = Join-Path $HarnessRoot "tools/local-ci.ps1"
if (-not (Test-Path -LiteralPath $localCiPath -PathType Leaf)) {
    [Console]::Error.WriteLine("Required local CI check script is missing: $localCiPath")
    exit 3
}
Push-Location $RepositoryRoot
try {
    & $PwshPath -NoProfile -ExecutionPolicy Bypass -File $localCiPath -NoFix -NoConfigurationCache
    $localCiExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($localCiExit -ne 0) {
    [Console]::Error.WriteLine("Local CI pre-checks failed with exit code $localCiExit")
    exit 1
}

# 4. Canonical Coverage Capture
Write-Host "`n[Step 2/3] Capturing candidate coverage..." -ForegroundColor Cyan
$capturePath = Join-Path $CoverageDir "capture.ps1"
$captureOutput = & $PwshPath -NoProfile -ExecutionPolicy Bypass -File $capturePath `
    -RepositoryRoot $RepositoryRoot `
    -SourceRevision $candidateSha `
    -Purpose "Candidate" `
    -TargetRevision $targetSha `
    -HarnessRoot $HarnessRoot `
    -DeviceSerial $DeviceSerial `
    -OutputRoot $EvidenceRoot

$captureExit = $LASTEXITCODE
if ($captureExit -ne 0) {
    [Console]::Error.WriteLine("Coverage capture failed with exit code $captureExit")
    exit $captureExit
}

foreach ($line in $captureOutput) {
    Write-Host $line
}

try {
    $capturedJson = ConvertFrom-CaptureResultOutput -OutputLines $captureOutput
} catch {
    [Console]::Error.WriteLine("Failed to parse [CAPTURE_RESULT] structured output from capture.ps1: $_")
    exit 2
}

$candidateBundlePath = $capturedJson.run_dir
if (-not (Test-Path $candidateBundlePath)) {
    [Console]::Error.WriteLine("Candidate run bundle not found: $candidateBundlePath")
    exit 2
}

# Validate bundle manifest identities against candidate and target
$manifest = Read-SafeJson -Path (Join-Path $candidateBundlePath "manifest.json")
$identity = Test-CandidateManifestIdentity -Manifest $manifest -ExpectedSourceCommit $candidateSha -ExpectedTargetCommit $targetSha
if (-not $identity.IsValid) {
    [Console]::Error.WriteLine("Run bundle identity validation failed: $($identity.Errors -join '; ')")
    exit 2
}

# 5. Deterministic Verification
Write-Host "`n[Step 3/3] Verifying candidate decisions..." -ForegroundColor Cyan
$verifyPath = Join-Path $CoverageDir "verify.ps1"
$assessmentsDir = Join-Path (Join-Path $EvidenceRoot "assessments") $manifest.run_id

$effectiveBaseline = $BaselineBundle
$localCurrent = Join-Path (Join-Path $EvidenceRoot "baselines") "current.json"
if (-not (Test-Path $localCurrent)) {
    [Console]::Error.WriteLine("No accepted baseline index exists at '$localCurrent'; publish or measure the exact target before comparison.")
    exit 2
}
$curr = Read-SafeJson -Path $localCurrent
$indexValidation = Test-AcceptedBaselineIndex -Index $curr -ExpectedTargetCommit $targetSha
if (-not $indexValidation.IsValid) {
    [Console]::Error.WriteLine("Accepted baseline index validation failed: $($indexValidation.Errors -join '; ')")
    exit 2
}
if ($curr.bundle_path -match '(^|[/\\])\.\.([/\\]|$)' -or [System.IO.Path]::IsPathRooted([string]$curr.bundle_path)) {
    [Console]::Error.WriteLine('Accepted baseline index bundle_path must be a relative path contained under EvidenceRoot.')
    exit 2
}
$indexedBaseline = [System.IO.Path]::GetFullPath((Join-Path $EvidenceRoot $curr.bundle_path))
$canonicalEvidence = [System.IO.Path]::GetFullPath($EvidenceRoot).TrimEnd('\', '/')
if (-not $indexedBaseline.StartsWith($canonicalEvidence + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine('Accepted baseline bundle resolves outside EvidenceRoot.')
    exit 2
}
if ($effectiveBaseline -and [System.IO.Path]::GetFullPath($effectiveBaseline) -ne $indexedBaseline) {
    [Console]::Error.WriteLine('Caller-supplied BaselineBundle does not match the independently indexed accepted baseline.')
    exit 2
}
$effectiveBaseline = $indexedBaseline
if (-not (Test-Path (Join-Path $effectiveBaseline 'manifest.json'))) {
    [Console]::Error.WriteLine("Indexed accepted baseline bundle is missing: $effectiveBaseline")
    exit 2
}
$actualBaselineDigest = Get-CoverageBundleDigest -BundlePath $effectiveBaseline
$indexValidation = Test-AcceptedBaselineIndex -Index $curr -ExpectedTargetCommit $targetSha -ActualBundleDigest $actualBaselineDigest
if (-not $indexValidation.IsValid) {
    [Console]::Error.WriteLine("Accepted baseline index validation failed: $($indexValidation.Errors -join '; '); restore the indexed immutable bundle or measure the target again.")
    exit 2
}
$baselineManifest = Read-SafeJson -Path (Join-Path $effectiveBaseline 'manifest.json')
$baselineIdentity = Test-TrustedComparisonManifest -CandidateManifest $manifest -BaselineManifest $baselineManifest
if (-not $baselineIdentity.IsValid -or $baselineManifest.run_id -ne $curr.run_id) {
    [Console]::Error.WriteLine("Indexed baseline failed provenance/identity validation: $($baselineIdentity.Errors -join '; ')")
    exit 2
}

$changedPathsFile = Join-Path $candidateBundlePath 'changed-paths.txt'
Push-Location $RepositoryRoot
try {
    $computedChangedPaths = @(git diff --name-only $targetSha $candidateSha)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to compute target-to-candidate changed paths.' }
} finally { Pop-Location }
if (-not (Test-Path -LiteralPath $changedPathsFile -PathType Leaf)) {
    [Console]::Error.WriteLine("Candidate bundle is missing its sealed changed-path receipt: $changedPathsFile")
    exit 2
}
$capturedChangedPaths = @(Get-Content -LiteralPath $changedPathsFile | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if (($capturedChangedPaths -join "`n") -cne ($computedChangedPaths -join "`n")) {
    [Console]::Error.WriteLine('Sealed changed-path receipt does not match the exact target-to-candidate Git diff.')
    exit 2
}

$verifyArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $verifyPath,
    "-RunBundle", $candidateBundlePath,
    "-PolicyPath", $policyPath,
    "-OutputDirectory", $assessmentsDir
)
if ($effectiveBaseline) {
    $verifyArgs += @("-ComparisonBundle", $effectiveBaseline, "-AcceptedBaselineIndexPath", $localCurrent)
}
$verifyArgs += @("-ChangedPathsFile", $changedPathsFile)

& $PwshPath @verifyArgs
$verifyCode = $LASTEXITCODE

# 6. Revalidate Git Identities Immediately Before Completion
Push-Location $RepositoryRoot
try {
    $postCandidateSha = (git rev-parse $CandidateRef).Trim()
    $postTargetSha = (git rev-parse $TargetRef).Trim()
    $postRemoteOutput = @(git ls-remote $RemoteName $targetRemoteRef 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Unable to revalidate live remote target '$targetRemoteRef'." }
    $postRemoteSha = if ($postRemoteOutput.Count -gt 0) { (($postRemoteOutput[0] -split '\s+')[0]).Trim() } else { '' }
    if ($postCandidateSha -ne $candidateSha -or $postTargetSha -ne $targetSha -or $postRemoteSha -ne $targetSha) {
        [Console]::Error.WriteLine("Git head or target advanced during integration verification! Candidate: $postCandidateSha (was $candidateSha), Target: $postTargetSha (was $targetSha)")
        exit 2
    }
    $candidateStatus = (& git status --porcelain 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to revalidate candidate working-tree cleanliness.' }
    $candidateDirty = -not [string]::IsNullOrWhiteSpace($candidateStatus)
    $postHarnessRevision = (& git -C $HarnessRoot rev-parse HEAD 2>$null).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to revalidate pinned HarnessRoot revision.' }
    $harnessStatus = (& git -C $HarnessRoot status --porcelain 2>$null) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to revalidate HarnessRoot cleanliness.' }
    $harnessDirty = -not [string]::IsNullOrWhiteSpace($harnessStatus)
    if ($candidateDirty -or $harnessDirty -or $postHarnessRevision -ne $harnessRevision) {
        [Console]::Error.WriteLine('Candidate source or pinned HarnessRoot became dirty or changed during integration verification.')
        exit 2
    }
} finally {
    Pop-Location
}

if ($verifyCode -eq 0) {
    Write-Host "`nIntegration CI verification succeeded locally. Note: Remote hosted check and publication required for merge approval." -ForegroundColor Green
    exit 0
} else {
    [Console]::Error.WriteLine("Integration CI verification failed with exit code $verifyCode")
    exit $verifyCode
}
