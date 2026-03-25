# Changelog: FIDO2 Registration UX and Crypto Fixes (2026-03-13)

## Summary
This update resolves critical stability and usability issues in the FIDO2 registration flow, specifically addressing an Android-specific cryptographic provider conflict and transient UI visibility problems.

## Changes

### feature:fido2 Layer

#### [MODIFY] `Fido2CryptoService.kt`
- **BouncyCastle Provider Instance Fix**: Resolved `java.security.NoSuchAlgorithmException: no such algorithm: EC for provider BC`. Android's framework overrides the `"BC"` provider name with a limited version. The service now passes the `BouncyCastleProvider()` instance directly to `KeyFactory` and `Signature` instead of using the name string.
- **Improved Decoding Safety**: Updated `decodeUncompressedPoint` to use the explicit provider instance, ensuring reliable EC point reconstruction during credential creation.

#### [MODIFY] `RegistrationPromptViewModel.kt`
- **Error Screen Persistence**: Added a guard in the `init` block to prevent incoming registration requests (from PC retries) from overwriting an active `Error` state. This keeps the "Registration failed" screen visible.
- **Success Screen Delay**: Introduced `SUCCESS_DISPLAY_DURATION_MS` (2 seconds) delay before navigating away from the `Success` state. This allows the "Passkey created!" message to be read by the user.
- **Retry Logic Fix**: Updated `performRegistration` to retain `pendingOptions` upon failure. Previously, these were cleared regardless of result, causing the "Try again" button to fail with a "No pending request" error.
- **Concurrent Response**: Ensured the transport layer's `CompletableDeferred` is resolved immediately upon success, so the host PC receives the response without waiting for the UI delay.

## Impact
- **Stability**: Eliminates the `NoSuchAlgorithmException` crash during registration.
- **UX**: Provides clear, readable feedback on both successful and failed registration attempts.
- **Reliability**: Enables functional "Try Again" behavior without needing to restart the flow from the PC.

## Verification
- Verified via Logcat analysis that `Fido2CryptoService` successfully derives and reconstructs EC keys after the provider fix.
- Manually confirmed that success/error screens remain visible for a sufficient duration.
- Validated that the "Try again" action correctly re-initiates the registration process.
