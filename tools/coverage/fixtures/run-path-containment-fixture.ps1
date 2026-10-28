[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('coverage-path-containment-entry-' + [guid]::NewGuid().ToString('N'))
$tempRootFull = [System.IO.Path]::GetFullPath($tempRoot)
$tempParent = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
$originalPath = $env:Path

function Write-FixtureFile([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-FixtureScript([string]$ScriptPath, [string[]]$Arguments) {
    $output = @(& pwsh -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1)
    return [PSCustomObject]@{ ExitCode = $LASTEXITCODE; Text = ($output -join "`n") }
}

function Get-TreeDigest([string]$Path) {
    $parts = @()
    foreach ($file in @(Get-ChildItem -LiteralPath $Path -Recurse -File -Force | Sort-Object FullName)) {
        $relative = [System.IO.Path]::GetRelativePath($Path, $file.FullName).Replace('\', '/')
        $digest = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $parts += "${relative}:$digest"
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($parts -join "`n"))
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

try {
    New-Item -ItemType Directory -Path $tempRootFull -Force | Out-Null
    $repo = Join-Path $tempRootFull 'SealedBundle'
    $fakeBin = Join-Path $tempRootFull 'fake-bin'
    New-Item -ItemType Directory -Path $repo,$fakeBin -Force | Out-Null
    Write-FixtureFile -Path (Join-Path $fakeBin 'java.cmd') -Content "@echo off`r`necho openjdk version `"21.0.0`" 1^>^&2`r`nexit /b 0`r`n"
    Write-FixtureFile -Path (Join-Path $fakeBin 'adb.cmd') -Content "@echo off`r`necho List of devices attached`r`nexit /b 0`r`n"
    $env:Path = "$fakeBin;$originalPath"

    $policyPath = Join-Path $RepositoryRoot 'tools/coverage/policy.json'
    $policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
    foreach ($relative in @($policy.harness_requirements.required_files)) {
        $source = Join-Path $RepositoryRoot ([string]$relative).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $destination = Join-Path $repo ([string]$relative).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Required harness source is missing: $source" }
        $parent = Split-Path -Parent $destination
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
    & git init --initial-branch=main $repo *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize clean capture entry fixture repository' }
    & git -C $repo config user.name 'Coverage Path Fixture' *> $null
    & git -C $repo config user.email 'coverage-path-fixture@example.invalid' *> $null
    & git -C $repo add --all *> $null
    & git -C $repo commit -m 'fixture harness' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not commit clean capture entry fixture harness' }
    $sourceRevision = ((& git -C $repo rev-parse HEAD) -join '').Trim()
    $capturePath = Join-Path $repo 'tools/coverage/capture.ps1'

    $insideCapturePaths = @(
        (Join-Path $tempRootFull 'sealedbundle/assessments'),
        (Join-Path $tempRootFull 'sealedbundle'),
        (Join-Path $tempRootFull 'outside/../SealedBundle/assessments')
    )
    foreach ($outputRoot in $insideCapturePaths) {
        $beforeTree = Get-TreeDigest -Path $repo
        $before = (git -C $repo status --porcelain | Out-String)
        $run = Invoke-FixtureScript -ScriptPath $capturePath -Arguments @(
            '-RepositoryRoot', $repo, '-SourceRevision', $sourceRevision, '-Purpose', 'Initial', '-HarnessRoot', $repo,
            '-DeviceSerial', 'fixture-disconnected-device', '-OutputRoot', $outputRoot, '-SkipExecution'
        )
        if ($run.Text -notmatch 'OutputRoot cannot be inside the measured repository tree') { throw "Capture did not reject contained output '$outputRoot': $($run.Text)" }
        if ((Get-TreeDigest -Path $repo) -cne $beforeTree -or (git -C $repo status --porcelain | Out-String) -cne $before) {
            throw 'Capture changed the measured repository while refusing an unsafe OutputRoot'
        }
    }
    $captureSibling = Join-Path $tempRootFull 'SealedBundle-evidence'
    $captureSiblingRun = Invoke-FixtureScript -ScriptPath $capturePath -Arguments @(
        '-RepositoryRoot', $repo, '-SourceRevision', $sourceRevision, '-Purpose', 'Initial', '-HarnessRoot', $repo,
        '-DeviceSerial', 'fixture-disconnected-device', '-OutputRoot', $captureSibling, '-SkipExecution'
    )
    if ($captureSiblingRun.Text -match 'OutputRoot cannot be inside the measured repository tree' -or
        $captureSiblingRun.Text -notmatch 'Target device') { throw "Capture rejected a valid sibling or progressed past the containment gate unexpectedly: $($captureSiblingRun.Text)" }
    if (Test-Path -LiteralPath $captureSibling) { throw 'Capture mutated the valid sibling destination before device preflight completed' }

    $bundle = Join-Path $tempRootFull 'SealedBundleForAssessment'
    New-Item -ItemType Directory -Path $bundle -Force | Out-Null
    $verifyScript = Join-Path $RepositoryRoot 'tools/coverage/verify.ps1'
    $verifyPaths = @(
        (Join-Path $tempRootFull 'sealedbundleforassessment/assessments'),
        $bundle,
        (Join-Path $tempRootFull 'outside/../SealedBundleForAssessment/assessments')
    )
    foreach ($outputDirectory in $verifyPaths) {
        $before = Get-TreeDigest -Path $bundle
        $run = Invoke-FixtureScript -ScriptPath $verifyScript -Arguments @('-RunBundle', $bundle, '-PolicyPath', $policyPath, '-OutputDirectory', $outputDirectory)
        if ($run.Text -notmatch 'Failure receipt cannot be written inside the capture bundle') { throw "Verifier did not reject contained failure receipt '$outputDirectory': $($run.Text)" }
        if ((Get-TreeDigest -Path $bundle) -cne $before) { throw 'Verifier changed sealed bundle bytes while refusing an unsafe failure-receipt path' }
    }
    $verifySibling = Join-Path $tempRootFull 'SealedBundleForAssessment-receipt'
    $verifySiblingRun = Invoke-FixtureScript -ScriptPath $verifyScript -Arguments @('-RunBundle', $bundle, '-PolicyPath', $policyPath, '-OutputDirectory', $verifySibling)
    if ($verifySiblingRun.Text -match 'cannot be written inside the capture bundle' -or -not (Test-Path -LiteralPath (Join-Path $verifySibling 'decision.json'))) {
        throw "Verifier did not allow the valid sibling failure receipt: $($verifySiblingRun.Text)"
    }
    $null = Remove-Item -LiteralPath $verifySibling -Recurse -Force

    $manifest = [PSCustomObject]@{ run_id = 'path-fixture-run'; purpose = 'Candidate'; git = [PSCustomObject]@{ source_commit = ('a' * 40); target_commit = ('b' * 40) }; artifacts = @() }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $bundle 'manifest.json') -Encoding utf8
    $publishScript = Join-Path $RepositoryRoot 'tools/coverage/publish.ps1'
    $publishPaths = @(
        (Join-Path $tempRootFull 'sealedbundleforassessment/assessments/publication.json'),
        $bundle,
        (Join-Path $tempRootFull 'outside/../SealedBundleForAssessment/assessments/publication.json')
    )
    foreach ($receiptPath in $publishPaths) {
        $before = Get-TreeDigest -Path $bundle
        $run = Invoke-FixtureScript -ScriptPath $publishScript -Arguments @('-RunBundle', $bundle, '-OutputReceiptPath', $receiptPath, '-DryRun')
        if ($run.Text -notmatch 'Publication receipt cannot be written inside sealed capture bundle') { throw "Publisher did not reject contained receipt '$receiptPath': $($run.Text)" }
        if ((Get-TreeDigest -Path $bundle) -cne $before) { throw 'Publisher changed sealed bundle bytes while refusing an unsafe receipt path' }
    }
    $publishSibling = Join-Path $tempRootFull 'SealedBundleForAssessment-publication.json'
    $bundleBeforePublishSibling = Get-TreeDigest -Path $bundle
    $publishSiblingRun = Invoke-FixtureScript -ScriptPath $publishScript -Arguments @('-RunBundle', $bundle, '-OutputReceiptPath', $publishSibling, '-DryRun')
    if ($publishSiblingRun.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $publishSibling)) { throw "Publisher did not allow a valid sibling receipt path: $($publishSiblingRun.Text)" }
    if ((Get-TreeDigest -Path $bundle) -cne $bundleBeforePublishSibling) { throw 'Publisher changed sealed bundle bytes while writing an external dry-run receipt' }

    Write-Output 'Capture, verifier failure-receipt, and publisher production entries rejected case-insensitive, exact-parent, and normalized-traversal destinations without mutating repository or sealed-bundle bytes; sibling destinations remained allowed.'
} finally {
    $env:Path = $originalPath
    if (Test-Path -LiteralPath $tempRootFull) {
        if ([System.IO.Path]::GetDirectoryName($tempRootFull) -ne $tempParent -or [System.IO.Path]::GetFileName($tempRootFull) -notlike 'coverage-path-containment-entry-*') {
            throw "Refusing unsafe path fixture cleanup target: $tempRootFull"
        }
        Get-ChildItem -LiteralPath $tempRootFull -Recurse -File -Force | ForEach-Object { $_.Attributes = [System.IO.FileAttributes]::Normal }
        [System.IO.Directory]::Delete($tempRootFull, $true)
    }
}
