[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Case,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RepositoryRoot)
$fixture = Get-Content -LiteralPath (Join-Path $root 'tools/coverage/fixtures/candidate-tree.json') -Raw | ConvertFrom-Json
$selected = @($fixture.cases | Where-Object id -eq $Case)
if ($selected.Count -ne 1) { throw "Unknown or duplicate candidate-tree fixture case: $Case" }
$caseData = $selected[0]
$realGit = (Get-Command git.exe -ErrorAction Stop).Source
$realPwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source
$scriptPath = Join-Path $root 'tools/coverage/validate-candidate-tree.ps1'
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('coverage-candidate-tree-' + [guid]::NewGuid().ToString('N'))
$tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$repo = Join-Path $tempRoot 'repo'
New-Item -ItemType Directory -Path $repo -Force | Out-Null

function Invoke-Git([string[]]$Arguments) {
    $output = @(& $realGit -C $repo @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Git fixture command failed: git $($Arguments -join ' ')`n$($output -join ' ')" }
    return ($output -join "`n").Trim()
}

try {
    $null = & $realGit init --quiet --initial-branch=main $repo
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize candidate-tree fixture repository' }
    $null = Invoke-Git @('config','user.name','Candidate Tree Fixture')
    $null = Invoke-Git @('config','user.email','candidate-tree@example.invalid')
    [IO.File]::WriteAllText((Join-Path $repo 'base.txt'), 'accepted base', [Text.UTF8Encoding]::new($false))
    $null = Invoke-Git @('add','--','base.txt'); $null = Invoke-Git @('commit','--quiet','-m','accepted base')
    $base = Invoke-Git @('rev-parse','HEAD')
    $null = Invoke-Git @('switch','--quiet','-c','candidate')
    [IO.File]::WriteAllText((Join-Path $repo 'candidate.txt'), 'candidate change', [Text.UTF8Encoding]::new($false))
    $null = Invoke-Git @('add','--','candidate.txt'); $null = Invoke-Git @('commit','--quiet','-m','candidate change')
    $candidate = Invoke-Git @('rev-parse','HEAD')

    $source = $candidate
    $acceptedBase = $base
    $event = 'pull_request'
    if ($caseData.mode -in @('pr-behind','merge-group','merge-group-wrong-base')) {
        $null = Invoke-Git @('switch','--quiet','main')
        [IO.File]::WriteAllText((Join-Path $repo 'target.txt'), 'target advance', [Text.UTF8Encoding]::new($false))
        $null = Invoke-Git @('add','--','target.txt'); $null = Invoke-Git @('commit','--quiet','-m','target advance')
        $acceptedBase = Invoke-Git @('rev-parse','HEAD')
    }
    if ($caseData.mode -in @('merge-group','merge-group-wrong-base')) {
        $null = Invoke-Git @('merge','--no-ff','--no-edit','candidate')
        if ($LASTEXITCODE -ne 0) { throw 'Unable to create deterministic merge-group candidate' }
        $source = Invoke-Git @('rev-parse','HEAD')
        $event = 'merge_group'
        if ($caseData.mode -eq 'merge-group-wrong-base') { $acceptedBase = $base }
    }
    if ($caseData.mode -eq 'target-push') { $event = 'push' }
    $output = @(& $realPwsh -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
        -RepositoryPath $repo -SourceSha $source -AcceptedBaseSha $acceptedBase -EventName $event 2>&1)
    $actualExit = $LASTEXITCODE
    $expectedExit = if ($caseData.expected_valid) { 0 } else { 1 }
    if ($actualExit -ne $expectedExit) { throw "$Case expected validator exit $expectedExit; got $actualExit; $($output -join ' ')" }
    if ($caseData.expected_valid) {
        $identity = ($output -join "`n") | ConvertFrom-Json -ErrorAction Stop
        $tree = Invoke-Git @('rev-parse',"$source`^{tree}")
        if (-not $identity.valid -or $identity.tested_candidate_sha -cne $source -or
            $identity.tested_candidate_tree -cne $tree -or $identity.accepted_base_sha -cne $acceptedBase -or
            $identity.check_destination_sha -cne $source) { throw "$Case returned incomplete or substituted candidate/check identity" }
    }
    Write-Output "$Case passed (validator exit $actualExit)."
} finally {
    $resolved = [IO.Path]::GetFullPath($tempRoot)
    if ([IO.Directory]::Exists($resolved)) {
        if (-not $resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolved) -notlike 'coverage-candidate-tree-*') { throw "Refusing unsafe candidate-tree fixture cleanup: $resolved" }
        foreach ($file in [IO.Directory]::EnumerateFiles($resolved, '*', [IO.SearchOption]::AllDirectories)) {
            $attributes = [IO.File]::GetAttributes($file)
            if (($attributes -band [IO.FileAttributes]::ReadOnly) -ne 0) {
                [IO.File]::SetAttributes($file, $attributes -band (-bnot [IO.FileAttributes]::ReadOnly))
            }
        }
        [IO.Directory]::Delete($resolved, $true)
    }
}
