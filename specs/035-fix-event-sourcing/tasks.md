# Tasks: fix-event-sourcing

**Input**: Design documents from `/specs/035-fix-event-sourcing/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md

**Tests**: Tests are included per the Constitution (§IX Local CI/CD, §III TDD enforcement).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: No new project initialization needed. This feature modifies existing modules. Setup ensures the build configuration fix is in place.

- [x] T001 Add `sourceSets.getByName("main") { assets.srcDirs("src/androidMain/assets") }` to the `android {}` block in `core/security/build.gradle.kts` and verify Gradle sync succeeds

**Checkpoint**: Build configuration fix applied. BIP39 asset will now be bundled into the APK.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define the `EventStoreKeyProvider` interface and implementation. This MUST be complete before any repository can be refactored.

**⚠️ CRITICAL**: No User Story 2 repository work can begin until this phase is complete.

### Tests for Foundation

- [x] T003 Write unit tests for `EventStoreKeyProviderImpl` in `core/security/src/androidHostTest/kotlin/com/chimali/core/security/impl/EventStoreKeyProviderImplTest.kt`: (a) verify HMAC-SHA512 key derivation produces a 32-byte key, (b) verify `IllegalStateException` is thrown when master seed is not initialized, (c) verify distinct keys are produced for vault vs passkey labels

### Implementation for Foundation

- [x] T006 Define `EventStoreKeyProvider` interface with `suspend fun getEventStoreKey(aggregateLabel: String): ByteArray` in `core/security/src/commonMain/kotlin/com/chimali/core/security/EventStoreKeyProvider.kt`
- [x] T007 Implement `EventStoreKeyProviderImpl` using HMAC-SHA512 derivation from `WalletMasterSeedProvider.getMasterSeed()` in `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/EventStoreKeyProviderImpl.kt`
- [x] T008 Register `EventStoreKeyProviderImpl` as a singleton in Koin via `core/security/src/androidMain/kotlin/com/chimali/core/security/di/SecurityModule.kt`
- [x] T009 Run tests T003–T005 and verify they pass

**Checkpoint**: Foundation ready — `EventStoreKeyProvider` is defined, implemented, tested, and registered in DI.

---

## Phase 3: User Story 1 — FIDO2 Crypto Initialization (Priority: P1) 🎯 MVP

**Goal**: Eliminate the `FileNotFoundException: bip39_english.txt` crash on app launch.

**Independent Test**: Build the APK, verify `bip39_english.txt` is present in the APK assets, launch the app and confirm no crash.

### Implementation for User Story 1

- [x] T010 [US1] Build debug APK via `./gradlew :app:assembleDebug` and verify build succeeds
- [x] T011 [US1] Verify `bip39_english.txt` is present in the debug APK via Analyze APK or `verifyBip39Asset` task
- [x] T012 [US1] Run `./gradlew :core:security:androidHostTest` and verify all 7 Bip39 unit tests pass

**Checkpoint**: User Story 1 complete. BIP39 wordlist asset is bundled, app launches without `FileNotFoundException`.

---

## Phase 4: User Story 2 — Secure Event Sourcing Storage (Priority: P1)

**Goal**: Replace dummy encryption keys with real keys derived from the master seed, and truncate legacy dummy-encrypted data.

**Independent Test**: Verify that `EventStore` and `SnapshotStore` payloads cannot be decrypted with a zero-filled key after the fix.

### SQLDelight Migrations (Truncation)

- [x] T013 [P] [US2] Create SQLDelight migration `3.sqm` to truncate `EventStore` and `SnapshotStore` tables in `core/database/src/main/sqldelight/com/chimali/core/database/VaultDatabase/3.sqm`
- [x] T014 [P] [US2] Create SQLDelight migration `10.sqm` to truncate `EventStore` and `SnapshotStore` tables in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/10.sqm`

### DI Wiring (must precede repository refactoring)

