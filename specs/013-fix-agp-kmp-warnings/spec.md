# Feature Specification: Fix AGP and KMP Build Warnings

**Feature Branch**: `013-fix-agp-kmp-warnings`  
**Created**: 2026-04-23  
**Status**: Draft  
**Input**: User description: "Fix build warnings related to Kotlin Multiplatform and AGP 9.0 compatibility."

## Clarifications

### Session 2026-04-23
- Q: Target AGP version? → A: 9.2.0
- Q: Custom build logic handling? → A: Fix immediately, preceded by a dependency analysis phase to identify potential migration issues.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Clean Build Environment (Priority: P1)

As a developer, I want to build the project without deprecation warnings so that I can focus on actual code issues and ensure future compatibility with AGP 9.0.

**Why this priority**: Removing warnings is essential for long-term maintainability and prevents "warning fatigue" where developers ignore critical messages.

**Independent Test**: Can be fully tested by running `./gradlew build` and verifying that the specific warnings mentioned in the request are no longer present in the output.

**Acceptance Scenarios**:

1. **Given** a codebase with AGP 9.0 deprecation warnings, **When** I run the build, **Then** no warnings regarding KMP plugin compatibility or `org.jetbrains.kotlin.android` usage are displayed.
2. **Given** the migrated build configuration, **When** I run tests for both Android and JVM targets, **Then** all tests pass.

---

### User Story 2 - Build System Modernization (Priority: P2)

As a build engineer, I want the project to use the latest recommended plugins so that we can leverage performance improvements and new features in AGP 9.0.

**Why this priority**: Keeping the build system modern reduces technical debt and makes it easier to upgrade dependencies in the future.

**Independent Test**: Verify that `gradle.properties` no longer contains legacy flags and that plugins are updated to their modern equivalents.

**Acceptance Scenarios**:

1. **Given** the project configuration, **When** I check `gradle.properties`, **Then** `android.builtInKotlin` and `android.newDsl` are absent.
2. **Given** a KMP module, **When** I check its applied plugins, **Then** `com.android.kotlin.multiplatform.library` is used instead of `com.android.library`.

---

### Edge Cases

- **Complex Build Logic**: A module uses AGP internal APIs that have changed or are removed in the new KMP-Android plugin.
- **Incompatible Dependencies**: A third-party library or plugin is not yet compatible with AGP 9.2.0 or the `com.android.kotlin.multiplatform.library` plugin.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST perform a comprehensive analysis of all modules, libraries, and dependencies to identify potential migration issues related to AGP 9.2.0 and the new KMP-Android plugin.
- **FR-002**: System MUST replace `com.android.library` with `com.android.kotlin.multiplatform.library` in all Kotlin Multiplatform modules, ensuring compatibility with AGP 9.2.0.
- **FR-003**: System MUST remove `org.jetbrains.kotlin.android` from the `:feature:vault` module as it is redundant in AGP 9.2.0+.
- **FR-004**: System MUST remove `android.builtInKotlin=true` and `android.newDsl=false` from `gradle.properties`.
- **FR-005**: System MUST ensure that any necessary build logic changes required by the new plugins (like DSL changes) are implemented.
- **FR-006**: All existing build targets (Android, Desktop, JVM) MUST remain functional.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero occurrences of the "org.jetbrains.kotlin.multiplatform plugin deprecated compatibility" warning in the build log.
- **SC-002**: Zero occurrences of the "Deprecated 'org.jetbrains.kotlin.android' plugin usage" warning in the build log.
- **SC-003**: 100% of modules build successfully using Gradle.
- **SC-004**: Full pass rate for existing unit tests across all targets.

## Assumptions

- The project is ready to commit to AGP 9.2.0+ patterns.
- The `com.android.kotlin.multiplatform.library` plugin is available and stable in AGP 9.2.0.
- The removal of `org.jetbrains.kotlin.android` in `:feature:vault` will not break any Android-specific Kotlin features as AGP 9.2.0 handles them natively.
- No other legacy properties in `gradle.properties` are critical for the current build beyond what was specified.
