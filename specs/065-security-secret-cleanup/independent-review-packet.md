# Feature 065 independent security review packet — T042 preparation

**Status (2026-11-22):** The recorded review and supplemental disposition in `independent-review.md` cover frozen candidate `2627644114f95f7f0c33e7448abc5e0f3961cc26` and the explicitly listed heap cohorts, with limitations. This packet is historical handoff material for that review; it does not cover the subsequent clipboard source changes or establish release acceptance. Affected independent review must be refreshed for a new candidate.

**Requirement linkage:** FR-016, SC-006, T042.

## Purpose

Provide the future independent reviewer with the required candidate identity, prerequisite checklist, evidence package, review procedure, and required record fields for `specs/065-security-secret-cleanup/independent-review.md`.

## Reviewer independence requirements

The reviewer must:

- be independent of the remediation author and of the agent/session that prepared this packet;
- record their identity, role/organization if applicable, review date, and conflict-of-interest check;
- have controlled access to the frozen candidate and retained evidence artifacts;
- not substitute host-only, emulator-only, exploratory-worktree, or self-review evidence for release acceptance;
- record limitations and unresolved obligations rather than converting them into passes.

A tool-assisted review may be used only as preparation; the accountable reviewer must be identified and must accept the review record.

## Prerequisites that must be complete before review

The reviewer must verify all of the following before accepting the candidate for review:

- [ ] T039 policy/register synchronization is current.
- [ ] T043 policy/register synchronization is current.
- [ ] A clean product candidate is frozen with exact commit, tree, build variants, generated signatures, and scope.
- [ ] T036 final source/inventory reconciliation matches the frozen candidate.
- [ ] T041 final regressions were executed against the frozen candidate or explicitly scoped candidate-equivalent evidence is accepted by the reviewer.
- [ ] T037 evidence validation was refreshed against the frozen candidate with zero real-record errors and demonstrated synthetic invalid-record rejection.
- [ ] T040 implementation/release dispositions were refreshed after candidate freeze.
- [ ] T045 controlled raw-artifact access, synthetic-state attestation, retention dependencies, and reviewer access are verified.
- [ ] No unresolved mandatory noncompliance is represented as passing.
- [ ] All cited artifacts remain accessible while cited evidence relies on them.

If any prerequisite is missing, the review must remain `Unverified` or record `Unable to review` with the exact blocker.

## Frozen candidate identity block

The completed review must record:

| Field | Required value |
|---|---|
| Frozen commit | Exact 40-character commit SHA |
| Frozen tree | Exact tree SHA |
| Branch/ref | Branch or tag used for the candidate |
| Build variants | Release/minified/debug or other reviewed variants |
| Generated signatures | SQLDelight signatures, build outputs, or other generated artifacts that define reviewed scope |
| Review scope | Modules, source sets, features, and evidence families included |
| Worktree state | Must be clean or explicitly reconciled to the frozen commit/tree |
| Candidate freeze record | Link or artifact that establishes the freeze |

## Evidence package to review

The reviewer should inspect, at minimum:

- `specs/065-security-secret-cleanup/spec.md`
- `specs/065-security-secret-cleanup/plan.md`
- `specs/065-security-secret-cleanup/data-model.md`
- `specs/065-security-secret-cleanup/quickstart.md`
- `specs/065-security-secret-cleanup/tasks.md`
- `specs/065-security-secret-cleanup/assessment.md`
- `specs/065-security-secret-cleanup/source-inventory.json`
- `specs/065-security-secret-cleanup/evidence/index.md`
- `specs/065-security-secret-cleanup/evidence/evidence-validation.md`
- `specs/065-security-secret-cleanup/evidence/evidence-validation.hashes.txt`
- `specs/065-security-secret-cleanup/evidence/regression.md`
- `docs/backlog/security-secret-cleanup-audit.md`
- `docs/brd.md`
- `docs/trd.md`
- `docs/security/data-classification.md`
- `docs/security/platform-text-boundaries.md`
- retained synthetic runtime captures, heap/HPROF summaries, clipboard observations, diagnostic-output records, and raw-artifact access metadata permitted by FR-014/T045

## Pre-review hash capture procedure

Before review, compute SHA-256 hashes for the evidence package and retain them as the pre-review manifest. Example PowerShell from the repository root:

