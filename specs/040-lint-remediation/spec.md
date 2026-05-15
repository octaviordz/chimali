# Feature Specification: Lint Remediation

**Feature Branch**: `040-lint-remediation`  
**Created**: 2026-05-15  
**Status**: Draft  
**Input**: User description: "Enhance code quality by implementing remediations for issues listed in audit.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Remove Redundant Compose Suppressions (Priority: P1)

A developer opens any Jetpack Compose UI file in the `:feature:vault` or `:feature:fido2` modules and no longer sees `@Suppress("FunctionNaming")` annotations on `@Composable` functions, because the project's `detekt.yml` already excludes Composable-annotated functions from the `FunctionNaming` rule. Removing these annotations reduces visual noise, eliminates configuration drift, and ensures developers trust the centralized linting configuration.

**Why this priority**: This is the highest-volume issue identified in the audit (affecting numerous Compose files across multiple modules). It is a low-risk, high-reward change that immediately improves codebase hygiene and developer confidence in the linting pipeline, with zero functional impact.

**Independent Test**: Can be verified by running `detektDebug` and `ktlintCheck` across all affected modules and confirming zero new violations. A global search for `@Suppress("FunctionNaming")` in Compose files should return zero results.

**Acceptance Scenarios**:

1. **Given** a Compose UI file containing `@Suppress("FunctionNaming")` on a `@Composable` function, **When** the suppression is removed, **Then** the Detekt `FunctionNaming` rule does not flag the function.
2. **Given** all redundant `@Suppress("FunctionNaming")` annotations have been removed, **When** `detektDebug` is executed on the full project, **Then** zero `FunctionNaming` violations are reported.
3. **Given** all redundant suppressions are removed, **When** the local CI pipeline (`tools/local-ci.ps1`) is run, **Then** it passes without regressions.

---

### User Story 2 - Resolve ForbiddenComment Suppressions and Underlying TODOs (Priority: P2)

A developer reviews `VaultViewModel.kt`, `CredentialListScreen.kt`, `DevelopmentToolsScreen.kt`, `AuthenticationPromptScreen.kt`, `RegistrationPromptScreen.kt`, and `Fido2RegistrationNavGraph.kt` and finds that all `@Suppress("ForbiddenComment")` annotations have been removed. Each underlying `TODO` or `FIXME` comment has been either resolved (the incomplete functionality is implemented) or, where implementation is out of scope for this feature, converted into a tracked issue reference (e.g., `// DEFERRED(040): <description>`) that satisfies the `ForbiddenComment` rule without hiding the debt.

**Why this priority**: The audit identified that `ForbiddenComment` suppressions actively subvert the project's architectural enforcement mechanisms. Resolving them restores the integrity of the Detekt quality gate and surfaces hidden technical debt.

**Independent Test**: A global search for `@Suppress("ForbiddenComment")` returns zero results. Running `detektDebug` produces zero `ForbiddenComment` violations. Each previously-suppressed TODO has a traceable resolution.

**Acceptance Scenarios**:

1. **Given** a file containing `@Suppress("ForbiddenComment")` hiding a `TODO` comment, **When** the suppression is removed and the `TODO` is resolved or converted to a tracked reference, **Then** `detektDebug` reports zero `ForbiddenComment` violations for that file.
2. **Given** a `TODO` comment that represents functionality out of scope for this feature, **When** it is converted to a tracked issue reference format (`DEFERRED(040)`), **Then** the reference is documented and the comment no longer triggers `ForbiddenComment`.
3. **Given** all `ForbiddenComment` suppressions are resolved, **When** the local CI pipeline (`tools/local-ci.ps1`) is run, **Then** it passes without regressions.

---

### User Story 3 - Migrate Generic Exception Handling to Outcome (Priority: P3)

A developer reviews `VaultRepositoryImpl.kt` and `RegisterCredentialUseCase.kt` and finds that `@Suppress("TooGenericExceptionCaught")` annotations have been removed. The `catch(e: Exception)` blocks have been replaced with specific exception handling that maps known failure modes to the appropriate `DomainError` subtypes via the project's `Outcome<D, E : DomainError>` pattern, as mandated by the constitution.

**Why this priority**: This addresses a direct constitutional violation (Principle X.2 and X.7). While lower volume than P1, it resolves an architectural anti-pattern that can mask critical runtime failures. Requires careful refactoring to ensure no regressions.

**Independent Test**: A search for `@Suppress("TooGenericExceptionCaught")` returns zero results. The affected functions still return `Outcome` with correctly-typed error variants. Existing unit tests continue to pass, and new tests cover the specific exception-to-DomainError mappings.

**Acceptance Scenarios**:

1. **Given** `VaultRepositoryImpl.kt` catches a raw `Exception`, **When** the catch block is refactored to handle specific exceptions mapped to `DomainError` subtypes, **Then** the `@Suppress("TooGenericExceptionCaught")` annotation can be removed and `detektDebug` reports no violations.
2. **Given** `RegisterCredentialUseCase.kt` catches a raw `Exception`, **When** the catch block is refactored similarly, **Then** the suppression is removed and the function correctly propagates specific error types through `Outcome`.
3. **Given** both files have been refactored, **When** the full test suite is run, **Then** all existing tests pass and new tests verify the specific exception mappings.

---

### User Story 4 - Address Deprecated API Usage (Priority: P4)

