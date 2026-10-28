<#
.SYNOPSIS
    Publishes verified coverage run bundles to the protected codex/coverage-evidence branch.
.DESCRIPTION
    Validates artifact digests, enforces append-only immutability (runs/<run_id>/),
    detects atomic publication conflicts, and produces an immutable publication receipt
    outside the bundle to avoid self-referential content hashes.
    Dry runs, absent credentials, transfer failure, and pending pushes never produce
    a Published acceptance receipt.
.PARAMETER RunBundle
    Absolute path to the local run bundle directory.
.PARAMETER EvidenceBranch
    Branch name for the coverage evidence repository (default: codex/coverage-evidence).
.PARAMETER RemoteName
    Git remote name (default: origin).
.PARAMETER OutputReceiptPath
    Path to write the resulting publication receipt JSON file.
    Must not be inside RunBundle.
.PARAMETER PromoteAsAcceptedTarget
    Advance the accepted comparison target index to this run.
.PARAMETER IntegratedCommit
    Commit SHA of the integrated candidate.
.PARAMETER IntegratedTree
    Tree SHA of the integrated candidate.
.PARAMETER DryRun
    Simulates publication and generates a DryRun receipt without pushing to remote.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RunBundle,

    [Parameter(Mandatory = $false)]
    [string]$EvidenceBranch = "codex/coverage-evidence",

    [Parameter(Mandatory = $false)]
    [string]$RemoteName = "origin",

    [Parameter(Mandatory = $false)]
    [string]$OutputReceiptPath,

    [switch]$PromoteAsAcceptedTarget,

    [Parameter(Mandatory = $false)]
    [string]$IntegratedCommit,

    [Parameter(Mandatory = $false)]
    [string]$IntegratedTree,

    [Parameter(Mandatory = $false)]
    [string]$AssessmentPath,

    [Parameter(Mandatory = $false)]
    [string]$IntegrationEventPath,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedRepository,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedHeadSha,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedBaseSha,

    [Parameter(Mandatory = $false)]
    [int]$PullRequestNumber,

    [Parameter(Mandatory = $false)]
    [string]$IntegrationTargetRef,

    [Parameter(Mandatory = $false)]
    [string]$ComparisonBundle,

    [Parameter(Mandatory = $false)]
    [string]$AcceptedBaselineIndexPath,

    [Parameter(Mandatory = $false)]
    [string]$ChangedPathsFile,

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
function Write-CoverageError {
    param([Parameter(Mandatory = $true)][object]$Message)
    [Console]::Error.WriteLine([string]$Message)
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-CoverageError "Common utilities not found at $CommonPath"
    exit 3
}

. $CommonPath

if (-not (Test-Path $RunBundle)) {
    Write-CoverageError "RunBundle directory not found: $RunBundle"
    exit 2
}

$manifestPath = Join-Path $RunBundle "manifest.json"
if (-not (Test-Path $manifestPath)) {
    Write-CoverageError "manifest.json not found in RunBundle: $manifestPath"
    exit 2
}

$manifest = Read-SafeJson -Path $manifestPath
$manifestDigest = Get-FileSha256 -Path $manifestPath
$runId = $manifest.run_id
$testedCommit = [string]$manifest.git.source_commit
$testedTree = [string]$manifest.git.source_tree

# Resolve and validate the immutable receipt destination before promotion checks.
# A real trusted-verifier failure must leave a durable, non-success receipt.
$bundleParent = Split-Path -Parent $RunBundle
$receiptTarget = if ($OutputReceiptPath) {
    if (-not [System.IO.Path]::IsPathRooted($OutputReceiptPath)) {
        Write-CoverageError "OutputReceiptPath must be an absolute path: $OutputReceiptPath"
        exit 3
    }
    $OutputReceiptPath
} else {
    Join-Path (Join-Path $bundleParent "assessments") (Join-Path $runId "publication-receipt.json")
}
$canonicalBundle = Get-CanonicalCoveragePath -Path $RunBundle
$canonicalReceipt = Get-CanonicalCoveragePath -Path $receiptTarget
if (Test-CoveragePathWithinRoot -Path $canonicalReceipt -Root $canonicalBundle) {
    Write-CoverageError "Publication receipt cannot be written inside sealed capture bundle: $RunBundle"
    exit 3
}
if (Test-Path -LiteralPath $receiptTarget) {
    Write-CoverageError "Publication receipt is immutable and already exists: $receiptTarget"
    exit 1
}
$receiptDir = Split-Path -Parent $receiptTarget
if (-not (Test-Path $receiptDir)) {
    New-Item -ItemType Directory -Path $receiptDir -Force | Out-Null
}

