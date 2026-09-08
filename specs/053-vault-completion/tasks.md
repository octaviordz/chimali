# Tasks: Vault Feature Completion

**Input**: Design documents from `specs/053-vault-completion/`  
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

## Format: `[ID] [P?] [Story] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g. `[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`, `[US6]`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify and prepare feature infrastructure and dependencies

- [x] T001 Verify dependencies and KMP build for `:feature:vault` in `feature/vault/build.gradle.kts`
- [x] T002 Verify Koin dependency injection registrations in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core cryptographic serialization services and base navigation prerequisites that all user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] Create `VaultCryptoService` contract in `feature/vault/src/main/java/com/chimali/feature/vault/internal/crypto/VaultCryptoService.kt`
- [x] T004 [P] Unit tests for `VaultCryptoService` in `feature/vault/src/test/java/com/chimali/feature/vault/internal/crypto/VaultCryptoServiceTest.kt`
- [x] T005 Implement `VaultCryptoServiceImpl` using `AesEncryptionManager` and `EventStoreKeyProvider` with memory zeroing in `feature/vault/src/main/java/com/chimali/feature/vault/internal/crypto/VaultCryptoServiceImpl.kt`
- [x] T006 Register `VaultCryptoService` in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`
- [x] T007 Define `VaultDestinations` constants in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultDestinations.kt`
- [x] T008 Implement `VaultNavGraph` with `NavHost` connecting list, entry, and detail routes in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T009 Replace stubbed `VaultListScreen` in `app/src/main/kotlin/com/chimali/navigation/AppNavGraph.kt` with `VaultNavGraph`

**Checkpoint**: Core crypto engine and navigation shell are ready; user story flows can now be integrated.

---

## Phase 3: User Story 1 - Add a New Password Entry (Priority: P1) 🎯 MVP

**Goal**: Users can tap (+) on VaultListScreen, choose Password, fill in details, save, and see the encrypted item saved and displayed in the vault list.

**Independent Test**: Navigate from Vault list -> Add Password form -> Save -> Verify item appears in list.

### Tests for User Story 1
- [x] T010 [P] [US1] Unit test for saving password payload via ViewModel in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelPasswordSaveTest.kt`

### Implementation for User Story 1
- [x] T011 [US1] Update `VaultIntent` in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt` to support `SavePassword(payload: PasswordPayload, labelIds: List<UUID>)`
- [x] T012 [US1] Implement `SavePassword` intent handling in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt` using `VaultCryptoService` and `VaultService`
- [x] T013 [US1] Add Add-Item-Type selector (bottom sheet / dialog) in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt` triggered by FAB
- [x] T014 [US1] Connect `PasswordEntryScreen` onSave to `VaultViewModel.processIntent(VaultIntent.SavePassword(...))` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`

**Checkpoint**: User Story 1 complete! Password creation delivers the core MVP.

---

## Phase 4: User Story 2 - View and Copy a Stored Password (Priority: P1)

**Goal**: Users can tap a password entry to view decrypted details, reveal the password with high-legibility font, and copy it to clipboard with 60s auto-clear.

**Independent Test**: Tap password item in list -> Open detail -> Toggle password visibility -> Copy to clipboard -> Verify clipboard cleared after 60s.

### Tests for User Story 2
- [x] T015 [P] [US2] Unit test for password decryption in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelPasswordDecryptTest.kt`

### Implementation for User Story 2
- [x] T016 [US2] Update `VaultIntent.DecryptItem` and `VaultState` in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt` to expose decrypted `PasswordPayload`
- [x] T017 [US2] Implement item decryption in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt` via `VaultCryptoService.decryptPassword`
- [x] T018 [US2] Wire list item click to detail route and connect `PasswordDetailScreen` to decrypted state and clipboard copy in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T019 [US2] Ensure `PasswordDetailScreen` calls `payload.clearMemory()` when back navigation occurs in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`

**Checkpoint**: User Stories 1 and 2 deliver end-to-end password management.

---

## Phase 5: User Story 3 - Edit and Delete a Vault Entry (Priority: P1)

**Goal**: Users can edit or delete an existing vault entry from its detail screen.

**Independent Test**: Open detail view -> Edit fields -> Save -> Verify updated values. Open detail view -> Tap Delete -> Confirm -> Verify entry removed from list.

### Tests for User Story 3
- [x] T020 [P] [US3] Unit test for deleting vault entries in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelDeleteTest.kt`

