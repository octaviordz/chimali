# Proposed Constitution Amendment: Verifiable Acceptance and Evidence

- **Date**: 2026-10-23
- **Status**: Approved and Adopted (Constitution 4.1.0)
- **Lifecycle**: `Draft → Ready for review → Approved / Rejected / Changes requested`
- **Current Lifecycle State**: `Approved and Adopted` (Constitution 4.1.0 Adopted)
- **Baseline**: Constitution 4.0.0, last amended 2026-10-19
- **Ratified Date Retained**: 2026-02-19
- **Adopted Version**: 4.1.0 (MINOR)

---

## 1. Exact Proposed Edit

**Affected Principle**: `V. Module Criticality & DO-178B Rigor`

### Current Text
Retain all three current Principle V bullets verbatim:
- **Criticality Classification**: Code is partitioned into Tier 1 (Critical: `:core:security`, `:core:domain`, `:core:data`, `:feature:fido2`, `:feature:authenticator`) and Tier 2 (Feature & Infrastructure).
- **DO-178B Traceability**: Tier 1 code MUST maintain 100% bi-directional requirement traceability. Every public and internal function MUST carry a `@Req` annotation mapping to stable IDs in `docs/brd.md` (`FR-*`) or `docs/trd.md` (`NFR-*`, `TR-*`). Unreferenced dead code is strictly prohibited.
- **Execution Determinism & Testing Gates**: Hot execution paths MUST avoid dynamic memory allocations. Loops and retries MUST be bounded. Automated CI pipelines MUST enforce test coverage gates: Tier 1 ≥ 40%, Tier 2 ≥ 20%, Global repository floor ≥ 20%. Detailed rules are enforced via `.agents/rules/specify-rules.md` §3.

### Added Text
Add immediately following the "Execution Determinism & Testing Gates" bullet:

> - **Verifiable Acceptance & Evidence**: Verification obligations MUST define their assessed scope, expected outcomes, and traceable evidence. Unexecuted, deferred, and unsupported coverage MUST be identified explicitly and MUST NOT be represented as passing. Detailed verification procedures reside in `docs/trd.md` §9 and `.agents/rules/specify-rules.md` §1.

*Note*: No other principle wording change is proposed. Numeric budgets (Principle VI), memory restrictions and platform text boundary laws (Principle I), derivation profile laws (Principle II), architecture layering (Principle III), append-only history (Principle IV), pragmatic discipline (Principle VII), accessibility principles (Principle VIII), and authority precedence remain strictly intact.

---

## 2. Rationale and Version Impact

### Architectural Rationale
The five backlog areas identified in `docs/backlog/constitution-testability-improvements.md` (rendering benchmarks, input validation, sensitive data classification, integration journeys, and accessibility verification) all share a fundamental requirement: verification obligations must be verifiable, bounded by explicit scope, and accompanied by truthful, traceable evidence. 

Rather than overloading individual principles with ephemeral tooling names, screen classes, or benchmark numbers—which would reverse the 4.0.0 conciseness refactor—a single normative governance bullet in Principle V establishes the universal requirement for verifiable acceptance and truthful evidence reporting across all project domains. Technical procedures, parameters, and matrices are housed in supporting technical authorities (`docs/trd.md` §9 and `.agents/rules/specify-rules.md` §1).

### Semantic Versioning Justification
- Under Constitution Governance §2:
  - **MINOR**: "Addition of new principles or materially expanded guidance."
  - **PATCH**: "Clarifications, wording refinements, and typo fixes."
  - **MAJOR**: "Backward-incompatible governance or principle redefinitions/removals."
- Adding the "Verifiable Acceptance & Evidence" requirement materially expands project guidance by establishing binding evidence and reporting rules, justifying a **MINOR** version increment from **4.0.0 to 4.1.0**.
- It is not a PATCH because it imposes new normative constraints on verification reporting.
- It is not a MAJOR change because it neither breaks backward compatibility nor weakens, removes, or redefines any existing principle.
- **Rule on Reassessment**: If subsequent engineering review materially modifies the semantics or removes obligations, the version impact MUST be re-evaluated prior to adoption.
- **Rule on Material Edits**: "Material edits after approval require renewed review of the changed package."

---

## 3. Per-Area Constitutional Disposition

