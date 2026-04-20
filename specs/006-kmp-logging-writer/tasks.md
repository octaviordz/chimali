# Tasks: KMP Logging Writer

**Input**: Design documents from `/specs/006-kmp-logging-writer/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Verify and add `com.squareup.okio:okio` and `org.jetbrains.kotlinx:kotlinx-datetime` dependencies to the appropriate `build.gradle.kts` source sets

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Create `LogDirectoryProvider` interface in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/LogDirectoryProvider.kt`
- [x] T003 [P] Create `PlatformLock` expect declaration in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PlatformLock.kt`
- [x] T004 [P] Implement `PlatformLock` actual using `ReentrantLock` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/PlatformLock.kt`
- [x] T005 [P] Implement `PlatformLock` actual using `NSRecursiveLock` in `feature/fido2/src/iosMain/kotlin/com/chimali/fido2/util/logging/PlatformLock.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - KMP Crash Logger Usage (Priority: P1) 🎯 MVP

**Goal**: Use `LocalCrashReportingLogWriter` from any KMP module (commonMain) so that crash reports are logged reliably regardless of the target platform.

**Independent Test**: Trigger a logged event from common code and verify that the log is written to the expected file path using the multiplatform Okio file system.

### Implementation for User Story 1

- [x] T006 [US1] Move `PrivacyLogScrubber.kt` to `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubber.kt`
- [x] T007 [US1] Move `LocalCrashReportingLogWriter.kt` to `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt`
- [x] T008 [US1] Refactor `LocalCrashReportingLogWriter` to replace `android.content.Context` with `LogDirectoryProvider`
- [x] T009 [US1] Refactor `LocalCrashReportingLogWriter` file I/O and 5MB rotation to use `okio.FileSystem.SYSTEM`
- [x] T010 [US1] Refactor `LocalCrashReportingLogWriter` timestamping to use `kotlinx-datetime`
- [x] T011 [US1] Apply `PlatformLock` to secure thread-safe file appending in `LocalCrashReportingLogWriter`
- [x] T012 [P] [US1] Implement `AndroidLogDirectoryProvider` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/AndroidLogDirectoryProvider.kt`
- [x] T013 [P] [US1] Implement `IosLogDirectoryProvider` in `feature/fido2/src/iosMain/kotlin/com/chimali/fido2/util/logging/IosLogDirectoryProvider.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T014 [P] Update Android Application class and iOS AppDelegate to inject correct providers as shown in `quickstart.md`
- [x] T015 [P] Run quickstart logic validation and verify logs are written to expected paths
- [x] T016 Code cleanup and removal of any lingering `java.*` or `android.*` imports in the `commonMain` logging utilities

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories

### Parallel Opportunities

- Foundational `PlatformLock` actual implementations for Android and iOS can be done in parallel.
- User Story 1's `LogDirectoryProvider` implementations for Android and iOS can be done in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently by verifying cross-platform compilation and successful Okio file writes.
5. Finish Polish Phase.
