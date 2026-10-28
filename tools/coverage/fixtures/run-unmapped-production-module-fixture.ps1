[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RepositoryRoot)
$temp = Join-Path ([IO.Path]::GetTempPath()) ('coverage-unmapped-production-' + [guid]::NewGuid().ToString('N'))
$moduleRoot = Join-Path $temp 'module'
$initScript = Join-Path $temp 'include-unmapped.init.gradle'
New-Item -ItemType Directory -Path (Join-Path $moduleRoot 'src/main/kotlin') -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $moduleRoot 'build.gradle'), "// isolated unmapped-production fixture`n", [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $moduleRoot 'src/main/kotlin/UnmappedFixture.kt'), "package fixture`nclass UnmappedFixture`n", [Text.UTF8Encoding]::new($false))
@'
gradle.settingsEvaluated { settings ->
    settings.include(':spec058UnmappedFixture')
    settings.project(':spec058UnmappedFixture').projectDir = new File(System.getProperty('coverage.fixture.moduleDir'))
}
'@ | Set-Content -LiteralPath $initScript -Encoding utf8

try {
    Push-Location $root
    try {
        $gradleOutput = @(& .\gradlew.bat --no-configuration-cache "-Dcoverage.fixture.moduleDir=$moduleRoot" `
            -I (Join-Path $root 'tools/hdk-coverage.init.gradle') -I $initScript help 2>&1)
        $gradleExit = $LASTEXITCODE
    } finally { Pop-Location }
    $outputText = $gradleOutput -join "`n"
    if ($gradleExit -eq 0 -or $outputText -notmatch 'Unmapped production modules have no reviewed production_scope\.variants ownership' -or
        $outputText -notmatch ':spec058UnmappedFixture \[src/main/kotlin\]') {
        throw "Expected the Gradle coverage collector to reject the unmapped production source module; exit=$gradleExit`n$outputText"
    }
    Write-Output 'Production collector rejected an included source module without reviewed variant ownership.'
    Write-Output (($outputText -split "`n" | Where-Object { $_ -match 'Unmapped production modules have no reviewed production_scope\.variants ownership' } | Select-Object -First 1).Trim())
} finally {
    $resolvedTemp = [IO.Path]::GetFullPath($temp)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if ([IO.Path]::GetDirectoryName($resolvedTemp) -cne $tempRoot -or [IO.Path]::GetFileName($resolvedTemp) -notlike 'coverage-unmapped-production-*') {
        throw "Refusing unsafe unmapped production fixture cleanup target: $resolvedTemp"
    }
    if ([IO.Directory]::Exists($resolvedTemp)) { [IO.Directory]::Delete($resolvedTemp, $true) }
}
