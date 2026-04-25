# Feature Specification: Detekt Quality Enhancements

**Feature Branch**: `021-detekt-quality-enhancements`  
**Created**: 2026-04-25  
**Status**: Draft  
**Input**: User description: "Enhance Detekt configuration to enforce logging discipline, idiomatic scope functions, and string literal management."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prevent Standard Output in Production (Priority: P1)

As a developer, I want the build to fail if I accidentally leave `println` or `print` statements in the code, so that our production logs remain clean and use only our structured logging solution (e.g., Kermit).

**Why this priority**: Production logs must be structured and manageable. Standard output bypasses logging configurations and can leak information or clutter logs.

**Independent Test**: Can be fully tested by adding a `println` statement to a production file and verifying that the Detekt task fails with a specific error.

**Acceptance Scenarios**:

1. **Given** a production Kotlin file, **When** it contains a `println("debug message")` call, **Then** Detekt must report a violation.
2. **Given** a production Kotlin file, **When** it uses the project's approved logger (e.g., `Logger.d { ... }`), **Then** Detekt must not report a violation.

---

### User Story 2 - Idiomatic Scope Function Usage (Priority: P2)

As a developer, I want Detekt to flag unnecessary or non-idiomatic use of scope functions (like `let`), so that the codebase remains readable and follows Kotlin best practices.

**Why this priority**: Improper use of `let` (e.g., when not needed for null-safety or as a simple alternative to `if`) increases cognitive load and boilerplate.

**Independent Test**: Can be tested by adding a redundant `.let { it.doSomething() }` on a non-nullable object and verifying that Detekt flags it.

**Acceptance Scenarios**:

1. **Given** a non-nullable variable, **When** it is followed by an unnecessary `.let`, **Then** Detekt must suggest removing it.
2. **Given** a code block that can be simplified using `UseLet`, **When** analyzed, **Then** Detekt must flag it for refactoring.

---

### User Story 3 - String Literal Duplication Detection (Priority: P3)

As a developer, I want Detekt to identify cases where the same string literal is used multiple times, so that I can extract them into named constants for better maintainability.

**Why this priority**: Duplicated strings ("magic strings") make refactoring difficult and can lead to bugs if one instance is updated but others are missed.

**Independent Test**: Can be tested by using the same string literal (>= 5 characters) 3 or more times in a file and verifying that Detekt reports a duplication violation.

**Acceptance Scenarios**:

1. **Given** a file with a string literal repeated 3 or more times, **When** analyzed, **Then** Detekt must report a violation.
2. **Given** a file where repeated strings are extracted into a `private const val`, **When** analyzed, **Then** Detekt must pass.

---

### Edge Cases

- **Logging in Tests**: Standard output might be acceptable in test code for quick debugging; the rule should focus on production source sets.
- **Short Strings**: Very short strings (like empty strings or single characters) should be ignored by the duplication check to avoid noise.
- **Compose Naming**: Ensure that any naming or style rules do not conflict with Jetpack Compose requirements (e.g., `@Composable` function naming).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Detekt MUST fail the build if `println` or `print` is used in production modules.
- **FR-002**: Detekt MUST flag unnecessary `let` usage where it does not provide value (e.g., single-call on non-nullable).
- **FR-003**: Detekt MUST flag duplicated string literals exceeding a configurable threshold (default: 3 occurrences).
- **FR-004**: String duplication detection MUST ignore literals with less than 5 characters by default.
- **FR-005**: All new rules MUST be applied to production source sets while allowing for reasonable exceptions in test code.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero instances of `println` or `print` in the `main` source sets across all modules.
- **SC-002**: No more than 3 occurrences of the same string literal within a single file without being extracted to a constant.
- **SC-003**: 100% compliance with idiomatic `let` usage as defined by the new Detekt ruleset.

## Assumptions

- **Logging Library**: The project uses a structured logging library (e.g., Kermit or Timber) that is the only approved way to log.
- **Source Set Separation**: Detekt is correctly configured to distinguish between `main` and `test` source sets for rule application.
- **Thresholds**: A threshold of 3 occurrences for string duplication is considered a reasonable default for this project.
