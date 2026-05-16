# Tasks: SQL Convention Alignment

**Input**: Design documents from `specs/043-sql-convention-alignment/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: Preparation and schema research verification

- [x] T001 Verify current database schema versions by inspecting `core/database/src/main/sqldelight/com/chimali/core/database/VaultDatabase/` and `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/` migration directories to confirm next migration numbers are `4.sqm` and `11.sqm` respectively

---

## Phase 2: Foundational — VaultDatabase Schema Migration

**Purpose**: Rename all tables and columns in the VaultDatabase module to `snake_case` and enforce column ordering. This MUST complete before User Story 5 (Kotlin updates for this module) can begin.

**⚠️ CRITICAL**: Fido2Database changes (Phase 3) can proceed in parallel with this phase.

- [x] T002 [P] Create migration `core/database/src/main/sqldelight/com/chimali/core/database/VaultDatabase/4.sqm` that renames all VaultDatabase tables from PascalCase to snake_case using the rename-recreate-copy-drop pattern: `Identity` → `identity`, `IdentityBackup` → `identity_backup`, `VaultEntry` → `vault_entry`, `Label` → `label`, `VaultEntryLabel` → `vault_entry_label`, `EventStore` → `event_store`, `SnapshotStore` → `snapshot_store`. Also rename columns to snake_case and enforce column ordering (PK → audit → alphabetised) per data-model.md mappings.

- [x] T003 Update schema definitions in `core/database/src/main/sqldelight/com/chimali/core/database/Vault.sq`: rename all `CREATE TABLE` statements to use snake_case table names and snake_case column names, reorder columns (PK → audit → alphabetised), update all query references (`INSERT`, `SELECT`, `DELETE`, `JOIN`) to use new names, and update the `truncateAll` query to reference new table names. Refer to data-model.md for the complete column mapping for `identity`, `identity_backup`, `vault_entry`, `label`, `vault_entry_label`, `event_store`, `snapshot_store`.

---

## Phase 3: Foundational — Fido2Database Schema Migration

**Purpose**: Rename all tables, columns, views, and indexes in the Fido2Database module to `snake_case` and enforce column ordering. This MUST complete before User Story 5 (Kotlin updates for this module) can begin.

- [x] T004 [P] Create migration `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/11.sqm` that renames all Fido2Database tables from PascalCase to snake_case using the rename-recreate-copy-drop pattern. Order of operations: (1) drop views `CredentialSummary` and `RelyingPartyStats`, (2) drop all indexes, (3) rename-recreate each table: `PasskeyCredential` → `passkey_credential`, `RelyingParty` → `relying_party`, `UserConsentRecord` → `user_consent_record`, `BluetoothHidSession` → `bluetooth_hid_session`, `PairedDevice` → `paired_device`, `EventStore` → `event_store`, `SnapshotStore` → `snapshot_store`, (4) recreate indexes with new names per data-model.md, (5) recreate views with new names. Also rename all columns to snake_case and enforce column ordering per data-model.md. Note: `isBiometricUsed` → `biometric_used` and `isPinUsed` → `pin_used` (drop the `is` prefix for boolean columns that don't start with `is_` in the canonical form — verify against data-model.md).

- [x] T005 Update schema definitions in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq`: rename all `CREATE TABLE` statements to snake_case table/column names, reorder columns per data-model.md, rename `CREATE VIEW` statements (`CredentialSummary` → `credential_summary`, `RelyingPartyStats` → `relying_party_stats`) and update their column references, rename `CREATE INDEX` statements per data-model.md index mapping, update all event sourcing queries to reference `event_store` and `snapshot_store`, and update `truncateAll` to reference new table names.

