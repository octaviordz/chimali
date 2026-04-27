# Tasks: Enforce Modifier Missing

**Input**: Design documents from `/specs/024-enforce-modifier-missing/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Verify `ModifierMissing` rule is active in `config/detekt/detekt.yml`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

*(No foundational tasks required for this refactoring feature)*

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Developer Receives Immediate Feedback (Priority: P1) 🎯 MVP

**Goal**: Catch bad design practices early without needing a code review by enabling linting feedback.

**Independent Test**: Can be tested by creating a dummy composable without a `modifier` parameter, verifying that the lint check catches it locally.

### Implementation for User Story 1

- [x] T002 [US1] Search the codebase for `@Suppress("ModifierMissing")` annotations and identify files needing refactoring.
- [x] T003 [US1] Remove `@Suppress("ModifierMissing")` annotations from all identified files.

**Checkpoint**: At this point, running detekt will fail, exposing the violations.

---

## Phase 4: User Story 2 - Implement Best Practice Modifiers (Priority: P2)

**Goal**: All existing UI composables are updated to accept a `modifier` parameter and use it as the first parameter to the root layout element.

**Independent Test**: Can be tested by inspecting the previously suppressed composable functions and verifying they now accept a `modifier: Modifier = Modifier` and pass it to their root UI node.

### Implementation for User Story 2

- [x] T004 [US2] Refactor identified composables to accept `modifier: Modifier = Modifier` as an optional parameter.
- [x] T005 [US2] Update the internal implementation of the refactored composables to apply the `modifier` parameter to the root layout element.

**Checkpoint**: At this point, all composables follow the best practices, and detekt checks should pass.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T006 Verify there are no remaining `ModifierMissing` violations by running `.\gradlew detektAll`.
- [x] T007 Run Local CI pipeline via `tools/local-ci.ps1`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: N/A
- **User Stories (Phase 3+)**: US2 depends on the identification of files in US1.
- **Polish (Final Phase)**: Depends on all user stories being complete to ensure the CI pipeline passes.

### Parallel Opportunities

- Due to the nature of this project-wide refactoring, multiple developers could theoretically parallelize the refactoring (US2) across different modules or files once they are identified in US1.

---

## Implementation Strategy

### Incremental Delivery

1. Complete Setup to ensure the rule is active.
2. Remove suppressions (US1) to expose the technical debt.
3. Fix the exposed violations by updating composable signatures and root modifiers (US2).
4. Validate project-wide compliance by running the Local CI pipeline.
