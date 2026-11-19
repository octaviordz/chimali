# FIDO home-screen lifecycle state cleanup

**Finding:** SEC065-FIDO-013 — paired-device identifiers and pending-removal UI state remained owned by an active background composition.

`Fido2HomeScreen` now collects the connection state, connected display name and paired-device list only while the screen lifecycle is `STARTED`. When collection stops, the Compose state is reset to `Idle`, `null` or an empty list. `PairedDevicesSection` now collects removal events only while started; lifecycle cancellation dismisses the snackbar and restores the pending removal rather than committing it.

The synthetic lifecycle tests passed on the API 35 AOSP emulator (`emulator-5554`): `LifecycleClearedStateTest` verified alias-state reset and reload across stop/restart; `PairedDevicesSectionLifecycleTest` verified snackbar cancellation restores the synthetic device. Result: `OK (2 tests)`. `:feature:fido2:assembleAndroidTest :feature:fido2:ktlintCheck` passed. Android device-test APK SHA-256: `a22c311c132765b6b0175c8051d8387a1b5de92ef320d046aa8923fc81c5064b`.

Both device-test APKs were removed from the emulator after the run. No physical device was used for this lifecycle check. The foreground FIDO transport continues to own active Bluetooth connection state; this change covers the home-screen presentation owner and paired-device UI event collector. It does not establish renderer/accessibility snapshot release, immutable `String` erasure, heap absence, or framework/IME copies. Compose state clearing is lifecycle cleanup, not guaranteed memory erasure.
