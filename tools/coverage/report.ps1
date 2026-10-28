<#
.SYNOPSIS
    Trusted GitHub App reporter that creates the coverage-integration check run.
.DESCRIPTION
    Runs only within the protected default-branch workflow_run context.
    Validates capture run metadata, PR head/base bindings, and verifies evidence
    before authenticating via the dedicated Coverage GitHub App and creating/updating
    the required 'coverage-integration' check run.
.PARAMETER RunBundle
    Path to the downloaded untrusted coverage bundle.
.PARAMETER Repository
    GitHub repository in owner/repo format.
.PARAMETER HeadSha
    Exact PR head commit SHA to receive the check run.
.PARAMETER BaseSha
    Integration target base commit SHA.
.PARAMETER AppId
    Dedicated Coverage GitHub App ID.
.PARAMETER AppPrivateKey
    GitHub App private key (PEM format).
.PARAMETER InstallationId
    GitHub App installation ID.
.PARAMETER DryRun
    Simulates check-run creation without contacting GitHub API.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RunBundle,

    [Parameter(Mandatory = $true)]
    [string]$Repository,

    [Parameter(Mandatory = $true)]
    [string]$HeadSha,

    [Parameter(Mandatory = $true)]
    [string]$BaseSha,

    [Parameter(Mandatory = $false)]
    [string]$AppId,

    [Parameter(Mandatory = $false)]
    [string]$AppPrivateKey,

    [Parameter(Mandatory = $false)]
    [string]$InstallationId,

    [switch]$PromoteIntegration,

    [Parameter(Mandatory = $false)]
    [string]$IntegratedCommit,

    [Parameter(Mandatory = $false)]
    [string]$IntegratedTree,

    [Parameter(Mandatory = $false)]
    [string]$ComparisonBundle,

    [Parameter(Mandatory = $false)]
    [string]$AcceptedBaselineIndexPath,

    [Parameter(Mandatory = $false)]
    [string]$IntegrationEventPath,

    [Parameter(Mandatory = $false)]
    [string]$ChangedPathsFile,

    [Parameter(Mandatory = $false)]
    [ValidateSet('pull_request', 'merge_group')]
    [string]$LiveEventName,

    [Parameter(Mandatory = $false)]
    [int]$PullRequestNumber,

    [Parameter(Mandatory = $false)]
    [string]$IntegrationTargetRef,

    [Parameter(Mandatory = $false)]
    [string]$ActionsToken,

    [Parameter(Mandatory = $false)]
    [string]$ServerUrl = 'https://github.com',

    [Parameter(Mandatory = $false)]
    [string]$LiveEvidencePath,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedReporterWorkflowSha,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedReporterSha,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedRepositoryId,

    [Parameter(Mandatory = $false)]
    [string]$ExpectedCoverageAppId,

    [Parameter(Mandatory = $false)]
    [string]$ArtifactId,

    [Parameter(Mandatory = $false)]
    [string]$ArtifactDigest,

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"
$ApiRoot = if ($ServerUrl -eq 'https://github.com') { 'https://api.github.com' } else { "$($ServerUrl.TrimEnd('/'))/api/v3" }

if (-not (Test-Path $CommonPath)) {
    Write-Error "Common utilities not found at $CommonPath"
    exit 3
}

. $CommonPath

Write-Host "Evaluating coverage report for check-run creation on $HeadSha (Base: $BaseSha)..." -ForegroundColor Cyan

# 1. Read and validate manifest
$manifestPath = Join-Path $RunBundle "manifest.json"
if (-not (Test-Path $manifestPath)) {
    Write-Error "manifest.json not found in RunBundle: $RunBundle"
    exit 2
}

$manifest = Read-SafeJson -Path $manifestPath
$verifiedManifestDigest = Get-FileSha256 -Path $manifestPath
$verifiedBundleDigest = Get-CoverageBundleDigest -BundlePath $RunBundle
$policyPath = Join-Path $ScriptDir "policy.json"
$verifyScript = Join-Path $ScriptDir "verify.ps1"
$trustedHarnessRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '../..'))