| Backlog Area | Constitution Principle Disposition | Supporting Authority Synchronization Plan |
| --- | --- | --- |
| **1. Rendering Benchmark Scope** | Retain Principle VI general consistent-60-FPS vitals obligation verbatim; Principle V verifiable evidence bullet applies. | Retain full `NFR-PERF-020` numeric budgets in BRD. Add vault list scrolling and password detail inspection as minimum journeys in TRD §9.6 and Rules §1. Require complete future measurement contracts. |
| **2. Validation Policy** | Retain Principles I, III, and V protections. No constitutional exception for ordinary invalid-input runtime exceptions. | Reconcile both adjacent paragraphs in Rules §5.2 together: expected invalid input yields typed `Outcome.Failure` or protocol mapping; internal `require`/`check` remain distinct. Add negative test rules in TRD §9.3 and Rules §1. |
| **3. Sensitive Data & Platform Boundaries** | Retain Principle I memory zeroing and strict platform UI text boundary laws. No new exceptions. | Create canonical registers `docs/security/data-classification.md` and `docs/security/platform-text-boundaries.md`. Link from BRD §5.1, TRD §5.1, and Rules §5.5. Consolidate historical evidence preserving dated provenance and explicit gaps. |
| **4. Integration Verification** | Retain Principles III (Architecture), IV (Append-only history), V (Rigor), and VIII (FIDO2 & Passkeys). | Update BRD §9 and Rules §1 to replace broad wording with explicit minimum scope. Define independent Bluetooth HID and Android Credential Manager lifecycle and 12 failure-category matrices in TRD §9.4. |
| **5. Accessibility Verification** | Retain Principle VIII accessibility obligation and BRD `FR-UI-011` implementation deferral. | Correct TRD §6 universal `contentDescription` rule to allow visible text and decorative elements. Document 4-category assessment matrix and screen-reader walkthrough procedure in TRD §9.5 and Rules §1. Preserve `FR-UI-011` deferral. |

---

## 4. Sync Impact Report for `.specify/memory/constitution.md`

Enacted in `.specify/memory/constitution.md` upon adoption under Task T035:

```markdown
<!--
SYNC IMPACT REPORT
Current amendment: Version 4.0.0 -> 4.1.0 (MINOR: Add verifiable acceptance & evidence clause to Principle V, and synchronize validation, integration, accessibility, rendering, and security register contracts).
Date: 2026-10-23; ratified date retained: 2026-02-19.

Modified Principles:
  - Principle V (Module Criticality & DO-178B Rigor): Retained existing three bullets (Criticality Classification, DO-178B Traceability, Execution Determinism & Testing Gates); added "Verifiable Acceptance & Evidence" bullet requiring explicit scope, expected outcomes, truthful evidence reporting, and cross-referencing TRD §9 and rules §1.

Added Sections:
  - None to constitution body (remains concise).

Removed Sections:
  - None.

Synchronized Authorities & Registers:
  - docs/brd.md: Added verification references in §4.5 (accessibility deferral), §5.1 (security registers), §5.2 (rendering minimum journeys & budgets), and §9 (integration lifecycle scope).
  - docs/trd.md: Added §9.1 Verification Evidence Schema & Status Taxonomy, §9.2 Verification Policy Framework & Five-Area Authority Map, §9.3 Validation & Negative-Case Verification Contracts, §9.4 Dual Credential Integration Lifecycle & Failure Matrices, §9.5 Universal Accessibility Verification Procedures, §9.6 Rendering Smoothness Measurement Contract; corrected §6 universal contentDescription rule.
  - .agents/rules/specify-rules.md: Updated §1 (evidence schema, integration boundaries, accessibility, rendering execution), §5.2 (reconciled Outcome.Failure vs internal assertions, negative testing), §5.5 (security registers).
  - docs/security/data-classification.md: Canonical register created.
  - docs/security/platform-text-boundaries.md: Canonical register created.
  - docs/backlog/constitution-testability-improvements.md: Synchronized delivery references and follow-ups.

Follow-up TODOs: Tracked in specs/057-improve-verification-contracts/follow-ups.md (FUP-SEC-001, FUP-SEC-002, FUP-INT-001, FUP-INT-002, FUP-ACC-001, FUP-PERF-001, FUP-GOV-001).
-->
```

---

## 5. Synchronized Review Package Manifest & Traceability (T033, T040)

- **Base Git Revision**: `2326db3ab5378968c25145bfd438fd14e609a75f`
- **Target Branch**: `codex/057-improve-verification-contracts`

### 5.1 Initial Review Package Manifest (T033 Pre-Adoption Snapshot)

The exact concrete review package submitted for engineering disposition at milestone T033 comprised the following document revisions:

