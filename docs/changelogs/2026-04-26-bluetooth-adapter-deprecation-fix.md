# Changelog Details: BluetoothAdapter Deprecation Fix (2026-04-26)

## Summary
Updated `BluetoothHidAuthenticatorImpl` to address the deprecation of `BluetoothAdapter.getDefaultAdapter()` and refined internal KDoc documentation.

## Changes
- **BluetoothManager Migration**: Migrated the Bluetooth adapter acquisition logic. Replaced the globally deprecated `BluetoothAdapter.getDefaultAdapter()` with modern system service retrieval: `context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager`.
- **Documentation Polish**: Fixed outdated class references in KDocs within `BluetoothHidAuthenticatorImpl`. `BluetoothHidWrapper` references have been correctly updated to `BluetoothHidDeviceWrapper`.

## Impact
- Ensures compatibility with future Android versions where `getDefaultAdapter()` might be restricted or unsupported.
- Improves code readability and maintainability.
