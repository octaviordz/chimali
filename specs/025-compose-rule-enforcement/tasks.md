# Tasks: Compose Rule Enforcement

**Feature**: Compose Rule Enforcement  
**Branch**: `025-compose-rule-enforcement`  
**Date**: 2026-04-27  
**Spec**: [Compose Rule Enforcement](spec.md) | **Plan**: [Implementation Plan](plan.md)

## Overview

This task list provides actionable steps for removing `@Suppress(LambdaParameterInRestartableEffect)` and `@Suppress(ComposableParamOrder)` annotations from the Chimali codebase while maintaining code quality and functionality.

## Phase 1: Setup

**Goal**: Prepare environment and tools for systematic cleanup

- [ ] T001 Verify Detekt configuration includes Compose rules in config/detekt/detekt.yml
- [ ] T002 Update build.gradle.kts files to ensure Detekt 1.23.0+ with Compose rules
- [ ] T003 Create suppression tracking spreadsheet in docs/compose-cleanup-tracker.xlsx
- [ ] T004 Set up branch protection rules for cleanup branch in version control

## Phase 2: Discovery & Analysis

**Goal**: Identify all target suppressions across the codebase

- [ ] T005 [P] Scan entire codebase for @Suppress annotations using ripgrep in app/src/
- [ ] T006 [P] Scan core modules for @Suppress annotations using ripgrep in core/
- [ ] T007 [P] Scan feature modules for @Suppress annotations using ripgrep in feature/
- [ ] T008 [P] Run Detekt analysis to identify specific rule violations in config/detekt/detekt.yml
- [ ] T009 [P] Create suppression inventory report in docs/suppression-inventory.md

## Phase 3: User Story 1 - LambdaParameterInRestartableEffect Removal (Priority: P1)

**Goal**: Remove all LambdaParameterInRestartableEffect suppressions and fix underlying restartable effect issues

**Independent Test**: Run static analysis to verify no LambdaParameterInRestartableEffect suppressions remain and all Compose code compiles without warnings

### Implementation Tasks

- [ ] T010 [US1] Analyze LambdaParameterInRestartableEffect suppressions in app/src/main/kotlin/
- [ ] T011 [US1] Analyze LambdaParameterInRestartableEffect suppressions in core/common/
- [ ] T012 [US1] Analyze LambdaParameterInRestartableEffect suppressions in core/data/src/
- [ ] T013 [US1] Analyze LambdaParameterInRestartableEffect suppressions in feature/authenticator/src/
- [ ] T014 [US1] Analyze LambdaParameterInRestartableEffect suppressions in feature/fido2/src/
- [ ] T015 [US1] Analyze LambdaParameterInRestartableEffect suppressions in feature/vault/src/
- [ ] T016 [US1] Analyze LambdaParameterInRestartableEffect suppressions in feature/editor/src/
- [ ] T017 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in app/src/main/kotlin/
- [ ] T018 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in core/common/
- [ ] T019 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in core/data/src/
- [ ] T020 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in feature/authenticator/src/
- [ ] T021 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in feature/fido2/src/
- [ ] T022 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in feature/vault/src/
- [ ] T023 [P] [US1] Remove LambdaParameterInRestartableEffect suppressions in feature/editor/src/
- [ ] T024 [P] [US1] Fix lambda parameter naming in restartable effects across all modules
- [ ] T025 [US1] Verify no memory leaks in restartable effects after suppression removal
- [ ] T026 [US1] Run Detekt analysis to verify LambdaParameterInRestartableEffect compliance

## Phase 4: User Story 2 - ComposableParamOrder Removal (Priority: P2)

**Goal**: Remove all ComposableParamOrder suppressions and reorder parameters to follow Compose conventions

**Independent Test**: Verify all Composable functions have parameters in correct order (content, modifiers, other parameters) and compile without warnings

### Implementation Tasks

- [ ] T027 [US2] Analyze ComposableParamOrder suppressions in app/src/main/kotlin/
- [ ] T028 [US2] Analyze ComposableParamOrder suppressions in core/common/src/
- [ ] T029 [US2] Analyze ComposableParamOrder suppressions in core/data/src/
- [ ] T030 [US2] Analyze ComposableParamOrder suppressions in feature/authenticator/src/
- [ ] T031 [US2] Analyze ComposableParamOrder suppressions in feature/fido2/src/
- [ ] T032 [US2] Analyze ComposableParamOrder suppressions in feature/vault/src/
- [ ] T033 [US2] Analyze ComposableParamOrder suppressions in feature/editor/src/
- [ ] T034 [US2] Identify public vs internal Composable functions for binary compatibility
- [ ] T035 [P] [US2] Remove ComposableParamOrder suppressions in app/src/main/kotlin/
- [ ] T036 [P] [US2] Remove ComposableParamOrder suppressions in core/common/src/
- [ ] T037 [P] [US2] Remove ComposableParamOrder suppressions in core/data/src/
- [ ] T038 [P] [US2] Remove ComposableParamOrder suppressions in feature/authenticator/src/
- [ ] T039 [P] [US2] Remove ComposableParamOrder suppressions in feature/fido2/src/
- [ ] T040 [P] [US2] Remove ComposableParamOrder suppressions in feature/vault/src/
- [ ] T041 [P] [US2] Remove ComposableParamOrder suppressions in feature/editor/src/
- [ ] T042 [US2] Reorder Composable function parameters to follow conventions (content, modifier, others)
- [ ] T043 [US2] Create overloads for public functions where reordering breaks binary compatibility
- [ ] T044 [US2] Add @Deprecated annotations for migration paths where needed
- [ ] T045 [US2] Run Detekt analysis to verify ComposableParamOrder compliance

