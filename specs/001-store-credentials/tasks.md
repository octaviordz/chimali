# Tasks: Secure Credentials Vault (FR-VAULT-010)

**Input**: Design documents from `/specs/001-store-credentials/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Unit tests are included as mandated by the project Constitution (100% core business logic coverage).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure for the vault feature.

- [x] T001 Initialize `feature/vault` module in `feature/vault/build.gradle.kts` and register in `settings.gradle.kts`
- [x] T002 Configure Rust cargo project for Loro.dev and UniFFI in `core/crdt/rust/Cargo.toml`
- [x] T003 Setup UniFFI generation tasks for Kotlin bindings in `core/crdt/build.gradle.kts`
- [x] T004 [P] Create `feature/vault/src/main/java/com/chimali/feature/vault/api`, `internal`, and `ui` packages

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T005 Setup SQLDelight schema `Vault.sq` in `core/database/src/main/sqldelight/` for `VaultEntry`, `Identity`, and `IdentityBackup`
- [x] T006 Implement UniFFI Rust bridge for Loro.dev CRDT document parsing in `core/crdt/rust/src/lib.rs`
- [x] T007 Define `VaultService` and `VaultItem` contracts in `feature/vault/src/api/VaultService.kt`
- [x] T008 Implement `VaultRepository` inside `feature/vault/src/internal/VaultRepositoryImpl.kt` utilizing SQLCipher and Android Keystore
- [x] T009 Define MVI `VaultState` and `VaultIntent` in `feature/vault/src/api/VaultMvi.kt`

**Checkpoint**: Foundation ready - SQL database and CRDT bridge are functional.

---

## Phase 3: User Story 1 - Add and View Secure Passwords (Priority: P1) 🎯 MVP

**Goal**: As a user, I want to securely add and view passwords so that I don't have to remember them.

**Independent Test**: Can be fully tested by creating a mock vault, adding a new password entry, verifying it encrypts cleanly, and retrieving it for viewing, delivering the primary store-and-retrieve capability.

### Tests for User Story 1

- [x] T010 [P] [US1] Unit test for Password encryption/decryption in `feature/vault/src/test/java/.../PasswordCryptoTest.kt`
- [x] T011 [P] [US1] Integration test for VaultService saving and loading Passwords in `feature/vault/src/androidTest/java/.../VaultServicePasswordTest.kt`

### Implementation for User Story 1

- [x] T012 [P] [US1] Create `PasswordPayload` parser and serializer in `feature/vault/src/internal/payload/PasswordPayload.kt`
- [x] T013 [P] [US1] Create `CustomField` parser and serializer in `feature/vault/src/internal/payload/CustomField.kt`
- [x] T014 [US1] Implement password-specific CRUD item handling (Create, Read, Update, Delete) in `feature/vault/src/internal/VaultViewModel.kt`
- [x] T015 [P] [US1] Create Compose UI for Password Entry form (with dynamic Custom Fields rendering) in `feature/vault/src/ui/PasswordEntryScreen.kt`
- [x] T016 [P] [US1] Create Compose UI for Password Details view (with Edit/Delete actions) in `feature/vault/src/ui/PasswordDetailScreen.kt`
- [x] T017 [P] [US1] Create base Compose UI for Vault List (`VaultListScreen`) to display saved passwords in `feature/vault/src/ui/VaultListScreen.kt`
- [x] T018 [US1] Implement Clipboard clear capability (FR-006) in `feature/vault/src/internal/ClipboardManagerWrapper.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Add and View Credit Cards (Priority: P1)

**Goal**: As a user, I want to securely store my credit card details (number, CVV, expiration) so that they are handy when needed.

**Independent Test**: Can be tested by storing card data formats, validating it saves successfully to encrypted storage, and verifying the data is correct upon read.

### Tests for User Story 2

- [x] T019 [P] [US2] Unit test for Credit Card payload serialization in `feature/vault/src/test/java/.../CreditCardPayloadTest.kt`

### Implementation for User Story 2

- [x] T020 [P] [US2] Create `CreditCardPayload` entity in `feature/vault/src/internal/payload/CreditCardPayload.kt`
- [x] T021 [US2] Update `VaultViewModel.kt` to handle Credit Card CRUD Intents
- [x] T022 [P] [US2] Create Compose UI for Credit Card Entry form (with dynamic Custom Fields rendering) in `feature/vault/src/ui/CreditCardEntryScreen.kt`
- [x] T023 [P] [US2] Create Compose UI for Credit Card Details view (with Edit/Delete actions) in `feature/vault/src/ui/CreditCardDetailScreen.kt`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Add and View Secure Notes (Priority: P2)

**Goal**: As a user, I want to create freeform secure notes for data that doesn't fit standard forms.

**Independent Test**: Can be tested by inputting arbitrary text, saving it, and verifying it is retrieved accurately.

### Tests for User Story 3

- [x] T024 [P] [US3] Unit test for Secure Note payload in `feature/vault/src/test/java/.../SecureNotePayloadTest.kt`

### Implementation for User Story 3

- [x] T025 [P] [US3] Create `SecureNotePayload` entity in `feature/vault/src/internal/payload/SecureNotePayload.kt`
- [x] T026 [US3] Update `VaultViewModel.kt` to handle Secure Note CRUD Intents
- [x] T027 [P] [US3] Create Compose UI for Secure Note Entry and Detail views (with Edit/Delete actions) in `feature/vault/src/ui/SecureNoteScreen.kt`

**Checkpoint**: All 3 core credential types are now supported.

---

## Phase 6: User Story 4 - Organize Items with Folders/Labels (Priority: P2)

**Goal**: As a user, I want to organize my saved items into logical groups so I can find them easily later.

**Independent Test**: Can be tested by creating a folder label, assigning it to multiple items, and filtering the list by that label.

### Tests for User Story 4

- [x] T028 [P] [US4] Integration test for VaultService filtering by Label in `feature/vault/src/androidTest/java/.../VaultLabelTest.kt`

### Implementation for User Story 4

- [x] T029 [P] [US4] Add `Label` and `VaultEntryLabel` tables to `core/database/src/main/sqldelight/Vault.sq`
- [x] T030 [US4] Implement `getItems(label_id: UUID?)` inside `VaultRepositoryImpl.kt`
- [x] T031 [US4] Update `VaultViewModel.kt` to handle `LoadItems(filter: Label)` Intent
- [x] T032 [P] [US4] Create Compose UI for Label Management in `feature/vault/src/ui/LabelManagerScreen.kt`
- [x] T033 [US4] Update Vault List UI to support filtering by Label in `feature/vault/src/ui/VaultListScreen.kt`

**Checkpoint**: All user stories should now be independently functional.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T034 Verify zero plain-text trace policies (CharArray `.fill()`) across all view models and parsers
- [x] T035 Optimize Compose Recomposition in Vault List to sustain 60 FPS for large vaults
- [x] T036 Research Android Keystore PQC (ML-KEM) support for future quantum-resistant cryptographic migration
- [x] T037 Create documentation and module README in `feature/vault/README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User story P1 (Passwords) can proceed sequentially, followed by P1 (Cards), then P2.
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Models within a story marked [P] can run in parallel (e.g. `PasswordPayload` and `CustomField`)
- Different User Stories (P1 Passwords and P1 Cards) can be worked on in parallel by different team members once Foundational is done.

### Implementation Strategy

#### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently to deliver the primary value.
