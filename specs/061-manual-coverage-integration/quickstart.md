# Validation Guide: Manual Coverage Review

Target routing, local manual assessment/review, report archival, historical archive identity preservation, guarded initial-baseline acceptance and manual-promotion entry points are implemented and fixture-validated. `main` and `swe/coverage-evidence` are protected by active rulesets, and the exact `main` baseline has been published and accepted. The legacy archive remains protected and retained; the repository default remains `lab/or/chimali`. The controlled review, merge and subsequent promotion remain unverified. Local hook success is not remote coverage enforcement.

## Prerequisites

PowerShell 7, Git and the existing Android/Gradle toolchain; a separate clean reviewed harness at an identified revision; a capable authorized Android device; and an external evidence directory containing a verified compatible accepted index/bundle for the exact target. Confirm the intended remote, `lab/or/main` source commit, archive protection and GitHub permissions. The hosted runner waiver remains active.

## Initial baseline

Capture the exact source commit used to create `main`. `InitialTargetRef` is restricted to `lab/or/main`; capture fails if that ref differs from `SourceRevision`. After the branch rollout, the publisher requires both live remote refs (`lab/or/main` and `main`) to equal that commit, independently verifies complete suites and policy floors, refuses to replace an existing accepted index, then archives the acceptance record and new index in the same atomic archive commit.

```powershell
pwsh -NoProfile -File .\tools\coverage\capture.ps1 `
  -RepositoryRoot '<clean-main-checkout>' -SourceRevision '<lab/or/main-sha>' `
  -Purpose Initial -InitialTargetRef lab/or/main `
  -HarnessRoot '<reviewed-clean-harness>' -DeviceSerial '<authorized-device-serial>' `
  -OutputRoot '<external-evidence-root>'

pwsh -NoProfile -File .\tools\coverage\publish.ps1 `
  -RunBundle '<initial-run-bundle>' -AcceptInitialBaseline `
  -EvidenceBranch swe/coverage-evidence `
  -OutputReceiptPath '<external-evidence-root>\assessments\<run-id>\publication-receipt.json'
```

Initial captures without `-InitialTargetRef` remain diagnostic only and cannot seed the accepted index. The initial baseline has already been accepted; the publisher refuses to overwrite an existing accepted index.

## Isolated routing validation

Extend `tools/coverage/fixtures/run-pre-push-hook-fixture.ps1` and `tools/coverage/test-policy.ps1` using temporary local repositories, isolated Git configuration and a stub verifier. Cover real Git hook arguments/stdin, unset target with malformed input (no parsing), configured target with malformed input (reject), empty/nonmatching pushes without device/config, exact remote and slash-containing target, same name on another remote, mixed refs, target deletion, wrong/dirty candidate, stale target and failed/incomplete evidence. A matching failure must abort the whole mixed push.

Run the extended fixture set and full required local gates after implementation:

```powershell
pwsh -NoProfile -File .\tools\coverage\test-policy.ps1 -FixturePattern pre-push-hook.json
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

Both commands must succeed; record source/harness/target identities, counts, outcomes and limitations. A partial run does not establish complete coverage.

## Target setup and manual assessment

After verifying `main`, the trusted harness and compatible baseline, configure the developer checkout:

```powershell
git config --local coverage.targetRef origin/main
```

Follow the implemented setup guidance for the device and evidence directory. Verify a feature push requires no coverage and an explicit manual feature assessment produces candidate/target-bound evidence. A target push assesses the exact proposed commit; a failed assessment or unavailable prerequisite blocks it. Use isolated test remotes rather than an actual protected target for fixture validation.

The existing integration entry point can be used for the full manual assessment after substituting verified local paths and device serial:

```powershell
pwsh -NoProfile -File .\tools\integration-ci.ps1 `
  -RepositoryRoot '<candidate-checkout>' -CandidateRef '<candidate-commit>' `
  -TargetRef origin/main -HarnessRoot '<reviewed-clean-harness>' `
  -DeviceSerial '<authorized-device-serial>' -EvidenceRoot '<external-evidence-root>'
