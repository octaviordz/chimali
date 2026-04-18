# Changelog: 2026-04-18 - FIDO2 Koin DI and Bluetooth Runtime Permission Fixes

## Overview
This update resolves two major runtime crashes in the FIDO2 feature module. First, we addressed Koin Dependency Injection `NoDefinitionFoundException` errors during component instantiation. Second, we fixed a `SecurityException` triggered by modern Android (API 31+) strict Bluetooth permission constraints.

## Changes

### 1. Koin DI Annotation Resolution
- Identified that `BluetoothHidDeviceWrapper` and `HidReportParser` were missing Koin `@Single` annotations, preventing KSP from generating DI definitions.
- Annotated both `BluetoothHidDeviceWrapper` and `HidReportParser` in `feature/fido2/src/main/kotlin/com/chimali/fido2/bluetooth/` with `@org.koin.core.annotation.Single`.
- Regenerated Koin dependency graph via KSP tasks (`.\gradlew :feature:fido2:kspDebugKotlinAndroid`) successfully.
- Resolved cascading `NoDefinitionFoundException` failures stemming from `BluetoothHidTransportImpl` which effectively crashed the FIDO2 view models upon loading.

### 2. Android 12+ Bluetooth Permission Handling
- Fixed a `SecurityException` (`Permission Denial: starting Intent { act=android.bluetooth.adapter.action.REQUEST_DISCOVERABLE... } requires android.permission.BLUETOOTH_CONNECT`) triggered when users interacted with the "Start Authenticator" button.
- Updated `Fido2HomeScreen` to explicitly check and dynamically request `android.Manifest.permission.BLUETOOTH_CONNECT` and `android.Manifest.permission.BLUETOOTH_ADVERTISE` via `ActivityResultContracts.RequestMultiplePermissions` if running on Android API 31+ during the toggling of the transport UI.
- Improved user experience by wrapping intent dispatch into a `try-catch` block to display an informative UI error dialog instead of crashing the process if permission requests silently fail.

## Technical Notes
- **Tooling Engine**: The build system requires KSP (`kspDebugKotlinAndroid`) to pick up new module graph topologies. Always re-run KSP when introducing or altering DI scope annotations (`@Single`, `@Factory`) in `src/main` layers.
- **Permissions**: Starting an intent for `ACTION_REQUEST_DISCOVERABLE` implicitly requires explicit authorization in the background for `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` since Android 12, regardless of previously authorized `Manifest` declarations alone.

## Next Steps
- Validate overall application stability on real hardware running API 31+.
- Proceed with verification of complete FIDO2 Bluetooth HID connectivity with host platforms.