# Validate candidate/target bindings using the canonical nested schema.
$identity = Test-CandidateManifestIdentity -Manifest $manifest -ExpectedSourceCommit $HeadSha -ExpectedTargetCommit $BaseSha
if (-not $identity.IsValid) {
    Write-Error "RunBundle identity validation failed: $($identity.Errors -join '; ')"
    exit 1
}
# 2. Execute verification using trusted verify.ps1
$bundleParent = Split-Path -Parent ([System.IO.Path]::GetFullPath($RunBundle))
$assessmentsDir = Join-Path (Join-Path $bundleParent "assessments") $manifest.run_id
$decisionJsonPath = Join-Path $assessmentsDir "decision.json"
if (Test-Path -LiteralPath $decisionJsonPath -PathType Leaf) {
    [Console]::Error.WriteLine("Refusing to reuse an existing assessment for run $($manifest.run_id): $decisionJsonPath")
    exit 3
}
if (-not (Test-Path $assessmentsDir)) {
    New-Item -ItemType Directory -Path $assessmentsDir -Force | Out-Null
}

$verifyArgs = @(
    "-RunBundle", $RunBundle,
    "-PolicyPath", $policyPath,
    "-OutputDirectory", $assessmentsDir
)
if ($manifest.purpose -eq "Comparison" -or $manifest.purpose -eq "Candidate") {
    if ($ComparisonBundle) { $verifyArgs += @("-ComparisonBundle", $ComparisonBundle) }
    if ($AcceptedBaselineIndexPath) { $verifyArgs += @("-AcceptedBaselineIndexPath", $AcceptedBaselineIndexPath) }
}
if ($ChangedPathsFile) { $verifyArgs += @("-ChangedPathsFile", $ChangedPathsFile) }

$verifyOutput = & pwsh -NoProfile -ExecutionPolicy Bypass -File $verifyScript @verifyArgs
$verifyCode = $LASTEXITCODE

$decision = if (Test-Path $decisionJsonPath) { Read-SafeJson -Path $decisionJsonPath } else { $null }

if ($PromoteIntegration -and $verifyCode -eq 0 -and $decision -and $decision.overall_passed -and
    ($PullRequestNumber -lt 1 -or -not $IntegrationEventPath -or -not (Test-Path -LiteralPath $IntegrationEventPath -PathType Leaf))) {
    Write-Error 'Promotion requires a pull request number and integration-event request context; the trusted publisher resolves merged commit/tree metadata from the repository API.'
    exit 3
}

$checkConclusion = if ($verifyCode -eq 0 -and $decision -and $decision.overall_passed) {
    "success"
} else {
    "failure"
}

Write-Host "Derived check-run conclusion: $checkConclusion" -ForegroundColor $(if ($checkConclusion -eq "success") { "Green" } else { "Red" })

