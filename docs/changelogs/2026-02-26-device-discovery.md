# Bluetooth Device Discovery & Runtime Permissions

**Date:** 2026-02-26  
**Type:** Feature Addition & Compatibility Fix  

## Overview
Implemented Bluetooth device discovery and fixed a critical runtime permission crash on Android 12+ devices, allowing users to safely scan, pair, and connect to new devices directly from the FIDO2 Virtual Authenticator management screen.

## Changes Implemented

### 1. Bluetooth Runtime Permissions (Android 12+)
* **`MainActivity.kt`**: Added an `ActivityResultLauncher` to explicitly request `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, and `BLUETOOTH_CONNECT` permissions before allowing the user to access the Device Manager.
* **`FIDO2 Feature`**: Implemented permission guards to prevent `SecurityException` crashes when retrieving bonded devices or initiating connections.

### 2. Bluetooth Device Discovery (Scanning)
* **`HidManager.kt`**: 
    * Implemented `startDiscovery()` and `stopDiscovery()` using `BluetoothAdapter`.
    * Added a `BroadcastReceiver` to actively listen for `BluetoothDevice.ACTION_FOUND` and surface dynamically discovered devices.
    * Exposed `discoveredDevices` and `isScanning` as reactive `StateFlow` streams.
* **`FidoMvi.kt` & `FidoViewModel.kt`**: Expanded state management to handle `StartScan`, `StopScan`, and `PairDevice` intents.

### 3. UI/UX Enhancements
* **`DeviceManagerScreen.kt`**: 
    * Implemented Jetpack Compose Material 3 `PullToRefreshBox`. Pulling down on the screen now dynamically scans for nearby advertising Bluetooth devices.
    * Split the UI into two distinct sections: "Paired Devices" and "Available Devices".
    * Added logic to dynamically render "Connect" or "Disconnect" buttons based on the live `BluetoothHidDevice` connection state.
    * Added a `onConnectionStateChanged` callback to log the granular connection lifecycle of the HID profile proxy.

## Impact
The FIDO2 Authenticator feature is now fully self-contained for device management. Users can find and pair with target hosts (PCs, laptops) directly within the Chimali application without needing to navigate to the Android system Bluetooth settings.
