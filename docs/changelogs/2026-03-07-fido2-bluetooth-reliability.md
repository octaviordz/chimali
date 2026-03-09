# FIDO2 Bluetooth HID Connection Reliability Fixes

**Date:** 2026-03-07

## Overview

This update hardens the Android `BluetoothHidDeviceWrapper` implementation to seamlessly handle non-compliant Bluetooth stacks from various Android manufacturers (specifically identified on Motorola devices like the Moto G Stylus).

The connection logic has been overhauled to clear phantom L2CAP sockets and gracefully recover from dropped asynchronous Bluetooth registrations, significantly improving connection success rates during FIDO2 WebAuthn cross-device flows.

## What Was Fixed

### 1. Phantom Connection Clearing
Certain OEM Bluetooth stacks have a race condition where they falsely report an active `pluggedDevice` the moment `BluetoothHidDevice#registerApp` is called. The OS incorrectly believes an L2CAP socket is already occupied, which silently drops the actual incoming WebAuthn `CTAPHID_INIT` connection from the Windows PC.
- **Resolution:** Added explicit teardown logic in `onAppStatusChanged`. If a phantom device is reported upon registration, we trigger `hidDevice?.disconnect(pluggedDevice)` to forcefully close the stale socket and free up the line for the real host.

### 2. Dropped Callback Hang Prevention
The Android framework's `registerApp` asynchronous call is inherently unreliable on older hardware. The low-level Bluetooth service occasionally returns `false` or drops the expected `registered=true/false` callback entirely, leaving the Coroutine suspended indefinitely, freezing the "Start Authenticator" UI.
- **Resolution:** Wrapped the registration coroutine in a `withTimeoutOrNull(5000L)` block.
- **Resolution:** Introduced a robust retry loop with exponential backoff (`1000ms`, `2000ms`, `4000ms`, max 3 attempts) to cleanly recover from temporary Bluetooth service congestion.

### 3. HID Subclass Redefinition (Profile Splitting Bug)
The previously used `BluetoothHidDevice.SUBCLASS1_COMBO` SDP value caused some strict Windows 11 drivers to interpret the Android authenticator as a composite device, creating two separate UI entries (a Phone icon and a Laptop/Screen icon). This caused routing confusion, leading Windows to loop endlessly between connected and disconnected states.
- **Resolution:** Changed the SDP registration attribute to `BluetoothHidDevice.SUBCLASS1_NONE` (Unspecified HID device) to align accurately with raw FIDO Security Keys, preventing Windows from aggressively splitting the Bluetooth profile.

## Affected Components

- `feature/fido2/src/main/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt`

## Verification

- **Code Inspection:** Coroutines no longer hang indefinitely. Validated on an Asus Zenphone for regression testing and Moto G Stylus for edge-case recovery tracking.
- **Bluetooth SDP:** The Android app correctly presents itself as a FIDO Security Key without falsely claiming keyboard/mouse hardware properties.
