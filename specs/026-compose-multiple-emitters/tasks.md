# Implementation Tasks: Compose MultipleEmitters Rule Enforcement

**Branch**: `026-compose-multiple-emitters` | **Date**: 2026-04-27  
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Phase 1: Setup

**Goal**: Prepare development environment and validate prerequisites

**Independent Test**: All development tools are configured and MultipleEmitters rule is active

### Setup Tasks

- [X] T001 Verify Detekt MultipleEmitters rule is active in config/detekt/detekt.yml
- [X] T002 Run baseline static analysis to identify current MultipleEmitters violations
- [X] T003 Create development documentation directory docs/development/
- [X] T004 Backup current DevelopmentToolsScreen.kt for comparison testing

## Phase 2: Foundational

**Goal**: Establish core infrastructure for MultipleEmitters enforcement

**Independent Test**: Static analysis pipeline detects violations and provides clear reporting

### Foundational Tasks

- [X] T005 Validate existing local-ci.ps1 script runs Detekt successfully
- [X] T006 Test current MultipleEmitters violations detection in DevelopmentToolsScreen.kt
- [X] T007 Create compliance tracking mechanism for violation resolution
- [X] T008 Configure Android Studio Detekt plugin for real-time MultipleEmitters feedback
- [X] T009 Verify Ktlint configuration supports Compose rules in .editorconfig
- [X] T010 Test Ktlint detects code style issues in Compose functions
- [X] T011 Ensure Ktlint and Detekt rules don't conflict for MultipleEmitters
- [X] T012 Set up IDE code inspections for MultipleEmitters rule violations
- [X] T013 Create IDE live templates for proper Compose single-emission patterns
- [X] T014 Configure IDE quick fix actions for common MultipleEmitters refactoring patterns

## Phase 3: User Story 1 - Remove MultipleEmitters Suppressions

**Goal**: Eliminate all existing MultipleEmitters suppressions and fix violations

**Independent Test**: Static analysis runs with zero suppressions and zero violations

### US1 Tasks

- [X] T015 [US1] Remove @Suppress("MultipleEmitters") from DebugMnemonicSection function in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt
- [X] T016 [US1] Refactor DebugMnemonicSection to use single content emission pattern
- [X] T017 [US1] Remove @Suppress("MultipleEmitters") from RecoverSeedForm function in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt
- [X] T018 [US1] Refactor RecoverSeedForm to use single content emission pattern
- [X] T019 [US1] Remove @Suppress("MultipleEmitters") from ManualMnemonicEntryForm function in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt
- [X] T020 [US1] Refactor ManualMnemonicEntryForm to use single content emission pattern
- [X] T021 [US1] Remove @Suppress("MultipleEmitters") from AlgorithmSelector function in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt
- [X] T022 [US1] Refactor AlgorithmSelector to use single content emission pattern
- [X] T023 [US1] Remove @Suppress("MultipleEmitters") from TestRegistrationTrigger function in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt
- [X] T024 [US1] Refactor TestRegistrationTrigger to use single content emission pattern
- [X] T025 [P] [US1] Verify all refactored functions maintain existing functionality
- [X] T026 [US1] Run static analysis to confirm zero MultipleEmitters suppressions remain
- [X] T027 [US1] Test refactored DevelopmentToolsScreen functionality with UI tests

## Phase 4: User Story 2 - Enforce MultipleEmitters Rule in CI/CD

**Goal**: Integrate MultipleEmitters enforcement into build pipeline

**Independent Test**: CI pipeline fails on MultipleEmitters violations and passes on compliant code

### US2 Tasks

- [X] T028 [US2] Enhance tools/local-ci.ps1 to fail build on MultipleEmitters violations
- [X] T029 [US2] Update pre-commit git hooks to check for MultipleEmitters suppressions
- [X] T030 [US2] Configure Detekt to treat MultipleEmitters as error (not warning)
- [X] T031 [US2] Add clear error messaging for MultipleEmitters violations in CI output
- [X] T032 [US2] Test CI failure by introducing temporary MultipleEmitters violation
- [X] T033 [US2] Validate CI passes with compliant Compose code
- [X] T034 [US2] Document CI enforcement process in project README
- [X] T035 [US2] Enhance tools/local-ci.ps1 to run both Detekt and Ktlint for MultipleEmitters enforcement

