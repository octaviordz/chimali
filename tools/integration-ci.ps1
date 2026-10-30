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
    [string]$RemoteName = 'origin',

    [Parameter(DontShow = $true)]
    [string]$InternalCandidateToken
)

$ErrorActionPreference = "Stop"
$gitBindingVariables = @('GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE','GIT_COMMON_DIR','GIT_OBJECT_DIRECTORY','GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_PREFIX','GIT_CEILING_DIRECTORIES','GIT_CONFIG_COUNT','GIT_CONFIG_PARAMETERS')
$savedGitBindingVariables = @{}
foreach ($name in $gitBindingVariables) {
    $savedGitBindingVariables[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
    Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
}
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$internalTokenValid = $InternalCandidateToken -match '^[0-9a-f]{64}$' -and
    $env:CHIMALI_INTEGRATION_PREPARED_TOKEN -ceq $InternalCandidateToken -and
    $env:CHIMALI_INTEGRATION_PREPARED_ROOT -ceq $RepositoryRoot
if (-not $internalTokenValid) {
    $candidateOutput = @(& git -C $RepositoryRoot rev-parse --verify "$CandidateRef^{commit}" 2>&1)
    if ($LASTEXITCODE -ne 0 -or $candidateOutput.Count -ne 1 -or $candidateOutput[0] -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') {
        [Console]::Error.WriteLine("CandidateRef does not resolve to one exact commit: $CandidateRef (exit=$LASTEXITCODE, output=$($candidateOutput -join ' | '), root=$RepositoryRoot)")
        exit 2
    }
    $candidateShaForWorktree = [string]$candidateOutput[0]
    $tempRootForWorktree = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $preparedCheckout = Join-Path $tempRootForWorktree ("chimali-integration-candidate-" + [guid]::NewGuid().ToString('N'))
    $tokenForChild = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $worktreeAdded = $false
    try {
        & git -C $RepositoryRoot worktree add --detach $preparedCheckout $candidateShaForWorktree
        if ($LASTEXITCODE -ne 0) { throw 'Could not create a detached checkout for the exact candidate commit.' }
        $worktreeAdded = $true
        $pwshForChild = Get-Command pwsh -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $pwshForChild) { throw 'PowerShell 7 (pwsh) is required to run the isolated candidate verification.' }
        $trustedEntryPoint = Join-Path ([System.IO.Path]::GetFullPath($HarnessRoot)) 'tools/integration-ci.ps1'
        if (-not (Test-Path -LiteralPath $trustedEntryPoint -PathType Leaf)) { throw 'Selected trusted measurement harness has no integration entry point.' }
        $childArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$trustedEntryPoint,
            '-RepositoryRoot',$preparedCheckout,'-CandidateRef',$candidateShaForWorktree,'-TargetRef',$TargetRef,
            '-HarnessRoot',$HarnessRoot,'-DeviceSerial',$DeviceSerial,'-EvidenceRoot',$EvidenceRoot,
            '-RemoteName',$RemoteName,'-InternalCandidateToken',$tokenForChild)
        if ($BaselineBundle) { $childArgs += @('-BaselineBundle',$BaselineBundle) }
        $env:CHIMALI_INTEGRATION_PREPARED_TOKEN = $tokenForChild
        $env:CHIMALI_INTEGRATION_PREPARED_ROOT = $preparedCheckout
        & $pwshForChild.Source @childArgs
        $isolatedExit = $LASTEXITCODE
        exit $isolatedExit
    } catch {
        [Console]::Error.WriteLine("Exact-candidate isolation failed: $_")
        exit 2
    } finally {
        $canonicalPrepared = [System.IO.Path]::GetFullPath($preparedCheckout)
        $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/')
        if (-not [string]::Equals([System.IO.Path]::GetDirectoryName($canonicalPrepared), $tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($canonicalPrepared) -notmatch '^chimali-integration-candidate-[a-f0-9]{32}$') {
            throw "Refusing unsafe prepared-checkout cleanup path: $canonicalPrepared"
        }
        if ((Test-Path -LiteralPath $canonicalPrepared) -and ((Get-Item -LiteralPath $canonicalPrepared -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw 'Prepared checkout is a reparse point.'
        }
        if ($worktreeAdded) { & git -C $RepositoryRoot worktree remove --force $canonicalPrepared 2>$null | Out-Null }
        for ($attempt = 0; $attempt -lt 10 -and (Test-Path -LiteralPath $canonicalPrepared); $attempt++) {
            try {
                $rootItem = Get-Item -LiteralPath $canonicalPrepared -Force
                if ($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw 'Prepared checkout is a reparse point.' }
                Get-ChildItem -LiteralPath $canonicalPrepared -Force -Recurse | ForEach-Object {
                    if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) { $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly) }
                }
                [System.IO.Directory]::Delete($canonicalPrepared,$true)
            } catch {
                if ($attempt -eq 9) { Write-Warning "Could not remove prepared checkout '$canonicalPrepared': $_" }
                else { Start-Sleep -Milliseconds 500 }
            }
        }
        if ($worktreeAdded) { & git -C $RepositoryRoot worktree prune 2>$null | Out-Null }
        Remove-Item Env:CHIMALI_INTEGRATION_PREPARED_TOKEN -ErrorAction SilentlyContinue
        Remove-Item Env:CHIMALI_INTEGRATION_PREPARED_ROOT -ErrorAction SilentlyContinue
        foreach ($name in $gitBindingVariables) {
            if ($null -eq $savedGitBindingVariables[$name]) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            } else {
                [Environment]::SetEnvironmentVariable($name,$savedGitBindingVariables[$name],'Process')
            }
        }
    }
}
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
$env:CHIMALI_CANDIDATE_CHECKOUT = $RepositoryRoot

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
try {
    $computedChangedPaths = @(Get-GitChangedPaths -RepositoryRoot $RepositoryRoot -TargetRevision $targetSha -SourceRevision $candidateSha)
} catch { [Console]::Error.WriteLine("Unable to compute target-to-candidate changed paths: $_"); exit 2 }
if (-not (Test-Path -LiteralPath $changedPathsFile -PathType Leaf)) {
    [Console]::Error.WriteLine("Candidate bundle is missing its sealed changed-path receipt: $changedPathsFile")
    exit 2
}
$changedReceipt = Read-ChangedPathReceipt -Path $changedPathsFile -ExpectedTargetCommit $targetSha -ExpectedSourceCommit $candidateSha
$changedPathsDiffer = $changedReceipt.Paths.Count -ne $computedChangedPaths.Count
if (-not $changedPathsDiffer) {
    for ($i = 0; $i -lt $computedChangedPaths.Count; $i++) {
        if ([string]$changedReceipt.Paths[$i] -cne [string]$computedChangedPaths[$i]) { $changedPathsDiffer = $true; break }
    }
}
if (-not $changedReceipt.IsValid -or $changedPathsDiffer) {
    [Console]::Error.WriteLine('Sealed changed-path receipt does not match the exact target-to-candidate Git diff.')
    if ($changedReceipt.Errors.Count -gt 0) { [Console]::Error.WriteLine($changedReceipt.Errors -join '; ') }
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

$assessmentRecordPath = Join-Path $assessmentsDir 'assessment-record.json'
New-Item -ItemType Directory -Path $assessmentsDir -Force | Out-Null
if (Test-Path -LiteralPath $assessmentRecordPath) {
    [Console]::Error.WriteLine("Assessment record is immutable and already exists: $assessmentRecordPath")
    exit 2
}
$baselineBundleDigest = Get-CoverageBundleDigest -BundlePath $effectiveBaseline
$baselineTree = [string]$baselineManifest.git.source_tree
$candidateBundleDigest = Get-CoverageBundleDigest -BundlePath $candidateBundlePath
$manifestDigest = Get-FileSha256 -Path (Join-Path $candidateBundlePath 'manifest.json')
$assessmentStatus = if ($verifyCode -eq 0) { 'PASS' } elseif ($verifyCode -eq 1) { 'FAIL' } else { 'INCOMPLETE' }
$assessmentRecord = [PSCustomObject]@{
    schema_version = '1.0'
    assessment_id = [guid]::NewGuid().ToString()
    decision = $assessmentStatus
    created_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    repository = [PSCustomObject]@{ root = $RepositoryRoot; remote = $RemoteName }
    candidate = [PSCustomObject]@{ commit = $candidateSha; tree = [string]$manifest.git.source_tree }
    target = [PSCustomObject]@{ ref = $TargetRef; commit = $targetSha }
    accepted_baseline = [PSCustomObject]@{
        commit = [string]$baselineManifest.git.source_commit
        tree = $baselineTree
        run_id = [string]$baselineManifest.run_id
        bundle_path = [System.IO.Path]::GetRelativePath($EvidenceRoot,$effectiveBaseline).Replace('\','/')
        bundle_digest = $baselineBundleDigest
        index_path = [System.IO.Path]::GetRelativePath($EvidenceRoot,$localCurrent).Replace('\','/')
        index_digest = Get-FileSha256 -Path $localCurrent
    }
    policy = [PSCustomObject]@{
        id = [string]$manifest.policy_id
        scope_fingerprint = [string]$manifest.measurement_fingerprint
    }
    harness = [PSCustomObject]@{
        commit = $harnessRevision
        file_sha256 = $manifest.harness.file_sha256
    }
    device = $manifest.device
    required_suites = $manifest.suites
    run = [PSCustomObject]@{
        id = [string]$manifest.run_id
        bundle_path = $candidateBundlePath
        bundle_digest = $candidateBundleDigest
        manifest_digest = $manifestDigest
        decision_path = (Join-Path $assessmentsDir 'decision.json')
        decision_digest = if (Test-Path -LiteralPath (Join-Path $assessmentsDir 'decision.json')) { Get-FileSha256 -Path (Join-Path $assessmentsDir 'decision.json') } else { $null }
    }
    hosted_check = 'Unverified'
}
$assessmentRecord | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $assessmentRecordPath -Encoding utf8

if ($verifyCode -eq 0) {
    Write-Host "`nIntegration CI verification succeeded locally. Note: Remote hosted check and publication required for merge approval." -ForegroundColor Green
    exit 0
} else {
    [Console]::Error.WriteLine("Integration CI verification failed with exit code $verifyCode")
    exit $verifyCode
}
