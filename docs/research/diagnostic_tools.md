# Chimali: Bluetooth & FIDO2 Diagnostic Tools

This document contains recommended tools, commands, and filters for debugging the FIDO2 Bluetooth HID transport layer and the underlying Android Bluetooth stack.

## 1. Android Studio Logcat Filters

To isolate the FIDO2 Bluetooth handshake and monitor the hidden system-level events (bonding, L2CAP socket management, and baseband driver status), use the following combined filter in the Android Studio Logcat window.

### Comprehensive Bluetooth HID Filter
```text
package:mine | tag:BluetoothHidDevice | tag:bt_btif | tag:btif_hd | tag:BTA_HD | tag:BluetoothAdapter | tag:BluetoothBondStateMachine
```

#### Logical Breakdown of Tags:
*   **`package:mine`**: Standard application logs from Chimali.
*   **`BluetoothHidDevice`**: High-level status logs from our `BluetoothHidDeviceWrapper.kt`.
*   **`bt_btif` / `btif_hd`**: Internal logs from the Android Bluetooth Interface (BTIF). Crucial for seeing when registration fails at the JNI/daemon level.
*   **`BTA_HD`**: Logs from the Bluetooth Application (BTA) layer. This is where protocol-level rejections (like MTU failures) are often visible.
*   **`BluetoothAdapter`**: Tracks hardware state changes (Enabling/Disabling).
*   **`BluetoothBondStateMachine`**: Monitors the pairing and bonding lifecycle. Use this to verify if Windows is correctly negotiating a secure link before attempting HID data transfer.

---

## 2. Windows 11 Diagnostics

When the Android logs show a successful "Connected" state but Windows fails to register the security key, monitor the following:

### Event Viewer
1.  Open **Event Viewer** (`eventvwr.msc`).
2.  Navigate to: `Applications and Services Logs` -> `Microsoft` -> `Windows` -> `DeviceSetupManager`.
3.  Look for errors related to **WebAuthn** or **HID-compliant device**.

### Device Manager
*   Verify that **"FIDO2 Bluetooth HID Device"** appears under **Human Interface Devices** when connected.
*   If it appears with a yellow exclamation mark, right-click -> Properties -> Events to check for **Device Not Started** errors (often caused by Report Descriptor mismatches).
