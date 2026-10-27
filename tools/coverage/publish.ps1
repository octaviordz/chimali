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

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-Error "Common utilities not found at $CommonPath"
    exit 3
}

. $CommonPath

if (-not (Test-Path $RunBundle)) {
    Write-Error "RunBundle directory not found: $RunBundle"
    exit 2
}

$manifestPath = Join-Path $RunBundle "manifest.json"
if (-not (Test-Path $manifestPath)) {
    Write-Error "manifest.json not found in RunBundle: $manifestPath"
    exit 2
}

$manifest = Read-SafeJson -Path $manifestPath
$runId = $manifest.run_id
$testedCommit = [string]$manifest.git.source_commit
$testedTree = [string]$manifest.git.source_tree

if (-not $runId) {
    Write-Error "manifest.json is missing run_id"
    exit 2
}

# 1. Output Receipt Path Validation (Must be outside sealed bundle)
$bundleParent = Split-Path -Parent $RunBundle
$receiptTarget = if ($OutputReceiptPath) {
    if (-not [System.IO.Path]::IsPathRooted($OutputReceiptPath)) {
        Write-Error "OutputReceiptPath must be an absolute path: $OutputReceiptPath"
        exit 3
    }
    $OutputReceiptPath
} else {
    Join-Path (Join-Path $bundleParent "assessments") (Join-Path $runId "publication-receipt.json")
}

$canonicalBundle = [System.IO.Path]::GetFullPath($RunBundle).TrimEnd('\', '/')
$canonicalReceipt = [System.IO.Path]::GetFullPath($receiptTarget)
if ($canonicalReceipt.StartsWith($canonicalBundle + [System.IO.Path]::DirectorySeparatorChar) -or $canonicalReceipt -eq $canonicalBundle) {
    Write-Error "Publication receipt cannot be written inside sealed capture bundle: $RunBundle"
    exit 3
}

$receiptDir = Split-Path -Parent $receiptTarget
if (-not (Test-Path $receiptDir)) {
    New-Item -ItemType Directory -Path $receiptDir -Force | Out-Null
}

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
                    Write-Error "Artifact SHA256 digest mismatch: expected $($art.sha256), actual $($payloadHashes[$normPath])"
                    exit 1
                }
            } else {
                Write-Error "Artifact declared in manifest missing from bundle payload: $($art.path)"
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
    Write-Error "Current directory is not inside a git repository"
    exit 2
}

# Check if remote branch exists and can be fetched
$publishToken = $env:CHIMALI_COVERAGE_PUBLISH_TOKEN
if ([string]::IsNullOrWhiteSpace($publishToken)) {
    Write-Error "CHIMALI_COVERAGE_PUBLISH_TOKEN is required for trusted archive publication."
    exit 2
}

$remoteUrl = (& git remote get-url $RemoteName 2>$null | Select-Object -First 1)
try {
    $remoteUri = [Uri]$remoteUrl
} catch {
    Write-Error "Remote '$RemoteName' must use an HTTPS GitHub URL for trusted archive publication."
    exit 2
}
if ($remoteUri.Scheme -ne 'https' -or $remoteUri.Host -ne 'github.com') {
    Write-Error "Remote '$RemoteName' must use an HTTPS GitHub URL for trusted archive publication."
    exit 2
}

$credentialBytes = [System.Text.Encoding]::UTF8.GetBytes("x-access-token:$publishToken")
$credentialBase64 = [Convert]::ToBase64String($credentialBytes)
$gitAuthArgs = @('-c', "http.https://github.com/.extraheader=AUTHORIZATION: basic $credentialBase64")
$remoteCheck = & git @gitAuthArgs ls-remote --heads $RemoteName $EvidenceBranch 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to access remote $RemoteName branch ${EvidenceBranch}: credentials absent or remote unreachable"
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

    # Atomic Conflict Detection: Check if runs/<runId> already exists
    $targetRunDir = Join-Path (Join-Path $tempWorktree "runs") $runId
    if (Test-Path $targetRunDir) {
        Write-Error "Cannot overwrite immutable run record: runs/$runId already exists in $EvidenceBranch"
        exit 1
    }

    # Copy bundle files into runs/<runId>/
    New-Item -ItemType Directory -Path $targetRunDir -Force | Out-Null
    Copy-Item -Path (Join-Path $RunBundle "*") -Destination $targetRunDir -Recurse -Force

    # If promoting as accepted target, verify tree and update index
    if ($PromoteAsAcceptedTarget) {
        if ([string]::IsNullOrWhiteSpace($testedCommit) -or [string]::IsNullOrWhiteSpace($testedTree)) {
            Write-Error "manifest.git.source_commit and manifest.git.source_tree are required for promotion"
            exit 1
        }
        if (-not $IntegratedTree) {
            Write-Error "IntegratedTree is required when promoting as accepted target"
            exit 1
        }
        if ($testedTree -ne $IntegratedTree) {
            Write-Error "Integrated tree ($IntegratedTree) does not match tested candidate tree ($testedTree); fresh complete coverage capture required"
            exit 1
        }

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
        Write-Error "Failed to commit archive evidence in temporary worktree"
        exit 1
    }
    $archiveCommit = (git rev-parse HEAD).Trim()

    # Push to remote with atomic check and no-verify (evidence branch contains no product build scripts)
    & git @gitAuthArgs push --no-verify $RemoteName "${EvidenceBranch}:${EvidenceBranch}" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Concurrent publication conflict or push failure to $EvidenceBranch"
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
        exit 1
    }

    # Read-back verification must inspect the remote ref, not only the local worktree.
    $remoteRef = "refs/heads/$EvidenceBranch"
    $publishedRef = & git @gitAuthArgs ls-remote --heads $RemoteName $remoteRef 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Archive push succeeded, but remote read-back failed for $remoteRef"
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
        exit 1
    }
    $verifyRef = Get-RemoteRefCommit -Output @($publishedRef) -ExpectedRef $remoteRef
    if ([string]::IsNullOrWhiteSpace($verifyRef) -or $verifyRef -ne $archiveCommit) {
        Write-Error "Remote read-back verification failed: expected $archiveCommit, got '$verifyRef'"
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
        bundle_digest = $bundleDigest
        status = "Published"
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
