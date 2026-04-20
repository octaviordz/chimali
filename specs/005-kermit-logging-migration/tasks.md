# Tasks: Kermit Logging Migration

**Input**: Design documents from `/specs/005-kermit-logging-migration/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Add `kermit` dependency declaration in `gradle/libs.versions.toml`
- [x] T002 Add `api(libs.kermit)` in `core/common/build.gradle.kts` `commonMain` dependencies
- [x] T003 Remove `libs.timber` dependency from `feature/fido2/build.gradle.kts` and other modules

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Sync Gradle to ensure Kermit is available across all modules

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 2 - Privacy-Safe Local File Logging (Priority: P1)

**Goal**: Implement a privacy-aware rotating file sink for Android crash reporting.

**Independent Test**: Can be fully tested by verifying the contents and rotation of `fido2_crash_log.txt` on the device storage after generating logs.

### Implementation for User Story 2

- [x] T005 [US2] Implement `LocalCrashReportingLogWriter` in `feature/fido2/src/main/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt`
- [x] T006 [US2] Update `PrivacyLogScrubber` integration within the new `LocalCrashReportingLogWriter`
- [x] T007 [US2] Delete legacy `LocalCrashReportingTree` at `feature/fido2/src/main/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingTree.kt`

**Checkpoint**: The file sink implementation is complete, ready to be wired into the app.

---

## Phase 4: User Story 1 - Multiplatform Logging Support (Priority: P1) 🎯 MVP

**Goal**: Replace Timber API with unified Kermit `Logger` across the project.

**Independent Test**: Can be tested by adding a log statement and verifying it appears in the Android Logcat and local file.

### Implementation for User Story 1

- [x] T008 [US1] Update `Fido2Initializer` to initialize Kermit `Logger` and add the new `LocalCrashReportingLogWriter` in `feature/fido2/src/main/kotlin/com/chimali/fido2/Fido2Initializer.kt`
- [ ] T009 [P] [US1] Replace Timber imports with `co.touchlab.kermit.Logger` in `feature/fido2` source files
- [ ] T010 [P] [US1] Replace Timber imports with `co.touchlab.kermit.Logger` in `core/security` source files
- [ ] T011 [P] [US1] Replace Timber imports with `co.touchlab.kermit.Logger` in `app` source files

**Checkpoint**: At this point, the application uses Kermit completely. Timber is fully eradicated.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T012 Run app to verify startup time impact is <50ms and logging output is visible in Logcat
- [ ] T013 Simulate a log burst to verify 5MB log file rotation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion
- **User Stories (Phase 3+)**: US2 handles the file writing logic which is a prerequisite for US1's initialization step.
- **Polish (Final Phase)**: Depends on all user stories being complete.

### Parallel Opportunities

- All Setup tasks (T001, T002, T003) can be done sequentially in one go before syncing.
- T009, T010, T011 in User Story 1 can be executed in parallel since they involve independent module source files.

---

## Implementation Strategy

### MVP First

1. Complete Phase 1 & 2: Setup and Foundation.
2. Complete Phase 3 (US2): Build the `LogWriter`.
3. Complete Phase 4 (US1): Wire it up and replace all call sites.
4. **STOP and VALIDATE**: Test application logging and rotation.
