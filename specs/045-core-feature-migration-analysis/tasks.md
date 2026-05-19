---
description: "Task list for Core Feature Migration"
---

# Tasks: Core Feature Migration

**Input**: Design documents from `specs/045-core-feature-migration-analysis/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each migration.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Exact file paths are included in descriptions.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and validation

- [ ] T001 Verify project structure and baseline local CI via `.\tools\local-ci.ps1`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

*No blocking foundational tasks required for these migrations. All stories can be implemented independently.*

---

## Phase 3: User Story 1 - Unified Encrypted Database Storage (Priority: P1) 🎯 MVP

**Goal**: Provide a centralized, SQLCipher-backed encrypted database factory in `:core:database` for all feature modules, enforcing zeroed keys.

**Independent Test**: Can be fully validated by creating a database through the centralized service and verifying that the resulting database file is encrypted, that integrity checks pass, and that the encryption key is properly derived.

### Implementation for User Story 1

- [ ] T002 [P] [US1] Create `EncryptedDriverFactory` using SQLCipher `SupportFactory` in `core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt`, enforcing `try/finally` zeroing of key material (Constitution §X.5)
- [ ] T002b [P] [US1] Create unit/integration tests in `:core:database` to verify driver creation, database encryption, key derivation (PBKDF2-SHA512), and file integrity check
- [ ] T003 [US1] Update `DatabaseModule` Koin DSL to use `EncryptedDriverFactory` instead of raw `AndroidSqliteDriver` in `core/database/src/main/java/com/chimali/core/database/di/DatabaseModule.kt`
- [ ] T004 [P] [US1] Delete the non-functional `SqlCipherWrapper` stub from `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/storage/SqlCipherWrapper.kt`
- [ ] T005 [US1] Remove any FIDO2 DI references to `SqlCipherWrapper` in `:feature:fido2` (e.g., in `Fido2Module` if explicitly declared)

**Checkpoint**: At this point, User Story 1 (SQLCipher migration) should be fully functional and testable independently.

---

## Phase 4: User Story 2 - Secure Clipboard with Auto-Clear (Priority: P2)

**Goal**: Centralize clipboard operations using the existing, fully-implemented `ClipboardManagerService` in `:core:common`, ensuring sensitive data auto-clears within 60s.

**Independent Test**: Can be tested by copying a sensitive value, waiting for the configured timeout, and verifying that the clipboard no longer contains the original value.

### Implementation for User Story 2

- [ ] T006 [P] [US2] Delete the obsolete `ClipboardManagerWrapper` stub in `feature/vault/src/main/java/com/chimali/feature/vault/internal/ClipboardManagerWrapper.kt`
- [ ] T007 [US2] Update `VaultViewModel` to use core `ClipboardManagerService` instead of the old wrapper in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`
- [ ] T008 [US2] Update `vaultModule` Koin DSL to remove `ClipboardManagerWrapper` binding (if explicitly bound) in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`

**Checkpoint**: At this point, User Story 2 (Clipboard migration cleanup) should be fully functional and testable independently.

---

## Phase 5: User Story 3 - Centralized Crypto Provider Initialization (Priority: P3)

**Goal**: Register the BouncyCastle security provider exactly once at application startup so any feature module can rely on it being available immediately.

**Independent Test**: Can be validated by verifying that cryptographic operations succeed immediately at application startup.

### Implementation for User Story 3

- [ ] T009 [P] [US3] Add `Security.addProvider(BouncyCastleProvider())` to `onCreate` in `app/src/main/kotlin/com/chimali/ChimaliApplication.kt` before Koin initialization
- [ ] T010 [P] [US3] Remove redundant `Security.addProvider()` call from `WarmUpHelper.warmUpBouncyCastle()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/WarmUpHelper.kt`
- [ ] T011 [P] [US3] Remove redundant `Security.addProvider()` call from `Fido2CryptoService` `init` block in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt`

**Checkpoint**: At this point, User Story 3 (BouncyCastle provider registration) should be fully functional and testable independently.

---

## Phase 6: User Story 4 - Shared Biometric Capability Check (Priority: P4)

**Goal**: Extract the pure platform capability check (`PlatformUserVerification`) to `:core:security` so multiple features can query biometric readiness without depending on FIDO2.

**Independent Test**: Can be validated by calling the capability check from a test that uses only core module dependencies.

### Implementation for User Story 4

- [ ] T012 [P] [US4] Add `implementation(libs.androidx.biometric)` to `:core:security` androidMain dependencies in `core/security/build.gradle.kts`
- [ ] T013 [P] [US4] Copy `PlatformUserVerification.kt` (expect class) to `core/security/src/commonMain/kotlin/com/chimali/core/security/biometrics/PlatformUserVerification.kt` and update its package
- [ ] T014 [P] [US4] Copy Android `actual` to `core/security/src/androidMain/kotlin/com/chimali/core/security/biometrics/PlatformUserVerification.kt` and update its package
- [ ] T015 [P] [US4] Copy iOS `actual` to `core/security/src/iosMain/kotlin/com/chimali/core/security/biometrics/PlatformUserVerification.kt` and update its package
- [ ] T015b [P] [US4] Create KMP unit test in `:core:security` (androidHostTest) to verify `PlatformUserVerification` is callable using only core dependencies per SC-004
- [ ] T016 [US4] Update all import statements in `:feature:fido2` (including main and test source sets) to use the new `com.chimali.core.security.biometrics` package
- [ ] T017 [US4] Delete the old `PlatformUserVerification.kt` files from `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/platform/`, `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/platform/`, and `feature/fido2/src/iosMain/kotlin/com/chimali/fido2/platform/`

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and validation of the migration

- [ ] T018 Run `.\tools\local-ci.ps1` to validate all changes
- [ ] T019 Update project changelog with migration details

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: N/A
- **User Stories (Phase 3+)**: Can start immediately after Setup
  - User stories can proceed in parallel
  - Or sequentially in priority order (P1 → P2 → P3 → P4)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start immediately - No dependencies on other stories
- **User Story 2 (P2)**: Can start immediately - No dependencies on other stories
- **User Story 3 (P3)**: Can start immediately - No dependencies on other stories
- **User Story 4 (P4)**: Can start immediately - No dependencies on other stories

### Within Each User Story

- Service migrations/implementations before integration
- Story complete before moving to next priority

### Parallel Opportunities

- US1, US2, US3, and US4 can all be worked on in parallel by different team members
- Tasks marked [P] within a story can run in parallel

---

## Parallel Example: User Story 4

```bash
# Launch file copies/creation in parallel:
Task: "Copy expect class to core/security"
Task: "Copy Android actual to core/security"
Task: "Copy iOS actual to core/security"
Task: "Add androidx.biometric dependency to core/security"
```

---

## Implementation Strategy

### Incremental Delivery

1. Add User Story 1 → Test independently → Validate MVP
2. Add User Story 2 → Test independently
3. Add User Story 3 → Test independently
4. Add User Story 4 → Test independently
5. Each story migrates a discrete piece of architecture without breaking previous ones

### Parallel Team Strategy

With multiple developers:

1. Developer A: User Story 1 (SQLCipher)
2. Developer B: User Story 4 (Biometric Check)
3. Developer C: User Story 2 (Clipboard) & User Story 3 (BouncyCastle)
4. Stories complete and integrate independently
