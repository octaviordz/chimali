[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RepositoryRoot)
$module = Join-Path $root 'core/security'
$classRoot = Join-Path $module 'build/classes/kotlin/android/main'
$classFile = Get-ChildItem -LiteralPath $classRoot -Filter '*Impl.class' -Recurse -File |
    Where-Object { $_.FullName -notmatch '[\\/]org[\\/]koin[\\/]ksp[\\/]generated[\\/]' } | Select-Object -First 1
if (-not $classFile) { throw 'Compile :core:security:compileAndroidMain before this fixture.' }
$className = [IO.Path]::GetRelativePath($classRoot, $classFile.FullName).Replace('\', '/').Replace('.class', '')
$cacheRoot = 'D:\packages\gradle\caches\modules-2\files-2.1'
$coreJar = Get-ChildItem -LiteralPath (Join-Path $cacheRoot 'org.jacoco/org.jacoco.core/0.8.15') -Filter 'org.jacoco.core-0.8.15.jar' -Recurse -File | Select-Object -First 1 -ExpandProperty FullName
$asmJars = @()
foreach ($artifact in @('asm', 'asm-tree', 'asm-commons', 'asm-analysis', 'asm-util')) {
    $asmJars += Get-ChildItem -LiteralPath (Join-Path $cacheRoot "org.ow2.asm/$artifact") -Filter "$artifact-*.jar" -Recurse -File |
        Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } |
        Sort-Object { [version]$_.Directory.Parent.Name } -Descending | Select-Object -First 1 -ExpandProperty FullName
}
$classPath = (@($coreJar) + $asmJars) -join [IO.Path]::PathSeparator
$temp = Join-Path ([IO.Path]::GetTempPath()) ('coverage-probe-mismatch-' + [guid]::NewGuid().ToString('N'))
$fixtureExec = Join-Path $module 'build/jacoco/hdk/testAndroidHostTest.exec'
$deviceFixtureExec = Join-Path $module 'build/outputs/code_coverage/spec058-host-device-merge-fixture.ec'
$mismatchSource = Join-Path $temp 'mismatch.exec'
$deviceSource = Join-Path $temp 'device.exec'
$fixtureInit = Join-Path $temp 'install-execution-fixture.init.gradle'
$backup = Join-Path $temp 'original.exec'
$executionBackupRoot = Join-Path $temp 'execution-data-backup'
$executionRoots = @(@('app', 'core', 'feature') | ForEach-Object { Join-Path $root $_ } | Where-Object { Test-Path -LiteralPath $_ -PathType Container })
function Get-ExecutionDataPaths {
    Push-Location $root
    try {
        $paths = @(& rg.exe --files --hidden --no-ignore -g '*.exec' -g '*.ec' -- app core feature 2>$null)
        if ($LASTEXITCODE -gt 1) { throw 'Could not enumerate JaCoCo execution data for safe fixture restoration.' }
        return $paths
    } finally { Pop-Location }
}
$originalExecutionPaths = @(Get-ExecutionDataPaths)
$executionBackups = @()
foreach ($relativePath in $originalExecutionPaths) {
    $absolutePath = [IO.Path]::GetFullPath((Join-Path $root $relativePath))
    $relativeToRoot = [IO.Path]::GetRelativePath($root, $absolutePath)
    $backupPath = Join-Path $executionBackupRoot $relativeToRoot
    New-Item -ItemType Directory -Path (Split-Path -Parent $backupPath) -Force | Out-Null
    Copy-Item -LiteralPath $absolutePath -Destination $backupPath
    $executionBackups += [PSCustomObject]@{ RelativePath = $relativeToRoot; BackupPath = $backupPath; Sha256 = (Get-FileHash -LiteralPath $absolutePath -Algorithm SHA256).Hash }
}
$hadOriginal = Test-Path -LiteralPath $fixtureExec -PathType Leaf
$originalDigest = if ($hadOriginal) { (Get-FileHash -LiteralPath $fixtureExec -Algorithm SHA256).Hash } else { $null }
if (-not $hadOriginal) { throw 'A retained host test execution file is required before running this fixture.' }
if (Test-Path -LiteralPath $deviceFixtureExec) { throw "Refusing to overwrite existing device execution data: $deviceFixtureExec" }
$reportXml = Join-Path $module 'build/reports/hdk-coverage/hdkModuleCoverage.xml'
function Get-InstructionBranchTotals([string]$Path) {
    [xml]$xml = Get-Content -LiteralPath $Path -Raw
    $instruction = $xml.SelectSingleNode('/report/counter[@type="INSTRUCTION"]')
    $branch = $xml.SelectSingleNode('/report/counter[@type="BRANCH"]')
    if (-not $instruction -or -not $branch) { throw "Report lacks instruction or branch totals: $Path" }
    return "$($instruction.GetAttribute('missed'))/$($instruction.GetAttribute('covered'));$($branch.GetAttribute('missed'))/$($branch.GetAttribute('covered'))"
}
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    if ($hadOriginal) { Copy-Item -LiteralPath $fixtureExec -Destination $backup }
    @'
