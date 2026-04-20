# Tasks: KMP Common Module Migration

**Input**: Design documents from `/specs/007-kmp-common-migration/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create directory structure for KMP in `core/common/src/` (`commonMain`, `androidMain`, `iosMain`, `commonTest`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Refactor `core/common/build.gradle.kts` to apply `kotlin("multiplatform")` and configure source sets (`androidTarget`, `ios`)
- [X] T003 Move common dependencies (coroutines-core, kermit, koin-core) to `commonMain` in `core/common/build.gradle.kts`
- [X] T004 Move Android-specific dependencies (core-ktx, coroutines-android, koin-android) to `androidMain` in `core/common/build.gradle.kts`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Shared Utilities Access (Priority: P1) 🎯 MVP

**Goal**: Access utility interfaces and infrastructure from `commonMain` of any module.

**Independent Test**: Create a dummy class in another module's `commonMain` that imports and uses `ClipboardManagerService` or `Fido2EventBus`.

### Implementation for User Story 1

- [X] T005 [P] [US1] Move `ClipboardManagerService.kt` interface to `core/common/src/commonMain/kotlin/com/chimali/core/clipboard/ClipboardManagerService.kt`
- [X] T006 [P] [US1] Move `Fido2EventBus.kt` and `Fido2Event.kt` to `core/common/src/commonMain/kotlin/com/chimali/core/events/`
- [X] T007 [US1] Move `AndroidClipboardManagerService.kt` to `androidMain` and verify that the 60s clipboard clearing logic remains functional (Constitution IV)
- [X] T008 [P] [US1] Create `IosClipboardManagerService.kt` placeholder in `core/common/src/iosMain/kotlin/com/chimali/core/clipboard/IosClipboardManagerService.kt`
- [X] T009 [US1] Update `ClipboardModule.kt` to handle platform-specific Koin module inclusion and move to `core/common/src/commonMain/kotlin/com/chimali/core/clipboard/di/ClipboardModule.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - Cross-Platform DI Configuration (Priority: P1)

**Goal**: Define and use DI qualifiers in a platform-agnostic way.

**Independent Test**: Verify `DispatcherQualifiers` can be used in `commonMain` to inject dispatchers resolved at runtime.

### Implementation for User Story 2

- [X] T010 [P] [US2] Move `DispatcherQualifiers.kt` to `core/common/src/commonMain/kotlin/com/chimali/core/common/di/DispatcherQualifiers.kt`
- [X] T011 [US2] Move `DispatchersModule.kt` to `commonMain` and refactor to use platform-specific Koin module inclusion
- [X] T012 [P] [US2] Create platform-specific dispatcher providers in `androidMain` and `iosMain`

**Checkpoint**: At this point, User Story 2 should be fully functional and testable independently.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T013 [P] Move non-Android unit tests from `core/common/src/test` to `core/common/src/commonTest`
- [X] T014 [P] Update `Fido2Initializer.kt` in `:feature:fido2` to ensure it still correctly initializes the moved components
- [X] T015 Clean up unused `core/common/src/main` directory
- [X] T016 Run `./gradlew :core:common:test` to verify Android stability
- [X] T017 Run `./gradlew :core:common:compileKotlinIosArm64` to verify iOS compatibility
- [X] T018 [P] Verify `commonMain` purity by checking for `android.*` or `java.*` imports (excluding permitted KMP libs)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### Parallel Opportunities

- T003 and T004 (dependency configuration) can run in parallel.
- US1 tasks T005, T006, T008 can run in parallel.
- US2 tasks T010, T012 can run in parallel.
- Once Phase 2 is complete, US1 and US2 can proceed in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
