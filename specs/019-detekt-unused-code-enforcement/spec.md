# Feature Specification: detekt-unused-code-enforcement

**Feature Branch**: `019-detekt-unused-code-enforcement`
**Created**: 2026-04-24
**Status**: Draft
**Input**: User description: "Goal create specification to enhance code quality by enformcent of detekt rules, UnusedPrivateMember, and UnusedPrivateProperty. Remove excludes @[d:\octav\source\repos\Chimali\config\detekt\detekt.yml:L747-L756]. Allow exclues for generated code."


## Clarifications

### Session 2026-04-24
- Q: Should we include additional specific path patterns for common Kotlin generators? → A: Standard patterns only (`**/build/**`, `**/generated/**`).
- Q: How should developers handle intentional unused members? → A: Use `@Suppress` ONLY after analysis; if no stated reason or template requirement exists, unused code MUST be removed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enforce Clean Code in Tests (Priority: P1)

As a developer, I want Detekt to flag unused private members and properties in my test code, so that the test suites remain maintainable and free of dead code.

**Why this priority**: Removing excludes for tests ensures that the same quality standards apply to the entire codebase, reducing clutter in the test suite.

**Independent Test**: Can be fully tested by introducing an unused private variable in a test file and verifying that `detekt` flags it.

**Acceptance Scenarios**:

1. **Given** a Kotlin test file, **When** an unused private property is added, **Then** Detekt MUST report an `UnusedPrivateProperty` violation.
2. **Given** a Kotlin test file, **When** an unused private function is added, **Then** Detekt MUST report an `UnusedPrivateMember` violation.

---

### User Story 2 - Maintain Generated Code Compatibility (Priority: P2)

As a developer, I want Detekt to ignore unused private elements in generated code, so that third-party library generation doesn't block the build with violations I cannot fix.

**Why this priority**: Generated code is often out of the developer's control and may contain unused elements by design or necessity.

**Independent Test**: Can be verified by running Detekt on a project with generated code (e.g., KSP or Dagger) and ensuring no violations are reported from those paths.

**Acceptance Scenarios**:

1. **Given** code in a generated directory (e.g., `**/build/generated/**`), **When** Detekt runs, **Then** it MUST NOT report unused private violations for those files.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST ensure `UnusedPrivateMember` rule is active and apply it to test source sets by removing overrides.
- **FR-002**: System MUST ensure `UnusedPrivateProperty` rule is active and apply it to test source sets by removing overrides.
- **FR-003**: System MUST remove existing broad excludes (e.g., `**/test/**`, `**/androidTest/**`) for both `UnusedPrivateMember` and `UnusedPrivateProperty`.
- **FR-004**: System MUST configure `excludes` for these rules to specifically include standard generated code paths: `**/build/**` and `**/generated/**`.
- **FR-005**: Developers MUST remove unused private code unless a stated reason (via comments) or template requirement exists; in such cases, `@Suppress` MUST be used.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Running `./gradlew detekt` (or local CI script) identifies all unused private members and properties across production and test source sets.
- **SC-002**: No violations are reported for files located within standard generated code directories (`**/build/**` and `**/generated/**`).
- **SC-003**: The CI pipeline successfully blocks commits containing new unused private members or properties in non-generated code.

## Assumptions

- "Generated code" is primarily located under `**/build/**` or `**/generated/**` directories.
- Existing violations in the codebase will be resolved or suppressed individually if they are intentional, rather than using broad excludes.
- The project uses a centralized `detekt.yml` configuration.
