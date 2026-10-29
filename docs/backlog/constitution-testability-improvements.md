# Backlog: Constitution Testability & Conciseness Improvements

**Status**: Policy & Verification Contracts Delivered and Adopted (Constitution 4.1.0) — Runtime Backlog Open

**Origin**: Review of the [constitution](../../.specify/memory/constitution.md) for testability, conciseness, and automated verification.

**Date Created**: 2026-09-23

**Last Reviewed**: 2026-10-28

**Review Baseline**: Constitution 5.0.0, last amended 2026-10-24 (ratified 2026-02-19). Feature 057 adopted these verification contracts in version 4.1.0; feature 058 subsequently amended the coverage floors.

---

## Overview

These proposals address ambiguous requirements, incomplete verification criteria, and scattered security boundary evidence. The October constitution refactor relocated engineering standards to [specify-rules.md](../../.agents/rules/specify-rules.md). References below use their current locations.

This backlog tracks the specification of testability requirements and the subsequent implementation of runtime tests and benchmarks. **Documentary completion of verification policies, schemas, and matrices under feature `057-improve-verification-contracts` DOES NOT close the runtime backlog items.** Each item remains open until its governing references, verification method, evidence location, and completion criteria are satisfied by executed runtime evidence. Missing thresholds, hardware selections, or unexecuted tests MUST NOT be interpreted as passing results. The [feature 057 follow-up register](../../specs/057-improve-verification-contracts/follow-ups.md) tracks the associated work and must be read alongside later feature evidence.

## Recommended Sequence

1. Resolve the validation and accessibility wording in items 2 and 5 before using either as an implementation requirement. *(Delivered in specification 057)*
2. Establish item 3's canonical registers by consolidating existing evidence. *(Delivered in specification 057)*
3. Define and implement item 4's integration gates and item 1's performance gates. *(Verification contracts delivered in specification 057; runtime test implementation pending)*

---

## Backlog Items

### 1. Define Measurable Frame-Rate Verification

**Disposition**: Retain with a complete measurement contract.

**Governing references**: Constitution Principle VI, Performance Vitals & Resource Budgets; BRD `NFR-PERF-020`, Rendering Smoothness; TRD §9.6.

**Problem**: The consistent-60-FPS obligation lacks repeatable workloads and an acceptance statistic. Naming a tool and reference hardware alone does not establish a gate. The earlier proposal named `CredentialDetailScreen`, which does not match a current screen declaration.

**Documentary Delivery & Policy Status**:
- **Delivery Status**: Policy and contracts delivered and adopted into Constitution 4.1.0; runtime implementation open.
- **Governing Contracts**: BRD `NFR-PERF-020` retains full 60 FPS / <1% slow / 0 frozen frame budgets and designates vault list scrolling (`JRN-PERF-LIST`) and password detail inspection (`JRN-PERF-DETAIL`) as minimum journeys. TRD §9.6 establishes the 12-field measurement contract and execution integrity rules; Rules §1 enforces benchmark execution proof.
- **Follow-Up Work**: Tracked in the [follow-up register](../../specs/057-improve-verification-contracts/follow-ups.md) (`FUP-PERF-001`).
- **Runtime Execution Status**: `Deferred` / `Unverified`. Feature-055 performance deferrals remain intact. Hardware selection, benchmark fixes, and execution remain open.

**Current evidence**: [CredentialListScrollBenchmark.kt](../../feature/fido2/src/macrobenchmark/kotlin/com/chimali/fido2/performance/CredentialListScrollBenchmark.kt) targets `com.chimali.app`, while the base application ID is `com.chimali`. Its conditional navigation/scrolling can skip the intended workload. The FIDO2 build file does not explicitly configure `src/macrobenchmark` as a runnable source set. The file is a starting point, not execution evidence.

**Verification and evidence location**: Prove test discovery, correct application targeting, seeded navigation, and actual scrolling. Retain benchmark output and environment metadata in the implementing feature's evidence record, linked from the TRD procedure.

