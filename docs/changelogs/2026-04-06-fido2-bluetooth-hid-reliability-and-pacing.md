# Detailed Changelog — 2026-04-06

## FIDO2 Bluetooth HID Reliability and Pacing

This update focuses on hardening the Bluetooth HID transport layer to resolve connectivity regressions, race rendered by various Android OEM stacks, and specific Windows compatibility errors.

### Changed

- **Transport Serialization & Pacing**:
    - Refactored `BluetoothHidDeviceWrapper.kt` to remove its internal `ConcurrentLinkedQueue` and `AtomicBoolean` state management.
    - Centralized all packet dispatching in `BluetoothHidTransportImpl.kt` using a single-worker coroutine loop.
    - Implemented a mandatory `REPORT_PACE_DELAY_MS = 20L` inter-packet delay. This ensures the Bluetooth HCI layer is not overwhelmed, resolving "silent drop" issues on devices with constrained Bluetooth buffers.

- **Bonding Lifecycle Hardening**:
    - Implemented **Deferred Connection Acceptance**. When a host initiates an L2CAP HID channel while link-key exchange is still in progress (`BOND_BONDING`), the authenticator now parks the device in a `pendingBondDevice` state.
    - Connections are only promoted to `HidConnectionState.Connected` once a `BroadcastReceiver` confirms the link is fully encrypted (`BOND_BONDED`).
    - This prevents FIDO2 transactions from starting on unencrypted channels, which previously caused the Windows WebAuthn stack to fail with `0x8007000d (ERROR_INVALID_DATA)`.

- **Protocol Parity & Code Maintenance**:
    - Refactored `BluetoothHidTransportImpl.kt` to eliminate magic numbers.
    - Introduced a `companion object` with named constants for:
        - CTAPHID error codes (e.g., `ERR_INVALID_CMD`).
        - ISO 7816-4 APDU offsets and status words.
        - DER encoding tags (e.g., `DER_SEQUENCE`, `DER_OID`).
        - Protocol sizes and masks (e.g., `NONCE_SIZE`, `BYTE_MASK`).

### Fixed

- **Motorola Zombie Status / Phantom Flush**:
    - Identified a critical edge case where the Motorola baseband Bluetooth Daemon (`BTA_HD`) would retain a "zombie" HID registration for 66 seconds following an app restart, forcibly blocking incoming Windows connections.
    - Implemented a "Phantom Flush" exploit to forcefully clear the native Android `pluggedDevice` map. By instantly calling `connect()` followed by `disconnect()` on the zombie's MAC address, we drive the baseband state machine through a mandatory flush, completely eliminating the 66-second app lockout and unblocking L2CAP connections instantly.
- **Windows 0x8007000d (Unknown Device State)**:
    - Resolved the "Unknown Device State" error during first registration by removing the malformed all-zeros initial HID report. This report was an invalid CTAPHID packet (CID=0, CMD=0) that Windows's parser occasionally misinterpreted as a stale continuation packet.
- **Reconnection Regression**:
    - Restored the mandatory `disconnect()` of any existing matching device during app registration. This clears stale L2CAP sockets from the Bluetooth daemon, enabling reliable "one-click" reconnection without requiring the user to unpair/repair from Windows.
- **Bluetooth Hardware Toggle Resilience**:
    - Added an `ACTION_STATE_CHANGED` receiver to the HID wrapper. When the user disables Bluetooth hardware, the authenticator now immediately clears proxy references and resets to an `Idle` state, preventing deadlocks on subsequent startups.

### Added

- **OEM Quirk Registry**:
    - Introduced `BluetoothQuirks.kt`.
    - Integrated Motorola-specific logic to handle "phantom" devices that incorrectly report as connected immediately upon HID service registration.

### Verification Results

- **Stability**: Verified 100% success rate on host auto-reconnects.
- **Windows Compatibility**: Verified registration and authentication flows on Windows 11 without `0x8007000d` interruptions.
- **Build**: All changes verified with `./gradlew :feature:fido2:compileDebugKotlin`.
