# setup-hooks.ps1
# Installs the project's git hooks into the .git/hooks directory.

$ErrorActionPreference = "Stop"

$repoRoot = (git rev-parse --show-toplevel).Trim()
$hooksSource = Join-Path $repoRoot "tools/hooks"
$hooksDest = Join-Path $repoRoot ".git/hooks"

Write-Host "Installing Git hooks..." -ForegroundColor Cyan

if (-not (Test-Path $hooksDest)) {
    Write-Host "Error: .git directory not found. Are you in a git repository?" -ForegroundColor Red
    exit 1
}

# Copy pre-commit hook
$preCommitSrc = Join-Path $hooksSource "pre-commit"
$preCommitDest = Join-Path $hooksDest "pre-commit"

if (Test-Path $preCommitSrc) {
    Copy-Item -Path $preCommitSrc -Destination $preCommitDest -Force
    Write-Host "Done: Installed pre-commit hook." -ForegroundColor Green
} else {
    Write-Host "Warning: pre-commit hook template not found in $hooksSource" -ForegroundColor Yellow
}

Write-Host "`nGit hooks setup complete!" -ForegroundColor Cyan
