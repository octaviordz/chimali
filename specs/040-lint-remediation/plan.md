# Implementation Plan: Lint Remediation

**Branch**: `040-lint-remediation` | **Date**: 2026-05-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/040-lint-remediation/spec.md`

## Summary

Remediate all invalid inline `@Suppress` annotations identified in the [039 audit](../039-linting-baseline-audit/audit.md) by removing redundant suppressions, replacing generic exception handling with constitution-compliant `Outcome` patterns, converting hidden `TODO` markers to trackable issue references, and addressing deprecated API usage in the Bluetooth HID layer. The goal is zero invalid inline suppressions in production code while maintaining full local CI (`tools/local-ci.ps1`) compliance.

## Technical Context

**Language/Version**: Kotlin 2.x (KMP), Android SDK 28+
**Primary Dependencies**: Detekt 1.23.x, Ktlint, Jetpack Compose, SQLDelight, Koin
**Storage**: N/A (no schema changes)
**Testing**: kotlin.test (KMP commonTest), JUnit 5, MockK — existing test suites must continue to pass
**Target Platform**: Android Native (KMP module structure)
**Project Type**: Mobile App (Android)
**Performance Goals**: N/A — no runtime behaviour changes expected
**Constraints**: All changes must pass `tools/local-ci.ps1` (Detekt, Ktlint, unit tests). No Detekt baseline XML modifications unless explicitly required for deferred TODO resolutions.
**Scale/Scope**: ~35 `@Suppress("FunctionNaming")` across 17 files, ~15 `@Suppress("ForbiddenComment")` across 14 files, ~8 `@Suppress("TooGenericExceptionCaught")` across 4 files (excluding `FunctionalCatching.kt`), 2 `@Suppress("DEPRECATION")` in 1 file.

> **Note on Scope Expansion**: A full repository scan revealed additional violations not explicitly listed in the original `audit.md` (e.g., 14 `ForbiddenComment` files vs the 4 listed in the audit, and 4 `TooGenericExceptionCaught` files vs the 2 listed). The implementation plan encompasses all discovered instances to ensure complete compliance.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle III (Architecture & Quality)**: The feature directly improves static analysis compliance by removing invalid suppressions, aligning with the mandatory Detekt/Ktlint enforcement.
- **Principle IX (Local CI)**: All changes must pass `tools/local-ci.ps1`. This is the primary quality gate.
- **Principle X.2 (Kotlin Idioms)**: The `TooGenericExceptionCaught` remediation enforces `Outcome<D, E : DomainError>` as mandated. The `FunctionalCatching.kt` utility in `core:common` is the established architectural boundary for generic exception catching — per its own documented decision, it is the *only* layer where this is permitted.
- **Principle X.7 (Anti-Patterns)**: `catch(e: Exception)` is a prohibited wildcard catch. Migration to `runCatchingOutcome` or specific catches resolves this violation.
- **Principle XI (Risk Management)**: This is a targeted remediation of known debt, not speculative refactoring. Every change traces to a specific audit finding.

*Result*: **PASS**. No constitutional violations. The feature enforces existing constitutional mandates.

*Post-Phase-1 Re-check*: **PASS**. The design uses existing infrastructure (`runCatchingOutcome`, `DomainError` hierarchy, Detekt config exclusions). No new abstractions, modules, or patterns are introduced.

## Project Structure

### Documentation (this feature)

```text
specs/040-lint-remediation/
├── plan.md              # This file
├── research.md          # Phase 0 output — suppression inventory & remediation strategy
├── data-model.md        # Phase 1 output — no data model changes
├── quickstart.md        # Phase 1 output — developer reference
├── contracts/           # N/A — no API changes
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
# Modules affected by this feature:
feature/
├── vault/
│   └── src/main/java/com/chimali/feature/vault/
│       ├── internal/VaultViewModel.kt              # ForbiddenComment (P2)
│       ├── internal/VaultRepositoryImpl.kt          # TooGenericExceptionCaught (P3)
│       └── ui/                                      # FunctionNaming (P1) — 8 files
│           ├── VaultListScreen.kt
│           ├── SecureNoteEntryScreen.kt
│           ├── PasswordEntryScreen.kt
│           ├── SecureNoteDetailScreen.kt
│           ├── PasswordDetailScreen.kt
│           ├── LabelManagerScreen.kt
│           ├── CreditCardEntryScreen.kt
│           └── CreditCardDetailScreen.kt
├── fido2/
│   ├── src/androidMain/kotlin/com/chimali/fido2/
│   │   ├── presentation/ui/                         # FunctionNaming (P1) — 8 files
│   │   │   ├── Fido2HomeScreen.kt
│   │   │   ├── DevelopmentToolsScreen.kt            # + ForbiddenComment (P2)
│   │   │   ├── RegistrationPromptScreen.kt          # + ForbiddenComment (P2)
│   │   │   ├── RegistrationProgressIndicator.kt
│   │   │   ├── PairedDevicesSection.kt
│   │   │   ├── EditPairedDeviceScreen.kt
│   │   │   ├── BiometricPromptComponent.kt
│   │   │   └── AuthenticationPromptScreen.kt        # + ForbiddenComment (P2)
│   │   ├── presentation/management/
│   │   │   ├── CredentialComponents.kt              # FunctionNaming (P1) + ForbiddenComment (P2)
│   │   │   └── CredentialListScreen.kt              # ForbiddenComment (P2) [NEW]
│   │   ├── presentation/navigation/
│   │   │   └── Fido2RegistrationNavGraph.kt         # ForbiddenComment (P2) [NEW]
│   │   ├── domain/usecase/
│   │   │   └── RegisterCredentialUseCase.kt         # TooGenericExceptionCaught (P3)
│   │   ├── domain/service/impl/
│   │   │   └── UserVerificationServiceImpl.kt       # ForbiddenComment (P2)
│   │   ├── data/repository/
│   │   │   ├── Fido2RepositoryImpl.kt               # ForbiddenComment (P2)
│   │   │   ├── UserConsentRepositoryImpl.kt         # ForbiddenComment (P2)
│   │   │   ├── RelyingPartyRepositoryImpl.kt        # ForbiddenComment (P2)
│   │   │   └── PairedDeviceRepositoryImpl.kt        # TooGenericExceptionCaught (P3)
│   │   ├── data/crypto/
│   │   │   └── Fido2CryptoService.kt                # TooGenericExceptionCaught (P3)
│   │   └── bluetooth/
│   │       └── BluetoothHidDeviceWrapper.kt         # DEPRECATION (P4)
│   └── src/commonMain/kotlin/com/chimali/fido2/
│       └── data/eventsourcing/
│           ├── PasskeyEventStoreRepositoryImpl.kt   # ForbiddenComment (P2)
│           └── PasskeySnapshotRepositoryImpl.kt     # ForbiddenComment (P2)
core/
├── data/src/main/kotlin/com/chimali/core/data/
│   └── eventsourcing/
│       ├── EventStoreRepositoryImpl.kt              # ForbiddenComment (P2)
│       └── SnapshotRepositoryImpl.kt                # ForbiddenComment (P2)
└── common/src/commonMain/kotlin/com/chimali/core/common/
    └── result/FunctionalCatching.kt                 # TooGenericExceptionCaught — EXCLUDED (valid)
```

**Structure Decision**: No new source directories or modules. All changes are in-place modifications to existing files within the `feature/vault`, `feature/fido2`, `core/data`, and `core/common` modules.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified.*
