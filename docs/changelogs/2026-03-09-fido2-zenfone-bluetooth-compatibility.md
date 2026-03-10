# Changelog - 2026-03-09 - Asus Zenfone 10 Bluetooth HID Compatibility & Automated Pairing

## Problem Statement
The FIDO2 Authenticator failed to start on the Asus Zenfone 10 (running Android 14) due to a combination of silent failures in the Bluetooth profile binding and a UI flow that didn't account for `STATE_OFF` (10) adapter conditions. 

- **Silent Hangs**: `BluetoothAdapter.getProfileProxy` would return `true`, but the `onServiceConnected` callback was never delivered, causing the initialization coroutine to hang indefinitely.
- **Strict Vendor Stacks**: The Asus Bluetooth stack rejected HID advertisements if custom QoS parameters were provided, even if they matched the spec.
- **UI Friction**: Starting the authenticator required the user to manually turn on Bluetooth and go to system settings to make the device discoverable for pairing.

## Solutions Implemented

### Resilient Profile Binding
- **Timeout and Retries**: Added a 5-second timeout to the `initialize()` method in `BluetoothHidDeviceWrapper.kt`. If the proxy callback is not received, the app now retries up to 3 times with exponential backoff (1s, 2s). This allows the Bluetooth daemon time to fully initialize without hanging the UI/foreground service.
- **Adapter State Tracking**: Added explicit logging of the `BluetoothAdapter.state` during initialization to detect `STATE_OFF` (10) vs `STATE_ON` (12) conditions.

### Automated Discoverability Flow
- **Single-Action Pairing**: Replaced `ACTION_REQUEST_ENABLE` with `ACTION_REQUEST_DISCOVERABLE` in `Fido2HomeScreen.kt`. This launches a single system prompt that:
    1. Turns on Bluetooth if it's off.
    2. Makes the phone visible to Windows for pairing (120s duration).
    3. Triggers the Authenticator start upon user approval.
- **In-App Feedback**: Added an `AlertDialog` to handle cases where the user denies visibility or Bluetooth, providing clear guidance on why the authenticator won't start.

### HID Compatibility Enhancements
- **SDP Settings**: Optimized `BluetoothHidDeviceAppSdpSettings` to use `SUBCLASS1_COMBO`, ensuring the device is correctly identified by the Asus/Qualcomm stack.
- **Default QoS**: Removed explicit `BluetoothHidDeviceAppQosSettings` from `registerApp()`. By passing `null`, the app now relies on the system default QoS, which prevents silent rejections from strict vendor implementations that don't support custom QoS.

## Verification
- Verified on a real Asus Zenfone 10 device.
- Logcat confirmed the transition from State 10 (Off) to 12 (On) through the new prompt.
- Verified Windows can see and pair with the device immediately after granting discoverability.