- [x] T006 [P] Update queries in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/PasskeyCredential.sq`: rename all column references from camelCase to snake_case (e.g., `createdAt` → `created_at`, `lastUsedAt` → `last_used_at`, `rpId` → `rp_id`, `signCount` → `sign_count`, `coseAlgorithm` → `cose_algorithm`, `credentialId` → `credential_id`, `credProtectPolicy` → `cred_protect_policy`, `privateKeyAlias` → `private_key_alias`, `publicKey` → `public_key`, `rpName` → `rp_name`, `userDisplayName` → `user_display_name`, `userId` → `user_id`, `userName` → `user_name`). Update named parameters in queries to match. Table name references change from `PasskeyCredential` to `passkey_credential`.

- [x] T007 [P] Update queries in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/RelyingParty.sq`: rename all column references from camelCase to snake_case (e.g., `createdAt` → `created_at`, `lastUsedAt` → `last_used_at`, `credentialCount` → `credential_count`, `iconUrl` → `icon_url`, `isBlocked` → `is_blocked`). Table name references change from `RelyingParty` to `relying_party`. Update `RelyingPartyStats` view references to `relying_party_stats`.

- [x] T008 [P] Update queries in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/UserConsentRecord.sq`: rename all column references from camelCase to snake_case (e.g., `isBiometricUsed` → `biometric_used`, `credentialId` → `credential_id`, `deviceId` → `device_id`, `ipAddress` → `ip_address`, `operationType` → `operation_type`, `isPinUsed` → `pin_used`, `rpId` → `rp_id`, `userAgent` → `user_agent`). Table name references change from `UserConsentRecord` to `user_consent_record`.

- [x] T009 [P] Update queries in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/PairedDevice.sq`: rename all column references from camelCase to snake_case (e.g., `macAddress` → `mac_address`, `createdAt` → `created_at`, `lastUsedAt` → `last_used_at`, `deviceClass` → `device_class`). Table name references change from `PairedDevice` to `paired_device`.

**Checkpoint**: All SQL schema files now use snake_case names and canonical column ordering. SQLDelight code generation can be triggered to regenerate Kotlin types.

---

## Phase 4: User Story 5 — Update Kotlin Data Layer References (Priority: P1) 🎯 MVP

**Goal**: All Kotlin code compiles against the new SQLDelight-generated types with snake_case accessors. Zero logic changes.

**Independent Test**: `.\gradlew assembleDebug` compiles successfully. All tests pass via `.\tools\local-ci.ps1`.

### Implementation for User Story 5

- [x] T010 [US5] Update entity mappers in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/mapper/EntityMappers.kt`: update import aliases for the renamed SQLDelight generated types (e.g., `import ... Passkey_credential as PasskeyCredentialEntity`), update all property accessor references from camelCase to snake_case (e.g., `this.createdAt` → `this.created_at`, `this.rpId` → `this.rp_id`, `this.publicKey` → `this.public_key`, `this.coseAlgorithm` → `this.cose_algorithm`, `this.signCount` → `this.sign_count`, `this.lastUsedAt` → `this.last_used_at`, `this.aaguid` → `this.aaguid`, `this.credentialId` → `this.credential_id`, `this.credProtectPolicy` → `this.cred_protect_policy`, `this.label` → `this.label`, `this.privateKeyAlias` → `this.private_key_alias`, `this.userName` → `this.user_name`, `this.userDisplayName` → `this.user_display_name`, `this.iconUrl` → `this.icon_url`, `this.credentialCount` → `this.credential_count`, `this.isBlocked` → `this.is_blocked`, `this.isBiometricUsed` → `this.biometric_used`, `this.isPinUsed` → `this.pin_used`, `this.operationType` → `this.operation_type`).

- [x] T011 [P] [US5] Update DAO in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt`: update the queries accessor from `database.passkeyCredentialQueries` to the new generated name (likely `database.passkey_credentialQueries`), update all named parameter references in `insert()`, `update()`, `updateLabel()`, `updatePublicKey()`, `updateSignCount()`, `updateLastUsedAt()` calls to use snake_case (e.g., `createdAt =` → `created_at =`, `lastUsedAt =` → `last_used_at =`, `coseAlgorithm =` → `cose_algorithm =`, `credentialId =` → `credential_id =`, `credProtectPolicy =` → `cred_protect_policy =`, `privateKeyAlias =` → `private_key_alias =`, `publicKey =` → `public_key =`, `rpId =` → `rp_id =`, `rpName =` → `rp_name =`, `signCount =` → `sign_count =`, `userDisplayName =` → `user_display_name =`, `userId =` → `user_id =`, `userName =` → `user_name =`). Update the import alias for the entity type.

