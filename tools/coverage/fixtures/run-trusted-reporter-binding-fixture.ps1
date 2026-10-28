[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
. (Join-Path $root 'tools/coverage/coverage-common.ps1')
$fixture = Get-Content -LiteralPath (Join-Path $root 'tools/coverage/fixtures/trusted-reporter-binding.json') -Raw | ConvertFrom-Json
$selected = @($fixture.cases | Where-Object id -eq $Case)
if ($selected.Count -ne 1) { throw "Unknown or duplicate reporter-binding fixture case: $Case" }
$caseData = $selected[0]
$workflowSha = 'a' * 40
$reporterSha = 'b' * 40
$manifest = [PSCustomObject]@{ trusted_reporter_binding = [PSCustomObject]@{
    workflow_commit = $workflowSha; reporter_sha = $reporterSha; repository_id = '123456'; app_id = '7890'
} }
switch ($caseData.mutation) {
    'missing' { $manifest.trusted_reporter_binding = $null }
    'workflow' { $manifest.trusted_reporter_binding.workflow_commit = 'c' * 40 }
    'reporter' { $manifest.trusted_reporter_binding.reporter_sha = 'd' * 40 }
    'repository' { $manifest.trusted_reporter_binding.repository_id = '654321' }
    'app' { $manifest.trusted_reporter_binding.app_id = '4321' }
    'malformed' { $manifest.trusted_reporter_binding.reporter_sha = 'not-a-sha' }
    'none' { }
    default { throw "Unsupported reporter-binding mutation '$($caseData.mutation)'" }
}
$result = Test-TrustedReporterBinding -Manifest $manifest -WorkflowCommit $workflowSha -ReporterSha $reporterSha -RepositoryId '123456' -AppId '7890'
if ($result.IsValid -ne [bool]$caseData.expected_valid) {
    throw "$Case expected IsValid=$($caseData.expected_valid), got $($result.IsValid): $($result.Errors -join '; ')"
}
Write-Output "$Case passed."