A developer reviews `BluetoothHidDeviceWrapper.kt` and finds that `@Suppress("DEPRECATION")` has been removed. The deprecated Bluetooth API calls have been modernized to their current equivalents, or, where no direct replacement exists on the minimum SDK target (API 28), the usage is guarded with proper SDK version checks and the suppression is narrowed to the smallest possible scope with a documented justification.

**Why this priority**: While important for long-term platform compatibility, this is the lowest-priority item because it affects a single file and is limited to platform-specific code. It may require careful API migration research.

**Independent Test**: The `@Suppress("DEPRECATION")` annotation is either removed entirely or narrowed to a single deprecated call with a documented reason. The Bluetooth HID authenticator continues to function correctly on supported API levels.

**Acceptance Scenarios**:

1. **Given** `BluetoothHidDeviceWrapper.kt` suppresses deprecation warnings, **When** deprecated API calls are replaced with modern equivalents (where available for API 28+), **Then** the suppression is removed and the code compiles without deprecation warnings.
2. **Given** a deprecated API has no replacement on the minimum SDK (API 28), **When** the suppression is retained, **Then** it is narrowed to the exact statement using `@Suppress` at the expression level and is accompanied by a comment referencing the replacement API and the minimum SDK required.
3. **Given** all deprecation changes are made, **When** Bluetooth HID tests are run, **Then** no regressions are detected.

---

### Edge Cases

- What happens when removing a `@Suppress("FunctionNaming")` annotation from a non-`@Composable` function that coincidentally has a composable-style name? The function should be renamed to follow standard `camelCase` conventions.
- How does the system handle a `catch(e: Exception)` block that genuinely needs to catch multiple unrelated exception types? A multi-catch or sequential specific-catch block should be used, catching only the known exception types and letting truly unexpected exceptions propagate.
- What if a `TODO` comment in a `ForbiddenComment`-suppressed file represents a critical feature gap that cannot be converted to a simple issue reference? The `TODO` should be resolved by implementing the missing functionality if feasible, or escalated as a blocking issue with the `ForbiddenComment` suppression moved to the Detekt baseline with explicit documented justification.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: All `@Suppress("FunctionNaming")` annotations on `@Composable` functions MUST be removed from the codebase, as the Detekt configuration already exempts Composable-annotated functions.
- **FR-002**: All `@Suppress("ForbiddenComment")` annotations MUST be removed. Each underlying `TODO`/`FIXME` comment MUST be either resolved (functionality implemented) or converted to a tracked issue reference that does not trigger the `ForbiddenComment` rule.
- **FR-003**: All `@Suppress("TooGenericExceptionCaught")` annotations MUST be removed. The associated `catch(e: Exception)` blocks MUST be replaced with specific exception handling that maps to `DomainError` subtypes via `Outcome`.
- **FR-004**: The `@Suppress("DEPRECATION")` annotation in `BluetoothHidDeviceWrapper.kt` MUST be addressed by either modernizing the deprecated API calls or narrowing the suppression to the minimal scope with documented justification.
- **FR-005**: All changes MUST pass the project's local CI pipeline (`tools/local-ci.ps1`) including `detektDebug`, `ktlintCheck`, and the full unit test suite.
- **FR-006**: Valid suppressions identified in the audit (`EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` and `UNCHECKED_CAST` in test scope) MUST NOT be modified.
- **FR-007**: No changes to the Detekt baseline files (`config/detekt/detekt-baseline.xml`, `feature/fido2/detekt-baseline.xml`) are permitted unless explicitly required to track a deferred TODO resolution.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero instances of `@Suppress("FunctionNaming")` remain on `@Composable` functions across the entire codebase.
- **SC-002**: Zero instances of `@Suppress("ForbiddenComment")` remain in production code.
- **SC-003**: Zero instances of `@Suppress("TooGenericExceptionCaught")` remain in production code.
- **SC-004**: The `@Suppress("DEPRECATION")` in `BluetoothHidDeviceWrapper.kt` is either removed or narrowed to the minimal necessary scope with documented justification.
- **SC-005**: The local CI pipeline (`tools/local-ci.ps1`) passes with zero new Detekt or Ktlint violations introduced.
- **SC-006**: All existing unit tests continue to pass with no regressions.
- **SC-007**: The total count of inline `@Suppress` annotations in production code is reduced by at least 80% compared to the pre-remediation audit baseline.

## Assumptions

- The Detekt configuration in `config/detekt/detekt.yml` correctly excludes `@Composable`-annotated functions from the `FunctionNaming` rule, as stated in the audit. No changes to the Detekt configuration are required.
- The `Outcome<D, E : DomainError>` pattern and its associated `DomainError` sealed hierarchy are already well-established in the codebase and can be extended with new error subtypes as needed for the exception handling migration.
- Implementation holes identified in audit Section 3 (e.g., `Fido2RepositoryImpl`, `UserConsentRepositoryImpl`, `RelyingPartyRepositoryImpl` stubs) are out of scope for this feature. Their associated `TODO` comments will be converted to tracked issue references.
- The deprecated Bluetooth API analysis may require platform-specific research to identify the correct modern replacements and minimum SDK requirements.
- The `@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")` and `@Suppress("UNCHECKED_CAST")` annotations (validated as acceptable in the audit) will not be touched.
