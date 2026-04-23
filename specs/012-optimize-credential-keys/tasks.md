---
description: "Task list for Optimize Credential Keys feature implementation"
---

# Tasks: Optimize Credential Keys

**Input**: Design documents from `/specs/012-optimize-credential-keys/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Test tasks are included as required by the Chimali Constitution (TDD methodology).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and dependency validation

- [x] T001 [P] Validate `kmpworkmanager` dependency is present in `feature/fido2/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Update `Fido2Database.sq` to include `getPagedCredentials` and `getPagedCredentialsByRpId` queries in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq`
- [x] T003 Update `CredentialRepository` interface with `getPagedCredentials` and `getPagedCredentialsByRpId` methods in `feature/fido2/src/main/kotlin/com/chimali/fido2/domain/repository/CredentialRepository.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Instant Passkey List Loading (Priority: P1) 🎯 MVP

**Goal**: Load the passkeys screen instantly by decoding stored public keys and handling corrupted keys via a background worker.

**Independent Test**: Create multiple passkeys, navigate to 'Passkeys', and verify immediate rendering with zero "key pair generated" logs. Manually corrupt a key in DB and verify it is hidden and a worker is enqueued.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T004 [P] [US1] Unit test for decoding keys in `feature/fido2/src/test/kotlin/com/chimali/fido2/data/crypto/PublicKeyDecoderTest.kt`
- [x] T005 [P] [US1] Unit test for worker enqueuing in `feature/fido2/src/test/kotlin/com/chimali/fido2/data/worker/CorruptedKeyRepairWorkerTest.kt`

### Implementation for User Story 1

- [x] T006 [P] [US1] Create `PublicKeyDecoder.kt` utility in `feature/fido2/src/main/kotlin/com/chimali/fido2/data/crypto/PublicKeyDecoder.kt`
- [x] T007 [P] [US1] Create `CorruptedKeyRepairWorker.kt` interface and implementation in `feature/fido2/src/main/kotlin/com/chimali/fido2/data/worker/CorruptedKeyRepairWorker.kt`
- [x] T008 [US1] Update `EntityMappers.kt` to use `PublicKeyDecoder` in `feature/fido2/src/main/kotlin/com/chimali/fido2/data/mapper/EntityMappers.kt`
- [x] T009 [US1] Update `CredentialRepositoryImpl.kt` to catch decoder exceptions, enqueue worker, and return valid keys in `feature/fido2/src/main/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Lazy Loading / Infinite Scroll (Priority: P2)

**Goal**: Implement pagination to load only the visible items plus one next page to minimize memory usage and prevent UI freezes.

**Independent Test**: Load 100+ credentials, open the list, and verify only the first page (e.g., 20) is fetched. Scroll down and verify seamless fetching of subsequent pages.

### Tests for User Story 2 ⚠️

- [x] T010 [P] [US2] Unit test for pagination logic in `feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModelTest.kt`

### Implementation for User Story 2

- [x] T011 [US2] Update `CredentialManagementViewModel.kt` to manage pagination offset, state, and `loadNextPage` method in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt`
- [x] T012 [US2] Update `CredentialListScreen.kt` to trigger `loadNextPage` on scroll using `LaunchedEffect` in `feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and system stability.

- [x] T013 [P] Clean up obsolete list retrieval logic from `GetAllCredentialsUseCase.kt` in `feature/fido2/src/main/kotlin/com/chimali/fido2/domain/usecase/GetAllCredentialsUseCase.kt`
- [x] T014 [P] Write Macrobenchmark test for UI scroll performance in `feature/fido2/src/macrobenchmark/kotlin/com/chimali/fido2/performance/CredentialListScrollBenchmark.kt`
- [x] T015 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can proceed sequentially (US1 → US2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Depends on US1 completing the repository updates.

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Decoders/Workers before Mappers
- Mappers before Repository implementation
- ViewModel before UI implementation
- Story complete before moving to next priority

### Parallel Opportunities

- Unit tests across US1 and US2 can be stubbed out simultaneously.
- `PublicKeyDecoder` and `CorruptedKeyRepairWorker` can be implemented in parallel by different developers.

---

## Parallel Example: User Story 1

```bash
# Launch implementation of utility and worker simultaneously:
Task: "Create PublicKeyDecoder.kt utility"
Task: "Create CorruptedKeyRepairWorker.kt interface and implementation"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (Instantly load passkeys, avoid crypto derivations)
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 (Lazy Loading) → Test independently → Deploy/Demo
4. Each story adds value without breaking previous stories

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing (TDD enforcement)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
