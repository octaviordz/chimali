# Implementation Tasks: Functional Exception Handling

## Implementation Strategy

We will deliver this feature in phases to ensure a safe migration:
1. **MVP**: Implement the core `DataResult` and `DomainError` sealed classes in the `core/common` module.
2. **Incremental Migration**: Migrate low-level data layer services and crypto components to use `DataResult` and catch explicit exceptions instead of `Exception`.
3. **UI Integration**: Update ViewModels to exhaustively consume `DataResult` and log at the boundary.

## Phase 1: Setup

**Goal**: Establish the base functional error handling types.

- [X] T001 Create `DomainError.kt` and `DataResult.kt` sealed hierarchies in `core/common/src/commonMain/kotlin/com/chimali/core/common/result/`.
- [X] T002 Implement `FunctionalCatching.kt` utility in `core/common` to provide `runCatchingFunctional` inline functions that map standard exceptions while properly rethrowing `CancellationException`.

## Phase 2: Foundational

**Goal**: Address core module exception handling suppressions.

- [X] T003 Remove `@Suppress("TooGenericExceptionCaught")` from `core/common/src/androidMain/kotlin/com/chimali/core/clipboard/AndroidClipboardManagerService.kt` and map specific system exceptions.
- [X] T004 Remove `@Suppress("TooGenericExceptionCaught")` from `core/common/src/iosMain/kotlin/com/chimali/core/clipboard/IosClipboardManagerService.kt`.
- [X] T005 Remove `@Suppress("TooGenericExceptionCaught")` from `core/common/src/androidMain/kotlin/com/chimali/core/common/BuildVariant.kt`.

## Phase 3: User Story 1 (System Reliability via Specific Error Handling)

**Goal**: Remove all generic `catch (e: Exception)` blocks and `@Suppress` annotations from the data, domain, and transport layers.

**Independent Test**: Static analysis passes with zero Detekt violations for generic exception catching.

- [X] T006 [P] [US1] Migrate `feature/fido2` crypto services to catch specific exceptions and add Kermit boundary logging: `CredentialEncryptionService.kt`, `HmacSecretProcessor.kt`, `Fido2CryptoService.kt`, `PostQuantumCrypto.kt`, `MemoryUtils.kt`.
- [X] T007 [P] [US1] Migrate `feature/fido2` domain models to catch specific exceptions and add Kermit boundary logging: `AttestationObject.kt`, `MakeCredentialOptions.kt`, `PublicKeyCredentialDescriptor.kt`, `RelyingParty.kt`, `PublicKeyCredentialRpEntity.kt`, `PublicKeyCredentialUserEntity.kt`, `ClientData.kt`.
- [X] T008 [P] [US1] Migrate `feature/fido2` data layer to return `DataResult` and add Kermit boundary logging: `SqlCipherWrapper.kt`, `EncryptedMetadataIndexService.kt`, `CredentialStorageService.kt`, `PasskeyCredentialRepositoryImpl.kt`, `CredentialRepositoryImpl.kt`.
- [X] T009 [P] [US1] Migrate `feature/fido2` CTAP2 handlers to catch specific exceptions and add Kermit boundary logging: `Ctap2CredentialManagementHandler.kt`, `Ctap2GetAssertionHandler.kt`, `Ctap2MakeCredentialHandler.kt`, `Ctap2ResetAuthenticatorHandler.kt`.
- [X] T010 [P] [US1] Migrate `feature/fido2` transports and background workers to catch specific exceptions and add Kermit boundary logging: `BluetoothHidTransportImpl.kt`, `CorruptedKeyRepairWorker.kt`, `BluetoothHidDeviceWrapper.kt`.
- [X] T011 [P] [US1] Migrate `feature/fido2` utilities and use cases to catch specific exceptions and add Kermit boundary logging: `WarmUpHelper.kt`, `LocalCrashReportingLogWriter.kt`, `MemoryUtils.kt`, `GetUserConsentUseCase.kt`, `RegisterCredentialUseCase.kt`.

## Phase 4: User Story 2 (Centralized Error Monitoring and Functional Propagation)

**Goal**: Ensure ViewModels exclusively use `DataResult` for flow control and execute boundary logging via Kermit.

**Independent Test**: Trigger a repository failure and ensure no app crash occurs, a user-friendly UI state is shown, and the error is logged correctly at the repository boundary.

- [X] T012 [P] [US2] Refactor `VaultViewModel.kt` in `feature/vault` to consume `DataResult` and remove suppressions.
- [X] T013 [P] [US2] Refactor `DevToolsViewModel.kt` in `feature/fido2` to consume `DataResult` and remove suppressions.
- [X] T014 [P] [US2] Refactor `CredentialManagementViewModel.kt` in `feature/fido2` to consume `DataResult` and remove suppressions.

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T015 Verify exhaustiveness of all `when` statements over `DataResult` in UI and domain mappers.
- [X] T016 Run `local-ci.ps1` to ensure all unit tests pass, and zero Detekt violations remain project-wide.

## Dependencies

- Phase 1 must be completed before Phase 2.
- Phase 3 depends on Phase 1 (for `DataResult`). Tasks T006 to T011 can be done in parallel.
- Phase 4 depends on Phase 3 (repository refactors). Tasks T012, T013, T014 can be done in parallel.
- Final Phase requires all previous phases.

## Parallel Execution Examples

- **Developer A** can focus on crypto service refactoring (T006).
- **Developer B** can tackle CTAP2 handler migrations (T009) concurrently.
- Once the repository layer is updated (T008), the UI updates (T012, T013, T014) can be split among multiple developers.
