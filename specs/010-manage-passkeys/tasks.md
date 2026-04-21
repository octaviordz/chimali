# Tasks: Manage Saved Passkeys

**Input**: Design documents from `/specs/010-manage-passkeys/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contract.md

**Tests**: Manual verification via `quickstart.md`.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1 (Browse), US2 (Search), US3 (Delete & Undo)

---

## Phase 1: Setup & Cleanup

**Purpose**: Align existing boilerplate with the new specification.

- [ ] T001 [P] Remove `DeleteAllCredentialsUseCase.kt` from `feature/fido2/src/main/kotlin/com/chimali/fido2/domain/usecase/`
- [ ] T002 [P] Remove `UpdateCredentialLabelUseCase.kt` from `feature/fido2/src/main/kotlin/com/chimali/fido2/domain/usecase/`
- [ ] T003 Remove DI bindings for deleted use cases in `feature/fido2/src/main/kotlin/com/chimali/fido2/di/Fido2Module.kt`
- [ ] T004 [P] Update `CredentialManagementIntent` and `CredentialManagementState` in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt` to remove out-of-scope intents (DeleteAll, UpdateLabel)

---

## Phase 2: Foundational

**Purpose**: Core infrastructure updates.

- [ ] T005 [P] Add `lastDeleted` property to `CredentialManagementState` for Undo support
- [ ] T006 [P] Add `ShowUndoSnackbar` effect to `CredentialManagementEffect`
- [ ] T007 Ensure `PasskeyCredentialRepository` implementation in `feature/fido2/src/main/kotlin/com/chimali/fido2/data/repository/PasskeyCredentialRepositoryImpl.kt` correctly handles `lastUsedAt` sorting

---

## Phase 3: User Story 1 - Browse and View (Priority: P1) 🎯 MVP

**Goal**: Display a list of saved passkeys sorted by last used/creation date.

**Independent Test**: Navigate to the screen and see the list sorted correctly.

### Implementation for User Story 1

- [ ] T008 [US1] Update `CredentialManagementViewModel` to fetch all credentials and sort them by `lastUsedAt` or `createdAt` descending
- [ ] T009 [US1] Refactor `CredentialListScreen.kt` UI to use Material 3 `ListItem` with RP Name, Username, and Date
- [ ] T010 [P] [US1] Implement RP icon placeholder logic in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialComponents.kt`

---

## Phase 4: User Story 2 - Search and Filter (Priority: P2)

**Goal**: Filter the passkey list in real-time.

**Independent Test**: Type in the search bar and verify list filtering.

### Implementation for User Story 2

- [ ] T011 [US2] Add `searchQuery` to `CredentialManagementState` and `UpdateSearchQuery` intent to `CredentialManagementViewModel`
- [ ] T012 [US2] Implement real-time filtering logic in `CredentialManagementViewModel` using `StateFlow.combine`
- [ ] T013 [US2] Add Material 3 `SearchBar` or `TextField` to the top of `CredentialListScreen.kt`

---

## Phase 5: User Story 3 - Delete & Undo (Priority: P1)

**Goal**: Securely delete a passkey with an option to undo.

**Independent Test**: Delete a key (auth required) and restore it using the snackbar.

### Implementation for User Story 3

- [ ] T014 [US3] Add `InitiateDelete` intent and `BiometricAuthRequired` state/effect to `CredentialManagementViewModel`
- [ ] T015 [US3] Integrate `BiometricPromptComponent` in `CredentialListScreen.kt` to trigger before deletion
- [ ] T016 [US3] Implement `UndoDelete` logic in `CredentialManagementViewModel` (restoring the `lastDeleted` item)
- [ ] T017 [US3] Add `SnackbarHost` to `CredentialListScreen.kt` and show "Undo" snackbar after successful deletion

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Add Kermit logging for Search, Delete, and Undo operations
- [ ] T019 [P] Verify SC-001 (Search < 3s) and SC-003 (Load < 300ms) targets
- [ ] T020 Final validation using `quickstart.md` manual steps

---

## Dependencies & Execution Order

1. **Phase 1 (Setup)**: Must complete first to clean up the existing boilerplate.
2. **Phase 2 (Foundational)**: Prerequisite for all User Stories.
3. **Phase 3 (US1)**: First functional increment (MVP).
4. **Phase 4 & 5**: Can proceed in parallel or sequentially. US3 has higher priority (P1) than US2 (P2).
5. **Phase 6 (Polish)**: Final verification.
