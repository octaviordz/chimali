# Backlog: UI Accessibility Implementation and TalkBack Verification

**Status**: Deferred — BRD FR-UI-011 deferral remains active

**Created**: 2026-11-13 (split from feature 057 follow-ups)

**Tracking ID**: `FUP-ACC-001`

**Origin**: [Feature 057 follow-up index](../../specs/057-improve-verification-contracts/follow-ups.md) and [original combined backlog](../../specs/057-improve-verification-contracts/testability-backlog-origin.md). This file owns active scope, status and completion evidence for this work area.

## Context carried forward

- **Governing Area**: Universal Accessibility (Constitution Principle VIII, BRD `FR-UI-010` / `FR-UI-011`, TRD §6 / §9)
- **Mapped Feature Requirements**: FR-012, FR-013, FR-014
- **Remaining Scope**: Implementing high-contrast mode palettes, Compose Semantics trees for all screens, dynamic text scaling layout fixes up to 200%, and conducting physical TalkBack walkthrough audits.
- **Reason Excluded**: Full UI accessibility implementation is explicitly deferred in BRD `FR-UI-011`; feature 057 updates verification criteria and procedures only.
- **Current Verification Status**: `Deferred` (Governing deferral in BRD `FR-UI-011` remains active; verification contracts updated)
- **Lifecycle Status**: `Identified`
- **Preserved Authorization / Deferral Reference**: BRD `FR-UI-011` footnote: *"Note: Full implementation deferred to a future dedicated UI accessibility phase"*.
- **Responsible Role**: UI / Accessibility Engineer
- **Needed Completion Evidence**: Completed TalkBack walkthrough logs across the 4 representative journeys (vault, credential management, registration, authentication), automated Compose Semantics verification, and contrast ratio validation reports.

## Next steps

Plan the dedicated accessibility phase under the existing deferral. Implement needed semantics, contrast and layout corrections, then execute automated checks and physical TalkBack walkthroughs for vault, credential management, registration and authentication, including 200% scaling and explicit secret reveal.

## Evidence and completion

Retain Compose semantics/layout results, contrast measurements, and manual TalkBack reports in the implementing feature's accessibility evidence record, linked from the TRD. Include journey, device/OS, TalkBack version, theme, font scale, results, and unresolved defects.

Approved scope and contrast criteria; executed semantics/scaling/contrast checks; completed TalkBack journeys; evidence of essential action completion and appropriate secret disclosure; deferred/failing coverage explicitly tracked.

The existing implementation deferral remains active until the dedicated phase is taken up.
