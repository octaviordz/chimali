# Documentary Data Model

**Date**: 2026-10-23 | **Scope**: Markdown records only; no runtime or database model

## Amendment Record

Fields: proposal ID, exact principle/bullet, current/proposed wording, rationale, semantic-version impact, affected authorities/sections, package revision, reviewer identity, review date, disposition, adoption date when applicable.

Relationships: one amendment maps to all five policy-area dispositions and supporting edits. Its approval references the exact package revision, not an evolving draft.

Validation: no accepted/adopted status without reviewer/date/revision evidence; no adoption date for a pending proposal; preserve ratification date. Version justification must match the actual approved semantics.

Lifecycle: `Draft → Ready for review → Approved / Rejected / Changes requested`; `Approved → Adopted` only after synchronized authority edits and metadata updates. Material edits after approval require renewed review of the changed package.

## Data Classification Record

Fields: stable local record ID, field/category, documentary scope, sensitivity, owner, permitted representation, persistence restriction, logging restriction, lifetime, terminal cleanup obligations, governing requirements, dated source references, unresolved findings.

Relationships: one classification may be used by several boundary records. Identical text in multiple layers does not imply identical ownership/lifetime.

Validation: record secret/private material separately from public derivation metadata and privacy-sensitive associations. Unknown facts must be marked `Unresolved` with a follow-up reference; no secret values or identifying examples. Public metadata classification is not a blanket exception for related privacy-sensitive text.

Lifecycle: `Documented with gaps → Documentary reconciliation complete`; runtime compliance remains a separate assessment with its own dated evidence.

## Platform Text Boundary Record

Fields: local ID, classification references, exact dependency/API, documented usage location, reason no mutable interface exists, authorized purpose, reference lifetime, application release/control behavior, provider/runtime limitation, source date/revision, reviewer/date/disposition, existing evidence or gap, follow-up reference.

Validation: a register entry cannot create an exemption. Cryptographic key material, seeds, and serialization buffers never qualify for the platform UI text exception. Historical prohibited/internal usages are recorded as findings rather than approved boundaries. Missing reviewer evidence remains missing; the consolidating author is not automatically the prior security reviewer.

Lifecycle: `Documented → Reviewed permitted / Noncompliant / Unresolved`; historical review status remains scoped to original evidence. No transition implies current-source independent approval.

## Journey and Constraint Matrix

Fields: local case ID, governing requirement, entry path/task, phase or constraint class, setup, trigger, expected outcome, real/substituted boundaries, required future environment/evidence, applicability rationale, follow-up reference.

Validation: Bluetooth and Credential Manager have independent rows. Each covers registration, encrypted persistence, restart/reconstruction, and authentication/retrieval; failure coverage has six categories per path, included or justified as not applicable. Accessibility covers four task categories. Rendering covers two minimum journeys.

A matrix expresses required future evidence. `Specified` is a documentary status, not a passing runtime result.

## Evidence Policy and Historical Reference

Required future fields: requirement, scope, source revision, named test/manual procedure, environment, expected/observed result, run date, limitations, status, artifact reference.

Validation: historical evidence keeps original date/revision and limitations. Future runtime status is one of `Passed`, `Failed`, `Deferred`, `Unverified`; only executed results can be passed/failed. Unavailable environments are unverified; missing workload in an attempted run fails. No records may copy real secrets or identifying user data.

## Follow-Up Record

Fields: local ID, governing requirement/policy area, excluded code/test/device/measurement work, reason excluded, current verification status, preserved authorization/deferral reference, responsible role, completion evidence needed.

Lifecycle: `Identified → Assigned → Separately authorized → Completed with evidence`. This feature may identify/assign work but cannot mark excluded runtime work complete without its separate evidence.

## Documentary Review Result

Fields: package revision, reviewed document/section/record, mapped feature FR/SC, check performed, observed result, reviewer/date, open finding or follow-up, disposition.

Review outcomes: `Satisfied`, `Needs correction`, `Pending approval`. These are distinct from runtime passed/failed/deferred/unverified results. Satisfied document criteria do not erase underlying product gaps.
