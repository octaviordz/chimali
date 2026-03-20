# Manual Testing Protocol: NFR-PERF-030 and SC-004 (T161)

**Feature**: FIDO2 Virtual Authenticator via Bluetooth HID Device
**Objective**: To manually verify device compatibility, Bluetooth stability, and successful CTAP2 ceremony operations across distinct hardware targets, fulfilling task `T161`.

## Pre-requisites
1. Android device with Chimali installed (API 28+).
2. Host machine(s) equipped with Bluetooth 4.0+.
3. WebAuthn test site (e.g., [webauthn.io](https://webauthn.io) or [passkeys.io](https://www.passkeys.io)).

---

## 1. Device Pairing & Discovery
*Goal: Ensure the Android device emits the correct SDP records and connects as an HID device.*

- [ ] **Test Windows 11**:
  - Open Settings > Bluetooth & Devices.
  - Enable "Start Authenticator" in Chimali.
  - Pair the Android device.
  - **Expected**: Device pairs successfully and is recognized rapidly. No driver errors.

- [ ] **Test macOS** (If available):
  - Open System Settings > Bluetooth.
  - Pair the Android device.
  - **Expected**: Device pairs and is recognized as an input/security device.

## 2. FIDO2 Registration (MakeCredential)
*Goal: Successfully register a new passkey via the Virtual Authenticator.*

- [ ] Navigate to `webauthn.io` on the connected host.
- [ ] Enter a test username (e.g., `test-user-1`).
- [ ] Select "Register".
- [ ] **Android Device**:
  - **Expected**: Chimali automatically intercepts the HID protocol request and displays the "Create Passkey" biometric prompt.
- [ ] Authenticate using Biometrics/PIN on Android.
- [ ] **Host Machine**:
  - **Expected**: `webauthn.io` confirms successful registration.

## 3. FIDO2 Authentication (GetAssertion)
*Goal: Successfully authenticate using the previously stored passkey.*

- [ ] Navigate to the login section of `webauthn.io`.
- [ ] Enter the username `test-user-1` and select "Authenticate".
- [ ] **Android Device**:
  - **Expected**: Chimali intercepts the request, correctly identifies the stored Passkey for `webauthn.io`, and displays the "Use Passkey" biometric prompt.
- [ ] Authenticate via Biometrics/PIN.
- [ ] **Host Machine**:
  - **Expected**: `webauthn.io` confirms successful login.

## 4. Connectivity Stress Test (SC-003: Persistent HID Availability)
*Goal: Ensure the GATT/Bluetooth connection does not drop prematurely during extended use.*

- [ ] Leave the Android device paired and connected to the host for **10 minutes** without initiating any FIDO2 requests.
- [ ] After 10 minutes, initiate a FIDO2 Authentication request from the host.
- [ ] **Expected**: The Android device immediately receives the request without dropping the HID session or requiring a manual reconnect/restart of the Authenticator service.

## 5. Teardown
- [ ] Disconnect and Unpair the Android device from the host's Bluetooth settings.
- [ ] Verify the Chimali app correctly shuts down the foreground service without crashing.