if (-not $runId) {
    Write-CoverageError "manifest.json is missing run_id"
    exit 2
}

if ($PromoteAsAcceptedTarget) {
    if (-not $AssessmentPath -or -not (Test-Path -LiteralPath $AssessmentPath -PathType Leaf) -or
        -not $IntegrationEventPath -or -not (Test-Path -LiteralPath $IntegrationEventPath -PathType Leaf) -or
        -not $ExpectedRepository -or -not $ExpectedHeadSha -or -not $ExpectedBaseSha -or
        -not $ComparisonBundle -or -not $AcceptedBaselineIndexPath -or -not $ChangedPathsFile) {
        Write-CoverageError 'Promotion requires an assessment, explicit integration-event evidence, expected repository/head/base identities, and the exact accepted baseline/index and sealed changed paths.'
        exit 3
    }
    $trustedVerificationAttempted = $false
    $trustedVerificationDecision = $null
    $trustedVerificationOutput = @()
    $trustedVerificationCode = $null
    $trustedVerifierRejected = $false
    try {
        $suppliedAssessment = Read-SafeJson -Path $AssessmentPath
        $integrationEvent = Read-SafeJson -Path $IntegrationEventPath
        $manifestDigest = Get-FileSha256 -Path $manifestPath
        if ($suppliedAssessment.manifest_digest -ne $manifestDigest) { throw 'Assessment manifest digest does not match the sealed run manifest' }

        # Re-run the trusted verifier before accepting any caller-provided assessment
        # for baseline promotion. The supplied assessment is only a consistency check.
        $verifyScript = Join-Path $ScriptDir 'verify.ps1'
        $policyPath = Join-Path $ScriptDir 'policy.json'
        if (-not (Test-Path -LiteralPath $verifyScript -PathType Leaf) -or -not (Test-Path -LiteralPath $policyPath -PathType Leaf)) {
            throw 'Trusted verifier or reviewed policy is missing beside the publisher'
        }
        $verificationRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-promotion-verify-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $verificationRoot -Force | Out-Null
        try {
            $independentArgs = @('-RunBundle', $RunBundle, '-PolicyPath', $policyPath, '-OutputDirectory', $verificationRoot,
                '-ComparisonBundle', $ComparisonBundle, '-AcceptedBaselineIndexPath', $AcceptedBaselineIndexPath, '-ChangedPathsFile', $ChangedPathsFile)
            Push-Location (Join-Path $ScriptDir '../..')
            try {
                $trustedVerificationAttempted = $true
                $trustedVerificationOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $verifyScript @independentArgs 2>&1)
                $independentVerifyCode = $LASTEXITCODE
                $trustedVerificationCode = $independentVerifyCode
            } finally { Pop-Location }
            $independentDecisionPath = Join-Path $verificationRoot 'decision.json'
            if (Test-Path -LiteralPath $independentDecisionPath -PathType Leaf) {
                $trustedVerificationDecision = Read-SafeJson -Path $independentDecisionPath
            }
            if ($independentVerifyCode -ne 0 -or -not (Test-Path -LiteralPath $independentDecisionPath -PathType Leaf)) {
                $trustedVerifierRejected = $true
                throw "Independent trusted verification did not pass (exit $independentVerifyCode)"
            }
            $assessment = $trustedVerificationDecision
            if ($assessment.overall_passed -isnot [bool] -or -not $assessment.overall_passed) {
                $trustedVerifierRejected = $true
                throw 'Independent trusted verification returned a non-passing decision'
            }
            foreach ($field in @('run_id', 'overall_passed')) {
                if ($suppliedAssessment.$field -cne $assessment.$field) { throw "Supplied assessment $field does not match independent verification" }
            }
            if ($suppliedAssessment.decisions.collection -cne $assessment.decisions.collection -or
                $suppliedAssessment.decisions.tests -cne $assessment.decisions.tests -or
                $suppliedAssessment.decisions.floors -cne $assessment.decisions.floors -or
                $suppliedAssessment.decisions.comparison_compatibility -cne $assessment.decisions.comparison_compatibility -or
                $suppliedAssessment.decisions.regression -cne $assessment.decisions.regression) {
                throw 'Supplied assessment decisions do not match independent verification'
            }
        } finally {
            $canonicalVerifyRoot = [System.IO.Path]::GetFullPath($verificationRoot)
            $canonicalTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
            if ([System.IO.Path]::GetDirectoryName($canonicalVerifyRoot) -ne $canonicalTempRoot -or [System.IO.Path]::GetFileName($canonicalVerifyRoot) -notlike 'coverage-promotion-verify-*') {
                throw "Refusing to remove unexpected promotion verification directory: $canonicalVerifyRoot"
            }
            if (Test-Path -LiteralPath $canonicalVerifyRoot) { [System.IO.Directory]::Delete($canonicalVerifyRoot, $true) }
        }
        # Treat the event file only as request context. Establish merge state and
        # integrated object identities through the trusted live-event validator.
        $publishToken = $env:CHIMALI_COVERAGE_PUBLISH_TOKEN
        $eventValidator = Join-Path $ScriptDir 'validate-integration-event.ps1'
        if (-not (Test-Path -LiteralPath $eventValidator -PathType Leaf)) { throw 'Trusted integration-event validator is missing beside the publisher' }
        $eventArgs = @('-Repository', $ExpectedRepository, '-HeadSha', $ExpectedHeadSha, '-BaseSha', $ExpectedBaseSha,
            '-IntegrationEventPath', $IntegrationEventPath, '-PullRequestNumber', [string]$PullRequestNumber, '-Token', $publishToken)
        if ($IntegrationTargetRef) { $eventArgs += @('-ExpectedTargetRef', $IntegrationTargetRef) }
        $eventOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $eventValidator @eventArgs 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "Independent integration-event validation failed: $($eventOutput -join ' ')" }
        $verifiedEvent = ($eventOutput -join "`n") | ConvertFrom-Json -ErrorAction Stop
        $apiIntegratedCommit = [string]$verifiedEvent.integration_commit
        $apiIntegratedTree = [string]$verifiedEvent.integration_tree
        if ([string]::IsNullOrWhiteSpace($apiIntegratedCommit) -or [string]::IsNullOrWhiteSpace($apiIntegratedTree)) { throw 'Trusted integration-event validator omitted actual commit/tree metadata' }
        if ($IntegratedCommit -and $IntegratedCommit -cne $apiIntegratedCommit) { throw 'Caller integrated commit disagrees with repository API metadata' }
        if ($IntegratedTree -and $IntegratedTree -cne $apiIntegratedTree) { throw 'Caller integrated tree disagrees with repository API metadata' }
        foreach ($field in @('event_type', 'merged', 'integration_confirmed', 'target_ref', 'repository', 'head_sha', 'base_sha', 'integration_commit', 'integration_tree')) { $integrationEvent | Add-Member -NotePropertyName $field -NotePropertyValue $verifiedEvent.$field -Force }
        $readiness = Test-PromotionReadiness -Manifest $manifest -Decision $assessment -IntegrationEvent $integrationEvent -Repository $ExpectedRepository -HeadSha $ExpectedHeadSha -BaseSha $ExpectedBaseSha
        if (-not $readiness.IsValid) { throw ($readiness.Errors -join '; ') }
        $IntegratedCommit = $readiness.IntegratedCommit
        $IntegratedTree = $readiness.IntegratedTree
    } catch {
        if ($trustedVerificationAttempted -and $trustedVerifierRejected -and -not (Test-Path -LiteralPath $receiptTarget)) {
            try {
                $failureBundleDigest = Get-CoverageBundleDigest -BundlePath $RunBundle
                $failureManifestDigest = Get-FileSha256 -Path $manifestPath
                $failureReceipt = [PSCustomObject]@{
                    run_id = $runId
                    schema_version = '1.0'
                    publication_purpose = $manifest.purpose
                    published_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
                    archive_branch = $EvidenceBranch
                    archive_commit = $null
                    read_back_commit = $null
                    bundle_digest = $failureBundleDigest
                    manifest_digest = $failureManifestDigest
                    status = 'Failed'
                    retrievable = $false
                    content_readback_verified = $false
                    verification_exit_code = $trustedVerificationCode
                    verification_decision = $trustedVerificationDecision
                    verification_output = @($trustedVerificationOutput | ForEach-Object { [string]$_ })
                    error = [string]$_
                }
                $failureReceipt | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $receiptTarget -Encoding utf8
            } catch {
                Write-Warning "Could not retain the trusted-verification failure receipt: $_"
            }
        }
        Write-CoverageError "Promotion readiness validation failed: $_"
        exit 1
    }
}

