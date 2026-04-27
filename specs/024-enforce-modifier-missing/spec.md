# Feature Specification: Enforce Modifier Missing

**Feature Branch**: `024-enforce-modifier-missing`  
**Created**: 2026-04-27  
**Status**: Draft  
**Input**: User description: "enhance code quality by enforcing compose-rule ModifierMissing. Remove of suppressions @Suppress("ModifierMissing"). And implementation of best practice solution. Non goal change code logic."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Developer Receives Immediate Feedback (Priority: P1)

As a developer, I want my IDE and CI pipeline to flag any composable function that does not accept a Modifier parameter (where applicable), so that I can catch bad design practices early without needing a code review.

**Why this priority**: It establishes the foundation for enforcing the lint rule directly at development and CI time.

**Independent Test**: Can be tested by creating a dummy composable without a `modifier` parameter, verifying that the lint check catches it both locally and on CI.

**Acceptance Scenarios**:

1. **Given** a new composable function is written without a `modifier` parameter, **When** the developer runs Detekt or Android Lint, **Then** a `ModifierMissing` violation is raised.
2. **Given** an existing composable function has `@Suppress("ModifierMissing")`, **When** a lint check is run after this rule enforcement, **Then** we should either see that the suppression was removed and the code fixed, or the lint rules enforce this pattern globally.

---

### User Story 2 - Implement Best Practice Modifiers (Priority: P2)

As a developer, I want all existing UI composables to be updated to accept a `modifier` parameter and use it as the first parameter to the root layout element, so that my components are reusable and follow official Jetpack Compose guidelines.

**Why this priority**: It ensures that existing technical debt related to the `ModifierMissing` rule is fully resolved following industry best practices.

**Independent Test**: Can be tested by inspecting the previously suppressed composable functions and verifying they now accept a `modifier: Modifier = Modifier` and pass it to their root UI node.

**Acceptance Scenarios**:

1. **Given** an existing composable function that previously suppressed the `ModifierMissing` rule, **When** the developer updates the code, **Then** the function must expose a `modifier` parameter with a default value of `Modifier`.
2. **Given** the updated composable function, **When** it renders its root layout, **Then** it must apply the passed `modifier` parameter correctly.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST flag `ModifierMissing` violations during static analysis (Detekt/Android Lint).
- **FR-002**: System MUST NOT contain any `@Suppress("ModifierMissing")` annotations in the codebase.
- **FR-003**: System MUST provide a `modifier: Modifier = Modifier` parameter for all public or internal composable functions that emit UI.
- **FR-004**: System MUST apply the passed `modifier` parameter to the root element of the composable function.
- **FR-005**: System MUST preserve existing code logic while only refactoring the composable signatures and root modifier usage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of `@Suppress("ModifierMissing")` annotations are removed from the project.
- **SC-002**: 100% of the previously suppressed composable functions now pass the `ModifierMissing` lint rule without suppression.
- **SC-003**: The CI pipeline successfully runs and passes all static analysis checks, verifying no regressions or missing modifiers.
- **SC-004**: Zero changes to the underlying logic or behavior of the updated composable components.

## Assumptions

- We assume that adding a default `modifier: Modifier = Modifier` parameter does not negatively impact binary compatibility or reflection in ways that affect current workflows.
- We assume the codebase is already configured with Compose Detekt rules, and this task is solely about enforcing the `ModifierMissing` rule and refactoring non-compliant code.
