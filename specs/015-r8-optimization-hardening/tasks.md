# Tasks: R8 Optimization & Hardening

**Input**: Design documents from `/specs/015-r8-optimization-hardening/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project-wide flag initialization

- [x] T001 [P] Enable R8 Full Mode in `gradle.properties` via `android.r8.strictFullModeForKeepRules=true`
- [x] T002 [P] Enable optimized resource shrinking in `gradle.properties` via `android.r8.optimizedResourceShrinking=true`
- [x] T003 Update `AGENTS.md` to reflect new R8 optimization context

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core build type definitions that enable optimization and verification

**⚠️ CRITICAL**: Build types must be defined before any story verification can begin.

- [x] T004 Define `release` build type minification and resource shrinking in `app/build.gradle.kts`
- [x] T005 Implement `shrunkDebug` build type in `app/build.gradle.kts` with `initWith(release)` and `signingConfig = debug`
- [x] T006 Ensure `isDebuggable = false` for `shrunkDebug` in `app/build.gradle.kts` (Note: `isDebuggable = true` disables R8 optimizations in AGP 9.x+)
- [x] T007 [P] Configure `proguard-android-optimize.txt` as the default ProGuard file for all optimized build types in `app/build.gradle.kts`
- [x] T008 [P] Link `feature/fido2/proguard-rules.pro` to the app module configuration

**Checkpoint**: Foundation ready - optimized build types can now be compiled and tested.

---

## Phase 3: User Story 1 - Enable Production-Ready Minification (Priority: P1) 🎯 MVP

**Goal**: Enable aggressive R8 shrinking and obfuscation for production builds.

**Independent Test**: Generate a `shrunkDebug` APK and verify that classes are obfuscated (using `retrace` or a DEX explorer) and the APK size is significantly reduced (min 15% per SC-001).

### Implementation for User Story 1

- [x] T009 [US1] Set `isMinifyEnabled = true` for `release` in `app/build.gradle.kts`
- [x] T010 [US1] Set `isShrinkResources = true` for `release` in `app/build.gradle.kts`
- [x] T011 [US1] Set `isMinifyEnabled = true` for `shrunkDebug` in `app/build.gradle.kts`
- [x] T012 [US1] Set `isShrinkResources = true` for `shrunkDebug` in `app/build.gradle.kts`
- [x] T013 [P] [US1] Verify that `release` build type is not debuggable in `app/build.gradle.kts`
- [x] T014 [US1] Document baseline APK size for the unoptimized release vs the new optimized release

**Checkpoint**: User Story 1 delivers a functional, minified build.

---

## Phase 4: User Story 2 - ProGuard Keep Rule Cleanup (Priority: P2)

**Goal**: Remove redundant rules to allow deeper R8 optimization.

**Independent Test**: Verify that `shrunkDebug` build still compiles and boots to the main screen after rule removal.

### Implementation for User Story 2

- [x] T015 [US2] Purge redundant Bouncy Castle rules from `feature/fido2/proguard-rules.pro`
- [x] T016 [US2] Purge redundant Kotlinx Serialization rules from `feature/fido2/proguard-rules.pro`
- [x] T017 [US2] Purge obsolete Hilt rules from `feature/fido2/proguard-rules.pro`
- [x] T018 [US2] Purge redundant SQLCipher rules from `feature/fido2/proguard-rules.pro`
- [x] T019 [US2] Purge redundant AndroidX rules from `feature/fido2/proguard-rules.pro`
- [x] T020 [US2] Remove broad package-level wildcards (`com.chimali.fido2.**`) in `feature/fido2/proguard-rules.pro`
- [x] T021 [P] [US2] Add defensive rules for `kotlinx.serialization` named companion objects in `feature/fido2/proguard-rules.pro`
- [x] T022 [P] [US2] Add specific keep rules for critical JNI or reflection entry points in `feature/fido2/proguard-rules.pro`

**Checkpoint**: User Story 2 achieves a leaner, hardened ProGuard configuration.

---

## Phase 5: User Story 3 - Runtime Stability Verification (Priority: P3)

**Goal**: Ensure no reflection-related crashes occur in the optimized build.

**Independent Test**: Successfully run full instrumentation test suite against `shrunkDebug` build type.

### Implementation for User Story 3

- [ ] T023 [US3] Execute FIDO2 registration flow on a `shrunkDebug` build (Manual verification required on device)
- [ ] T024 [US3] Execute FIDO2 authentication flow on a `shrunkDebug` build (Manual verification required on device)
- [ ] T025 [US3] Run all UI Automator tests against the `shrunkDebug` build type
- [x] T026 [P] [US3] Analyze `mapping.txt` and `usage.txt` to ensure critical security classes were not accidentally preserved in plain text

**Checkpoint**: All user stories are now verified as stable in an optimized environment.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and documentation

- [x] T027 [P] Update `CHANGELOG.md` with R8 optimization details
- [x] T028 [P] Create a new changelog entry in `docs/changelogs/` for R8 hardening
- [ ] T029 Run `quickstart.md` validation steps (File not found, skipping)
- [x] T030 [P] Perform a final APK size and build time comparison and document in `research.md`
- [x] T031 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup (Phase 1). BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational (Phase 2).
- **User Story 2 (Phase 4)**: Depends on User Story 1 (Phase 3) for baseline optimization.
- **User Story 3 (Phase 5)**: Depends on User Story 2 (Phase 4).
- **Polish (Phase 6)**: Depends on completion of all User Stories.

### Parallel Opportunities

- T001, T002 (Setup)
- T007, T008 (Foundational)
- T013 (US1)
- T021, T022 (US2)
- T026 (US3)
- T027, T028, T030 (Polish)

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Enable R8 flags and define build types.
2. Enable minification for release.
3. Verify basic boot and size reduction.

### Incremental Delivery

1. Foundation + US1 -> Optimized Release build.
2. US2 -> Hardened and smaller build.
3. US3 -> Verified stable build.