**Completion criteria**: Approved measurement contract; executable benchmark with assertions preventing empty workloads; recorded results assessed against approved thresholds; unresolved device/metric limitations explicitly tracked.

**Primary reference**: [Android Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).

---

### 2. Clarify Input Validation and Negative-Test Contracts

**Disposition**: Rewrite to align with typed domain errors and all untrusted entry paths.

**Governing references**: Engineering rules §5.2, Kotlin & KMP Guidelines, and §3.5, Fail-Safe Error Handling & Graceful Degradation; Constitution Principles I, III, and V; TRD §9.3.

**Problem**: The rules mandate `Outcome` for domain errors while also prescribing aggressive `require()` / `check()` validation. The earlier proposal left this tension unresolved by accepting an escaped `IllegalArgumentException` for ordinary invalid input. It also omitted non-UI input and required only one negative test per command/use case.

**Documentary Delivery & Policy Status**:
- **Delivery Status**: Policy and contracts delivered and adopted into Constitution 4.1.0; runtime implementation open.
- **Governing Contracts**: Rules §5.2 rewritten to require typed `Outcome.Failure` or wire mapping for external rejection while reserving `require()` / `check()` for internal invariants, strictly preserving §3.5 encapsulation. TRD §9.3 defines 4 boundaries, 6 constraint classes, parameterless rules, and negative test criteria; Rules §1 synchronizes negative-testing obligations.
- **Follow-Up Work**: Tracked in the [follow-up register](../../specs/057-improve-verification-contracts/follow-ups.md) (`FUP-SEC-002`).
- **Runtime Execution Status**: `Unverified` for the complete contract; partial negative tests have been implemented and executed. Feature 058 records, for example, invalid Base64, empty plaintext, malformed UTF-8 and cleanup assertions (COV-022), and corrupted snapshot rejection, cleanup and replay (COV-021). See its [test selection](../../specs/058-enforce-coverage-targets/test-selection.md) and [implementation evidence](../../specs/058-enforce-coverage-targets/implementation-evidence.md). These selected cases do not establish the complete boundary and constraint mapping or close FUP-SEC-002.

**Current evidence**: [CredentialId.kt](../../core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/CredentialId.kt) validates encoding and decoded length; [PasskeyDecider.kt](../../core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/PasskeyDecider.kt) asserts supported algorithm/profile pairs. Clarify their boundary contracts rather than mechanically replacing assertions or duplicating validators.

**Verification and evidence location**: Maintain a constraint-to-test mapping and named execution results in the implementing feature's evidence record, linked from the rules/TRD. Include expected failures, side-effect assertions, and applicable cleanup assertions.

**Completion criteria**: Reconciled error policy; documented entry boundaries and constraints; executed invalid-class and boundary tests; no ordinary invalid-input exception escaping the assessed component boundary.

---

### 3. Establish Canonical Classification and Platform Text Boundary Registers

**Disposition**: Prioritize consolidation of existing evidence.

**Governing references**: Constitution Principle I, Cryptographic & Memory Security; engineering rules §5.5; TRD §5.1; BRD `NFR-SEC-010` / `030`.

**Problem**: Historically, classification and ownership evidence was scattered across disparate feature reviews without canonical registers.

**Documentary Delivery & Policy Status**:
- **Delivery Status**: Canonical registers delivered and linked under Constitution 4.1.0; runtime release audit open.
- **Governing Contracts**: Created `docs/security/data-classification.md` (12 records) and `docs/security/platform-text-boundaries.md` (8 records). Linked bidirectionally from BRD §5.1, TRD §5.1, and Rules §5.5. Consolidated historical scopes (2026-09-09 Vault and 2026-10-14..16 feature-055).
- **Follow-Up Work**: Tracked in the [follow-up register](../../specs/057-improve-verification-contracts/follow-ups.md) (`FUP-SEC-001`).
- **Runtime Execution Status**: `Unverified`. Registers document historical findings; current release branch static and runtime memory audit remains open.

**Verification and evidence location**: Store canonical entries in the two proposed registers. Cross-reference assessed source usages and ownership/cleanup tests or manual evidence; retain detailed run records in the relevant feature evidence. Mark unassessed usages explicitly open.

