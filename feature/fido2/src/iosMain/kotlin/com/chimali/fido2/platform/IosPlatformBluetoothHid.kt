package com.chimali.fido2.platform

/**
 * T191/T192 — iOS placeholder implementation of [PlatformBluetoothHid].
 *
 * Bluetooth HID peripheral mode on iOS requires CoreBluetooth and explicit
 * entitlements not yet configured. Returns false for all queries as a safe
 * placeholder until CoreBluetooth peripheral support is implemented.
 *
 * @see <a href="https://developer.apple.com/documentation/corebluetooth">CoreBluetooth</a>
 */
class IosPlatformBluetoothHid : PlatformBluetoothHid {
    /**
     * iOS: placeholder — Bluetooth HID peripheral mode requires CoreBluetooth
     * entitlements and is not yet configured for this iOS target.
     */
    override fun isSupported(): Boolean = false

    /**
     * iOS: placeholder — returns false until CBCentralManager state is integrated.
     */
    override fun isAdapterEnabled(): Boolean = false
}
