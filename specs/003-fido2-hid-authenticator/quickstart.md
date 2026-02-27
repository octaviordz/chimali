# Quickstart: FIDO2 HID Authenticator

## Usage Flow

### 1. Pairing
1. Open Chimali on your Android device.
2. Navigate to **Virtual Authenticator** settings.
3. Enable **HID Emulation**.
4. On your PC (Windows/macOS/Linux), search for Bluetooth devices.
5. Pair with **Chimali Authenticator**.

### 2. Registration (Passkey Creation)
1. On a website (e.g., [webauthn.io](https://webauthn.io)), click **Register**.
2. Select **Security Key** (or use cross-device flow).
3. Confirm the registration on your phone via Biometrics.

### 3. Authentication
1. Click **Login** on the website.
2. The phone will receive a Bluetooth notification.
3. Tap the notification and authenticate via Biometrics.
4. Sign-in completes automatically on the PC.

## Troubleshooting
- Ensure Bluetooth is enabled on both devices.
- Verify the phone supports `BluetoothHidDevice` (Min SDK 28 + Hardware support).
- If the PC doesn't recognize the device as a FIDO2 key, try unpairing and re-pairing.
