# Changelog: FIDO2 Bluetooth HID Connectivity and Windows Protocol Fixes

## Date: 2026-04-08

## Overview
This update addresses critical regressions and protocol mismatches in the FIDO2 Bluetooth HID implementation that prevented stable connectivity and registration on Asus and Motorola devices. The fixes focus on protocol signaling, packet framing accuracy, and hardware-specific connection management.

## Fixed

### 1. CTAPHID Framing Correction (64-byte Alignment)
- **Problem**: `HidReportParser` was incorrectly assuming a 62-byte packet size. This caused multi-packet CBOR responses (like `GetInfo`) to be corrupted because Android expanded the 62-byte arrays to 64 bytes by adding two `0x00` padding bytes. Windows interpreted these padding bytes as part of the FIDO payload, breaking the CBOR stream.
- **Fix**: Aligned `HID_PACKET_SIZE` to exactly 64 bytes in `HidReportParser.kt`, matching the HID Descriptor and actual Android `onInterruptData` delivery.
- **Impact**: Resolves `GetInfo` corruption and "Unknown Device State" (0x8007000d) errors on Windows.

### 2. Windows U2F Polling Loop mitigation (`CAPABILITY_NMSG`)
- **Problem**: A previous cleanup commit removed the `CAPABILITY_NMSG` (0x08) bit. Without this bit, Windows assumes the device supports legacy U2F (CTAPHID_MSG) and sends `U2F_REGISTER` probes. Retrying these probes caused Windows to enter an infinite "Touch your security key" loop.
- **Fix**: Restored `CAPABILITY_NMSG` in the `CTAPHID_INIT` response. This signals to Windows that the device is CTAP2-only, causing it to skip the U2F probe and jump straight to `CTAPHID_CBOR`.
- **Impact**: Eliminates the "Touch your security key" polling loop on Windows registration.

### 3. Asus Bond Destruction Avoidance
-  **Problem**: Proactive `connect()` calls in the HID wrapper were triggering `btif_storage_remove_bonded_device` on Asus devices, leading to bond loss and connection failure.
-  **Fix**: Reverted the proactive connection logic in `BluetoothHidDeviceWrapper.kt`. The authenticator now relies on passive OS-level connection management, which is more stable across diverse Android vendor stacks.
-  **Impact**: Stabilizes connectivity on Asus Zenfone and similar Qualcomm-based devices.

### 4. U2F Escalation Logic
- **Fix**: Updated `BluetoothHidTransportImpl.kt` to return `SW_CONDITIONS_NOT_SATISFIED` (0x6985) for `U2F_REGISTER` requests. This is the spec-compliant way to signal "User Presence not yet confirmed," which combined with corrected capabilities, ensures a smooth escalation to CTAP2.

## Maintenance
- Added a high-visibility warning comment in `HidReportParser.kt` explaining the 64-byte invariant and the purpose of `CAPABILITY_NMSG` to prevent future "cleanup" regressions.
- Removed ~90 lines of dead U2F dummy certificate generation code.

## Verification
- Verified successful `CTAPHID_INIT` and `GetInfo` exchange on Motorola Logcat.
- Verified Windows 11 escalates correctly to `CTAPHID_CBOR` commands.
- Verified Asus Zenfone 10 maintains bond after connection.