```powershell
$files = @(
  'specs/065-security-secret-cleanup/tasks.md',
  'specs/065-security-secret-cleanup/assessment.md',
  'specs/065-security-secret-cleanup/source-inventory.json',
  'specs/065-security-secret-cleanup/evidence/index.md',
  'specs/065-security-secret-cleanup/evidence/evidence-validation.md',
  'specs/065-security-secret-cleanup/evidence/evidence-validation.hashes.txt',
  'specs/065-security-secret-cleanup/evidence/regression.md',
  'docs/backlog/security-secret-cleanup-audit.md',
  'docs/brd.md',
  'docs/trd.md',
  'docs/security/data-classification.md',
  'docs/security/platform-text-boundaries.md',
  'specs/065-security-secret-cleanup/quickstart.md'
)
foreach ($f in $files) {
  if (Test-Path $f) {
    $h = (Get-FileHash $f -Algorithm SHA256).Hash.ToLowerInvariant()
    "$h  $f"
  }
}
```

The completed review must include the exact pre-review hashes and state whether any artifact changed during review.

## Review procedure

1. Verify the frozen candidate identity and clean/reconciled worktree state.
2. Verify prerequisite task states and refreshed evidence freshness.
3. Recompute or verify pre-review evidence hashes.
4. Inspect `source-inventory.json` against the frozen candidate for path/hash/link/register consistency.
5. Inspect `assessment.md` for owner outcomes, findings, limitations, and unresolved obligations.
6. Inspect `evidence/index.md` for status taxonomy, scope, limitations, and artifact accessibility.
7. Inspect runtime evidence for host/emulator/device/heap/diagnostic scope and confirm no overclaim.
8. Inspect policy/register synchronization in BRD/TRD, data classification, and platform-text boundaries.
9. Inspect backlog disposition and retention requirements.
10. Record findings, severity, disposition, limitations, and the final review decision.

## Required completed-review record

The final `independent-review.md` must contain:

- reviewer identity and review date;
- reviewer independence/conflict check;
- exact frozen product commit/tree and build/scope;
- pre-review evidence hashes;
- required artifact access granted/verified;
- findings with severity and disposition;
- limitations and unresolved obligations;
- final disposition: accept, accept with limitations, reject, or unable to review;
- invalidation triggers for later product/build/policy/evidence changes.

## Prohibited substitutions

The following do not satisfy T042:

- remediation-author self-review;
- agent self-review without an accountable independent reviewer;
- review of a dirty exploratory worktree as if it were a frozen candidate;
- host-only, emulator-only, or partial-cohort evidence promoted to release acceptance;
- backdated or fabricated reviewer identity/date;
- missing artifact access represented as verified;
- unresolved mandatory noncompliance represented as passing.

## Current preflight snapshot — 2026-11-20

This snapshot explains why the packet is prepared but the review cannot yet run:

- Branch: `codex/065-security-secret-cleanup`
- Freeze candidate: commit `49e37f606c08dd51a899d4728ce23052334c608a` (`fix(security): complete feature 065 secret cleanup remediation and evidence`), tree `46c871e56b4a99f075c1e6426fd320af2512e9d3`, created 2026-11-20 on top of `30825cc8713206aea35039675ca60860bf448c9e`; worktree clean at freeze. Build outputs, source-set scope and tool fingerprints not yet recorded for the candidate.
- Feature delivery commit: `fe38031c9f9ee3aa4a0aee02df198d0c43813da8` (`feat: add clipboard observer and related tests for security enhancements CP`), tree `9c2e62a64aeaa1ec087b549d1fc8bc9085bf81a8`, created during the 2026-11-19 rebase of this branch onto `lab/or/chimali` (merge `a8e09810` of `lab/or/main`). The prior baseline commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57d` is preserved in reflog and remains the historical comparison point, not the current HEAD.
- Worktree: clean at freeze commit `49e37f60` (2026-11-20). Pre-freeze the worktree carried 43 entries — production-relevant uncommitted changes including `core/domain/.../SecureRandomStatus.kt` (SEC065-CORE-033 fixed-cause remap), `config/detekt/detekt.yml`, `docs/security/data-classification.md`, `docs/security/platform-text-boundaries.md`, `tools/security/run-heap-capture.ps1`, `tools/security/run-clipboard-observation.ps1`, `app/src/clipboardAudit/.../ClipboardTargetCopyActivity.kt`, and androidTest/androidHostTest/androidDeviceTest files — all now committed in `49e37f60`.
- T042 checkbox: unchecked; `independent-review.md` status `Unverified`
- Latest evidence validation: 546 records, zero errors, 1,548 artifact hashes, 5/5 synthetic rejections — exploratory only; no frozen candidate supplied
- Heap raw artifacts: all four owner groups regenerated 2026-11-20 under the ACL-protected `SEC065-heap` destination after the 2026-11-19 loss; passkey terminal frame-local retention reproduced across two runs and root-attributed as a direct JavaFrame `String` stack slot
- Open gates include T006/T007/T012/T016/T027/T029/T034/T035/T036/T037/T039/T040/T042/T044/T045 as recorded in `tasks.md`/`assessment.md`; candidate-bound reruns of T015/T027/T034/T041 evidence are now unblocked by the freeze and remain required before review
- Documentation integrity: residual U+FFFD corruption in six Feature 065 files repaired 2026-11-20 (`—`, `–`, `→`, `∈`, `§` restored contextually); repo-wide sweep clean. Validator remains 546 records / 0 errors / 1,548 hashes / 5 synthetic rejections. Register-repair claim matrix: 39 recorded owner-matrix claims independently re-verified against source with zero discrepancies

## Disposition

- **Packet status:** prepared.
- **T042 status:** `Unverified`.
- **Release acceptance:** not accepted.
- **Next action:** candidate `49e37f60` is frozen; refresh candidate-bound prerequisites (T036/T037/T041 and build/scope fingerprints), grant controlled artifact access to an independent reviewer, then complete `independent-review.md`.

## Current candidate update — 2026-11-22

The 2026-11-20 snapshot above is historical. The current local freeze candidate is:

- Commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`, branch `lab/or/chimali`; freeze commit was created locally with precommit checks and has not been pushed.
- Candidate product source is frozen at that commit. The current checkout contains follow-up evidence and validator edits; reviewers must inspect the frozen commit/tree and the separately identified evidence package, not assume the checkout is clean or identical.
- T111 candidate-bound heap evidence is retained in `evidence/passkey-heap-candidate-api35-20261122.md`; raw HPROFs and marker dictionary remain in the ACL-restricted `SEC065-heap` location. Candidate T111 report/XML/restricted artifact hashes are pinned in the evidence index.
- T112 candidate-bound Mi A2/API 29 clipboard cohort is documented in `evidence/clipboard-api29-frozen-candidate-20261122.md`, with nine sanitized reports pinned in the index. Locked/sleep ownership-unavailable skip passed; independent post-wake removal is Unverified. API 28 was explicitly waived by the owner.
- Candidate-mode validation for the declared commit/tree parses 623 records, recognizes eight candidate rows, verifies 1,691 linked artifact hashes, reports seven Passed/Satisfied claims and one Unverified clipboard outcome, and rejects all 8 synthetic invalid records. The full no-candidate historical audit reports 1 historical digest mismatch after the FIDO XML was regenerated; the validator report and digest-refresh note preserve this distinction.
- **Review readiness:** T113 is complete for candidate evidence traceability: `evidence/feature065-candidate-claim-coverage-20261122.md` maps FR-001–FR-016 and SC-001–SC-006 and names partial/unverified outcomes. Reviewer Octavio Rodríguez-Castañeda, repository owner (`LOQ15IRX10-3\octav`), attested non-authorship/material non-editing; Codex performed the edits in this session. The freeze commit's Git author field names the owner because the local commit used the configured repository identity. The reviewer confirmed access, completed the review, and supplied `Accept with limitations`; reviewed scope, raw artifact hashes and three findings are recorded in `independent-review.md`. T114 is complete; overall release acceptance remains Unverified.
- **Release decision:** The review disposition does not grant overall release acceptance. Keep release acceptance Unverified while the explicitly listed clipboard observation and vault/recovery heap evidence gaps remain unresolved.

## Reviewer handoff status — 2026-11-22

### FIDO report artifact availability correction — 2026-11-22

The candidate FIDO host row and the historical SEC065-ERR-019 row were reconciled after the validator found that report files at the cited paths no longer match their original expected digests. The index preserves the original fingerprints and marks the affected records Unverified. The reviewed report files are unavailable at those paths, so regenerated files have not been substituted. This changes evidence availability after the recorded review and does not change its stated scope or disposition.

This update supersedes the access and T036 statements above:

- The owner-reviewer attested they did not author or materially edit the remediation and confirmed access. They later reported review completion and supplied `Accept with limitations`; the candidate scope, restricted artifact hashes and findings are now recorded in `independent-review.md`.
- T036 inventory reconciliation has been refreshed to the exact candidate above: 736 file records / 640 usages, all 700 candidate `app/core/feature` source paths covered, zero hash/path/link/duplicate/open-disposition errors, and all 14 configured modules listed. The `core/crdt` orphaned bridge is recorded with a scoped no-current-use rationale. See `evidence/source-inventory-validation.md`.
- **Packet readiness:** The T114 review record is complete with an `Accept with limitations` disposition. The matrix's partial/unverified owner, clipboard and heap outcomes remain explicit; overall release acceptance remains Unverified until those requirements receive their required evidence and disposition.
- Before review, read the prerequisite checklist above, the evidence index, T111/T112 candidate reports and restricted heap artifacts, and the assessment's current disposition. Treat locked/sleep post-wake clipboard removal as Unverified; API 28 was waived and not tested.