gradle.projectsEvaluated {
    def root = gradle.rootProject
    def modulePath = root.findProperty('coverageProbeModulePath')?.toString() ?: ':core:security'
    def module = root.findProject(modulePath)
    if (module == null) throw new GradleException("Probe fixture module ${modulePath} is missing")
    def copies = []
    def hostSource = root.findProperty('coverageProbeExecSource')?.toString()
    if (hostSource) {
        def testTaskName = root.findProperty('coverageProbeTestTaskName')?.toString() ?: 'testAndroidHostTest'
        def testTask = module.tasks.findByName(testTaskName)
        if (testTask == null) throw new GradleException("Probe fixture test task ${modulePath}:${testTaskName} is missing")
        def destination = testTask.extensions.getByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension).destinationFile
        copies.add([source: new File(hostSource), target: destination])
    }
    def deviceSource = root.findProperty('coverageProbeDeviceSource')?.toString()
    if (deviceSource) copies.add([source: new File(deviceSource), target: module.file('build/outputs/code_coverage/spec058-device-probe-fixture.ec')])
    if (!copies.isEmpty()) {
        def install = root.tasks.register('hdkInstallProbeExecutionFixture') {
            dependsOn(module.tasks.named('hdkPrepareEmptyCoverageData'))
            doLast {
                copies.each { item ->
                    if (!item.source.isFile()) throw new GradleException("Probe fixture source is missing: ${item.source}")
                    item.target.parentFile.mkdirs()
                    java.nio.file.Files.copy(item.source.toPath(), item.target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        module.tasks.named('hdkModuleCoverage').configure { dependsOn(install) }
    }
}
'@ | Set-Content -LiteralPath $fixtureInit -Encoding utf8
    $fixtureSource = Join-Path $root 'tools/coverage/fixtures/JaCoCoMismatchedExecutionDataWriter.java'
    & javac.exe -cp $classPath -d $temp $fixtureSource
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the mismatch fixture.' }
    foreach ($mode in @('class-id', 'probe-length')) {
        & java.exe -cp "$temp$([IO.Path]::PathSeparator)$classPath" JaCoCoMismatchedExecutionDataWriter $mode $mismatchSource $className $classFile.FullName
        if ($LASTEXITCODE -ne 0) { throw "JaCoCo did not validate the $mode mismatch fixture." }

        $gradleArgs = @(
            '--no-configuration-cache',
            '-I', (Join-Path $root 'tools/hdk-coverage.init.gradle'),
            '-I', $fixtureInit,
            "-PcoverageProbeExecSource=$mismatchSource",
            ':core:security:hdkModuleCoverage'
        )
        $output = & (Join-Path $root 'gradlew.bat') @gradleArgs 2>&1
        $exitCode = $LASTEXITCODE
        $text = $output -join "`n"
        if ($mode -eq 'class-id') {
            $matched = $text -match 'class-ID mismatches for measured production classes'
        } else {
            $matched = $text -match 'JaCoCo rejected incompatible execution data for production class file'
        }
        if ($exitCode -eq 0 -or -not $matched) {
            throw "Expected production report rejection for $mode incompatibility (exit $exitCode).`n$text"
        }
        Write-Output "PASS: production module report rejected JaCoCo $mode incompatibility."
        Write-Output (($text -split "`n" | Where-Object { $_ -match 'class-ID mismatches|JaCoCo rejected incompatible execution data' } | Select-Object -First 1).Trim())
    }

    Copy-Item -LiteralPath $backup -Destination $fixtureExec -Force
    if ((Get-FileHash -LiteralPath $fixtureExec -Algorithm SHA256).Hash -cne $originalDigest) { throw 'Could not restore the host execution file before merge validation.' }
    Copy-Item -LiteralPath $backup -Destination $deviceSource
    $hostSource = Join-Path $temp 'matching-host.exec'
    Copy-Item -LiteralPath $backup -Destination $hostSource
    $hostArgs = @(
        '--no-configuration-cache',
        '-I', (Join-Path $root 'tools/hdk-coverage.init.gradle'),
        '-I', $fixtureInit,
        "-PcoverageProbeExecSource=$hostSource",
        ':core:security:hdkModuleCoverage'
    )
    $hostOutput = & (Join-Path $root 'gradlew.bat') @hostArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Valid host execution data did not produce a report.`n$($hostOutput -join "`n")" }
    $beforeMergeTotals = Get-InstructionBranchTotals -Path $reportXml
    $mergeArgs = @(
        '--no-configuration-cache',
        '-I', (Join-Path $root 'tools/hdk-coverage.init.gradle'),
        '-I', $fixtureInit,
        "-PcoverageProbeExecSource=$hostSource",
        "-PcoverageProbeDeviceSource=$deviceSource",
        ':core:security:hdkModuleCoverage'
    )
    $mergeOutput = & (Join-Path $root 'gradlew.bat') @mergeArgs 2>&1
    $mergeExitCode = $LASTEXITCODE
    if ($mergeExitCode -ne 0) { throw "Valid host/device JaCoCo records did not merge (exit $mergeExitCode).`n$($mergeOutput -join "`n")" }
    $afterMergeTotals = Get-InstructionBranchTotals -Path $reportXml
    if ($afterMergeTotals -cne $beforeMergeTotals) { throw "Duplicate matching host/device contribution changed report denominators or counts: before=$beforeMergeTotals after=$afterMergeTotals" }
    Write-Output 'PASS: matching host/device execution data merged and counted each production class once.'

    $appClassRoot = Join-Path $root 'app/build/intermediates/built_in_kotlinc/shrunkDebug/compileShrunkDebugKotlin/classes'
    $appClassFile = Get-ChildItem -LiteralPath $appClassRoot -Filter 'AppNavGraphKt.class' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $appClassFile) { throw 'Compile :app:compileShrunkDebugKotlin before the shrunkDebug device-probe fixture.' }
    $appClassName = [IO.Path]::GetRelativePath($appClassRoot, $appClassFile.FullName).Replace('\', '/').Replace('.class', '')
    $appDeviceMismatch = Join-Path $temp 'shrunk-debug-device-mismatch.ec'
    foreach ($mode in @('class-id', 'probe-length')) {
        & java.exe -cp "$temp$([IO.Path]::PathSeparator)$classPath" JaCoCoMismatchedExecutionDataWriter $mode $appDeviceMismatch $appClassName $appClassFile.FullName
        if ($LASTEXITCODE -ne 0) { throw "JaCoCo did not validate the app shrunkDebug $mode device fixture." }
        $appArgs = @(
            '--no-configuration-cache',
            '-I', (Join-Path $root 'tools/hdk-coverage.init.gradle'),
            '-I', $fixtureInit,
            '-PcoverageProbeModulePath=:app',
            '-PcoverageProbeTestTaskName=testShrunkDebugUnitTest',
            "-PcoverageProbeDeviceSource=$appDeviceMismatch",
            ':app:hdkModuleCoverage'
        )
        $appOutput = & (Join-Path $root 'gradlew.bat') @appArgs 2>&1
        $appExitCode = $LASTEXITCODE
        $appText = $appOutput -join "`n"
        $appMatched = if ($mode -eq 'class-id') {
            $appText -match 'class-ID mismatches for measured production classes'
        } else {
            $appText -match 'JaCoCo rejected incompatible execution data for production class file'
        }
        if ($appExitCode -eq 0 -or -not $appMatched) {
            throw "Expected :app shrunkDebug production report rejection for device $mode incompatibility (exit $appExitCode).`n$appText"
        }
        Write-Output "PASS: :app shrunkDebug production report rejected device JaCoCo $mode incompatibility."
        Write-Output (($appText -split "`n" | Where-Object { $_ -match 'class-ID mismatches|JaCoCo rejected incompatible execution data' } | Select-Object -First 1).Trim())
    }
} finally {
    foreach ($relativePath in @(Get-ExecutionDataPaths)) {
        $absolutePath = [IO.Path]::GetFullPath((Join-Path $root $relativePath))
        Remove-Item -LiteralPath $absolutePath -Force -ErrorAction SilentlyContinue
    }
    foreach ($entry in $executionBackups) {
        $restorePath = Join-Path $root $entry.RelativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $restorePath) -Force | Out-Null
        Copy-Item -LiteralPath $entry.BackupPath -Destination $restorePath -Force
        if ((Get-FileHash -LiteralPath $restorePath -Algorithm SHA256).Hash -cne $entry.Sha256) { throw "Original JaCoCo execution file was not restored byte-for-byte: $($entry.RelativePath)" }
    }
    $canonicalTemp = [IO.Path]::GetFullPath($temp)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if ([IO.Path]::GetDirectoryName($canonicalTemp) -ne $tempRoot -or [IO.Path]::GetFileName($canonicalTemp) -notlike 'coverage-probe-mismatch-*') {
        throw "Refusing to remove unexpected fixture directory: $canonicalTemp"
    }
    Remove-Item -LiteralPath $canonicalTemp -Recurse -Force
}
