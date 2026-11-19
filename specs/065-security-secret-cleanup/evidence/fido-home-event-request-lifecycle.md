# FIDO home request event lifecycle

**Finding:** SEC065-FIDO-015 — the home-screen event observer retained pending registration/authentication request values in its Compose state while the activity was backgrounded.

`Fido2EventObserver` now collects the event bus pending-request StateFlows with lifecycle-aware state that resets the Compose-owned request reference to `null` when the screen stops. The event bus remains the request owner while its deferred request is active; on resume, the observer reads the current still-pending request. Terminal deferred completion continues to clear the event bus request through its completion hook.

`LifecycleClearedStateTest` and `PairedDevicesSectionLifecycleTest` passed together on the API 35 emulator (`emulator-5554`): `OK (2 tests)`. The lifecycle-state test also verifies the source StateFlow retains its value while stopped, and that a replacement value is received after restart. `:feature:fido2:assembleAndroidTest :feature:fido2:ktlintCheck` passed. APK SHA-256: `a22c311c132765b6b0175c8051d8387a1b5de92ef320d046aa8923fc81c5064b`. Test APK was uninstalled afterward.

The runtime case uses a synthetic string StateFlow with the same lifecycle-reset helper; it does not dispatch a real CTAP request, exercise the system FIDO host or inspect deferred/event-bus request bytes. Evidence supports the generic UI-owner reset and source wiring only. Event-bus retention is intentional while the operation remains active; request-model, transport/provider and heap lifetimes remain open. No renderer/accessibility erasure claim is made.
