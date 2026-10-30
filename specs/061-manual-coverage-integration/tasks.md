---

description: "Dependency-ordered tasks for manual coverage review and target-specific pushes"
---

# Tasks: Manual Coverage Review for Integration

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [workflow contract](contracts/workflow.md), [quickstart.md](quickstart.md)

**Format**: `- [ ] T### [P?] [US#?] Action and path`. `[P]` means separate files and no dependency on incomplete tasks; story labels appear only in story phases. All task checkboxes are implementation state, distinct from reviewer-owned checklist markers.

## Phase 1: Setup

- [X] T001 Inventory current Git hook configuration, source changes, `lab/or/main`, archive refs and existing rules without mutating them; record identities, permissions, migration risks and hosted-waiver status in `specs/061-manual-coverage-integration/implementation-evidence.md`.
- [X] T002 Resolve the applicable coverage policy, baseline schema, fixture entry points and existing trusted harness requirements in `specs/058-enforce-coverage-targets/spec.md`, `tools/coverage/policy.json` and `specs/061-manual-coverage-integration/implementation-evidence.md`; identify available device capability and retain any unavailable prerequisite as unverified.

## Phase 2: Foundation

- [X] T003 Reconcile Feature 058 FR-012–017, `docs/brd.md` NFR-ARCH-050, `docs/trd.md` §2.2 and `.agents/rules/specify-rules.md` §1 with the owner-authorized manual PR decision: replace the current-target-baseline requirement with strict comparison against the accepted assessment-time baseline, retain the unchanged-tree reuse rule and require new verification when the proposed tree changes. Explicitly record that an older comparison does not prove nonregression against a newer baseline. Preserve constitutional CI floors, required suites, stable IDs, trusted evidence, historical records and the hosted waiver; follow applicable governance before integration and amend the constitution only if its wording requires it.
- [X] T004 Define the shared manual assessment, trusted reviewer and archive identity contracts in `specs/061-manual-coverage-integration/contracts/workflow.md` and `specs/061-manual-coverage-integration/data-model.md`: bind authenticated repository user or authorized agent, access and trusted configuration outside candidate content, candidate/head/prospective-tree/base, assessment-time baseline, harness, policy/scope, suites, raw counts, decisions, run/digest and timestamp; isolate publisher credentials from candidate execution, retain historical archive identities and define PASS/FAIL/INCOMPLETE and atomic promotion states.
- [X] T005 Design an isolated, exact-candidate verification boundary and inherited Git-environment cleanup for `tools/integration-ci.ps1` and `tools/coverage/pre-push-hook.ps1`; preserve target freshness at assessment time, pinned clean trusted harness, accepted-index/digest validation, required suite/floor/nonregression checks and explicit failure when an arbitrary pushed commit cannot be safely assessed.

## Phase 3: User Story 1 — Review coverage before integration (P1)

**Goal**: Provide a trusted archived report and a manual decision path for a PR into `main`.

**Independent test**: In isolated repositories and an evidence archive, review passing and missing, incompatible, incomplete, failed or tree-mismatched evidence; an advanced `main` alone does not require a new comparison when the tested and proposed trees are identical.

- [X] T006 [US1] Add isolated manual-review and publication fixture cases in `tools/coverage/fixtures/manual-review.json` and `tools/coverage/test-policy.ps1` for valid evidence, forged reviewer name, missing/insufficient repository access, agent acting for an authorized user, exposed candidate credentials, altered evidence, untrusted harness, wrong candidate/tree/baseline/policy and false hosted claims; assert baseline reuse when `main` advances with the same prospective tree, rejection of merge conflicts/unknown trees, and successful fresh verification of a new feature commit after merging the changed base.
- [X] T007 [US1] Implement a reviewer-controlled manual assessment and decision entry point in `tools/coverage/manual-review.ps1`, verifying the authenticated repository user's applicable access or an agent acting under that user's authority against trusted configuration outside candidate content; bind PR, reviewer, candidate/tree, assessment-time baseline, trusted policy/harness, report/run/digest, decision and timestamp, isolate publisher credentials from candidate execution, reject absent/invalid/untrusted evidence and do not invent hosted App status or a bypass exception record.
- [X] T008 [US1] Implement and validate pre-acceptance proposed merge-tree comparison in `tools/coverage/manual-review.ps1` and `tools/coverage/fixtures/manual-review.json`: resolve authenticated current PR head and `main` base, compute the prospective tree in an isolated non-mutating Git context, reject conflict or unavailable identity, compare with the sealed tested tree, record head/base/tree/result and recheck identities immediately before merge; a base advance with the same tree must not trigger a new coverage comparison. When the tree differs, direct the developer to merge the current `main` into the feature branch, resolve conflicts and assess the resulting new commit before repeating the tree check; do not modify or push their branch automatically.
- [X] T009 [US1] Adapt `tools/coverage/report.ps1` and `tools/coverage/publish.ps1` so a trusted manual-review publication path can produce a specific retrievable report link without weakening verifier authority, immutable bundle integrity, credential boundaries or existing historical/hosted receipt semantics.
- [X] T010 [P] [US1] Document the author/reviewer evidence procedure in `specs/061-manual-coverage-integration/quickstart.md`: original accepted baseline and strict comparison, exact proposed tree, required test/floor outcomes, PR report link, authorized review, failure handling and no recheck solely because `main` advances; retain a requirement to verify a changed proposed tree before acceptance.
- [X] T011 [US1] Run the isolated manual-review fixtures from `tools/coverage/test-policy.ps1` and record counts, expected rejections and resulting artifacts in `specs/061-manual-coverage-integration/implementation-evidence.md`; mark unexecuted hosted verification explicitly unverified.

