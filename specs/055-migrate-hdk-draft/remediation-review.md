# Remediation of Cross-Artifact Analysis Findings

**Date**: 2026-10-14

**Authorization**: The user requested concrete remediation suggestions and application of those edits after the read-only analysis. These are specification/design/task corrections. Production code, application tests and the current constitution are not changed by this remediation.

## Applied Corrections

| Finding | Concrete correction | Updated artifacts / tasks |
| --- | --- | --- |
| C1 — transaction scope | Specify an explicit event-append transaction followed by an explicit atomic projection-replacement transaction. All related projection deletions/insertion commit together or roll back; prior projections survive failure/cancellation and an already committed event stays append-only. Test each failure boundary, typed error/cancellation and success ordering. | [Lifecycle contract](contracts/credential-lifecycle.md), [data model](data-model.md), [plan](plan.md), [quickstart](quickstart.md); T017, T041, T042, T022/T051. |
| C2 — executable Compose verification | Add an explicit move/update/configuration/execution task for the existing mnemonic Compose suite in `androidDeviceTest`; keep ViewModel tests and record named UI execution instead of assuming old `androidTest` sources are discovered. | Plan, quickstart, [tasks](tasks.md); new T021 and T051/T063. |
| C3 — Tier 2 module floors | Independently enforce affected Tier 1 statement/branch ≥40%, each affected Tier 2 statement ≥20%, and repository aggregate ≥20%. Identify `core:database`, `core:common`, `app` and additionally changed Tier 2 owners; merge real host/device data when necessary without duplicate denominators. Report Tier 2 branches and reject missing module data. | Plan, research, quickstart, security review; T001/T008/T064. |
| I1 — cleanup before compilation | Move retired implementation deletion and configured old-API test replacement into US1 before DI/compilation/host execution. Capture the caller inventory at setup and retain a final US2 debt audit. Add no compatibility wrappers to repair compilation. | Plan and tasks; T001, T047/T048 before T049/T050; US2 T054/T062. |
| A1 — session ownership | Define bounded operation owners, foreground/HID overlap, authorization, last-owner cleanup, disconnect, generation fencing on replacement/each required memory level, and lazy reconstruction of the same wallet. Check signing admission and provider return; discard stale in-flight results and document synchronous-provider interruption limits. | Lifecycle contract, data model, plan, research, security review, quickstart; T013/T022/T035/T037. |
| D1 — duplicate initialization requirement | Retain both requirement identifiers: FR-002 now specifies new P-256 profile selection for generation/signing; FR-009 exclusively specifies clean-install wallet/storage initialization. | [Specification](spec.md); existing requirement mapping remains valid. |

## Task Identity Changes

All items remain unchecked. Renumbering preserves sequential checklist order; no implementation completion is implied.

| Previous task IDs | Current task IDs |
| --- | --- |
| T001–T020 | Unchanged |
| New configured Compose task | T021 |
| T021–T045 | T022–T046 |
| T052: retired implementation removal | T047, now US1 |
| T053: retired test replacement | T048, now US1 |
| T046: DI/compile integration | T049 |
| T047–T051 | T050–T054 |
| T054–T068 | T055–T069 |

Current totals: **69 tasks**, comprising 3 setup, 5 foundational, **44 US1**, **10 US2**, and 7 cross-cutting tasks; **20** have conditional parallel markers. The feature retains **19 functional requirements** and **10 success criteria**.

## Review Disposition and Remaining Gates

The six reported artifact defects are addressed by the concrete corrections above. Static rechecks passed for 69 sequential task IDs, story/parallel counts, checklist labels/file paths, task references and local links across 13 feature Markdown documents. The transaction, session, coverage and cleanup ordering corrections are reflected in the contracts and execution dependencies. This record does not claim that the planned implementation passes its tests or achieves its coverage/security/performance targets.

The authorized constitution amendment remains T004 and the engineering/formal review gate remains T005. The current constitution is still 2.0.0; apply the separate explicit amendment before replacing production derivation. Actual production implementation, fixtures, test execution, measured gates and review signoff remain pending.
