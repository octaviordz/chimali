package com.chimali.fido2.platform

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * T191 — Android `actual` implementation of [PlatformBluetoothHid].
 *
 * Checks whether the device supports the Bluetooth HID Device profile using
 * PackageManager feature flags (no permissions required for capability checks).
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformBluetoothHid(
    private val context: Context,
) {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    /**
     * True if the device hardware supports the Bluetooth HID Device profile.
     * BluetoothHidDeviceWrapper requires this to be true before calling initialize().
     */
    actual fun isSupported(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
            bluetoothManager?.adapter != null

    /**
     * True if the Bluetooth adapter is currently powered on.
     * Callers should check this before attempting to register the HID app.
     */
    actual fun isAdapterEnabled(): Boolean =
        runCatching {
            bluetoothManager?.adapter?.isEnabled == true
        }.getOrDefault(false)
}
