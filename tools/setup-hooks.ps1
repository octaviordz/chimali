# setup-hooks.ps1
# Configures the repository to use the tracked .gitconfig for Git hooks with PowerShell 7.

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

# 4. Optional local default target configuration
$currentTarget = git config --get coverage.targetRef
if ([string]::IsNullOrWhiteSpace($currentTarget)) {
    Write-Host "Tip: Set explicit target with: git config coverage.targetRef origin/main" -ForegroundColor Cyan
}

Write-Host "Done: Repository configured to include .gitconfig for shared hooks." -ForegroundColor Green
Write-Host "NOTE: Local hook bypass (e.g. git push --no-verify) does NOT grant remote merge approval; remote GitHub branch protection strictly enforces the dedicated App coverage check." -ForegroundColor Yellow
Write-Host "`nGit hooks setup complete!" -ForegroundColor Cyan
