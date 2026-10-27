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
    [string]$ChangedPathsFile,

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

Write-Host "Evaluating coverage report for check-run creation on $HeadSha (Base: $BaseSha)..." -ForegroundColor Cyan

# 1. Read and validate manifest
$manifestPath = Join-Path $RunBundle "manifest.json"
if (-not (Test-Path $manifestPath)) {
    Write-Error "manifest.json not found in RunBundle: $RunBundle"
    exit 2
}

$manifest = Read-SafeJson -Path $manifestPath
$policyPath = Join-Path $ScriptDir "policy.json"
$verifyScript = Join-Path $ScriptDir "verify.ps1"

# Validate candidate/target bindings using the canonical nested schema.
$identity = Test-CandidateManifestIdentity -Manifest $manifest -ExpectedSourceCommit $HeadSha -ExpectedTargetCommit $BaseSha
if (-not $identity.IsValid) {
    Write-Error "RunBundle identity validation failed: $($identity.Errors -join '; ')"
    exit 1
}

# 2. Execute verification using trusted verify.ps1
$bundleParent = Split-Path -Parent ([System.IO.Path]::GetFullPath($RunBundle))
$assessmentsDir = Join-Path (Join-Path $bundleParent "assessments") $manifest.run_id
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
}
if ($ChangedPathsFile) { $verifyArgs += @("-ChangedPathsFile", $ChangedPathsFile) }

$verifyOutput = & pwsh -NoProfile -ExecutionPolicy Bypass -File $verifyScript @verifyArgs
$verifyCode = $LASTEXITCODE

$decisionJsonPath = Join-Path $assessmentsDir "decision.json"
$decision = if (Test-Path $decisionJsonPath) { Read-SafeJson -Path $decisionJsonPath } else { $null }

$checkConclusion = if ($verifyCode -eq 0 -and $decision -and $decision.overall_passed) {
    "success"
} else {
    "failure"
}

Write-Host "Derived check-run conclusion: $checkConclusion" -ForegroundColor $(if ($checkConclusion -eq "success") { "Green" } else { "Red" })

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
$reporterReceiptPath = Join-Path $assessmentsDir "reporter-receipt.json"

if ($DryRun -or [string]::IsNullOrWhiteSpace($AppId) -or [string]::IsNullOrWhiteSpace($AppPrivateKey) -or [string]::IsNullOrWhiteSpace($InstallationId)) {
    Write-Warning "Dedicated GitHub App credentials not provided or DryRun specified; check-run cannot be authorized via API."
    $unverifiedReceipt = [PSCustomObject]@{
        check_name = "coverage-integration"
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
$tokenUrl = "https://api.github.com/app/installations/$InstallationId/access_tokens"
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

# Create Check Run via GitHub API
$checkUrl = "https://api.github.com/repos/$Repository/check-runs"
$checkHeaders = @{
    "Authorization" = "token $installToken"
    "Accept" = "application/vnd.github+json"
    "User-Agent" = "Chimali-Coverage-Integration/1.0"
    "X-GitHub-Api-Version" = "2022-11-28"
}

$summaryText = if ($decision -and $decision.reasons) { ($decision.reasons -join "`n") } else { "Coverage evaluation completed with conclusion: $checkConclusion" }

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
    app_id = $AppId
    installation_id = $InstallationId
    repository = $Repository
    head_sha = $HeadSha
    base_sha = $BaseSha
    conclusion = $checkConclusion
    check_url = $createdCheck.html_url
    published_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    status = "Published"
}
$reporterReceipt | ConvertTo-Json -Depth 5 | Set-Content -Path $reporterReceiptPath -Encoding utf8
Write-Host "Check run created successfully: ID $($createdCheck.id), conclusion $checkConclusion" -ForegroundColor Green

# 4. Promotion lifecycle if requested and passing
if ($PromoteIntegration -and $checkConclusion -eq "success") {
    $publishScript = Join-Path $ScriptDir "publish.ps1"
    Write-Host "Integration succeeded. Promoting run bundle as accepted comparison target..." -ForegroundColor Cyan
    $publishArgs = @(
        "-RunBundle", $RunBundle,
        "-PromoteAsAcceptedTarget",
        "-IntegratedCommit", $(if ($IntegratedCommit) { $IntegratedCommit } else { $HeadSha }),
        "-IntegratedTree", $(if ($IntegratedTree) { $IntegratedTree } else { $manifest.git.source_tree })
    )
    $publishTokenVariable = "CHIMALI_COVERAGE_PUBLISH_TOKEN"
    $previousPublishToken = [Environment]::GetEnvironmentVariable($publishTokenVariable, "Process")
    try {
        [Environment]::SetEnvironmentVariable($publishTokenVariable, $installToken, "Process")
        & pwsh -NoProfile -ExecutionPolicy Bypass -File $publishScript @publishArgs
    } finally {
        [Environment]::SetEnvironmentVariable($publishTokenVariable, $previousPublishToken, "Process")
        $previousPublishToken = $null
        $publishTokenVariable = $null
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to promote accepted target baseline in publish.ps1"
        exit 1
    }
}

exit $(if ($checkConclusion -eq "success") { 0 } else { 1 })
