# setup-hooks.ps1
# Configures the repository to use the tracked .gitconfig for Git hooks (Git 2.54+).

$ErrorActionPreference = "Stop"

Write-Host "Configuring Git hooks (Git 2.54+)..." -ForegroundColor Cyan

# Check Git version
$gitVersion = git --version
if ($gitVersion -match "git version (\d+\.\d+)") {
    $version = [double]$matches[1]
    if ($version -lt 2.54) {
        Write-Host "Warning: Git 2.54 or higher is recommended for config-based hooks." -ForegroundColor Yellow
        Write-Host "Current version: $gitVersion" -ForegroundColor Yellow
    }
}

# Set up the include path to point to the tracked .gitconfig
# Using ../.gitconfig because it's relative to the .git directory
git config --local include.path ../.gitconfig

Write-Host "Done: Repository configured to include .gitconfig for shared hooks." -ForegroundColor Green
Write-Host "`nGit hooks setup complete!" -ForegroundColor Cyan
