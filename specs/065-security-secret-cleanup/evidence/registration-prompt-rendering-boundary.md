# Registration prompt rendering and test-owner review

**Date:** 2026-11-17  
**Requirements:** FR-002/003/004/005/008/009/010; T005/T006/T012/T015/T029/T039.

`RegistrationPromptScreen` observes `RegistrationPromptViewModel` state. The consent state owns mutable `SensitiveCredentialText` RP and user fields; `RegistrationPromptContent` converts RP name/ID and user display/name owners into immutable strings for Compose. The view model erases the state-owned owners when the state changes and from `onCleared`. On user verification, the view model derives an immutable RP-name subtitle for `BiometricPrompt.PromptInfo`; the screen owns the active prompt reference, cancels it and clears that reference on composition disposal, and clears it on callback completion. Android/Compose/system copies remain outside app-owned erasure evidence.

The Android error callback passes `errString.toString()` to `UserVerificationFailed`; the view model keeps it in the error state and snackbar for display. This path does not log the platform string. It is distinct from the credential-management biometric component, whose callback uses fixed UI text.

`RegistrationPromptScreenTest` supplies only synthetic RP/user strings while checking consent rendering, headings/semantics, verification/error states, and callbacks. It is a separate test fixture owner. `UserVerificationPromptInfo.kt` only selects the Android authenticator policy based on OS version; it carries no identity or secret text.

The production screen, ViewModel state producer, and UI test are Relevant owners under `BND-UI-014`, a registration-consent boundary distinct from credential management (`BND-UI-009`). The ViewModel record also carries `CLS-FIDO-008` because the registration state transports client-data challenge material, although the prompt-rendering boundary covers only RP/user identity and platform error text. The authenticator-policy helper is assessed-no-relevant-usage. This source review does not prove renderer/accessibility/system-prompt heap release or physical TalkBack behavior; those remain under T015/T027.

Source SHA-256 values:

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/RegistrationPromptScreen.kt`: `f96efa528e2ba062f82623782b200c51e50ccb89ebb67ce5010d89b19bc9be75`
- `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/presentation/ui/RegistrationPromptScreenTest.kt`: `d1ddf38b22eabf11e3e5a40fe9bed71596a6a19a68df224e58f971e41791ad3f`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/UserVerificationPromptInfo.kt`: `e449c9da56fe8dd250ff9a016327b36f669506fd66522ee2e77026084e7f57ac`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/RegistrationPromptViewModel.kt`: `a250c3cd9e410e5b57045509137dd2f28a693316e3e07457aab776590b95fa95`
