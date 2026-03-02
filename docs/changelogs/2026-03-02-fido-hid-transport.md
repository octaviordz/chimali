# Changelog: FIDO2 HID Transport Implementation

**Date:** 2026-03-02
**Feature:** FIDO2 Virtual Authenticator (HID Transport)

## Summary
Implemented the core transport layer for FIDO2 authentication over Bluetooth HID. This allows the Android device to present itself as a standard security key (Usage Page `0xF1D0`) to host operating systems.

## Changes

### 1. Bluetooth HID Core (`core:bluetooth`)
- **FIDO-Compliant HID Descriptor**: Updated `BluetoothHidConstants` with a standard FIDO U2FHID report descriptor.
- **Android Compatibility Adjustments**:
    - Restricted HID report size to 62 bytes to fit within Android's 64-byte L2CAP MTU limit (accounting for HIDP/Report headers).
    - Matched QoS settings (Latency, Token Rate) to the `wiokey-android` reference implementation for stable Windows communication.
- **FIDO HID Framing Implementation**: Created `FidoHidFraming.kt` to handle:
    - Channel ID (CID) assignment and broadcast handling.
    - Message fragmentation into Initialization and Continuation packets.
    - Reassembly of incoming HID reports into complete CTAP messages.
- **Reliability & Race Condition Fixes**:
    - Implemented a `pendingAdvertise` mechanism in `BluetoothHidAuthenticatorImpl` to defer registration until the system's `BluetoothHidDevice` proxy is fully initialized.
    - Corrected callback signatures and method naming (`onInterruptData`) to match specific Android SDK behaviors and avoid runtime crashes.

### 2. Feature Implementation (`feature:fido2`)
- **Service Re-wiring**: Updated `FidoBleService` to inject and start the `BluetoothHidAuthenticator`.
- **Protocol Handshake**: Added initial support for the FIDO HID `INIT` command, allowing Windows to establish a shared Channel ID with the phone.
- **Message Dispatching**: Wires up a listener for complete FIDO messages, ready for integration with the Rust `passkey-rs` processor.

## Current Status
- [x] Transport Layer Implementation
- [x] HID Handwriting (INIT/CID assignment)
- [x] Build and Runtime Stability
- [/] Host OS Recognition: Windows acknowledges the device but requires further protocol responses (`GetInfo`) to complete pairing as a "Security Key".

## Next Steps
- Integrate `passkey-rs` to provide valid CTAP2 responses (`GetInfo`, `Authenticate`).
- Finalize host-to-authenticator user presence flow (UI confirmation).