## Phase 4: User Story 2 — Feature branches and optional assessment (P1)

**Goal**: Keep unrelated feature pushes free of mandatory coverage and provide an explicit local assessment.

**Independent test**: With an isolated remote and no device/configuration, push a nonmatching feature ref; separately run a manual assessment with controlled valid and missing prerequisites.

- [X] T012 [US2] Extend `tools/coverage/fixtures/run-pre-push-hook-fixture.ps1` and `tools/coverage/fixtures/pre-push-hook.json` with isolated Git configuration and real hook argument/stdin cases for unset target including malformed input, configured target with malformed input, empty stream, nonmatching feature refs and another remote; assert an unset target returns without parsing and no nonmatching push requires device, harness, evidence directory or coverage invocation.
- [X] T013 [US2] Refactor `tools/coverage/pre-push-hook.ps1` to return before parsing when no target is configured; otherwise validate the target and rows and identify exact nonmatching remote/ref updates before checking prerequisites. Allow valid nonmatching pushes without coverage side effects; until T026 implements target verification, fail closed for matching updates. Preserve the positional argument fix and avoid invoking the hook on local merges.
- [X] T014 [US2] Add unambiguous authorized-device discovery and stable external evidence-location setup with explicit overrides and actionable missing/multiple/unsupported diagnostics in `tools/setup-hooks.ps1` and `tools/coverage/setup-host.ps1`; require a reviewed clean pinned harness and verified accepted baseline rather than inferring trust from the candidate checkout.
- [X] T015 [US2] Expose and document an explicit feature-revision assessment using `tools/integration-ci.ps1` and `specs/061-manual-coverage-integration/quickstart.md`; bind the exact candidate, selected target at assessment time, verified device, trusted harness and indexed baseline, and retain complete PASS/FAIL/INCOMPLETE evidence. Support a new feature commit containing the current `main` when the previously assessed prospective merge tree differs, and publish a new report without modifying the earlier run.
- [X] T016 [P] [US2] Create `README.md` with high-level feature-branch, `coverage.targetRef`, optional assessment, target-push, PR evidence/review, merge-commit and baseline workflow instructions, linking `specs/061-manual-coverage-integration/quickstart.md` and distinguishing planned/unavailable hosted behavior.
- [X] T017 [US2] Run the isolated nonmatching-push and manual assessment prerequisite cases in `tools/coverage/test-policy.ps1`, and record results and any device limitation in `specs/061-manual-coverage-integration/implementation-evidence.md`.

## Phase 5: User Story 4 — Baseline and durable archive (P1)

**Goal**: Preserve the coverage archive and maintain an accepted baseline tied to verified integrated trees.

**Independent test**: In a local bare archive, migrate historical bundles, validate old/new receipt identities, establish an initial baseline and exercise matching, mismatched and concurrent promotion without a hosted check.

