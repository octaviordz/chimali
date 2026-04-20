# FIDO2 Android 14 Foreground Service and Notification Fixes

**Date**: 2026-04-19
**Scope**: `:feature:fido2`, `:app`
**Status**: `Unreleased`

## Problem Context
The FIDO2 "Register" (MakeCredential) ceremony was failing on Android 14+ devices due to stricter background-to-foreground transition rules. Specifically, the `Fido2TransportService` was being denied the `START_FOREGROUND` operation because it lacked a mandatory foreground service type declaration. Additionally, the service could not display its mandatory ongoing notification on Android 13+ because the `POST_NOTIFICATIONS` permission was neither declared nor requested.

## Changes Implemented

### 1. Foreground Service Compliance (Android 14+)
- **Service Type**: Updated `Fido2TransportService.kt` to include `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` in the `startForeground()` call.
- **Manifest Declarations**: Added `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` permissions to `feature/fido2/src/main/AndroidManifest.xml` to satisfy Android 14+ requirements for HID/Connected Device services.

### 2. Notification Permissions (Android 13+)
- **Manifest Update**: Added `android.permission.POST_NOTIFICATIONS` to both the `app` and `feature:fido2` manifests.
- **Startup Logic**: Expanded the `MainActivity` startup permission gate (T191a) to include `POST_NOTIFICATIONS` in the `ActivityResultLauncher` request for Android 13+ (API 33+). This ensures the user is prompted for notification permissions alongside Bluetooth permissions at first launch.

### 3. Permission Request Refactoring
- **SDK Targeting**: Refined the `MainActivity` permission request logic to differentiate between:
    - **Android 13+**: Requests Nearby Devices + Notifications.
    - **Android 12**: Requests Nearby Devices only.
    - **Legacy**: (No change to existing logic).

## Verification Results
- **Build**: `./gradlew assembleDebug` completed successfully.
- **Static Analysis**: Verified manifests follow the required security and foreground service patterns.
- **Manual Verification (Pending)**: Requires testing on a physical Android 14 device to confirm the permission dialog appears and the HID transport persists during registration.

## Impact & Security
- **Reliability**: Ensures the Bluetooth HID transport remains active when the app is backgrounded during a FIDO2 ceremony.
- **Transparency**: Complies with Android 13+ user notification requirements for background services.
- **Compliance**: Adheres to the "Reliable Bluetooth connectivity" mandate defined in Phase 8 (T191a) of the FIDO2 implementation plan.

---
*Refs: 004-fido2-hid, FR-HID-010, T195, T196, T197*
