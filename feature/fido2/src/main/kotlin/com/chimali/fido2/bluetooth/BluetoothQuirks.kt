package com.chimali.fido2.bluetooth

import timber.log.Timber

/**
 * Registry of OEM-specific Bluetooth HID workarounds.
 *
 * Some Android devices exhibit non-standard behaviour in the Bluetooth HID stack
 * that requires special handling. This object centralises all such device-specific
 * logic to keep the main connection path clean and auditable.
 *
 * Adding new quirks
 * -----------------
 * 1. Add a private `fun matchesXxx(device: BluetoothDevice): Boolean` matching on
 *    manufacturer / model substrings (case-insensitive).
 * 2. Expose a public `fun requiresXxx(device: BluetoothDevice): Boolean` that
 *    delegates to the private matcher.
 * 3. Document the affected devices and the observed symptom in KDoc.
 */
object BluetoothQuirks {

    // ── Phantom-device quirk ──────────────────────────────────────────────────

    /**
     * Certain Android devices (confirmed: Motorola G-series) falsely report a connected
     * host device via `BluetoothHidDevice.Callback.onAppStatusChanged` *at the
     * moment of app registration*, before any real host has initiated a connection.
     *
     * This phantom report occupies the L2CAP socket, preventing legitimate hosts
     * from connecting until the socket is explicitly freed by a forced disconnect.
     *
     * Affected devices (manufacturer substring match, case-insensitive):
     *  - "motorola" — Moto G series (confirmed)
     *
     * @return `true` when the local Android device belongs to an OEM known to trigger
     *         this phantom-registration bug.
     */
    fun requiresPhantomDeviceDisconnect(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val model = android.os.Build.MODEL.lowercase()

        val matched = isMotorola(manufacturer) || isMotorola(model)
        if (matched) {
            Timber.d(
                "BluetoothQuirks: phantom-device quirk matched for local device (manufacturer=%s, model=%s)",
                manufacturer.ifEmpty { "<unknown>" },
                model.ifEmpty { "<unknown>" },
            )
        }
        return matched
    }

    // ── Private matchers ──────────────────────────────────────────────────────

    /**
     * Matches Motorola devices by checking if the manufacturer or model string contains
     * the relevant keywords.
     */
    private fun isMotorola(lowerString: String): Boolean =
        lowerString.startsWith("moto") || lowerString.contains("motorola")
}
