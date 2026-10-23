# Validation Guide: Documentation-Only Governance Package

## Prerequisites

- Read [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), the [review contract](contracts/documentation-review.md), and [proposed amendment](constitution-amendment.md).
- Resolve the active feature through `.specify/feature.json`; the intended Git branch is `codex/057-improve-verification-contracts`.
- Use current authority files and original dated vault/local-key security records. No Android device, app build, runtime test framework, benchmark harness, or network service is needed.
- During planning, checks establish artifact readiness only. During implementation, perform the same documentary review against the actual synchronized edits.

## 1. Inspect Scope

From the repository root, use read-only Git inspection:

```powershell
git branch --show-current
git status --short
git diff --name-only
git diff --check
```

Expected: only the documented Markdown deliverables and feature-selection metadata, including any already-present backlog refresh. Untracked feature files appear in status and require separate content/link review; an empty tracked diff alone is not validation of untracked files. No application/build/CI/test-code changes. Do not run local CI or runtime tests as part of this feature; no commit is requested here and existing commit policies are unchanged.

## 2. Review Amendment and Synchronization

1. Compare exact proposed wording with current Principle V; preserve all existing bullets and principles.
2. Confirm five backlog-area dispositions, rationale, provisional MINOR version impact, and BRD/TRD/rules/register mapping.
3. During implementation, review the complete synchronized package and record reviewer/date/revision/disposition. If approval is pending, verify it remains proposed and the live constitution is not presented as adopted.
4. On an approved adoption, verify actual amendment date/version, retained ratification date, and synchronized impact report. No deployment or security acceptance follows from this step.

Expected: FR-001/FR-002/FR-019 and SC-001 are traceable to concrete document review; no fabricated approval.

## 3. Review Validation and Registers

1. Read both validation/error paragraphs together and confirm consistent expected-failure/internal-assertion guidance, applicable entry paths, constraint classes, side effects, and cleanup requirements.
2. Trace at least one vault field and one local-key/passkey field through classification, ownership/lifetime, relevant documented platform usage, and original dated evidence.
3. Check all consolidated rows against the required fields. Missing facts/evidence must be explicit gaps with follow-ups, not blank implied permissions.
4. Verify no entry authorizes immutable cryptographic material or claims physical erasure of provider/runtime copies.

Expected: FR-003–FR-008 and SC-002/SC-003 satisfied by documentary consistency and reconciliation; no new source/device audit claimed.

## 4. Review Future Verification Matrices

1. Confirm two independent credential lifecycle matrices and all 12 failure-category applicability decisions from the review contract.
2. Confirm four accessibility task categories, correct naming/grouping policy, future scaling/contrast/screen-reader requirements, and preserved implementation deferral.
3. Confirm two minimum rendering journeys, all future contract fields, review responsibility, retained BRD numeric limits, and rules for missing/unsupported workloads.
4. Check host/device distinctions, expected response verification, partial persistence, future evidence metadata, and reporting statuses.

Expected: FR-009–FR-018 and SC-004–SC-006 satisfied as policies/procedures, without an executed test or performance result.

## 5. Review Links, Traceability, and Closure

- Resolve every relative link introduced by the feature from its containing file. Proposed future file paths may appear as code until those files exist; do not create broken links to unimplemented deliverables.
- Inspect new Markdown for unresolved template markers and trailing whitespace, including untracked files.
- Map every FR/SC to changed sections in the implementation review record. Keep documentary decisions distinct from future runtime statuses.
- Verify stable IDs/authority precedence and preserve `FR-UI-011`, feature-055 T084/T085/partial SC-007, device limitations, vault pending evidence, and fingerprint-scoped security dispositions.
- Ensure excluded code/test/benchmark/device work has separate follow-up records and the backlog distinguishes policy delivery from runtime completion.

Expected: SC-007 and all documentary acceptance criteria have explicit results or pending approval. Report document checks only; no builds, tests, benchmarks, or device audits were run.
