# Research: Manual Coverage Review

## Target routing

**Decision**: When `coverage.targetRef` is absent, return without parsing update rows or requiring prerequisites. Otherwise parse standard Git remote arguments and all update rows, rejecting malformed input, and normalize the configured target to an exact remote and full branch ref. A valid nonmatching push requires no coverage prerequisites. Block matching target deletion because it has no candidate. A failing matching update aborts the entire Git push.

**Rationale**: The current hook checks prerequisites before stdin and assesses every product ref. Git supplies remote name/URL and four fields per ref, including zero object IDs for deletion. [Git hook contract](https://git-scm.com/docs/githooks#_pre_push).

**Alternatives**: Prefix matching, assuming `origin/main`, or assessing current HEAD for every pushed ref can select the wrong target or candidate. Fail closed on ambiguity and candidate isolation failures.

## Verification and prerequisites

**Decision**: Reuse the trusted integration wrapper and coverage verifier with a reviewed pinned clean harness, exact candidate, stable external evidence root and compatible accepted baseline. Detect one authorized device only when unambiguous and verify capabilities; allow explicit overrides.

**Rationale**: The wrapper validates live target, ancestry, harness identity, coverage and baseline index/digest. An empty directory or connected device does not prove a usable baseline or full capability.

**Alternatives**: Selecting the first device, candidate-controlled authority, or an arbitrary baseline weakens evidence. Nonmatching pushes should not create evidence directories or need devices.

## Manual approval and promotion

**Decision**: An authenticated repository user with applicable access, or an agent explicitly acting for that user, reviews independently verified retrievable evidence using trusted configuration outside candidate content. Record the actor, PR, run/digest, current head/base and independently computed prospective merge tree; reject identity, access, conflict or tree failures. A reviewer-controlled publication and promotion path preserves identity, digest and concurrency checks. Verify the proposed tree before acceptance and actual integrated tree before promotion; a user may authorize their own agent and no separate bypass-exception record is required.

**Rationale**: The owner chose not to recalculate the baseline or repeat comparison solely when `main` advances. The original comparison does not prove nonregression against any newer baseline. If the proposed integrated tree differs from the tested tree, verify the changed tree before acceptance. Feature 058, BRD NFR-ARCH-050, TRD §2.2 and the integration/regression rule have been reconciled with that decision; a manual record cannot claim an App check. Rechecking head/base immediately before merge reduces races but is not an atomic GitHub merge guarantee; post-merge tree mismatch blocks promotion and is surfaced for remediation.

**Changed-tree path**: A developer merges the then-current `main` into the feature branch, resolves conflicts, commits and assesses that new feature revision; the new run identifies its assessment-time baseline and is published separately. The reviewer repeats the prospective-tree check. This uses the existing commit-based verifier without silently modifying the branch or inventing a synthetic candidate identity.

**Alternatives**: Candidate self-approval, pre-merge baseline advancement, an undocumented synthetic merge candidate, or assuming PR protection verifies coverage.

## Archive and branch migration

**Decision**: Preserve archive history and run bytes during the rename to `swe/coverage-evidence`; validate historical receipts against their original ref and new publications against the new ref. Update publisher, verifier, dormant workflows, fixtures and active docs; verify refs, protections, permissions, links and concurrent updates. Create `main` from the recorded `lab/or/main` commit and establish its compatible baseline.

**Rationale**: Exact legacy archive refs occur in publisher and receipt validation. The cached archive has no accepted baseline index and contains a documented synthetic trial. GitHub rename can affect protections and PRs, so verify actual settings. [GitHub branch rename](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-branches-in-your-repository/renaming-a-branch).

**Alternatives**: Empty replacement archive or rewritten historical receipts risk lost evidence or invalid provenance. Remote operations are rollout work, not planning outputs.

## Governance and documentation

**Decision**: Reconcile Feature 058 FR-012–017 and applicable governing documentation with manual PR acceptance while retaining constitution CI floors, trusted verification and the hosted waiver. Add a root README and detailed procedures.

**Rationale**: Feature 058 describes a hosted App-bound producer; the owner's manual decision is a material change. Historical records and immutable receipts remain historical.

**Alternatives**: Claiming hosted enforcement is active or silently weakening the verifier.
