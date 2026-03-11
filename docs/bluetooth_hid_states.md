# Bluetooth HID State Machine

This document defines the high-level state machine for the Chimali Bluetooth HID Authenticator. It maps the Android `BluetoothHidDevice` states to the logical FIDO2 authenticator lifecycle.

## State Diagram

```mermaid
stateDiagram-v2
    direction TB

    [*] --> Idle : Initial / Unpaired

    state "Setup & Discovery" as Discovery {
        Advertising
        Pairing
        Paired
    }

    state "Connection Lifecycle" as Connection {
        Connecting
        Connected
    }

    Idle --> Advertising : startAdvertising()
    Advertising --> Pairing : Host initiates Bonding
    Pairing --> Paired : Bond Created

    Paired --> Connecting : OS Connection Request
    Connecting --> Connected : L2CAP Tunnel Established
    Connected --> Advertising : OS Disconnected (Idle/Sleep)

    Advertising --> Error : Registration failure
    Connecting --> Error : Connection failure
    Error --> Idle : stop() / reset

    Connected --> Idle : stop() / App Unregistered
    Paired --> Idle : stop() / App Unregistered
    Advertising --> Idle : stop() / App Unregistered

    note right of Advertising
        Device is Discoverable
        to new hosts.
    end note

    note right of Connected
        Active FIDO2 session.
        HID reports can be sent.
    end note
```

## State Definitions

| State | Description |
| :--- | :--- |
| **Idle / Unpaired** | Initial state. No Bluetooth profiles are active or registered. |
| **Advertising** | The HID app is registered and the device is **Discoverable**. Ready for initial pairing or reconnection. |
| **Pairing** | The temporary state during the Bluetooth bonding handshake. |
| **Paired / Disconnected** | The device is bonded with a host (e.g., Windows) but the active L2CAP HID channel is closed. |
| **Connecting** | The host has initiated a connection to the HID profile (L2CAP handshake in progress). |
| **Connected** | The active state. The FIDO2 tunnel is established and HID reports (FIDO2 commands) are being exchanged. |
| **Error** | An unrecoverable error occurred during HID profile registration or connection. |

## Transitions

- **startAdvertising()**: Registers the HID SDP record and makes the device discoverable.
- **Pairing Success**: Moves from discovery to a stable bonded state (`Paired`).
- **OS Connection**: Windows/Android OS typically manages the connection lifecycle automatically once paired.
- **OS Disconnected**: When the active L2CAP channel drops, the device immediately transitions back to `Advertising` to listen for future connections.
- **stop()**: Unregisters the HID application and returns the device to a dormant `Idle` state.
