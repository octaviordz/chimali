# Feature Specification: Detekt Quality Enforcement: MagicNumber

**Feature Branch**: `018-detekt-magic-number`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: User description: "Goal remove excludes and enhance code quality such that detekt rule MagicNumber passes. Remove excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**', '**/*.kts', '**/feature/vault/**']. Start with section, rule MagicNumber"

## Clarifications

### Session 2026-04-24

- **Q**: Handling of .kts (Kotlin Script) files → **A**: Enforce in all .kts files; move version numbers/IDs to named constants.
- **Q**: Specific Generated Code Patterns → **A**: `**/build/generated/**`, `**/*Generated.kt`
- **Q**: Detekt Rule Configuration Tuning → **A**: Tune settings: Expand `ignoreNumbers` to include common SDK versions (e.g., 17, 21, 24, 31, 33, 34, 35) and set `ignoreAnnotation`, `ignoreEnums`, and `ignoreRanges` to `true`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enforce MagicNumber Project-Wide (Priority: P1)

As a developer, I want all code—including tests, build scripts, and feature modules—to follow the project's magic number standards, so that the entire codebase remains readable and maintainable without exceptions.

**Why this priority**: Magic numbers obscure intent and make maintenance difficult. Enforcing this across all source sets ensures that every number has a clear, named purpose.

**Independent Test**: Can be fully tested by running `detekt` on the entire project (including tests and build scripts) and verifying that no `MagicNumber` violations occur.

**Acceptance Scenarios**:

1. **Given** the `MagicNumber` rule has no excludes, **When** I run `detekt`, **Then** it should not find any magic numbers in `test`, `androidTest`, `.kts` files, or the `vault` feature.
2. **Given** a new magic number is introduced in a test file, **When** I run `detekt`, **Then** the build should fail with a `MagicNumber` violation.

---

### User Story 2 - Automated Compliance (Priority: P2)

As a developer, I want the codebase to be refactored using named constants to comply with the `MagicNumber` rule once the excludes are removed, ensuring maintainability without manual repetition.

**Why this priority**: Removing excludes will immediately trigger violations. A systematic refactoring ensures the rule can be activated without breaking the build indefinitely.

**Independent Test**: Verify that after the initial refactoring pass, `detekt` passes project-wide.

**Acceptance Scenarios**:

1. **Given** existing code with magic numbers in previously excluded paths, **When** the refactoring is applied, **Then** all magic numbers should be replaced with descriptive constants.

---

### Edge Cases

- **How does the system handle build scripts (.kts)?** Build scripts often contain versions or configuration values. These should be moved to constants or acknowledged as exceptions if they meet Detekt's ignore criteria (e.g., 0, 1, 2).
- **Are any exclusions allowed?** Yes, exclusions are permitted for generated code (specifically `**/build/generated/**` and `**/*Generated.kt`) and those already defined in `ignoreNumbers` (e.g., -1, 0, 1, 2).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST remove the specified `excludes` list from the `MagicNumber` rule in `config/detekt/detekt.yml`, while retaining or adding exclusions for generated code (`**/build/generated/**`, `**/*Generated.kt`).
- **FR-002**: System MUST refactor all Kotlin files in the previously excluded directories (`**/test/**`, `**/androidTest/**`, `**/*.kts`, `**/feature/vault/**`, etc.) to comply with the `MagicNumber` rule.
- **FR-003**: The `detekt` check MUST pass successfully for the entire project after the changes.
- **FR-004**: System MUST NOT introduce logic or semantic changes during refactoring.
- **FR-005**: System MUST NOT remove any code comments during refactoring.
- **FR-006**: System MUST use descriptive constant names that reflect the intent of the number (e.g., `MAX_RETRY_COUNT` instead of `TEN`).
- **FR-007**: System MUST update the `MagicNumber` configuration to include common SDK versions in `ignoreNumbers` and set `ignoreAnnotation`, `ignoreEnums`, and `ignoreRanges` to `true`.

### Key Entities *(include if feature involves data)*

- **Detekt Configuration**: The `detekt.yml` file defining the project's static analysis rules.
- **Source Sets**: All project files, including production code, tests, and build scripts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The specified `MagicNumber.excludes` paths are removed from `config/detekt/detekt.yml`.
- **SC-002**: Project-wide `detekt` execution completes with zero `MagicNumber` violations.
- **SC-003**: 100% of Kotlin and KTS files are checked by the rule.

## Assumptions

- **Assumption about constants**: It is acceptable to add private or internal constants to files to resolve violations.
- **Assumption about build scripts**: Magic numbers in `build.gradle.kts` files are subject to the same enforcement unless they are already ignored by Detekt's default behavior for such files (if any).
- **Assumption about vault feature**: The `vault` feature was previously excluded and now requires full compliance.