# 1. Output Receipt Path Validation (Must be outside sealed bundle)
Write-Host "Publishing coverage run bundle $runId to $EvidenceBranch..." -ForegroundColor Cyan

# 2. Complete Evidence Payload Verification & Digest Calculation
$bundleFiles = Get-ChildItem -Path $RunBundle -Recurse -File
$payloadHashes = @{}
$artifactLinks = @{}
$allHashesString = ""

foreach ($f in ($bundleFiles | Sort-Object FullName)) {
    $relPath = [System.IO.Path]::GetRelativePath($RunBundle, $f.FullName).Replace("\", "/")
    $fHash = Get-FileSha256 -Path $f.FullName
    $payloadHashes[$relPath] = $fHash
    $artifactLinks[$relPath] = "runs/$runId/$relPath"
    $allHashesString += "${relPath}:${fHash};"
}

# Verify manifest artifact digests if present in manifest
if ($manifest.artifacts) {
    foreach ($art in $manifest.artifacts) {
        if ($art.path -and $art.sha256) {
            $normPath = $art.path.Replace("\", "/")
            if ($payloadHashes.ContainsKey($normPath)) {
                if ($payloadHashes[$normPath] -ne $art.sha256.ToLower()) {
                    Write-CoverageError "Artifact SHA256 digest mismatch: expected $($art.sha256), actual $($payloadHashes[$normPath])"
                    exit 1
                }
            } else {
                Write-CoverageError "Artifact declared in manifest missing from bundle payload: $($art.path)"
                exit 1
            }
        }
    }
}

$sha256Obj = [System.Security.Cryptography.SHA256]::Create()
$bundleDigest = [BitConverter]::ToString($sha256Obj.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($allHashesString))).Replace("-", "").ToLower()

# 3. Dry-Run Handling
if ($DryRun) {
    Write-Host "Dry run enabled. Skipping remote publication. Receipt marked DryRun (not Published)." -ForegroundColor Yellow
    $dryReceipt = [PSCustomObject]@{
        run_id = $runId
        schema_version = "1.0"
        publication_purpose = $manifest.purpose
        published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        archive_branch = $EvidenceBranch
        archive_commit = $null
        bundle_digest = $bundleDigest
        status = "DryRun"
        retrievable = $false
        artifacts = $artifactLinks
    }
    $dryReceipt | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
    Write-Host "Dry run receipt generated at $receiptTarget" -ForegroundColor Yellow
    exit 0
}

# 4. Check Remote Accessibility and Atomic Conflict Detection
# Verify git remote
$repoRoot = (git rev-parse --show-toplevel 2>$null)
if (-not $repoRoot) {
    Write-CoverageError "Current directory is not inside a git repository"
    exit 2
}
# Check if remote branch exists and can be fetched
$publishToken = $env:CHIMALI_COVERAGE_PUBLISH_TOKEN
if ([string]::IsNullOrWhiteSpace($publishToken)) {
    Write-CoverageError "CHIMALI_COVERAGE_PUBLISH_TOKEN is required for trusted archive publication."
    exit 2
}

$remoteUrl = (& git remote get-url $RemoteName 2>$null | Select-Object -First 1)
try {
    $remoteUri = [Uri]$remoteUrl
} catch {
    Write-CoverageError "Remote '$RemoteName' must use an HTTPS GitHub URL for trusted archive publication."
    exit 2
}
if ($remoteUri.Scheme -ne 'https' -or $remoteUri.Host -ne 'github.com') {
    Write-CoverageError "Remote '$RemoteName' must use an HTTPS GitHub URL for trusted archive publication."
    exit 2
}

$credentialBytes = [System.Text.Encoding]::UTF8.GetBytes("x-access-token:$publishToken")
$credentialBase64 = [Convert]::ToBase64String($credentialBytes)
$gitAuthArgs = @('-c', "http.https://github.com/.extraheader=AUTHORIZATION: basic $credentialBase64")
if ($PromoteAsAcceptedTarget) {
    # The trusted reporter checkout may not contain the merge commit. Fetch its
    # exact API-confirmed SHA before verifying the object, tree, and ancestry.
    & git @gitAuthArgs fetch --no-tags $RemoteName $IntegratedCommit --quiet 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-CoverageError 'Unable to retrieve the API-confirmed integrated commit from the trusted repository remote.'
        exit 1
    }
    $resolvedIntegratedCommit = (& git -C $repoRoot rev-parse --verify "$IntegratedCommit^{commit}" 2>$null).Trim()
    $resolvedIntegratedTree = if ($resolvedIntegratedCommit) { (& git -C $repoRoot rev-parse "$resolvedIntegratedCommit^{tree}" 2>$null).Trim() } else { '' }
    if ($resolvedIntegratedCommit -cne $IntegratedCommit -or $resolvedIntegratedTree -cne $IntegratedTree) {
        Write-CoverageError 'Integrated commit is not locally retrievable or its actual tree differs from the tested tree.'
        exit 1
    }
    & git -C $repoRoot merge-base --is-ancestor $ExpectedBaseSha $IntegratedCommit 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-CoverageError 'Integrated commit is not descended from the expected target base.'
        exit 1
    }
    & git -C $repoRoot merge-base --is-ancestor $testedCommit $IntegratedCommit 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-CoverageError 'Integrated commit does not contain the tested candidate commit.'
        exit 1
    }
}
$remoteCheck = & git @gitAuthArgs ls-remote --heads $RemoteName $EvidenceBranch 2>&1
if ($LASTEXITCODE -ne 0) {
    $failReceipt = [PSCustomObject]@{
        run_id = $runId
        schema_version = "1.0"
        publication_purpose = $manifest.purpose
        published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        archive_branch = $EvidenceBranch
        archive_commit = $null
        bundle_digest = $bundleDigest
        status = "Failed"
        error = "Credentials absent or remote unreachable: $remoteCheck"
    }
    $failReceipt | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
    Write-Warning "Failed to access remote $RemoteName branch ${EvidenceBranch}: credentials absent or remote unreachable"
    exit 2
}

