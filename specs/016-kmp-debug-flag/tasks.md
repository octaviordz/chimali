# Tasks: KMP Debug Flag

**Input**: Design documents from `specs/016-kmp-debug-flag/`
**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Enable required build-time features.

- [x] T001 [P] Enable `buildConfig` in `core/common/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core KMP infrastructure for build variant detection.

- [x] T002 Define `expect val isDebug` in `core/common/src/commonMain/kotlin/com/chimali/core/common/BuildVariant.kt`
- [x] T003 [P] Implement `actual val isDebug` for Android in `core/common/src/androidMain/kotlin/com/chimali/core/common/BuildVariant.kt`
- [x] T004 [P] Implement `actual val isDebug` for iOS in `core/common/src/iosMain/kotlin/com/chimali/core/common/BuildVariant.kt`
- [x] T005 Create verification test in `core/common/src/commonTest/kotlin/com/chimali/core/common/BuildVariantTest.kt`

**Checkpoint**: Foundation ready - feature adoption can now begin.

---

## Phase 3: User Story 1 - Secure Debug Tool Access (Priority: P1) 🎯 MVP

**Goal**: Replace hardcoded guards in the UI with the secure debug flag.

**Independent Test**: Verify "Dev-only — Master Seed" section visibility in debug vs release builds.

### Implementation for User Story 1

- [x] T006 [US1] Update `DevelopmentToolsScreen.kt` to use `com.chimali.core.common.isDebug` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`

**Checkpoint**: User Story 1 is functional and testable independently.

---

## Phase 4: User Story 2 - Platform-Agnostic Build Detection (Priority: P2)

**Goal**: Extend debug flag usage to feature initializers.

**Independent Test**: Verify logging behavior (Kermit) changes based on build variant.

### Implementation for User Story 2

- [x] T007 [US2] Update `Fido2Initializer.kt` to use `com.chimali.core.common.isDebug` (refactor `init` signature to remove manual parameter) in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/Fido2Initializer.kt`
- [x] T008 [US2] Update `ChimaliApplication.kt` caller to remove manual debug parameter in `app/src/main/kotlin/com/chimali/ChimaliApplication.kt`



**Checkpoint**: All user stories are independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verification and final quality checks.

- [x] T009 Verify R8 dead code elimination for `shrunkDebug` variant using APK Analyzer (Manual verification by user recommended)
- [x] T010 Run Local CI pipeline via `tools/local-ci.ps1` (Android verified; iOS requires Mac host)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion.
- **User Stories (Phase 3+)**: Depend on Foundational phase completion.
- **Polish (Final Phase)**: Depends on all user stories being complete.

### Parallel Opportunities

- T003 and T004 (Platform implementations) can run in parallel.
- US1 and US2 implementation tasks can technically run in parallel once T002-T005 are done, though they are sequential in priority.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2.
2. Complete Phase 3 (User Story 1).
3. **STOP and VALIDATE**: Verify UI guard works as expected.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story from spec.md.
- Ensure `BuildVariantTest.kt` covers both true and false states (via mock/shadow if possible, or manual verification).
