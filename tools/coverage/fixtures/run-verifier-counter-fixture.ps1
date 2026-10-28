[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$SourceBundle,
    [ValidateSet('negative-tier2', 'tier2-xml-mismatch', 'zero-repository', 'repository-xml-mismatch', 'fractional-tier2', 'large-tier2-counter', 'negative-branch', 'missing-tier2', 'duplicate-counter-field', 'module-report-digest-mismatch', 'inventory-digest-mismatch', 'class-inventory-digest-mismatch', 'duplicate-tier2-xml-counter', 'malformed-tier2-xml-counter', 'malformed-secondary-xml-counter', 'noncanonical-tier2-xml-counter', 'noncanonical-repository-xml-counter', 'comparison-valid', 'comparison-no-index', 'comparison-altered-counters', 'comparison-missing-module', 'comparison-missing-report', 'comparison-failed-suite', 'comparison-missing-suite', 'comparison-candidate-below-floor', 'comparison-incompatible-fingerprint', 'changed-tier2-missing-counter', 'changed-tier2-below-floor', 'incomplete-collection-error', 'missing-workload-task', 'unowned-junit-artifact', 'extra-counter-scope', 'missing-inventory-counter', 'contradictory-applicability', 'execution-class-mismatch', 'execution-device-file-mismatch')]
    [string]$Case = 'negative-tier2'
)

