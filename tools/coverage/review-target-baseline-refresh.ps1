[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository,
    [Parameter(Mandatory = $true)][string]$RunBundle,
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$TrustedHarnessRoot,
    [Parameter(Mandatory = $true)][string]$AcceptedBaselineIndexPath,
    [Parameter(Mandatory = $true)][ValidateSet('ACCEPT','REJECT')][string]$Decision,
    [Parameter(Mandatory = $true)][string]$Reason,
    [Parameter(Mandatory = $false)][string]$AgentIdentity,
    [Parameter(Mandatory = $false)][string]$AuthorizedUser
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'coverage-common.ps1')

function Invoke-GhJson {
    param([Parameter(Mandatory = $true)][string]$Endpoint)
    $response = @(& gh api $Endpoint 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Authenticated GitHub API request failed for '$Endpoint': $($response -join ' ')" }
    return (($response -join [Environment]::NewLine) | ConvertFrom-Json -Depth 50)
}

try {
    foreach ($entry in @(
        @{ Name = 'RunBundle'; Value = $RunBundle }, @{ Name = 'EvidenceRoot'; Value = $EvidenceRoot },
        @{ Name = 'TrustedHarnessRoot'; Value = $TrustedHarnessRoot }, @{ Name = 'AcceptedBaselineIndexPath'; Value = $AcceptedBaselineIndexPath }
    )) {
        if (-not [System.IO.Path]::IsPathRooted($entry.Value)) { throw "$($entry.Name) must be an absolute path." }
    }
    $RunBundle = [System.IO.Path]::GetFullPath($RunBundle)
    $EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
    $TrustedHarnessRoot = [System.IO.Path]::GetFullPath($TrustedHarnessRoot)
    $AcceptedBaselineIndexPath = [System.IO.Path]::GetFullPath($AcceptedBaselineIndexPath)
    if (-not (Test-Path -LiteralPath $EvidenceRoot -PathType Container)) { throw 'EvidenceRoot must already exist.' }
    if (-not (Test-Path -LiteralPath (Join-Path $RunBundle 'manifest.json') -PathType Leaf)) { throw 'RunBundle is missing manifest.json.' }
    if (-not (Test-Path -LiteralPath $AcceptedBaselineIndexPath -PathType Leaf)) { throw 'The accepted baseline index is missing.' }
    $expectedIndexPath = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $EvidenceRoot 'baselines') 'current.json'))
    if ($AcceptedBaselineIndexPath -cne $expectedIndexPath) { throw 'AcceptedBaselineIndexPath must be EvidenceRoot/baselines/current.json.' }

    $harnessCommit = (& git -C $TrustedHarnessRoot rev-parse HEAD 2>$null).Trim()
    $harnessState = (& git -C $TrustedHarnessRoot status --porcelain --untracked-files=all 2>$null) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $harnessCommit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or $harnessState) {
        throw 'TrustedHarnessRoot must be a clean, pinned Git checkout.'
    }
    $trustedVerifier = Join-Path $TrustedHarnessRoot 'tools/coverage/verify.ps1'
    $trustedPolicy = Join-Path $TrustedHarnessRoot 'tools/coverage/policy.json'
    if (-not (Test-Path -LiteralPath $trustedVerifier -PathType Leaf) -or -not (Test-Path -LiteralPath $trustedPolicy -PathType Leaf)) {
        throw 'Trusted harness is missing the coverage verifier or policy.'
    }

    $auth = @(& gh auth status 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI must be authenticated to review an accepted target refresh.' }
    $authenticatedUser = [string](Invoke-GhJson 'user').login
    $repositoryInfo = Invoke-GhJson "repos/$Repository"
    $permission = [string](Invoke-GhJson "repos/$Repository/collaborators/$authenticatedUser/permission").permission
    if ($permission -notin @('write','maintain','admin')) { throw "Authenticated user '$authenticatedUser' cannot review this repository." }
    if ($Decision -eq 'ACCEPT' -and ($authenticatedUser -cne [string]$repositoryInfo.owner.login -or $permission -cne 'admin')) {
        throw 'Accepting a same-tree baseline refresh requires the authenticated repository owner with admin access.'
    }
    if ($AgentIdentity -and (-not $AuthorizedUser -or $AuthorizedUser -cne $authenticatedUser)) {
        throw 'An agent may act only under the authenticated user explicit authority.'
    }
    if ($AuthorizedUser -and $AuthorizedUser -cne $authenticatedUser) { throw 'AuthorizedUser must match the authenticated GitHub user.' }
    if ($Decision -eq 'ACCEPT' -and [string]::IsNullOrWhiteSpace($Reason)) { throw 'An explicit reason is required for accepting a baseline refresh.' }

    $main = Invoke-GhJson "repos/$Repository/branches/main"
    $targetCommit = [string]$main.commit.sha
    $targetTree = [string](Invoke-GhJson "repos/$Repository/git/commits/$targetCommit").tree.sha
    if ($targetCommit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or $targetTree -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') {
        throw 'GitHub did not return full current main commit and tree identities.'
    }

    $manifest = Read-SafeJson -Path (Join-Path $RunBundle 'manifest.json')
    $bundleDigest = Get-CoverageBundleDigest -BundlePath $RunBundle
    $manifestDigest = Get-FileSha256 -Path (Join-Path $RunBundle 'manifest.json')
    if ([string]$manifest.git.source_commit -cne $targetCommit -or [string]$manifest.git.target_commit -cne $targetCommit -or
        [string]$manifest.git.source_tree -cne $targetTree -or [string]$manifest.harness.revision -cne $harnessCommit) {
        throw 'RunBundle must measure the exact current main commit/tree with this independent trusted harness.'
    }
    $index = Read-SafeJson -Path $AcceptedBaselineIndexPath
    $oldIndexDigest = Get-FileSha256 -Path $AcceptedBaselineIndexPath
    if ([string]$index.target_commit -cne $targetCommit -or [string]$index.target_tree -cne $targetTree) {
        throw 'Current accepted baseline does not identify the unchanged live main commit and tree.'
    }
    $oldBundlePath = [System.IO.Path]::GetFullPath((Join-Path $EvidenceRoot ([string]$index.bundle_path)))
    $evidencePrefix = $EvidenceRoot.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $oldBundlePath.StartsWith($evidencePrefix,[System.StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $oldBundlePath -PathType Container) -or
        (Get-CoverageBundleDigest -BundlePath $oldBundlePath) -cne [string]$index.bundle_digest) {
        throw 'The currently accepted immutable bundle is missing or does not match its index digest.'
    }
    $acceptedIndexCheck = Test-AcceptedBaselineIndex -Index $index -ExpectedTargetCommit $targetCommit -ActualBundleDigest (Get-CoverageBundleDigest -BundlePath $oldBundlePath)
    if (-not $acceptedIndexCheck.IsValid -or [string]$index.target_tree -cne $targetTree -or [string]$index.tested_tree -cne $targetTree) {
        throw "Current accepted index is not a valid baseline for the unchanged main tree: $($acceptedIndexCheck.Errors -join '; ')"
    }
    $oldManifestPath = Join-Path $oldBundlePath 'manifest.json'
    if (-not (Test-Path -LiteralPath $oldManifestPath -PathType Leaf)) { throw 'Current accepted immutable baseline has no manifest.' }
    $oldManifest = Read-SafeJson -Path $oldManifestPath
    if ([string]$oldManifest.run_id -cne [string]$index.run_id -or [string]$oldManifest.git.source_tree -cne $targetTree) {
        throw 'Current accepted baseline manifest does not bind its indexed run and unchanged main tree.'
    }

    $verificationDirectory = Join-Path (Join-Path $EvidenceRoot 'assessments') (Join-Path $manifest.run_id 'target-refresh-verification')
    if (Test-Path -LiteralPath $verificationDirectory) { throw 'Target refresh verification output is immutable and already exists.' }
    New-Item -ItemType Directory -Path $verificationDirectory -Force | Out-Null
    $verifyOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $trustedVerifier -RunBundle $RunBundle -PolicyPath $trustedPolicy `
        -OutputDirectory $verificationDirectory -TargetBaselineRefresh 2>&1)
    $verifyExit = $LASTEXITCODE
    $decisionPath = Join-Path $verificationDirectory 'decision.json'
    if ($verifyExit -ne 0 -or -not (Test-Path -LiteralPath $decisionPath -PathType Leaf)) {
        throw "Trusted target refresh verification failed: $($verifyOutput -join ' ')"
    }
    $verified = Read-SafeJson -Path $decisionPath
    if ($verified.baseline_refresh_ready -ne $true -or $verified.decision_type -cne 'TargetBaselineRefresh' -or
        $verified.run_id -cne $manifest.run_id -or $verified.manifest_digest -cne $manifestDigest) {
        throw 'Trusted verifier did not produce eligible evidence for this exact target run.'
    }

    $record = [PSCustomObject]@{
        schema_version = '1.0'; review_type = 'AcceptedTargetRefresh'; decision = $Decision
        decision_id = [guid]::NewGuid().ToString(); reviewed_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        repository = $Repository
        actor = [PSCustomObject]@{ authenticated_user = $authenticatedUser; permission = $permission; agent_identity = $AgentIdentity; authorized_user = $AuthorizedUser }
        authenticated_user = $authenticatedUser; permission = $permission
        run_id = [string]$manifest.run_id; manifest_digest = $manifestDigest; bundle_digest = $bundleDigest
        target_commit = $targetCommit; target_tree = $targetTree; measurement_fingerprint = [string]$manifest.measurement_fingerprint
        prior_index_digest = $oldIndexDigest; prior_run_id = [string]$index.run_id; reason = $Reason
        verifier_decision_digest = Get-FileSha256 -Path $decisionPath; verifier_decision_path = [System.IO.Path]::GetRelativePath($EvidenceRoot,$decisionPath).Replace('\','/')
        hosted_check = 'Unverified'
    }
    $reviewDirectory = Join-Path (Join-Path (Join-Path $EvidenceRoot 'reviews') 'baseline-refresh') $manifest.run_id
    New-Item -ItemType Directory -Path $reviewDirectory -Force | Out-Null
    $recordPath = Join-Path $reviewDirectory "$($record.decision_id).json"
    if (Test-Path -LiteralPath $recordPath) { throw 'Target refresh review record already exists.' }
    $record | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $recordPath -Encoding utf8
    Write-Output "Target refresh review record: $recordPath"
    Write-Output "Verified profile: $($manifest.measurement_fingerprint); accepted index digest: $oldIndexDigest"
} catch {
    [Console]::Error.WriteLine("Accepted target refresh review failed: $_")
    exit 1
}
