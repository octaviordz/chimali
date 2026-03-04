# FIDO2 CTAP2 Compliance and Navigation Fixes (2026-03-03)

## Summary
Performance and reliability audit of the FIDO2 registration flow. Resolved critical "UNIQUE constraint failed" errors on re-registration, fixed a navigation "stuck" loop in the Compose-based prompt, and ensured full CTAP2 SPEC compliance for CBOR encoding.

## Changes

### 🛠 Fixed
- **CTAP2 Spec Compliance**: Updated `Ctap2ResponseBuilder` to use integer CBOR keys (e.g., `1` for versions, `3` for aaguid) as required by the FIDO2/CTAP2 specification. Previously used string keys caused Windows to distrust the response.
- **Double-Resume Exception**: Fixed an `IllegalStateException` in `BluetoothHidDeviceWrapper` where `registerApp()` could attempt to resume a coroutine twice if it returned `false` synchronously.
- **Credential UNIQUE Constraint**: Implemented "upsert" logic in `CredentialRepositoryImpl`. The system now automatically replaces an existing passkey if the same user re-registers at the same site, preventing crashes.
- **Navigation Feedback Loop**: Fixed a "stuck" UI where clicking "Cancel" would immediately re-open the registration prompt. 
    - Changed `Fido2UiEventBus` to use non-replaying flows (`replay=0`).
    - Implemented event consumption in `RegistrationPromptViewModel` to clear pending requests immediately.
- **Back Stack Management**: Added explicit `popBackStack()` calls in `Fido2RegistrationNavGraph` for both successful and cancelled registration paths.

### 🔄 Changed
- **Bluetooth HID Connectivity**: Stabilized the "Security Key" recognition in Windows by ensuring the HID descriptor and response packets use the correct lengths and keys.

### ✨ Added
- **Event Consumption**: Added `clearRegistrationRequest()` and `clearAuthenticationRequest()` to the `Fido2UiEventBus` for better lifecycle management of UI events.

## Impact
FIDO2 registration is now fully functional and spec-compliant. Users can register, cancel, and re-register passkeys without the application becoming unresponsive or crashing due to database constraints. Windows now correctly recognizes the device as a trustworthy FIDO2 Bluetooth security key.

## Verification
- [x] **Spec Validation**: Verified CBOR output matches CTAP2 integer key requirements.
- [x] **Registration Flow**: Confirmed `webauthn.io` succeeds on first try and on re-registration.
- [x] **Navigation**: Confirmed "Cancel" and system "Back" properly dismiss the prompt.
- [x] **Build Verification**: `:app:assembleDebug` successful.
