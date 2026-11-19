# Passkey management component rendering boundary

- **Finding:** SEC065-FIDO-025
- **Source:** `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialComponents.kt`
- **Classifications:** CLS-FIDO-001/002
- **Boundary:** BND-UI-009
- **Requirements:** FR-002/003/004; NFR-SEC-041; TR-AUTH-031; T005/T006/T039
- **Review date:** 2026-11-17

## Source and caller review

`CredentialItem` displays the relying-party ID and username. `CredentialDetailsScreen` also displays the Base64URL credential ID, username, display name and relying-party ID. The current `CredentialListScreen` caller supplies a deletion message containing the username and a biometric prompt subtitle containing the relying-party ID. The component forwards these immutable values to Compose `Text` and the Android biometric prompt API. It does not log them or establish a new persistent owner.

The current screen-scoped `CredentialManagementViewModel` lifecycle handling removes and erases its mutable credential state on stop, then requests a fresh list on start; its host lifecycle evidence is in `evidence/credential-list-lifecycle-owner-cleanup.md`. The composables borrow the screen model and materialize immutable display text at the platform boundary.

## Disposition and limits

Mapped the component to CLS-FIDO-001/002 and BND-UI-009, and synchronized the canonical owner lists. No user-visible behavior was changed. Source review does not establish release timing for Compose text/layout caches, semantics/accessibility/TalkBack, screenshots, biometric UI, window/GPU buffers, process teardown or JVM/native heap roots. No rendering instrumentation or heap result is claimed; those remain under T015/T027.
