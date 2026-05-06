# Research & Decisions

## Duplicate GetAssertion Requests
- **Decision**: Implement a headless fast-path for GetAssertion and fallback auto-confirm for UV=NONE.
- **Rationale**: The host (webauthn.io) legitimately sends two sequential GetAssertion requests (e.g., first with a specific credential ID, then falling back to discoverable credentials). We cannot suppress these at the transport level. By handling requests headlessly when `uv=preferred` and exactly one credential matches, we eliminate the unnecessary UI prompt. As a fallback, if the UI must be shown but UV is NONE, we auto-confirm to minimize user friction.
- **Alternatives considered**: Throttling or debouncing requests at the Bluetooth HID level was rejected because the payload lengths differ and these are valid distinct ceremonies requested by the host.

## Auto-Confirm Mechanism
- **Decision**: Add logic in `AuthenticationPromptViewModel.initAuthentication()` to immediately call `performAuthentication` if `availability.getBestAvailableMethod() == VerificationMethod.NONE`.
- **Rationale**: Reduces the 3-second delay waiting for user interaction on devices with no biometric enrolled. This helps meet the <200ms end-to-end target for Bluetooth HID.
- **Alternatives considered**: Relying solely on the headless path (Fix E) is preferred, but Fix F provides a necessary fallback for cases where the headless path is bypassed (e.g., UV is required but device has no capability, leading to an immediate failure rather than a UI hang).