| Package Document | File Path | Deliverable Scope & Role | Approved SHA-256 Checksum (T033) |
| --- | --- | --- | --- |
| **BRD** | `docs/brd.md` | Verification references in §4.5, §5.1, §5.2, and §9; budgets and deferrals retained | `8da20773f4e822cd0b3187ff4c4c20a4deaece2d9aba4c5a2f0d6d019cec767c` |
| **TRD** | `docs/trd.md` | §6 accessibility correction; §9.1–§9.6 verification schemas, matrices, and contracts | `9e56a57801c6a71239feaa91d6a516a0431f5182051cda23104af2b8ad672ac0` |
| **Rules** | `.agents/rules/specify-rules.md` | §1 testing rules; §5.2 validation & assertion separation; §5.4 UI; §5.5 crypto | `599d1a95c5576e62a9fbed873c8c532d0f20b73c9078053dfcad39e8be3e5e93` |
| **Data Classification** | `docs/security/data-classification.md` | Canonical register of 12 classified fields with dated provenance & limitations | `7bb087e1d100b4af11e185131ae3ad4de61ac29e2bee5318bf69fcd66f73a418` |
| **Platform Text Boundaries** | `docs/security/platform-text-boundaries.md` | Canonical register of 8 audited platform UI text boundaries and provider limits | `e5f00503cc4153963da0dc1c9852d2d02be435dab042303c74f74b8024afc7ae` |
| **Backlog** | `docs/backlog/constitution-testability-improvements.md` | Synchronized status and delivery references across all 5 testability items | `1f193b5c0a8b67b871ba974a28e47aae0ea49d8f7f140696c80771a640a2f12b` |
| **Review Record** | `specs/057-improve-verification-contracts/documentation-review.md` | Complete documentary review matrix for FR-001–FR-020 and SC-001–SC-007 | `6fa08965326c007a332fdd6e5a43d89a0ac1c6f256de8e35f5cd7ef1fbeabf99` |
| **Follow-Up Register** | `specs/057-improve-verification-contracts/follow-ups.md` | Active register tracking 7 excluded runtime engineering & testing tasks | `6f260cf2e73e0080d6ff35cc281fe70a11fab2fc1786bb43541eaa16ab4728f3` |

### 5.2 Phase 9, 10, & 11 Convergence Remediation Manifest (Post-Adoption Deliverables)

Following affirmative authorization to adopt Constitution 4.1.0 on 2026-10-23, convergence assessments in Phases 9, 10, and 11 identified and resolved findings F1–F7 (tasks T037–T046). In accordance with governance integrity, post-adoption corrective edits preserve the historical T033 snapshot while documenting the exact byte hashes of the completed convergence deliverables:

| Package Document | File Path | Deliverable Scope & Role | Remediated SHA-256 Checksum (Phase 9–11) |
| --- | --- | --- | --- |
| **BRD** | `docs/brd.md` | Verification references in §4.5, §5.1, §5.2, and §9 synchronized with adopted 4.1.0 | `6fffbb578d39cc9a35ead9f40f000f8f49c7e0b76b86e3968df9a0fc3f73fd7d` |
| **TRD** | `docs/trd.md` | §6 accessibility; §9.1–§9.6 verification contracts, crypto RP verification, attestation handling, and measurement fields | `f771110fa080dc9af712b2deb2f89a61254262131649c3da0089348e87670c9b` |
| **Rules** | `.agents/rules/specify-rules.md` | §1 testing rules; §5.2 validation, WebAuthn rpId domain rules, state/auth, and side effects; §5.5 crypto | `4e07bc077cabca1053a2671a3e33df01613761696e450bced2f3b89ab20edaea` |
| **Data Classification** | `docs/security/data-classification.md` | 12 classified fields; public parameters separated from identifying data; blind-index/audit logging restricted | `b2d217a431425fe5f15db15c6e556ed03facea2b52c7b6b68c09e90011a46fa2` |
| **Platform Text Boundaries** | `docs/security/platform-text-boundaries.md` | 8 audited boundary records; key material/seeds barred from UI text exception; provider limits documented | `756742179708711a6f53ce1260959f6f807ba2b2a396c9a647edcca877c99098` |
| **Backlog** | `docs/backlog/constitution-testability-improvements.md` | Synchronized status and delivery references across all 5 testability items under Constitution 4.1.0 | `6e2c8f3991ab332185e2653491f484873135a8eace49cc21b3a7799396f9868d` |
| **Review Record** | `specs/057-improve-verification-contracts/documentation-review.md` | Complete documentary review matrix for FR-001–FR-020, SC-001–SC-007, and Phases 9–11 findings F1–F7 | `f960e62702433a4cc7c38de4a6d552b7d304d4d0415481e92b85a4ae9464d7ca` |
| **Follow-Up Register** | `specs/057-improve-verification-contracts/follow-ups.md` | Active register tracking 7 excluded runtime engineering & testing tasks with taxonomy compliance | `87614239cb779d9bf0b8111f2c1b0cc2b168d760da7606d87ae63542a5c4c8c3` |

---

## 6. Engineering Review Disposition & Adoption Record (T034, T035, T040, T043, T044, T045, T046)

### 6.1 Baseline Amendment Adoption (T034, T035)

