# Tasks: Fix AGP and KMP Build Warnings

**Input**: Design documents from `/specs/013-fix-agp-kmp-warnings/`
**Prerequisites**: plan.md, spec.md, research.md

**Tests**: Verification via Gradle build output and Local CI pipeline.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and analysis

- [ ] T001 Analyze all modules, libraries, and dependencies for migration impact (FR-001)
- [ ] T002 [P] Verify `libs.versions.toml` plugin definitions for AGP 9.2.0 compatibility

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core project-wide configuration cleanup

**⚠️ CRITICAL**: Legacy flag removal MUST be complete before user story work begins

- [ ] T003 Remove `android.builtInKotlin` from `gradle.properties` (FR-004)
- [ ] T004 [P] Remove `android.newDsl` from `gradle.properties` (FR-004)

**Checkpoint**: Foundation ready - project is configured for AGP 9.2.0 built-in Kotlin and modern DSL

---

## Phase 3: User Story 1 - Clean Build Environment (Priority: P1) 🎯 MVP

**Goal**: Remove "deprecated compatibility" warnings from KMP modules by migrating to the new KMP-Android plugin.

**Independent Test**: Run `./gradlew build` and verify zero occurrences of the "org.jetbrains.kotlin.multiplatform plugin deprecated compatibility" warning.

### Implementation for User Story 1

- [ ] T005 [P] [US1] Migrate `:feature:fido2` to `com.android.kotlin.multiplatform.library` in `feature/fido2/build.gradle.kts`
- [ ] T006 [P] [US1] Migrate `:core:security` to `com.android.kotlin.multiplatform.library` in `core/security/build.gradle.kts`
- [ ] T007 [P] [US1] Migrate `:core:domain` to `com.android.kotlin.multiplatform.library` in `core/domain/build.gradle.kts`
- [ ] T008 [P] [US1] Migrate `:core:common` to `com.android.kotlin.multiplatform.library` in `core/common/build.gradle.kts`

**Checkpoint**: At this point, all KMP modules should be using the modern plugin and build without deprecation warnings.

---

## Phase 4: User Story 2 - Build System Modernization (Priority: P2)

**Goal**: Complete the migration to built-in Kotlin support and ensure cross-platform target stability.

**Independent Test**: Run `./gradlew build` and verify zero occurrences of the "Deprecated 'org.jetbrains.kotlin.android' plugin usage" warning.

### Implementation for User Story 2

- [ ] T009 [US2] Remove `org.jetbrains.kotlin.android` plugin application from `feature/vault/build.gradle.kts`
- [ ] T010 [US2] Verify build targets (Android, Desktop, JVM) for all migrated modules (FR-006)

**Checkpoint**: Build system is fully modernized and redundant plugins are removed.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and quality gate

- [ ] T011 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup completion.
- **User Stories (Phase 3 & 4)**: Depend on Foundational phase completion.
- **Polish (Phase 5)**: Depends on all user stories completion.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2.
- **User Story 2 (P2)**: Can start after Phase 2.

### Parallel Opportunities

- T002 can run in parallel with T001.
- T004 can run in parallel with T003.
- T005, T006, T007, T008 can all run in parallel.

---

## Parallel Example: User Story 1

```bash
# Migrate multiple modules in parallel
Task: "Migrate :feature:fido2 to com.android.kotlin.multiplatform.library in feature/fido2/build.gradle.kts"
Task: "Migrate :core:security to com.android.kotlin.multiplatform.library in core/security/build.gradle.kts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2.
2. Complete Phase 3 (US1).
3. **STOP and VALIDATE**: Verify warnings are gone.

### Incremental Delivery

1. Foundation (Phase 1 & 2) → Baseline ready.
2. User Story 1 → Deprecation warnings fixed.
3. User Story 2 → Built-in Kotlin migration complete.
4. Final verification via Local CI.
