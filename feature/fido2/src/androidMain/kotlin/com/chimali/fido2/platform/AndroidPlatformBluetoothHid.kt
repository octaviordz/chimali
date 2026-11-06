package com.chimali.fido2.platform

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Android implementation of [PlatformBluetoothHid].
 *
 * This is a non-initializing API/hardware preflight only. Android exposes no public
 * package-manager feature for the OEM HID_DEVICE profile service, so a positive result
 * does not guarantee that `getProfileProxy()` will bind. Actual profile availability is
 * determined during the selected Classic transport's activation.
 */
class AndroidPlatformBluetoothHid(
    private val context: Context,
) : PlatformBluetoothHid {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    /**
     * True when the Android HID Device API level and a Bluetooth adapter are present.
     * This does not verify the OEM profile service; activation still has to acquire the proxy.
     */
    override fun isSupported(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
            bluetoothManager?.adapter != null

    /**
     * True if the Bluetooth adapter is currently powered on.
     * Callers should check this before attempting to register the HID app.
     */
    override fun isAdapterEnabled(): Boolean =
        runCatching {
            bluetoothManager?.adapter?.isEnabled == true
        }.getOrDefault(false)
}
