# Data Model: WebAuthn L3 Compliance (Fast Path)

No new entities are introduced in this fix. The fix relies on existing state models (`Outcome`, `AuthenticationRequested`, `Fido2UiEventBus`) and the `Ctap2GetAssertionHandler`.

### State Transitions

- **Headless Fast-Path**
  - **Condition**: `uv == PREFERRED || uv == DISCOURAGED` AND exactly one matching credential in allow-list.
  - **Action**: `Ctap2GetAssertionHandler` evaluates credentials directly.
  - **Transition**: Resolves CTAP2 Deferred immediately. `AuthenticationRequested` is NOT dispatched to the UI Event Bus.

- **UI Fallback**
  - **Condition**: Headless path condition not met (e.g., UV REQUIRED or multiple credentials).
  - **Action**: Dispatches `AuthenticationRequested`.
  - **Transition**: `AuthenticationPromptViewModel` enters `AwaitingUserConsent` or auto-confirms if device `uvAvailability == NONE`.
