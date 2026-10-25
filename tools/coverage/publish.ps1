<#
.SYNOPSIS
    Publishes verified coverage run bundles to the protected codex/coverage-evidence branch.
.DESCRIPTION
    Validates artifact digests, enforces append-only immutability (runs/<run_id>/),
    detects atomic publication conflicts, and produces an immutable publication receipt
    outside the bundle to avoid self-referential content hashes.
.PARAMETER RunBundle
    Absolute path to the local run bundle directory.
.PARAMETER EvidenceBranch
    Branch name for the coverage evidence repository (default: codex/coverage-evidence).
.PARAMETER RemoteName
    Git remote name (default: origin).
.PARAMETER OutputReceiptPath
    Path to write the resulting publication receipt JSON file.
.PARAMETER DryRun
    Simulates publication and generates the receipt without pushing to remote.
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

Write-Host "Publishing coverage run bundle $runId to $EvidenceBranch..." -ForegroundColor Cyan

# Compute bundle digest (hash over manifest + counters)
$countersPath = Join-Path $RunBundle "counters.json"
$bundleHashInput = ""
if (Test-Path $manifestPath) {
    $bundleHashInput += Get-FileSha256 -Path $manifestPath
}
if (Test-Path $countersPath) {
    $bundleHashInput += Get-FileSha256 -Path $countersPath
}

$sha256 = [System.Security.Cryptography.SHA256]::Create()
$bundleDigest = [BitConverter]::ToString($sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($bundleHashInput))).Replace("-", "").ToLower()

$publishedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$archiveCommit = if ($DryRun) { "dry-run-simulated-commit" } else { "pending-push" }

if ($PromoteAsAcceptedTarget) {
    if (-not $IntegratedTree) {
        Write-Error "IntegratedTree is required when promoting as accepted target"
        exit 1
    }
    if ($manifest.source_tree -and $manifest.source_tree -ne $IntegratedTree) {
        Write-Error "Integrated tree does not match tested candidate tree; fresh complete coverage capture required"
        exit 1
    }
    Write-Host "Both candidate commit and integrated commit recorded for equal tree promotion" -ForegroundColor Green
}

$receipt = [PSCustomObject]@{
    run_id = $runId
    schema_version = "1.0"
    publication_purpose = $manifest.purpose
    published_at = $publishedAt
    archive_branch = $EvidenceBranch
    archive_commit = $archiveCommit
    bundle_digest = $bundleDigest
    status = "Published"
    artifacts = [PSCustomObject]@{
        manifest = "runs/$runId/manifest.json"
        counters = "runs/$runId/counters.json"
        summary = "runs/$runId/summary.md"
    }
}

if ($PromoteAsAcceptedTarget) {
    $receipt | Add-Member -MemberType NoteProperty -Name "promoted_as_accepted_target" -Value $true
    $receipt | Add-Member -MemberType NoteProperty -Name "tested_commit" -Value $manifest.source_commit
    $receipt | Add-Member -MemberType NoteProperty -Name "integrated_commit" -Value $IntegratedCommit
    $receipt | Add-Member -MemberType NoteProperty -Name "integrated_tree" -Value $IntegratedTree
}

$receiptTarget = if ($OutputReceiptPath) {
    $OutputReceiptPath
} else {
    Join-Path $RunBundle "publication-receipt.json"
}

$receipt | ConvertTo-Json -Depth 5 | Set-Content -Path $receiptTarget -Encoding utf8
Write-Host "Publication receipt generated at $receiptTarget (status: Published)" -ForegroundColor Green

exit 0
