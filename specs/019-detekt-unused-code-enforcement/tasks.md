# Tasks: detekt-unused-code-enforcement

**Input**: Design documents from `/specs/019-detekt-unused-code-enforcement/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a baseline of current violations before making configuration changes.

- [x] T001 Run `./gradlew detekt` and record current baseline of violations.
- [x] T002 [P] Verify that the local CI pipeline `tools/local-ci.ps1` is passing.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Identify existing violations that will be triggered once the rules are enforced.

- [x] T003 Research and list all existing `UnusedPrivateMember` and `UnusedPrivateProperty` violations in test source sets (`**/test/**`, `**/androidTest/**`).
- [x] T004 [P] Identify any current production code violations that are intentionally ignored or suppressed.

---

## Phase 3: User Story 2 - Maintain Generated Code Compatibility (Priority: P2)

**Goal**: Ensure Detekt ignores unused private elements in generated code to prevent build blocks.

**Independent Test**: Run Detekt on a project with generated code in `**/build/**` and verify no violations are reported for those files.

### Implementation for User Story 2

- [x] T005 [US2] Update `config/detekt/detekt.yml` to add targeted excludes `['**/build/**', '**/generated/**']` for the `UnusedPrivateMember` rule.
- [x] T006 [US2] Update `config/detekt/detekt.yml` to add targeted excludes `['**/build/**', '**/generated/**']` for the `UnusedPrivateProperty` rule.
- [x] T007 [US2] Verify generated code compatibility by running Detekt and checking for zero findings in `**/build/generated/**`.

**Checkpoint**: User Story 2 complete - generated code is now safely excluded from unused code rules.

---

## Phase 4: User Story 1 - Enforce Clean Code in Tests (Priority: P1) 🎯 MVP

**Goal**: Apply same code quality standards to test suites by removing broad test-related excludes.

**Independent Test**: Introduce an unused private property in a test file and verify that Detekt flags it as a violation.

### Implementation for User Story 1

- [x] T008 [US1] Update `config/detekt/detekt.yml` to remove test-related excludes (e.g., `**/test/**`, `**/androidTest/**`) from the `UnusedPrivateMember` rule.
- [x] T009 [US1] Update `config/detekt/detekt.yml` to remove test-related excludes (e.g., `**/test/**`, `**/androidTest/**`) from the `UnusedPrivateProperty` rule.
- [x] T010 [US1] Resolve identified violations from T003 by removing dead code in test suites.
- [x] T011 [US1] Handle intentional unused code in tests by applying `@Suppress` with justifying comments as per the project policy.
- [x] T012 [US1] Perform a final cleanup of any remaining unused private members/properties across production code.

**Checkpoint**: User Story 1 complete - tests are now subject to strict unused code enforcement.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and documentation alignment.

- [x] T013 Update configuration comments in `config/detekt/detekt.yml` to reflect the removal of test excludes and the inclusion of generated code excludes.
- [x] T014 [P] Verify that the instructions in `specs/019-detekt-unused-code-enforcement/quickstart.md` are accurate for the final implementation.
- [x] T015 Run Local CI pipeline via `tools/local-ci.ps1` to ensure all gates pass.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Setup.
- **User Story 2 (P2)**: Can be implemented first to ensure safety for generated code.
- **User Story 1 (P1)**: Final enforcement step, depends on resolving violations identified in Phase 2.
- **Polish (Final Phase)**: Depends on all stories completion.

### User Story Dependencies

- **User Story 2 (P2)**: Should be completed before or alongside US1 to avoid false positives in generated folders during cleanup.
- **User Story 1 (P1)**: The core quality increment.

### Parallel Opportunities

- T002 and T004 can be performed in parallel with their respective phase counterparts.
- US2 and US1 configuration changes (T005, T006 vs T008, T009) can be prepared in parallel but should be applied sequentially to `detekt.yml`.

---

## Parallel Example: User Story 2

```bash
# Update both rules in detekt.yml in parallel (logical preparation):
Task: "T005 [US2] Update config/detekt/detekt.yml for UnusedPrivateMember"
Task: "T006 [US2] Update config/detekt/detekt.yml for UnusedPrivateProperty"
```

---

## Implementation Strategy

### MVP First (User Story 1 Focus)

1. Complete Setup and Foundational research.
2. Apply US2 excludes to ensure build stability.
3. Apply US1 enforcement and resolve test violations.
4. Validate with a manual violation in a test file.

## Notes

- [P] tasks = different files or research-based actions with no shared state.
- [Story] label ensures traceability to the feature specification.
- Every task must be verified with a Detekt run.
