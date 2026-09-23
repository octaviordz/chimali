# Backlog: Constitution Testability & Conciseness Improvements

**Status**: Open / Ready for Review  
**Origin**: Analysis of [constitution.md](file:///D:/octav/source/repos/Chimali/.specify/memory/constitution.md) for testability, conciseness, and automated agent verification.  
**Date Created**: 2026-09-23  

---

## Overview

The following items are remaining candidates from the constitution testability review. These items address areas where rules contain ambiguous language, lack clear verification mechanisms, or lack concrete boundary specifications.

---

## Backlog Items

### 1. Pin Frame Rate Benchmark Scope
* **Section**: `Principle III. Performance & Reliability`
* **Current Text**: `"Maintain 60 FPS during interactions."`
* **Problem**: "During interactions" is unconstrained and cannot be verified automatically without defining target screens.
* **Proposed Enhancement**:
  Specify target user journeys and tooling:
  > Maintain 60 FPS during key interactions (specifically list scrolling on `VaultListScreen` and detail inspection on `CredentialDetailScreen`), measured via Jetpack Macrobenchmark `FrameTimingMetric` on reference hardware.

---

### 2. Precise Domain Input Validation Contract & Negative Testing
* **Section**: `ES-V.2. Kotlin & KMP Guidelines`
* **Current Text**: `"All user inputs MUST be strictly validated before processing or persisting. Apply require() and check() for aggressive input validation and state verification. Fail fast."`
* **Problem**: "All user inputs" and "strictly validated" lack concrete verification criteria.
* **Proposed Enhancement**:
  Clarify boundary enforcement and mandatory test expectations:
  > All data arriving at a Domain layer boundary from the UI layer MUST be validated using `require()` / `check()` before execution or persistence. Validation MUST cover nullability/blankness for required fields, range bounds for numeric values, and structural format validation (e.g. URI, UUID). Each Domain command/use-case MUST include at least one negative unit test asserting that invalid inputs yield an `Outcome.Failure` or throw `IllegalArgumentException`.

---

### 3. Formalize Security Data Classification & Platform Text Boundary Registers
* **Section**: `Principle I.5. Security First (Memory Security)`
* **Current Text**:
  - *"Application-owned sensitive plaintext MUST remain in volatile, explicitly erasable mutable storage..."*
  - *"Each permitted boundary MUST document the exact dependency/API, classified fields, authorized purpose and lifetime..."*
* **Problem**: What qualifies as "sensitive" is not cataloged in a single reference, and the location for documenting platform text boundaries is unspecified.
* **Proposed Enhancement**:
  - Point sensitive field definitions to a canonical registry: `docs/security/data-classification.md`.
  - Designate `docs/security/platform-text-boundaries.md` as the mandatory register for platform text boundary exemptions.

---

### 4. Integration Test Scope Specification
* **Section**: `ES-I. Development Methodology & Testing`
* **Current Text**: `"Comprehensive integration tests MUST verify the interaction between Bluetooth HID emulation, Credential Manager, and Encryption layers."`
* **Problem**: "Comprehensive" is subjective and does not provide an automated binary pass/fail condition.
* **Proposed Enhancement**:
  Define minimal mandatory end-to-end integration flows:
  > Integration tests MUST cover at minimum: (a) successful CTAP2 assertion flow end-to-end, (b) credential encryption-then-decryption roundtrip, and (c) Credential Manager registration + retrieval.

---

### 5. Universal Accessibility Verification Criteria
* **Section**: `Principle V. Inclusion & Universal Accessibility`
* **Current Text**: `"The application MUST support screen readers (TalkBack), high-contrast modes, and dynamic text scaling."`
* **Problem**: Lacks specific verification criteria for automated tests.
* **Proposed Enhancement**:
  Add concrete automated and manual checks:
  > Compliance MUST be verified via: (a) Compose Semantics accessibility testing ensuring all interactive composables provide non-empty `contentDescription`, and (b) pre-release TalkBack navigation audits.
