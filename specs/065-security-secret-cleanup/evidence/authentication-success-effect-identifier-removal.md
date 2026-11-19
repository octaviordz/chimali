# Authentication success effect no longer carries the credential ID

- **Finding:** SEC065-CORE-009
- **Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T006/T012/T015
- **Date:** 2026-11-17
- **Synthetic data only:** The host integration test uses a synthetic credential identifier and assertion.

## Change

`AuthenticationEffect.NavigateToSuccess` is now a no-payload signal. `AuthenticationPromptScreen` and `Fido2RegistrationNavGraph` no longer forward the credential ID through the Compose callback; the app's only parent completion callback was empty. Authentication still completes the full `AssertionObject` through the `CompletableDeferred` owned by the CTAP request. `AuthenticationPromptViewModel.onCleared()` now cancels the buffered effects channel so queued navigation or feedback events are discarded with the screen-scoped owner.

## Verification

`AuthenticationIntegrationTest` passes 27 cases. The success regression verifies the exact `AssertionObject` still reaches the request deferred and that the UI effect is only `NavigateToSuccess`, with no credential field. A lifecycle regression leaves the success effect queued, clears the `ViewModelStore`, and verifies collection is cancelled instead of delivering the buffered effect. The full FIDO host suite passes 1,328 tests across 172 reports with zero failures, errors, or skips; KtLint passes.

## Limits

This removes one unnecessary immutable credential-ID handoff and releases buffered effects on ViewModel disposal. It does not erase the `CredentialId` String inside the assertion, the transport/AndroidX/Compose copies required by their APIs, or prove heap reclamation. Those owner and runtime boundaries remain open under T006/T012/T015/T037.
