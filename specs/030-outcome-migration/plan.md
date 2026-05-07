# Implementation Plan: Outcome Migration & Functional Exception Expansion

**Branch**: `030-outcome-migration` | **Date**: 2026-04-30 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/030-outcome-migration/spec.md`

## Summary

Migrate the `fido2` and `vault` modules to use the functional exception handling pattern by using `Outcome`, applying it to crypto services, repositories, and `VaultService`, and converting `VaultViewModel` to use an exhaustive `when` expression instead of `try-catch` blocks.

## Technical Context

**Language/Version**: Kotlin (KMP)
**Primary Dependencies**: Coroutines, Kermit, Detekt
**Storage**: SQLDelight (SQLCipher) via Repositories
**Testing**: kotlin.test, JUnit 5, MockK
**Target Platform**: Android Native (KMP structure)
**Project Type**: Mobile app
**Performance Goals**: Avoid exception instantiation overhead for standard flow control; 60 fps UI smoothness.
**Constraints**: Zero `@Suppress("TooGenericExceptionCaught")` annotations; Must log causes via Kermit at the boundary.
**Scale/Scope**: Refactoring of `fido2` data/crypto layers and `vault` service/presentation layers.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **III. Uncompromising Architecture & Quality**: Passes. Refactoring removes Detekt suppressions and improves code quality (no generic exception catching in ViewModels).
- **IV. Performance & Reliability Excellence**: Passes. Using the `Outcome` wrapper avoids exception throwing overhead for standard flow control.

## Project Structure

### Documentation (this feature)

```text
specs/030-outcome-migration/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (N/A)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
# Project module updates
core/
└── common/
    └── src/commonMain/kotlin/com/chimali/core/common/result/
        ├── Outcome.kt
        └── DomainError.kt

feature/
├── fido2/
│   └── src/androidMain/kotlin/com/chimali/fido2/
│       ├── data/
│       │   ├── crypto/             # Update services to return Outcome<T, DomainError>
│       │   │   ├── CredentialEncryptionService.kt
│       │   │   ├── Fido2CryptoService.kt
│       │   │   └── CredentialStorageService.kt
│       │   └── repository/         # Update repositories to return Outcome<T, DomainError>
│       │       ├── CredentialRepositoryImpl.kt
│       │       └── PasskeyCredentialRepositoryImpl.kt
│       └── ctap2/                  # Update handlers to return Outcome<T, DomainError>
│           └── Ctap2MakeCredentialHandler.kt (and other CTAP2 handlers)
└── vault/
    └── src/main/java/com/chimali/feature/vault/
        ├── api/
        │   └── VaultService.kt (and its implementation) # Update to return Outcome<T, DomainError>
        └── internal/
            └── VaultViewModel.kt   # Remove try-catch, use exhaustive when on Outcome
```

**Structure Decision**: A global migration to `Outcome` has taken place in `core/common`. The `fido2` and `vault` modules have been updated to consume this new type. No new directories are created.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
