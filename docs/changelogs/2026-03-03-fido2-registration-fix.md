# FIDO2 Registration Storage Fix and UI Improvements (2026-03-03)

## Summary
Resolved a persistent "Could not save your passkey" error during FIDO2 registration and implemented several UI/UX improvements to the registration and authentication flows.

## Changes

### 🛠 Fixed
- **Passkey Storage Failure**: Resolved a `SQLiteConstraintException` caused by a foreign key violation. The system now ensures that the `RelyingParty` (RP) is persisted in the database *before* attempting to save associated user consent records or credentials.
- **Infinite Loading Screen**: Fixed a race condition where the `RegistrationPromptViewModel` could miss the initial request event because it was emitted before the ViewModel was fully initialized. The `Fido2UiEventBus` now retains the last pending request.
- **Biometric Prompt Triggers**: Corrected an issue where the biometric verification prompt would not appear automatically. The ViewModels now directly invoke the verification service instead of relying on ignored UI effects.
- **Predictive Back Gesture**: Resolved a Logcat warning by enabling `android:enableOnBackInvokedCallback` in the manifest.

### 🔄 Changed
- **RP ID Validation**: Relaxed the strict HTTPS requirement for Relying Party IDs in domain models (`PublicKeyCredentialRpEntity`, `PasskeyCredential`, etc.) to allow testing with local origins (e.g., `webauthn.io` without prefix).
- **Registration Flow Logic**: Refactored `RegisterCredentialUseCase` to explicitly handle Relying Party registration as the first step of the credential creation process.
- **Mock Data Improvements**: Randomized mock user IDs and display names in `Fido2HomeScreen` to prevent SQLite unique constraint collisions (`UNIQUE(rpId, userId)`) during repeated manual testing.

### ✨ Added
- **Repository Enhancements**: Added `saveRelyingParty` to `CredentialRepository` and implemented it in `CredentialRepositoryImpl` to handle both insertion of new RPs and updating of existing ones.
- **Database Cascade**: Added `ON DELETE CASCADE` to the foreign key constraint for `UserConsentRecord` to ensure automatic cleanup when an RP is deleted.
- **Diagnostic Error Messages**: Enhanced `RegistrationErrorHandler` to append the underlying exception message to the "Storage error" display, providing better visibility into failures during the pilot phase.

## Impact
Users can now successfully complete the "Test Registration UI" flow on physical devices. The UI is more responsive, and error reporting is significantly more detailed for future troubleshooting.

## Verification
- Successful build of `:app:assembleDebug`.
- Manual verification on device confirms the biometric prompt appears and credentials can be saved successfully.
