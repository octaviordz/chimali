[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
. (Join-Path ([System.IO.Path]::GetFullPath($RepositoryRoot)) 'tools/coverage/coverage-common.ps1')
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-changed-paths-' + [guid]::NewGuid().ToString('N'))
$repo = Join-Path $tempRoot 'repo'
New-Item -ItemType Directory -Path $repo -Force | Out-Null

function Invoke-FixtureGit {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = @(& git -C $repo @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed: $($output -join ' ')" }
    return $output
}

function Write-FixtureFile {
    param([string]$RelativePath, [string]$Contents)
    $fullPath = Join-Path $repo $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
    New-Item -ItemType Directory -Path (Split-Path -Parent $fullPath) -Force | Out-Null
    [IO.File]::WriteAllText($fullPath, $Contents, [Text.UTF8Encoding]::new($false))
}

try {
    Invoke-FixtureGit @('init', '--quiet') | Out-Null
    Invoke-FixtureGit @('config', 'user.name', 'Coverage Fixture') | Out-Null
    Invoke-FixtureGit @('config', 'user.email', 'coverage-fixture@example.invalid') | Out-Null
    $unicodePath = 'core/security/src/androidHostTest/kotlin/Prueba ñ Test.kt'
    $spacePath = 'core/domain/src/commonTest/kotlin/Domain Boundary Test.kt'
    $deletedPath = 'core/data/src/commonTest/kotlin/Deleted Test.kt'
    $oldOwnerPath = 'core/security/src/commonTest/kotlin/Move Source.kt'
    $newOwnerPath = 'feature/fido2/src/commonTest/kotlin/Move Destination.kt'
    $tier2Path = 'core/bluetooth/src/test/kotlin/Bluetooth Gate Test.kt'
    $unaffectedPath = 'docs/notes and plans.md'

    foreach ($path in @($unicodePath, $spacePath, $deletedPath, $oldOwnerPath, $tier2Path, $unaffectedPath)) {
        Write-FixtureFile -RelativePath $path -Contents 'baseline'
    }
    Invoke-FixtureGit @('add', '-A') | Out-Null
    Invoke-FixtureGit @('commit', '--quiet', '-m', 'fixture base') | Out-Null
    $target = ([string](Invoke-FixtureGit @('rev-parse', 'HEAD'))).Trim()

    Write-FixtureFile -RelativePath $unicodePath -Contents 'changed unicode'
    Write-FixtureFile -RelativePath $spacePath -Contents 'changed spaced path'
    Remove-Item -LiteralPath (Join-Path $repo $deletedPath.Replace('/', [IO.Path]::DirectorySeparatorChar)) -Force
    Remove-Item -LiteralPath (Join-Path $repo $oldOwnerPath.Replace('/', [IO.Path]::DirectorySeparatorChar)) -Force
    Write-FixtureFile -RelativePath $newOwnerPath -Contents 'moved across module owner'
    Write-FixtureFile -RelativePath $tier2Path -Contents 'changed tier two'
    Write-FixtureFile -RelativePath $unaffectedPath -Contents 'ordinary documentation change'
    Invoke-FixtureGit @('add', '-A') | Out-Null
    Invoke-FixtureGit @('commit', '--quiet', '-m', 'fixture candidate') | Out-Null
    $source = ([string](Invoke-FixtureGit @('rev-parse', 'HEAD'))).Trim()

    $actual = @(Get-GitChangedPaths -RepositoryRoot $repo -TargetRevision $target -SourceRevision $source)
    $expected = @($unicodePath, $spacePath, $deletedPath, $oldOwnerPath, $newOwnerPath, $tier2Path, $unaffectedPath)
    if ($actual.Count -ne $expected.Count) { throw "Git collector returned $($actual.Count) paths; expected $($expected.Count): $($actual -join ' | ')" }
    foreach ($expectedPath in $expected) {
        if (@($actual | Where-Object { [string]$_ -ceq $expectedPath }).Count -ne 1) { throw "Git collector did not preserve exact path: $expectedPath" }
    }

    $receiptPath = Join-Path $tempRoot 'changed-paths.txt'
    Write-ChangedPathReceipt -Path $receiptPath -TargetCommit $target -SourceCommit $source -ChangedPaths $actual
    $receipt = Read-ChangedPathReceipt -Path $receiptPath -ExpectedTargetCommit $target -ExpectedSourceCommit $source
    if (-not $receipt.IsValid -or $receipt.Paths.Count -ne $actual.Count) { throw "Receipt did not round-trip: $($receipt.Errors -join '; ')" }
    foreach ($path in $actual) {
        if (@($receipt.Paths | Where-Object { [string]$_ -ceq [string]$path }).Count -ne 1) { throw "Receipt did not preserve path exactly: $path" }
    }
    if ((Get-FileSha256 -Path $receiptPath) -notmatch '^[0-9a-f]{64}$') { throw 'Changed-path receipt did not produce a SHA-256 digest' }
    $wrongBinding = Read-ChangedPathReceipt -Path $receiptPath -ExpectedTargetCommit $source -ExpectedSourceCommit $source
    if ($wrongBinding.IsValid -or -not ($wrongBinding.Errors -join '; ').Contains('target/source commit binding')) { throw 'Receipt with a mismatched target binding was not rejected' }

    $policy = [PSCustomObject]@{ tier1_modules = @(':core:security', ':core:domain', ':core:data', ':feature:fido2', ':feature:authenticator'); tier2_gated_modules = @(':core:bluetooth') }
    $inventory = [PSCustomObject]@{ modules = @([PSCustomObject]@{ module_path = ':core:bluetooth'; tier = 2; project_directory = 'core/bluetooth' }) }
    $tier1Owners = @(Get-AffectedTier1Modules -Policy $policy -ChangedPaths $receipt.Paths -ChangedPathsProvided $true)
    foreach ($owner in @(':core:security', ':core:domain', ':core:data', ':feature:fido2')) {
        if ($owner -notin $tier1Owners) { throw "Expected changed Tier 1 owner was not selected: $owner" }
    }
    if (':feature:authenticator' -in $tier1Owners) { throw 'An unchanged Tier 1 owner was selected by the path set' }
    $tier2Owners = @(Get-AffectedTier2Modules -Policy $policy -Inventory $inventory -ChangedPaths $receipt.Paths -ChangedPathsProvided $true)
    if ($tier2Owners.Count -ne 1 -or $tier2Owners[0] -cne ':core:bluetooth') { throw 'The changed Tier 2 owner was not selected' }

    $unaffectedTier1 = @(Get-AffectedTier1Modules -Policy $policy -ChangedPaths @($unaffectedPath) -ChangedPathsProvided $true)
    $unaffectedTier2 = @(Get-AffectedTier2Modules -Policy $policy -Inventory $inventory -ChangedPaths @($unaffectedPath) -ChangedPathsProvided $true)
    if ($unaffectedTier1.Count -ne 0 -or $unaffectedTier2.Count -ne 0) { throw 'A genuinely unaffected documentation path selected a module gate' }
    $malformed = Test-ChangedPathRecords -Paths @('core/../outside/file.kt')
    if ($malformed.IsValid) { throw 'Malformed traversal path was accepted as an unaffected change' }

    Write-Output 'Lossless Git changed-path collection, JSON round-trip, target/source binding, Tier 1/Tier 2 ownership, rename/deletion coverage, and unaffected-path controls passed.'
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}