# Create an isolated temporary worktree to prepare the archive commit
$tempWorktree = Join-Path ([System.IO.Path]::GetTempPath()) ("coverage-pub-" + [Guid]::NewGuid().ToString())
try {
    # Fetch or clone branch
    if ([string]::IsNullOrWhiteSpace($remoteCheck)) {
        # Branch does not exist on remote yet; create orphan worktree
        & git worktree add --detach $tempWorktree
        Push-Location $tempWorktree
        & git checkout --orphan $EvidenceBranch
        & git rm -rf . --quiet 2>$null
    } else {
        & git @gitAuthArgs fetch $RemoteName "${EvidenceBranch}:${EvidenceBranch}" --quiet 2>$null
        & git worktree add $tempWorktree $EvidenceBranch --quiet
        Push-Location $tempWorktree
    }

    # The archive must preserve the exact measured bytes; disable Windows CRLF
    # normalization in the temporary archive worktree before staging the bundle.
    & git config core.autocrlf false
    if ($LASTEXITCODE -ne 0) {
        Write-CoverageError 'Unable to disable line-ending normalization in the temporary archive worktree.'
        exit 1
    }

    # Coverage bundles can include deeply nested Android test-result paths. Git
    # for Windows rejects those paths by default even when the worktree itself
    # is short, so opt into long-path support for this isolated archive worktree.
    & git config core.longpaths true
    if ($LASTEXITCODE -ne 0) {
        Write-CoverageError 'Unable to enable long-path support in the temporary archive worktree.'
        exit 1
    }

    # Atomic Conflict Detection: Check if runs/<runId> already exists
    $targetRunDir = Join-Path (Join-Path $tempWorktree "runs") $runId
    if (Test-Path $targetRunDir) {
        Write-CoverageError "Cannot overwrite immutable run record: runs/$runId already exists in $EvidenceBranch"
        exit 1
    }

    # Copy bundle files into runs/<runId>/
    New-Item -ItemType Directory -Path $targetRunDir -Force | Out-Null
    Copy-Item -Path (Join-Path $RunBundle "*") -Destination $targetRunDir -Recurse -Force

    # If promoting as accepted target, verify tree and update index
    if ($PromoteAsAcceptedTarget) {
        if (-not $IntegratedTree) {
            Write-CoverageError "IntegratedTree is required when promoting as accepted target"
            exit 1
        }
        $promotionIdentity = Test-PromotionIdentity -Manifest $manifest -IntegratedTree $IntegratedTree -IntegratedCommit $IntegratedCommit
        if (-not $promotionIdentity.IsValid) {
            Write-CoverageError "Promotion identity validation failed: $($promotionIdentity.Errors -join '; ')"
            exit 1
        }
        $testedCommit = $promotionIdentity.TestedCommit
        $testedTree = $promotionIdentity.TestedTree
        $IntegratedCommit = $promotionIdentity.IntegratedCommit

        $baselinesDir = Join-Path $tempWorktree "baselines"
        if (-not (Test-Path $baselinesDir)) {
            New-Item -ItemType Directory -Path $baselinesDir -Force | Out-Null
        }
        $acceptedIndex = [PSCustomObject]@{
            tested_commit = $testedCommit
            integrated_commit = if ($IntegratedCommit) { $IntegratedCommit } else { $testedCommit }
            target_tree = $IntegratedTree
            tested_tree = $testedTree
            target_commit = if ($IntegratedCommit) { $IntegratedCommit } else { $testedCommit }
            run_id = $runId
            archive_branch = $EvidenceBranch
            bundle_path = "runs/$runId"
            bundle_digest = $bundleDigest
            promoted_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
            provenance = "ArchivedAcceptedRun"
        }
        $acceptedIndex | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $baselinesDir "current.json") -Encoding utf8
    }

    # Commit to evidence branch (bypass product build pre-commit hook since evidence branch contains only archive runs)
    & git add -A
    $commitMsg = "archive(coverage): publish run $runId"
    & git commit -m $commitMsg --no-verify --quiet
    if ($LASTEXITCODE -ne 0) {
        $commitFailure = [PSCustomObject]@{
            run_id = $runId
            schema_version = '1.0'
            publication_purpose = $manifest.purpose
            published_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
            archive_branch = $EvidenceBranch
            archive_commit = $null
            bundle_digest = $bundleDigest
            status = 'Failed'
            retrievable = $false
            content_readback_verified = $false
            error = 'Failed to commit archive evidence in temporary worktree'
        }
        $commitFailure | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $receiptTarget -Encoding utf8
        Write-Warning $commitFailure.error
        exit 1
    }
    $archiveCommit = (git rev-parse HEAD).Trim()

    # Push to remote with atomic check and no-verify (evidence branch contains no product build scripts)
    & git @gitAuthArgs push --no-verify $RemoteName "${EvidenceBranch}:${EvidenceBranch}" 2>&1
    if ($LASTEXITCODE -ne 0) {
        $conflictReceipt = [PSCustomObject]@{
            run_id = $runId
            schema_version = "1.0"
            publication_purpose = $manifest.purpose
            published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
            archive_branch = $EvidenceBranch
            archive_commit = $null
            bundle_digest = $bundleDigest
            status = "Failed"
            error = "Push conflict on $EvidenceBranch"
        }
        $conflictReceipt | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
        Write-Warning "Concurrent publication conflict or push failure to $EvidenceBranch"
        exit 1
    }

    # Read-back verification must inspect the remote ref, not only the local worktree.
    $remoteRef = "refs/heads/$EvidenceBranch"
    $publishedRef = & git @gitAuthArgs ls-remote --heads $RemoteName $remoteRef 2>&1
    if ($LASTEXITCODE -ne 0) {
        $readBackFailure = [PSCustomObject]@{
            run_id = $runId
            schema_version = "1.0"
            publication_purpose = $manifest.purpose
            published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
            archive_branch = $EvidenceBranch
            archive_commit = $null
            bundle_digest = $bundleDigest
            status = "Failed"
            error = "Remote read-back failed after push"
        }
        $readBackFailure | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
        Write-Warning "Archive push succeeded, but remote read-back failed for $remoteRef"
        exit 1
    }
    $verifyRef = Get-RemoteRefCommit -Output @($publishedRef) -ExpectedRef $remoteRef
    if ([string]::IsNullOrWhiteSpace($verifyRef) -or $verifyRef -ne $archiveCommit) {
        $readBackFailure = [PSCustomObject]@{
            run_id = $runId
            schema_version = "1.0"
            publication_purpose = $manifest.purpose
            published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
            archive_branch = $EvidenceBranch
            archive_commit = $null
            bundle_digest = $bundleDigest
            status = "Failed"
            error = "Remote read-back commit did not match the published commit"
        }
        $readBackFailure | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
        Write-Warning "Remote read-back verification failed: expected $archiveCommit, got '$verifyRef'"
        exit 1
    }

    # Fetch the published ref and compare every archived path's remote blob to the local sealed bundle.
    try {
    $fetchOutput = & git @gitAuthArgs -C $tempWorktree fetch --no-tags $RemoteName $remoteRef 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Remote ref was visible but could not be fetched for content read-back: $fetchOutput"
    }
    # FETCH_HEAD is populated from the branch ref above and names its commit.
    # Avoid shell-sensitive `^{commit}` syntax so the verification also works
    # with Windows command shims used by offline protocol fixtures.
    $fetchedCommitOutput = & git -C $tempWorktree rev-parse --verify FETCH_HEAD 2>$null
    if ($LASTEXITCODE -ne 0 -or $null -eq $fetchedCommitOutput) {
        throw "Fetched archive ref has no resolvable commit (fetch output: $fetchOutput)"
    }
    $fetchedCommit = ([string]$fetchedCommitOutput).Trim()
    $localBundleFiles = @(Get-ChildItem -LiteralPath $RunBundle -Recurse -File)
    $expectedRelativePaths = @($localBundleFiles | ForEach-Object { [System.IO.Path]::GetRelativePath($RunBundle, $_.FullName).Replace('\', '/') } | Sort-Object -CaseSensitive)
    $runPrefix = "runs/$runId/"
    $remoteTreeLines = @(& git -C $tempWorktree ls-tree -r --full-tree --format='%(objectname)%x09%(path)' $fetchedCommit -- "runs/$runId")
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate the fetched archive tree' }
    $remoteObjects = @{}
    foreach ($line in $remoteTreeLines) {
        $record = [string]$line
        $separator = $record.IndexOf("`t", [System.StringComparison]::Ordinal)
        if ($separator -le 0) { throw 'Fetched archive tree contained a malformed object record' }
        $objectId = $record.Substring(0, $separator)
        $path = $record.Substring($separator + 1)
        if (-not $path.StartsWith($runPrefix, [System.StringComparison]::Ordinal)) {
            throw "Unexpected archive path in fetched run tree: $path"
        }
        $relativePath = $path.Substring($runPrefix.Length)
        if ($remoteObjects.ContainsKey($relativePath)) { throw "Fetched archive tree contains a duplicate path: $relativePath" }
        $remoteObjects[$relativePath] = $objectId
    }
    if ($fetchedCommit -cne $verifyRef -or $expectedRelativePaths.Count -eq 0 -or
        $expectedRelativePaths.Count -ne $remoteObjects.Count -or
        (@($expectedRelativePaths | Where-Object { -not $remoteObjects.ContainsKey($_) }).Count -gt 0)) {
        throw 'Fetched archive run tree does not exactly match the published bundle file set'
    }
    $objectFormat = (& git -C $tempWorktree rev-parse --show-object-format=storage 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $objectFormat -notin @('sha1', 'sha256')) { throw 'Unsupported archive Git object format during content read-back' }
    $hashAlgorithm = if ($objectFormat -eq 'sha256') { [Security.Cryptography.HashAlgorithmName]::SHA256 } else { [Security.Cryptography.HashAlgorithmName]::SHA1 }
    foreach ($bundleFile in $localBundleFiles) {
        $relativePath = [System.IO.Path]::GetRelativePath($RunBundle, $bundleFile.FullName).Replace('\', '/')
        if ($relativePath.Contains("`t") -or $relativePath.Contains("`n") -or $relativePath.Contains("`r")) {
            throw "Bundle path contains a control character unsupported by Git's formatted tree output: $relativePath"
        }
        $blobHash = [Security.Cryptography.IncrementalHash]::CreateHash($hashAlgorithm)
        try {
            $header = [Text.Encoding]::ASCII.GetBytes("blob $($bundleFile.Length)`0")
            $blobHash.AppendData($header)
            $stream = [IO.File]::OpenRead($bundleFile.FullName)
            try {
                $buffer = [byte[]]::new(65536)
                while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) { $blobHash.AppendData($buffer, 0, $read) }
            } finally { $stream.Dispose() }
            $localBlob = [Convert]::ToHexString($blobHash.GetHashAndReset()).ToLowerInvariant()
        } finally { $blobHash.Dispose() }
        if ($localBlob -cne [string]$remoteObjects[$relativePath]) {
            throw "Fetched archive content differs from the measured run artifact: $relativePath"
        }
    }

    } catch {
        $readBackFailure = [PSCustomObject]@{
            run_id = $runId
            schema_version = '1.0'
            publication_purpose = $manifest.purpose
            published_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
            archive_branch = $EvidenceBranch
            archive_commit = $archiveCommit
            bundle_digest = $bundleDigest
            status = 'Failed'
            retrievable = $false
            content_readback_verified = $false
            error = "Remote content read-back failed: $($_.Exception.Message)"
        }
        $readBackFailure | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $receiptTarget -Encoding utf8
        Write-CoverageError $readBackFailure.error
        exit 1
    }

    # Write Published receipt
    $receipt = [PSCustomObject]@{
        run_id = $runId
        schema_version = "1.0"
        publication_purpose = $manifest.purpose
        published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        archive_branch = $EvidenceBranch
        archive_commit = $archiveCommit
        manifest_digest = $manifestDigest
        bundle_digest = $bundleDigest
        status = "Published"
        retrievable = $true
        archive_ref = $remoteRef
        read_back_commit = $verifyRef
        content_readback_verified = $true
        archived_bundle_path = "runs/$runId"
        artifacts = $artifactLinks
    }
    if ($PromoteAsAcceptedTarget) {
        $receipt | Add-Member -MemberType NoteProperty -Name "promoted_as_accepted_target" -Value $true
        $receipt | Add-Member -MemberType NoteProperty -Name "tested_commit" -Value $testedCommit
        $receipt | Add-Member -MemberType NoteProperty -Name "integrated_commit" -Value $IntegratedCommit
        $receipt | Add-Member -MemberType NoteProperty -Name "integrated_tree" -Value $IntegratedTree
    }
    $receipt | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
    Write-Host "Publication successful! Archive commit: $archiveCommit. Receipt: $receiptTarget" -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
    & git worktree remove $tempWorktree --force 2>$null
    if (Test-Path $tempWorktree) {
        Remove-Item -Path $tempWorktree -Recurse -Force -ErrorAction SilentlyContinue
    }
    $publishToken = $null
    $credentialBytes = $null
    $credentialBase64 = $null
    $gitAuthArgs = $null
}
