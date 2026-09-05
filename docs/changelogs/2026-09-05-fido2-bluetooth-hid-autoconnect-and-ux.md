# 2026-09-05 — Bluetooth HID Proactive Connectivity, Concurrency Fix, and Ready State UX

**Branch**: `052-migrate-sqlite3mc` / `lab/or/chimali`

## Summary

Resolved an issue where Chimali appeared stuck in the "Advertising..." state after initial pairing and was not passing through to "Connected". Investigated Bluetooth baseband link dynamics and Windows on-demand WebAuthn behavior, implemented proactive outbound connection handling for bonded hosts, resolved a concurrency race in `Fido2HomeViewModel`, and updated the dashboard UI status card to clearly indicate when the authenticator is ready for authentication requests.

---

## Root Cause Analysis

1. **Bluetooth Classic ACL Link Dormancy**:
   - Host computers (Windows 11 / macOS) do not maintain continuous, battery-draining L2CAP HID interrupt/control channels when idle.
   - When a host computer initiates a WebAuthn ceremony (e.g. `navigator.credentials.get()` on `webauthn.io`), the Windows Bluetooth stack wakes up the radio and establishes an ACL link.
   - For already bonded devices, Android does not re-broadcast `ACTION_BOND_STATE_CHANGED`, and `ACTION_ACL_CONNECTED` is only fired when the radio link transitions from disconnected to connected.
   - When Chimali was started while the devices were already paired, it sat in `Advertising` without checking whether an active ACL link existed.

2. **ViewModel Concurrency Race in Outbound Connect**:
   - In `Fido2HomeViewModel.connectDevice(macAddress)`, if the transport was `Idle`, the ViewModel simultaneously called `startForegroundService` and `fido2Transport.connectDevice(macAddress)`.
   - The transport immediately failed because the `BluetoothHidDevice` profile proxy had not yet finished initializing.

3. **Ambiguous UI State**:
   - The status indicator card displayed a pulsing circle with the text `Advertising...` and no descriptive subtitle.
   - Users naturally interpreted `Advertising...` as an unresolved intermediate loading or search state, even though the authenticator was fully registered, operational, and listening for on-demand ceremonies from the paired PC.

---

## Changes

### Modified Files

#### `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt`
- **Proactive Connection Scheduling (`scheduleProactiveConnect`)**:
  - Implemented single-flight, cancellable `proactiveConnectJob: Job?` to prevent duplicate or overlapping outbound connections.
  - Automatically cancels proactive connection attempts upon establishing `STATE_CONNECTED`, unregistering the app, or closing the wrapper.
- **Pre-Connected ACL Detection (`checkExistingConnectionsWhileAdvertising`)**:
  - Automatically queries bonded devices upon entering `HidConnectionState.Advertising` (and upon `registerApp` confirmation).
  - Uses `isAclConnected(device)` via reflection (`BluetoothDevice.isConnected()`) with strict, non-generic exception handling (`SecurityException`, `NoSuchMethodException`, `IllegalAccessException`, `InvocationTargetException`) adhering to DO-178B §XII guidelines.
  - Filters out non-host device classes (`BluetoothClass.Device.Major.AUDIO_VIDEO` and `WEARABLE`).
  - Proactively triggers `scheduleProactiveConnect` for any bonded host already connected via ACL.
- **Broadcast Receiver Hardening**:
  - Updated `ACTION_ACL_CONNECTED` and `ACTION_BOND_STATE_CHANGED` (`BOND_BONDED`) to route through `scheduleProactiveConnect` with statically bounded settling delays (`aclConnectDelay = 500ms`, `bondConnectDelay = 1s`).
  - Swallowed exception logging enforced across all device query blocks to satisfy Detekt rules.

#### `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModel.kt`
- **Eliminated Concurrency Race in `connectDevice`**:
  - When initiating a connection from an `Idle` or `Error` state, `connectDevice` starts `Fido2TransportService` and suspends until `connectionState` reaches `HidConnectionState.Advertising` (bounded by `SERVICE_STARTUP_TIMEOUT_MS = 10_000L`) before calling `fido2Transport.connectDevice(macAddress)`.

#### `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt`
- **Contextual Status Display in `StatusIndicator`**:
  - Updated `StatusIndicator` to accept `hasPairedDevices: Boolean`.
  - When in `Advertising` state with paired devices present:
    - **Title**: `Ready for Authentication`
    - **Subtitle**: `Waiting for request from PC`
  - When in `Advertising` state with no paired devices:
    - **Title**: `Advertising...`
    - **Subtitle**: `Ready to pair with a new host PC`
  - Added descriptive subtitles for `Idle` ("Tap 'Start Authenticator' to begin"), `Connecting` ("Establishing Bluetooth HID connection"), `Connected` ("Connected to PC"), and `Error` (message).
  - Aligned Composable parameter order with standard Compose conventions (`state`, `displayName`, `modifier`, `hasPairedDevices`).

#### `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/PairedDevicesSection.kt`
- **Explicit Connect Action**:
  - Added a dedicated Bluetooth `IconButton` in each paired device item's `trailingContent` alongside the edit button, allowing users to explicitly initiate an outbound connection to any paired host.
  - Formatted imports in lexicographic order.

#### `CHANGELOG.md`
- Added entry under `[Unreleased] - 2026-09-05` documenting the Bluetooth HID auto-connect and UI enhancements.

---

## Verification

1. **Static Analysis & Linters**:
   - `./gradlew :feature:fido2:ktlintCheck`: 0 violations.
   - `./gradlew :feature:fido2:detekt`: 0 violations.
2. **Automated Unit Tests**:
   - `./gradlew :feature:fido2:testAndroidHostTest`: All unit tests passed.
3. **Hardware / Logcat Verification**:
   - Moto G Stylus 5G (2022) paired with Windows 11 host `LOQ15IRX10-3`.
   - Verified initial bonding proactive connect (`BOND_BONDED → connect → CONNECTED(2)`).
   - Verified `webauthn.io` registration (`MakeCredential` completed, Ed25519 key generated, attestation returned).
   - Verified transport disconnect and reconnect (`Advertising`).
   - Verified on-demand WebAuthn authentication (`webauthn.io` trigger → `ACL_CONNECTED` → proactive connect in 500ms → `CONNECTED(2)` → `GetAssertion` signed in 6ms).
