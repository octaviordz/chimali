[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $RepositoryRoot 'tools/coverage/coverage-common.ps1')
$fixture = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'tools/coverage/fixtures/report-live-conclusion.json') -Raw | ConvertFrom-Json
$testCase = @($fixture.cases | Where-Object id -eq $Case)
if ($testCase.Count -ne 1) { throw "Unknown live conclusion case: $Case" }
$caseData = $testCase[0]
$liveValidation = if ($caseData.mutation -eq 'none') {
    @{ IsValid = $true; Errors = @() }
} else {
    @{ IsValid = $false; Errors = @("live $($caseData.mutation) requires fresh capture") }
}
$result = Resolve-ReportLiveConclusion -CurrentConclusion $caseData.current_conclusion -LiveCandidateValidation $liveValidation
if ($result.Conclusion -cne $caseData.expected_conclusion -or -not $result.CanCreateCheck) {
    throw "Unexpected live conclusion result: conclusion=$($result.Conclusion), canCreate=$($result.CanCreateCheck), errors=$($result.Errors -join '; ')"
}
if ($caseData.mutation -ne 'none' -and ($result.Errors.Count -eq 0 -or $result.Errors[0] -notmatch 'fresh capture')) {
    throw 'Stale candidate did not retain its reason for the failure check'
}
Write-Output "$Case passed."
