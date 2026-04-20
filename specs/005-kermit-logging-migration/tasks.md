# Tasks: Kermit Logging Migration

**Input**: Design documents from `/specs/005-kermit-logging-migration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Update `build.gradle.kts` to add `co.touchlab:kermit`, `com.squareup.okio:okio`, and `org.jetbrains.kotlinx:kotlinx-datetime` dependencies to common and platform source sets

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T002 Create `LogDirectoryProvider` expect declaration in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/LogDirectoryProvider.kt`
- [ ] T003 [P] Implement `AndroidLogDirectoryProvider` actual in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/AndroidLogDirectoryProvider.kt`
- [ ] T004 [P] Implement `IosLogDirectoryProvider` actual in `feature/fido2/src/iosMain/kotlin/com/chimali/fido2/util/logging/IosLogDirectoryProvider.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Multiplatform Logging Support (Priority: P1) 🎯 MVP

**Goal**: Use a unified logging API in `commonMain` so that platform-agnostic logic can be logged once and work on all targets.

**Independent Test**: Add a log statement in a `commonMain` class and verify it appears in the Android Logcat/iOS Console.

### Implementation for User Story 1

- [ ] T005 [P] [US1] Remove Timber dependencies and usage, replace with `co.touchlab.kermit.Logger` in `commonMain`
- [ ] T006 [P] [US1] Initialize Kermit with `LogcatWriter` in Android App entry point
- [ ] T007 [P] [US1] Initialize Kermit with `NSLogWriter` in iOS AppDelegate

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Privacy-Safe Local File Logging (Priority: P1)

**Goal**: Write all logs above a certain priority to a rotating local file for post-crash analysis using KMP libraries, masking sensitive data.

**Independent Test**: Verify that logs are successfully written to `fido2_crash_log.txt` on device storage via `okio` and that sensitive data is masked. Verify log rotation occurs at 5MB.

### Implementation for User Story 2

- [ ] T008 [P] [US2] Move `PrivacyLogScrubber.kt` from `androidMain` to `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubber.kt`
- [ ] T009 [US2] Move and refactor `LocalCrashReportingLogWriter.kt` to `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt` to use `okio` for file I/O
- [ ] T010 [US2] Refactor timestamping in `LocalCrashReportingLogWriter` to use `kotlinx-datetime`
- [ ] T011 [US2] Implement 5MB file rotation and backup mechanism in `LocalCrashReportingLogWriter` using `okio` `FileSystem`
- [ ] T012 [US2] Add thread-safety (via Mutex or atomic operations) to `LocalCrashReportingLogWriter`
- [ ] T013 [US2] Inject `LogDirectoryProvider` into `LocalCrashReportingLogWriter` and register the writer in app entry points

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T014 [P] Clean up any remaining legacy `Timber` configurations or classes
- [ ] T015 [P] Add unit tests for `PrivacyLogScrubber` in `commonTest`
- [ ] T016 [P] Add unit tests for `LocalCrashReportingLogWriter` rotation logic in `commonTest`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in priority order
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2)
- **User Story 2 (P1)**: Can start after Foundational (Phase 2). Technically independent from US1 but shares Kermit initialization code.

### Parallel Opportunities

- All Setup/Foundational tasks marked [P] can run in parallel
- US1 Kermit initialization across Android and iOS can be done in parallel
- Refactoring Timber calls across multiple modules can be done in parallel
- Unit tests can be written in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently (verify logs output to console/logcat)

### Incremental Delivery

1. Complete Setup + Foundational
2. Add User Story 1 (Basic logging) → Test independently → Deploy/Demo
3. Add User Story 2 (File logging) → Test independently → Deploy/Demo
