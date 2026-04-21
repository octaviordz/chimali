# Tasks: KMP Domain Module Migration

**Input**: Design documents from `/specs/008-domain-kmp-migration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and KMP build configuration

- [ ] T001 Configure core:domain/build.gradle.kts for KMP and KSP with target-specific processors

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [ ] T002 Create KMP source directory structure (commonMain, androidMain, iosMain) in core/domain/src/

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Multiplatform Domain Logic Support (Priority: P1) 🎯 MVP

**Goal**: Enable shared business logic definitions in commonMain

**Independent Test**: Verify that classes in commonMain are visible to platform source sets

### Implementation for User Story 1

- [ ] T003 [P] [US1] Create base UseCase interface in core/domain/src/commonMain/kotlin/com/chimali/core/domain/usecase/UseCase.kt
- [ ] T004 [P] [US1] Create base DomainModel placeholder in core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/DomainModel.kt

**Checkpoint**: User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Clean Architecture Preservation (Priority: P2)

**Goal**: Ensure domain purity and platform independence

**Independent Test**: Verify zero platform-specific imports in commonMain

### Implementation for User Story 2

- [ ] T005 [P] [US2] Clean up unused Android-only dependencies from core/domain/build.gradle.kts
- [ ] T006 [US2] Move any residual Android-specific code to androidMain (if found during migration)

**Checkpoint**: User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T007 [P] Verify Android compilation with ./gradlew :core:domain:assembleDebug
- [ ] T008 [P] Verify iOS compilation with ./gradlew :core:domain:iosArm64MainKlibrary
- [ ] T009 [P] Update module documentation and Run quickstart.md validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2)
- **User Story 2 (P2)**: Can start after Foundational (Phase 2)

### Parallel Opportunities

- T003 and T004 can run in parallel
- T005, T007, and T008 can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all models/interfaces for User Story 1 together:
Task: "Create base UseCase interface in core/domain/src/commonMain/kotlin/com/chimali/core/domain/usecase/UseCase.kt"
Task: "Create base DomainModel placeholder in core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/DomainModel.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Verify commonMain accessibility

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- No need to create code files in iosMain; structure is enough.
