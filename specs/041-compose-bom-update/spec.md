# Feature Specification: Compose BOM Update

**Feature Branch**: `041-compose-bom-update`  
**Created**: 2026-05-15  
**Status**: Draft  
**Input**: RCA finding — upgrade Compose BOM from 2024.12.01 to the latest stable release to eliminate benign but noisy classpath scan warnings during instrumented test execution.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Clean Test Log Output (Priority: P1)

As a developer running the "All Tests" configuration in the IDE, I want the test runner output to be free of spurious `ClassNotFoundException` warnings so that I can focus on genuine test failures and not be distracted by framework noise.

**Why this priority**: The warnings (`PlaceholderHardwareCanvas`, `ComposeAnimation` tooling classes) currently fill the Logcat output with dozens of stack traces during every instrumented test run, making it harder to spot real issues. Eliminating them improves daily developer experience for the entire team.

**Independent Test**: Can be fully tested by running the "All Tests" configuration on a physical device or emulator and verifying the Logcat output no longer contains `ScanningTestLoader` "Could not load class" warnings for Compose internal stubs.

**Acceptance Scenarios**:

1. **Given** the project dependencies have been updated to the latest stable Compose BOM, **When** the developer runs the full instrumented test suite ("All Tests"), **Then** no `ClassNotFoundException` warnings for `PlaceholderHardwareCanvas` or `androidx.compose.ui.tooling.animation.*` classes appear in Logcat.
2. **Given** the dependencies have been updated, **When** the developer runs the full instrumented test suite, **Then** all previously passing tests (including `VaultLabelTest`, `VaultServicePasswordTest`, and `VaultListScreenTest`) continue to pass with zero regressions.

---

### User Story 2 - Continued Application Stability (Priority: P1)

As a user of the Chimali app, I want the application to remain fully functional after the dependency update so that my password vault, secure notes, and authenticator features work exactly as before.

**Why this priority**: Dependency upgrades carry regression risk. Ensuring zero functional regressions is equally important to achieving the log cleanup goal.

**Independent Test**: Can be tested by running the full local CI pipeline (`tools/local-ci.ps1`) and verifying all static analysis checks and unit tests pass. Manual smoke testing of core flows (vault list, credential detail, FIDO2 registration) validates end-to-end stability.

**Acceptance Scenarios**:

1. **Given** the updated dependencies, **When** the local CI pipeline is executed, **Then** all Detekt, Ktlint, and unit test checks pass without new violations or failures.
2. **Given** the updated dependencies, **When** the app is launched on a device, **Then** all core features (vault listing, password entry, secure note viewing, FIDO2 authenticator) render correctly and function without crashes.

---

### User Story 3 - Access to Latest Platform Capabilities (Priority: P2)

As a developer on the Chimali project, I want the project to use a current and supported version of Compose so that I can leverage recent bug fixes, performance improvements, and new features without maintaining workarounds for known issues in older releases.

**Why this priority**: The current Compose BOM (2024.12.01 / Compose 1.7.3) is over a year behind the latest stable. Staying current reduces the surface area for known bugs and simplifies future upgrades by avoiding large version jumps.

**Independent Test**: Can be verified by confirming the project builds successfully with the updated BOM and that the version catalog reflects the latest stable Compose version.

**Acceptance Scenarios**:

1. **Given** the dependency update is complete, **When** a developer inspects the version catalog, **Then** the Compose BOM and individual Compose library versions reflect the latest stable release.

---

### Edge Cases

- What happens if a Compose API used in the project has been deprecated or removed in the newer version?
- How does the update affect Compose UI test libraries (`ui-test-junit4`, `ui-test-manifest`) that must remain version-aligned?
- What happens if the newer Compose version introduces new Detekt or Ktlint violations due to generated code changes?
- What if the newer BOM requires a minimum Kotlin version higher than the project's current Kotlin version?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST update the Compose dependency set to the latest stable release while maintaining version alignment across all Compose libraries (UI, Material 3, animation, tooling, testing).
- **FR-002**: The system MUST ensure all instrumented tests that previously passed continue to pass after the update with zero regressions.
- **FR-003**: The system MUST pass the full local CI pipeline (`tools/local-ci.ps1`) — including Detekt, Ktlint, and all unit tests — after the update.
- **FR-004**: The system MUST resolve any compilation errors or deprecation warnings introduced by the version change.
- **FR-005**: The system MUST update the version catalog (central dependency management) to reflect the new versions so that all modules in the project consume consistent dependency versions.
- **FR-006**: The system MUST verify that the minimum Kotlin version required by the new Compose release is satisfied by the project's current Kotlin configuration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero `ClassNotFoundException` warnings for `PlaceholderHardwareCanvas` or `androidx.compose.ui.tooling.animation.*` in test runner output when executing the full instrumented test suite.
- **SC-002**: 100% of previously passing tests continue to pass — zero regressions in both instrumented and unit test suites.
- **SC-003**: Local CI pipeline (`tools/local-ci.ps1`) passes on first run after the update with zero new Detekt, Ktlint, or compilation violations.
- **SC-004**: All Compose library versions in the version catalog are aligned to a single BOM release — no mixed or manually pinned individual Compose versions remain.
- **SC-005**: Zero new deprecation warnings introduced by the update (any deprecated API usage in the existing codebase must be migrated as part of this feature).

## Assumptions

- The latest stable Compose BOM is compatible with the project's current minimum SDK (API 28) and target SDK (API 35).
- The project's current Kotlin version (2.3.20) meets or exceeds the minimum Kotlin version required by the latest Compose release.
- No breaking API changes in the latest Compose release affect the project's existing UI code (based on the project using standard Material 3 APIs rather than experimental Compose APIs).
- The `compose-bom` platform dependency mechanism will continue to manage transitive version alignment, so individual Compose library version pins can be removed where the BOM provides them.
- The Compose compiler plugin version will be updated in tandem, as it is coupled to the Kotlin version and Compose runtime.
- The Compose Detekt rules plugin (`io.nlopez.compose.rules:detekt`) remains compatible with the updated Compose version.
