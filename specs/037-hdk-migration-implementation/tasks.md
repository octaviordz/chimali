# Tasks: HDK Migration Implementation

**Input**: Design documents from `/specs/037-hdk-migration-implementation/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Exact file paths are included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

*(No setup tasks required. Refactoring happens in existing files.)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

*(No foundational blocking tasks required for this internal refactoring.)*

---

## Phase 3: User Story 1 - Secure Post-Quantum Key Derivation (Priority: P1) 🎯 MVP

**Goal**: Replace legacy BIP-32/85 derivation with HDK `DeriveSalt` for the PQ branch.

**Independent Test**: `getPqChildSeed` must return a deterministic 64-byte result based on HDK `DeriveSalt` and HMAC-SHA512.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T001 [US1] Add KAT (Known Answer Test) and determinism test for `getPqChildSeed` using `DeriveSalt` and `HMAC-SHA512` expansion in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProviderTest.kt`

### Implementation for User Story 1

- [x] T002 [US1] Inject `HdkManager` into `WalletMasterSeedProvider` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt`
- [x] T003 [US1] Implement HDK `DeriveSalt` and `HMAC-SHA512` expansion logic for `getPqChildSeed()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt`

**Checkpoint**: At this point, the new PQ derivation logic should be fully functional and tested.

---

## Phase 4: User Story 2 - BIP-39 Mnemonic Continuity (Priority: P1)

**Goal**: Ensure existing mnemonic logic remains unchanged.

**Independent Test**: Verify that importing an existing BIP-39 mnemonic still produces the same master seed.

### Tests for User Story 2

- [x] T004 [US2] Verify or add tests to ensure the master seed generation from BIP-39 mnemonic phrase remains unaffected in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProviderTest.kt`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 5: User Story 3 - Full BIP-32 Removal (Priority: P2)

**Goal**: Remove all legacy BIP-32/85 logic and constants from the codebase.

**Independent Test**: Codebase search reveals zero functional occurrences of "BIP-32", "ckdHard", and related constants.

### Implementation for User Story 3

- [x] T005 [US3] Remove `ckdHard()` and all legacy BIP-32/85 constants from `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt` (Depends on T003)
- [x] T006 [P] [US3] Update KDoc references to remove BIP-32 mentions in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt`
- [x] T007 [P] [US3] Update KDoc derivation table to remove BIP-32 mentions in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt`

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and governance alignment.

- [x] T008 [P] Update Project Constitution (Section II) in `.specify/memory/constitution.md` to reflect the removal of the BIP-32 exception and adoption of HDK DeriveSalt
- [x] T009 [P] Annotate historical documentation (CHANGELOG.md, BRD) with notes about BIP-32 supersession by HDK
- [x] T010 Run local CI pipeline (`tools/local-ci.ps1`) to validate all tests and static analysis (Ktlint/Detekt)

---

## Dependencies & Execution Order

### Phase Dependencies

- **User Story 1 (P1)**: Can start immediately.
- **User Story 2 (P1)**: Can run parallel to US1 (testing tasks only).
- **User Story 3 (P2)**: T005 depends on the completion of US1 (T003) because it removes the old code that US1 replaces. T006 and T007 can start immediately.
- **Polish (Final Phase)**: Depends on all user stories being complete.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD approach).
- Story complete before moving to next priority.

### Parallel Opportunities

- T006, T007, and T008 can be executed in parallel at any time as they are documentation/comment updates in separate files.
- T001 and T004 can be executed in parallel (both update the same test file, but distinct test cases).

## Implementation Strategy

### MVP First (User Story 1 & 2)

1. Complete User Story 1 (New Derivation).
2. Complete User Story 2 (Verify BIP-39 continuity).
3. **STOP and VALIDATE**: Test PQ derivation independently.

### Incremental Delivery

1. Implement US1 and US2 (MVP).
2. Clean up technical debt by implementing US3.
3. Finalize documentation and run CI in the Polish phase.
