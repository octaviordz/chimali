# Tasks: Compose BOM Update

**Input**: Design documents from `/specs/041-compose-bom-update/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

*(No setup tasks needed for this dependency update)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T001 Open and analyze `gradle/libs.versions.toml` to map out all Compose-related dependencies and version pins

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Clean Test Log Output (Priority: P1) 🎯 MVP

**Goal**: Eliminate spurious `ClassNotFoundException` warnings during test runs.

**Independent Test**: Run "All Tests" configuration to verify Logcat output is clean of `PlaceholderHardwareCanvas` and `androidx.compose.ui.tooling.animation.*` warnings.

### Implementation for User Story 1

- [x] T002 [US1] Update the Compose BOM version to `2026.04.00` in `gradle/libs.versions.toml`
- [x] T003 [US1] Clean project to clear cached classes (`./gradlew clean`)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Continued Application Stability (Priority: P1)

**Goal**: Ensure the app continues to function exactly as before without regressions.

**Independent Test**: Local CI passes.

### Implementation for User Story 2

- [x] T004 [US2] Run instrumented tests to ensure 100% of previously passing tests continue to pass
- [x] T005 [US2] Run the full Local CI pipeline (`tools/local-ci.ps1`) to ensure unit tests, Detekt, and Ktlint checks pass with the updated BOM

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Access to Latest Platform Capabilities (Priority: P2)

**Goal**: Keep the project on a current and supported version of Compose.

**Independent Test**: Verify the build uses the expected versions and no manual overrides conflict with the BOM.

### Implementation for User Story 3

- [x] T006 [US3] Remove manual version pins for any Compose libraries in `gradle/libs.versions.toml` that are now managed by the BOM
- [x] T007 [US3] Ensure the Compose compiler plugin version aligns with the current Kotlin version (2.3.20) in `gradle/libs.versions.toml`

**Checkpoint**: All user stories should now be independently functional

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T008 [P] Fix any new deprecation warnings introduced by the Compose BOM update across the codebase

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: T001
- **User Stories (Phase 3+)**: Depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Depends on Foundational (Phase 2). Modifies versions.
- **User Story 3 (P2)**: Depends on User Story 1. Cleans up redundant overrides.
- **User Story 2 (P1)**: Depends on US1 and US3. Validates stability after changes.

### Within Each User Story

- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- Due to the nature of a centralized dependency update, all tasks within `gradle/libs.versions.toml` are highly coupled and should be performed sequentially by one agent.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational (Analyze current state)
2. Complete Phase 3: User Story 1 (Update BOM and clean)
3. **STOP and VALIDATE**: Test User Story 1 independently

### Incremental Delivery

1. Complete Foundational → Foundation ready
2. Add User Story 1 (Bump BOM)
3. Add User Story 3 (Remove manual pins)
4. Add User Story 2 (Run full tests to validate stability)
5. Polish (Fix deprecations if any)
