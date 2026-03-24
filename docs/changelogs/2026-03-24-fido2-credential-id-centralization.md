# FIDO2 Credential ID Centralization and Security Enhancement

**Date**: 2026-03-24
**Author**: Antigravity (AI Assistant)

## Summary

This change centralizes the FIDO2 credential ID generation logic into a single, secure implementation within the `PasskeyCredential` domain model. It also enhances user privacy by transitioning from predictable, timestamp-based IDs to high-entropy 32-byte `SecureRandom` IDs.

## Technical Details

### 1. Centralized Generation API
Refactored `PasskeyCredential.kt` to include a centralized generator in its companion object. This ensures that all components (UseCases, Handlers, etc.) produce IDs using the same logic and format.

- **New Class**: `PasskeyCredential.Companion.GeneratedId`
- **New Method**: `PasskeyCredential.Companion.generateRandomId()`

### 2. High-Entropy Credential IDs
Replaced the legacy formatting:
- **Old Format**: `cred_` + `System.currentTimeMillis()` + 6 random digits (approx. 70 bits of entropy, leaks registration timing and authenticator type).
- **New Format**: 32-byte (256-bit) `SecureRandom` buffer (no metadata leakage, extremely high collision resistance).

The raw 32 bytes are used for the FIDO2 protocol, while a Base64URL-encoded version is used as the internal key alias and for HDK derivation paths.

### 3. Integrated Callsites
Updated the following callsites to use the new centralized generator:
- `RegisterCredentialUseCase.kt`
- `PasskeyCredential.fromMakeCredentialOptions()`

This eliminates code duplication and ensures that all new credentials follow the secure-by-default pattern.

### 4. Test Stability and Compilation
Fixed pre-existing compilation errors in the test suite that were blocking verification:
- **`RegisterCredentialUseCaseTest.kt`**: Removed obsolete `fido2Authenticator` parameter from the `RegisterCredentialUseCase` constructor.
- **`Ctap2WindowsCompatibilityTest.kt`**: Removed obsolete `AuthenticatorDataBuilder` parameter from the `Ctap2GetAssertionHandler` constructor.

## Verification

- **Unit Tests**: Ran `Fido2CryptoServiceTest` to ensure that the mapping of 32-byte IDs to 31-bit HDK indices remains stable and deterministic.
- **Compilation**: Verified that all `:feature:fido2` unit tests now compile and pass (`BUILD SUCCESSFUL`).
- **Logic Review**: Confirmed that the transition to 32-byte IDs aligns with FIDO2/WebAuthn best practices and does not negatively impact the deterministic key recovery flow (HDK).

## Impact

- **Security**: Increased entropy for credential IDs.
- **Privacy**: Prevented authenticator fingerprinting through predictable IDs.
- **Maintainability**: Centralized logic reduces the risk of future divergence in ID formats.
