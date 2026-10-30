function Test-Feature060DeviceReportIdentity {
    param(
        [Parameter(Mandatory)] [System.Collections.IDictionary]$Report,
        [Parameter(Mandatory)] [string]$RunId,
        [Parameter(Mandatory)] [string]$SourceFingerprint,
        [Parameter(Mandatory)] [string]$TestPackage,
        [Parameter(Mandatory)] [string]$InstalledApkSha256
    )
    $valid =
        $Report.run_id -eq $RunId -and
        $Report.assessment.workload.source_revision -eq $SourceFingerprint -and
        $Report.test_package -eq $TestPackage -and
        $Report.test_apk_sha256 -eq $InstalledApkSha256 -and
        $Report.provenance_valid -eq $true
    [pscustomobject]@{
        Valid = [bool]$valid
        PerformanceDisposition = [string]$Report.disposition
        CollectionSucceeded = [bool]$valid
    }
}

function New-Feature060IncompleteCollectorReport {
    param(
        [Parameter(Mandatory)] [string]$RunId,
        [Parameter(Mandatory)] [string]$Reason,
        [Parameter(Mandatory)] [string]$SourceFingerprint,
        [AllowNull()] [System.Collections.IDictionary]$PartialDeviceReport
    )
    [ordered]@{
        schema = '060-android-collector-v1'
        run_id = $RunId
        disposition = 'INCOMPLETE'
        reason = $Reason
        source_fingerprint = $SourceFingerprint
        partial_device_report = $PartialDeviceReport
        recorded_at_utc = [DateTime]::UtcNow.ToString('O')
        limitations = @('Device serial is not retained.', 'No recovered device attempts are available unless partial_device_report is present.')
    }
}

Export-ModuleMember -Function Test-Feature060DeviceReportIdentity, New-Feature060IncompleteCollectorReport