- [x] T012 [P] [US5] Update DAO in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/RelyingPartyDao.kt`: update the queries accessor from `database.relyingPartyQueries` to the new generated name, update all named parameter references to snake_case (e.g., `createdAt =` → `created_at =`, `lastUsedAt =` → `last_used_at =`, `credentialCount =` → `credential_count =`, `iconUrl =` → `icon_url =`, `isBlocked =` → `is_blocked =`), update the import alias for the entity type, and update `credentialCount` property references in `RelyingPartyStatistics`/`TopRelyingParty` data classes.

- [x] T013 [P] [US5] Update repository in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`: update any direct references to SQLDelight generated types or query accessors to use the new snake_case names.

- [x] T014 [P] [US5] Update repository in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2RepositoryImpl.kt`: update any references to SQLDelight generated types or query accessors to use the new snake_case names.

- [x] T015 [P] [US5] Update repository in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/PairedDeviceRepositoryImpl.kt`: update `database.pairedDeviceQueries` to the new generated name, update column references (e.g., `macAddress` → `mac_address`, `createdAt` → `created_at`, `lastUsedAt` → `last_used_at`, `deviceClass` → `device_class`).

- [x] T016 [P] [US5] Update repository in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/RelyingPartyRepositoryImpl.kt`: update any references to SQLDelight generated types or query accessors to use the new snake_case names.

- [x] T017 [P] [US5] Update any remaining Kotlin source files in `feature/fido2/src/androidMain/kotlin/` that reference SQLDelight generated types: search for imports of `com.chimali.fido2.data.database.PasskeyCredential`, `com.chimali.fido2.data.database.RelyingParty`, `com.chimali.fido2.data.database.UserConsentRecord`, `com.chimali.fido2.data.database.BluetoothHidSession`, `com.chimali.fido2.data.database.PairedDevice`, and update import paths and any property accessor references. Key files include `CorruptedKeyRepairWorker.kt`, `BluetoothHidTransportImpl.kt`, and any use-case or service files that directly touch entity types.

- [x] T018 [P] [US5] Update Kotlin source files in `core/database/` and `core/data/` that reference VaultDatabase SQLDelight generated types: update imports and property accessors for renamed tables (`Identity`, `IdentityBackup`, `VaultEntry`, `Label`, `VaultEntryLabel`, `EventStore`, `SnapshotStore`) and their snake_case column accessors. Key file: `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/VaultAggregateServiceImpl.kt`.

**Checkpoint**: Project compiles with `.\gradlew assembleDebug`. All SQLDelight-generated types are correctly referenced.

---

## Phase 5: User Story 5 (continued) — Update Test Files

**Goal**: All test files compile and pass against the renamed schema.

- [x] T019 [P] [US5] Update test files in `feature/fido2/src/test/kotlin/` that reference SQLDelight generated types: update imports, property accessors, and entity construction in all test files. Key files: `DatabaseSchemaTest.kt`, `PasskeyCredentialDaoTest.kt`, `CredentialRepositoryImplTest.kt`, `SecurityStorageIntegrityTest.kt`, `CredentialManagementViewModelTest.kt`, `ManagementIntegrationTest.kt`, `MultiAlgorithmIntegrationTest.kt`, `RegistrationAuthenticationDataIntegrationTest.kt`, `Fido2StressTest.kt`, `SelectCredentialUseCaseTest.kt`, `PasskeyCredentialTest.kt`, `GetAssertionUseCaseTest.kt`, `GetAllCredentialsUseCaseTest.kt`, `Ctap2Fido21FlagsTest.kt`, `Ctap2CredentialManagementHandlerTest.kt`, `RegisterCredentialUseCaseTest.kt`, `RelyingPartyTest.kt`, `UserConsentRecordTest.kt`.

- [x] T020 [P] [US5] Update instrumentation test files in `feature/fido2/src/androidTest/kotlin/` that reference SQLDelight generated types: `RegistrationFlowIntegrationTest.kt`, `CredentialListScreenTest.kt`.

- [x] T021 [P] [US5] Update any test files in `core/domain/src/commonTest/` that reference renamed types: `DomainModelSerializationTest.kt`.

**Checkpoint**: All tests pass. `.\gradlew test` and `.\gradlew connectedAndroidTest` succeed.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and cleanup

- [x] T022 Run full local CI pipeline via `.\tools\local-ci.ps1` to verify Ktlint, Detekt, lint, and all unit tests pass with zero violations.

- [x] T023 Run `.\gradlew assembleDebug` and `.\gradlew assembleRelease` to verify both build variants compile successfully.

- [x] T024 Verify migration correctness by inspecting the generated schema dump: run `.\gradlew generateDebugFido2DatabaseSchema` (or equivalent SQLDelight schema task) and confirm all table/column/view/index names are snake_case.

- [x] T025 Update project changelog (`CHANGELOG.md`) to document the SQL convention alignment refactoring under the appropriate version section.

- [x] T026 Run quickstart.md validation steps to confirm end-to-end verification.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (VaultDatabase Migration)**: Depends on Phase 1 — can run in parallel with Phase 3
- **Phase 3 (Fido2Database Migration)**: Depends on Phase 1 — can run in parallel with Phase 2
- **Phase 4 (Kotlin Updates)**: Depends on BOTH Phase 2 and Phase 3 completion
- **Phase 5 (Test Updates)**: Depends on Phase 4 completion
- **Phase 6 (Polish)**: Depends on Phase 5 completion

### Within Phase 3 (Fido2Database)

- T004 (migration .sqm) and T005 (schema .sq) are sequential — T005 depends on T004's column mapping
- T006, T007, T008, T009 (query .sq files) can run in parallel [P] after T005

### Within Phase 4 (Kotlin Updates)

- T010 (EntityMappers) should complete first — it establishes the import alias pattern
- T011–T018 can all run in parallel [P] after T010

### Within Phase 5 (Test Updates)

- T019, T020, T021 can all run in parallel [P]

### Parallel Opportunities

```text
# Phase 2 + Phase 3 in parallel:
Task: T002 (VaultDatabase migration 4.sqm)
Task: T004 (Fido2Database migration 11.sqm)

