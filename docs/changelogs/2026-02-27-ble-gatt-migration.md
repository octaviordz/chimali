# FIDO2 BLE GATT Transport Migration

## Context
The previous implementation used Classic Bluetooth HID (`BluetoothHidDevice`). While this allowed the phone to pair as a "Combo" HID device, Windows WebAuthn excludes Classic HID devices from Security Key discovery. Windows requires FIDO Security Keys to use **Bluetooth Low Energy (BLE)** with the FIDO GATT Service (UUID `0xFFFD`).

## Changes
- **BleGattManager**: Implemented a full FIDO GATT Server with Control Point, Status (Notify), and Length characteristics.
- **Spec Alignment (Windows Discovery)**:
    - **Service Revision**: Corrected format from bitfield to required "1.2" UTF-8 string.
    - **Appearance Characteristic**: Implemented the `Generic Access` service with the `Appearance` value `0x0181` (Security Key) to ensure OS-level categorization.
    - **Permissions**: Relaxed GATT permissions from `ENCRYPTED` to standard `READ/WRITE` to allow Windows to perform initial discovery before secure bonding.
- **Advertising**:
    - **Service Data (0xFFFD)**: Included the FIDO Revision bitfield in the advertising payload (Mandatory for Windows).
    - **Scan Response**: Moved the device name to the scan response to stay within the 31-byte legacy advertising limit.
    - **Performance**: Switched to `LOW_LATENCY` mode and `HIGH` power for faster discovery.
- **Reliability Fixes**:
    - **MTU Fragmentation**: Fixed a critical bug in `FidoBleFraming.kt` where packet offsets were calculated incorrectly relative to MTU, causing truncated responses.
    - **CTAP2 GetInfo**: Enhanced the `authenticatorGetInfo` CBOR response to include `uv` (User Verification) and `rk` (Resident Key) support flags.
- **Diagnostics**: Added a "Hardware Limited" warning in the UI for phones that do not support BLE Peripheral (Advertising) mode.

## Impact
Windows WebAuthn now recognizes the Chimali application as a valid Security Key over Bluetooth. The systematic alignment with the FIDO BLE 1.2 specification ensures that Windows defaults to using the BLE transport rather than prompting for a USB key insertion.
