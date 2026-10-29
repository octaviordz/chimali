# Follow-Up Work Register: Verifiable Security and Quality Contracts

**Branch**: `codex/057-improve-verification-contracts`  
**Date**: 2026-10-23  
**Status**: Active Follow-Up Tracking  
**Last Reviewed**: 2026-10-28
**Lifecycle Stages**: `Identified → Assigned → Separately authorized → Completed with evidence`

---

## Overview

This register tracks all engineering, testing, benchmarking, hardware audit, and policy work identified during the `specs/057-improve-verification-contracts` feature that is excluded from this feature's documentation-only scope. In accordance with Constitution Principle V and the feature specification, **no excluded runtime or implementation work may be marked complete without separate, dated, reproducible execution evidence.**

### Mandatory Model Constraints & Validation Rules (from `data-model.md`)
- **"no accepted/adopted status without reviewer/date/revision evidence"**: No entry in this register, nor any amendment proposal, may be transitioned to an accepted or adopted state without explicit reviewer identity, timestamp, and commit SHA/tree revision evidence.
- **"no adoption date for a pending proposal"**: Proposal records and follow-ups awaiting authorization must not carry adoption dates.
- **"preserve ratification date"**: Any subsequent governance action must preserve the constitutional ratification date (`2026-02-19`).
- **Final Adoption Gate**: Enacting changes in live authorities requires affirmative engineering approval for the concrete review package. Documentary completion of this feature identifies and assigns follow-up work, but does not execute it or close runtime gaps.

Existing implementation deferrals (e.g., BRD `FR-UI-011`), historical security review boundaries, and feature-055 performance deferrals remain fully preserved.

---

## Register Entries

### FUP-SEC-001: Current-Source Platform Text & Secret Cleanup Audit
- **Governing Area**: Cryptographic & Memory Security (Constitution Principle I, Rules §5.5, TRD §5.1, BRD `NFR-SEC-010` / `030`)
- **Mapped Feature Requirements**: FR-006, FR-007, FR-008
- **Excluded Work**: Static code inspection across production source code to audit all platform UI text boundaries and memory zeroing; dynamic runtime heap inspections; physical verification of clipboard 60s timeout on physical Android devices.
- **Reason Excluded**: Feature 057 is strictly documentation-only; historical evidence is consolidated without certifying new source audits. "Runtime compliance remains a separate assessment with its own dated evidence."
- **Current Verification Status**: `Unverified` (Documentary consolidation complete; live source audit pending)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: Vault ownership changelog (`docs/changelogs/2026-09-09-vault-secret-ownership-remediation.md`), Vault handoff (`specs/053-vault-completion/handoff.md`), and feature-055 security review (`specs/055-migrate-hdk-draft/security-review.md`) retain their original dated scopes and commit fingerprints. Consolidation MUST NOT expand reviewer fingerprints or provider-erasure assurance.
- **Responsible Role**: Security Lead / Security Engineer
- **Needed Completion Evidence**: Formal security audit report against the current release branch, recording commit hash, verified files, confirmed absence of string secrets, and heap dump verification of terminal cleanup. Documented handling of residual provider limitations (BouncyCastle `BCMLDSAPrivateKey` lack of destroy, JCA `BigInteger`, ICU native heap, and IME caches) without false erasure claims. Explicit audit of identifying RP/user account strings, DB projection models, and CTAP request models in memory to verify absence of plaintext retention, improper audit logging, and evaluate residual GC-collected String instances.

---

### FUP-SEC-002: Negative-Test Suite Implementation & Boundary Validation
- **Governing Area**: Input Validation & Error Handling (Constitution Principle III & V, Rules §1 / §5.2, TRD §9.3)
- **Mapped Feature Requirements**: FR-003, FR-004, FR-005
- **Excluded Work**: Writing and executing unit/integration negative test suites covering distinct invalid input classes, boundary values, parameterless authorization checks, and forbidden side effects.
- **Reason Excluded**: Feature 057 defines documentation contracts and rules; test implementation and execution are excluded. "Runtime compliance remains a separate assessment with its own dated evidence."
- **Current Verification Status**: `Unverified` for the complete contract. Feature 058 implemented and executed selected negative and cleanup cases, including COV-021 and COV-022; see [test-selection.md](../058-enforce-coverage-targets/test-selection.md) and [implementation-evidence.md](../058-enforce-coverage-targets/implementation-evidence.md). A complete constraint-to-test mapping and execution evidence for all applicable boundaries remain outstanding.
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: Constitution Principle V testing gates; rules §5.2.
- **Responsible Role**: Core Domain / Security Engineer
- **Needed Completion Evidence**: Executed test suite reports in `commonTest` asserting typed `Outcome.Failure` or protocol mapping on invalid inputs, zeroing of working buffers, and no uncaught exceptions escaping component boundaries.

---

### FUP-INT-001: Physical Bluetooth HID Transport & Multi-Device Interoperability Testing
- **Governing Area**: Virtual Authenticator & Bluetooth Transport (Constitution Principle VIII, BRD `FR-HID-010`, `FR-VAL-010`, TRD §4.2 / §9)
- **Mapped Feature Requirements**: FR-009, FR-010, FR-011
- **Excluded Work**: Execution of physical Bluetooth HID pairing, connection maintenance, fragment handling, CTAP2 packet exchange, and failure recovery across real Android devices and desktop hosts (Windows, macOS, Linux).
- **Reason Excluded**: Feature 057 establishes documentary lifecycle and failure matrices; physical transport test execution is excluded.
- **Current Verification Status**: `Unverified` (Matrices specified; physical hardware runs pending)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: BRD `FR-VAL-010` multi-device interoperability requirement; TRD §4.2.
- **Responsible Role**: QA / Bluetooth Hardware Verification Engineer
- **Needed Completion Evidence**: Dated test execution reports covering reference Android devices (API 28+) connected to Windows/macOS/Linux hosts, with capture logs demonstrating successful ceremonies and safe termination under disconnect, timeout, and malformed input.

