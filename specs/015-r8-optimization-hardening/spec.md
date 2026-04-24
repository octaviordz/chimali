# Feature Specification: R8 Optimization & Hardening

**Feature Branch**: `015-r8-optimization-hardening`  
**Created**: 2026-04-23  
**Status**: Draft  
**Input**: User description: "Consider the analysis R8_Configuration_Analysis.md. The goal is to take action on the recommendations listed."

## Clarifications

### Session 2026-04-23
- Q: Should we enable resource shrinking? → A: Enable isShrinkResources = true in release builds
- Q: Should we add a shrunkDebug build type? → A: Add a shrunkDebug build type for earlier R8 verification

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enable Production-Ready Minification (Priority: P1)

As a developer, I want to enable R8 shrinking and obfuscation for release builds so that the application size is minimized and the code is protected against reverse engineering.

**Why this priority**: This is critical for production readiness. Currently, the release build is unoptimized, leading to larger APK sizes and unprotected source code.

**Independent Test**: Can be fully tested by generating a release APK/AAB and verifying that it is significantly smaller than a non-minified version and that the code is obfuscated.

**Acceptance Scenarios**:

1. **Given** a release build type, **When** I compile the app, **Then** R8 shrinking, optimization, and obfuscation MUST be performed.
2. **Given** the Gradle configuration, **When** R8 runs, **Then** it MUST operate in "Full Mode" for maximum aggressive optimization.

---

### User Story 2 - ProGuard Keep Rule Cleanup (Priority: P2)

As a developer, I want to remove redundant and overly broad ProGuard keep rules so that R8 can effectively strip unused code and optimize the dependency graph.

**Why this priority**: Removing noise from ProGuard rules improves build performance and allows for deeper code shrinking, which directly impacts app size and cold start performance.

**Independent Test**: Can be tested by verifying that the application still compiles and runs successfully after removing manual rules for libraries that provide their own (Bouncy Castle, Serialization, etc.).

**Acceptance Scenarios**:

1. **Given** the FIDO2 module ProGuard rules, **When** I remove redundant rules for libraries like Bouncy Castle and Kotlinx Serialization, **Then** the build MUST still succeed.
2. **Given** the migrated KMP architecture, **When** I remove obsolete Hilt rules, **Then** Koin dependency injection MUST remain fully functional in production builds.

---

### User Story 3 - Runtime Stability Verification (Priority: P3)

As a developer, I want to verify that the optimized release build remains stable at runtime so that I can be confident that R8 didn't over-shrink necessary components (especially those using reflection).

**Why this priority**: Aggressive shrinking can lead to `ClassNotFoundException` or `NoSuchMethodError` if keep rules are too narrow or missing for reflection-heavy code.

**Independent Test**: Can be fully tested by running the complete suite of instrumentation tests on a minified release build.

**Acceptance Scenarios**:

1. **Given** a minified release build, **When** I run the FIDO2 registration and authentication flows, **Then** no reflection-related crashes MUST occur.
2. **Given** a minified release build, **When** I execute UI Automator tests, **Then** all Compose-based UI components MUST function correctly.

---

### Edge Cases

- **What happens when a library doesn't actually bundle its rules?** System MUST allow for specific, narrow keep rules to be re-added if a third-party dependency lacks proper consumer ProGuard support.
- **How does system handle reflection in models?** Serialized models MUST be verified to ensure their fields are not stripped or obfuscated if they are used by external services (e.g., FIDO2 server-side).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST enable `isMinifyEnabled = true` and `isShrinkResources = true` for the `release` build type in the app module.
- **FR-002**: System MUST enable `android.r8.strictFullModeForKeepRules=true` in the project configuration.
- **FR-003**: System MUST remove all redundant ProGuard rules identified in the analysis for Bouncy Castle, Kotlinx Serialization, SQLCipher, and AndroidX libraries.
- **FR-004**: System MUST remove all obsolete Hilt-related ProGuard rules.
- **FR-005**: System MUST remove broad package-level wildcards (`com.chimali.fido2.**`) and replace them with narrow, specific rules only where strictly necessary for reflection or JNI.
- **FR-006**: System MUST ensure that serialized domain models remain functional under R8 full mode.
- **FR-007**: System MUST provide a `shrunkDebug` build type that mirrors `release` optimization settings but uses `debug` signing for local R8 verification.

### Key Entities *(include if feature involves data)*

- **R8 Configuration**: The set of Gradle and ProGuard flags that control the shrinking, optimization, and obfuscation process.
- **Keep Rules**: Specific directives that tell R8 which parts of the code must not be removed or renamed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Release APK size is reduced by at least 15% compared to the current unminified version.
- **SC-002**: 100% of redundant ProGuard rules identified in the analysis are removed.
- **SC-003**: 0 runtime crashes related to `ClassNotFoundException` or `NoSuchMethodError` in the minified release build during full regression testing.
- **SC-004**: Build time for a minified release build is documented and within acceptable CI limits.

## Assumptions

- **Assumption about libraries**: All modern libraries used (Bouncy Castle v1.80, Kotlinx Serialization, etc.) correctly bundle their own consumer ProGuard rules as per their documentation.
- **Assumption about reflection**: The current codebase uses minimal reflection, or such usage is already handled by `@Serializable` or standard Android/Kotlin consumer rules.
- **Assumption about tests**: The existing instrumentation test suite is sufficient to catch most over-shrinking issues.
