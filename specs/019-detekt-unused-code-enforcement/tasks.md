# Tasks: detekt-unused-code-enforcement

**Input**: Design documents from `/specs/019-detekt-unused-code-enforcement/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)
- [x] T001 Run `./gradlew detekt` and record current baseline of violations.
- [x] T002 [P] Verify that the local CI pipeline `tools/local-ci.ps1` is passing.

## Phase 2: Foundational (Blocking Prerequisites)
- [x] T003 Research and record all existing `UnusedPrivateMember` and `UnusedPrivateProperty` violations in test source sets (`**/test/**`, `**/androidTest/**`) in `research_findings.txt`.
- [x] T004 [P] Identify and record any current production code violations that are intentionally ignored or suppressed in `research_findings.txt`.

## Phase 3: User Story 2 - Maintain Generated Code Compatibility (Priority: P2)
- [x] T005 [US2] Update `config/detekt/detekt.yml` to add targeted excludes `['**/build/**', '**/generated/**']` for the `UnusedPrivateMember` rule.
- [x] T006 [US2] Update `config/detekt/detekt.yml` to add targeted excludes `['**/build/**', '**/generated/**']` for the `UnusedPrivateProperty` rule.
- [x] T007 [US2] Verify generated code compatibility by running Detekt and checking for zero findings in `**/build/generated/**`.

## Phase 4: User Story 1 - Enforce Clean Code in Tests (Priority: P1) 🎯 MVP
- [x] T008 [US1] Update `config/detekt/detekt.yml` to remove test-related excludes from the `UnusedPrivateMember` rule.
- [x] T009 [US1] Update `config/detekt/detekt.yml` to remove test-related excludes from the `UnusedPrivateProperty` rule.
- [x] T010 [US1] Resolve identified violations from T003 by removing dead code in test suites.
- [x] T011 [US1] Handle intentional unused code in tests by applying `@Suppress` (None found/needed).
- [x] T012 [US1] Perform a final cleanup of any remaining unused private members/properties across production code.

## Phase 5: Polish & Cross-Cutting Concerns
- [x] T013 Update configuration comments in `config/detekt/detekt.yml` to reflect the removal of test excludes and the inclusion of generated code excludes.
- [x] T014 [P] Verify that the instructions in `specs/019-detekt-unused-code-enforcement/quickstart.md` are accurate for the final implementation.
- [x] T015 Intentionally introduce an unused private property in a test file and verify that `tools/local-ci.ps1` fails as expected (SC-003).
- [x] T016 Run Local CI pipeline via `tools/local-ci.ps1` to ensure all gates pass after final cleanup.
