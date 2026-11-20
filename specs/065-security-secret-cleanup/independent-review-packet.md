# Feature 065 independent security review packet — T042 preparation

**Status:** Prepared for a future independent review. This packet is not a review, finding, approval, or release acceptance. T042 remains `Unverified` until a real independent review is performed against a frozen product candidate.

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
- HEAD: `30825cc8713206aea35039675ca60860bf448c9e` (`chore(version): update speckit versions to 1.4.2`), tree `be71549cfafa64b65f6c3ba28aadfc5e2443305d`
- Feature delivery commit: `fe38031c9f9ee3aa4a0aee02df198d0c43813da8` (`feat: add clipboard observer and related tests for security enhancements CP`), tree `9c2e62a64aeaa1ec087b549d1fc8bc9085bf81a8`, created during the 2026-11-19 rebase of this branch onto `lab/or/chimali` (merge `a8e09810` of `lab/or/main`). The prior baseline commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57d` is preserved in reflog and remains the historical comparison point, not the current HEAD.
- Worktree: still dirty/exploratory; `git status --porcelain` reported 43 entries. Non-spec remainder includes production-relevant uncommitted changes: `core/domain/.../SecureRandomStatus.kt` (SEC065-CORE-033 fixed-cause remap), `config/detekt/detekt.yml`, `docs/security/data-classification.md`, `docs/security/platform-text-boundaries.md`, `tools/security/run-heap-capture.ps1`, `tools/security/run-clipboard-observation.ps1`, `app/src/clipboardAudit/.../ClipboardTargetCopyActivity.kt`, and androidTest/androidHostTest/androidDeviceTest files. `fe38031c` therefore does **not** contain the complete current remediation; freeze must commit or explicitly reconcile these first.
- T042 checkbox: unchecked; `independent-review.md` status `Unverified`
- Latest evidence validation: 546 records, zero errors, 1,548 artifact hashes, 5/5 synthetic rejections — exploratory only; no frozen candidate supplied
- Heap raw artifacts: all four owner groups regenerated 2026-11-20 under the ACL-protected `SEC065-heap` destination after the 2026-11-19 loss; passkey terminal frame-local retention reproduced across two runs and root-attributed as a direct JavaFrame `String` stack slot
- Open gates include T006/T007/T012/T016/T027/T029/T034/T035/T036/T037/T039/T040/T042/T044/T045 as recorded in `tasks.md`/`assessment.md`; candidate-bound reruns of T015/T027/T034/T041 evidence are still required after freeze
- Documentation integrity: residual U+FFFD corruption in six Feature 065 files repaired 2026-11-20 (`—`, `–`, `→`, `∈`, `§` restored contextually); repo-wide sweep clean. Validator remains 546 records / 0 errors / 1,548 hashes / 5 synthetic rejections. Register-repair claim matrix: 39 recorded owner-matrix claims independently re-verified against source with zero discrepancies

## Disposition

- **Packet status:** prepared.
- **T042 status:** `Unverified`.
- **Release acceptance:** not accepted.
- **Next action:** freeze the candidate, refresh candidate-bound prerequisites, grant controlled artifact access to an independent reviewer, then complete `independent-review.md`.
