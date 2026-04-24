# Feature Specification: KMP Debug Flag

**Feature Branch**: `016-kmp-debug-flag`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: User description: "Implement a platform-agnostic debug flag for Kotlin Multiplatform to replace hardcoded guards."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Secure Debug Tool Access (Priority: P1)

As a developer, I want to use development utilities (like mnemonic viewing and recovery) during local development without risking their exposure in production builds.

**Why this priority**: Security is paramount. Hardcoded guards like `if (true)` are prone to human error and can lead to sensitive data exposure in production.

**Independent Test**: Can be fully tested by running the app in a debug build, verifying the "Test & Debug Utilities" section is visible, and then running a release build to verify it is completely removed.

**Acceptance Scenarios**:

1. **Given** a debug build of Chimali, **When** I navigate to the Development Tools screen, **Then** I should see the "Dev-only — Master Seed" section.
2. **Given** a release (or `shrunkDebug`) build of Chimali, **When** I navigate to the Development Tools screen, **Then** the "Dev-only — Master Seed" section must not be visible.

---

### User Story 2 - Platform-Agnostic Build Detection (Priority: P2)

As a developer, I want to write shared code (in `commonMain`) that can behave differently based on whether it's a debug or release build, regardless of the target platform (Android/iOS).

**Why this priority**: Supports the long-term goal of moving feature logic to `commonMain` while maintaining platform-specific security behaviors.

**Independent Test**: Can be verified by using the `isDebug` flag in a `commonMain` component and observing expected behavior on both Android and iOS simulators.

**Acceptance Scenarios**:

1. **Given** a KMP module, **When** I access `com.chimali.core.common.isDebug`, **Then** it should return the correct build state for the current target platform.

---

### Edge Cases

- **Build Configuration Mismatch**: What happens if a module is built as debug but depends on a core module built as release? (Handled by Gradle's dependency resolution and `buildConfig` propagation).
- **Optimization Hardening**: How does the system handle R8 optimization? (The flag must be a constant literal to enable effective dead code elimination).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a centralized `isDebug` flag in the `:core:common` module.
- **FR-002**: The `isDebug` flag MUST be defined using Kotlin's `expect/actual` pattern to support multiplatform targets.
- **FR-003**: On Android, the `isDebug` flag MUST be sourced from `BuildConfig.DEBUG`.
- **FR-004**: On iOS/Native, the `isDebug` flag MUST be sourced from `Platform.isDebugBinary`.
- **FR-005**: The `isDebug` flag MUST be a compile-time constant to support dead code elimination by R8/ProGuard.
- **FR-006**: The `DevelopmentToolsScreen` MUST use the centralized `isDebug` flag to guard sensitive UI sections.
- **FR-007**: `Fido2Initializer` MUST be updated to use the centralized flag for logging configuration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of sensitive developer tools are hidden in non-debuggable builds (e.g., `release`, `shrunkDebug`).
- **SC-002**: Binary size analysis confirms that debug-only code blocks guarded by `isDebug` are stripped in optimized release builds.
- **SC-003**: No manual "isDebug" parameter passing is required in feature initializers (reduction of boilerplate).

## Assumptions

- **BuildConfig availability**: Assumes that `buildFeatures.buildConfig = true` can be enabled in the `:core:common` module without conflicts.
- **K/N Platform API**: Assumes `Platform.isDebugBinary` remains the standard way to detect debug binaries in Kotlin/Native.
- **R8 Constant Folding**: Assumes R8 is configured to perform constant folding on the generated `BuildVariant` properties.