$ErrorActionPreference = 'Stop'
$repository = [System.IO.Path]::GetFullPath($RepositoryRoot)
$source = [System.IO.Path]::GetFullPath($SourceBundle)
if (-not (Test-Path -LiteralPath (Join-Path $repository 'tools/coverage/verify.ps1') -PathType Leaf)) {
    throw "RepositoryRoot does not contain the trusted verifier: $repository"
}
if (-not (Test-Path -LiteralPath (Join-Path $source 'manifest.json') -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $source 'counters.json') -PathType Leaf)) {
    throw "SourceBundle must contain manifest.json and counters.json: $source"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-verifier-counter-fixture-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$bundle = Join-Path $tempRoot 'bundle'
$assessment = Join-Path $tempRoot 'assessment'
New-Item -ItemType Directory -Path $bundle -Force | Out-Null

try {
    Get-ChildItem -LiteralPath $source | Copy-Item -Destination $bundle -Recurse -Force
    . (Join-Path $repository 'tools/coverage/coverage-common.ps1')
    $policy = Read-SafeJson -Path (Join-Path $repository 'tools/coverage/policy.json')
    $manifestPath = Join-Path $bundle 'manifest.json'
    $manifest = Read-SafeJson -Path $manifestPath
    $comparisonCase = $Case.StartsWith('comparison-', [System.StringComparison]::Ordinal)
    $manifest.purpose = if ($Case -eq 'comparison-candidate-below-floor') { 'Candidate' } elseif ($comparisonCase) { 'Comparison' } else { 'Initial' }
    $manifest.git.target_commit = if ($comparisonCase) { [string]$manifest.git.source_commit } else { $null }
    $manifest.git.clean_preflight = $true
    $manifest.git.clean_postflight = $true
    $manifest.decisions.collection = if ($Case -eq 'incomplete-collection-error') { 'Incomplete' } else { 'Complete' }
    $manifest.decisions.tests = 'Passed'
    $manifest.decisions.floors = 'Unverified'
    $manifest.decisions.comparison_compatibility = 'Missing'
    $manifest.decisions.regression = 'NotApplicable'
    if ($Case -eq 'incomplete-collection-error') {
        $manifest | Add-Member -NotePropertyName collection_errors -NotePropertyValue @('Required device workload failed during capture') -Force
    }
    $manifest | Add-Member -NotePropertyName toolchain -NotePropertyValue ([PSCustomObject]@{
        java = '17'; gradle = '8.13'; android_gradle_plugin = '8.13.0'; kotlin = '2.2.0'; jacoco = '0.8.15'
    }) -Force

    $harnessDigests = [ordered]@{}
    foreach ($relative in $policy.harness_requirements.required_files) {
        $harnessDigests[$relative] = Get-FileSha256 -Path (Join-Path $repository $relative)
    }
    $manifest.harness.file_sha256 = [PSCustomObject]$harnessDigests
    $manifest.harness.revision = (& git -C $repository rev-parse HEAD).Trim()
    $manifest.harness.tree = (& git -C $repository rev-parse 'HEAD^{tree}').Trim()

    $junitPaths = @($manifest.artifacts | Where-Object { $_.path -match '^reports/tests/.+\.xml$' } | ForEach-Object { [string]$_.path })
    $junitDirectories = @($junitPaths | ForEach-Object { [IO.Path]::GetDirectoryName($_.Substring('reports/tests/'.Length)).Replace('\', '/') } | Select-Object -Unique)
    if ($junitDirectories.Count -eq 0) { throw 'Source bundle has no JUnit artifact directories for production verifier setup' }
    $deviceDirectories = @($junitDirectories | Where-Object { $_ -match '(^|/)outputs/androidTest-results/connected/' })
    $hostDirectories = @($junitDirectories | Where-Object { $_ -notmatch '(^|/)outputs/androidTest-results/connected/' })
    $appDeviceDirectory = $deviceDirectories | Where-Object { $_ -like 'app/*' } | Select-Object -First 1
    if ($hostDirectories.Count -eq 0) { throw 'Source bundle has no host JUnit artifact directories for production verifier setup' }
    $assignedDeviceDirectories = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $requiredDeviceTasks = foreach ($modulePath in $policy.required_workload.required_device_test_modules) {
        $projectDirectory = ([string]$modulePath).TrimStart(':').Replace(':', '/')
        $deviceDirectory = $deviceDirectories | Where-Object { $_ -like "$projectDirectory/*" -and -not $assignedDeviceDirectories.Contains($_) } | Select-Object -First 1
        if (-not $deviceDirectory) { throw "Source bundle has no uniquely owned connected JUnit output for required device module $modulePath" }
        $null = $assignedDeviceDirectories.Add([string]$deviceDirectory)
        [PSCustomObject]@{
            module_path = [string]$modulePath; task_path = "$($modulePath):connectedAndroidDeviceTest"; executed = $true; skipped = $false; failed = $false
            junit_xml_directory = [string]$deviceDirectory
        }
    }
    $optionalDeviceTasks = foreach ($deviceDirectory in $deviceDirectories) {
        if ($assignedDeviceDirectories.Contains([string]$deviceDirectory)) { continue }
        $projectDirectory = ([string]$deviceDirectory -split '/build/outputs/androidTest-results/connected/')[0]
        if ([string]::IsNullOrWhiteSpace($projectDirectory)) { throw "Cannot bind connected JUnit directory to a project: $deviceDirectory" }
        $modulePath = ':' + $projectDirectory.Replace('/', ':')
        [PSCustomObject]@{
            module_path = $modulePath; task_path = "$modulePath`:connectedAndroidDeviceTest"; executed = $true; skipped = $false; failed = $false
            junit_xml_directory = [string]$deviceDirectory
        }
    }
    $workload = [PSCustomObject]@{
        host_test_tasks = @($hostDirectories | ForEach-Object -Begin { $index = 0 } -Process {
            [PSCustomObject]@{
                task_path = ":fixture:hostTest$index"; executed = $true; skipped = $false; failed = $false
                disposition = 'Applicable'; skip_message = $null; junit_xml_directory = $_
            }
            $null = $index++
        })
        required_device_tasks = @($requiredDeviceTasks)
        optional_device_tasks = @($optionalDeviceTasks)
    }
    $manifest | Add-Member -NotePropertyName workload -NotePropertyValue $workload -Force
    if ($Case -eq 'missing-workload-task') {
        $workload.host_test_tasks = @()
    } elseif ($Case -eq 'unowned-junit-artifact') {
        $unownedPath = 'reports/tests/feature/removed/build/test-results/testDebugUnitTest/TEST-unowned.xml'
        $unownedFile = Join-Path $bundle $unownedPath
        New-Item -ItemType Directory -Path (Split-Path -Parent $unownedFile) -Force | Out-Null
        [IO.File]::WriteAllText($unownedFile, '<testsuite name="unowned" tests="1" failures="0" errors="0" skipped="0"></testsuite>', [Text.UTF8Encoding]::new($false))
        $manifest.artifacts = @($manifest.artifacts) + @([PSCustomObject]@{ path = $unownedPath; sha256 = Get-FileSha256 -Path $unownedFile })
    }
    $fingerprint = Get-MeasurementFingerprint -Policy $policy -Toolchain $manifest.toolchain -Device $manifest.device -HarnessFileDigests $manifest.harness.file_sha256
    $manifest | Add-Member -NotePropertyName measurement_fingerprint -NotePropertyValue $fingerprint -Force

    $countersPath = Join-Path $bundle 'counters.json'
    $counters = Read-SafeJson -Path $countersPath
    if ($Case -in @('changed-tier2-missing-counter', 'changed-tier2-below-floor')) {
        $inventoryPath = Join-Path $bundle 'inventory.json'
        $inventory = Read-SafeJson -Path $inventoryPath
        foreach ($inventoryModule in $inventory.modules) {
            $projectDirectory = ([string]$inventoryModule.module_path).TrimStart(':').Replace(':', '/')
            $inventoryModule | Add-Member -NotePropertyName project_directory -NotePropertyValue $projectDirectory -Force
        }
        $inventory | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $inventoryPath -Encoding utf8
        $inventoryDigest = Get-FileSha256 -Path $inventoryPath
        $counters.inventory_digest = $inventoryDigest
        foreach ($proof in @($counters.zero_count_proofs)) { $proof.inventory_digest = $inventoryDigest }
        $manifest.production_inventory_digest = $inventoryDigest
    }
    foreach ($module in $counters.modules) {
        foreach ($metricName in @('instruction', 'branch')) {
            $metric = $module.$metricName
            $total = [System.Numerics.BigInteger]::Parse([string]$metric.covered, [Globalization.CultureInfo]::InvariantCulture) +
                [System.Numerics.BigInteger]::Parse([string]$metric.missed, [Globalization.CultureInfo]::InvariantCulture)
            $metric | Add-Member -NotePropertyName total -NotePropertyValue $total -Force
        }
    }
    if ($Case -eq 'extra-counter-scope') {
        $sourceModule = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($sourceModule.Count -ne 1) { throw 'Source bundle must contain exactly one :core:common counter record' }
        $extraModule = $sourceModule[0] | ConvertTo-Json -Depth 30 | ConvertFrom-Json
        $extraModule.module_path = ':feature:unaccounted'
        $counters.modules = @($counters.modules) + @($extraModule)
    } elseif ($Case -eq 'missing-inventory-counter') {
        $inventory = Read-SafeJson -Path (Join-Path $bundle 'inventory.json')
        $optionalPaths = @($inventory.modules | ForEach-Object { [string]$_.module_path } | Where-Object { $_ -notin @($policy.tier1_modules + $policy.tier2_gated_modules) })
        $missingPath = $optionalPaths | Select-Object -First 1
        if (-not $missingPath) { throw 'Source inventory has no non-gated module counter to omit' }
        $counters.modules = @($counters.modules | Where-Object { $_.module_path -cne $missingPath })
    } elseif ($Case -eq 'contradictory-applicability') {
        $counterModule = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($counterModule.Count -ne 1) { throw 'Source bundle must contain exactly one :core:common counter record' }
        $counterModule[0].disposition = 'NotApplicable'
    } elseif ($Case -eq 'execution-class-mismatch') {
        $inventory = Read-SafeJson -Path (Join-Path $bundle 'inventory.json')
        $inventoryModule = @($inventory.modules | Where-Object { $_.module_path -eq ':core:common' })
        $counterModule = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($inventoryModule.Count -ne 1 -or $counterModule.Count -ne 1) { throw 'Source bundle must contain exactly one :core:common inventory and counter record' }
        $inventoryModule = $inventoryModule[0]
        $counterModule = $counterModule[0]
        $counterModule | Add-Member -Force -NotePropertyName execution_files -NotePropertyValue @()
        $counterModule | Add-Member -Force -NotePropertyName execution_compatibility -NotePropertyValue ([PSCustomObject]@{
            status = 'Compatible'
            analyzer_identity = [string]$counters.analyzer_identity
            selected_variant = [string]$inventoryModule.selected_variant
            report_digest = [string]$counterModule.report_digest
            class_inventory_digest = [string]$inventoryModule.class_inventory_digest
            retained_class_count = @($inventoryModule.retained_classes).Count
            analyzed_class_count = 1
            analyzed_class_ids = @('fixture/not-retained.class')
            unmatched_class_count = 0
            unmatched_class_ids = @()
            execution_files = @()
        })
    } elseif ($Case -eq 'execution-device-file-mismatch') {
        $counterModule = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($counterModule.Count -ne 1 -or -not $counterModule[0].execution_compatibility) { throw 'Source bundle must contain one :core:common compatibility receipt' }
        $deviceInput = @($counterModule[0].execution_compatibility.execution_files | Where-Object { $_.kind -ceq 'device' })
        if ($deviceInput.Count -ne 1) { throw 'Source bundle must contain one :core:common device compatibility input' }
        $deviceInput[0].sha256 = 'f' * 64
    }
    foreach ($metricName in @('instruction', 'branch')) {
        $metric = $counters.repository.$metricName
        $total = [System.Numerics.BigInteger]::Parse([string]$metric.covered, [Globalization.CultureInfo]::InvariantCulture) +
            [System.Numerics.BigInteger]::Parse([string]$metric.missed, [Globalization.CultureInfo]::InvariantCulture)
        $metric | Add-Member -NotePropertyName total -NotePropertyValue $total -Force
    }

    if ($Case -eq 'changed-tier2-below-floor') {
        $sourceModule = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        $inventory = Read-SafeJson -Path (Join-Path $bundle 'inventory.json')
        $inventoryModule = @($inventory.modules | Where-Object { $_.module_path -eq ':core:bluetooth' })
        if ($sourceModule.Count -ne 1 -or $inventoryModule.Count -ne 1) { throw 'Source bundle must contain one source counter and one :core:bluetooth inventory entry' }
        $dynamicModule = $sourceModule[0] | ConvertTo-Json -Depth 30 | ConvertFrom-Json
        $dynamicModule.module_path = ':core:bluetooth'
        $dynamicModule.tier = 2
        $dynamicModule.disposition = 'Applicable'
        $dynamicModule.instruction.covered = [System.Numerics.BigInteger]::Zero
        $dynamicModule.instruction.missed = [System.Numerics.BigInteger]100
        $dynamicModule.instruction.total = [System.Numerics.BigInteger]100
        $dynamicModule.class_inventory_digest = [string]$inventoryModule[0].class_inventory_digest
        $sourceReportPath = Join-Path $bundle 'reports/modules/core_common.xml'
        $dynamicReportPath = Join-Path $bundle 'reports/modules/core_bluetooth.xml'
        Copy-Item -LiteralPath $sourceReportPath -Destination $dynamicReportPath -Force
        $dynamicXml = [System.Xml.XmlDocument]::new()
        $dynamicXml.Load($dynamicReportPath)
        $dynamicInstruction = $dynamicXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
        if (-not $dynamicInstruction) { throw 'Source Tier 2 module report has no report-level INSTRUCTION counter' }
        $dynamicInstruction.SetAttribute('covered', '0')
        $dynamicInstruction.SetAttribute('missed', '100')
        $dynamicXml.Save($dynamicReportPath)
        $dynamicModule.report_digest = Get-FileSha256 -Path $dynamicReportPath
        $counters.modules = @($counters.modules) + @($dynamicModule)
        $manifest.artifacts = @($manifest.artifacts | Where-Object { $_.path -cne 'reports/modules/core_bluetooth.xml' }) + @([PSCustomObject]@{ path = 'reports/modules/core_bluetooth.xml'; sha256 = Get-FileSha256 -Path $dynamicReportPath })
    }

    $repositoryReportPath = Join-Path $bundle 'reports/repository.xml'
    if ($Case -in @('negative-tier2', 'fractional-tier2', 'negative-branch', 'missing-tier2', 'duplicate-counter-field')) {
        $tier2 = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($tier2.Count -ne 1) { throw "Source bundle must contain exactly one :core:common counter record (found $($tier2.Count))" }
        switch ($Case) {
            'negative-tier2' { $tier2[0].instruction.covered = [System.Numerics.BigInteger]::MinusOne }
            'fractional-tier2' { $tier2[0].instruction.covered = [decimal]1.5 }
            'negative-branch' { $tier2[0].branch.covered = [System.Numerics.BigInteger]::MinusOne }
            'missing-tier2' { $counters.modules = @($counters.modules | Where-Object { $_.module_path -ne ':core:common' }) }
            'duplicate-counter-field' { }
        }
    } elseif ($Case -in @('tier2-xml-mismatch', 'duplicate-tier2-xml-counter', 'malformed-tier2-xml-counter', 'malformed-secondary-xml-counter', 'noncanonical-tier2-xml-counter')) {
        $tier2 = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($tier2.Count -ne 1) { throw "Source bundle must contain exactly one :core:common counter record (found $($tier2.Count))" }
        $tier2ReportPath = Join-Path $bundle 'reports/modules/core_common.xml'
        $tier2Xml = [System.Xml.XmlDocument]::new()
        $tier2Xml.Load($tier2ReportPath)
        $instructionNode = $tier2Xml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
        if (-not $instructionNode) { throw 'Source bundle :core:common report has no report-level INSTRUCTION counter' }
        if ($Case -eq 'tier2-xml-mismatch') {
            $incrementedCovered = [System.Numerics.BigInteger]::Parse($instructionNode.GetAttribute('covered'), [Globalization.CultureInfo]::InvariantCulture) + [System.Numerics.BigInteger]::One
            $instructionNode.SetAttribute('covered', $incrementedCovered.ToString([Globalization.CultureInfo]::InvariantCulture))
        } elseif ($Case -eq 'duplicate-tier2-xml-counter') {
            $duplicateInstruction = $instructionNode.CloneNode($true)
            $null = $tier2Xml.DocumentElement.AppendChild($duplicateInstruction)
        } elseif ($Case -eq 'malformed-tier2-xml-counter') {
            $instructionNode.SetAttribute('missed', '1.5')
        } elseif ($Case -eq 'malformed-secondary-xml-counter') {
            $lineNode = $tier2Xml.SelectSingleNode('/report/counter[@type="LINE"]')
            if (-not $lineNode) { throw 'Source bundle :core:common report has no report-level LINE counter' }
            $lineNode.SetAttribute('covered', '1.5')
        } else {
            $instructionNode.SetAttribute('covered', '+' + $instructionNode.GetAttribute('covered'))
        }
        $tier2Xml.Save($tier2ReportPath)
        $tier2[0].report_digest = Get-FileSha256 -Path $tier2ReportPath
    } elseif ($Case -eq 'zero-repository') {
        $repositoryXml = [System.Xml.XmlDocument]::new()
        $repositoryXml.Load($repositoryReportPath)
        $instructionNode = $repositoryXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
        if (-not $instructionNode) { throw 'Source repository report has no report-level INSTRUCTION counter' }
        $instructionNode.SetAttribute('covered', '0')
        $instructionNode.SetAttribute('missed', '0')
        $repositoryXml.Save($repositoryReportPath)
        $counters.repository.instruction.covered = [System.Numerics.BigInteger]::Zero
        $counters.repository.instruction.missed = [System.Numerics.BigInteger]::Zero
        $counters.repository.instruction.total = [System.Numerics.BigInteger]::Zero
        $counters.repository.report_digest = Get-FileSha256 -Path $repositoryReportPath
    } elseif ($Case -eq 'repository-xml-mismatch') {
        $repositoryXml = [System.Xml.XmlDocument]::new()
        $repositoryXml.Load($repositoryReportPath)
        $instructionNode = $repositoryXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
        if (-not $instructionNode) { throw 'Source repository report has no report-level INSTRUCTION counter' }
        $incrementedCovered = [System.Numerics.BigInteger]::Parse($instructionNode.GetAttribute('covered'), [Globalization.CultureInfo]::InvariantCulture) + [System.Numerics.BigInteger]::One
        $instructionNode.SetAttribute('covered', $incrementedCovered.ToString([Globalization.CultureInfo]::InvariantCulture))
        $repositoryXml.Save($repositoryReportPath)
        $counters.repository.report_digest = Get-FileSha256 -Path $repositoryReportPath
    } elseif ($Case -eq 'noncanonical-repository-xml-counter') {
        $repositoryXml = [System.Xml.XmlDocument]::new()
        $repositoryXml.Load($repositoryReportPath)
        $instructionNode = $repositoryXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
        if (-not $instructionNode) { throw 'Source repository report has no report-level INSTRUCTION counter' }
        $instructionNode.SetAttribute('covered', '+' + $instructionNode.GetAttribute('covered'))
        $repositoryXml.Save($repositoryReportPath)
        $counters.repository.report_digest = Get-FileSha256 -Path $repositoryReportPath
    } elseif ($Case -eq 'large-tier2-counter') {
        $tier2 = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
        if ($tier2.Count -ne 1) { throw "Source bundle must contain exactly one :core:common counter record (found $($tier2.Count))" }
        $largeCount = [System.Numerics.BigInteger]::Parse('9223372036854775808', [Globalization.CultureInfo]::InvariantCulture)
        $tier2[0].instruction.covered = $largeCount
        $tier2[0].instruction.missed = [System.Numerics.BigInteger]::Zero
        $tier2[0].instruction.total = $largeCount
        $tier2ReportPath = Join-Path $bundle 'reports/modules/core_common.xml'
        $tier2Xml = [System.Xml.XmlDocument]::new()
        $tier2Xml.Load($tier2ReportPath)
        $instructionNode = $tier2Xml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
        if (-not $instructionNode) { throw 'Source bundle :core:common report has no report-level INSTRUCTION counter' }
        $instructionNode.SetAttribute('covered', $largeCount.ToString([Globalization.CultureInfo]::InvariantCulture))
        $instructionNode.SetAttribute('missed', '0')
        $tier2Xml.Save($tier2ReportPath)
        $tier2[0].report_digest = Get-FileSha256 -Path $tier2ReportPath
    } elseif ($Case -in @('module-report-digest-mismatch', 'inventory-digest-mismatch', 'class-inventory-digest-mismatch')) {
        if ($Case -eq 'inventory-digest-mismatch') {
            $counters.inventory_digest = 'f' * 64
        } else {
            $tier2 = @($counters.modules | Where-Object { $_.module_path -eq ':core:common' })
            if ($tier2.Count -ne 1) { throw "Source bundle must contain exactly one :core:common counter record (found $($tier2.Count))" }
            if ($Case -eq 'module-report-digest-mismatch') { $tier2[0].report_digest = 'f' * 64 }
            if ($Case -eq 'class-inventory-digest-mismatch') { $tier2[0].class_inventory_digest = 'e' * 64 }
        }
    }
    $counters | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $countersPath -Encoding utf8
    if ($Case -eq 'duplicate-counter-field') {
        $json = [System.IO.File]::ReadAllText($countersPath)
        $moduleStart = $json.IndexOf('"module_path": ":core:common"', [System.StringComparison]::Ordinal)
        if ($moduleStart -lt 0) { throw 'Serialized counters are missing the :core:common record' }
        $instructionStart = $json.IndexOf('"instruction": {', $moduleStart, [System.StringComparison]::Ordinal)
        $coveredStart = if ($instructionStart -ge 0) { $json.IndexOf('"covered": ', $instructionStart, [System.StringComparison]::Ordinal) } else { -1 }
        if ($coveredStart -lt 0) { throw 'Serialized :core:common instruction counter is missing covered field' }
        $valueStart = $coveredStart + '"covered": '.Length
        $valueEnd = $json.IndexOf(',', $valueStart)
        if ($valueEnd -lt 0) { throw 'Serialized :core:common covered value has no following field' }
        $value = $json.Substring($valueStart, $valueEnd - $valueStart)
        $json = $json.Insert($valueEnd, ', "covered": ' + $value)
        [System.IO.File]::WriteAllText($countersPath, $json, [System.Text.UTF8Encoding]::new($false))
    }

    if ($Case -in @('changed-tier2-missing-counter', 'changed-tier2-below-floor', 'comparison-candidate-below-floor')) {
        $changedPathsPath = Join-Path $bundle 'changed-paths.txt'
        $changedPathText = if ($Case -eq 'comparison-candidate-below-floor') { "core/security/src/androidMain/kotlin/com/chimali/security/Changed.kt`n" } else { "core/bluetooth/src/main/kotlin/com/chimali/bluetooth/Changed.kt`n" }
        [System.IO.File]::WriteAllText($changedPathsPath, $changedPathText, [System.Text.UTF8Encoding]::new($false))
        $manifest.artifacts = @($manifest.artifacts | Where-Object { $_.path -cne 'changed-paths.txt' }) + @([PSCustomObject]@{ path = 'changed-paths.txt'; sha256 = Get-FileSha256 -Path $changedPathsPath })
    }

    foreach ($artifact in $manifest.artifacts) {
        $artifactPath = Join-Path $bundle ([string]$artifact.path)
        if (Test-Path -LiteralPath $artifactPath -PathType Leaf) {
            $artifact.sha256 = Get-FileSha256 -Path $artifactPath
        }
    }
    if (@($manifest.artifacts | Where-Object { [string]$_.path -ceq 'summary.md' }).Count -eq 0) {
        $manifest.artifacts = @($manifest.artifacts) + @([PSCustomObject]@{ path = 'summary.md'; sha256 = Get-FileSha256 -Path (Join-Path $bundle 'summary.md') })
    }
    $manifest | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $manifestPath -Encoding utf8

    $verifyArgs = @('-RunBundle', $bundle, '-PolicyPath', (Join-Path $repository 'tools/coverage/policy.json'), '-OutputDirectory', $assessment)
    if ($Case -in @('changed-tier2-missing-counter', 'comparison-candidate-below-floor')) {
        $verifyArgs += @('-ChangedPathsFile', (Join-Path $bundle 'changed-paths.txt'))
    }
    if ($comparisonCase) {
        $evidenceRoot = Join-Path $tempRoot 'evidence'
        $baseline = Join-Path $evidenceRoot 'baselines/runs/baseline'
        New-Item -ItemType Directory -Path (Split-Path -Parent $baseline) -Force | Out-Null
        Copy-Item -LiteralPath $bundle -Destination $baseline -Recurse -Force

        $baselineManifestPath = Join-Path $baseline 'manifest.json'
        $baselineManifest = Read-SafeJson -Path $baselineManifestPath
        $baselineDigestBeforeMutation = Get-CoverageBundleDigest -BundlePath $baseline
        switch ($Case) {
            'comparison-candidate-below-floor' {
                $baselineCountersPath = Join-Path $baseline 'counters.json'
                $baselineCounters = Read-SafeJson -Path $baselineCountersPath
                $securityModule = @($baselineCounters.modules | Where-Object { $_.module_path -eq ':core:security' })
                if ($securityModule.Count -ne 1) { throw 'Below-floor comparison baseline must contain exactly one :core:security counter record' }
                $oldCovered = [System.Numerics.BigInteger]::Parse([string]$securityModule[0].instruction.covered, [Globalization.CultureInfo]::InvariantCulture)
                $oldMissed = [System.Numerics.BigInteger]::Parse([string]$securityModule[0].instruction.missed, [Globalization.CultureInfo]::InvariantCulture)
                $newCovered = [System.Numerics.BigInteger]2500
                $newMissed = ($oldCovered + $oldMissed) - $newCovered
                if ($newCovered * 100 -ge ($oldCovered + $oldMissed) * 60) { throw 'Fixture source must have enough instructions to make the accepted target fall below 60%' }
                $securityModule[0].instruction.covered = $newCovered
                $securityModule[0].instruction.missed = $newMissed
                $securityModule[0].instruction.total = $newCovered + $newMissed
                $securityReportPath = Join-Path $baseline 'reports/modules/core_security.xml'
                $securityXml = [System.Xml.XmlDocument]::new()
                $securityXml.Load($securityReportPath)
                $securityInstruction = $securityXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
                if (-not $securityInstruction) { throw 'Below-floor baseline security report is missing report-level instructions' }
                $securityInstruction.SetAttribute('covered', $newCovered.ToString([Globalization.CultureInfo]::InvariantCulture))
                $securityInstruction.SetAttribute('missed', $newMissed.ToString([Globalization.CultureInfo]::InvariantCulture))
                $securityXml.Save($securityReportPath)
                $securityReportDigest = Get-FileSha256 -Path $securityReportPath
                $securityModule[0].report_digest = $securityReportDigest
                if ($securityModule[0].execution_compatibility) { $securityModule[0].execution_compatibility.report_digest = $securityReportDigest }

                $repoMetric = $baselineCounters.repository.instruction
                $repoCovered = [System.Numerics.BigInteger]::Parse([string]$repoMetric.covered, [Globalization.CultureInfo]::InvariantCulture) - ($oldCovered - $newCovered)
                $repoMissed = [System.Numerics.BigInteger]::Parse([string]$repoMetric.missed, [Globalization.CultureInfo]::InvariantCulture) + ($oldCovered - $newCovered)
                $repoMetric.covered = $repoCovered
                $repoMetric.missed = $repoMissed
                $repoMetric.total = $repoCovered + $repoMissed
                $repositoryReportPath = Join-Path $baseline 'reports/repository.xml'
                $repositoryXml = [System.Xml.XmlDocument]::new()
                $repositoryXml.Load($repositoryReportPath)
                $repositoryInstruction = $repositoryXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
                if (-not $repositoryInstruction) { throw 'Below-floor baseline repository report is missing report-level instructions' }
                $repositoryInstruction.SetAttribute('covered', $repoCovered.ToString([Globalization.CultureInfo]::InvariantCulture))
                $repositoryInstruction.SetAttribute('missed', $repoMissed.ToString([Globalization.CultureInfo]::InvariantCulture))
                $repositoryXml.Save($repositoryReportPath)
                $baselineCounters.repository.report_digest = Get-FileSha256 -Path $repositoryReportPath
                $baselineCounters | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $baselineCountersPath -Encoding utf8
                foreach ($artifact in $baselineManifest.artifacts) {
                    if ($artifact.path -ceq 'counters.json') { $artifact.sha256 = Get-FileSha256 -Path $baselineCountersPath }
                    elseif ($artifact.path -ceq 'reports/modules/core_security.xml') { $artifact.sha256 = $securityReportDigest }
                    elseif ($artifact.path -ceq 'reports/repository.xml') { $artifact.sha256 = Get-FileSha256 -Path $repositoryReportPath }
                }
                $baselineManifest.purpose = 'Comparison'
            }
            'comparison-altered-counters' {
                $baselineCountersPath = Join-Path $baseline 'counters.json'
                $baselineCounters = Read-SafeJson -Path $baselineCountersPath
                $tier1 = @($baselineCounters.modules | Where-Object { $_.module_path -eq ':core:security' })
                if ($tier1.Count -ne 1) { throw 'Baseline fixture must contain exactly one :core:security counter record' }
                $covered = [System.Numerics.BigInteger]::Parse([string]$tier1[0].instruction.covered, [Globalization.CultureInfo]::InvariantCulture)
                $missed = [System.Numerics.BigInteger]::Parse([string]$tier1[0].instruction.missed, [Globalization.CultureInfo]::InvariantCulture)
                if ($missed -le [System.Numerics.BigInteger]::Zero) { throw 'Baseline :core:security report has no missed instruction available for a balanced mutation' }
                $tier1[0].instruction.covered = $covered + [System.Numerics.BigInteger]::One
                $tier1[0].instruction.missed = $missed - [System.Numerics.BigInteger]::One
                $tier1[0].instruction.total = $covered + $missed
                $securityReportPath = Join-Path $baseline 'reports/modules/core_security.xml'
                if (-not (Test-Path -LiteralPath $securityReportPath -PathType Leaf)) { throw 'Baseline fixture is missing the canonical :core:security report' }
                $securityXml = [System.Xml.XmlDocument]::new()
                $securityXml.Load($securityReportPath)
                $securityInstruction = $securityXml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
                if (-not $securityInstruction) { throw 'Baseline :core:security report has no report-level INSTRUCTION counter' }
                $securityInstruction.SetAttribute('covered', ($covered + [System.Numerics.BigInteger]::One).ToString([Globalization.CultureInfo]::InvariantCulture))
                $securityInstruction.SetAttribute('missed', ($missed - [System.Numerics.BigInteger]::One).ToString([Globalization.CultureInfo]::InvariantCulture))
                $securityXml.Save($securityReportPath)
                $mutatedReportDigest = Get-FileSha256 -Path $securityReportPath
                $tier1[0].report_digest = $mutatedReportDigest
                if ($tier1[0].execution_compatibility) { $tier1[0].execution_compatibility.report_digest = $mutatedReportDigest }
                $baselineCounters | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $baselineCountersPath -Encoding utf8
                foreach ($artifact in $baselineManifest.artifacts) {
                    if ($artifact.path -ceq 'counters.json') { $artifact.sha256 = Get-FileSha256 -Path $baselineCountersPath }
                    elseif ($artifact.path -ceq 'reports/modules/core_security.xml') { $artifact.sha256 = $mutatedReportDigest }
                }
            }
            'comparison-missing-module' {
                $baselineCountersPath = Join-Path $baseline 'counters.json'
                $baselineCounters = Read-SafeJson -Path $baselineCountersPath
                $baselineCounters.modules = @($baselineCounters.modules | Where-Object { $_.module_path -ne ':core:data' })
                $baselineCounters | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $baselineCountersPath -Encoding utf8
            }
            'comparison-missing-report' {
                $missingReport = Join-Path $baseline 'reports/repository.xml'
                if (-not (Test-Path -LiteralPath $missingReport -PathType Leaf)) { throw 'Baseline fixture repository report is absent before mutation' }
                Remove-Item -LiteralPath $missingReport -Force
            }
            'comparison-failed-suite' {
                $requiredSuite = @($baselineManifest.suites | Where-Object { $_.required -eq $true } | Select-Object -First 1)
                if ($requiredSuite.Count -ne 1) { throw 'Baseline fixture has no required suite to mutate' }
                $requiredSuite[0].failures = 1
            }
            'comparison-missing-suite' { $baselineManifest.suites = @() }
            'comparison-incompatible-fingerprint' {
                $baselineManifest.device.abi = 'x86_64'
                $baselineManifest.measurement_fingerprint = Get-MeasurementFingerprint -Policy $policy -Toolchain $baselineManifest.toolchain -Device $baselineManifest.device -HarnessFileDigests $baselineManifest.harness.file_sha256
            }
        }
        $baselineManifest | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $baselineManifestPath -Encoding utf8

        $baselineIndexPath = Join-Path $evidenceRoot 'baselines/current.json'
        $baselineIndex = [PSCustomObject]@{
            provenance = 'MeasuredAcceptedTarget'
            target_commit = [string]$manifest.git.target_commit
            run_id = [string]$baselineManifest.run_id
            bundle_path = 'baselines/runs/baseline'
            bundle_digest = if ($Case -eq 'comparison-altered-counters') { $baselineDigestBeforeMutation } else { Get-CoverageBundleDigest -BundlePath $baseline }
        }
        $baselineIndex | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $baselineIndexPath -Encoding utf8
        $verifyArgs += @('-ComparisonBundle', $baseline)
        if ($Case -ne 'comparison-no-index') { $verifyArgs += @('-AcceptedBaselineIndexPath', $baselineIndexPath) }
    }

    $verify = Join-Path $repository 'tools/coverage/verify.ps1'
    $verifyOutput = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $verify @verifyArgs 2>&1)
    $verifyExit = $LASTEXITCODE
    $decisionPath = Join-Path $assessment 'decision.json'
    if ($Case -in @('comparison-valid', 'comparison-candidate-below-floor')) {
        if ($verifyExit -ne 0 -or -not (Test-Path -LiteralPath $decisionPath -PathType Leaf)) {
            $reasons = if (Test-Path -LiteralPath $decisionPath -PathType Leaf) { (Read-SafeJson -Path $decisionPath).unmet_reasons -join '; ' } else { 'decision receipt was not written' }
            throw "Expected indexed production self-comparison to pass; observed exit $verifyExit. $reasons"
        }
        if ($Case -eq 'comparison-candidate-below-floor') {
            $decision = Read-SafeJson -Path $decisionPath
            $security = @($decision.modules | Where-Object { $_.Module -eq ':core:security' }) | Select-Object -First 1
            if (-not $security -or $security.Instruction.CoveragePercent -ge 60 -or -not $decision.overall_passed -or $decision.decisions.regression -cne 'Passed') {
                throw 'Production comparison did not accept the below-floor target while passing the eligible candidate and regression checks.'
            }
            Write-Output 'Production verifier accepted an indexed, test-passing target below the new Tier 1 floor and an eligible Candidate above that target.'
        } else {
            Write-Output 'Production verifier accepted the complete indexed self-comparison.'
        }
        Write-Output 'Verifier exit: 0'
        return
    }
    $expectedExit = if ($Case -in @('missing-workload-task', 'unowned-junit-artifact')) { 3 } else { 1 }
    if ($verifyExit -ne $expectedExit -or -not (Test-Path -LiteralPath $decisionPath -PathType Leaf)) {
        $preflightReasons = if (Test-Path -LiteralPath $decisionPath -PathType Leaf) { (Read-SafeJson -Path $decisionPath).unmet_reasons -join '; ' } else { 'no decision receipt' }
        throw "Expected verifier rejection exit $expectedExit with a decision receipt; observed exit $verifyExit. Verifier output: $($verifyOutput -join ' '); reasons: $preflightReasons"
    }
    $decision = Read-SafeJson -Path $decisionPath
    if ($Case -eq 'large-tier2-counter') {
        $largeCountText = '9223372036854775808'
        $decisionModule = @($decision.modules | Where-Object { $_.Module -eq ':core:common' }) | Select-Object -First 1
        if (-not $decisionModule -or [string]$decisionModule.Instruction.Covered -cne $largeCountText) {
            throw "Verifier did not preserve the full-precision Tier 2 instruction count $largeCountText"
        }
        if (@($decision.unmet_reasons | Where-Object { $_ -match ':core:common.*(invalid|nonnegative|mismatch|overflow|supported integer)' }).Count -gt 0) {
            throw "Verifier rejected a valid above-Int64 Tier 2 count: $($decision.unmet_reasons -join '; ')"
        }
        Write-Output 'Production verifier preserved the above-Int64 Tier 2 count through JSON, XML reconciliation, floor evaluation, and decision serialization.'
        Write-Output "Verifier exit: $verifyExit (unrelated bundle decisions may still fail)"
        return
    }
    $reasonPattern = switch ($Case) {
        'negative-tier2' { ':core:common.*instruction covered must be a nonnegative JSON integer' }
        'tier2-xml-mismatch' { ':core:common.*INSTRUCTION XML counts disagree with canonical covered/missed counts' }
        'zero-repository' { 'Repository instruction aggregate denominator is zero' }
        'repository-xml-mismatch' { 'repository INSTRUCTION counts disagree between JaCoCo XML and canonical counters' }
        'fractional-tier2' { ':core:common.*instruction covered must be a nonnegative JSON integer' }
        'negative-branch' { ':core:common.*branch covered must be a nonnegative JSON integer' }
        'missing-tier2' { 'Run must contain exactly one counter record for :core:common' }
        'duplicate-counter-field' { 'Failed to read counters.json' }
        'module-report-digest-mismatch' { ':core:common.*Counter report digest mismatch' }
        'inventory-digest-mismatch' { 'Inventory file digest does not match manifest and canonical counters' }
        'class-inventory-digest-mismatch' { ':core:common.*Counter class inventory digest mismatch' }
        'duplicate-tier2-xml-counter' { ':core:common.*exactly one report-level INSTRUCTION counter' }
        'malformed-tier2-xml-counter' { ':core:common.*INSTRUCTION XML missed must be a nonnegative integer' }
        'malformed-secondary-xml-counter' { ':core:common LINE XML covered must be a nonnegative integer' }
        'noncanonical-tier2-xml-counter' { ':core:common.*must be a nonnegative integer' }
        'noncanonical-repository-xml-counter' { 'repository.*must be a nonnegative integer' }
        'comparison-no-index' { 'Comparison requires the trusted accepted-baseline index file; an arbitrary caller-supplied bundle is insufficient' }
        'comparison-altered-counters' { 'Accepted baseline bundle digest does not match its index' }
        'comparison-missing-module' { 'Baseline must contain exactly one counter record for :core:data' }
        'comparison-missing-report' { 'Baseline artifact is missing: reports/repository.xml' }
        'comparison-failed-suite' { 'Baseline suite .* is not Passed' }
        'comparison-missing-suite' { 'Baseline must contain required suite outcome records|Missing expected suite records|Baseline has no required suite record' }
        'comparison-incompatible-fingerprint' { 'Baseline and candidate measurement compatibility fingerprints differ' }
        'changed-tier2-missing-counter' { 'Run production inventory must contain exactly one counter record for :core:bluetooth' }
        'changed-tier2-below-floor' { ':core:bluetooth: Tier 2 instruction.*below.*20%' }
        'incomplete-collection-error' { 'Capture collection error: Required device workload failed during capture' }
        'missing-workload-task' { 'Missing required host/device workload task evidence' }
        'unowned-junit-artifact' { 'Retained JUnit artifact is not owned by a recorded workload' }
        'extra-counter-scope' { 'Run counter module :feature:unaccounted is absent from production inventory' }
        'missing-inventory-counter' { 'Run production inventory module .* has no canonical counter record' }
        'contradictory-applicability' { ':core:common: inventory and canonical counter dispositions disagree' }
        'execution-class-mismatch' { ':core:common execution compatibility analyzed class is absent from retained inventory: fixture/not-retained.class' }
        'execution-device-file-mismatch' { ':core:common execution compatibility file binding differs from canonical execution attribution' }
    }
    $expectedReason = @($decision.unmet_reasons | Where-Object { $_ -match $reasonPattern })
    if ($expectedReason.Count -eq 0) {
        throw "Verifier failed for a different reason; expected '$Case' diagnostic. Reasons: $($decision.unmet_reasons -join '; ')"
    }
    Write-Output "Production verifier rejected the '$Case' evidence defect."
    $expectedReason
    Write-Output "Verifier exit: $verifyExit"
} finally {
    if ([System.IO.Directory]::Exists($tempRoot)) {
        $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
        if (-not $resolvedTemp.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-verifier-counter-fixture-*') {
            throw "Refusing unsafe fixture cleanup target: $resolvedTemp"
        }
        [System.IO.Directory]::Delete($resolvedTemp, $true)
    }
}
