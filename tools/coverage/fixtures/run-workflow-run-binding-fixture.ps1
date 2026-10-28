[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixturePath = Join-Path $root 'tools/coverage/fixtures/workflow-run-binding.json'
$scriptPath = Join-Path $root 'tools/coverage/validate-workflow-run.ps1'
$fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
$testCase = @($fixture.cases | Where-Object id -eq $Case)
if ($testCase.Count -ne 1) { throw "Unknown or duplicate workflow-run fixture case: $Case" }
$caseData = $testCase[0]
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-workflow-run-fixture-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

try {
    $shaHead = 'a' * 40
    $shaBase = 'b' * 40
    $eventRun = [PSCustomObject]@{
        id = 12345; name = 'Coverage Capture'; workflow_id = 67890
        path = '.github/workflows/coverage.yml@main'; event = 'pull_request'; conclusion = 'success'
        run_attempt = 2; head_sha = $shaHead
        repository = [PSCustomObject]@{ full_name = 'octaviordz/chimali' }
        head_repository = [PSCustomObject]@{ full_name = 'octaviordz/chimali' }
        pull_requests = @([PSCustomObject]@{
            number = 7
            head = [PSCustomObject]@{ sha = $shaHead; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
            base = [PSCustomObject]@{ sha = $shaBase; repo = [PSCustomObject]@{ full_name = 'octaviordz/chimali' } }
        })
    }
    $event = [PSCustomObject]@{ repository = [PSCustomObject]@{ full_name = 'octaviordz/chimali' }; workflow_run = $eventRun }
    $authoritative = [PSCustomObject]@{
        id = 12345; run_attempt = 2; workflow_id = 67890; head_sha = $shaHead
        event = 'pull_request'; conclusion = 'success'; path = '.github/workflows/coverage.yml@main'
        repository = [PSCustomObject]@{ full_name = 'octaviordz/chimali' }
    }
    switch ($caseData.mutation) {
        'authoritative-head' { $authoritative.head_sha = 'c' * 40 }
        'workflow-id' { $eventRun.workflow_id = 67891 }
        'fork-head' { $eventRun.head_repository.full_name = 'untrusted/fork'; $eventRun.pull_requests[0].head.repo.full_name = 'untrusted/fork' }
        'workflow-ref' { $eventRun.path = '.github/workflows/coverage.yml@refs/pull/7/merge'; $authoritative.path = $eventRun.path }
        'short-base' { $eventRun.pull_requests[0].base.sha = 'deadbeef' }
        'merge-group' {
            $eventRun.event = 'merge_group'
            $eventRun.pull_requests = @()
            $authoritative.event = 'merge_group'
        }
        'none' { }
        default { throw "Unsupported fixture mutation '$($caseData.mutation)'" }
    }

    $eventPath = Join-Path $tempRoot 'event.json'
    $apiPath = Join-Path $tempRoot 'api-run.json'
    $outputPath = Join-Path $tempRoot 'identity.json'
    $event | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $eventPath -Encoding utf8
    $authoritative | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $apiPath -Encoding utf8
    $null = & pwsh -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
        -EventPath $eventPath -ExpectedRepository 'octaviordz/chimali' -ExpectedWorkflowId '67890' `
        -DefaultBranch 'main' -ServerUrl 'https://github.com' -AuthoritativeRunPath $apiPath -OutputPath $outputPath 2>&1
    $actualExit = $LASTEXITCODE
    $expectedExit = if ($caseData.expected_valid) { 0 } else { 1 }
    if ($actualExit -ne $expectedExit) { throw "$Case expected validator exit $expectedExit; got $actualExit" }
    if ($caseData.expected_valid) {
        if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) { throw "$Case did not write a validated identity record" }
        $identity = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
        if (-not $identity.valid -or $identity.head_sha -ne $shaHead) { throw "$Case output identity did not preserve the validated candidate SHA" }
    } elseif (Test-Path -LiteralPath $outputPath -PathType Leaf) {
        throw "$Case wrote a success identity despite rejection"
    }
    Write-Output "$Case passed (validator exit $actualExit)."
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    if ([System.IO.Directory]::Exists($resolvedTemp)) {
        if (-not $resolvedTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-workflow-run-fixture-*') {
            throw "Refusing unsafe workflow fixture cleanup target: $resolvedTemp"
        }
        [System.IO.Directory]::Delete($resolvedTemp, $true)
    }
}
