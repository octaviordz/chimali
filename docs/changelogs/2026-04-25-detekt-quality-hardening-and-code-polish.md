# Detailed Changes - 2026-04-25 - Detekt Quality Hardening and Code Polish

## Overview
This update focuses on hardening the project's static analysis gates and performing a comprehensive code polish across the FIDO2 and core modules. We successfully enforced several Detekt rules that were previously suppressed or violated, leading to a more maintainable and specification-compliant codebase.

## Changes

### Detekt Quality Hardening
- **MagicNumber Enforcement**: Extracted over 100 hardcoded numeric literals (timeouts, offsets, buffer sizes, bit shifts) into descriptive named constants in companion objects.
- **MaxLineLength Compliance**: Manually refactored long logical lines into readable segments, prioritizing string concatenation for long log messages and UI text to stay within the 120-character limit.
- **Exception Handling**: Added `@Suppress("TooGenericExceptionCaught")` to legitimate generic catch blocks while improving internal diagnostic logging within those blocks.
- **Unused Code Purge**: 
    - Removed unused `hdkManager` dependency from `WalletMasterSeedProvider`.
    - Eliminated dead variable `hasSpecial` in `Fido2Authenticator`.
    - Removed redundant `getPublicKey` calls in `CredentialRepositoryImpl` and `CorruptedKeyRepairWorkerImpl`.
    - Deleted unused scratch file `TestDirection.kt`.
- **Naming Consistency**: Renamed `Services.kt` to `CryptoService.kt` in `:core:domain` to align with the single-responsibility principle and improve discovery.
- **Extended Rule Hardening**:
    - **Logging Standard**: Banned `android.util.Log` and `println` in production code. Migrated all FIDO2 production and test code to `co.touchlab.kermit.Logger`.
    - **Import Discipline**: Expanded all internal wildcard imports (e.g., `import com.chimali.fido2.*`) to explicit imports. Updated `detekt.yml` to prohibit wildcards except for approved DSLs (Compose, Material Icons).
    - **Collection Integrity**: Enabled `DontDowncastCollectionTypes` to prevent dangerous casts from read-only `List` to `MutableList`, ensuring immutable data contracts are respected.
    - **Raw String Threshold**: Standardized the `StringShouldBeRawString` threshold to **5** escaped characters. This allows short escaped strings (useful for JSON keys) while enforcing raw strings for complex or heavily escaped text.

### Structural Refactoring
- **RegisterCredentialUseCase**: Refactored the core registration logic from a deeply nested "pyramid of doom" `if/else` structure to a linear flow using guard clauses (early returns). This significantly improves readability and simplifies future maintenance of the registration flow.
- **Structured Concurrency**: Replaced `GlobalScope.launch` in `Fido2Initializer` with a dedicated, supervisor-backed `featureScope`. This aligns with modern coroutine best practices and ensures predictable cleanup.
- **Idiomatic Kotlin**: Refactored several `if-null` blocks to use safe-calls (`?.let`) and replaced manual `IllegalStateException` throws with the more idiomatic `error()` and `require()` functions.

### Diagnostic Improvements
- **Enhanced Error Tracing**: Updated `EncryptedMetadataIndexService` and `BluetoothHidDeviceWrapper` to include the full exception object in warning and error logs, facilitating easier remote troubleshooting of cryptographic and hardware-level failures.

## Impact
- **Maintainability**: The codebase is now significantly cleaner, with magic numbers replaced by meaningful names that reflect the FIDO2 and BIP39 specifications.
- **Build Stability**: Local CI (`local-ci.ps1`) now passes all Detekt gates without the need for broad baseline suppressions.
- **Logic Integrity**: All changes were verified to be non-functional (logic-preserving) refactors.

## Detailed Diff Summary
- **Files Modified**: 65
- **Insertions**: 706
- **Deletions**: 373
