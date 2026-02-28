# Technical Analysis: FIDO2 BLE Connection & "Insert USB" Issue

## Executive Summary
Despite a full migration from Classic Bluetooth HID to BLE GATT and multiple protocol-level fixes, Windows 10/11 continues to display the "Insert your security key into a USB port" prompt during WebAuthn ceremonies. This document outlines the technical steps taken, the logic behind them, and the remaining hypotheses for the persistent failure.

---

## 🛠 What has been implemented & optimized

### 1. Transport Pivot (Architectural)
*   **Action**: Removed `BluetoothHidDevice` (Classic) and implemented a full **BLE GATT Server** (`BleGattManager`).
*   **Reason**: Windows WebAuthn discovery prioritizes BLE for external security keys. Classic HID is often filtered out to prevent interference with keyboards/mice.

### 2. Advertising & Discovery (Handshake Level)
*   **Service UUID**: broadcasting standard FIDO Service UUID `0xFFFD`.
*   **Service Data**: Added the mandatory **FIDO Service Revision Bitfield** (0x20) to the advertising packet.
*   **Scan Response**: Moved the Device Name to the scan response to ensure the primary advertisement stays under the 31-byte legacy limit for maximum compatibility.
*   **Identity (Appearance)**: Added the `Appearance` characteristic (`0x0181` - **Security Key**) to the Generic Access Service to help Windows categorize the device.

### 3. Spec Compliance (GATT Level)
*   **Service Revision Format**: Corrected the `Service Revision` characteristic from a raw bitfield to the UTF-8 string `"1.2"` as required by the FIDO BLE 1.2 specification.
*   **Permission Relaxation**: Lowered GATT characteristic permissions from `PERMISSION_WRITE_ENCRYPTED` to standard `PERMISSION_WRITE` for discovery. 
    *   *Logic*: Windows often needs to read the device metadata *before* it initiates a secure bond. If reading is blocked by encryption requirements at the start, Windows fails the discovery.

### 4. Data Integrity (Transmission Level)
*   **MTU Fragmentation**: Fixed a bug in `FidoBleFraming.kt` where fragment sizes were exceeding (MTU - 3). This was likely causing packet truncation on Windows, leading to silent failures during the `authenticatorGetInfo` exchange.
*   **Ctap2 GetInfo**: expanded the `authenticatorGetInfo` CBOR response to include `uv` (User Verification) and `rk` (Resident Key) support flags, making the device appear as a high-capability authenticator.

---

## 🔍 Remaining Hypotheses

### 1. Windows GATT Caching
Windows is notorious for caching GATT services. If the device was previously paired as a HID device, Windows may be refusing to re-scan the services to see the new FIDO/DIS/GAP services.
*   **Try**: Turning Bluetooth OFF/ON in Windows *and* unpairing/re-pairing.

### 2. Advertising Completeness
Some versions of Windows require the `Appearance` (0x0181) to be present in the **Advertising Data** itself, rather than just the GATT characteristic. 
*   **Constraint**: Adding this might exceed the 31-byte limit, requiring an update to "Extended Advertising" (supported on Android 8+, but not all Bluetooth chips).

### 3. AAGUID Trust
We are currently sending a "dummy" AAGUID (`00...01`). If Windows or the browser (Edge/Chrome) performs an attestation check against a known metadata service (FIDO MDS), it might reject the device as "untrusted" or "unknown," falling back to the generic USB prompt.

### 4. Pairing Type
Windows supports different types of Bluetooth pairing (Just Works vs Passkey). If the bond is not established with sufficient security levels, the FIDO stack might refuse to send sensitive credentials.

---

## 📋 Diagnostic Data Needed
To solve this, we would ideally need:
1.  **Android Bluetooth HCI Snoop Log**: (Developer Options -> Enable Bluetooth HCI Snoop Log). Allows us to see exactly what Windows is asking for and what the phone is responding with.
2.  **Windows Event Viewer**: Checking the `HardwareEvent` or `WebAuthn` logs to see why the BLE transport was discarded.
