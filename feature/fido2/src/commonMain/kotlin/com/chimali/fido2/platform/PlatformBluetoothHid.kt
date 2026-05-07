package com.chimali.fido2.platform

/**
 * T191 — Platform-specific Bluetooth HID transport capability check.
 *
 * `expect class` boundary: declared in commonMain, `actual` in each platform source set.
 *
 * ## Why `expect class` for BT HID?
 *
 * On Android, `BluetoothHidDevice` is an Android-system Bluetooth profile proxy that
 * requires `android.bluetooth.*` APIs unavailable in iOS or desktop targets. This `expect
 * class` wraps the capability query so that domain logic in commonMain can check whether
 * the transport is available without importing Android-specific types.
 *
 * The full `BluetoothHidDeviceWrapper` (which manages the connection lifecycle) remains in
 * `androidMain`. This boundary class only answers "is this device capable?".
 *
 * ## Behavioural contract
 *
 * - [isSupported] — `true` iff the running device hardware and OS support acting as a
 *   Bluetooth HID peripheral (HID Device profile available). On iOS this returns `false`
 *   until a CoreBluetooth-based implementation is provided.
 * - [isAdapterEnabled] — `true` iff Bluetooth is currently powered on.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PlatformBluetoothHid {
    /** Returns true if BT HID Device profile is supported by this device. */
    fun isSupported(): Boolean

    /** Returns true if the Bluetooth adapter is currently powered on. */
    fun isAdapterEnabled(): Boolean
}
