# Feature Specification: Detekt Rules Upgrade and Enforcement

**Feature Branch**: `028-detekt-rules-upgrade`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: User description: "Create a specification with the goal of enhance code quality. Goal review next [1] detekt configuration, rules, analyze current@[config/detekt/detekt.yml], add, update configuration based on [1] when best practices for code quality are uphold. Goal remove rule's supress '@Supress(\"{RULENAME}\")' where 'RULENAME' is a rule name from [1]. Goal fix errors, and warning caused by removal of suppress. Non goal make logic changes."

## Clarifications

### Session 2026-04-29
- **Q: MagicNumber Exception Strategy** → **A: Strict Zero Literal policy**, preserving only the existing `ignoreNumbers` (-1, 0, 1, 2) defined in `config/detekt/detekt.yml`. All other numeric literals MUST be extracted to constants.
- **Q: LongMethod Refactoring Priority** → **A: Prioritize private helper functions** in the same file/class to minimize architectural risk.

## Configuration Reference: Expert Detekt Rules

The following configuration defines the expert-level rules and thresholds to be enforced:

```yaml
# Detekt configuration for Android Kotlin Expert Rules
complexity:
  active: true
  # 3. Prevent spaghetti logic
  CyclomaticComplexity:
    active: true
    threshold: 40
  # 4. Enforce concise functions and constructors
  LongMethod:
    active: true
    threshold: 400
  LongParameterList:
    active: true
    threshold: 12
  # 6. Combat "God Objects"
  TooManyFunctions:
    active: true
    threshold: 40
  LargeClass:
    active: true
    threshold: 600

coroutines:
  active: true
  # 1. Prevent memory leaks from uncontrolled scopes
  GlobalCoroutineUsage:
    active: true

exceptions:
  active: true
  # 5. Prevent silent failures
  SwallowedException:
    active: true

style:
  active: true
  # 7. Keep hierarchy simple
  UnnecessaryAbstractClass:
    active: true
  # 8. Use constants/resources instead of literals
  MagicNumber:
    active: true
    ignoreNumbers: ['-1', '0', '1', '2']
    ignoreConstantDeclaration: true
    ignoreAnnotation: true
  # 9. Clearer dependency tracking
  WildcardImport:
    active: true

performance:
  active: true
  # 10. Avoid unnecessary array copies in hot paths
  SpreadOperator:
    active: true
```

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Expert Detekt Rules Configuration (Priority: P1)

As a developer, I want to upgrade the project's static analysis configuration to use expert-level thresholds and rules so that the codebase maintains a high standard of quality and maintainability.

**Why this priority**: Establishing the rules is the foundation for all subsequent quality improvements and enforcement.

**Independent Test**: Can be tested by running Detekt with the new configuration and verifying that it identifies violations based on the new thresholds (e.g., methods longer than 40 lines).

**Acceptance Scenarios**:

1. **Given** the current `detekt.yml`, **When** I update the configuration with the rules from the **Configuration Reference**, **Then** all specified rules (Complexity, Coroutines, Exceptions, Style, Performance) MUST be active with the provided thresholds.
2. **Given** a method with 50 lines, **When** `LongMethod` threshold is set to 40, **Then** Detekt MUST report a violation for that method.

---

### User Story 2 - Suppression Removal and Exposure (Priority: P2)

As a developer, I want to remove all existing `@Suppress` annotations for the upgraded rules so that all hidden technical debt is exposed for resolution.

**Why this priority**: Suppressions hide quality issues; removing them is necessary to ensure the new rules are actually enforced project-wide.

**Independent Test**: Can be tested by searching for `@Suppress("MagicNumber")`, `@Suppress("LongMethod")`, etc., and verifying none remain in the codebase for the target rules.

**Acceptance Scenarios**:

1. **Given** the codebase contains `@Suppress` annotations, **When** I run the enforcement task for the rules in the **Configuration Reference**, **Then** all such annotations for the target rules MUST be removed.
2. **Given** a file where a suppression was removed, **When** I run Detekt, **Then** it MUST now report the previously suppressed violations.

---

### User Story 3 - Automated Quality Remediation (Priority: P3)

As a developer, I want the exposed quality violations to be fixed automatically or semi-automatically without changing any business logic, so that the build stays green and the code is cleaner.

**Why this priority**: Fixing the violations is the ultimate goal of the upgrade, but it must be done safely to avoid regressions.

**Independent Test**: Can be tested by running `local-ci.ps1` and ensuring it passes after suppressions are removed and fixes are applied.

**Acceptance Scenarios**:

1. **Given** a violation of `MagicNumber`, **When** I apply fixes, **Then** the literal MUST be replaced by a named constant or resource without changing the logic.
2. **Given** a `LongMethod` violation, **When** I refactor, **Then** the method MUST be decomposed into smaller units that maintain the same external behavior.

---

### Edge Cases

- **Conflicting Rules**: If a new rule from [1] conflicts with an existing project rule, the expert rule from [1] takes precedence.
- **Nested Suppressions**: If multiple rules are suppressed (e.g., `@Suppress("MagicNumber", "UnusedPrivateMember")`), only the target rule from [1] should be removed from the list.
- **Third-party/Generated Code**: Rules should continue to respect existing `excludes` for generated code to avoid build failures in code we don't control.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST update `config/detekt/detekt.yml` to match the configuration provided in the **Configuration Reference** section.
- **FR-002**: System MUST identify all rules mentioned in the **Configuration Reference**.
- **FR-003**: System MUST remove `@Suppress` annotations for the rules identified in FR-002 across all `.kt` files.
- **FR-004**: System MUST fix all newly exposed violations.
- **FR-005**: **Strict MagicNumber Policy**: All numeric literals not in the global ignore list (-1, 0, 1, 2) MUST be extracted to named constants.
- **FR-006**: **Refactoring Priority**: Decompose long methods into private helper functions within the same class/file.
- **FR-007**: All refactoring MUST maintain original logic and behavior (Non-goal: logic changes).
- **FR-008**: System MUST verify that `local-ci.ps1` (specifically detekt) passes after all changes.

### Key Entities *(include if feature involves data)*

- **Detekt Configuration**: The central `detekt.yml` file defining rules and thresholds.
- **Rule Suppression**: Inline annotations like `@Suppress("MagicNumber")` used to bypass static analysis.
- **Quality Violation**: A specific instance where code fails to meet the criteria defined in the Detekt configuration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the expert quality rules and thresholds defined in the **Configuration Reference** are implemented in the configuration.
- **SC-002**: Zero instances of manual rule suppressions remain in the codebase for the rules defined in the **Configuration Reference**.
- **SC-003**: The project's automated quality checks pass with zero reported violations.
- **SC-004**: All automated tests pass, confirming no logic changes were introduced during refactoring.

## Assumptions

- The project uses a Detekt version compatible with the rule names provided (or I will map them to the closest standard equivalent).
- `MagicNumber` fixes will prioritize extracting constants to the nearest appropriate scope (companion object, file level, or class level).
- `LongMethod` and `LargeClass` fixes will involve structural refactoring (extracting methods/classes) rather than deleting code.
- `SwallowedException` fixes will involve logging the exception or rethrowing it as appropriate for the context.
