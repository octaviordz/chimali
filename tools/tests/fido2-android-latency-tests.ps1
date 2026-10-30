$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot '..\Fido2AndroidLatency.Helpers.psm1') -Force

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$expected = @{
    RunId = 'run-1'
    SourceFingerprint = 'revision+sha256'
    TestPackage = 'com.chimali.fido2.test'
    InstalledApkSha256 = 'apk-sha256'
}
foreach ($disposition in @('PASS', 'FAIL', 'INCOMPLETE')) {
    $report = @{
        run_id = $expected.RunId
        disposition = $disposition
        provenance_valid = $true
        test_package = $expected.TestPackage
        test_apk_sha256 = $expected.InstalledApkSha256
        assessment = @{ workload = @{ source_revision = $expected.SourceFingerprint } }
    }
    $check = Test-Feature060DeviceReportIdentity -Report $report @expected
    Assert-True $check.Valid "A valid $disposition report must be collectable."
    Assert-True $check.CollectionSucceeded "A performance $disposition must not fail collection."
    Assert-True ($check.PerformanceDisposition -eq $disposition) 'The diagnostic disposition must be retained.'
}

$missingProvenance = @{
    run_id = $expected.RunId
    disposition = 'PASS'
    provenance_valid = $false
    test_package = $expected.TestPackage
    test_apk_sha256 = $expected.InstalledApkSha256
    assessment = @{ workload = @{ source_revision = 'missing' } }
}
$check = Test-Feature060DeviceReportIdentity -Report $missingProvenance @expected
Assert-True (-not $check.Valid) 'Missing provenance must be rejected.'

$mismatched = $missingProvenance.Clone()
$mismatched.provenance_valid = $true
$mismatched.assessment.workload.source_revision = 'another revision'
$check = Test-Feature060DeviceReportIdentity -Report $mismatched @expected
Assert-True (-not $check.Valid) 'Mismatched source identity must be rejected.'

$partial = New-Feature060IncompleteCollectorReport -RunId $expected.RunId -Reason 'device disconnected' `
    -SourceFingerprint $expected.SourceFingerprint -PartialDeviceReport @{ attempts = @(@{ attempt_id = 'one' }) }
Assert-True ($partial.disposition -eq 'INCOMPLETE') 'Interrupted work must be retained as incomplete.'
Assert-True ($partial.partial_device_report.attempts.Count -eq 1) 'Recoverable attempts must be retained.'

Write-Output 'Feature 060 Android collector behavior checks passed.'
