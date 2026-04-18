package com.chimali.fido2.platform

/**
 * T191/T192 — iOS placeholder `actual` for [PlatformBluetoothHid].
 *
 * Bluetooth HID peripheral mode on iOS requires CoreBluetooth and explicit
 * entitlements not yet configured. Returns false for all queries as a safe
 * placeholder until CoreBluetooth peripheral support is implemented.
 *
 * @see <a href="https://developer.apple.com/documentation/corebluetooth">CoreBluetooth</a>
 */
actual class PlatformBluetoothHid {

    /**
     * iOS: placeholder — Bluetooth HID peripheral mode requires CoreBluetooth
     * entitlements and is not yet configured for this iOS target.
     */
    actual fun isSupported(): Boolean = false

    /**
     * iOS: placeholder — returns false until CBCentralManager state is integrated.
     */
    actual fun isAdapterEnabled(): Boolean = false
}
