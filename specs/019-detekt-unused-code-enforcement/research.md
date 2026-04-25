# Research: detekt-unused-code-enforcement

## Findings

### 1. Current State of Rules
- **Rule**: `UnusedPrivateMember`
  - **Status**: Active (`active: true`)
  - **Current Excludes**: Includes all test source sets (`**/test/**`, `**/androidTest/**`, etc.).
- **Rule**: `UnusedPrivateProperty`
  - **Status**: Active (`active: true`)
  - **Current Excludes**: Includes all test source sets.

### 2. Generated Code Paths
- Research into the project structure confirms that generated code is primarily located under `**/build/generated/**`.
- Standard Kotlin/Android patterns often use `**/build/**` and `**/generated/**` as top-level containers for build artifacts and tool-generated sources (e.g., KSP, Hilt).

### 3. Baseline Violations (Hidden by Excludes)
- **Module**: `:core:security`
  - `UnusedPrivateMember`: `String.decodeHex()` in `HdkEcdhP256Test.kt:18`
- **Module**: `:feature:fido2`
  - `UnusedPrivateMember`: `fakeInfo()` in `Ctap2Fido21FlagsTest.kt:57`
  - `UnusedPrivateProperty`: `responseBuilder` in `Ctap2Fido21FlagsTest.kt:30`
  - `UnusedPrivateProperty`: `cid` in `Ctap2Fido21FlagsTest.kt:71`
  - `UnusedPrivateProperty`: `MAX_MSG_SIZE_1200` in `Ctap2WindowsCompatibilityTest.kt:33`
  - `UnusedPrivateProperty`: `MAX_CRED_COUNT_255` in `Ctap2WindowsCompatibilityTest.kt:34`
  - `UnusedPrivateProperty`: `DAYS_31` in `RelyingPartyTest.kt:31`
  - `UnusedPrivateProperty`: `DAYS_29` in `RelyingPartyTest.kt:33`
  - `UnusedPrivateProperty`: `otherRpConsent` in `GetUserConsentUseCaseTest.kt:409`
  - `UnusedPrivateProperty`: `updateCredentialLabelUseCase` in `CredentialManagementViewModelTest.kt:27`

### 4. Suppression Strategy
- Detekt supports `@Suppress("UnusedPrivateMember")` and `@Suppress("UnusedPrivateProperty")` annotations.
- These can be applied at the file, class, or individual declaration level.

## Decisions

- **Decision**: Remove all test-related excludes from both rules.
- **Rationale**: Ensure consistent code quality standards across production and test code.
- **Decision**: Add targeted excludes for generated code.
- **Rationale**: Prevent build failures from code that developers do not directly control.
- **Patterns chosen**: `['**/build/**', '**/generated/**']`.
- **Decision**: Adopt a "Removal-First" policy.
- **Rationale**: Aligns with user requirement to only allow suppression after analysis and with a stated reason.

## Alternatives Considered

- **Alternative**: Rely on global `build.excludes` in `detekt.yml`.
- **Evaluation**: Rejected. While global excludes exist, rule-specific excludes provide more granular control and ensure the policy is documented explicitly where the rule is configured.
