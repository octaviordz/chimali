# Feature Specification: Detekt Quality Enhancements

**Feature Branch**: `021-detekt-quality-enhancements`  
**Created**: 2026-04-25  
**Status**: Draft  
**Input**: User description: "Enhance Detekt configuration to enforce logging discipline, idiomatic scope functions, string literal management, bug prevention, and performance optimization."

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

### User Story 4 - Enforce Structured Logging Architecture (Priority: P1)

As a developer, I want the build to fail if I use `android.util.Log` directly instead of the project's approved Kermit logger, so that we maintain a single, consistent, and platform-agnostic logging architecture.

**Why this priority**: Direct use of `android.util.Log` bypasses the centralized logging configuration, making it impossible to globally toggle logs or redirect them to crash reporting tools in a platform-agnostic way.

**Independent Test**: Add `Log.d("tag", "message")` to a source file and verify Detekt failure.

**Acceptance Scenarios**:

1. **Given** a Kotlin file in `main`, **When** it calls `android.util.Log.e(...)`, **Then** Detekt must report a violation.
2. **Given** a Kotlin file, **When** it uses `Logger.e { ... }` from Kermit, **Then** Detekt must pass.

---

### User Story 5 - Prevent API Decay and Collection Correctness (Priority: P2)

As a developer, I want to be alerted when I use deprecated APIs or perform unsafe collection downcasting, so that the codebase remains stable across library updates and free from silent runtime collection errors.

**Why this priority**: Deprecated APIs increase technical debt and risk build breakage upon SDK upgrades. Unsafe downcasting (e.g., `List` to `MutableList`) violates Kotlin's immutability contract and causes unpredictable runtime crashes.

**Independent Test**: Use a `@Deprecated` standard library function or `as MutableList` on a `List` and verify Detekt flags it.

**Acceptance Scenarios**:

1. **Given** a production file, **When** it calls an API marked with `@Deprecated`, **Then** Detekt must report a violation.
2. **Given** a `List`, **When** it is cast to a `MutableList` using the `as` operator, **Then** Detekt must report a violation.

---

### User Story 6 - Optimize Collection Processing for Scalability (Priority: P2)

As a developer, I want Detekt to suggest using Sequences when processing large collections in multiple steps, so that we meet our performance targets for handling 10,000+ vault items without excessive memory allocations.

**Why this priority**: Constitution §IV requires handling 10k items with negligible degradation. Chained list operations allocate new collections at every step, which is inefficient compared to lazy Sequences.

**Independent Test**: Create a chain of 3+ operations on a List (e.g., `filter.map.first`) and verify Detekt suggests `asSequence()`.

**Acceptance Scenarios**:

1. **Given** a List processing chain with 3 or more operations, **When** analyzed, **Then** Detekt must suggest conversion to a Sequence.

---

### User Story 7 - Maintainable Imports and Readable Strings (Priority: P3)

As a developer, I want to avoid internal wildcard imports and use raw strings for multi-line or escaped text, so that the codebase remains easy to navigate and strings are readable.

**Why this priority**: Internal wildcard imports obscure dependencies and break IDE tooling. Multi-line strings with excessive escape characters are error-prone and hard to maintain compared to Kotlin Raw Strings.

**Independent Test**: Use a wildcard import for an internal `com.chimali` package and verify failure. Use a string with 3+ escape sequences and verify it suggests a Raw String.

**Acceptance Scenarios**:

1. **Given** an import like `com.chimali.fido2.ui.*`, **Then** Detekt must report a `WildcardImport` violation.
2. **Given** a string like `"\\n\\t\\\""`, **Then** Detekt must suggest using `"""..."""`.


### Edge Cases

- **Logging in Tests**: Standard output (`println`) and `android.util.Log` might be acceptable in test code; rules should target `main` source sets.
- **Third-Party Wildcards**: Wildcard imports for well-known external libraries (e.g., Compose, JUnit) are permitted to reduce boilerplate.
- **Regex and JSON**: Raw strings should be prioritized for regex patterns and JSON templates.
- **Compose Naming**: Ensure that any naming or style rules do not conflict with Jetpack Compose requirements (e.g., `@Composable` function naming).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Detekt MUST fail the build if `println`, `print`, or `android.util.Log` calls are used in main source sets.
- **FR-002**: Detekt MUST flag unnecessary `let` usage across all source sets (main and test) where it does not provide value.
- **FR-003**: Detekt MUST flag duplicated string literals (>= 5 chars) exceeding 3 occurrences.
- **FR-004**: Detekt MUST flag usage of `@Deprecated` APIs in main source sets.
- **FR-005**: Detekt MUST prohibit downcasting of standard collection types to their mutable counterparts.
- **FR-006**: Detekt MUST suggest Sequence conversion for collection operation chains of length 3 or greater.
- **FR-007**: Detekt MUST prohibit wildcard imports for internal `com.chimali` packages.
- **FR-008**: Detekt MUST suggest Raw Strings for literals containing 3 or more escaped characters.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero instances of `println`, `print`, or `android.util.Log` in `main` source sets.
- **SC-002**: No string literal repeated 3+ times within a single file without extraction to a constant.
- **SC-003**: 100% compliance with idiomatic `let` usage.
- **SC-004**: Zero usage of deprecated APIs in production code.
- **SC-005**: Zero unsafe collection downcasts detected.
- **SC-006**: All eligible list processing chains (length 3+) converted to Sequences.
- **SC-007**: Zero internal wildcard imports in the codebase.

## Assumptions

- **Logging Library**: The project uses a structured logging library (e.g., Kermit or Timber) that is the only approved way to log.
- **Source Set Separation**: Detekt is correctly configured to distinguish between `main` and `test` source sets for rule application.
- **Thresholds**: A threshold of 3 occurrences for string duplication is considered a reasonable default for this project.