function Test-LiveCandidateNow {
    param([Parameter(Mandatory = $true)][string]$Phase)
    if (-not $LiveEventName) { throw "Live candidate event metadata is required before $Phase" }
    $liveValidator = Join-Path $ScriptDir 'validate-live-candidate.ps1'
    if (-not (Test-Path -LiteralPath $liveValidator -PathType Leaf)) { throw 'Trusted live candidate validator is missing' }
    $liveArgs = @(
        '-Repository', $Repository,
        '-HeadSha', $HeadSha,
        '-BaseSha', $BaseSha,
        '-TreeSha', [string]$manifest.git.source_tree,
        '-EventName', $LiveEventName,
        '-ServerUrl', $ServerUrl
    )
    if ($PullRequestNumber -gt 0) { $liveArgs += @('-PullRequestNumber', [string]$PullRequestNumber) }
    if ($IntegrationTargetRef) { $liveArgs += @('-TargetRef', $IntegrationTargetRef) }
    if ($ActionsToken) { $liveArgs += @('-Token', $ActionsToken) }
    if ($LiveEvidencePath) { $liveArgs += @('-LiveEvidencePath', $LiveEvidencePath) }
    $liveOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $liveValidator @liveArgs 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Live candidate revalidation failed $Phase`: $($liveOutput -join ' ')" }
}

# Recheck after the independent assessment and immediately before minting an
# installation token; the earlier workflow preflight can become stale.
if (-not $DryRun -and -not [string]::IsNullOrWhiteSpace($AppId) -and
    -not [string]::IsNullOrWhiteSpace($AppPrivateKey) -and -not [string]::IsNullOrWhiteSpace($InstallationId)) {
    if ($AppId -cne $ExpectedCoverageAppId -or
        [string]::IsNullOrWhiteSpace($ExpectedReporterWorkflowSha) -or
        [string]::IsNullOrWhiteSpace($ExpectedReporterSha) -or
        [string]::IsNullOrWhiteSpace($ExpectedRepositoryId) -or
        $ArtifactId -notmatch '^[0-9]+$' -or $ArtifactDigest -notmatch '^sha256:[0-9a-fA-F]{64}$') {
        [Console]::Error.WriteLine('Protected reporter workflow/App identity configuration is missing or inconsistent.')
        exit 3
    }
    $reporterBinding = Test-TrustedReporterBinding -Manifest $manifest `
        -WorkflowCommit $ExpectedReporterWorkflowSha `
        -ReporterSha $ExpectedReporterSha `
        -RepositoryId $ExpectedRepositoryId `
        -AppId $ExpectedCoverageAppId
    if (-not $reporterBinding.IsValid) {
        [Console]::Error.WriteLine("Capture reporter binding rejected: $($reporterBinding.Errors -join '; ')")
        exit 1
    }
    try { Test-LiveCandidateNow -Phase 'before App authentication' }
    catch {
        $liveAdmission = Resolve-ReportLiveConclusion -CurrentConclusion $checkConclusion -LiveCandidateValidation @{
            IsValid = $false
            Errors = @([string]$_)
        }
        $checkConclusion = $liveAdmission.Conclusion
        if ($decision) {
            if (-not $decision.unmet_reasons) { $decision | Add-Member -NotePropertyName unmet_reasons -NotePropertyValue @() }
            $decision.unmet_reasons += $liveAdmission.Errors
        }
        Write-Warning "Live candidate metadata rejected before App authentication; publishing a failure check for the trusted capture identity: $($liveAdmission.Errors -join '; ')"
    }
}

# Helper to mint JWT for GitHub App
function New-GitHubAppJwtInternal {
    param(
        [string]$AppIdVal,
        [string]$PrivateKeyPemVal
    )
    $headerJson = '{"alg":"RS256","typ":"JWT"}'
    $headerBytes = [System.Text.Encoding]::UTF8.GetBytes($headerJson)
    $headerB64 = [Convert]::ToBase64String($headerBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payloadObj = @{
        iat = ($now - 60)
        exp = ($now + 600)
        iss = $AppIdVal
    }
    $payloadJson = $payloadObj | ConvertTo-Json -Compress
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
    $payloadB64 = [Convert]::ToBase64String($payloadBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    $rawToSign = "$($headerB64).$($payloadB64)"
    [byte[]]$dataToSign = [System.Text.Encoding]::UTF8.GetBytes($rawToSign)

    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $rsa.ImportFromPem($PrivateKeyPemVal)
    } catch {
        $cleanedPem = ($PrivateKeyPemVal -replace '-----BEGIN (RSA )?PRIVATE KEY-----', '' -replace '-----END (RSA )?PRIVATE KEY-----', '' -replace '\s+', '')
        $keyBytes = [Convert]::FromBase64String($cleanedPem)
        try {
            $rsa.ImportRSAPrivateKey($keyBytes, [ref]$null)
        } catch {
            $rsa.ImportPkcs8PrivateKey($keyBytes, [ref]$null)
        }
    }

    [byte[]]$sigBytes = $rsa.SignData($dataToSign, [System.Security.Cryptography.HashAlgorithmName]::SHA256, [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $sigB64 = [Convert]::ToBase64String($sigBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    return "$($rawToSign).$($sigB64)"
}

# 3. Dedicated GitHub App Check-run publication
$receiptNonce = [guid]::NewGuid().ToString('N')
$reporterReceiptPath = Join-Path $assessmentsDir "reporter-receipt-$receiptNonce.json"

if ($DryRun -or [string]::IsNullOrWhiteSpace($AppId) -or [string]::IsNullOrWhiteSpace($AppPrivateKey) -or [string]::IsNullOrWhiteSpace($InstallationId)) {
    Write-Warning "Dedicated GitHub App credentials not provided or DryRun specified; check-run cannot be authorized via API."
    $unverifiedReceipt = [PSCustomObject]@{
        check_name = "coverage-integration"
        run_id = [string]$manifest.run_id
        app_id = if ($AppId) { $AppId } else { "unconfigured" }
        repository = $Repository
        head_sha = $HeadSha
        base_sha = $BaseSha
        conclusion = "unverified"
        status = if ($DryRun) { "DryRun" } else { "MissingCredentials" }
        evaluated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        reason = "Dedicated Coverage GitHub App credentials missing or dry-run requested; integration check remains Unverified"
    }
    $unverifiedReceipt | ConvertTo-Json -Depth 5 | Set-Content -Path $reporterReceiptPath -Encoding utf8
    Write-Host "Integration check remains Unverified (no API authorization issued)" -ForegroundColor Yellow
    exit 1
}

# Mint installation access token
$jwt = New-GitHubAppJwtInternal -AppIdVal $AppId -PrivateKeyPemVal $AppPrivateKey
    $tokenUrl = "$ApiRoot/app/installations/$InstallationId/access_tokens"
$headers = @{
    "Authorization" = "Bearer $jwt"
    "Accept" = "application/vnd.github+json"
    "User-Agent" = "Chimali-Coverage-Integration/1.0"
    "X-GitHub-Api-Version" = "2022-11-28"
}

Write-Host "Requesting installation token from GitHub API for App $AppId..." -ForegroundColor Cyan
$installToken = $null
$maxRetries = 3
$attempt = 0
while ($attempt -lt $maxRetries) {
    $attempt++
    try {
        $tokenResp = Invoke-RestMethod -Uri $tokenUrl -Method Post -Headers $headers
        $installToken = $tokenResp.token
        break
    } catch {
        Write-Warning "Attempt $attempt to obtain installation token failed: $($_.Exception.Message)"
        if ($attempt -ge $maxRetries) {
            Write-Error "Failed to obtain GitHub App installation token after $maxRetries attempts"
            exit 1
        }
        Start-Sleep -Seconds 2
    }
}

# Archive the exact run bundle and read it back before creating an App success check.
$publishScript = Join-Path $ScriptDir "publish.ps1"
$publicationReceiptPath = Join-Path $assessmentsDir "publication-receipt-$receiptNonce.json"
$publishArgs = @("-RunBundle", $RunBundle, "-OutputReceiptPath", $publicationReceiptPath)
if ($PromoteIntegration -and $checkConclusion -eq "success") {
    $publishArgs += @(
        "-PromoteAsAcceptedTarget",
        "-AssessmentPath", $decisionJsonPath,
        "-IntegrationEventPath", $IntegrationEventPath,
        "-ExpectedRepository", $Repository,
        "-ExpectedHeadSha", $HeadSha,
        "-ExpectedBaseSha", $BaseSha,
        "-PullRequestNumber", [string]$PullRequestNumber
    )
    if ($ComparisonBundle) { $publishArgs += @('-ComparisonBundle', $ComparisonBundle) }
    if ($AcceptedBaselineIndexPath) { $publishArgs += @('-AcceptedBaselineIndexPath', $AcceptedBaselineIndexPath) }
    if ($ChangedPathsFile) { $publishArgs += @('-ChangedPathsFile', $ChangedPathsFile) }
}
$publishTokenVariable = "CHIMALI_COVERAGE_PUBLISH_TOKEN"
$previousPublishToken = [Environment]::GetEnvironmentVariable($publishTokenVariable, "Process")
try {
    [Environment]::SetEnvironmentVariable($publishTokenVariable, $installToken, "Process")
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $publishScript @publishArgs
    $publishCode = $LASTEXITCODE
} finally {
    [Environment]::SetEnvironmentVariable($publishTokenVariable, $previousPublishToken, "Process")
}
$publicationReceipt = if (Test-Path -LiteralPath $publicationReceiptPath) { Read-SafeJson -Path $publicationReceiptPath } else { $null }
if ($checkConclusion -eq "success") {
    $publicationEligible = $false
    if ($publishCode -eq 0 -and $publicationReceipt) {
        $expectedBundleDigest = Get-CoverageBundleDigest -BundlePath $RunBundle
        $currentManifestDigest = Get-FileSha256 -Path $manifestPath
        $publicationGate = Test-ReportSuccessEligibility -Decision $decision -Receipt $publicationReceipt -ExpectedRunId ([string]$manifest.run_id) -ExpectedManifestDigest $currentManifestDigest -ExpectedBundleDigest $expectedBundleDigest
        $publicationEligible = $publicationGate.IsEligible
        if (-not $publicationEligible) { $decision.unmet_reasons += $publicationGate.Errors }
    }
    if (-not $publicationEligible) {
        $checkConclusion = "failure"
        if (-not $publicationReceipt) { Write-Warning "No retrievable publication receipt was produced; coverage success is blocked." }
        elseif ($publishCode -ne 0) { Write-Warning "Coverage bundle publication failed with exit code $publishCode; coverage success is blocked." }
    }
}

# Create Check Run via GitHub API
if ($checkConclusion -eq 'success') {
    try {
        $currentManifestDigest = Get-FileSha256 -Path $manifestPath
        $currentBundleDigest = Get-CoverageBundleDigest -BundlePath $RunBundle
        $currentManifest = Read-SafeJson -Path $manifestPath
        $currentIdentity = Test-CandidateManifestIdentity -Manifest $currentManifest -ExpectedSourceCommit $HeadSha -ExpectedTargetCommit $BaseSha
        $currentReporterBinding = Test-TrustedReporterBinding -Manifest $currentManifest `
            -WorkflowCommit $ExpectedReporterWorkflowSha `
            -ReporterSha $ExpectedReporterSha `
            -RepositoryId $ExpectedRepositoryId `
            -AppId $ExpectedCoverageAppId
        $currentManifestValidation = Test-ManifestSchema -Manifest $currentManifest -Policy (Read-SafeJson -Path $policyPath) -TrustedHarnessRoot $trustedHarnessRoot
        $finalBinding = Test-ReportFinalBinding `
            -VerifiedManifestDigest $verifiedManifestDigest `
            -CurrentManifestDigest $currentManifestDigest `
            -VerifiedBundleDigest $verifiedBundleDigest `
            -CurrentBundleDigest $currentBundleDigest `
            -CandidateIdentity $currentIdentity `
            -ReporterBinding $currentReporterBinding `
            -ManifestValidation $currentManifestValidation `
            -PublicationReceipt $publicationReceipt
        if (-not $finalBinding.IsValid) {
            throw "Final trusted reporter binding rejected: $($finalBinding.Errors -join '; ')"
        }

        Test-LiveCandidateNow -Phase 'before success check creation'
    }
    catch {
        $checkConclusion = 'failure'
        if ($decision) { $decision.unmet_reasons += [string]$_ }
        Write-Warning "Coverage success is blocked because a live run, reporter, harness, or candidate binding changed: $_"
    }
}
$checkUrl = "$ApiRoot/repos/$Repository/check-runs"
$checkHeaders = @{
    "Authorization" = "token $installToken"
    "Accept" = "application/vnd.github+json"
    "User-Agent" = "Chimali-Coverage-Integration/1.0"
    "X-GitHub-Api-Version" = "2022-11-28"
}

$summaryReasons = @()
if ($decision) {
    $summaryReasons = @((@($decision.reasons) + @($decision.unmet_reasons)) | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Select-Object -Unique)
}
$summaryText = if ($summaryReasons.Count -gt 0) { $summaryReasons -join "`n" } else { "Coverage evaluation completed with conclusion: $checkConclusion" }

$checkPayload = @{
    name = "coverage-integration"
    head_sha = $HeadSha
    status = "completed"
    conclusion = $checkConclusion
    completed_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    output = @{
        title = "Coverage Integration Gate: $checkConclusion"
        summary = $summaryText
        text = "Run ID: $($manifest.run_id)`nTarget Base: $BaseSha`nHead SHA: $HeadSha"
    }
} | ConvertTo-Json -Depth 5

Write-Host "Creating/updating 'coverage-integration' check run on $Repository ($HeadSha) with conclusion '$checkConclusion'..." -ForegroundColor Cyan

$createdCheck = $null
$attempt = 0
while ($attempt -lt $maxRetries) {
    $attempt++
    try {
        $createdCheck = Invoke-RestMethod -Uri $checkUrl -Method Post -Headers $checkHeaders -Body $checkPayload -ContentType "application/json"
        break
    } catch {
        Write-Warning "Attempt $attempt to create check-run failed: $($_.Exception.Message)"
        if ($attempt -ge $maxRetries) {
            Write-Error "Failed to create check run via GitHub API after $maxRetries attempts"
            exit 1
        }
        Start-Sleep -Seconds 2
    }
}

$reporterReceipt = [PSCustomObject]@{
    check_name = "coverage-integration"
    check_id = $createdCheck.id
    run_id = $manifest.run_id
    app_id = $AppId
    installation_id = $InstallationId
    artifact_id = $ArtifactId
    artifact_digest = $ArtifactDigest
    repository = $Repository
    head_sha = $HeadSha
    base_sha = $BaseSha
    conclusion = $checkConclusion
    check_url = $createdCheck.html_url
    publication_status = if ($publicationReceipt) { $publicationReceipt.status } else { "Missing" }
    publication_bundle_digest = if ($publicationReceipt) { $publicationReceipt.bundle_digest } else { $null }
    publication_archive_ref = if ($publicationReceipt) { $publicationReceipt.archive_ref } else { $null }
    publication_archive_commit = if ($publicationReceipt) { $publicationReceipt.archive_commit } else { $null }
    published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    status = "Published"
}
$reporterReceipt | ConvertTo-Json -Depth 5 | Set-Content -Path $reporterReceiptPath -Encoding utf8
Write-Host "Check run created successfully: ID $($createdCheck.id), conclusion $checkConclusion" -ForegroundColor Green

exit $(if ($checkConclusion -eq "success") { 0 } else { 1 })
