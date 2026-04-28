# Feature Specification: Compose Rule Enforcement

**Feature Branch**: `025-compose-rule-enforcement`  
**Created**: 2026-04-27  
**Status**: Draft  
**Input**: User description: "improve code quality by enforcing compose-rule. Remove '@Suppress(LambdaParameterInRestartableEffect)' , Remove '@Suppress(ComposableParamOrder)' and if possible, per case basis, remove '@Suppress(ComposableParamOrder)'. Remove them across all code base. Non goal no logic changes, no new features."

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Remove LambdaParameterInRestartableEffect Suppressions (Priority: P1)

As a developer, I want all `@Suppress(LambdaParameterInRestartableEffect)` annotations removed from the codebase so that the code follows proper Compose conventions and potential issues are not hidden.

**Why this priority**: This is the highest priority because LambdaParameterInRestartableEffect suppressions can hide potential runtime issues and memory leaks in restartable effects, which are critical for app stability.

**Independent Test**: Can be fully tested by running static analysis tools to verify no LambdaParameterInRestartableEffect suppressions remain and that all Compose code compiles without warnings.

**Acceptance Scenarios**:

1. **Given** the codebase contains `@Suppress(LambdaParameterInRestartableEffect)` annotations, **When** the cleanup process runs, **Then** all such annotations are removed and any underlying issues are properly addressed
2. **Given** a Composable function with restartable effects, **When** LambdaParameterInRestartableEffect suppression is removed, **Then** the code still compiles and follows proper parameter naming conventions

---

### User Story 2 - Remove ComposableParamOrder Suppressions (Priority: P2)

As a developer, I want all `@Suppress(ComposableParamOrder)` annotations removed from the codebase so that Composable functions follow the standard parameter ordering conventions.

**Why this priority**: This is medium priority because while it doesn't affect functionality, maintaining consistent parameter order improves code readability and maintainability across the team.

**Independent Test**: Can be fully tested by verifying that all Composable functions have parameters in the correct order (content, modifiers, then other parameters) and compile without warnings.

**Acceptance Scenarios**:

1. **Given** the codebase contains `@Suppress(ComposableParamOrder)` annotations, **When** the cleanup process runs, **Then** all such annotations are removed and parameters are reordered correctly
2. **Given** a Composable function with non-standard parameter order, **When** the suppression is removed, **Then** the function parameters are reordered to follow Compose conventions

---

### User Story 3 - Case-by-case ComposableParamOrder Evaluation (Priority: P3)

As a developer, I want complex cases with `@Suppress(ComposableParamOrder)` to be evaluated individually so that we can determine if the suppression is truly necessary or if the code can be refactored to follow conventions.

**Why this priority**: This is lower priority because it requires careful manual evaluation of each case to ensure we don't break functionality while enforcing the rules.

**Independent Test**: Can be fully tested by manually reviewing each complex case and verifying that either the suppression is removed with proper refactoring or documented justification is provided for keeping it.

**Acceptance Scenarios**:

1. **Given** a complex Composable with ComposableParamOrder suppression, **When** evaluated case-by-case, **Then** either the suppression is removed with proper refactoring or documented justification is provided
2. **Given** refactored Composable functions, **When** tested, **Then** they maintain the same functionality while following proper parameter ordering

### Edge Cases

- What happens when removing suppressions reveals underlying code issues that require significant refactoring?
- How does system handle cases where parameter reordering would break binary compatibility?
- What happens when suppressed warnings were masking legitimate design patterns that don't fit standard conventions?
- How does system handle generated code or third-party library code that contains suppressions?
- What happens when removing suppressions causes compilation failures due to circular dependencies?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST remove all `@Suppress(LambdaParameterInRestartableEffect)` annotations from the codebase
- **FR-002**: System MUST remove all `@Suppress(ComposableParamOrder)` annotations from the codebase
- **FR-003**: System MUST evaluate complex ComposableParamOrder cases on a case-by-case basis before removal
- **FR-004**: System MUST ensure all code compiles successfully after suppressions are removed
- **FR-005**: System MUST maintain existing functionality without introducing logic changes
- **FR-006**: System MUST properly reorder Composable function parameters to follow standard conventions (content, modifiers, other parameters)
- **FR-007**: System MUST address underlying issues that were previously hidden by suppressions
- **FR-008**: System MUST document any cases where suppressions cannot be removed with clear justification

### Key Entities

- **Suppression Annotation**: Code annotations that disable specific compiler warnings or lint rules
- **LambdaParameterInRestartableEffect**: Detekt/Compose rule that ensures proper parameter naming in restartable effects
- **ComposableParamOrder**: Detekt/Compose rule that enforces standard parameter ordering in Composable functions
- **Composable Function**: Jetpack Compose UI components that follow specific conventions
- **Static Analysis Tools**: Tools like Detekt that enforce code quality rules

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Zero `@Suppress(LambdaParameterInRestartableEffect)` annotations remain in the codebase
- **SC-002**: Zero `@Suppress(ComposableParamOrder)` annotations remain in the codebase (except documented exceptions)
- **SC-003**: 100% of Composable functions follow standard parameter ordering conventions
- **SC-004**: All code compiles without warnings related to the removed suppressions
- **SC-005**: Zero functionality regressions introduced during the cleanup process
- **SC-006**: Improved code maintainability measured by reduced complexity in Composable functions

## Assumptions

- The existing codebase has Jetpack Compose components with various suppress annotations
- Static analysis tools (Detekt) are configured to detect the suppressed rules
- Development team has capacity to review and test changes after suppressions are removed
- Build system will properly flag any issues that emerge after suppressions are removed
- Code follows standard Kotlin/Compose patterns that can be refactored to meet conventions
- No new features or logic changes are required as part of this cleanup effort
- The codebase is under version control to allow for careful review of changes