```

It creates an isolated detached checkout of the exact candidate, checks the live target and accepted baseline, runs trusted local checks and coverage capture, and retains a `PASS`, `FAIL` or `INCOMPLETE` decision plus an assessment record. The record binds the candidate/tree, assessment-time baseline digest, policy, harness, suites, device, run and timestamps. Missing prerequisites, incompatible baselines or failed checks do not produce a passing assessment.

Publish a passing bundle only from a separate trusted process that holds the archive credential:

```powershell
pwsh -NoProfile -File .\tools\coverage\publish.ps1 -RunBundle '<candidate run bundle>' -AssessmentRecordPath '<external evidence root>\assessments\<run-id>\assessment-record.json' -ManualReviewPublication -EvidenceBranch 'swe/coverage-evidence' -OutputReceiptPath '<external evidence root>\assessments\<run-id>\publication-receipt.json'
```

The publisher validates and archives the immutable assessment record, then writes the report URL, run digest and record digest under `assessments/<run-id>/publication.json`. Never expose the publication credential to candidate execution.

For a pull request, run the trusted review entry point with the exact archived run bundle and external evidence root:

```powershell
pwsh -NoProfile -File .\tools\coverage\manual-review.ps1 -Repository 'octaviordz/chimali' -PullRequestNumber '<number>' -Decision ACCEPT -RunBundle '<candidate run bundle>' -EvidenceRoot '<external evidence root>' -TrustedHarnessRoot '<clean trusted harness>'
```

Use `-Decision REJECT` to record a rejection. The tool checks the authenticated repository user and access, archived bundle and report digests, exact assessment-time baseline, trusted harness and policy, PR head and base, and prospective merge tree. It retains an append-only review record. An agent may pass `-AgentIdentity` and `-AuthorizedUser` only when the authenticated user matches the user explicitly authorizing the agent. Do not accept an actor name or decision supplied by candidate content as authority.

After GitHub records the review decision and the PR is merged, record the promotion against the same run. For a PR authored by the repository owner, GitHub does not permit self-approval, so the authorized owner or agent records the decision as an authenticated review comment bound to the run and tested tree. For another author's PR, acceptance uses a GitHub approval. The promotion command rechecks the exact decision, merged PR, actual merge commit/tree, current `main`, assessment-time baseline and current accepted-index digest:

```powershell
pwsh -NoProfile -File .\tools\coverage\manual-review.ps1 `
  -Repository 'octaviordz/chimali' -PullRequestNumber '<number>' `
  -Decision PROMOTE -RunBundle '<candidate run bundle>' `
  -EvidenceRoot '<external evidence root>' `
  -TrustedHarnessRoot '<clean trusted harness>'
```

Then promote the accepted index with the same immutable candidate bundle, the recorded promotion file, the external evidence root and the exact current local index used by review:

```powershell
pwsh -NoProfile -File .\tools\coverage\publish.ps1 `
  -RunBundle '<candidate run bundle>' -PromoteManualBaseline `
  -ManualPromotionRecordPath '<external evidence root>\reviews\pr-<number>\<promotion-record>.json' `
  -EvidenceRoot '<external evidence root>' `
  -VerificationHarnessRoot '<clean harness recorded by the assessment>' `
  -AcceptedBaselineIndexPath '<external evidence root>\baselines\current.json' `
  -EvidenceBranch 'swe/coverage-evidence' `
  -OutputReceiptPath '<external evidence root>\assessments\<run-id>\promotion-receipt.json'
```

The publisher independently reruns the verifier from the clean, pinned measurement harness recorded by the assessment against the original assessment-time baseline. The separate publisher may contain security fixes without changing that measurement identity. The publisher removes its credential from the verifier subprocess, validates exact merge parents and the live `main` ref, and updates the index only if its old digest still matches. The resulting archive record identifies `ArchivedAcceptedRun` with `ManualReview` provenance. This does not create a hosted App check or a merge-event receipt; hosted enforcement remains waived and unverified.

Resolve the current PR head and target base, then compute the prospective merge tree in an isolated, non-mutating Git context. Record both SHAs and the resulting tree alongside the report and decision. A base advance with an unchanged prospective tree does not require recalculating the baseline or repeating coverage comparison. If the prospective tree differs, the developer merges the latest target into the feature branch, resolves conflicts, commits and publishes a fresh assessment for that revision; retain the previous report. Recheck head/base before merge and compare the actual integrated tree before promotion.

## Controlled integration and migration

1. Review the recorded archive migration inventory: old/new refs and commits, rules, bundle digests and legacy receipt identities. Preserve recoverability, verify read-back and resolve permission/race failures without overwriting evidence. The current `swe/coverage-evidence` ref is a recoverable copy of the legacy tip; `codex/coverage-evidence` remains intact and the old receipt digest discrepancy must remain historical-only.
2. Verify that `main` still points at the recorded `lab/or/main` commit; configure and verify PR-required, merge-commit-only and `octaviordz`-only bypass rules. Establish a compatible accepted baseline for that exact commit before the first PR.
3. Link the exact report on `swe/coverage-evidence`. The authenticated authorized user or their agent checks repository access and trusted configuration outside candidate content, then rejects missing, failed or mismatched evidence. Resolve current PR head and `main`, compute the proposed merge tree in an isolated non-mutating context, reject conflicts, and record its comparison with the tested tree before approval. Recheck head and base immediately before merging; an advance of `main` alone does not require baseline recalculation or another coverage comparison.
4. If the proposed tree differs or has conflicts, the developer merges the then-current `main` into the feature branch, resolves conflicts, commits the result, runs the complete assessment for that new commit and links its new report. Retain the prior report and repeat the prospective-tree check; an advance with an unchanged tree alone does not require another assessment. The tooling does not automatically change or push the branch.
5. Verify the actual integrated tree against the tested tree after merge. An advance after the pre-merge check can still create a race: withhold promotion and surface a mismatch for remediation. The manual and initial-baseline commands enforce their local evidence gates, but do not treat isolated fixtures as evidence that remote rules or a live baseline are in place. Feature 058's governing baseline requirements have been reconciled; the live rollout also requires the recorded permissions and protections.
6. Check README guidance against implemented behavior and record actual remote operations, permissions and unexecuted steps. Local results do not establish an active hosted coverage check.
