# Bluetooth HID authenticator source-owner map

**Date:** 2026-11-15  
**Scope:** `:core:bluetooth` API, Koin binding, Android profile implementation, descriptor constant and host test. The app includes `bluetoothModule`; the singleton is constructed only if resolved. A prior repository-wide reference review found no production consumer resolving this service, so application profile activation is not claimed.

The API exposes only the `AuthenticatorState` enum through a `StateFlow`; it carries no peer name/address or report bytes. The Koin module transfers only a service reference. `BluetoothHidAuthenticatorImpl` retains the Android framework HID proxy, updates IDLE/ADVERTISING/CONNECTED state from callbacks, ignores callback `BluetoothDevice` values, and does not process HID report payloads. Its permission failures use fixed labels without throwable forwarding under SEC065-LOG-033; the existing captured-marker host test verifies this alongside host-side state transitions.

`BluetoothHidConstants` is a fixed static HID descriptor array with no user, peer or runtime-derived secret data, so it is assessed no-relevant-usage for this secret-cleanup inventory. The descriptor comment calls the simplified keyboard descriptor FIDO HID; this owner map makes no protocol compliance claim.

Lifecycle limits remain: `stop()` resets the local state but does not unregister the system profile, and `startAdvertising()` creates a single-thread executor without a corresponding shutdown path in this class. Their actual activation is not established because no production consumer resolves the service in inspected source. This is recorded as an implementation limitation for future service-lifecycle work, not evidence of secret exposure. No Android profile/device test was executed during this source review; configured diagnostic destinations and frozen-candidate verification remain open.
