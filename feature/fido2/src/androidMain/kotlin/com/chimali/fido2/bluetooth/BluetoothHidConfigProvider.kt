package com.chimali.fido2.bluetooth

import co.touchlab.kermit.Logger

/**
 * Centralised registry of Bluetooth HID connection tuning parameters.
 *
 * Resolves the shared default [BluetoothHidConfig] once at startup. All consumers
 * read from [config] instead of hard-coding magic numbers.
 */
object BluetoothHidConfigProvider {
    /**
     * The resolved connection configuration for this device.
     *
     * Thread-safe — Kotlin `lazy` uses [LazyThreadSafetyMode.SYNCHRONIZED]
     * by default. Resolved exactly once on first access.
     */
    val config: BluetoothHidConfig by lazy { resolveConfig() }

    // ── Config resolution ────────────────────────────────────────────────────

    private fun resolveConfig(): BluetoothHidConfig {
        val resolved = BluetoothHidConfig()

        Logger.i { "BluetoothHidConfigProvider: connection configuration resolved" }
        return resolved
    }
}
