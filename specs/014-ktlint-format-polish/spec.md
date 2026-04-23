# Feature Specification: KtLint Format Polish

**Feature Branch**: `014-ktlint-format-polish`  
**Created**: 2026-04-23  
**Status**: Draft  
**Input**: User description: "Polish session. Enhance code quality by the use of klintFormat to format code. It is important to point out that the format changes should not introduce logic, nor semantice differences. And very important that all relevant comments must also be keept."

## Clarifications

### Session 2026-04-23
- Q: Should ktlint be enforced project-wide or only for fido2? → A: Project-wide Enforcement: Apply ktlint to all modules (core, feature, app) to satisfy the Constitution.
- Q: Which import ordering style should be adopted? → A: Official Kotlin Style Guide: Pure alphabetical ordering with no package grouping.
- Q: Are there any specific modules or legacy directories to exclude? → A: Standard Exclusions Only: Only exclude build/, .gradle/, and known generated folders (KSP, SqlDelight, BuildKonfig).

## User Scenarios & Testing *(mandatory)*

### User Story 0 - Satisfy Pre-Commit Quality Gates (Priority: P0)

As a developer working on the KMP stabilization, I want to resolve all style violations across the project so that the local pre-commit hook passes and I can finalize the commit for the build infrastructure migration.

**Why this priority**: Pre-commit hooks are currently failing due to `ktlint` errors (e.g., import ordering), blocking the commit of critical build stabilization changes. This is the immediate blocker for the current development cycle.

**Independent Test**: Can be verified by running the pre-commit script or `./gradlew ktlintCheck`. It must succeed for all files, including tests in `androidHostTest` and `androidMain`.

**Acceptance Scenarios**:

1. **Given** files with import ordering issues (e.g., `CredentialListScreenTest.kt`), **When** formatting is applied, **Then** the import order is corrected and the pre-commit hook passes.
2. **Given** structural changes from the AGP 9.2.0 migration (source sets moved to `androidMain`), **When** formatting is applied, **Then** no files are moved or reverted to old locations.

---

### User Story 1 - Automated Code Formatting (Priority: P1)

As a developer, I want to run a single command that automatically formats all Kotlin code in the project according to the project's style guide, ensuring no semantic changes are introduced and all comments are preserved.

**Why this priority**: Consistent code style is fundamental to code quality. However, it is CRITICAL that automated tools do not change the behavior of the code or delete developer documentation/comments.

**Independent Test**: Verify by comparing binary or bytecode output before and after formatting (if possible) or by running the full test suite. Inspect files manually to ensure comments (KDoc, TODOs, inline notes) are untouched.

**Acceptance Scenarios**:

1. **Given** a Kotlin source file with non-standard formatting and extensive comments, **When** the formatting command is executed, **Then** the file content is updated to follow the style guide but ALL comments are preserved in their relative locations.
2. **Given** a codebase with valid logic, **When** formatting is applied, **Then** the logic remains identical (no semantic changes) and all unit tests still pass.

---

### User Story 2 - Style Violation Enforcement (Priority: P2)

As a developer, I want the system to check for style violations that cannot be automatically corrected, so that I can fix them before committing my changes.

**Why this priority**: Ensures high-level code quality for rules that require manual intervention.

**Independent Test**: Tested by introducing a naming convention violation. The check should report the violation without making any destructive changes to the code or comments.

**Acceptance Scenarios**:

1. **Given** a Kotlin file with a style violation that cannot be auto-formatted, **When** the lint check command is executed, **Then** the system reports the specific line and rule violated.

---

### Edge Cases

- **Mixed Line Endings**: Normalize to LF.
- **Generated Code**: Exclude generated directories (e.g., SqlDelight generated code, BuildKonfig).
- **Read-only Files**: Report failure for specific files.
- **Comment-heavy Code**: Ensure blocks of commented-out code or complex KDocs are not mangled or removed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST integrate a Kotlin linting and formatting tool (e.g., `ktlint` via a Gradle plugin) PROJECT-WIDE into all modules in the build infrastructure.
- **FR-002**: System MUST provide a Gradle task (e.g., `ktlintFormat`) that automatically corrects style violations in all Kotlin source files.
- **FR-003**: System MUST provide a Gradle task (e.g., `ktlintCheck`) that validates code style without modifying files.
- **FR-004**: System MUST support Kotlin Multiplatform (KMP) source sets, including `commonMain`, `androidMain`, and `iosMain`.
- **FR-005**: System MUST allow configuration of custom style rules or exclusions via a standard configuration file (e.g., `.editorconfig`), which MUST follow the Official Kotlin Style Guide for import ordering.
- **FR-006**: Formatting MUST NOT introduce any semantic changes to the code (no logic changes).
- **FR-007**: Formatting MUST preserve all comments, including KDocs, inline comments, and block comments.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Running the formatting task on the entire project results in 0 style violations reported by the subsequent check task.
- **SC-002**: Local pre-commit hooks pass successfully after formatting.
- **SC-003**: 100% of existing unit tests pass after formatting is applied.
- **SC-004**: Manual audit of 5 randomly selected files confirms no comments were deleted or mangled.
- **SC-005**: The formatting task completes in under 60 seconds for the current codebase size.

## Assumptions

- The project uses Gradle as the primary build system.
- "klintFormat" in the user description refers to the `ktlintFormat` task provided by the `ktlint` Gradle plugin.
- The project already has a `.editorconfig` or is willing to adopt one for rule definition.
- Existing code might have significant linting violations that will be fixed in bulk during this "polish session".
- The structural changes from AGP 9.2.0 migration (move to `androidMain`) are final and must be respected by the formatting tool.