| Governance Field | Value | Notes / Status |
| --- | --- | --- |
| **Approved Baseline Revision** | Section 5.1 manifest (SHA-256 verified) | Concrete review package submitted at T033 |
| **Engineering Reviewer** | Lead Engineer / User | Formal sign-off granted by authorized engineering maintainer |
| **Review Date** | 2026-10-23 | Affirmative review recorded ("Proceed with T035") |
| **Disposition** | `Approved` | T033 proposal package approved without amendments |
| **Adopted Version** | `4.1.0` | Live constitution updated from 4.0.0 to 4.1.0 under T035 |
| **Adopted Date** | `2026-10-23` | Principle V amendment enacted in `.specify/memory/constitution.md` |
| **Ratified Date** | `2026-02-19` | Immutable ratification baseline strictly preserved |

### 6.2 Materiality Assessment of Convergence Refinements (T044)

In accordance with Constitution Governance §1 ("Material edits after approval require renewed review of the changed package"), the Phase 9 and 10 convergence refinements (T037–T045) were evaluated for materiality against the approved T033 baseline package:

1. **Principle V Amendment Text & Sync Header**: The enacted text in `.specify/memory/constitution.md` remains identical in wording, scope, and semantics to the text approved at T034.
2. **Security Registers (F1/T037, F2/T038)**: *Material refinement*. Barred cryptographic key material, seeds, normalization internals, and diagnostic logging APIs from platform UI text exceptions; removed unsupported blanket permissions for privacy-sensitive RP/account text retention, GC-only cleanup, and blind-index logging. These edits tighten security controls to strictly enforce Constitution Principle I (Memory Safety) and resolve accidental discrepancies in the baseline draft.
3. **Integration Verification Contracts (F5/T041, T045)**: *Material refinement*. Added explicit relying-party cryptographic response verification, direct/enterprise attestation signature verification requirements, and format-specific rationale for standard consumer passkeys (`attestation="none"` per WebAuthn L3 §8.7); clarified that non-volatile storage stores only encrypted recovery/credential metadata (zero persisted derived private keys or seeds per Principle II).
4. **Validation Guidance (F6/T042)**: *Substantive refinement*. Replaced unconditional "NO session state mutation" with operation-specific forbidden business/security side effects while permitting safe-failure state transitions and buffer zeroing; aligned RP ID validation with WebAuthn L3 §5.4.2 domain rules.
5. **Rendering Contract & Metadata Sync (F3/T039, F4/T040, F7/T043)**: *Non-material editorial synchronization*. Restored rendering parameter selection to future reviewed contracts, updated section numbering (TRD §9.3–§9.6), and separated historical from post-adoption package manifests.

### 6.3 Convergence Package Governance Disposition (T044, T046)

| Governance Field | Value | Notes / Status |
| --- | --- | --- |
| **Remediated Package Revision** | Section 5.2 manifest (SHA-256 verified) | Concrete convergence deliverables submitted under Phase 10 & 11 |
| **Remediation Scope** | Tasks T037–T045 (Findings F1–F7 resolved) | Security tightening, RP cryptographic verification, attestation handling, validation rules |
| **Materiality Finding** | Material refinement of supporting security & verification policies | Re-review required per Constitution Governance §1 |
| **Live Constitution Impact** | None | Enacted Principle V text in Constitution 4.1.0 remains fully valid |
| **Corrected Package Disposition** | `Pending approval` | Awaiting formal renewed engineering sign-off from Lead Engineer for post-adoption material refinements |
| **Engineering Reviewer** | Lead Engineer (Pending Renewed Review) | Author documentary verification complete; formal engineering decision pending |
| **Review Date** | Pending | Submitted for renewed review on 2026-10-23 |
| **Authority Precedence** | Upheld strictly | Constitution 4.1.0 → BRD 0.2.0 → TRD 0.2.0 → Rules §1/§5 → Feature specs |

> [!NOTE]
> **Adoption Record & Convergence Package Review Status (T034, T035, T044, T045, T046)**:
> In accordance with project constitution governance, affirmative engineering approval for the baseline amendment was granted on 2026-10-23 ("Proceed with T035"). The approved Principle V verifiable acceptance bullet and sync header impact report were incorporated into `.specify/memory/constitution.md`, the version bumped to 4.1.0 (MINOR), and the baseline ratification date `2026-02-19` strictly preserved.
> Subsequent Phase 9 & 10 convergence edits (T037–T045) resolved all findings F1–F7. Because these edits materially tightened security registers and refined verification contracts, Constitution Governance §1 requires renewed review of the corrected package. The deliverables in §5.2 are documentary-complete and submitted for engineering disposition; the corrected-package status is recorded truthfully as `Pending approval` without inventing sign-off.
