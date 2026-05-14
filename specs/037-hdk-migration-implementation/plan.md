# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Transition the Post-Quantum (ML-DSA) key branch to use `DeriveSalt` from IETF `draft-dijkhuis-cfrg-hdkeys-06` and remove all legacy BIP-32/BIP-44 path-based derivation logic from the codebase. The implementation uses a domain-separated context string for the PQ branch and preserves the existing BIP-39 mnemonic logic.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin Multiplatform (KMP)  
**Primary Dependencies**: `HdkManager` (internal core/security), `HMAC-SHA512`  
**Storage**: N/A (Cryptographic derivation layer)  
**Testing**: JUnit 5, MockK, kotlin.test  
**Target Platform**: Android Native Application (Minimum SDK 28)
**Project Type**: Android App / KMP Shared Library  
**Performance Goals**: < 200ms end-to-end for cryptography actions  
**Constraints**: Must maintain clean break for existing PQ keys; must not alter BIP-39 mnemonic seed generation.  
**Scale/Scope**: Refactoring specific classes (`WalletMasterSeedProvider`, `PostQuantumCrypto`, `Fido2CryptoService`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Section I (Security First)**: Passes. Seed derivation happens in volatile memory and is zeroed out.
- **Section II (Master Seed Architecture)**: **VIOLATION (INTENTIONAL)**. The constitution currently dictates: "The ML-DSA/Post-Quantum key branch uses a **BIP-85-style** hardened CKD derivation...". This feature *specifically aims* to change this to HDK `DeriveSalt`. The constitution must be updated during this feature's implementation (FR-007) to resolve this conflict.
- **Section III (No Magic Numbers)**: Passes. New context strings (`"PQ_ML-DSA_Branch"`) and HMAC keys (`"chimali_pq_seed_v1"`) will be defined as meaningful constants.
- **Section VII (Test-Driven Development)**: Passes. New KAT tests and determinism tests are planned to cover the new `DeriveSalt` usage.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/
├── WalletMasterSeedProvider.kt
├── PostQuantumCrypto.kt
└── Fido2CryptoService.kt

feature/fido2/src/androidTest/kotlin/com/chimali/fido2/data/crypto/
└── WalletMasterSeedProviderTest.kt
```

**Structure Decision**: Modifying existing cryptographic provider classes within the `feature/fido2` module.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
