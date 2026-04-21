# Tasks: Fix Dev Tools QR scan button

**Input**: Design documents from `/specs/009-fix-devtools-qr-scan/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are not explicitly requested in the specification; however, manual verification via `quickstart.md` is mandatory.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Mobile**: `feature/fido2/src/main/kotlin/com/chimali/fido2/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 [P] Review `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt` for regression context
- [x] T002 [P] Review `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/MnemonicQrScanner.kt` for resource leak patterns

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure and resource management fixes identified in research

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] Refactor `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/MnemonicQrScanner.kt` to reuse a single `BarcodeScanner` instance instead of per-frame allocation
- [x] T004 [P] Update `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/MnemonicQrScanner.kt` to ensure `cameraProvider.unbindAll()` is called during lifecycle transitions or disposal

**Checkpoint**: Foundation ready - resource leaks and lifecycle issues resolved.

---

## Phase 3: User Story 1 - Import Mnemonic via QR Scan (Priority: P1) 🎯 MVP

**Goal**: Restore the ability to scan and import a 24-word BIP39 mnemonic via QR code.

**Independent Test**: Open Dev Tools -> Tap Scan -> Scan valid QR -> Mnemonic imported successfully.

### Implementation for User Story 1

- [x] T005 [P] [US1] Update `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/MnemonicQrScanner.kt` to handle non-critical validation errors without closing the scanner
- [x] T006 [US1] Fix `showScanner` state management in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt` to ensure UI consistency during scanner launch

**Checkpoint**: User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - Permission Recovery (Priority: P2)

**Goal**: Handle denied camera permissions gracefully and provide user recovery paths.

**Independent Test**: Deny permission -> Tap Scan -> Rationale shown -> Grant from rationale or settings.

### Implementation for User Story 2

- [x] T007 [US2] Implement `shouldShowRequestPermissionRationale` check and dialog in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`
- [x] T008 [US2] Add snackbar with "Settings" action for permanent permission denial in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`

**Checkpoint**: User Story 2 handles all permission edge cases.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T009 [P] Remove redundant permission checks or logging from `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`
- [ ] T010 [P] Final validation of all scenarios defined in `specs/009-fix-devtools-qr-scan/quickstart.md`
- [ ] T011 [P] Profile scanner launch (<500ms) and recognition (<1s) to verify success criteria SC-002 and SC-003

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion.
- **Polish (Final Phase)**: Depends on all user stories being complete.

### Parallel Opportunities

- T001, T002 can run in parallel.
- T003, T004 can run in parallel once Setup is done.
- T009, T010 can run in parallel during the Polish phase.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2.
2. Complete Phase 3 (User Story 1).
3. **STOP and VALIDATE**: Verify QR scanning works on a physical device or simulator.
