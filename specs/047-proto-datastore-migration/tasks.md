# Tasks: Proto DataStore Migration

**Input**: Design documents from `/specs/047-proto-datastore-migration/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Tests are included as this is a security-critical migration requiring validation

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **KMP structure**: `core/common/` for shared code, `feature/fido2/` for feature-specific code
- **Platform-specific**: `androidMain/` for Android-specific implementations

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and dependency setup

- [x] T001 Add Proto DataStore dependencies to core/common/build.gradle.kts in core/common/build.gradle.kts
- [x] T002 Add Protocol Buffers plugin configuration to core/common/build.gradle.kts in core/common/build.gradle.kts
- [x] T003 [P] Add Android DataStore dependencies to core/androidMain/build.gradle.kts in core/common/build.gradle.kts

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Create proto directory structure in core/common/src/main/proto in core/common/src/main/proto
- [x] T005 Define UserPreferences Protocol Buffer schema in core/common/src/main/proto/user_preferences.proto
- [x] T006 Configure protobuf compilation in core/common/build.gradle.kts in core/common/build.gradle.kts
- [x] T007 Create datastore package structure in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore
- [x] T008 Implement UserPreferencesSerializer in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializer.kt
- [x] T009 Implement UserPreferencesDataStore extension in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesDataStore.kt
- [x] T010 [P] Create EncryptionWrapper in core/common/src/androidMain/kotlin/com/chimali/core/common/datastore/EncryptionWrapper.kt
- [x] T011 [P] Implement Android KeyStore integration in EncryptionWrapper in core/common/src/androidMain/kotlin/com/chimali/core/common/datastore/EncryptionWrapper.kt
- [x] T011a [P] Implement memory zeroing for sensitive data in EncryptionWrapper in core/common/src/androidMain/kotlin/com/chimali/core/common/datastore/EncryptionWrapper.kt
- [x] T012 Configure Koin module for DataStore dependencies in core/common/di/DataStoreModule.kt

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Migrate Wallet Seed Storage (Priority: P1) 🎯 MVP

**Goal**: Migrate BIP39 mnemonic storage from EncryptedSharedPreferences to Proto DataStore with encryption

**Independent Test**: Verify existing BIP39 mnemonics stored in EncryptedSharedPreferences are successfully migrated to Proto DataStore and can be retrieved without data loss

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T013 [P] [US1] Unit test for UserPreferencesSerializer in core/common/src/commonTest/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializerTest.kt
- [x] T014 [P] [US1] Unit test for EncryptionWrapper in core/common/src/androidTest/kotlin/com/chimali/core/common/datastore/EncryptionWrapperTest.kt
- [x] T015 [P] [US1] Integration test for wallet seed migration in feature/fido2/src/androidTest/kotlin/com/chimali/fido2/WalletSeedMigrationTest.kt

### Implementation for User Story 1

- [x] T016 [US1] Implement migration logic in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T017 [US1] Add transaction flag for crash recovery in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T018 [US1] Implement legacy data reading from EncryptedSharedPreferences in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T019 [US1] Implement data writing to Proto DataStore in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T020 [US1] Add Info level logging for migration progress in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T021 [US1] Add Error level logging for migration failures in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T022 [US1] Implement corrupted legacy data handling in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T023 [US1] Delete legacy EncryptedSharedPreferences file after successful migration in WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T024 [US1] Remove EncryptedSharedPreferences imports from WalletMasterSeedProvider in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [x] T025 [US1] Update WalletMasterSeedProviderTest to use DataStore in feature/fido2/src/androidTest/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProviderTest.kt

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Migrate FIDO2 Settings (Priority: P2)

**Goal**: Migrate FIDO2 settings (max credential count) from EncryptedSharedPreferences to Proto DataStore

**Independent Test**: Verify existing max credential count settings are migrated to Proto DataStore and the FIDO2 authenticator respects the migrated limits

### Tests for User Story 2

- [x] T026 [P] [US2] Integration test for FIDO2 settings migration in feature/fido2/src/androidTest/kotlin/com/chimali/fido2/Fido2SettingsMigrationTest.kt

### Implementation for User Story 2

- [x] T027 [US2] Implement migration logic in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T028 [US2] Add transaction flag for crash recovery in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T029 [US2] Implement legacy data reading from EncryptedSharedPreferences in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T030 [US2] Implement data writing to Proto DataStore in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T031 [US2] Add Info level logging for migration progress in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T032 [US2] Add Error level logging for migration failures in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T033 [US2] Implement corrupted legacy data handling in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T034 [US2] Delete legacy EncryptedSharedPreferences file after successful migration in Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt
- [x] T035 [US2] Remove EncryptedSharedPreferences imports from Fido2SettingsRepositoryImpl in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Establish Shared User Preferences Schema (Priority: P3)

**Goal**: Establish shared User Preferences schema in common KMP location using Protocol Buffers

**Independent Test**: Verify Protocol Buffer schema compiles correctly, generates expected Kotlin data classes, and can store/retrieve preference data in common KMP module

### Tests for User Story 3

- [x] T036 [P] [US3] Unit test for UserPreferencesDataStore in core/common/src/commonTest/kotlin/com/chimali/core/common/datastore/UserPreferencesDataStoreTest.kt
- [x] T037 [P] [US3] Integration test for cross-platform data sharing in core/common/src/commonTest/kotlin/com/chimali/core/common/datastore/CrossPlatformDataStoreTest.kt

### Implementation for User Story 3

- [x] T038 [US3] Verify Protocol Buffer compilation generates Kotlin classes in core/common/build.gradle.kts in core/common/build.gradle.kts
- [x] T039 [US3] Add migration_version field handling in UserPreferencesSerializer in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializer.kt
- [x] T040 [US3] Add migration_completed field handling in UserPreferencesSerializer in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializer.kt
- [x] T041 [US3] Implement schema evolution support in UserPreferencesSerializer in core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializer.kt
- [x] T042 [US3] Add reserved field numbers for future preferences in core/common/proto/user_preferences.proto

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup and validation across all user stories

- [x] T043 [P] Remove EncryptedSharedPreferences references from Fido2CryptoService.kt in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt
- [x] T044 [P] Remove EncryptedSharedPreferences references from AesSivEncryptionManager.kt in core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesSivEncryptionManager.kt
- [x] T045 [P] Remove EncryptedSharedPreferences references from BluetoothHidTransportImpl.kt in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportImpl.kt
- [x] T046 [P] Remove EncryptedSharedPreferences dependency from build.gradle.kts files in feature/fido2/build.gradle.kts
- [x] T047 [P] Remove SharedPreferences references from codebase (grep search and removal)
- [x] T048 Run Detekt static analysis in tools/local-ci.ps1
- [x] T049 Run Ktlint formatting check in tools/local-ci.ps1
- [x] T050 Run Local CI pipeline in tools/local-ci.ps1
- [x] T051 Update documentation in docs/ if needed
- [x] T052 Verify zero EncryptedSharedPreferences references remain in codebase
- [x] T053 Verify zero SharedPreferences references remain in codebase
- [x] T054 Validate quickstart.md implementation steps
- [x] T055 [P] Add performance benchmark for DataStore operations in core/common/src/commonTest/kotlin/com/chimali/core/common/datastore/UserPreferencesDataStorePerformanceTest.kt

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - User Story 1 (P1): Can start after Foundational - No dependencies on other stories
  - User Story 2 (P2): Can start after Foundational - Independent of US1
  - User Story 3 (P3): Can start after Foundational - Independent of US1/US2
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - No dependencies on US1
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - No dependencies on US1/US2

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD approach)
- Migration logic before cleanup
- Core implementation before logging
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T003)
- Foundational tasks T010, T011 can run in parallel
- All tests for a user story marked [P] can run in parallel
- Polish tasks T043, T044, T045, T046, T047 can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Unit test for UserPreferencesSerializer in core/common/src/commonTest/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializerTest.kt"
Task: "Unit test for EncryptionWrapper in core/common/src/androidTest/kotlin/com/chimali/core/common/datastore/EncryptionWrapperTest.kt"
Task: "Integration test for wallet seed migration in feature/fido2/src/androidTest/kotlin/com/chimali/fido2/WalletSeedMigrationTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004-T012) - CRITICAL
3. Complete Phase 3: User Story 1 (T013-T025)
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Verify wallet seed migration works with existing data
6. Run Local CI pipeline

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Validate MVP
3. Add User Story 2 → Test independently → Validate settings migration
4. Add User Story 3 → Test independently → Validate shared schema
5. Complete Polish → Final validation and cleanup

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (wallet seed migration)
   - Developer B: User Story 2 (FIDO2 settings migration)
   - Developer C: User Story 3 (shared schema)
3. Stories complete and integrate independently
4. Team completes Polish phase together

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing (TDD approach)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Security-critical: All sensitive data must remain encrypted per Constitution §I
- Memory safety: Sensitive data must be zeroed after use per Constitution X.5
