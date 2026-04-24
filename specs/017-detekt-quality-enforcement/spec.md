# Feature Specification: Detekt Quality Enforcement: MaxLineLength

**Feature Branch**: `017-detekt-quality-enforcement`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: User description: "Goal remove excludes and enhance code quality such that detekt rule passes ---- excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**'] ---- Star with section, rule MaxLineLength"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enforce MaxLineLength in Test Code (Priority: P1)

As a developer, I want all code—including tests—to follow the project's line length standards, so that the entire codebase remains readable and maintainable without exceptions.

**Why this priority**: Test code often becomes bloated and hard to read. Enforcing the same standards as production code ensures long-term maintainability.

**Independent Test**: Can be fully tested by running `detekt` on the entire project (including test source sets) and verifying that no `MaxLineLength` violations occur.

**Acceptance Scenarios**:

1. **Given** the `MaxLineLength` rule has no excludes, **When** I run `detekt`, **Then** it should not find any lines longer than 120 characters in `test`, `androidTest`, or other test-related source sets.
2. **Given** a line in a test file exceeds 120 characters, **When** I run `detekt`, **Then** the build should fail with a `MaxLineLength` violation.

---

### User Story 2 - Automated Compliance (Priority: P2)

As a developer, I want the codebase to be refactored using standardized patterns to comply with the 120-character limit once the excludes are removed, ensuring maintainability without manual repetition.

**Why this priority**: Removing excludes will immediately trigger violations in existing code. Ensuring a clear path to compliance is necessary for the rule to be active.

**Independent Test**: Verify that after the initial refactoring pass, `detekt` passes project-wide.

**Acceptance Scenarios**:

1. **Given** existing test code that exceeds 120 characters, **When** the refactoring is applied, **Then** all lines should be wrapped or refactored using intermediate variables to fit 120 characters.

---

### Edge Cases

- **How does the system handle multi-platform test source sets?** The excludes cover `commonTest`, `jvmTest`, `jsTest`, `iosTest`, etc. All must be cleared and validated.
- **Are any exclusions allowed?** Yes, exclusions are permitted for special cases such as generated code (e.g., SQLDelight generated classes, build artifacts).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST remove the `excludes` list from the `MaxLineLength` rule in `config/detekt/detekt.yml`.
- **FR-002**: System MUST refactor all Kotlin files in test directories (`**/test/**`, `**/androidTest/**`, etc.) to comply with the 120-character line length limit.
- **FR-003**: The `detekt` check MUST pass successfully for the entire project after the changes.
- **FR-004**: System MUST maintain `excludeRawStrings: true` to avoid breaking valid long raw strings.
- **FR-005**: System MUST NOT introduce logic or semantic changes during refactoring.
- **FR-006**: System MUST apply the Detekt plugin to all subprojects to ensure comprehensive enforcement across the entire multi-module project.
- **FR-007**: System MUST NOT remove any code comments during refactoring.
- **FR-008**: System MUST use string concatenation (`+`) for long strings that do not require multiple lines in their final output, taking advantage of compile-time optimization. Multiline raw strings should only be used if the string logically requires multiple lines.

### Key Entities *(include if feature involves data)*

- **Detekt Configuration**: The `detekt.yml` file defining the project's static analysis rules.
- **Test Source Sets**: The various directories (`test`, `androidTest`, `commonTest`, etc.) that were previously excluded from the rule.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The `MaxLineLength.excludes` property in `config/detekt/detekt.yml` is an empty list `[]`.
- **SC-002**: Project-wide `detekt` execution completes with zero `MaxLineLength` violations.
- **SC-003**: 100% of Kotlin files in the specified test directories are checked by the rule.
- **SC-004**: Automated tests pass post-refactoring, confirming no logic or semantic changes were introduced.

## Assumptions

- **Assumption about line length**: 120 characters is the hard limit for all Kotlin code in the project.
- **Assumption about formatting**: It is acceptable to wrap long lines in tests, even if it slightly changes the visual structure of long assertions or mock data.
- **Assumption about tooling**: The project uses Gradle to run detekt.
- **Assumption about refactoring**: All changes made to comply with line length are purely structural (e.g., line breaks, indentation) and do not alter code logic.
