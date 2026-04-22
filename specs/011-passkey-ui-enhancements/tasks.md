# Tasks: Passkey UI Enhancements

**Input**: Design documents from `specs/011-passkey-ui-enhancements/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Tests**: TDD approach is MANDATORY per project constitution. Write tests FIRST.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure verification

- [ ] T001 Verify project compiles and Local CI passes via `tools/local-ci.ps1`
- [ ] T002 [P] Verify Atkinson Hyperlegible font is available in `commonMain` resources

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure for state management and "Undo" logic

- [ ] T003 Update `CredentialManagementState` to include `pendingDeleteIds: Set<String>` in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt`
- [ ] T004 Add `removalEvents: SharedFlow<PasskeyCredential>` to `CredentialManagementViewModel` in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt`
- [ ] T005 [P] Add `PendingDelete`, `UndoDelete`, and `CommitDelete` intents to `CredentialManagementIntent` in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt`

**Checkpoint**: Foundation ready - state management for undo logic is in place.

---

## Phase 3: User Story 1 - Swipe to Delete Passkey (Priority: P1) 🎯 MVP

**Goal**: Implement swipe-to-delete gesture with a long-duration undo snackbar.

**Independent Test**: Swipe a passkey, verify it disappears immediately, and can be restored via the "UNDO" button on the snackbar.

### Tests for User Story 1
- [ ] T006 [P] [US1] Create unit test for `pendingRemove` and `undoRemove` state transitions in `feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModelTest.kt`
- [ ] T007 [P] [US1] Create unit test for `commitRemove` triggering repository deletion in `feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModelTest.kt`

### Implementation for User Story 1
- [ ] T008 [US1] Implement `pendingRemove`, `undoRemove`, and `commitRemove` logic in `CredentialManagementViewModel.kt`
- [ ] T009 [US1] Update `observeCredentials` in `CredentialManagementViewModel.kt` to filter out items in `pendingDeleteIds`
- [ ] T010 [US1] Wrap `CredentialItem` with `SwipeToDismissBox` in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt`
- [ ] T011 [US1] Implement `SnackbarHost` and handle `removalEvents` in `CredentialListScreen.kt` using `SnackbarDuration.Long`
- [ ] T012 [US1] Connect swipe-to-dismiss actions to `PendingDelete` and snackbar results to `UndoDelete`/`CommitDelete`
- [ ] T012a [US1] Implement error handling in `commitRemove` to restore item if deletion fails and show error message

**Checkpoint**: User Story 1 is fully functional and testable independently.

---

## Phase 4: User Story 2 - Simplified Passkey Details (Priority: P1)

**Goal**: Remove "Custom Label / Note" field from the details screen.

**Independent Test**: Open passkey details and verify the label input field is no longer present.

### Implementation for User Story 2
- [ ] T013 [US2] Remove `OutlinedTextField` for "Custom Label / Note" from `CredentialDetailsScreen`, ensuring RP ID, Username, Created Date, and Last Used Date remain visible in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialComponents.kt`
- [ ] T014 [US2] Remove `updateCredentialLabelUseCase` and associated logic from `CredentialManagementViewModel.kt`
- [ ] T015 [P] [US2] Remove `UpdateCredentialLabel` intent and related state from `CredentialManagementViewModel.kt`

**Checkpoint**: Details screen is simplified and focused.

---

## Phase 5: User Story 3 - Visual Consistency (Priority: P2)

**Goal**: Enhance item presentation to match the Devices list.

**Independent Test**: Verify passkeys use the Fingerprint icon and Atkinson Hyperlegible font for technical IDs.

### Implementation for User Story 3
- [ ] T016 [P] [US3] Replace domain-letter circle with `Icons.Default.Fingerprint` in `CredentialItem` (`CredentialComponents.kt`)
- [ ] T017 [US3] Align spacing and typography of `CredentialItem` with `PairedDeviceItem` using Material 3 tokens
- [ ] T018 [US3] Ensure `rpId` and `userName` use Atkinson Hyperlegible font in `CredentialItem`

**Checkpoint**: All user stories are complete and visually consistent.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and cleanup

- [ ] T019 [P] Remove any deprecated [DEPRECATED] markers if logic is fully removed
- [ ] T020 Run `quickstart.md` manual verification steps
- [ ] T021 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: No dependencies.
- **Phase 2 (Foundational)**: BLOCKS Phase 3 (US1).
- **Phase 3 (US1)**: Priority P1.
- **Phase 4 (US2)**: Priority P1 (can run in parallel with US1 if needed).
- **Phase 5 (US3)**: Depends on US1 completion for layout refinement.
- **Phase 6 (Polish)**: Depends on all US completion.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 & 2.
2. Complete Phase 3 (US1).
3. **VALIDATE**: Run independent test for swipe-to-delete.

### Parallel Opportunities
- T006, T007 (Tests) can be written in parallel.
- US1 and US2 can be implemented in parallel by different developers.
- Iconography updates (T016) can be done independently.