- [X] T018 [US4] Add isolated migration and promotion cases in `tools/coverage/fixtures/publication.json` and `tools/coverage/fixtures/run-publisher-promotion-fixture.ps1` for preserved historical bytes/ref/receipts, new archive ref, synthetic-run rejection, digest corruption, collisions, read-back failure and concurrent index changes.
- [X] T019 [US4] Update `tools/coverage/coverage-common.ps1`, `tools/coverage/publish.ps1` and `.github/workflows/coverage-report.yml` to use exact `refs/heads/swe/coverage-evidence` for new publication while validating legacy receipts against their original identity; retain immutable run IDs, full historical history and atomic conflict/read-back checks without activating the waived hosted worker.
- [X] T020 [US4] Implement trusted manual baseline promotion in `tools/coverage/publish.ps1` and `tools/coverage/manual-review.ps1`: validate authorized review, original assessment baseline, archived bundle digest, policy/scope, actual integrated/tested tree equality and accepted-index expected-old-ref; record a manual decision without inventing hosted App or merge-event receipts.
- [X] T021 [US4] Implement initial baseline discovery or capture in `tools/coverage/capture.ps1` and `tools/coverage/verify.ps1` so **initial baseline acceptance** requires evidence identifying the exact `lab/or/main` commit used for `main`, required device/suites and compatible policy; an empty evidence root or synthetic trial must never become an accepted baseline. Preserve normal verification of later candidates and later accepted baselines using their own recorded identities.
- [X] T022 [US4] Prepare and execute the archive migration and `main` creation rollout described in `specs/061-manual-coverage-integration/quickstart.md`: record exact source/old/new refs, retained commit and bundle digests, existing GitHub protections and permissions, preserve recoverability, verify read-back and establish `main` at the recorded `lab/or/main` commit; do not overwrite concurrent archive updates.
- [X] T023 [US4] Configure and verify GitHub PR, merge-commit and `octaviordz`-only bypass rules for `main`, and capture or validate the initial baseline before first PR acceptance; record actual identities, settings and unavailable permissions in `specs/061-manual-coverage-integration/implementation-evidence.md` without creating a bypass exception-record requirement.
- [X] T024 [US4] Run isolated archive, legacy-receipt, promotion, corruption and race fixtures in `tools/coverage/test-policy.ps1` before live rollout; record counts and synthetic scope in `specs/061-manual-coverage-integration/implementation-evidence.md` without claiming actual remote or baseline status.

## Phase 6: User Story 3 — Check pushes to a configured target (P2)

**Goal**: Assess only exact target updates and prevent unverified target pushes.

**Independent test**: Push controlled matching, nonmatching, multi-ref and deletion updates to isolated local remotes and inspect verifier calls and exit outcomes.

- [X] T025 [US3] Extend `tools/coverage/fixtures/run-pre-push-hook-fixture.ps1` and `tools/coverage/fixtures/pre-push-hook.json` with exact remote/ref mapping, slash-containing branches, same name on another remote, mixed refs, deletion, malformed/ambiguous input, unavailable prerequisites, stale target and failure propagation; isolate Git config from the caller.
- [X] T026 [US3] Implement exact configured remote and destination-ref matching in `tools/coverage/pre-push-hook.ps1`; assess each matching non-deletion candidate, block matching target deletion and ambiguous input, prevent archive exemption from bypassing a matching product target, and report that a failing match aborts the whole push.
- [X] T027 [US3] Implement or integrate exact candidate isolation and live target/accepted-index checks in `tools/integration-ci.ps1` and `tools/coverage/pre-push-hook.ps1`; clear inherited Git environment when accessing another checkout, preserve clean reviewed harness and no-skip verification, and fail closed when isolation or evidence is unavailable.
- [X] T028 [US3] Run real Git-hook argument/stdin and multi-ref fixtures through `tools/coverage/test-policy.ps1`; record candidate/ref mapping, exit behavior and fixture isolation evidence in `specs/061-manual-coverage-integration/implementation-evidence.md`.

## Phase 7: Polish and Cross-Cutting Verification

