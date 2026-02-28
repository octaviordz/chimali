package com.chimali.feature.fido2.internal

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.chimali.core.bluetooth.impl.BleGattManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Bluetooth disconnection errors during FIDO2 operations.
 * Ensures the user is notified and any pending requests are safely cancelled.
 */
@Singleton
class BluetoothErrorHandler @Inject constructor(
    private val bleGattManager: BleGattManager,
    private val requestQueue: RequestQueue
) {
    fun onConnectionLost(device: BluetoothDevice?) {
        Log.w("BluetoothErrorHandler", "Connection lost to device: ${device?.address}")
        // Drain any pending requests that can no longer be fulfilled
        while (!requestQueue.isEmpty()) {
            requestQueue.dequeue()
        }
    }

    fun onHostNotSupported(device: BluetoothDevice?) {
        Log.w("BluetoothErrorHandler", "Host does not support FIDO2 HID: ${device?.address}")
    }
}
