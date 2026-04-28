---

description: "Task list for ViewModel Forwarding Cleanup feature implementation"
---

# Tasks: ViewModel Forwarding Cleanup

**Input**: Design documents from `/specs/025-viewmodel-forwarding-cleanup/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are NOT included - not explicitly requested in feature specification

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Android Mobile**: `feature/`, `core/`, `config/`, `tools/`
- Paths reflect the actual project structure from plan.md

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Verify branch is ready for development
- [ ] T002 [P] Run baseline static analysis to document current state
- [ ] T003 [P] Create backup of target file before modifications

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Verify Detekt configuration includes ViewModelForwarding rule in config/detekt/detekt.yml
- [ ] T005 Run existing test suite to establish baseline in feature/fido2/
- [ ] T006 Document current swipe-to-dismiss behavior for validation reference

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Code Quality Enforcement (Priority: P1) 🎯 MVP

**Goal**: Remove @Suppress("ViewModelForwarding") annotations and enforce proper Compose patterns

**Independent Test**: Run static analysis tools (detekt/compose lint) and verify no @Suppress("ViewModelForwarding") annotations exist and all Compose code properly forwards ViewModels

### Implementation for User Story 1

- [ ] T007 [US1] Analyze current CredentialSwipeToDismissBox signature in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt
- [ ] T008 [US1] Refactor CredentialSwipeToDismissBox to accept callback instead of ViewModel parameter in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt
- [ ] T009 [US1] Update CredentialSwipeToDismissBox implementation to use onPendingDelete callback in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt
- [ ] T010 [US1] Update parent CredentialListScreen to pass callback instead of ViewModel in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt
- [ ] T011 [US1] Remove @Suppress("ViewModelForwarding") annotation in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt
- [ ] T012 [US1] Remove TODO comment about state hoisting in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Maintain Code Functionality (Priority: P1)

**Goal**: Ensure code quality improvements do not change any existing functionality

**Independent Test**: Run the existing test suite and verify that all tests pass with identical results before and after the cleanup

### Implementation for User Story 2

- [ ] T013 [US2] Run existing test suite to verify no behavioral changes in feature/fido2/
- [ ] T014 [US2] Perform manual testing of swipe-to-dismiss functionality in CredentialListScreen
- [ ] T015 [US2] Verify visual feedback and animations remain identical
- [ ] T016 [US2] Confirm biometric prompt integration still works correctly
- [ ] T017 [US2] Validate user interaction patterns are unchanged
- [ ] T018 [US2] Run performance tests to ensure no degradation

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T019 [P] Run Detekt static analysis to verify zero ViewModelForwarding violations
- [ ] T020 [P] Run Ktlint formatting to ensure code style consistency
- [ ] T021 [P] Execute Local CI pipeline (tools/local-ci.ps1) to ensure all checks pass
- [ ] T022 [P] Perform final code review of refactored component
- [ ] T023 [P] Perform code review to validate consistent ViewModel forwarding patterns across all Compose components (SC-004)
- [ ] T024 [P] Research and document legitimate @Suppress("ViewModelForwarding") use cases in research.md
- [ ] T025 [P] Create strategy for handling third-party library ViewModel forwarding violations in research.md
- [ ] T026 [P] Document decision framework for when suppression might be acceptable in quickstart.md
- [ ] T027 Update any relevant documentation if needed
- [ ] T028 Clean up any temporary files or backups created during setup

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after User Story 1 completion - Validates the refactoring work

### Within Each User Story

- Component analysis before refactoring
- Signature changes before implementation changes
- Implementation before validation
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- All Polish tasks marked [P] can run in parallel after user stories complete
- No parallel opportunities within user stories due to single-file scope

---

## Metrics:

- Total Requirements: 24 tasks across 5 phases
- Total Tasks: 28 tasks (increased from 24 with edge case handling)
- Coverage %: 100% (all requirements now have explicit task coverage)
- Ambiguity Count: 0 (resolved with clarifications)
- Duplication Count: 0
- Critical Issues Count: 0

---

## Parallel Example: User Story 1

```bash
# Phase 1 Setup tasks can run in parallel:
Task: "Verify branch is ready for development"
Task: "Run baseline static analysis to document current state"
Task: "Create backup of target file before modifications"

# Phase 2 Foundational tasks can run in parallel:
Task: "Verify Detekt configuration includes ViewModelForwarding rule in config/detekt/detekt.yml"
Task: "Run existing test suite to establish baseline in feature/fido2/"
Task: "Document current swipe-to-dismiss behavior for validation reference"

# Phase 5 Polish tasks can run in parallel:
Task: "Run Detekt static analysis to verify zero ViewModelForwarding violations"
Task: "Run Ktlint formatting to ensure code style consistency"
Task: "Execute Local CI pipeline (tools/local-ci.ps1) to ensure all checks pass"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Run static analysis to verify ViewModelForwarding compliance
5. Validate functionality with User Story 2

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Validate with static analysis
3. Add User Story 2 → Test independently → Validate functionality
4. Complete Polish phase → Final validation and cleanup
5. Each phase adds value without breaking previous work

### Sequential Strategy (Recommended for this feature)

Due to the single-file scope and sequential nature of the refactoring:

1. Complete Setup + Foundational together
2. Complete User Story 1 (refactoring)
3. Complete User Story 2 (validation)
4. Complete Polish phase
5. Final review and commit

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- This is a single-file refactoring - tasks are sequential within User Story 1
- User Story 2 serves as validation of User Story 1 work
- All tasks must maintain exact functional behavior
- Zero tolerance for behavioral changes - this is a code quality enhancement only
- Stop at any checkpoint to validate story independently
- Avoid: any logic changes, UI behavior changes, performance degradation