**Completion criteria**: Populated registers for a declared assessed scope; reconciled classifications; updated references; each assessed entry evidenced or marked unresolved; no blanket compliance claim or new security exemption.

---

### 4. Define Integration Journeys and Failure Coverage

**Disposition**: Retain the minimum-flow objective and expand boundaries and failure conditions.

**Governing references**: Engineering rules §1 and §3.3–3.5; BRD integration-testing requirement and `FR-PASS-010`, `FR-HID-010`; TRD §4.2, §4.3, §9.4.

**Problem**: “Comprehensive” does not define a gate. Separate successful assertion, encryption roundtrip, and registration tests do not establish integration across the named layers. An isolated encryption roundtrip is useful but insufficient end-to-end evidence.

**Documentary Delivery & Policy Status**:
- **Delivery Status**: Matrices and contracts delivered and adopted under Constitution 4.1.0; runtime test implementation open.
- **Governing Contracts**: TRD §9.4 establishes independent lifecycle matrices for Bluetooth relying party (`JRN-BLE-*`) and Android Credential Manager (`JRN-PASS-*`), and specifies all 12 failure-category decisions with append-only history preservation. BRD §9 and Rules §1 synchronized with explicit scopes and host vs. device evidence distinctions.
- **Follow-Up Work**: Tracked in the [follow-up register](../../specs/057-improve-verification-contracts/follow-ups.md) (`FUP-INT-001`, `FUP-INT-002`).
- **Runtime Execution Status**: `Unverified`. Physical Bluetooth hardware runs and on-device Credential Manager test execution remain open.

**Verification and evidence location**: Maintain a journey-to-requirement/test matrix and named run reports in the implementing feature's evidence record, linked from the TRD. Record environment, restart strategy, substitutions, and unresolved limitations.

**Completion criteria**: Approved journey matrix; executed success and applicable failure cases; verified persistence/reconstruction and response results; clear host/device evidence boundaries; unresolved paths remain open.

---

### 5. Verify Accessibility Through Semantics, Contrast, Scaling, and TalkBack

**Disposition**: Replace the blanket `contentDescription` requirement; retain automated and manual checks.

**Governing references**: Constitution Principle VIII, Cross-Platform Authenticator & Universal Accessibility; BRD `FR-UI-010` and `FR-UI-011`; TRD §6, §9.5; engineering rules §1, §5.4.

**Problem**: Controls can obtain accessible names from visible text and built-in semantics. Requiring every interactive composable to have `contentDescription` rejects valid controls and can override useful semantics. The earlier proposal also leaves contrast and dynamic text scaling unverified.

**Documentary Delivery & Policy Status**:
- **Delivery Status**: Policy and contracts delivered and adopted into Constitution 4.1.0; runtime implementation open.
- **Governing Contracts**: TRD §6 blanket rule replaced with Compose semantics naming, roles, grouping, and decorative graphic exemptions. TRD §9.5 defines 4 task categories (`ACC-TASK-001..004`), 200% font scaling criteria, WCAG 2.1 AAA contrast ratios, and a 7-step TalkBack walkthrough protocol. Rules §1 and §5.4 synchronized.
- **Follow-Up Work**: Tracked in the [follow-up register](../../specs/057-improve-verification-contracts/follow-ups.md) (`FUP-ACC-001`).
- **Runtime Execution Status**: `Deferred`. BRD `FR-UI-011` implementation deferral remains strictly active; full UI rollout and TalkBack audits remain deferred.

**Verification and evidence location**: Retain Compose semantics/layout results, contrast measurements, and manual TalkBack reports in the implementing feature's accessibility evidence record, linked from the TRD. Include journey, device/OS, TalkBack version, theme, font scale, results, and unresolved defects.

**Completion criteria**: Approved scope and contrast criteria; executed semantics/scaling/contrast checks; completed TalkBack journeys; evidence of essential action completion and appropriate secret disclosure; deferred/failing coverage explicitly tracked.

**Primary reference**: [Android Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
