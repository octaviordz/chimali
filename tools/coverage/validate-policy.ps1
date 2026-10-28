[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PolicyPath
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$commonPath = Join-Path $scriptDir 'coverage-common.ps1'
$canonicalPolicyPath = [System.IO.Path]::GetFullPath((Join-Path $scriptDir 'policy.json'))

if (-not [System.IO.Path]::IsPathRooted($PolicyPath) -or
    -not [string]::Equals([System.IO.Path]::GetFullPath($PolicyPath), $canonicalPolicyPath, [System.StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine("PolicyPath must resolve to the reviewed harness policy: $canonicalPolicyPath")
    exit 3
}
if (-not (Test-Path -LiteralPath $commonPath -PathType Leaf) -or -not (Test-Path -LiteralPath $canonicalPolicyPath -PathType Leaf)) {
    [Console]::Error.WriteLine('Reviewed policy validator or policy file is missing.')
    exit 3
}

. $commonPath
try {
    $policy = Read-SafeJson -Path $canonicalPolicyPath
    $validation = Test-PolicyConfiguration -Policy $policy
    if (-not $validation.IsValid) {
        [Console]::Error.WriteLine("Invalid coverage policy: $($validation.Reasons -join '; ')")
        exit 3
    }
} catch {
    [Console]::Error.WriteLine("Failed to validate authoritative coverage policy: $_")
    exit 3
}

exit 0
