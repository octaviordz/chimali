# Feature Specification: Detekt Rules Remediation

**Feature Branch**: `[###-detekt-remediation]`  
**Created**: 2026-05-07  
**Status**: Draft  
**Input**: User description: "Goal enhance code quality by enforcing detek rules use the top 4 issues from analysis document @[specs/detekt_baseline_analysis.md]. We are not going to work on ForbiddenComment at the moment. Goal remove detekt exception from baseline file. Goal update code affected by the detekt rule, enforcing detekt rule, improving code quality. Non goal change busines logic."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Technical Debt Reduction (Priority: P1)

As a maintainer of the codebase, I want to eliminate the top 4 suppressed code smells in the Detekt baseline file (MagicNumber, ClassNaming, BooleanPropertyNaming, SuspendFunWithFlowReturnType) without altering any existing business logic, so that the overall code quality and maintainability are improved.

**Why this priority**: Removing technical debt improves long-term project health, makes the codebase easier to read and maintain, and prevents regressions in code quality standards.

**Independent Test**: Can be fully tested by running the CI linting pipeline and verifying that the `feature\fido2\detekt-baseline-main.xml` no longer contains the addressed rule suppressions, while unit/integration tests continue to pass seamlessly.

**Acceptance Scenarios**:

1. **Given** the current state of `feature\fido2`, **When** the code is refactored to comply with the top 4 Detekt rules, **Then** those suppressions are successfully removed from the baseline file.
2. **Given** the refactored code, **When** all tests are executed, **Then** all tests pass with no changes to the underlying business logic.

### Edge Cases

- What happens if a property is renamed and it was being used across different modules? The renaming must be safely propagated across all consumers of the API.
- What happens if removing the `suspend` modifier from a function returning a `Flow` breaks a downstream caller that was expecting a suspend function? The callers must be updated to correctly handle the standard Flow API.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST be completely free of suppressions for `MagicNumber`, `ClassNaming`, `BooleanPropertyNaming`, and `SuspendFunWithFlowReturnType` in the `feature\fido2\detekt-baseline-main.xml` file.
- **FR-002**: System MUST preserve all existing business logic during the refactoring process.
- **FR-003**: System MUST resolve `MagicNumber` violations by extracting hardcoded values into meaningfully named constants.
- **FR-004**: System MUST resolve `ClassNaming` and `BooleanPropertyNaming` violations by updating names to adhere to standard Kotlin conventions.
- **FR-005**: System MUST resolve `SuspendFunWithFlowReturnType` by removing the redundant `suspend` modifier from functions returning `Flow`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the target suppressions (MagicNumber, ClassNaming, BooleanPropertyNaming, SuspendFunWithFlowReturnType) are removed from the Detekt baseline file.
- **SC-002**: 0 regressions are introduced to the business logic, validated by 100% passing rates in the existing test suite.
- **SC-003**: `ForbiddenComment` violations remain unchanged in the baseline file, as explicitly declared out of scope.

## Assumptions

- Existing automated tests are comprehensive enough to verify that business logic has not changed.
- The `feature\fido2\detekt-baseline-main.xml` is the primary target for remediation.
