# Backlog: Coverage Policy Reconciliation Closure Evidence

**Status**: Open — policy implemented; formal closure evidence outstanding

**Created**: 2026-11-13 (split from feature 057 follow-ups)

**Tracking ID**: `FUP-GOV-001`

**Origin**: [Feature 057 follow-up index](../../specs/057-improve-verification-contracts/follow-ups.md) and [original combined backlog](../../specs/057-improve-verification-contracts/testability-backlog-origin.md). This file owns active scope, status and completion evidence for this work area.

## Context carried forward

- **Governing Area**: DO-178B Testing Gates & Governance (Constitution Principle V, Rules §1, BRD §9)
- **Mapped Feature Requirements**: FR-001, FR-019, FR-020
- **Excluded Work at feature 057**: Amending BRD §9 or Constitution Principle V to align the "100% coverage of core business logic" statement with the constitutional tier gates (Tier 1 ≥ 40%, Tier 2 ≥ 20%, global floor ≥ 20%).
- **Reason Excluded**: Pre-existing discrepancy noted in research; this feature does not silently alter established coverage floors.
- **Current Verification Status**: Policy discrepancy resolved in the working tree on 2026-10-28 at the owner instruction: BRD §9 no longer specifies a separate 100% core-business-logic coverage requirement ; NFR-ARCH-050 and Rules §1 retain the coverage floors. The current constitution and feature 058 define the coverage floors. Historical references to preserving the former objective describe the policy in force at the time. This resolution does not establish hosted enforcement or other runtime compliance.
- **Lifecycle Status**: Resolution implemented; formal closure pending reviewer identity and committed revision evidence required by this register.
- **Authorization / Evidence**: Owner instruction dated 2026-10-28; working-tree changes to `docs/brd.md` §9 and this entry, based on commit `db360428`. The 60% Tier 1 requirement remains in Constitution Principle V, BRD NFR-ARCH-050, TRD §2.2, Rules §1, and `tools/coverage/policy.json`.
- **Responsible Role**: Lead Architect / Governance Committee
- **Needed Completion Evidence**: Record reviewer identity and the committed revision containing the resolution; no additional coverage-policy reconciliation is identified.

## Next steps

Identify the committed revision that removed the BRD §9 separate 100% objective under the 2026-10-28 owner instruction. Record the actual reviewer, date, decision and committed revision. Preserve coverage floors and the hosted-work waiver; the corrected-package approval explicitly left this separate item open.

## Evidence and completion

Retain the closure decision with the implementing feature evidence and link it from this item.

Reviewer identity, date, actual decision and committed resolution revision recorded; current coverage floors and the hosted-work waiver preserved; no runtime or hosted pass inferred.

The [corrected-package approval](../../specs/057-improve-verification-contracts/corrected-package-review.md) explicitly left this separate item open.
