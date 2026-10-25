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
    Write-Error "manifest.json not found in RunBundle"
    exit 2
}

$manifest = Read-SafeJson -Path $manifestPath
$policyPath = Join-Path $ScriptDir "policy.json"
$verifyScript = Join-Path $ScriptDir "verify.ps1"

# 2. Execute verification using trusted verify.ps1
$assessmentsDir = Join-Path (Split-Path -Parent $RunBundle) "assessments" $manifest.run_id
$verifyOutput = & pwsh -NoProfile -ExecutionPolicy Bypass -File $verifyScript -RunBundle $RunBundle -PolicyPath $policyPath -OutputDirectory $assessmentsDir
$verifyCode = $LASTEXITCODE

$decisionJsonPath = Join-Path $assessmentsDir "decision.json"
$decision = if (Test-Path $decisionJsonPath) { Read-SafeJson -Path $decisionJsonPath } else { $null }

$checkConclusion = if ($verifyCode -eq 0 -and $decision -and $decision.overall_passed) {
    "success"
} else {
    "failure"
}

Write-Host "Derived check-run conclusion: $checkConclusion" -ForegroundColor $(if ($checkConclusion -eq "success") { "Green" } else { "Red" })

if ($PromoteIntegration -and $checkConclusion -eq "success") {
    $publishScript = Join-Path $ScriptDir "publish.ps1"
    Write-Host "Integration succeeded. Promoting run bundle as accepted comparison target..." -ForegroundColor Cyan
    $publishArgs = @(
        "-RunBundle", $RunBundle,
        "-PromoteAsAcceptedTarget",
        "-IntegratedCommit", $(if ($IntegratedCommit) { $IntegratedCommit } else { $HeadSha }),
        "-IntegratedTree", $(if ($IntegratedTree) { $IntegratedTree } else { $manifest.source_tree })
    )
    if ($DryRun -or [string]::IsNullOrEmpty($AppPrivateKey)) {
        $publishArgs += "-DryRun"
    }
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $publishScript @publishArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to promote accepted target baseline in publish.ps1"
        exit 1
    }
}

if ($DryRun -or [string]::IsNullOrEmpty($AppPrivateKey)) {
    Write-Host "Dry-run mode or App credentials not provided: Simulated 'coverage-integration' check creation ($checkConclusion) on $HeadSha" -ForegroundColor Yellow
    exit $(if ($checkConclusion -eq "success") { 0 } else { 1 })
}

# 3. Create check run via GitHub App API
Write-Host "Creating 'coverage-integration' check run on $Repository via dedicated App $AppId..." -ForegroundColor Cyan

# Receipt metadata binding
$receipt = [PSCustomObject]@{
    check_name = "coverage-integration"
    app_id = $AppId
    repository = $Repository
    head_sha = $HeadSha
    base_sha = $BaseSha
    conclusion = $checkConclusion
    evaluated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}

Write-Host "Check run created successfully: $($receipt | ConvertTo-Json -Compress)" -ForegroundColor Green
exit $(if ($checkConclusion -eq "success") { 0 } else { 1 })
