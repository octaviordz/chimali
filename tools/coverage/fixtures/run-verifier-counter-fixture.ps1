[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$SourceBundle,
    [ValidateSet('negative-tier2', 'tier2-xml-mismatch', 'zero-repository', 'repository-xml-mismatch', 'fractional-tier2', 'large-tier2-counter', 'negative-branch', 'missing-tier2', 'duplicate-counter-field', 'module-report-digest-mismatch', 'inventory-digest-mismatch', 'class-inventory-digest-mismatch', 'duplicate-tier2-xml-counter', 'malformed-tier2-xml-counter', 'malformed-secondary-xml-counter', 'noncanonical-tier2-xml-counter', 'noncanonical-repository-xml-counter', 'comparison-valid', 'comparison-no-index', 'comparison-altered-counters', 'comparison-missing-module', 'comparison-missing-report', 'comparison-failed-suite', 'comparison-failed-junit', 'comparison-dirty-preflight', 'comparison-dirty-harness', 'comparison-security-file-substitution', 'comparison-missing-suite', 'comparison-candidate-below-floor', 'comparison-incompatible-fingerprint', 'changed-tier2-missing-counter', 'changed-tier2-below-floor', 'incomplete-collection-error', 'dirty-preflight', 'dirty-harness', 'harness-changed-during-capture', 'junit-hidden-failure', 'junit-hidden-error', 'junit-hidden-skip', 'junit-inflated-count', 'junit-contradictory-total', 'missing-workload-task', 'zero-required-device', 'inventory-unapproved-variant', 'inventory-omitted-owner', 'inventory-compiler-boundary', 'inventory-output-boundary', 'inventory-unproven-exclusion', 'unowned-junit-artifact', 'extra-counter-scope', 'missing-inventory-counter', 'contradictory-applicability', 'execution-class-mismatch', 'execution-device-file-mismatch', 'execution-security-file-substitution')]
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
    function New-FixtureMeasurementRuntime {
        param([Parameter(Mandatory = $true)]$Policy)
        $daemon = [PSCustomObject]@{ java_version = '21'; java_runtime_version = '21.0.7+9'; java_vendor = 'JetBrains s.r.o.'; vm_name = 'OpenJDK 64-Bit Server VM'; architecture = 'amd64' }
        $javaCompilers = @(); $kotlinCompilers = @(); $hostTests = @()
        foreach ($moduleProperty in $Policy.production_scope.variants.PSObject.Properties) {
            $modulePath = [string]$moduleProperty.Name
            $compilation = $Policy.production_scope.compilations.([string]$moduleProperty.Value)
            foreach ($taskName in @($compilation.compiler_tasks)) {
                $record = [PSCustomObject]@{ task_path = "${modulePath}:$taskName"; java_version = '21'; java_runtime_version = '21.0.7+9'; java_vendor = 'JetBrains s.r.o.'; source_compatibility = '21'; target_compatibility = '21'; execution_strategy = 'in-process'; jvm_target = '21' }
                if ([string]$taskName -match 'JavaWithJavac$') { $javaCompilers += $record } else { $kotlinCompilers += $record }
            }
            foreach ($taskName in @($compilation.test_tasks)) {
                $hostTests += [PSCustomObject]@{ task_path = "${modulePath}:$taskName"; identity_source = 'GradleDaemon'; java_version = '21'; java_runtime_version = '21.0.7+9'; java_vendor = 'JetBrains s.r.o.' }
            }
        }
        return [PSCustomObject]@{
            schema_version = '1'; gradle_daemon = $daemon; kotlin_execution_strategy = 'in-process'
            java_compilers = @($javaCompilers); kotlin_compilers = @($kotlinCompilers); host_test_jvms = @($hostTests)
            jacoco_analyzer = [PSCustomObject]@{ engine = 'jacoco-0.8.15'; java_version = '21'; java_runtime_version = '21.0.7+9'; java_vendor = 'JetBrains s.r.o.' }
        }
    }
    $comparisonCase = $Case.StartsWith('comparison-', [System.StringComparison]::Ordinal)
    $manifest.purpose = if ($Case -eq 'comparison-candidate-below-floor') { 'Candidate' } elseif ($comparisonCase) { 'Comparison' } else { 'Initial' }
    $manifest.git.target_commit = if ($comparisonCase) { [string]$manifest.git.source_commit } else { $null }
    $manifest.git.clean_preflight = $true
    $manifest.git.clean_postflight = $true
    $manifest.harness.clean = $true
    if ($Case -eq 'dirty-preflight') { $manifest.git.clean_preflight = $false }
    if ($Case -eq 'dirty-harness') { $manifest.harness.clean = $false }
    if ($Case -eq 'harness-changed-during-capture') { $manifest.harness.clean = $false }
    $manifest.decisions.collection = if ($Case -eq 'incomplete-collection-error') { 'Incomplete' } else { 'Complete' }
    $manifest.decisions.tests = 'Passed'
    $manifest.decisions.floors = 'Unverified'
    $manifest.decisions.comparison_compatibility = 'Missing'
    $manifest.decisions.regression = 'NotApplicable'
    if ($Case -eq 'incomplete-collection-error') {
        $manifest | Add-Member -NotePropertyName collection_errors -NotePropertyValue @('Required device workload failed during capture') -Force
    }
    $manifest | Add-Member -NotePropertyName toolchain -NotePropertyValue ([PSCustomObject]@{
        java = '21.0.7+9'; gradle = '8.13'; android_gradle_plugin = '8.13.0'; kotlin = '2.2.0'; jacoco = '0.8.15'
        runtime = (New-FixtureMeasurementRuntime -Policy $policy); launcher_java = '25.0.5'
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
        $configuredDeviceTask = [string]$policy.required_workload.device_test_tasks.PSObject.Properties[[string]$modulePath].Value
        $deviceDirectory = $deviceDirectories | Where-Object { $_ -like "$projectDirectory/*" -and -not $assignedDeviceDirectories.Contains($_) } | Select-Object -First 1
        if (-not $deviceDirectory) { throw "Source bundle has no uniquely owned connected JUnit output for required device module $modulePath" }
        $null = $assignedDeviceDirectories.Add([string]$deviceDirectory)
        [PSCustomObject]@{
            module_path = [string]$modulePath; task_path = "${modulePath}:$configuredDeviceTask"; executed = $true; skipped = $false; failed = $false
            junit_xml_directory = [string]$deviceDirectory
        }
    }
    $optionalDeviceTasks = foreach ($deviceDirectory in $deviceDirectories) {
        if ($assignedDeviceDirectories.Contains([string]$deviceDirectory)) { continue }
        $projectDirectory = ([string]$deviceDirectory -split '/build/outputs/androidTest-results/connected/')[0]
        if ([string]::IsNullOrWhiteSpace($projectDirectory)) { throw "Cannot bind connected JUnit directory to a project: $deviceDirectory" }
        $modulePath = ':' + $projectDirectory.Replace('/', ':')
        $configuredDeviceTask = [string]$policy.required_workload.device_test_tasks.PSObject.Properties[$modulePath].Value
        [PSCustomObject]@{
            module_path = $modulePath; task_path = "${modulePath}:$configuredDeviceTask"; executed = $true; skipped = $false; failed = $false
            junit_xml_directory = [string]$deviceDirectory
        }
    }
    $configuredHostTasks = @()
    foreach ($modulePath in @($policy.production_scope.variants.PSObject.Properties.Name)) {
        $variant = [string]$policy.production_scope.variants.$modulePath
        $projectDirectory = ([string]$modulePath).TrimStart(':').Replace(':', '/')
        $moduleJUnitDirectory = $hostDirectories | Where-Object { $_ -like "$projectDirectory/build/test-results/*" } | Select-Object -First 1
        foreach ($taskName in @($policy.production_scope.compilations.$variant.test_tasks)) {
            $notApplicable = -not $moduleJUnitDirectory
            $configuredHostTasks += [PSCustomObject]@{
                task_path = "${modulePath}:$taskName"; executed = -not $notApplicable; skipped = $notApplicable; failed = $false
                disposition = if ($notApplicable) { 'NotApplicable' } else { 'Applicable' }
                skip_message = if ($notApplicable) { 'NO-SOURCE' } else { $null }
                test_scope_proof = if ($notApplicable) { [PSCustomObject]@{ task_path = "${modulePath}:$taskName"; gradle_no_source = $true; compiled_test_class_count = 0 } } else { $null }
                junit_xml_directory = if ($notApplicable) { $null } else { [string]$moduleJUnitDirectory }
            }
        }
    }
    $workload = [PSCustomObject]@{
        host_test_tasks = @($configuredHostTasks)
        required_device_tasks = @($requiredDeviceTasks)
        optional_device_tasks = @($optionalDeviceTasks)
    }
    $manifest | Add-Member -NotePropertyName workload -NotePropertyValue $workload -Force
    foreach ($executionInput in @($manifest.execution_files)) {
        $taskKind = [string]$executionInput.kind
        $taskPaths = @(Get-ExpectedExecutionTaskPaths -Workload $workload -Scope ([string]$executionInput.scope) -Kind $taskKind)
        $executionInput | Add-Member -Force -NotePropertyName run_id -NotePropertyValue ([string]$manifest.run_id)
        $executionInput | Add-Member -Force -NotePropertyName device_serial -NotePropertyValue ([string]$manifest.device.device_serial)
        $executionInput | Add-Member -Force -NotePropertyName task_paths -NotePropertyValue $taskPaths
    }
    $strictSuites = @()
    foreach ($testArtifact in @($manifest.artifacts | Where-Object { ([string]$_.path).Replace('\', '/') -match '^reports/tests/.+\.xml$' })) {
        $testArtifactPath = ([string]$testArtifact.path).Replace('\', '/')
        $testIdentity = $testArtifactPath.Substring('reports/tests/'.Length)
        $strictOutcome = Get-StrictJUnitOutcome -Path (Join-Path $bundle $testArtifactPath) -ReportIdentity $testIdentity
        if (-not $strictOutcome.IsValid) { throw "Source bundle JUnit report is not valid under the current strict parser: $testArtifactPath ($($strictOutcome.Errors -join '; '))" }
        foreach ($suite in $strictOutcome.Suites) {
            $strictSuites += [PSCustomObject]@{
                suite_identity = $suite.suite_identity; suite_name = $suite.suite_name
                executed = $suite.executed; successful = $suite.successful; failed = $suite.failed
                tests = $suite.tests; failures = $suite.failures; errors = $suite.errors; skipped = $suite.skipped; required = $true
            }
        }
    }
    $manifest.suites = $strictSuites
    if ($Case -like 'junit-*') {
        $testArtifact = @($manifest.artifacts | Where-Object { ([string]$_.path).Replace('\', '/') -match '^reports/tests/.+\.xml$' } | Select-Object -First 1)[0]
        if (-not $testArtifact) { throw 'Source bundle has no JUnit artifact to mutate' }
        $mutationXml = switch ($Case) {
            'junit-hidden-failure' { '<testsuite name="hidden" tests="1" failures="0" errors="0" skipped="0"><testcase name="hidden"><failure message="injected"/></testcase></testsuite>' }
            'junit-hidden-error' { '<testsuite name="hidden" tests="1" failures="0" errors="0" skipped="0"><testcase name="hidden"><error message="injected"/></testcase></testsuite>' }
            'junit-hidden-skip' { '<testsuite name="hidden" tests="1" failures="0" errors="0" skipped="0"><testcase name="hidden"><skipped/></testcase></testsuite>' }
            'junit-inflated-count' { '<testsuite name="hidden" tests="2" failures="0" errors="0" skipped="0"><testcase name="hidden"/></testsuite>' }
            'junit-contradictory-total' { '<testsuites tests="2" failures="0" errors="0" skipped="0"><testsuite name="hidden" tests="1" failures="0" errors="0" skipped="0"><testcase name="hidden"/></testsuite></testsuites>' }
        }
        [IO.File]::WriteAllText((Join-Path $bundle ([string]$testArtifact.path)), $mutationXml, [Text.UTF8Encoding]::new($false))
    }
    if ($Case -eq 'missing-workload-task') {
        $workload.host_test_tasks = @($workload.host_test_tasks | Select-Object -Skip 1)
    } elseif ($Case -eq 'zero-required-device') {
        $deviceRecord = @($workload.required_device_tasks | Select-Object -First 1)[0]
        $reportPrefix = "reports/tests/$($deviceRecord.junit_xml_directory)/"
        $deviceArtifact = @($manifest.artifacts | Where-Object { ([string]$_.path).Replace('\', '/').StartsWith($reportPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and [string]$_.path -match '\.xml$' } | Select-Object -First 1)[0]
        if (-not $deviceArtifact) { throw "Required device task has no report artifact to mutate: $($deviceRecord.task_path)" }
        [IO.File]::WriteAllText((Join-Path $bundle ([string]$deviceArtifact.path)), '<testsuite name="zero-required" tests="0" failures="0" errors="0" skipped="0" />', [Text.UTF8Encoding]::new($false))
        $reportIdentity = ([string]$deviceArtifact.path).Substring('reports/tests/'.Length)
        $zeroOutcome = Get-StrictJUnitOutcome -Path (Join-Path $bundle ([string]$deviceArtifact.path)) -ReportIdentity $reportIdentity
        $manifest.suites = @($manifest.suites | Where-Object { -not ([string]$_.suite_identity).StartsWith("$($deviceRecord.junit_xml_directory)/", [System.StringComparison]::OrdinalIgnoreCase) })
        foreach ($suite in $zeroOutcome.Suites) {
            $manifest.suites += [PSCustomObject]@{
                suite_identity = $suite.suite_identity; suite_name = $suite.suite_name; tests = $suite.tests; executed = $suite.executed
                successful = $suite.successful; failed = $suite.failed; failures = $suite.failures; errors = $suite.errors; skipped = $suite.skipped; required = $true
            }
        }
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
    $counters | Add-Member -Force -NotePropertyName measurement_runtime -NotePropertyValue $manifest.toolchain.runtime
    foreach ($module in @($counters.modules)) {
        foreach ($inputSet in @(@($module.execution_files), @($module.execution_compatibility.execution_files))) {
            foreach ($inputFile in @($inputSet)) {
                if ($null -eq $inputFile) { continue }
                $kind = [string]$inputFile.kind
                if (-not $kind) { $kind = if ([string]$inputFile.path -match '(?i)\.ec$') { 'device' } else { 'host' } }
                $inputFile | Add-Member -Force -NotePropertyName run_id -NotePropertyValue ([string]$manifest.run_id)
                $inputFile | Add-Member -Force -NotePropertyName device_serial -NotePropertyValue ([string]$manifest.device.device_serial)
                $inputFile | Add-Member -Force -NotePropertyName task_paths -NotePropertyValue @(Get-ExpectedExecutionTaskPaths -Workload $workload -Scope ([string]$module.module_path) -Kind $kind)
            }
        }
    }
    if ($counters.repository -and $null -eq $counters.repository.execution_files) {
        $repositoryInputs = @($manifest.execution_files | Where-Object { [string]$_.scope -ceq ':repository' } | ForEach-Object {
            [PSCustomObject]@{
                path = [string]$_.source_path; repository_path = [string]$_.source_path; sha256 = [string]$_.source_sha256
                run_id = [string]$_.run_id; device_serial = [string]$_.device_serial; task_paths = @($_.task_paths)
            }
        })
        $counters.repository | Add-Member -Force -NotePropertyName execution_files -NotePropertyValue $repositoryInputs
    }
    $inventoryPath = Join-Path $bundle 'inventory.json'
    $inventory = Read-SafeJson -Path $inventoryPath
    foreach ($inventoryModule in $inventory.modules) {
        $projectDirectory = ([string]$inventoryModule.module_path).TrimStart(':').Replace(':', '/')
        $inventoryModule | Add-Member -NotePropertyName project_directory -NotePropertyValue $projectDirectory -Force
        if ($null -eq $inventoryModule.PSObject.Properties['excluded_class_sources']) {
            $exclusionSources = @()
            $basePatterns = @($inventoryModule.excluded_class_patterns | Where-Object { [string]$_ -notmatch '\$\*\.class$' })
            foreach ($pattern in $basePatterns) {
                $classPath = ([string]$pattern).Substring(0, ([string]$pattern).Length - '.class'.Length)
                $exclusionSources += [PSCustomObject]@{
                    source_path = "build/generated/sqldelight/code/fixture/source/$classPath.kt"
                    source_sha256 = ('a' * 64)
                    class_patterns = @("$classPath.class", "$classPath`$*.class")
                }
            }
            $inventoryModule | Add-Member -NotePropertyName excluded_class_sources -NotePropertyValue @($exclusionSources) -Force
        }
    }
    switch ($Case) {
        'inventory-unapproved-variant' {
            $appInventory = @($inventory.modules | Where-Object { $_.module_path -eq ':app' })[0]
            $appInventory.selected_variant = 'release'
            $appInventory.compiler_tasks = @($policy.production_scope.compilations.release.compiler_tasks | ForEach-Object { ":app:$_" } | Sort-Object -CaseSensitive)
            $appCounter = @($counters.modules | Where-Object { $_.module_path -eq ':app' })[0]
            $appCounter.execution_compatibility | Add-Member -Force -NotePropertyName selected_variant -NotePropertyValue 'release'
            foreach ($inputFile in @($appCounter.execution_files) + @($appCounter.execution_compatibility.execution_files)) { $inputFile | Add-Member -Force -NotePropertyName selected_variant -NotePropertyValue 'release' }
            foreach ($inputFile in @($manifest.execution_files | Where-Object { $_.scope -ceq ':app' })) { $inputFile | Add-Member -Force -NotePropertyName selected_variant -NotePropertyValue 'release' }
        }
        'inventory-omitted-owner' { $inventory.modules = @($inventory.modules | Where-Object { $_.module_path -cne ':core:data' }) }
        'inventory-compiler-boundary' { (@($inventory.modules | Where-Object { $_.module_path -eq ':app' })[0]).compiler_tasks = @(':app:compileDebugKotlin') }
        'inventory-output-boundary' { (@($inventory.modules | Where-Object { $_.module_path -eq ':app' })[0]).output_directories = @('../outside/classes') }
        'inventory-unproven-exclusion' { (@($inventory.modules | Where-Object { $_.module_path -eq ':app' })[0]).excluded_class_patterns += 'com/chimali/HandWritten.class' }
    }
    $inventory | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $inventoryPath -Encoding utf8
    $inventoryDigest = Get-FileSha256 -Path $inventoryPath
    $counters.inventory_digest = $inventoryDigest
    foreach ($proof in @($counters.zero_count_proofs)) { $proof.inventory_digest = $inventoryDigest }
    foreach ($proof in @($manifest.zero_count_proofs)) { $proof.inventory_digest = $inventoryDigest }
    $manifest.production_inventory_digest = $inventoryDigest
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
    } elseif ($Case -eq 'execution-security-file-substitution') {
        $securityCounter = @($counters.modules | Where-Object { $_.module_path -eq ':core:security' })
        $securityManifestInput = @($manifest.execution_files | Where-Object { $_.scope -eq ':core:security' } | Select-Object -First 1)
        if ($securityCounter.Count -ne 1 -or -not $securityCounter[0].execution_compatibility -or $securityManifestInput.Count -ne 1) {
            throw 'Source bundle must contain one :core:security counter, compatibility receipt, and manifest execution input'
        }
        $securityManifestInput = $securityManifestInput[0]
        $substitutedDigest = if ($securityManifestInput.source_sha256 -ceq ('a' * 64)) { 'b' * 64 } else { 'a' * 64 }
        $securityManifestInput.source_sha256 = $substitutedDigest
        foreach ($fileInput in @($securityCounter[0].execution_files) + @($securityCounter[0].execution_compatibility.execution_files)) {
            if ([string]$fileInput.repository_path -ceq [string]$securityManifestInput.source_path) { $fileInput.sha256 = $substitutedDigest }
        }
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
            'comparison-failed-junit' {
                $testArtifact = @($baselineManifest.artifacts | Where-Object { ([string]$_.path).Replace('\', '/') -match '^reports/tests/.+\.xml$' } | Select-Object -First 1)[0]
                if (-not $testArtifact) { throw 'Baseline fixture has no JUnit artifact to mutate' }
                [IO.File]::WriteAllText((Join-Path $baseline ([string]$testArtifact.path)), '<testsuite name="hidden" tests="1" failures="0" errors="0" skipped="0"><testcase name="hidden"><failure message="injected"/></testcase></testsuite>', [Text.UTF8Encoding]::new($false))
                $testArtifact.sha256 = Get-FileSha256 -Path (Join-Path $baseline ([string]$testArtifact.path))
            }
            'comparison-dirty-preflight' { $baselineManifest.git.clean_preflight = $false }
            'comparison-dirty-harness' { $baselineManifest.harness.clean = $false }
            'comparison-security-file-substitution' {
                $baselineCountersPath = Join-Path $baseline 'counters.json'
                $baselineCounters = Read-SafeJson -Path $baselineCountersPath
                $securityModule = @($baselineCounters.modules | Where-Object { $_.module_path -eq ':core:security' })
                $securityManifestInput = @($baselineManifest.execution_files | Where-Object { $_.scope -eq ':core:security' } | Select-Object -First 1)
                if ($securityModule.Count -ne 1 -or -not $securityModule[0].execution_compatibility -or $securityManifestInput.Count -ne 1) {
                    throw 'Baseline bundle must contain one :core:security counter, compatibility receipt, and manifest execution input'
                }
                $securityManifestInput = $securityManifestInput[0]
                $substitutedDigest = if ($securityManifestInput.source_sha256 -ceq ('a' * 64)) { 'b' * 64 } else { 'a' * 64 }
                $securityManifestInput.source_sha256 = $substitutedDigest
                foreach ($fileInput in @($securityModule[0].execution_files) + @($securityModule[0].execution_compatibility.execution_files)) {
                    if ([string]$fileInput.repository_path -ceq [string]$securityManifestInput.source_path) { $fileInput.sha256 = $substitutedDigest }
                }
                $baselineCounters | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $baselineCountersPath -Encoding utf8
                foreach ($artifactRecord in $baselineManifest.artifacts) {
                    if ([string]$artifactRecord.path -ceq 'counters.json') { $artifactRecord.sha256 = Get-FileSha256 -Path $baselineCountersPath }
                }
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
    $expectedExit = if ($Case -in @('missing-workload-task', 'zero-required-device', 'unowned-junit-artifact')) { 3 } else { 1 }
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
        'dirty-preflight' { 'Source and reviewed harness cleanliness were not proved before and after collection' }
        'dirty-harness' { 'Source and reviewed harness cleanliness were not proved before and after collection' }
        'harness-changed-during-capture' { 'Source and reviewed harness cleanliness were not proved before and after collection' }
        'missing-workload-task' { 'Host workload task inventory differs from trusted policy' }
        'zero-required-device' { 'Required workload task has no actual testcase outcomes' }
        'inventory-unapproved-variant' { 'selected variant .* is not the reviewed variant|selected variant mismatch' }
        'inventory-omitted-owner' { 'inventory module ownership differs from reviewed policy' }
        'inventory-compiler-boundary' { 'compiler task ownership differs from reviewed variant' }
        'inventory-output-boundary' { 'compiler outputs outside its project build boundary' }
        'inventory-unproven-exclusion' { 'generated exclusions do not exactly match their retained generated source evidence' }
        'unowned-junit-artifact' { 'Retained JUnit artifact is not owned by a recorded workload' }
        'extra-counter-scope' { 'Run counter module :feature:unaccounted is absent from production inventory' }
        'missing-inventory-counter' { 'Run production inventory module .* has no canonical counter record' }
        'contradictory-applicability' { ':core:common: inventory and canonical counter dispositions disagree' }
        'execution-class-mismatch' { ':core:common execution compatibility analyzed class is absent from retained inventory: fixture/not-retained.class' }
        'execution-device-file-mismatch' { ':core:common execution compatibility file binding differs from canonical execution attribution' }
        'execution-security-file-substitution' { 'Execution-file attribution has invalid scope, relative paths, kind, or source/bundle digest binding' }
        'junit-hidden-failure' { 'declared failures=0 but testcase nodes derive 1' }
        'junit-hidden-error' { 'declared errors=0 but testcase nodes derive 1' }
        'junit-hidden-skip' { 'declared skipped=0 but testcase nodes derive 1' }
        'junit-inflated-count' { 'declared tests=2 but testcase nodes derive 1' }
        'junit-contradictory-total' { 'testsuites declared tests=2 but testcase nodes derive 1' }
        'comparison-failed-junit' { 'declared failures=0 but contains 1' }
        'comparison-dirty-preflight' { 'Baseline does not prove clean source and reviewed harness state throughout capture' }
        'comparison-dirty-harness' { 'Baseline does not prove clean source and reviewed harness state throughout capture' }
        'comparison-security-file-substitution' { 'Baseline Execution-file attribution has invalid scope, relative paths, kind, or source/bundle digest binding' }
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
