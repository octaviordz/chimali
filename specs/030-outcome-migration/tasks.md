# Implementation Tasks: Outcome Migration & Functional Exception Expansion

**Branch**: `030-outcome-migration`
**Plan**: [plan.md](./plan.md)

## Phase 1: Setup

Goal: Establish the renamed foundational `Outcome` type in `core/common`.

- [x] T001 Rename `DataResult` to `Outcome` and `runCatchingResult` to `runCatchingOutcome` in `core/common/src/commonMain/kotlin/com/chimali/core/common/result/DataResult.kt` (rename file to `Outcome.kt`). Ensure `DomainError.kt` remains aligned.

## Phase 2: Foundational

Goal: No separate foundational tasks required. The global rename in T001 is the only blocker for the feature modules.

## Phase 3: User Story 1 - Type-Safe FIDO2 Operations (P1)

Goal: Migrate all FIDO2 crypto services and repositories to return `Outcome` instead of `Result`, handling domain-level errors explicitly at the call site and removing raw exception propagation.

**Independent Test**: Trigger a storage or crypto exception in FIDO2 operations and verify it returns a categorized `Outcome.Error(DomainError.CryptoError)` or `Outcome.Error(DomainError.DatabaseError)` with Kermit logging at the boundary.

- [x] T002 [P] [US1] Refactor `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialStorageService.kt` to catch exceptions, log via Kermit, and return `Outcome<T, DomainError>`.
- [x] T003 [P] [US1] Refactor `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionService.kt` to catch `GeneralSecurityException`, log via Kermit, and return `Outcome<T, DomainError.CryptoError>`.
- [x] T004 [US1] Refactor `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt` to consume the new `Outcome` responses from its underlying services and return `Outcome`.
- [x] T005 [P] [US1] Refactor `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt` to catch SQLite exceptions, log via Kermit, and return `Outcome<T, DomainError.DatabaseError>`.
- [x] T006 [P] [US1] Refactor `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/PasskeyCredentialRepositoryImpl.kt` to catch exceptions, log via Kermit, and return `Outcome<T, DomainError>`.
- [x] T007 [US1] Refactor `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` (and any other affected handlers) to safely consume the `Outcome`s returned by the repositories.

## Phase 4: User Story 2 - Exception-Free ViewModels in Vault (P1)

Goal: Completely remove exception-based flow control (`try-catch`) from `VaultViewModel` by relying on exhaustive `when` evaluation of `Outcome` from the `VaultService`.

**Independent Test**: Trigger a storage/network failure in `VaultService` and verify `VaultViewModel` correctly updates the `errorMessage` state via the `Outcome.Error` branch without explicitly catching exceptions.

- [x] T008 [US2] Refactor `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultService.kt` (and its implementation) to catch exceptions, log via Kermit, and return `Outcome<List<Item>, DomainError>`.
- [x] T009 [US2] Refactor `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt` to replace `try-catch` blocks with exhaustive `when` expressions on `VaultService` outcomes.

## Phase 5: Polish & Cross-Cutting Concerns

Goal: Validate architectural constraints, static analysis compliance, and ensure tests pass.

- [ ] T010 Run the test suite (`tools/local-ci.ps1`) to verify unit tests for `VaultViewModel` and `Fido2` repositories still pass with the new `Outcome` mapping.

---

## Dependencies

- Phase 1 (T001) must be completed first.
- Phase 3 (US1) and Phase 4 (US2) can be executed in parallel after T001 is complete.
- Within Phase 3, T002, T003, T005, and T006 can be executed in parallel. T004 depends on T002 and T003. T007 depends on T005 and T006.
- Phase 5 must be completed last.

## Implementation Strategy
Start by renaming `DataResult` to `Outcome` (T001). This will temporarily break the build for components using `DataResult`. Immediately follow up by refactoring the FIDO2 crypto/repository layers (US1) and Vault layers (US2) to use `Outcome`. This ensures a focused migration path that quickly restores a compiling state with the new functional paradigm.
