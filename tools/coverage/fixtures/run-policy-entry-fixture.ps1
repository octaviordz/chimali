[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)]
    [ValidateSet('valid', 'weakened-tier2', 'unknown-schema', 'external-policy-path', 'capture-weakened-tier2', 'verify-weakened-tier2')]
    [string]$Case
)

$ErrorActionPreference = 'Stop'
$repository = [System.IO.Path]::GetFullPath($RepositoryRoot)
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-policy-entry-fixture-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$coverageDir = Join-Path $tempRoot 'tools/coverage'
New-Item -ItemType Directory -Path $coverageDir -Force | Out-Null

try {
    foreach ($file in @('coverage-common.ps1', 'policy.json', 'validate-policy.ps1', 'capture.ps1', 'verify.ps1')) {
        Copy-Item -LiteralPath (Join-Path $repository "tools/coverage/$file") -Destination $coverageDir
    }
    $policyPath = Join-Path $coverageDir 'policy.json'
    $validatorPath = Join-Path $coverageDir 'validate-policy.ps1'
    $expectedText = $null
    if ($Case -in @('weakened-tier2', 'capture-weakened-tier2', 'verify-weakened-tier2')) {
        $policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
        $policy.tier2_instruction_min_percent = 0
        $policy | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $policyPath -Encoding utf8
        $expectedText = 'Policy tier2_instruction_min_percent must be the reviewed integer value 20'
    } elseif ($Case -eq 'unknown-schema') {
        $policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
        $policy.schema_version = '99.0'
        $policy | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $policyPath -Encoding utf8
        $expectedText = 'Unsupported or missing policy schema_version'
    } elseif ($Case -eq 'external-policy-path') {
        $policyPath = Join-Path $repository 'tools/coverage/policy.json'
        $expectedText = 'PolicyPath must resolve to the reviewed harness policy'
    }

    if ($Case -eq 'capture-weakened-tier2') {
        $capturePath = Join-Path $coverageDir 'capture.ps1'
        $sourceRevision = (& git -C $repository rev-parse HEAD).Trim()
        $output = @(& pwsh -NoLogo -NoProfile -File $capturePath -RepositoryRoot $tempRoot -SourceRevision $sourceRevision -Purpose Initial -HarnessRoot $tempRoot -DeviceSerial 'fixture-device' -OutputRoot (Join-Path $tempRoot 'evidence') 2>&1)
    } elseif ($Case -eq 'verify-weakened-tier2') {
        $verifyPath = Join-Path $coverageDir 'verify.ps1'
        $runBundle = Join-Path $tempRoot 'bundle'
        $assessment = Join-Path $tempRoot 'assessment'
        New-Item -ItemType Directory -Path $runBundle -Force | Out-Null
        New-Item -ItemType Directory -Path $assessment -Force | Out-Null
        $output = @(& pwsh -NoLogo -NoProfile -File $verifyPath -RunBundle $runBundle -PolicyPath $policyPath -OutputDirectory $assessment 2>&1)
    } else {
        $output = @(& pwsh -NoLogo -NoProfile -File $validatorPath -PolicyPath $policyPath 2>&1)
    }
    $exitCode = $LASTEXITCODE
    $expectedExit = if ($Case -eq 'valid') { 0 } else { 3 }
    if ($exitCode -ne $expectedExit) {
        throw "Policy entry point case '$Case' expected exit $expectedExit, received $exitCode. Output: $($output -join ' ')"
    }
    if ($expectedText -and -not (($output | Out-String).Contains($expectedText))) {
        throw "Policy entry point case '$Case' did not report '$expectedText'. Output: $($output -join ' ')"
    }
    Write-Output "Policy entry point '$Case' returned the expected exit $expectedExit."
} finally {
    if ([System.IO.Directory]::Exists($tempRoot)) {
        $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
        if (-not $resolvedTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-policy-entry-fixture-*') {
            throw "Refusing unsafe fixture cleanup target: $resolvedTemp"
        }
        [System.IO.Directory]::Delete($resolvedTemp, $true)
    }
}
