# CTAP2 Pipeline MVP Wiring

## Context
During testing of the `quickstart.md`, it was discovered that the `HidManager` was discarding received FIDO CTAP packets rather than forwarding them to the domain layer. Additionally, the Rust UniFFI JNI bindings were entirely missing from the Android Gradle build process, rendering the Authenticator logic unreachable.

## Changes
- Updated `HidManager.kt` to expose a `SharedFlow` of incoming BT packets and added a `sendReport` routing function.
- Created an MVP `CtapProcessor.kt` in Kotlin to replace the missing Rust dummy library, enabling `quickstart.md` logic to proceed without requiring a complex JNI build toolchain recovery.
- Updated `FidoViewModel.kt` to trigger the `HidService` foreground task to keep Bluetooth alive on Android 12+.
- Wired the raw payload collection to trigger the `FidoIntent.AuthRequestReceived` state, opening the platform Biometric Confirmation UI.
- Rewired `approveRequest` to ping the `CtapProcessor` and ship the mocked attestation blocks back out over the `HidManager` socket.
