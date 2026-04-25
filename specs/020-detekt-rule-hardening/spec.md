# Feature Specification: Detekt Rule Hardening

**Feature Branch**: `020-detekt-rule-hardening`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: User description: "enhance code quality by enforcing WildcardImport, UnusedImports, NewLineAtEndOfFile, UnsafeCallOnNullableType, LateinitUsage, and EmptyDefaultConstructor detekt rules. Remove excludes, allow exclude for generated code"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automated Quality Gate Enforcement (Priority: P1)

As a developer, I want my code to be automatically checked for specific quality violations during local CI so that I can maintain high standards and prevent technical debt from entering the main codebase.

**Why this priority**: High. These rules represent fundamental code quality standards that should be enforced across the entire project to ensure consistency and prevent common errors (like unsafe null calls).

**Independent Test**: Can be fully tested by introducing a violation (e.g., a wildcard import) and running the local CI script. The build should fail with a clear detekt violation message.

**Acceptance Scenarios**:

1. **Given** a Kotlin file with a wildcard import, **When** running detekt analysis, **Then** the build MUST fail with a `WildcardImport` violation.
2. **Given** a Kotlin file with an unused import, **When** running detekt analysis, **Then** the build MUST fail with an `UnusedImports` violation.
3. **Given** a Kotlin file missing a newline at the end, **When** running detekt analysis, **Then** the build MUST fail with a `NewLineAtEndOfFile` violation.
4. **Given** a Kotlin file using `!!` on a nullable type, **When** running detekt analysis, **Then** the build MUST fail with an `UnsafeCallOnNullableType` violation.
5. **Given** a production Kotlin class using `lateinit var`, **When** running detekt analysis, **Then** the build MUST fail with a `LateinitUsage` violation. (Note: Test source sets may be excluded per FR-005).
6. **Given** a Kotlin class with an explicit empty default constructor, **When** running detekt analysis, **Then** the build MUST fail with an `EmptyDefaultConstructor` violation.

---

### User Story 2 - Generated Code Compatibility (Priority: P2)

As a developer, I want detekt to ignore violations in generated code so that I don't have to manually fix issues in files created by external tools or compilers.

**Why this priority**: Medium. Necessary to avoid false positives and build failures caused by code outside the developer's direct control.

**Independent Test**: Can be tested by running detekt on a project containing generated files (e.g., Room or BuildKonfig) that might violate these rules. The build should pass if those files are correctly excluded.

**Acceptance Scenarios**:

1. **Given** a generated Kotlin file (e.g., in a `build/generated` path), **When** it contains a violation (like a wildcard import), **Then** detekt MUST ignore it and the build MUST pass.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST enable and enforce the `WildcardImport` rule globally.
- **FR-002**: System MUST enable and enforce the `UnusedImports` rule globally.
- **FR-003**: System MUST enable and enforce the `NewLineAtEndOfFile` rule globally.
- **FR-004**: System MUST enable and enforce the `UnsafeCallOnNullableType` rule globally.
- **FR-005**: System MUST enable and enforce the `LateinitUsage` rule globally, with an exception for test source sets (e.g., `**/test/**`, `**/androidTest/**`) to support idiomatic test fixtures.
- **FR-006**: System MUST enable and enforce the `EmptyDefaultConstructor` rule globally.
- **FR-007**: System MUST remove all existing `excludes` patterns from the configuration for these specific rules, except for generated code paths.
- **FR-008**: System MUST configure a global `excludes` pattern for generated code (e.g., `**/build/**`) that applies to all rules.
- **FR-009**: System SHOULD allow excluding `.kts` files from analysis if they contain a high volume of violations (>20) that present a disproportionate refactoring effort.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of non-generated Kotlin source files in the project pass the new detekt rules without manual exclusions.
- **SC-002**: The `tools/local-ci.ps1` script completes successfully on a clean codebase with zero detekt violations.
- **SC-003**: Build failure is triggered within 30 seconds of introducing a rule violation in a production file.

## Assumptions

- **Existing Violations**: It is assumed that existing violations in the codebase will be refactored as part of the implementation.
- **Generated Code Identification**: It is assumed that generated code can be reliably identified by path (e.g., containing `build/generated`).
- **Build Scripts**: It is assumed that `.kts` files may be excluded from certain rules if they present a disproportionate refactoring effort compared to their impact on production code quality.
- **Tooling**: It is assumed that Detekt is already correctly integrated into the Gradle build system.
- **Test Lifecycle**: It is assumed that `lateinit` is the idiomatic and acceptable way to handle test fixture initialization in `kotlin.test` and MockK setups where constructor injection is not available.
