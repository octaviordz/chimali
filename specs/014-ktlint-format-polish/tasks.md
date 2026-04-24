# Tasks: KtLint Format Polish

**Input**: Design documents from `specs/014-ktlint-format-polish/`
**Prerequisites**: [plan.md](file:///d:/octav/source/repos/Chimali/specs/014-ktlint-format-polish/plan.md), [spec.md](file:///d:/octav/source/repos/Chimali/specs/014-ktlint-format-polish/spec.md)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story, prioritizing the resolution of current pre-commit blockers.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US0, US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Centralizing ktlint configuration and defining style rules.

- [x] T001 Centralize ktlint configuration in root [build.gradle.kts](file:///d:/octav/source/repos/Chimali/build.gradle.kts)
- [x] T002 [P] Create root [.editorconfig](file:///d:/octav/source/repos/Chimali/.editorconfig) with Official Kotlin Style Guide rules
- [x] T003 [P] Remove local ktlint configuration from [feature/fido2/build.gradle.kts](file:///d:/octav/source/repos/Chimali/feature/fido2/build.gradle.kts)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Ensuring the environment is ready for project-wide formatting.

- [x] T004 Apply ktlint plugin to all subprojects in root [build.gradle.kts](file:///d:/octav/source/repos/Chimali/build.gradle.kts)
- [x] T005 Verify all core modules (`core:common`, `core:security`, `core:ui`) are picked up by `ktlintCheck`

---

## Phase 3: User Story 0 - Satisfy Pre-Commit Gates (Priority: P0) 🎯 MVP

**Goal**: Resolve current import ordering failures blocking the KMP stabilization commit.

**Independent Test**: Running `./gradlew :feature:fido2:ktlintCheck` should pass.

### Implementation for User Story 0

- [x] T006 [US0] Run `./gradlew :feature:fido2:ktlintFormat` to fix import ordering in [feature/fido2/src/test/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDaoTest.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/test/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDaoTest.kt)
- [x] T007 [US0] Verify pre-commit hook succeeds for `feature:fido2` by running [tools/local-ci.ps1](file:///d:/octav/source/repos/Chimali/tools/local-ci.ps1) with `-SkipClean`

**Checkpoint**: Pre-commit blockers are resolved; the KMP stabilization work can now be committed.

---

## Phase 4: User Story 1 - Automated Code Formatting (Priority: P1)

**Goal**: Apply formatting project-wide while preserving comments and semantics.

**Independent Test**: Running `./gradlew ktlintCheck` should return no errors across all modules.

### Implementation for User Story 1

- [x] T008 [US1] Apply project-wide formatting via `./gradlew ktlintFormat`
- [x] T009 [US1] Perform manual audit of 5 files (e.g., [CredentialListScreenTest.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidTest/kotlin/com/chimali/fido2/presentation/management/CredentialListScreenTest.kt)) to ensure KDocs and comments are preserved
- [x] T010 [US1] Run full test suite `./gradlew test` to ensure no semantic logic changes were introduced

---

## Phase 5: User Story 2 - Style Violation Enforcement (Priority: P2)

**Goal**: Ensure style violations are caught in the build pipeline.

**Independent Test**: Introducing a style violation (e.g., incorrect indentation) triggers a failure in `ktlintCheck`.

### Implementation for User Story 2

- [x] T011 [US2] Introduce a temporary style violation in [LocalCrashReportingLogWriter.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt) and verify `./gradlew ktlintCheck` fails
- [x] T012 [US2] Fix the violation and verify `./gradlew ktlintCheck` passes again

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final documentation and quality verification.

- [x] T013 Update [quickstart.md](file:///d:/octav/source/repos/Chimali/specs/014-ktlint-format-polish/quickstart.md) with standardized formatting and check commands
- [x] T014 Run full Local CI pipeline via [tools/local-ci.ps1](file:///d:/octav/source/repos/Chimali/tools/local-ci.ps1) and verify execution time is < 60s (SC-005)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion.
- **User Story 0 (Phase 3)**: MUST complete first to unblock the current commit.
- **User Story 1 & 2 (Phase 4-5)**: Can proceed after US0 is complete.

---

## Parallel Example: Setup

```bash
# Launch setup tasks in parallel:
Task: "Create root .editorconfig with Official Kotlin Style Guide rules"
Task: "Remove local ktlint configuration from feature/fido2/build.gradle.kts"
```

---

## Implementation Strategy

### MVP First (User Story 0 Only)

1. Complete Phase 1 & 2.
2. Complete Phase 3 (US0) to unblock the KMP stabilization commit.
3. **STOP and VALIDATE**: Verify `local-ci.ps1` passes for the fido2 module.

### Incremental Delivery

1. Unblock pre-commit (US0).
2. Apply project-wide polish (US1).
3. Enforce quality gates (US2).
