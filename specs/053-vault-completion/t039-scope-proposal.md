# T039 proposal: application-owned plaintext cleanup with audited platform boundaries

**Status:** Approved by the user and applied. **Date:** 2026-09-08.
**Authorization:** The user authorized a separate scope proposal, including a Constitution amendment if needed. The user subsequently approved the final wording. Constitution 1.0.0 and synchronized feature requirements are effective; T039 itself is not complete.

## Recommended decision

Adopt the attached proposed Constitution revision and the feature amendments below together. Guarantee explicit cleanup of application-owned mutable data and release of application references. Permit only unavoidable, audited platform text adapters with stated retention controls and residual risks. Do not claim erasure of copies outside application control.

A Constitution amendment is necessary because current I.5 requires all decrypted sensitive data to be mutable. This is a substantive narrowing, not a wording clarification. Proposed version: **0.17.0 -> 1.0.0**, a major change. The full candidate is [constitution.proposed-t039.md](../../.specify/memory/constitution.proposed-t039.md); `.specify/memory/constitution.md` now contains the approved amendment.

## Evidence and tradeoff

The resolved Compose foundation 1.11.1 source and `ComposeSecretRetentionTest` show immutable text snapshots surviving logical field clearing. Installed Android SDK 36.1 `TextUtils.writeToParcel` calls `CharSequence.toString()` before writing text to a parcel. The sources and reproduction are recorded in `research.md`; these are not whole-device measurements.

The proposal enables ordinary platform editing, rendering and requested copying without making an unsupported erasure promise. Residual risk remains: platform snapshots, runtime copies, keyboards, accessibility services and clipboard consumers may retain plaintext after the app clears its owners. A heap sample cannot prove absence of every such copy. This proposal does not protect against a compromised OS or a malicious input/accessibility service.

## Proposed feature requirement replacements

Replace **FR-VAULT-026** with:

> The system MUST erase all application-owned mutable sensitive values and release application-owned secret references when their authorized operation or session ends. This includes replacement, successful save, confirmed discard, disposal, duplicate rejection, partial processing, failure before encryption, and cancellation before or during work. An active editor MAY retain its independent mutable draft through a pending save or recoverable failure; consuming a submission MUST NOT erase that retry draft. Returned details MUST be freshly retrieved rather than reuse erased data.
>
> The policy covers usernames, passwords, websites, cardholder names, card numbers, expiration dates, CVVs, notes, titles, and custom-field names/values. Authorized list-title display does not exempt title ownership from this policy. App models, drafts, baselines and payload serialization MUST NOT retain sensitive immutable Strings.
>
> Platform text boundaries MAY use only the audited exception in Constitution I.5. Each boundary MUST identify its API/dependency version, fields, purpose, lifetime, controls and residual copying risk. App-controlled restoration/history/caching and optional disclosure MUST follow that exception. Reference release or visible clearing MUST NOT be represented as erasure of immutable platform copies.

Replace **SC-VAULT-005** with:

> After a detail/editor session ends, none of its sensitive values remain visible through that session, its application-owned mutable buffers are zeroed, and its application-owned secret references are released. An independently owned active editor or authorized list display MAY retain its own data for its documented lifetime. Verification MUST inspect retained application-buffer references and actual lifecycle behavior, and verify each platform adapter's configured retention/disclosure controls. External runtime/platform copies are documented residual risks, not falsely reported as erased.

Amend **SC-VAULT-011**: retain the complete all-type success/discard/disposal/replacement/rejection/failure/cancellation matrix, but qualify "retain none of their sensitive contents" as application-owned storage and references. Add assertions for the platform controls and documented boundary inventory. Unavailable checks remain unverified.

Keep **FR-VAULT-027** clipboard-on-request/60-second clearing, **FR-VAULT-034** retry/session behavior, **FR-VAULT-035/SC-VAULT-012** compatibility, all encryption requirements, field classification, and Constitution XII.3 coverage requirements unchanged. Clearing the clipboard does not erase copies already read by another consumer.

## Proposed plan and task alignment upon approval

- Plan 6.1: replace the universal framework-erasure feasibility gate with a mandatory per-boundary adapter/retention-controls audit under amended I.5. Keep the prohibition on plaintext app saved state. Current widgets are candidates to audit, not automatically approved.
- Plan 6.2: retain every application owner, independent-copy, failure/cancellation and terminal cleanup obligation. Keep explicit success/discard cleanup plus disposal fallback.
- Plan 6.4: distinguish owned-buffer erasure, owned-reference release, platform control checks and documented residual exposure. Keep actual runtime tests and strict critical-path coverage gates.
- T055: complete the per-field/platform-boundary inventory and choose/justify the smallest compliant adapter approach, using resolved versions and runtime evidence.
- T056/T057/T058/T061: retain mutable ownership, crypto cleanup and independent format compatibility work; review existing code against the revised boundary rather than resetting task history.
- T059: audit and implement permitted adapters in entry/detail/list/clipboard flows. `VaultItem.title` and other retained application String models still require remediation or separately justified classification; this proposal grants no blanket metadata exception. Prevent unnecessary app-controlled save/restore, history or caching. Test reveal, multiline editing, copying and supported accessibility behavior.
- T060: execute real navigation/lifecycle cases, including late callbacks, pending disposal, system back and recreation.
- T062/T039: remain open until all revised requirements are implemented and verified. Do not reuse passing array tests as platform-control or device proof; do not relax coverage.

## Approval and rollout

Approval is for the combined policy and feature scope above. On approval, apply the proposed Constitution wording/version, synchronize `spec.md`, `plan.md`, `tasks.md`, `research.md`, `data-model.md` and the crypto contract/verification guide, then continue implementation and verification. The approved wording has been applied to the active governance and feature documents. No application behavior is automatically declared compliant by that documentation update.

An Android test runtime is still needed for connected verification. This proposal resolves a policy question only; it neither supplies a runtime nor marks unavailable tests passed.
