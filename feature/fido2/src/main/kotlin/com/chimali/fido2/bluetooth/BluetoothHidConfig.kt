package com.chimali.fido2.bluetooth

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
    // ── Proxy acquisition — initialize() ─────────────────────────────────────
    /** Maximum number of attempts to acquire the HID_DEVICE profile proxy. */
    val initMaxRetries: Int = 3,
    /** Initial back-off delay (ms) between [initMaxRetries]. Doubles each retry. */
    val initRetryDelayMs: Long = 1_000L,
    /** Per-attempt timeout (ms) waiting for `onServiceConnected` callback. */
    val initTimeoutMs: Long = 5_000L,
    // ── App registration — registerApp() ─────────────────────────────────────
    /** Maximum number of attempts to register the HID app with the Bluetooth daemon. */
    val registerMaxRetries: Int = 5,
    /** Initial back-off delay (ms) between [registerMaxRetries]. Doubles each retry. */
    val registerRetryDelayMs: Long = 2_000L,
    /** Per-attempt timeout (ms) waiting for `onAppStatusChanged` callback. */
    val registerTimeoutMs: Long = 10_000L,
    /** Hard cap (ms) on exponential back-off growth for registration retries. */
    val registerRetryMaxDelayMs: Long = 10_000L,
    // ── Packet pacing ────────────────────────────────────────────────────────
    /**
     * Inter-report sleep (ms) between consecutive `sendReport()` calls.
     *
     * Android's Classic BT L2CAP channel does not expose per-packet ACKs, so we
     * rely on a fixed sleep to avoid overwhelming the driver's internal queue.
     * 20 ms is safe across Pixel, Samsung, Asus, and Motorola.
     */
    val reportPaceDelayMs: Long = 20L,
    /** Delay (ms) before the first CTAPHID_KEEPALIVE during long operations. */
    val keepaliveInitialDelayMs: Long = 75L,
    /** Interval (ms) between subsequent CTAPHID_KEEPALIVE packets. */
    val keepalivePeriodMs: Long = 75L,
    // ── Connection behaviour ─────────────────────────────────────────────────
    /**
     * When `true`, a stale `pluggedDevice` reported by `onAppStatusChanged` at
     * registration time is force-disconnected to free the L2CAP socket.
     *
     * Confirmed necessary on Motorola G-series; harmless on other OEMs because
     * the code path only triggers when a non-null `pluggedDevice` is reported.
     */
    val requiresPhantomDisconnect: Boolean = true,
)
