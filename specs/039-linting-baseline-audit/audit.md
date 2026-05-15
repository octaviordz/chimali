# Code Quality and Linting Baseline Audit

**Date**: 2026-05-15
**Feature**: 039-linting-baseline-audit

## 1. Static Analysis Baselines

This section catalogs all Detekt and Ktlint baseline XML configurations across the project.

- `config/detekt/detekt-baseline.xml`
- `feature/fido2/detekt-baseline.xml`

## 2. Inline Code Suppressions

This section lists inline `@Suppress` or `@SuppressWarnings` annotations in Kotlin source files.
Exceptions mapped to explicit configurations in `config/detekt/detekt.yml` will be marked as valid exclusions.

### Catalog of Inline Suppressions

1. **`@Suppress("FunctionNaming")`**
   - **Locations**: Extensively used in Jetpack Compose UI screens (e.g., `LabelManagerScreen.kt`, `VaultListScreen.kt`, `SecureNoteEntryScreen.kt`, `AuthenticationPromptScreen.kt`, `CredentialComponents.kt`).
   - **Validity**: **INVALID (Redundant)**. The `config/detekt/detekt.yml` already configures `FunctionNaming` to ignore functions annotated with `['Composable']`. These inline suppressions are unnecessary clutter.

2. **`@Suppress("ForbiddenComment")`**
   - **Locations**: `VaultViewModel.kt`, `CredentialListScreen.kt`, `DevelopmentToolsScreen.kt`, `CredentialComponents.kt`.
   - **Validity**: **INVALID (Hiding Debt)**. The `detekt.yml` explicitly forbids `TODO:` and `FIXME:` comments. Suppressing this rule hides technical debt instead of addressing it.

3. **`@Suppress("TooGenericExceptionCaught")`**
   - **Locations**: `VaultRepositoryImpl.kt`, `RegisterCredentialUseCase.kt`.
   - **Validity**: **INVALID (Architectural Violation)**. The project constitution mandates returning `Outcome<D, E : DomainError>` instead of throwing/catching raw generic exceptions (`Exception` or `Throwable`). This suppression masks improper error handling.

4. **`@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")`**
   - **Locations**: `PlatformBluetoothHid.kt` (iOS), `PlatformUserVerification.kt` (common).
   - **Validity**: **VALID**. This is an expected Kotlin Multiplatform compiler warning suppression due to the experimental nature of the APIs.

5. **`@Suppress("UNCHECKED_CAST")`**
   - **Locations**: `Ctap2AttestationStatementTest.kt`, `Ctap2Fido21FlagsTest.kt`.
   - **Validity**: **VALID (Test Scope)**. Acceptable in test fixtures where dynamic typing or mocked boundaries are required.

6. **`@Suppress("DEPRECATION")`**
   - **Locations**: `BluetoothHidDeviceWrapper.kt` (Android).
   - **Validity**: **INVALID (Tech Debt)**. Represents technical debt regarding deprecated Bluetooth or Android APIs that should be modernized.

## 3. Implementation Holes

This section identifies significant implementation holes such as unresolved `TODO` or `FIXME` comments.

### Discovered Implementation Holes

1. **Incomplete Cryptographic Integration in Vault**
   - **File**: `VaultViewModel.kt`
   - **Debt**: `// TODO: Trigger actual payload decryption and UI state update here`
   - **Impact**: The UI cannot display decrypted payload data, meaning the secure note/password functionality is structurally incomplete at the presentation boundary.

2. **Missing FIDO2 Registration & Authentication Core Logic**
   - **File**: `Fido2RepositoryImpl.kt`
   - **Debt**: `// TODO: Implement FIDO2 registration logic` and `// TODO: Implement FIDO2 authentication logic`
   - **Impact**: The core repository layer for FIDO2 operations is stubbed out. The application cannot actually perform FIDO2 ceremonies yet.

3. **Incomplete Persistence Layer for FIDO2 Data**
   - **Files**: `UserConsentRepositoryImpl.kt`, `RelyingPartyRepositoryImpl.kt`
   - **Debt**: Multiple `// TODO: Implement database save/get/query/delete/update logic`
   - **Impact**: Relying Party configurations and User Consent records cannot be persisted. Any session data will be lost on app restart.

4. **Missing Consent Persistence in Services**
   - **File**: `UserVerificationServiceImpl.kt`
   - **Debt**: `// TODO: Persist consent record`
   - **Impact**: Even if the repository was implemented, the service layer is not configured to call the persistence layer, breaking the user verification flow state.

## 4. Architectural Deviations

This section highlights structural areas of code quality improvement and architectural deviations across application modules.

### Discovered Architectural Deviations

1. **Domain Layer Exception Handling Violation (`:feature:fido2`)**
   - **Locations**: `RegisterCredentialUseCase.kt`, `VaultRepositoryImpl.kt`
   - **Deviation**: The constitution mandates functional error handling via `Outcome<D, E : DomainError>`. While these classes return `Outcome`, they suppress `TooGenericExceptionCaught` to catch raw `Exception` instances at the boundary. This is an anti-pattern; specific expected exceptions should be caught and mapped to `DomainError` to avoid masking critical runtime crashes (e.g., `NullPointerException`).

2. **Detekt Configuration Disconnect (`:feature:vault`, `:feature:fido2`)**
   - **Locations**: Widespread across Compose UI files.
   - **Deviation**: Developers are manually annotating `@Suppress("FunctionNaming")` on `@Composable` functions. This indicates a disconnect from the project's global linting configuration, which already ignores `Composable` annotated functions. This adds unnecessary boilerplate and noise to the codebase.

3. **Deprecated Platform API Usage (`:feature:fido2`)**
   - **Locations**: `BluetoothHidDeviceWrapper.kt`
   - **Deviation**: Use of `@Suppress("DEPRECATION")` masks the usage of deprecated Android Bluetooth APIs. This represents technical debt that must be paid down to ensure future compatibility with newer Android versions.

4. **Suppression of Constitution Enforcements**
   - **Locations**: Multiple ViewModels and UI files.
   - **Deviation**: The use of `@Suppress("ForbiddenComment")` to hide `TODO` and `FIXME` comments actively subverts the project's architectural enforcement mechanisms designed to prevent shipping incomplete code.