- [X] T029 [P] Review trusted reviewer, harness, publication and baseline authority boundaries in `tools/coverage/manual-review.ps1`, `tools/coverage/publish.ps1` and `tools/coverage/coverage-common.ps1`; correct feature-introduced gaps without granting candidate-controlled approval or weakening CI gates.
- [X] T030 [P] Synchronize current instructions and branch references in `README.md`, `specs/058-enforce-coverage-targets/quickstart.md`, `specs/058-enforce-coverage-targets/contracts/coverage-workflow.md` and `specs/061-manual-coverage-integration/quickstart.md`; preserve dated historical references and distinguish local feedback, manual acceptance and unverified hosted controls.
- [X] T031 Run applicable static checks and `tools/coverage/test-policy.ps1`, then `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` with a capable connected device; record actual commands, source/harness identities, suite counts, coverage decisions and missing prerequisites in `specs/061-manual-coverage-integration/implementation-evidence.md`.
- [X] T032 Execute the controlled end-to-end manual scenario in `specs/061-manual-coverage-integration/quickstart.md`: feature push, optional assessment, report retrieval, reviewer acceptance/rejection, merge-tree comparison and atomic baseline promotion; retain real results and remote-rule verification in `specs/061-manual-coverage-integration/implementation-evidence.md`, and never represent unavailable hosted checks as passing.
- [X] T033 Reconcile task completion, implemented commands, README links, Feature 058 governance and actual rollout status in `specs/061-manual-coverage-integration/spec.md`, `specs/061-manual-coverage-integration/plan.md`, `specs/061-manual-coverage-integration/quickstart.md` and `specs/061-manual-coverage-integration/implementation-evidence.md`; preserve reviewer-owned checklists and historical immutable evidence.

## Dependencies & Execution Order

- Setup T001–T002 → Foundation T003–T005. Governance reconciliation must precede accepting an integration under the revised policy, not merely precede a compliance claim.
- US1 T006–T011 uses isolated fixtures; US2 T012–T017 delivers nonmatching pushes and manual assessment; US4 T018–T021/T024 implements and validates archive/baseline behavior; US3 T025–T028 completes matching target routing. Run US4 T024 before live rollout despite its numbering. A real first PR requires the implemented review and archive path, a compatible baseline and verified GitHub rules.
- Shared files are sequential: T006 → T007 → T008 → T009; US1 publication T009 precedes US4 T019–T020; US2 hook T013 precedes US3 T026–T027; all `test-policy.ps1` and shared fixture edits are integrated sequentially. T029 security review and T031 current-source CI/coverage run occur after all affected local code and fixtures; repeat them if later source changes invalidate their results.
- Live rollout T022–T023 occurs only after T024 and T025–T029 and T031 pass, with a reviewable migration inventory and resolved governance. T030 documentation can be drafted independently and reconciled after rollout; T032 end-to-end demonstration and T033 final reconciliation follow rollout. Preserve explicit missing-permission/device/waiver outcomes and do not infer remote success from fixtures.

## Parallel Opportunities

- After T005, the independent manual-review fixture design T006 and target-routing fixture design T012 may proceed in parallel only while they edit distinct fixture files; serialize any shared `test-policy.ps1` changes.
- US1 documentation T010 and US2 README T016 can proceed in parallel after their workflow contracts are stable. US4 migration cases T018 and US3 routing cases T025 can be designed in parallel only in their separate fixture files; shared publication, hook and policy edits remain sequential.
- T030 documentation reconciliation may proceed alongside T029's security review when their file sets are separate; T031–T033 require the resulting changes to be integrated.

## Implementation Strategy

Deliver the US1 manual review path against isolated evidence as the MVP, with no claim of live `main` readiness. Then implement US2, US4's local archive/baseline behavior and US3, and pass local security and CI verification before live US4 rollout. Complete the controlled integration before declaring the full workflow ready. Existing CI coverage gates and the hosted rollout waiver remain in force throughout.

## Phase 8: Convergence

- [X] T034 CRITICAL Reconcile the Feature 061 plan's rollout status with the final evidence in `specs/061-manual-coverage-integration/implementation-evidence.md`, preserving the hosted-enforcement waiver and historical observations per Constitution VII and T033 (contradicts).
- [X] T035 Verify the repository default branch and, if still `lab/or/chimali`, set it to `main`; read back the configuration and record the result and any unavailable permissions in `specs/061-manual-coverage-integration/implementation-evidence.md` per FR-001 and plan: primary integration branch (partial).

## Phase 9: Convergence

- [X] T036 CRITICAL Reconcile the Phase 0 research status sentence in `specs/061-manual-coverage-integration/plan.md` with the recorded verified local and remote rollout outcomes, while preserving the hosted-enforcement waiver and unverified status, per Constitution VII and T034 (contradicts).

## Phase 10: Convergence

- [X] T037 CRITICAL Reconcile the rollout-status statement in `specs/061-manual-coverage-integration/plan.md` to distinguish the `main` creation commit from its recorded post-merge tip, using `implementation-evidence.md` and preserving historical evidence per Constitution VII and T033 (contradicts).