### Implementation for User Story 3
- [x] T021 [US3] Implement entry deletion confirmation dialog and callback in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`
- [x] T022 [US3] Connect onDelete callback to `VaultViewModel.processIntent(VaultIntent.DeleteItem(id))` and navigate back in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T023 [US3] Support edit mode on `PasswordEntryScreen` pre-populated with existing payload in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`

**Checkpoint**: Full lifecycle (Create, Read, Update, Delete) functional for passwords.

---

## Phase 6: User Story 4 - Add and View Credit Cards (Priority: P1)

**Goal**: Users can add, view, and delete encrypted Credit Card entries.

**Independent Test**: Add Credit Card -> Verify in list -> Open detail -> Reveal card number/CVV -> Delete card.

### Tests for User Story 4
- [x] T024 [P] [US4] Unit test for credit card save and decrypt in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelCreditCardTest.kt`

### Implementation for User Story 4
- [x] T025 [US4] Add `SaveCreditCard` intent and `selectedCreditCardPayload` state in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt`
- [x] T026 [US4] Implement credit card encryption and decryption in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`
- [x] T027 [US4] Wire `CreditCardEntryScreen` and `CreditCardDetailScreen` into `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`

**Checkpoint**: Credit cards fully functional alongside passwords.

---

## Phase 7: User Story 5 - Add and View Secure Notes (Priority: P2)

**Goal**: Users can add, view, and delete encrypted Secure Notes.

**Independent Test**: Add Secure Note -> Verify in list -> Open detail -> Read note text -> Delete note.

### Tests for User Story 5
- [x] T028 [P] [US5] Unit test for secure note save and decrypt in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelSecureNoteTest.kt`

### Implementation for User Story 5
- [x] T029 [US5] Add `SaveSecureNote` intent and `selectedSecureNotePayload` state in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt`
- [x] T030 [US5] Implement secure note encryption and decryption in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`
- [x] T031 [US5] Wire `SecureNoteEntryScreen` and `SecureNoteDetailScreen` into `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`

**Checkpoint**: All three credential types (Passwords, Cards, Notes) are operational.

---

## Phase 8: User Story 6 - Filter Vault Items by Label (Priority: P2)

**Goal**: Users can manage labels and filter items by label in the vault list.

**Independent Test**: Open Label Manager -> Create label -> Filter list by label -> Confirm only matching items display.

### Implementation for User Story 6
- [x] T032 [P] [US6] Add label CRUD queries and repository methods in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt`
- [x] T033 [US6] Wire `onManageLabelsClick` in `VaultListScreen` to navigate to `LabelManagerScreen` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T034 [US6] Wire label filter tabs in `VaultListScreen` to `VaultViewModel.processIntent(VaultIntent.LoadItems(labelId))` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt`

**Checkpoint**: Organization and filtering operational.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Quality checks, formatting, and end-to-end verification

- [x] T035 [P] Run unit tests across `:feature:vault` via `./gradlew :feature:vault:testDebugUnitTest`
- [x] T036 [P] Run static analysis and formatting checks via `./gradlew detekt ktlintCheck`
- [x] T037 Execute quickstart verification scenarios from `specs/053-vault-completion/quickstart.md`
- [x] T038 Verify zero-memory trace policies (`clearMemory()` on dispose) across all detail and entry screens

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Phase 1 - **BLOCKS all user stories**
- **User Stories (Phase 3-8)**: Depend on Phase 2 completion
  - US1 (Add Password) -> US2 (View/Copy Password) -> US3 (Edit/Delete) -> US4 (Credit Cards) -> US5 (Secure Notes) -> US6 (Labels)
- **Polish (Phase 9)**: Depends on all user stories complete

### Parallel Opportunities
- T003 & T004 can be created in parallel (Contract & Tests)
- T010, T015, T020, T024, T028 can be written concurrently
- Phase 4 (US2) and Phase 6 (US4) models can proceed in parallel once Phase 2 is complete

---

## Implementation Strategy (MVP First)

1. **Phase 1 & 2**: Setup & Foundational (crypto service + navigation graph)
2. **Phase 3**: User Story 1 (Add Password) -> **Validate MVP**
3. **Phase 4 & 5**: View, copy, edit, and delete Passwords
4. **Phase 6 & 7**: Add Cards and Notes
5. **Phase 8**: Label filtering
6. **Phase 9**: Polish, static analysis, and verification
