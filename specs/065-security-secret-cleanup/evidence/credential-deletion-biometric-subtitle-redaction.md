# Credential deletion biometric subtitle boundary

**Date:** 2026-11-17 (local)
**Finding:** SEC065-FIDO-BIOMETRIC-001
**Requirements:** FR-003/004/005/010; NFR-SEC-041; TR-AUTH-031
**Scope:** The selected-credential deletion prompt text passed from `CredentialListScreen` to `BiometricPromptComponent`.

## Change

Before this correction, the biometric subtitle interpolated the selected passkey's RP ID, creating an immutable String and passing it to both AndroidX `BiometricPrompt.PromptInfo` and the Compose fallback presentation. The caller now passes the fixed text `Verify your identity to delete this passkey`. It no longer converts the RP ID for the biometric subtitle. The in-app delete-confirmation dialog still names the selected account in Compose so the user can identify the record being removed.

## Verification

Added a focused assertion to `CredentialListScreenTest.screen_showsDeleteDialog_whenDeleteIconClicked`. The regression expected the fixed subtitle, and the pre-correction implementation did not display it. After the source correction, the API 35 AOSP emulator test passed: 1 test, 0 failures, 0 errors, 0 skipped. `:feature:fido2:ktlintCheck` passed in the same Gradle run.

The test uses `ComponentActivity`, so `BiometricPromptComponent` exercises its Compose fallback rather than launching AndroidX's system biometric dialog. The passing result verifies the caller's rendered fixed subtitle. The AndroidX `.setSubtitle` call is source-reviewed to receive that same fixed value; real system dialog, accessibility, lifecycle, OEM retention and heap behavior were not exercised. The initial red run report was overwritten by the subsequent connected-test run and is not retained as an artifact.

## Artifacts

The passing connected-test XML was overwritten by a later run and is no longer retained; the result is recorded in this evidence summary, so no unavailable XML artifact is linked.


- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt` — SHA-256 `2d495c79c04ca5dead70afcbced0178b8c93cc2d8fc03d187419c1514695475b`
- `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/presentation/management/CredentialListScreenTest.kt` — SHA-256 `637f148d37b93125c21262eec64be66246e8888ca5a8cb7bcbccc830aca18a0e`

This narrows the system-text disclosure for this caller; it does not establish erasure of the selected credential's Compose dialog copies or close SEC065-FIDO-BIOMETRIC-001.
