# Tasks: Remove AuthenticateCredentialUseCase Stub and Consolidate into GetAssertionUseCase

**Input**: Design documents from `/specs/051-remove-auth-credential-stub/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Paths assume KMP Android project structure: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/` and `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure. Since the spec is already set up and directories created, this phase is complete.

- [x] T001 Verify branch and spec directory setup

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core cleanup before any user story implementation can begin.

- [X] T002 Delete the unused use case test file `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCaseTest.kt`
- [X] T003 Delete the unused use case class `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCase.kt`

**Checkpoint**: Dead use case files removed.

---

## Phase 3: User Story 1 - Validate and Remove AuthenticateCredentialUseCase (Priority: P1) 🎯 MVP

**Goal**: Remove the stub methods and clear references in the repository and service layers.

**Independent Test**: Clean compile with references removed.

### Implementation for User Story 1

- [X] T004 [P] [US1] Remove `authenticateCredential` from repository interface in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/repository/Fido2Repository.kt`
- [X] T005 [P] [US1] Remove `authenticateCredential` from repository implementation in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2RepositoryImpl.kt`
- [X] T006 [P] [US1] Remove `authenticateWithCredential` from service interface in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/Fido2Service.kt`
- [X] T007 [US1] Remove `authenticateWithCredential` and the `AuthenticateCredentialUseCase` injection from `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2ServiceImpl.kt`

**Checkpoint**: AuthenticateCredentialUseCase stub is fully removed from all layers.

---

## Phase 4: User Story 2 - GetAssertionUseCase Is the Sole Authentication Interactor (Priority: P2)

**Goal**: Verify that GetAssertionUseCase is functional and there are no regression issues in the authentication ViewModel.

**Independent Test**: Clean compilation and test pass.

### Implementation for User Story 2

- [X] T008 [US2] Verify `AuthenticationPromptViewModel.kt` compile correctness and verify that `GetAssertionUseCase` handles the authentication ceremony in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/AuthenticationPromptViewModel.kt`

**Checkpoint**: Authentication prompt ViewModel verified.

---

## Phase 5: User Story 3 - RegistrationPromptViewModel Injects RegisterCredentialUseCase Directly (Priority: P3)

**Goal**: Clean up the registration ViewModel by injecting the use case directly.

**Independent Test**: Registration works exactly as before.

### Implementation for User Story 3

- [X] T009 [US3] Modify `RegistrationPromptViewModel.kt` to inject `RegisterCredentialUseCase` instead of `Fido2Service`, and update `performRegistration` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/RegistrationPromptViewModel.kt`

**Checkpoint**: Registration ViewModel refactored.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification and validation.

- [X] T010 Run build verification: `.\gradlew :feature:fido2:compileDebugKotlin`
- [X] T011 Run test verification: `.\gradlew :feature:fido2:testDebugUnitTest`
- [X] T012 Run lint checks: `.\gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Complete.
- **Foundational (Phase 2)**: BLOCKS Phase 3.
- **User Stories (Phase 3+)**: Must follow Foundational phase completion.
- **Polish (Phase 6)**: Depends on all user stories completion.

---

## Parallel Example: User Story 1

```bash
# Deleting files and modifying interfaces in parallel:
Task: "Remove authenticateCredential from repository interface"
Task: "Remove authenticateWithCredential from service interface"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Delete dead use case files.
2. Remove stub references from repository and service.
3. Validate compiles cleanly.
