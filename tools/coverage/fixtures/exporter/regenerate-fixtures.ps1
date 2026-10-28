[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$fixtureDir = Join-Path $root 'tools/coverage/fixtures/exporter'
$sourceClass = Join-Path $root 'feature/fido2/build/classes/kotlin/android/main/com/chimali/fido2/domain/model/PublicKeyCredentialType.class'
if (-not (Test-Path $sourceClass)) {
    throw 'Compile :feature:fido2:compileReleaseKotlin before regenerating JaCoCo exporter fixtures.'
}

$cacheRoots = @(
    $env:GRADLE_USER_HOME,
    (Join-Path $env:USERPROFILE '.gradle'),
    'D:\packages\gradle'
) | Where-Object { $_ -and (Test-Path $_) } | ForEach-Object { Join-Path $_ 'caches/modules-2/files-2.1' } | Where-Object { Test-Path $_ }
function Find-CachedJar([string]$Group, [string]$Artifact, [string]$Version, [string]$Name) {
    foreach ($cacheRoot in $cacheRoots) {
        $artifactRoot = Join-Path $cacheRoot "$Group/$Artifact/$Version"
        if (Test-Path $artifactRoot) {
            $jar = Get-ChildItem $artifactRoot -Filter $Name -Recurse -File | Select-Object -First 1
            if ($jar) { return $jar.FullName }
        }
    }
    throw "Required cached dependency $Group`:$Artifact`:$Version was not found. Run the repository Gradle coverage setup first."
}

$coreJar = Find-CachedJar 'org.jacoco' 'org.jacoco.core' '0.8.15' 'org.jacoco.core-0.8.15.jar'
$reportJar = Find-CachedJar 'org.jacoco' 'org.jacoco.report' '0.8.15' 'org.jacoco.report-0.8.15.jar'
$asmJars = @()
foreach ($artifact in @('asm', 'asm-tree', 'asm-commons', 'asm-analysis', 'asm-util')) {
    $candidates = foreach ($cacheRoot in $cacheRoots) {
        $artifactRoot = Join-Path $cacheRoot "org.ow2.asm/$artifact"
        if (Test-Path $artifactRoot) {
            Get-ChildItem $artifactRoot -Filter "$artifact-*.jar" -Recurse -File |
                Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' }
        }
    }
    $selected = $candidates | Sort-Object { [version]($_.Directory.Parent.Name) } -Descending | Select-Object -First 1
    if (-not $selected) { throw "Required cached JaCoCo dependency org.ow2.asm:$artifact was not found." }
    $asmJars += $selected.FullName
}

$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ('jacoco-fixtures-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
    $java = Get-Command java.exe -ErrorAction Stop
    $javac = Get-Command javac.exe -ErrorAction Stop
    $classPath = (@($coreJar, $reportJar) + $asmJars) -join [IO.Path]::PathSeparator
    & $javac.Source -cp $classPath -d $temporary (Join-Path $fixtureDir 'FixtureReportGenerator.java')
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the JaCoCo fixture report generator.' }
    $branchlessStats = (& $java.Source -cp "$temporary$([IO.Path]::PathSeparator)$classPath" FixtureReportGenerator $sourceClass 'fido2-branchless' (Join-Path $fixtureDir 'branchless.xml') | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'JaCoCo could not export the branchless fixture.' }
    $emptyStats = (& $java.Source -cp "$temporary$([IO.Path]::PathSeparator)$classPath" FixtureReportGenerator '-' 'authenticator-empty' (Join-Path $fixtureDir 'empty.xml') | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'JaCoCo could not export the empty-scope fixture.' }
} finally {
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    $tempTarget = [System.IO.Path]::GetFullPath($temporary)
    if ([System.IO.Path]::GetDirectoryName($tempTarget) -ne $tempRoot -or [System.IO.Path]::GetFileName($tempTarget) -notlike 'jacoco-fixtures-*') {
        throw "Refusing to remove unexpected fixture-generation path: $tempTarget"
    }
    Remove-Item -LiteralPath $tempTarget -Recurse -Force
}

function Get-Sha256Text([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { [Convert]::ToHexString($sha.ComputeHash([Text.UTF8Encoding]::new($false).GetBytes($Value))).ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Get-Sha256File([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }

$classId = 'com/chimali/fido2/domain/model/PublicKeyCredentialType.class'
$classDigest = Get-Sha256Text "$classId`:$((Get-Sha256File $sourceClass))"
$emptyClassDigest = Get-Sha256Text ''
$branchlessMatch = [regex]::Match($branchlessStats, '^instructions=(\d+)/(\d+) branches=(\d+)/(\d+) classes=(\d+)$')
$emptyMatch = [regex]::Match($emptyStats, '^instructions=(\d+)/(\d+) branches=(\d+)/(\d+) classes=(\d+)$')
if (-not $branchlessMatch.Success -or -not $emptyMatch.Success) { throw 'JaCoCo fixture exporter returned malformed analysis counters.' }
$instructionTotal = [int]$branchlessMatch.Groups[2].Value
$instructionCovered = [int]$branchlessMatch.Groups[1].Value
if ($instructionTotal -le 0 -or [int]$branchlessMatch.Groups[4].Value -ne 0 -or [int]$branchlessMatch.Groups[5].Value -ne 1) {
    throw 'The selected fixture class must have executable instructions, no branches, and one retained class.'
}
if ([int]$emptyMatch.Groups[2].Value -ne 0 -or [int]$emptyMatch.Groups[4].Value -ne 0 -or [int]$emptyMatch.Groups[5].Value -ne 0) {
    throw 'The empty fixture must have no executable instructions, branches, or retained classes.'
}
$inventoryPath = Join-Path $fixtureDir 'production-inventory.json'
$inventory = [ordered]@{
    schema_version = '1.0'
    generated_from = 'JaCoCo 0.8.15 Analyzer + XMLFormatter'
    modules = @(
        [ordered]@{ module_path = ':feature:fido2'; project_directory = 'feature/fido2'; disposition = 'Applicable'; retained_classes = @([ordered]@{ class_id = $classId; sha256 = (Get-Sha256File $sourceClass) }); class_inventory_digest = $classDigest },
        [ordered]@{ module_path = ':feature:authenticator'; project_directory = 'feature/authenticator'; disposition = 'NotApplicable'; retained_classes = @(); class_inventory_digest = $emptyClassDigest }
    )
}
Set-Content -LiteralPath $inventoryPath -Value (ConvertTo-Json $inventory -Depth 8) -Encoding utf8
$inventoryDigest = Get-Sha256File $inventoryPath
$fixtureCasesPath = Join-Path $root 'tools/coverage/fixtures/inventory.json'
$fixtureCases = Get-Content -LiteralPath $fixtureCasesPath -Raw | ConvertFrom-Json
foreach ($caseId in @('INV_004_BRANCHLESS_WITH_VALID_PROOF', 'INV_007_EMPTY_WITH_VALID_PROOF')) {
    $case = $fixtureCases.cases | Where-Object id -eq $caseId
    $isBranchless = $caseId.StartsWith('INV_004')
    $modulePath = if ($isBranchless) { ':feature:fido2' } else { ':feature:authenticator' }
    $reportName = if ($isBranchless) { 'branchless.xml' } else { 'empty.xml' }
    $reportDigest = Get-Sha256File (Join-Path $fixtureDir $reportName)
    $instructions = if ($isBranchless) { [ordered]@{ covered = $instructionCovered; missed = ($instructionTotal - $instructionCovered); total = $instructionTotal } } else { [ordered]@{ covered = 0; missed = 0; total = 0 } }
    $branches = [ordered]@{ covered = 0; missed = 0; total = 0 }
    $retainedClasses = if ($isBranchless) { ,@([ordered]@{ class_id = $classId; sha256 = (Get-Sha256File $sourceClass) }) } else { ,([object[]]@()) }
    $disposition = if ($isBranchless) { 'Applicable' } else { 'NotApplicable' }
    $proof = [ordered]@{
        normalization_rule = 'AnalyzedZeroProofV1'; module_path = $modulePath; analyzer_identity = 'jacoco-0.8.15'
        report_file = $reportName; report_digest = $reportDigest; inventory_digest = $inventoryDigest
        class_inventory_digest = $(if ($isBranchless) { $classDigest } else { $emptyClassDigest })
        counters = [ordered]@{ INSTRUCTION = $instructions; BRANCH = $branches }
    }
    $proofPath = Join-Path $fixtureDir ($reportName -replace '\.xml$', '_proof.json')
    Set-Content -LiteralPath $proofPath -Value (ConvertTo-Json $proof -Depth 8) -Encoding utf8
    $case | Add-Member -Force -NotePropertyName canonical_binding -NotePropertyValue ([ordered]@{
        inventory_module = [ordered]@{ module_path = $modulePath; disposition = $disposition; retained_classes = $retainedClasses; class_inventory_digest = $proof.class_inventory_digest }
        counter_module = [ordered]@{ class_inventory_digest = $proof.class_inventory_digest; report_digest = $reportDigest; instruction = @{ covered = $instructions.covered; missed = $instructions.missed }; branch = @{ covered = 0; missed = 0 } }
        inventory_digest = $inventoryDigest; expected_inventory_digest = $inventoryDigest; analyzer_identity = 'jacoco-0.8.15'; report_digest = $reportDigest; proof = $proof
    })
}
$branchlessCase = $fixtureCases.cases | Where-Object id -eq 'INV_004_BRANCHLESS_WITH_VALID_PROOF'
$emptyCase = $fixtureCases.cases | Where-Object id -eq 'INV_007_EMPTY_WITH_VALID_PROOF'
$positiveOmissionCase = $fixtureCases.cases | Where-Object id -eq 'INV_008_NONZERO_PROOF_FOR_OMITTED_COUNTER_FAILS'
$positiveProof = $branchlessCase.canonical_binding.proof | ConvertTo-Json -Depth 8 | ConvertFrom-Json
$positiveProof.counters.BRANCH.covered = 1
$positiveProof.counters.BRANCH.missed = 1
$positiveProof.counters.BRANCH.total = 2
$positiveBinding = $branchlessCase.canonical_binding | ConvertTo-Json -Depth 12 | ConvertFrom-Json
$positiveBinding.counter_module.branch.covered = 1
$positiveBinding.counter_module.branch.missed = 1
$positiveBinding.proof = $positiveProof
$positiveOmissionCase.proof = $positiveProof
$positiveOmissionCase | Add-Member -Force -NotePropertyName canonical_binding -NotePropertyValue $positiveBinding
$emptyInventoryModule = $emptyCase.canonical_binding.inventory_module
$emptyCounterModule = $emptyCase.canonical_binding.counter_module
$emptyBinding = $emptyCase.canonical_binding
$inventoryMismatchCase = $fixtureCases.cases | Where-Object id -eq 'INV_010_INVENTORY_FILE_DIGEST_MISMATCH_FAILS'
$inventoryMismatchCase | Add-Member -Force -NotePropertyName canonical_binding -NotePropertyValue ([ordered]@{ inventory_module = $emptyInventoryModule; counter_module = $emptyCounterModule; inventory_digest = $inventoryDigest; expected_inventory_digest = (Get-Sha256Text 'different expected inventory'); analyzer_identity = 'jacoco-0.8.15'; report_digest = $emptyBinding.report_digest })
$proofMismatchCase = $fixtureCases.cases | Where-Object id -eq 'INV_011_ZERO_PROOF_INVENTORY_MISMATCH_FAILS'
$mismatchedProof = $emptyBinding.proof | ConvertTo-Json -Depth 8 | ConvertFrom-Json
$mismatchedProof.inventory_digest = Get-Sha256Text 'replayed proof inventory'
$proofMismatchCase | Add-Member -Force -NotePropertyName canonical_binding -NotePropertyValue ([ordered]@{ inventory_module = $emptyInventoryModule; counter_module = $emptyCounterModule; inventory_digest = $inventoryDigest; expected_inventory_digest = $inventoryDigest; analyzer_identity = 'jacoco-0.8.15'; report_digest = $emptyBinding.report_digest; proof = $mismatchedProof })
Set-Content -LiteralPath $fixtureCasesPath -Value (ConvertTo-Json $fixtureCases -Depth 30) -Encoding utf8