- [x] T015 [P] [US2] Update `DataModule` DI wiring to pass `EventStoreKeyProvider` to Vault repository constructors in `core/data/src/main/kotlin/com/chimali/core/data/di/DataModule.kt`
- [x] T016 [P] [US2] Update `Fido2Module` DI wiring to pass `EventStoreKeyProvider` to Passkey repository constructors in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`

### Refactoring Vault Repositories (core:data)

- [x] T017 [US2] Inject `EventStoreKeyProvider` into `EventStoreRepositoryImpl` and replace `dummyKey` with `keyProvider.getEventStoreKey("chimali_vault_es_v1")` in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/EventStoreRepositoryImpl.kt`
- [x] T018 [US2] Inject `EventStoreKeyProvider` into `SnapshotRepositoryImpl` and replace `dummyKey` with `keyProvider.getEventStoreKey("chimali_vault_es_v1")` in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/SnapshotRepositoryImpl.kt`

### Refactoring Passkey Repositories (feature:fido2)

- [x] T019 [P] [US2] Inject `EventStoreKeyProvider` into `PasskeyEventStoreRepositoryImpl` and replace `dummyKey` with `keyProvider.getEventStoreKey("chimali_passkey_es_v1")` in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyEventStoreRepositoryImpl.kt`
- [x] T020 [P] [US2] Inject `EventStoreKeyProvider` into `PasskeySnapshotRepositoryImpl` and replace `dummyKey` with `keyProvider.getEventStoreKey("chimali_passkey_es_v1")` in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeySnapshotRepositoryImpl.kt`

### Update Existing Tests

- [x] T021 [P] [US2] Update `VaultAggregateServiceImplTest` to mock `EventStoreKeyProvider` instead of relying on `dummyKey` in `core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/VaultAggregateServiceImplTest.kt`
- [x] T022 [P] [US2] Update `SnapshotRepositoryImplTest` to mock `EventStoreKeyProvider` instead of relying on `dummyKey` in `core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/SnapshotRepositoryImplTest.kt`
- [x] T023 [P] [US2] Update `PasskeyAggregateServiceImplTest` to mock `EventStoreKeyProvider` instead of relying on `dummyKey` in `feature/fido2/src/test/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyAggregateServiceImplTest.kt`

### Verification

- [x] T024 [US2] Run full test suite `./gradlew test` and verify all tests pass with new key provider mocks
- [x] T025 [US2] Verify no remaining `dummyKey` references exist in production code (test code excluded)

**Checkpoint**: User Story 2 complete. All event/snapshot repositories use real derived keys. Legacy dummy-encrypted data truncated via migrations.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and CI compliance.

- [x] T026 Run `tools/local-ci.ps1` and verify full pipeline passes (Ktlint, Detekt, unit tests)
- [x] T027 Run quickstart.md validation: build APK, verify asset presence, launch app, confirm no crash, confirm FIDO2 registration works

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS User Story 2
- **User Story 1 (Phase 3)**: Depends on Phase 1 only (T001) — can run in parallel with Phase 2
- **User Story 2 (Phase 4)**: Depends on Phase 2 completion (EventStoreKeyProvider must exist)
- **Polish (Phase 5)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 1 — No dependency on Phase 2 or US2
- **User Story 2 (P1)**: Can start after Phase 2 — Requires `EventStoreKeyProvider` to exist. Independent of US1 for code changes but logically requires the asset fix to be deployable.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD per Constitution §III)
- Interface before implementation
- Implementation before DI wiring
- DI wiring before integration tests
- Story complete before moving to next phase

### Parallel Opportunities

- T013 and T014 can run in parallel (different databases)
- T015 and T016 can run in parallel (DI wiring for different modules)
- T017/T018 (Vault repos) can run in parallel with T019/T020 (Passkey repos) after DI wiring
- T021, T022, T023 can run in parallel (different test files)

---

## Parallel Example: User Story 2

```text
# Launch all migration scripts together:
Task T013: "Truncate VaultDatabase EventStore/SnapshotStore"
Task T014: "Truncate Fido2Database EventStore/SnapshotStore"

# Launch Vault and Passkey repo refactoring in parallel (after respective DI updates):
Task T015: "Refactor EventStoreRepositoryImpl (Vault)"
Task T018: "Refactor PasskeyEventStoreRepositoryImpl (Passkey)"

# Launch all test updates in parallel:
Task T021: "Update VaultAggregateServiceImplTest"
Task T022: "Update SnapshotRepositoryImplTest"
Task T023: "Update PasskeyAggregateServiceImplTest"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 3: User Story 1 (T010–T012)
3. **STOP and VALIDATE**: App launches without `FileNotFoundException`
4. Deploy/demo if ready — FIDO2 feature no longer crashes

### Full Delivery

1. Complete Setup → Asset fix applied
2. Complete Foundation → `EventStoreKeyProvider` ready
3. Complete User Story 1 → App no longer crashes (MVP!)
4. Complete User Story 2 → Real encryption keys replace dummy keys
5. Complete Polish → CI passes, all validations green

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Verify tests fail before implementing (TDD)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- The `dummyKey` in `RegistrationAuthenticationDataIntegrationTest.kt` is an unrelated mock key for public key decoding — it is NOT part of this refactoring