## Phase 5: User Story 3 - Case-by-case Complex Evaluation (Priority: P3)

**Goal**: Evaluate complex ComposableParamOrder cases individually and determine if suppression can be removed

**Independent Test**: Manually review each complex case and verify either suppression is removed with proper refactoring or documented justification is provided

### Implementation Tasks

- [ ] T046 [US3] Identify complex ComposableParamOrder cases requiring manual review
- [ ] T047 [US3] Evaluate binary compatibility impact for complex cases
- [ ] T048 [US3] Document justification for any suppressions that cannot be removed
- [ ] T049 [US3] Create refactoring plans for complex cases that can be resolved
- [ ] T050 [US3] Implement refactoring for resolvable complex cases
- [ ] T051 [US3] Document remaining suppressions with clear justifications in docs/remaining-suppressions.md
- [ ] T052 [US3] Create migration guides for complex patterns requiring special handling

## Phase 6: Validation & Testing

**Goal**: Ensure all changes work correctly and no regressions are introduced

- [ ] T053 [P] Run full Detekt analysis on entire codebase
- [ ] T054 [P] Execute complete build with ./gradlew clean assembleDebug
- [ ] T055 [P] Run unit tests with ./gradlew testDebugUnitTest
- [ ] T056 [P] Run integration tests with ./gradlew connectedDebugAndroidTest
- [ ] T057 [P] Verify zero LambdaParameterInRestartableEffect suppressions remain
- [ ] T058 [P] Verify zero ComposableParamOrder suppressions remain (except documented exceptions)
- [ ] T059 [P] Verify 100% Composable functions follow parameter ordering conventions
- [ ] T060 [P] Validate no functionality regressions through manual testing

## Phase 7: Documentation & Polish

**Goal**: Complete documentation and finalize cleanup process

- [ ] T061 Update suppression tracking spreadsheet with final results
- [ ] T062 Generate cleanup summary report in docs/compose-cleanup-summary.md
- [ ] T063 Document any remaining suppressions with justifications
- [ ] T064 Update project documentation with new coding standards
- [ ] T065 Create migration guide for future Compose development
- [ ] T066 Update Detekt configuration if needed for ongoing enforcement

## Dependencies

### User Story Completion Order
1. **User Story 1** (LambdaParameterInRestartableEffect) - Must complete first as P1 priority
2. **User Story 2** (ComposableParamOrder) - Can start after US1 begins, some parallel work possible
3. **User Story 3** (Complex Cases) - Must wait for US2 completion to identify complex cases

### Parallel Execution Opportunities

**Within User Story 1**:
- Tasks T017-T023 can run in parallel across different modules
- Tasks T024-T026 can run in parallel after suppressions are removed

**Within User Story 2**:
- Tasks T035-T041 can run in parallel across different modules
- Tasks T042-T044 can run in parallel for different function groups

**Cross-Story Parallel**:
- Discovery tasks (T005-T009) can run while US1 cleanup is in progress
- Validation tasks (T053-T060) can run in parallel for different test suites

## Implementation Strategy

### MVP Scope (User Story 1 Only)
Focus on completing LambdaParameterInRestartableEffect removal first:
- Complete tasks T010-T026
- Validate with T053, T057, and T059
- This provides immediate value by fixing potential memory leaks and runtime issues

### Incremental Delivery
1. **Phase 1**: Complete discovery and setup (T001-T009)
2. **Phase 2**: Complete User Story 1 (T010-T026) - MVP delivery
3. **Phase 3**: Complete User Story 2 (T027-T045) - Code quality improvement
4. **Phase 4**: Complete User Story 3 (T046-T052) - Edge case handling
5. **Phase 5**: Complete validation and documentation (T053-T066) - Final delivery

### Risk Mitigation
- **Binary Compatibility**: Use overloads and deprecation for public functions
- **Compilation Failures**: Address immediately with incremental changes
- **Functionality Regression**: Comprehensive testing after each phase
- **Performance Impact**: Benchmark before and after changes

## Success Criteria

- [ ] Zero @Suppress(LambdaParameterInRestartableEffect) annotations remain in codebase
- [ ] Zero @Suppress(ComposableParamOrder) annotations remain (except documented exceptions)
- [ ] All code compiles without warnings related to removed suppressions
- [ ] Zero functionality regressions introduced during cleanup process
- [ ] 100% of Composable functions follow standard parameter ordering conventions
- [ ] Improved code maintainability measured by reduced complexity in Composable functions

## Total Tasks: 66

### Task Distribution by User Story
- **Setup**: 4 tasks (T001-T004)
- **Discovery**: 5 tasks (T005-T009)
- **User Story 1**: 17 tasks (T010-T026)
- **User Story 2**: 19 tasks (T027-T045)
- **User Story 3**: 7 tasks (T046-T052)
- **Validation**: 8 tasks (T053-T060)
- **Documentation**: 6 tasks (T061-T066)

### Parallel Execution Opportunities
- **High**: 35+ tasks can be executed in parallel across modules
- **Medium**: 15+ tasks have some dependencies but limited parallelization
- **Low**: 16 tasks must be sequential due to dependencies

### Estimated Timeline
- **MVP (US1)**: 2-3 days with parallel execution
- **Full Implementation**: 5-7 days with proper validation
- **Buffer Time**: 2 days for unexpected complexity and testing
