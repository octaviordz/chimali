# Quickstart: Fix Dev Tools QR scan button

## Development Setup

1. **Permissions**: Ensure your test device or emulator has the camera enabled.
2. **Debug Build**: This feature is only visible in the `debug` build variant (`BuildConfig.DEBUG == true`).
3. **QR Code**: Prepare a QR code containing a 24-word BIP39 mnemonic (space-separated).

## Testing the Fix

1. Launch the app and navigate to **Dev Tools** (via the navigation menu or debug trigger).
2. Scroll to the **Recover from Seed** section.
3. Tap the **Scan QR Code** button.
4. If prompted, grant camera permissions.
5. Point the camera at your QR code.
6. Verify that the scanner closes and a snackbar appears saying "Scanned 24 words."
7. Verify that the "Mnemonic validated successfully!" message appears.

## Troubleshooting

- **Button does nothing**: Check Logcat for "Permission Denied" or "Camera bind failed" errors.
- **Scanner shows black screen**: Ensure no other app is using the camera and that the `lifecycleOwner` is active.
- **QR not recognized**: Ensure the QR code has high contrast and contains exactly 24 words from the BIP39 English wordlist.