---

### FUP-INT-002: Android Credential Manager End-to-End Verification
- **Governing Area**: Passkey Integration (Constitution Principle VIII, BRD `FR-PASS-010`, TRD §4.3 / §9)
- **Mapped Feature Requirements**: FR-009, FR-010, FR-011
- **Excluded Work**: Execution of passkey creation, retrieval, and cancellation flows against the Android Credential Manager API on device/emulator.
- **Reason Excluded**: Feature 057 provides integration specifications; running on-device passkey ceremonies is excluded.
- **Current Verification Status**: `Unverified` (Matrix documented; runtime execution pending)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: TRD §4.3 and §9 integration matrix.
- **Responsible Role**: Android Client Engineer
- **Needed Completion Evidence**: Test run reports from Android instrumented test suite demonstrating successful registration/assertion and graceful degradation on cancellation, denial, and persistence failure.

---

### FUP-ACC-001: Full UI Accessibility Implementation & TalkBack Audit
- **Governing Area**: Universal Accessibility (Constitution Principle VIII, BRD `FR-UI-010` / `FR-UI-011`, TRD §6 / §9)
- **Mapped Feature Requirements**: FR-012, FR-013, FR-014
- **Excluded Work**: Implementing high-contrast mode palettes, Compose Semantics trees for all screens, dynamic text scaling layout fixes up to 200%, and conducting physical TalkBack walkthrough audits.
- **Reason Excluded**: Full UI accessibility implementation is explicitly deferred in BRD `FR-UI-011`; feature 057 updates verification criteria and procedures only.
- **Current Verification Status**: `Deferred` (Governing deferral in BRD `FR-UI-011` remains active; verification contracts updated)
- **Lifecycle Status**: `Identified`
- **Preserved Authorization / Deferral Reference**: BRD `FR-UI-011` footnote: *"Note: Full implementation deferred to a future dedicated UI accessibility phase"*.
- **Responsible Role**: UI / Accessibility Engineer
- **Needed Completion Evidence**: Completed TalkBack walkthrough logs across the 4 representative journeys (vault, credential management, registration, authentication), automated Compose Semantics verification, and contrast ratio validation reports.

---

### FUP-PERF-001: Rendering Macrobenchmark Setup, Parameter Selection, & Compliance Execution
- **Governing Area**: Performance Vitals & Rendering Smoothness (Constitution Principle VI, BRD `NFR-PERF-020`, TRD `TR-PERF-010` / §9)
- **Mapped Feature Requirements**: FR-015, FR-016, FR-017
- **Excluded Work**: Implementing new Macrobenchmark target screens (e.g. `PasswordDetailScreen`), fixing existing benchmark harness issues, selecting reference hardware models/compilation modes, and executing performance runs.
- **Reason Excluded**: Feature 057 preserves existing budgets and specifies measurement contract fields; selecting hardware and executing benchmarks is excluded.
- **Current Verification Status**: `Deferred` / `Unverified` (Budgets retained; feature-055 performance deferrals intact; benchmark run pending)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: Feature-055 T084/T085 and partial SC-007 performance deferral; BRD `NFR-PERF-020`.
- **Responsible Role**: Performance Verification Owner / Android Performance Engineer
- **Needed Completion Evidence**: Reviewed and approved measurement contract document, followed by benchmark execution reports on reference devices confirming consistent 60 FPS, 0 frozen frames (>700ms), and <1% slow frames (>16ms) on vault list scrolling and detail inspection.

---

### FUP-GOV-001: Unit Test Coverage Policy Discrepancy Reconciliation
- **Governing Area**: DO-178B Testing Gates & Governance (Constitution Principle V, Rules §1, BRD §9)
- **Mapped Feature Requirements**: FR-001, FR-019, FR-020
- **Excluded Work at feature 057**: Amending BRD §9 or Constitution Principle V to align the "100% coverage of core business logic" statement with the constitutional tier gates (Tier 1 ≥ 40%, Tier 2 ≥ 20%, global floor ≥ 20%).
- **Reason Excluded**: Pre-existing discrepancy noted in research; this feature does not silently alter established coverage floors.
- **Current Verification Status**: `Unverified` for closure of this follow-up. Constitution 5.0.0 and feature 058 raised the Tier 1 and repository floors and retained the separate BRD 100% core business-logic objective; see [feature 058 plan](../058-enforce-coverage-targets/plan.md). The earlier 40%/20% figures describe the feature 057 baseline, not current policy. This update does not assert that the 100% objective is met or that a governance review has closed the follow-up.
- **Lifecycle Status**: `Identified`
- **Preserved Authorization / Deferral Reference**: Constitution Principle V (current Constitution 5.0.0); Rules §1 Tiered Test Coverage Targets; BRD §9 retains the separate 100% core business-logic objective.
- **Responsible Role**: Lead Architect / Governance Committee
- **Needed Completion Evidence**: Synchronized wording amendment between BRD §9 and Constitution Principle V clarifying tier coverage floors versus unit testing scope.
