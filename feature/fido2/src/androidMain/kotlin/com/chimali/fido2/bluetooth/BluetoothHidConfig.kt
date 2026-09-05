package com.chimali.fido2.bluetooth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Centralised, per-device Bluetooth HID connection parameters.
 *
 * Every magic number that affects the Bluetooth HID lifecycle is declared here
 * so that [BluetoothHidConfigProvider] can resolve the correct profile at startup. The
 * default values are safe for all tested devices (Motorola, Asus, Samsung).
 *
 * To add OEM-specific tuning:
 * 1. Create a factory function in [BluetoothHidConfigProvider] (e.g. `motorolaConfig()`).
 * 2. Override only the fields that differ from the defaults.
 * 3. Wire the factory into [BluetoothHidConfigProvider.resolveConfig].
 */
data class BluetoothHidConfig(
    /** Maximum number of attempts to acquire the HID_DEVICE profile proxy. */
    val initMaxRetries: Int = 3,
    /** Initial back-off delay between [initMaxRetries]. Doubles each retry. */
    val initRetryDelay: Duration = 1.seconds,
    /** Per-attempt timeout waiting for `onServiceConnected` callback. */
    val initTimeout: Duration = 5.seconds,
    /** Maximum number of attempts to register the HID app with the Bluetooth daemon. */
    val registerMaxRetries: Int = 5,
    /** Initial back-off delay between [registerMaxRetries]. Doubles each retry. */
    val registerRetryDelay: Duration = 2.seconds,
    /** Per-attempt timeout waiting for `onAppStatusChanged` callback. */
    val registerTimeout: Duration = 10.seconds,
    /** Hard cap on exponential back-off growth for registration retries. */
    val registerRetryMaxDelay: Duration = 10.seconds,
    /**
     * Inter-report sleep between consecutive `sendReport()` calls.
     *
     * Android's Classic BT L2CAP channel does not expose per-packet ACKs, so we
     * rely on a fixed sleep to avoid overwhelming the driver's internal queue.
     * 20 ms is safe across Pixel, Samsung, Asus, and Motorola.
     */
    val reportPaceDelay: Duration = 20.milliseconds,
    /** Delay before the first CTAPHID_KEEPALIVE during long operations. */
    val keepaliveInitialDelay: Duration = 75.milliseconds,
    /** Interval between subsequent CTAPHID_KEEPALIVE packets. */
    val keepalivePeriod: Duration = 75.milliseconds,
    /**
     * When `true`, a stale `pluggedDevice` reported by `onAppStatusChanged` at
     * registration time is force-disconnected to free the L2CAP socket.
     *
     * Confirmed necessary on Motorola G-series; harmless on other OEMs because
     * the code path only triggers when a non-null `pluggedDevice` is reported.
     */
    val isPhantomDisconnectRequired: Boolean = true,
    /**
     * Delay after [android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED] confirms
     * [android.bluetooth.BluetoothDevice.BOND_BONDED] before initiating a proactive HID connection.
     * Allows baseband role-switch and concurrent HFP/A2DP profile connection attempts to settle.
     */
    val bondConnectDelay: Duration = 1.seconds,
    /**
     * Delay after [android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED] before initiating a
     * proactive HID connection to an already-bonded host.
     */
    val aclConnectDelay: Duration = 500.milliseconds,
)
