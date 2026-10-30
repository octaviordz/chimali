[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$PullRequestNumber,
    [Parameter(Mandatory = $true)][ValidateSet('ACCEPT','REJECT','PROMOTE')][string]$Decision,
    [Parameter(Mandatory = $false)][string]$RunBundle,
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$TrustedHarnessRoot,
    [Parameter(Mandatory = $false)][string]$AgentIdentity,
    [Parameter(Mandatory = $false)][string]$AuthorizedUser
)

$ErrorActionPreference = 'Stop'
$gitRepositoryVariables = @('GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE','GIT_COMMON_DIR','GIT_OBJECT_DIRECTORY','GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_PREFIX','GIT_CEILING_DIRECTORIES')
foreach ($name in $gitRepositoryVariables) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
. (Join-Path $PSScriptRoot 'coverage-common.ps1')

function Invoke-GhJson {
    param([Parameter(Mandatory = $true)][string]$Endpoint)
    $output = @(& gh api $Endpoint 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Authenticated GitHub API request failed for '$Endpoint': $($output -join ' ')" }
    return (($output -join [Environment]::NewLine) | ConvertFrom-Json -Depth 50)
}

function Get-CurrentPull {
    param([string]$Repo, [int]$Number)
    return Invoke-GhJson -Endpoint "repos/$Repo/pulls/$Number"
}

function Get-CommitTree {
    param([string]$Repo, [string]$Commit)
    $response = Invoke-GhJson -Endpoint "repos/$Repo/git/commits/$Commit"
    $tree = [string]$response.tree.sha
    if ($tree -notmatch '^[0-9a-fA-F]{40,64}$') { throw "GitHub did not return a full tree identity for commit $Commit." }
    return $tree
}

function Get-ContainedEvidencePath {
    param([string]$Root, [string]$RelativePath)
    if ([string]::IsNullOrWhiteSpace($RelativePath) -or [System.IO.Path]::IsPathRooted($RelativePath) -or $RelativePath -match '(^|[/\\])\.\.([/\\]|$)') {
        throw 'Assessment evidence contains a non-contained relative path.'
    }
    $canonicalRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    $path = [System.IO.Path]::GetFullPath((Join-Path $canonicalRoot $RelativePath))
    if (-not $path.StartsWith($canonicalRoot + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Assessment evidence path escapes EvidenceRoot.'
    }
    return $path
}

function ConvertFrom-GitHubFileContent {
    param($Response)
    if ($Response.encoding -cne 'base64' -or -not $Response.content) { throw 'GitHub did not return a base64 archived file.' }
    $encoded = ([string]$Response.content -replace '\s','')
    $bytes = [Convert]::FromBase64String($encoded)
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Get-TextSha256 {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($Text))
    return [Convert]::ToHexString($sha).ToLowerInvariant()
}

try {
    foreach ($entry in @(@{ Name = 'EvidenceRoot'; Value = $EvidenceRoot }, @{ Name = 'TrustedHarnessRoot'; Value = $TrustedHarnessRoot })) {
        if (-not [System.IO.Path]::IsPathRooted($entry.Value)) { throw "$($entry.Name) must be an absolute path." }
    }
    $EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
    $TrustedHarnessRoot = [System.IO.Path]::GetFullPath($TrustedHarnessRoot)
    if (-not (Test-Path -LiteralPath $EvidenceRoot -PathType Container)) { throw 'EvidenceRoot does not exist.' }
    if (-not (Test-Path -LiteralPath $TrustedHarnessRoot -PathType Container)) { throw 'TrustedHarnessRoot does not exist.' }
    $harnessCommit = (& git -C $TrustedHarnessRoot rev-parse HEAD 2>$null).Trim()
    $harnessStatus = (& git -C $TrustedHarnessRoot status --porcelain 2>$null) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or $harnessCommit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or $harnessStatus) {
        throw 'TrustedHarnessRoot must be a clean pinned Git checkout.'
    }

    $authOutput = @(& gh auth status 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI must have an authenticated repository user for a manual review.' }
    $authenticated = [string](Invoke-GhJson -Endpoint 'user').login
    if (-not $authenticated) { throw 'Authenticated GitHub user identity is unavailable.' }
    $permission = [string](Invoke-GhJson -Endpoint "repos/$Repository/collaborators/$authenticated/permission").permission
    if ($permission -notin @('write','maintain','admin')) { throw "Authenticated user '$authenticated' lacks write, maintain, or admin access to $Repository." }
    if ($AgentIdentity) {
        if (-not $AuthorizedUser -or $AuthorizedUser -cne $authenticated) { throw 'An agent may act only under the authenticated user explicit authority.' }
    } elseif ($AuthorizedUser -and $AuthorizedUser -cne $authenticated) {
        throw 'AuthorizedUser must match the authenticated repository user.'
    }

    $pull = Get-CurrentPull -Repo $Repository -Number $PullRequestNumber
    if ($pull.base.ref -cne 'main' -or
        ($Decision -eq 'PROMOTE' -and ($pull.state -cne 'closed' -or $pull.merged -ne $true)) -or
        ($Decision -ne 'PROMOTE' -and $pull.state -cne 'open')) {
        throw "The pull request must target main and be $(if ($Decision -eq 'PROMOTE') { 'closed and merged' } else { 'open' })."
    }
    $headSha = [string]$pull.head.sha
    $baseSha = [string]$pull.base.sha
    if ($headSha -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or $baseSha -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') {
        throw 'The pull request head or base identity is not a full commit SHA.'
    }
    $headRepository = [string]$pull.head.repo.full_name
    if (-not $headRepository) { throw 'The pull request head repository is unavailable.' }
    $headTree = Get-CommitTree -Repo $headRepository -Commit $headSha

    $runId = $null
    $reportLink = $null
    $record = $null
    $bundleDigest = $null
    if ($RunBundle) {
        $RunBundle = [System.IO.Path]::GetFullPath($RunBundle)
        $manifestPath = Join-Path $RunBundle 'manifest.json'
        if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'RunBundle is missing manifest.json.' }
        $manifest = Read-SafeJson -Path $manifestPath
        $runId = [string]$manifest.run_id
        if ($runId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') { throw 'RunBundle run_id is not a valid UUID.' }
        $recordPath = Join-Path (Join-Path (Join-Path $EvidenceRoot 'assessments') $runId) 'assessment-record.json'
        if (-not (Test-Path -LiteralPath $recordPath -PathType Leaf)) { throw 'The immutable assessment record is missing.' }
        $record = Read-SafeJson -Path $recordPath
        $bundleDigest = Get-CoverageBundleDigest -BundlePath $RunBundle
        $manifestDigest = Get-FileSha256 -Path $manifestPath
        if ($record.decision -cne 'PASS' -or $record.run.id -cne $runId -or $record.run.bundle_digest -cne $bundleDigest -or
            $record.run.manifest_digest -cne $manifestDigest -or $record.candidate.commit -cne $headSha -or
            $record.candidate.tree -cne $headTree -or [string]$manifest.git.source_commit -cne $headSha -or
            [string]$manifest.git.source_tree -cne $headTree) {
            throw 'Assessment evidence does not prove a passing run for the current PR head commit and tree.'
        }
        $decisionPath = Join-Path (Join-Path (Join-Path $EvidenceRoot 'assessments') $runId) 'decision.json'
        if ([System.IO.Path]::GetFullPath([string]$record.run.decision_path) -cne [System.IO.Path]::GetFullPath($decisionPath) -or
            -not (Test-Path -LiteralPath $decisionPath -PathType Leaf) -or (Get-FileSha256 -Path $decisionPath) -cne [string]$record.run.decision_digest) {
            throw 'Trusted verifier decision is missing or its digest differs from the sealed assessment record.'
        }
        $decisionData = Read-SafeJson -Path $decisionPath
        if ($decisionData.overall_passed -ne $true -or $decisionData.run_id -cne $runId -or
            $decisionData.manifest_digest -cne $manifestDigest) {
            throw 'Trusted verifier decision does not pass or bind to this sealed run.'
        }
        $baselinePath = Get-ContainedEvidencePath -Root $EvidenceRoot -RelativePath ([string]$record.accepted_baseline.bundle_path)
        if (-not (Test-Path -LiteralPath (Join-Path $baselinePath 'manifest.json') -PathType Leaf) -or
            (Get-CoverageBundleDigest -BundlePath $baselinePath) -cne [string]$record.accepted_baseline.bundle_digest) {
            throw 'Assessment-time accepted baseline bundle is missing or has changed.'
        }
        $baselineManifest = Read-SafeJson -Path (Join-Path $baselinePath 'manifest.json')
        if ($baselineManifest.run_id -cne $record.accepted_baseline.run_id -or
            $baselineManifest.git.source_commit -cne $record.accepted_baseline.commit -or
            $baselineManifest.git.source_tree -cne $record.accepted_baseline.tree) {
            throw 'Assessment-time accepted baseline identity does not match its immutable bundle.'
        }
        $baselineIndexPath = Get-ContainedEvidencePath -Root $EvidenceRoot -RelativePath ([string]$record.accepted_baseline.index_path)
        if (-not (Test-Path -LiteralPath $baselineIndexPath -PathType Leaf) -or
            (Get-FileSha256 -Path $baselineIndexPath) -cne [string]$record.accepted_baseline.index_digest) {
            throw 'Assessment-time accepted baseline index is missing or has changed.'
        }
        $baselineIndex = Read-SafeJson -Path $baselineIndexPath
        if ($baselineIndex.run_id -cne $record.accepted_baseline.run_id -or
            $baselineIndex.bundle_digest -cne $record.accepted_baseline.bundle_digest -or
            $baselineIndex.target_commit -cne $record.accepted_baseline.commit) {
            throw 'Assessment-time accepted baseline index does not identify the sealed baseline bundle and commit.'
        }
        if ($record.policy.scope_fingerprint -cne $manifest.measurement_fingerprint -or
            $record.policy.id -cne $manifest.policy_id) {
            throw 'Assessment-time policy scope does not match the sealed candidate manifest.'
        }
        $policy = Read-SafeJson -Path (Join-Path $TrustedHarnessRoot 'tools/coverage/policy.json')
        if ($manifest.policy_id -cne $policy.policy_id -or $record.harness.commit -cne $harnessCommit) {
            throw 'Evidence policy or trusted harness revision differs from the reviewed harness.'
        }
        $archiveResponse = Invoke-GhJson -Endpoint "repos/$Repository/contents/runs/$runId/summary.md?ref=swe/coverage-evidence"
        $reportLink = [string]$archiveResponse.html_url
        if (-not $reportLink) { throw 'The immutable report is not retrievable from swe/coverage-evidence.' }
        $receiptResponse = Invoke-GhJson -Endpoint "repos/$Repository/contents/assessments/$runId/publication.json?ref=swe/coverage-evidence"
        $publicationReceipt = (ConvertFrom-GitHubFileContent -Response $receiptResponse) | ConvertFrom-Json -Depth 50
        if ($publicationReceipt.run_id -cne $runId -or $publicationReceipt.publication_purpose -cne 'Candidate' -or
            $publicationReceipt.archive_branch -cne 'swe/coverage-evidence' -or
            $publicationReceipt.archive_ref -cne 'refs/heads/swe/coverage-evidence' -or
            $publicationReceipt.bundle_digest -cne $bundleDigest -or
            $publicationReceipt.report_url -cne $reportLink) {
            throw 'Archived publication receipt does not bind this report, bundle digest, and exact new archive ref.'
        }
        $archivedRecordResponse = Invoke-GhJson -Endpoint "repos/$Repository/contents/assessments/$runId/assessment-record.json?ref=swe/coverage-evidence"
        $archivedRecordText = ConvertFrom-GitHubFileContent -Response $archivedRecordResponse
        $archivedRecordDigest = Get-TextSha256 -Text $archivedRecordText
        if (-not $publicationReceipt.assessment_record_digest -or
            $archivedRecordDigest -cne [string]$publicationReceipt.assessment_record_digest -or
            $archivedRecordDigest -cne (Get-FileSha256 -Path $recordPath)) {
            throw 'Archived assessment record does not match the externally retained record digest.'
        }
        $archivedAssessment = $archivedRecordText | ConvertFrom-Json -Depth 50
        if ($archivedAssessment.run.id -cne $runId -or $archivedAssessment.candidate.tree -cne $headTree -or
            $archivedAssessment.accepted_baseline.bundle_digest -cne $record.accepted_baseline.bundle_digest) {
            throw 'Archived assessment record identity differs from the candidate or assessment-time baseline.'
        }
        $repositoryInfo = Invoke-GhJson -Endpoint "repos/$Repository"
        $archiveClone = Join-Path ([System.IO.Path]::GetTempPath()) ('chimali-review-archive-' + [guid]::NewGuid().ToString('N'))
        $archiveClone = [System.IO.Path]::GetFullPath($archiveClone)
        $tempPrefixForArchive = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
        if (-not $archiveClone.StartsWith($tempPrefixForArchive,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Archive clone path is outside the system temp directory.' }
        try {
            & git clone --quiet --depth 1 --single-branch --branch 'swe/coverage-evidence' ([string]$repositoryInfo.clone_url) $archiveClone
            if ($LASTEXITCODE -ne 0) { throw 'Could not retrieve the trusted evidence archive branch.' }
            $archivedBundle = Join-Path (Join-Path $archiveClone 'runs') $runId
            if ((Get-CoverageBundleDigest -BundlePath $archivedBundle) -cne $bundleDigest) {
                throw 'Archived immutable bundle digest differs from the assessment record.'
            }
            if ((Get-FileSha256 -Path (Join-Path $archivedBundle 'manifest.json')) -cne $manifestDigest) {
                throw 'Archived manifest differs from the reviewed candidate manifest.'
            }
            $archivedAssessmentPath = Join-Path (Join-Path (Join-Path $archiveClone 'assessments') $runId) 'assessment-record.json'
            if ((Get-FileSha256 -Path $archivedAssessmentPath) -cne $archivedRecordDigest) {
                throw 'Archived assessment record bytes differ from the authenticated archive response.'
            }
            $archivedBaselinePath = Join-Path (Join-Path $archiveClone 'runs') ([string]$record.accepted_baseline.run_id)
            if (-not (Test-Path -LiteralPath $archivedBaselinePath -PathType Container) -or
                (Get-CoverageBundleDigest -BundlePath $archivedBaselinePath) -cne [string]$record.accepted_baseline.bundle_digest) {
                throw 'The assessment-time accepted baseline is not retrievable from the trusted archive.'
            }
        } finally {
            Remove-CoverageReviewTemporaryDirectory -Path $archiveClone
        }
    } elseif ($Decision -in @('ACCEPT','PROMOTE')) {
        throw "$Decision requires a specific sealed passing RunBundle."
    }

    $acceptedReview = $null
    $mergeCommit = $null
    $integratedTree = $null
    $mainTip = $null
    $tempBare = Join-Path ([System.IO.Path]::GetTempPath()) ('chimali-manual-review-' + [guid]::NewGuid().ToString('N') + '.git')
    $tempFull = [System.IO.Path]::GetFullPath($tempBare)
    $tempPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $tempFull.StartsWith($tempPrefix,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Temporary merge check path is outside the system temp directory.' }
    try {
        & git init --bare $tempBare | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the isolated merge-tree repository.' }
        $remoteUrl = [string](Invoke-GhJson -Endpoint "repos/$Repository" | Select-Object -ExpandProperty clone_url)
        & git --git-dir=$tempBare fetch --no-tags $remoteUrl "+refs/heads/main:refs/review/base" "+refs/pull/$PullRequestNumber/head:refs/review/head" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not fetch the exact PR head and main base into the isolated merge-tree repository.' }
        if ($Decision -eq 'PROMOTE') {
            $mergeCommit = [string]$pull.merge_commit_sha
            if ($mergeCommit -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') { throw 'Merged pull request has no full merge commit identity.' }
            $mergeMetadata = Invoke-GhJson -Endpoint "repos/$Repository/git/commits/$mergeCommit"
            $integratedTree = [string]$mergeMetadata.tree.sha
            $parents = @($mergeMetadata.parents | ForEach-Object { [string]$_.sha })
            if ($integratedTree -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$' -or
                $headSha -notin $parents -or $baseSha -notin $parents) {
                throw 'Actual merge commit must bind both the reviewed PR head and the reviewed main base.'
            }
            if ($integratedTree -cne [string]$record.candidate.tree) {
                throw 'Actual integrated tree differs from the tested tree; do not promote this assessment.'
            }
            $mainRef = Invoke-GhJson -Endpoint "repos/$Repository/git/ref/heads/main"
            $mainTip = [string]$mainRef.object.sha
            if ($mainTip -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') { throw 'Current main ref is not a full commit identity.' }
            $mergeConflict = $false
            $prospectiveTree = $integratedTree
            & git --git-dir=$tempBare fetch --no-tags $remoteUrl "+refs/heads/main:refs/review/base" 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Could not fetch the actual main ref for post-merge validation.' }
            $resolvedBase = (& git --git-dir=$tempBare rev-parse refs/review/base).Trim()
            if ($resolvedBase -cne $mainTip) { throw 'Fetched main does not match the authenticated current main identity.' }
            & git --git-dir=$tempBare merge-base --is-ancestor $mergeCommit refs/review/base 2>$null
            if ($LASTEXITCODE -ne 0) { throw 'The reviewed merge commit is no longer contained in current main.' }

            $reviewFiles = @(Get-ChildItem -LiteralPath (Join-Path (Join-Path $EvidenceRoot 'reviews') "pr-$PullRequestNumber") -Filter '*.json' -File -ErrorAction SilentlyContinue)
            foreach ($reviewFile in $reviewFiles) {
                $candidateReview = Read-SafeJson -Path $reviewFile.FullName
                if ($candidateReview.decision -ceq 'ACCEPT' -and $candidateReview.pull_request.number -eq $PullRequestNumber -and
                    $candidateReview.head -ceq $headSha -and $candidateReview.base -ceq $baseSha -and
                    $candidateReview.actor.authenticated_user -ceq $authenticated -and
                    $candidateReview.report.run_id -ceq $runId -and $candidateReview.report.bundle_digest -ceq $bundleDigest -and
                    $candidateReview.prospective_tree -ceq $record.candidate.tree -and $candidateReview.github_review_id) {
                    $acceptedReview = $candidateReview
                    break
                }
            }
            if (-not $acceptedReview) { throw 'No authenticated ACCEPT decision for this exact PR head, base, run and bundle was found.' }
            $githubReview = Invoke-GhJson -Endpoint "repos/$Repository/pulls/$PullRequestNumber/reviews/$($acceptedReview.github_review_id)"
            if (-not (Test-ManualGitHubDecisionEvidence -PullRequest $pull -GitHubReview $githubReview `
                -Repository $Repository -Reviewer ([string]$acceptedReview.actor.authenticated_user) `
                -Head $headSha -ReviewId ([string]$acceptedReview.github_review_id) `
                -RunId $runId -TestedTree ([string]$record.candidate.tree))) {
                throw 'The accepted decision is not backed by the matching authenticated GitHub review on this candidate head.'
            }
            $currentIndexResponse = Invoke-GhJson -Endpoint "repos/$Repository/contents/baselines/current.json?ref=swe/coverage-evidence"
            $currentIndexText = ConvertFrom-GitHubFileContent -Response $currentIndexResponse
            $currentAcceptedIndex = $currentIndexText | ConvertFrom-Json -Depth 30
            $currentAcceptedIndexDigest = Get-TextSha256 -Text $currentIndexText
            if ($currentAcceptedIndex.run_id -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' -or
                $currentAcceptedIndex.bundle_digest -notmatch '^[0-9a-fA-F]{64}$') {
                throw 'Current archived accepted index is malformed; manual baseline promotion cannot proceed.'
            }
            $mergeConflict = $false
        } else {
            $mergeOutput = @(& git --git-dir=$tempBare merge-tree --write-tree refs/review/base refs/review/head 2>&1)
            $mergeConflict = $LASTEXITCODE -ne 0
            if ($mergeConflict -and $Decision -eq 'ACCEPT') { throw "The prospective merge tree is unavailable or conflicts: $($mergeOutput -join ' ')" }
            $prospectiveTree = if ($mergeConflict) { $null } else { [string]$mergeOutput[0] }
            $resolvedBase = (& git --git-dir=$tempBare rev-parse refs/review/base).Trim()
            $resolvedHead = (& git --git-dir=$tempBare rev-parse refs/review/head).Trim()
            if ($resolvedBase -cne $baseSha -or $resolvedHead -cne $headSha) { throw 'Fetched PR head or base no longer matches the authenticated API identities.' }
            if (-not $mergeConflict -and $prospectiveTree -notmatch '^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$') { throw 'The merge-tree command did not return a full tree identity.' }
        }
    } finally {
        Remove-CoverageReviewTemporaryDirectory -Path $tempFull
    }
    if ($Decision -eq 'ACCEPT') {
        $treeCase = [PSCustomObject]@{ tree = [PSCustomObject]@{ head = $headSha; base = $baseSha; prospective_tree = $prospectiveTree; tested_tree = [string]$record.candidate.tree; conflict = $mergeConflict; expected_new_assessment = $false } }
        $treeCheck = Test-ManualReviewTreeCase -Case $treeCase
        if (-not $treeCheck.IsValid) {
            throw "$($treeCheck.Errors -join '; '). Merge current main into the feature branch, resolve conflicts, commit, and publish a fresh assessment."
        }
    }

    $latestPull = Get-CurrentPull -Repo $Repository -Number $PullRequestNumber
    if ([string]$latestPull.head.sha -cne $headSha -or [string]$latestPull.base.sha -cne $baseSha -or
        ($Decision -eq 'PROMOTE' -and ($latestPull.merged -ne $true -or [string]$latestPull.merge_commit_sha -cne $mergeCommit))) {
        throw 'PR head or main base changed during review. Recompute the proposed tree and repeat review against the current identities.'
    }
    if ($Decision -eq 'ACCEPT' -and $prospectiveTree -cne $record.candidate.tree) {
        throw 'The proposed tree changed before acceptance.'
    }

    $actor = if ($AgentIdentity) { $AgentIdentity } else { $authenticated }
    $authorizedUserForRecord = if ($AgentIdentity) { $AuthorizedUser } else { $null }
    if ($Decision -in @('ACCEPT','REJECT')) {
        $repositoryOwner = ($Repository -split '/', 2)[0]
        $ownerSelfReview = $authenticated -ceq $repositoryOwner -and [string]$pull.user.login -ceq $repositoryOwner
        $reviewEvent = if ($ownerSelfReview) { 'COMMENT' } elseif ($Decision -eq 'ACCEPT') { 'APPROVE' } else { 'REQUEST_CHANGES' }
        $reviewBody = "Manual coverage review $Decision for run $runId; tested tree $($record.candidate.tree); prospective tree $prospectiveTree. Hosted coverage check remains unverified."
        $reviewOutput = @(& gh api "repos/$Repository/pulls/$PullRequestNumber/reviews" --method POST -f "event=$reviewEvent" -f "body=$reviewBody" -f "commit_id=$headSha" 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "GitHub did not accept the authenticated $reviewEvent review: $($reviewOutput -join ' ')" }
        $githubReview = ($reviewOutput -join [Environment]::NewLine) | ConvertFrom-Json -Depth 50
        $expectedReviewState = if ($ownerSelfReview) { 'COMMENTED' } elseif ($Decision -eq 'ACCEPT') { 'APPROVED' } else { 'CHANGES_REQUESTED' }
        if ($githubReview.state -cne $expectedReviewState -or $githubReview.user.login -cne $authenticated -or
            $githubReview.commit_id -cne $headSha -or [string]$githubReview.body -cne $reviewBody -or -not $githubReview.id) {
            throw 'GitHub review response did not bind the authenticated user, requested decision, and exact PR head.'
        }
    }
    $reviewRecord = [PSCustomObject]@{
        schema_version = '1.0'
        decision_id = [guid]::NewGuid().ToString()
        decision = $Decision
        reviewed_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        repository = $Repository
        pull_request = [PSCustomObject]@{ number = $PullRequestNumber; url = [string]$pull.html_url }
        actor = [PSCustomObject]@{ authenticated_user = $authenticated; acting_agent = $actor; authorized_user = $authorizedUserForRecord; permission = $permission }
        head = $headSha
        base = $baseSha
        prospective_tree = $prospectiveTree
        tested_tree = if ($record) { [string]$record.candidate.tree } else { $null }
        assessment_baseline = if ($record) { $record.accepted_baseline } else { $null }
        report = [PSCustomObject]@{ run_id = $runId; bundle_digest = $bundleDigest; link = $reportLink }
        trusted_harness_commit = $harnessCommit
        hosted_check = 'Unverified'
        github_review_id = if ($githubReview) { [string]$githubReview.id } else { [string]$acceptedReview.github_review_id }
        merged_commit = if ($Decision -eq 'PROMOTE') { $mergeCommit } else { $null }
        integrated_commit = if ($Decision -eq 'PROMOTE') { $mergeCommit } else { $null }
        integrated_tree = if ($Decision -eq 'PROMOTE') { $integratedTree } else { $null }
        main_tip = if ($Decision -eq 'PROMOTE') { $mainTip } else { $null }
        accepted_index = if ($Decision -eq 'PROMOTE') { $currentAcceptedIndex } else { $null }
        accepted_index_digest = if ($Decision -eq 'PROMOTE') { $currentAcceptedIndexDigest } else { $null }
        expected_old_index_digest = if ($Decision -eq 'PROMOTE') { $currentAcceptedIndexDigest } else { $null }
        reviewed_by = if ($Decision -eq 'PROMOTE') { [string]$acceptedReview.actor.authenticated_user } else { $null }
        reviewed_head = if ($Decision -eq 'PROMOTE') { [string]$acceptedReview.head } else { $null }
        manifest_digest = if ($record) { [string]$record.run.manifest_digest } else { $null }
        bundle_digest = if ($bundleDigest) { [string]$bundleDigest } else { $null }
        run_id = if ($runId) { [string]$runId } else { $null }
    }
    $reviewDirectory = Join-Path (Join-Path $EvidenceRoot 'reviews') ("pr-$PullRequestNumber")
    $null = New-Item -ItemType Directory -Path $reviewDirectory -Force
    $reviewPath = Join-Path $reviewDirectory "$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ'))-$($reviewRecord.decision_id).json"
    $reviewRecord | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $reviewPath -Encoding utf8
    Write-Output "Manual review $Decision recorded: $reviewPath"
} catch {
    [Console]::Error.WriteLine("Manual review did not complete: $_")
    exit 2
}
