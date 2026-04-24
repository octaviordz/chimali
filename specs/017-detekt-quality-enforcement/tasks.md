# Tasks: Detekt Quality Enforcement: MaxLineLength

**Input**: Design documents from `/specs/017-detekt-quality-enforcement/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Tests**: Tests are not explicitly requested as a separate TDD phase, but verification via `./gradlew detekt` is core to each task.

**Organization**: Tasks are grouped by setup, foundation, and user stories to ensure a stable transition to stricter enforcement.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1: Test Enforcement, US2: Automated Compliance)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Standardize Detekt application across the entire multi-module project.

- [ ] T001 Configure root `build.gradle.kts` to apply the Detekt plugin to all subprojects
- [ ] T002 Update `config/detekt/detekt.yml` to remove the `excludes` list from the `MaxLineLength` rule

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish a baseline of violations across all previously unmonitored modules.

- [ ] T003 Verify Detekt plugin is correctly applied to all modules by running `./gradlew detekt`
- [ ] T004 Generate a full report of `MaxLineLength` violations project-wide to confirm scope

---

## Phase 3: User Story 1 - Enforce MaxLineLength in Test Code (Priority: P1) 🎯 MVP

**Goal**: Ensure all test code complies with the 120-character limit.

**Independent Test**: Running `./gradlew detekt` should report zero `MaxLineLength` violations in any `*Test` directory.

### Implementation for User Story 1

- [ ] T005 [P] [US1] Refactor violations in `feature/fido2/src/test/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubberTest.kt`
- [ ] T006 [P] [US1] Refactor violations in `core/security/src/test/kotlin/` (and other core test directories)
- [ ] T007 [P] [US1] Refactor violations in `feature/vault/src/test/kotlin/`
- [ ] T008 [US1] Verify no logic changes were introduced by running `./gradlew test` across all modules

**Checkpoint**: User Story 1 is complete when all test code is compliant and tests pass.

---

## Phase 4: User Story 2 - Automated Compliance (Priority: P2)

**Goal**: Resolve existing violations in production code that were previously hidden by disconnected enforcement.

**Independent Test**: `./gradlew detekt` passes with zero `MaxLineLength` findings across the entire project.

### Implementation for User Story 2

- [ ] T009 [P] [US2] Refactor long parameter lists in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt` using wrapping patterns
- [ ] T010 [P] [US2] Refactor long expressions in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt` using intermediate variables
- [ ] T011 [P] [US2] Refactor any remaining production violations identified in T004
- [ ] T012 [US2] Verify no logic changes were introduced by running `./gradlew compileDebugSources` and all unit tests

**Checkpoint**: User Story 2 is complete when all production code is compliant and stable.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and documentation.

- [ ] T013 Update `docs/changelogs/` with the quality enforcement achievements
- [ ] T014 [P] Verify `excludeRawStrings: true` is working correctly by checking files with long raw strings
- [ ] T015 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Must be completed first to enable the checks.
- **Foundational (Phase 2)**: Depends on Setup. Necessary to see the work ahead.
- **User Story 1 (Phase 3)**: P1 priority. Focus on tests as requested.
- **User Story 2 (Phase 4)**: P2 priority. Focus on remaining production code.
- **Polish (Phase 5)**: Final verification.

---

## Parallel Example: User Story 1 & 2

```bash
# Refactor different modules in parallel:
Task: "Refactor violations in feature/fido2/src/test/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubberTest.kt"
Task: "Refactor violations in feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Apply plugin and update config.
2. Fix all violations in test code.
3. Validate that `detekt` passes for all test source sets.

### Incremental Delivery

1. Standardize checks → Foundations ready.
2. Clean up tests → US1 complete (MVP).
3. Clean up production → US2 complete.
4. Total compliance achieved.
