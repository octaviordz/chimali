# Contract: Governance Documentation and Review

**Status**: Design contract; no software interface or new runtime evidence

## Deliverable Interface

The reviewer consumes a synchronized Markdown package: exact amendment, BRD/TRD/rules edits, two canonical registers, a documentary review record, and separate follow-ups. Record shapes are defined in [data-model.md](../data-model.md). Future procedure requirements are policies, not instructions to execute tests during this feature.

The implementation review record `../documentation-review.md` must map FR-001–FR-020 and SC-001–SC-007 to exact changed sections/rows, reviewer/date, observed documentary result, and unresolved issues. It must record actual amendment approval status separately from specification/document quality. The implementation follow-up record `../follow-ups.md` must identify excluded runtime work and preserve existing deferrals/limitations. These two records are created during implementation, not fabricated as complete during planning.

## Validation Policy Contract

The final rules must distinguish external expected rejection, internal invariant assertions, and unrecoverable technical failures in one consistent policy. UI/protocol/import/reconstruction inputs are covered by applicable contracts before sensitive execution/event append/persistence. Future tests address invalid classes and boundaries, exact failures, forbidden side effects, and owned cleanup; assertions must not leak ordinary external rejection at boundaries. Revalidation of already validated objects and invented fields for parameterless operations are not mandatory.

## Integration Matrix Contract

Each path requires lifecycle rows for registration, encrypted persistence, restart/reconstruction, and authentication/retrieval with applicable response verification. Identify setup, expected result, real/substituted boundaries, restart method, and future evidence/environment requirements.

The final TRD matrix must contain all following applicability decisions. This table specifies required decisions; it does not claim that tests exist or executed.

| Path | Failure category | Required documentary decision |
| --- | --- | --- |
| Bluetooth relying party | Malformed input | Include expected protocol rejection, no unauthorized output, and cleanup. |
| Bluetooth relying party | Denied verification | Include abort/no-signing-success and owned-secret cleanup. |
| Bluetooth relying party | Cancellation | Include terminal cleanup and documented persistence state. |
| Bluetooth relying party | Timeout | Include bounded termination, no late success, and cleanup. |
| Bluetooth relying party | Disconnect | Include transport/session termination and no stale response success. |
| Bluetooth relying party | Persistence failure | Include event/projection consistency and no false success. |
| Credential Manager | Malformed input | Include domain/provider failure mapping and forbidden side-effect absence. |
| Credential Manager | Denied verification | Include rejected creation/retrieval and cleanup. |
| Credential Manager | Cancellation | Include request cancellation, cleanup, and no delivered success. |
| Credential Manager | Timeout | Include when supported by the request contract; otherwise give an applicability rationale. |
| Credential Manager | Disconnect | Define relevant caller/provider loss if applicable; otherwise justify transport-disconnect inapplicability. |
| Credential Manager | Persistence failure | Include documented committed-history/projection result and failure mapping. |

No future test may require deletion of already committed append-only history to satisfy rollback. An isolated cipher roundtrip cannot stand in for either complete journey. Host/component evidence cannot certify physical transport/device behavior.

## Accessibility Policy Contract

The documented coverage matrix must have four task categories: vault, credential management, registration, and authentication. Each specifies future accessible-name/role/action/state/grouping inspection, text scaling through 200%, supported themes, contrast categories/criteria, and screen-reader navigation/task-completion procedure.

Visible text may provide names; decorative graphics may remain undescribed. Preserve touch-target/legibility requirements. Walkthrough procedure covers focus order, labels, errors/states, dialogs, essential actions, and secret disclosure, with future device/OS/assistive-technology version metadata.

Retain `FR-UI-011` deferral. Document review establishes criteria, not completed TalkBack/scaling/contrast results. Standards-level numeric additions must use authoritative references and remain consistent with existing BRD obligations.

## Rendering Policy Contract

Minimum journeys: vault list scrolling and password detail inspection. They are minimum coverage of the general obligation, not an exclusive scope.

Required future measurement fields:

- Device model, OS, refresh rate, production-matching variant, and compilation mode where relevant.
- Seeded dataset/size, initial state, navigation, expected visible content, and actual measured interaction.
- Warm-up, repetition count, measurement window, selected metric and interpretation, statistic, numeric threshold, and aggregation policy.
- Relationship to the existing 60-FPS, zero-frozen-frame (>700 ms), and <1%-slow-frame (>16 ms) obligations; distinguish processing duration from missed display deadlines.
- Measurement limitations, applicable method on unsupported/older devices, and retained environment metadata.
- Performance verification owner and engineering review of the completed contract before results may support compliance.

Concrete device selection, metric implementation and parameter approval are future work. This feature specifies the required fields and review responsibility. Missing workload in an attempted run must fail; missing hardware/capability remains unverified unless an applicable reviewed method exists. No compliance based only on collection/averages or inferred cross-device equivalence.

## Evidence and Status Contract

Documentary result: `Satisfied`, `Needs correction`, or `Pending approval` with exact section/revision and reviewer/date.

Future runtime result: `Passed`, `Failed`, `Deferred`, or `Unverified` with requirement, scope, source revision, procedure, environment, expected/observed outcome, date, limitations, and artifact links. No executed results are generated by this feature. Historical references retain their original source/date/scope; unsupported claims become findings rather than silently repaired history.

## Closure Contract

Document completion requires all five dispositions, synchronized authority links, populated registers with explicit documentary scope/gaps, complete policy matrices/fields, preserved IDs/budgets/deferrals, and an explicit governance disposition. Pending/rejected amendment adoption must remain visible and cannot be reported as an adopted constitution change.

Separate follow-ups must cover input-test/code gaps, current-source security audits, missing device/integration evidence, accessibility rollout/walkthroughs, benchmark discovery/targeting/workload fixes and measurements, and pre-existing coverage-policy discrepancies where encountered. No follow-up is marked runtime-complete by document acceptance.
