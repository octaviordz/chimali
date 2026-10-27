[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$DeviceSerial,
    [string]$RunnerRoot = (Join-Path $env:LOCALAPPDATA 'GitHubActionsRunner\chimali-coverage-v2.338.0')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$RunnerRoot = [System.IO.Path]::GetFullPath($RunnerRoot)

if (Test-Path -LiteralPath (Join-Path $RunnerRoot '.coverage-runner-disabled.json')) {
    throw "This runner is disabled by its owner. Startup is blocked until the owner explicitly authorizes re-enabling this host."
}

if (-not (Test-Path -LiteralPath (Join-Path $RunnerRoot '.runner') -PathType Leaf)) {
    throw "The GitHub runner is not registered in '$RunnerRoot'. Configure it using the repository-specific setup instructions first."
}
if (-not (Test-Path -LiteralPath (Join-Path $RunnerRoot 'run.cmd') -PathType Leaf)) {
    throw "The GitHub runner launcher is missing from '$RunnerRoot'."
}

# Keep Java and Android SDK configuration process-scoped and pass it to the runner and jobs.
. (Join-Path $PSScriptRoot 'setup-host.ps1') -DeviceSerial $DeviceSerial

Set-Location -LiteralPath $RunnerRoot
& .\run.cmd
exit $LASTEXITCODE
