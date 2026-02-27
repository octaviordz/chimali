# CTAP2 over HID Contract

This document defines the interface contract between the Android Bluetooth HID service and the Rust-based CTAP2 protocol handler.

## HID Report Descriptor

The device registers with a standard FIDO HID descriptor (Usage Page `0xF1D0`, Usage `0x01`).

| Direction | Report ID | Type | Payload |
|-----------|-----------|------|---------|
| Host -> Device | 0x01 | Interrupt Out | 64-byte CTAP packet |
| Device -> Host | 0x01 | Interrupt In | 64-byte CTAP packet |

## CTAP2 Protocol Layer (Rust)

The Rust layer exposes a `CtapProcessor` via UniFFI.

```rust
trait CtapProcessor {
    fn process_packet(packet: Vec<u8>) -> CtapResponse;
}

enum CtapResponse {
    PendingUserPresence,
    Success(Vec<u8>),
    Error(u8),
}
```

## Android HID Service

The Kotlin service handles the low-level `BluetoothHidDevice` callbacks and forwards payloads to the Rust processor.

1.  `onGetReport`: Return status.
2.  `onSetReport`: Parse CTAP packet and send to Rust.
3.  `sendReport`: Send response from Rust back to host.
