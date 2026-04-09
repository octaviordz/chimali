package com.chimali.fido2.bluetooth

import timber.log.Timber

/**
 * Centralised registry of Bluetooth HID connection tuning parameters.
 *
 * Resolves a [BluetoothHidConfig] once at startup based on the local device's
 * manufacturer and model. All consumers read from [config] instead of
 * hard-coding magic numbers.
 *
 * ## Adding a new device profile
 *
 * 1. Create a private factory function (e.g. `samsungConfig()`) that returns a
 *    [BluetoothHidConfig] with only the fields that differ from the defaults.
 * 2. Add a private matcher function (e.g. `isSamsung()`).
 * 3. Wire both into [resolveConfig].
 * 4. Document the device, observed symptom, and tuning rationale in KDoc.
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
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val model = android.os.Build.MODEL.lowercase()

        // For now a single default configuration is used for all devices.
        // Add OEM-specific branches here as evidence demands, e.g.:
        //
        //   isMotorola(manufacturer, model) -> motorolaConfig()
        //   isAsus(manufacturer, model)     -> asusConfig()
        //
        val resolved = BluetoothHidConfig()

        Timber.i(
            "BluetoothHidConfigProvider: resolved config for manufacturer=%s model=%s → %s",
            manufacturer.ifEmpty { "<unknown>" },
            model.ifEmpty { "<unknown>" },
            resolved,
        )
        return resolved
    }

    // ── Private matchers (ready for future OEM profiles) ─────────────────────

    @Suppress("unused") // Kept as scaffolding for future OEM-specific configs
    private fun isMotorola(manufacturer: String, model: String): Boolean =
        manufacturer.startsWith("moto") || manufacturer.contains("motorola") ||
            model.startsWith("moto")

    @Suppress("unused") // Kept as scaffolding for future OEM-specific configs
    private fun isAsus(manufacturer: String, model: String): Boolean =
        manufacturer.contains("asus")
}
