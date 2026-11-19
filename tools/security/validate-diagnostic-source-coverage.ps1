[CmdletBinding()]
param(
    [string]$ReportPath = 'specs/065-security-secret-cleanup/evidence/diagnostic-source-census.md'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$inventoryPath = Join-Path $repositoryRoot 'specs/065-security-secret-cleanup/source-inventory.json'
$assessmentPath = Join-Path $repositoryRoot 'specs/065-security-secret-cleanup/assessment.md'
$tasksPath = Join-Path $repositoryRoot 'specs/065-security-secret-cleanup/tasks.md'
$absoluteReportPath = Join-Path $repositoryRoot $ReportPath

$sinkPattern = @(
    'Logger\.(?:v|d|i|w|e|log)\b'
    'android\.util\.Log\.(?:v|d|i|w|e|println)\b'
    '\bLog\.(?:v|d|i|w|e|println)\s*\('
    'System\.(?:out|err)'
    '\bprintln\s*\('
    '\bNSLog\s*\('
    '\bos_log\s*\('
    '\bTimber\.(?:v|d|i|w|e)\b'
) -join '|'

$sourcePaths = @(
    & rg --files `
        (Join-Path $repositoryRoot 'app') `
        (Join-Path $repositoryRoot 'core') `
        (Join-Path $repositoryRoot 'feature')
)
if ($LASTEXITCODE -gt 1) { throw 'Could not enumerate Git-visible application source files.' }
$sourceFiles = @(
    $sourcePaths |
        Where-Object {
            $_ -match '[\\/]src[\\/](?:main|androidMain|commonMain|iosMain|jvmMain|nativeMain)[\\/]' -and
            $_ -match '\.(?:kt|java|swift|m|mm)$' -and
            $_ -notmatch '[\\/](?:androidHostTest|androidDeviceTest|androidTest|commonTest|jvmTest|test|tests)[\\/]' -and
            $_ -notmatch '(?:Test|Tests)\.(?:kt|java)$'
        }
)

$matchesByPath = @{}
foreach ($path in $sourceFiles) {
    $matches = @(Select-String -LiteralPath $path -Pattern $sinkPattern)
    if ($matches.Count -eq 0) { continue }

    $relativePath = $path.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
    $matchesByPath[$relativePath] = $matches
}

$inventory = Get-Content -LiteralPath $inventoryPath -Raw | ConvertFrom-Json
$usageById = @{}
foreach ($usage in $inventory.usages) { $usageById[$usage.usage_id] = $usage }
$fileByPath = @{}
foreach ($file in $inventory.files) { $fileByPath[$file.path] = $file }
$assessmentText = Get-Content -LiteralPath $assessmentPath -Raw
$taskDefinitionText = (Select-String -LiteralPath $tasksPath -Pattern '^\s*- \[[xX ]\].*' | ForEach-Object Line) -join "`n"
$crosswalkHeading = '### T007 current inventory finding-to-task/story crosswalk'
$crosswalkStart = $assessmentText.IndexOf($crosswalkHeading, [System.StringComparison]::Ordinal)
$crosswalkEnd = if ($crosswalkStart -ge 0) {
    $assessmentText.IndexOf('### T016 current US1 coverage reconciliation', $crosswalkStart, [System.StringComparison]::Ordinal)
} else {
    -1
}
$crosswalkBlock = if ($crosswalkStart -ge 0) {
    if ($crosswalkEnd -gt $crosswalkStart) {
        $assessmentText.Substring($crosswalkStart, $crosswalkEnd - $crosswalkStart)
    } else {
        $assessmentText.Substring($crosswalkStart)
    }
} else {
    ''
}
$crosswalkRows = ($crosswalkBlock -split "`r?`n" | Where-Object { $_ -match '^\|' }) -join "`n"

$errors = [System.Collections.Generic.List[string]]::new()
$rows = [System.Collections.Generic.List[string]]::new()
$allFindingIds = [System.Collections.Generic.HashSet[string]]::new()
if ($crosswalkStart -lt 0) { $errors.Add('Assessment is missing the T007 finding-to-task/story crosswalk.') }
foreach ($path in ($matchesByPath.Keys | Sort-Object)) {
    $inventoryFile = $fileByPath[$path]
    $usageIds = @()
    if ($null -eq $inventoryFile) {
        $errors.Add("Missing source-inventory file record: $path")
    } else {
        $usageIds = @($inventoryFile.usage_ids)
        if ($inventoryFile.disposition -ne 'Relevant') {
            $errors.Add("Diagnostic source is not classified Relevant: $path ($($inventoryFile.disposition))")
        }
        if ($usageIds.Count -eq 0) {
            $errors.Add("Diagnostic source has no usage links: $path")
        }
        foreach ($usageId in $usageIds) {
            if (-not $usageById.ContainsKey($usageId)) {
                $errors.Add("Broken usage link $usageId from $path")
            } elseif ($usageById[$usageId].inventory_file -ne $path) {
                $errors.Add("Usage link $usageId points to $($usageById[$usageId].inventory_file), not $path")
            }
        }
    }

    $findingIds = @(
        foreach ($usageId in $usageIds) {
            if ($usageById.ContainsKey($usageId)) { $usageById[$usageId].finding_ids }
        }
    ) | Sort-Object -Unique
    foreach ($findingId in $findingIds) {
        [void]$allFindingIds.Add($findingId)
        if (-not $taskDefinitionText.Contains($findingId) -and -not $crosswalkRows.Contains($findingId)) {
            $errors.Add("Finding $findingId has no task or T007 crosswalk assignment.")
        }
    }
    $lineNumbers = @($matchesByPath[$path] | ForEach-Object LineNumber | Sort-Object -Unique)
    $rows.Add("| ``$path`` | $($matchesByPath[$path].Count) | $($lineNumbers -join ', ') | $($usageIds -join ', ') | $($findingIds -join ', ') |")
}

$date = Get-Date -Format 'yyyy-MM-dd'
$report = @(
    '# Diagnostic production-source census'
    ''
    "- Date: $date"
    '- Scope: current source files under `app`, `core` and `feature`, limited to production Kotlin, Java, Swift and Objective-C source sets; test source sets excluded.'
    "- Static token hits: $(@($matchesByPath.Values | ForEach-Object Count | Measure-Object -Sum).Sum) matches across $($matchesByPath.Count) source files; $($sourceFiles.Count) production source files enumerated."
    "- Finding assignment: $($allFindingIds.Count) distinct linked findings; each appears in a task checkbox row or a T007 crosswalk table row."
    '- Tokens searched: Kermit `Logger` level calls; Android `Log` calls and `println`; `System.out`/`System.err`; Kotlin/Java `println`; `NSLog`; `os_log`; and Timber level calls.'
    '- Interpretation: this syntactic census includes imports, comments, examples, and non-reachable branches where they match. It establishes source-file to inventory-link coverage only; it does not classify each hit as an actual emission, prove reachability, or establish destination behavior.'
    "- Inventory and finding-assignment errors: $($errors.Count)"
    ''
    '| Production source | Raw hits | Matching line numbers | Linked usage IDs | Linked finding IDs |'
    '|---|---:|---|---|---|'
    $rows
    ''
)

$reportDirectory = Split-Path -Parent $absoluteReportPath
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
[System.IO.File]::WriteAllText($absoluteReportPath, ($report -join "`r`n"), [System.Text.UTF8Encoding]::new($false))

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    Write-Error "Diagnostic source census found $($errors.Count) inventory or finding-assignment error(s)."
    exit 1
}

Write-Output "ProductionFiles=$($sourceFiles.Count) DiagnosticFiles=$($matchesByPath.Count) RawHits=$(@($matchesByPath.Values | ForEach-Object Count | Measure-Object -Sum).Sum) Findings=$($allFindingIds.Count) ValidationErrors=0 Report=$absoluteReportPath"