## Phase 5: User Story 3 - Developer Education and Documentation

**Goal**: Provide comprehensive guidance for MultipleEmitters compliance

**Independent Test**: Developers can reference documentation to avoid violations

### US3 Tasks

- [X] T036 [US3] Create comprehensive Compose patterns guide in docs/development/compose-patterns.md
- [X] T037 [US3] Document refactoring strategies with before/after examples
- [X] T038 [US3] Create IDE quick fix templates for common MultipleEmitters patterns
- [X] T039 [US3] Add MultipleEmitters section to code review checklist
- [X] T040 [US3] Update project onboarding documentation with MultipleEmitters guidance
- [X] T041 [US3] Create troubleshooting guide for complex refactoring scenarios
- [X] T042 [US3] Validate documentation completeness with peer review

## Phase 6: Polish & Cross-Cutting Concerns

**Goal**: Ensure robust enforcement and long-term maintainability

**Independent Test**: Complete enforcement system with monitoring and reporting

### Polish Tasks

- [ ] T043 Create compliance metrics dashboard for MultipleEmitters enforcement
- [ ] T044 Set up automated compliance reporting (weekly/monthly)
- [ ] T045 Add MultipleEmitters compliance to project health metrics
- [ ] T046 Create rollback plan for enforcement issues
- [ ] T047 Document exception handling process (though no exceptions allowed per clarification)
- [ ] T048 [SC-004] Implement baseline compliance measurement for MultipleEmitters violations
- [ ] T049 [SC-004] Create automated tracking of violation reduction over time
- [ ] T050 [SC-005] Implement code review time measurement before/after MultipleEmitters enforcement
- [ ] T051 [SC-005] Set up dashboard for code review efficiency metrics
- [ ] T052 Validate performance impact of refactored code (60 FPS targets)
- [ ] T053 Final integration testing of complete enforcement system

## Dependencies

### Story Completion Order

1. **User Story 1** (Remove Suppressions) - BLOCKS: US2, US3
2. **User Story 2** (CI/CD Enforcement) - DEPENDS: US1
3. **User Story 3** (Developer Education) - DEPENDS: US1

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) + Phase 5 (US3) → Phase 6
```

## Parallel Execution Opportunities

### Within User Story 1
- Tasks T015-T024 can be executed in parallel (different functions)
- Task T025 can run in parallel with T026 after refactoring is complete

### Within User Story 2
- Tasks T028-T034 can be executed in parallel (different CI components)
- Tasks T032-T033 can be executed in parallel (testing scenarios)

### Within User Story 3
- Tasks T036-T042 can be executed in parallel (different documentation components)

### Cross-Story Parallelism
- Phase 6 (Polish) tasks can begin as soon as their dependent stories are complete

## Implementation Strategy

### MVP Scope (User Story 1 Only)
- Focus on removing existing suppressions and fixing violations
- Basic CI enforcement to prevent regressions
- Minimal documentation for immediate team needs

### Incremental Delivery
1. **Week 1**: Complete User Story 1 (remove suppressions)
2. **Week 2**: Complete User Story 2 (CI/CD integration)
3. **Week 3**: Complete User Story 3 (documentation and education)
4. **Week 4**: Polish and monitoring

### Risk Mitigation
- Test each refactored function independently
- Maintain backup of original code for rollback
- Gradual CI integration to avoid blocking all development
- Peer review of all refactoring changes

## Success Metrics

### Quantitative
- Zero MultipleEmitters suppressions in codebase
- 100% CI pass rate on compliant code
- < 5 minutes additional CI processing time

### Qualitative
- Developer confidence in Compose patterns
- Reduced code review time for MultipleEmitters issues
- Clear documentation and guidance availability

## Notes

- All tasks follow the zero-tolerance policy clarified in specification
- IDE integration focus on real-time feedback and quick fixes
- Performance validation ensures 60 FPS targets are maintained
- No exceptions allowed - all violations must be fixed