# Query files in parallel after schema:
Task: T006 (PasskeyCredential.sq)
Task: T007 (RelyingParty.sq)
Task: T008 (UserConsentRecord.sq)
Task: T009 (PairedDevice.sq)

# Kotlin DAOs/repos in parallel:
Task: T011 (PasskeyCredentialDao.kt)
Task: T012 (RelyingPartyDao.kt)
Task: T013 (CredentialRepositoryImpl.kt)
Task: T014 (Fido2RepositoryImpl.kt)
Task: T015 (PairedDeviceRepositoryImpl.kt)
Task: T016 (RelyingPartyRepositoryImpl.kt)
Task: T017 (remaining fido2 Kotlin files)
Task: T018 (core database Kotlin files)

# Tests in parallel:
Task: T019 (unit tests)
Task: T020 (instrumentation tests)
Task: T021 (core domain tests)
```

---

## Implementation Strategy

### MVP First (Phase 2 + 3 + 4)

1. Complete Phase 1: Setup verification
2. Complete Phase 2 + 3: All SQL schema files updated (can be parallel)
3. Complete Phase 4: Kotlin compiles against new types
4. **STOP and VALIDATE**: `.\gradlew assembleDebug` passes
5. Complete Phase 5: Tests pass
6. **STOP and VALIDATE**: `.\tools\local-ci.ps1` passes

### Incremental Delivery

1. SQL migrations + schemas → Schema correct
2. Kotlin data layer → Compiles
3. Test updates → All green
4. Polish → CI clean, changelog updated

---

## Notes

- [P] tasks = different files, no dependencies
- [US5] label maps all Kotlin update tasks to User Story 5 (the mandatory companion to SQL renaming)
- SQL schema tasks (Phase 2–3) are not labeled with a story since they are foundational prerequisites
- The migration pattern (rename-recreate-copy-drop) is established in existing `7.sqm` — follow the same approach
- After completing T005, run SQLDelight code generation to identify the exact generated type names before starting Phase 4
- Commit after each phase or logical group
