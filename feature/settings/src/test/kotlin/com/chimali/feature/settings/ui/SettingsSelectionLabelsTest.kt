package com.chimali.feature.settings.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsSelectionLabelsTest {
    @Test
    fun `selection labels reflect each enabled feature combination`() {
        assertEquals(
            SettingsSelectionLabels("Vault: Disabled", "Passkey Authenticator: Disabled"),
            settingsSelectionLabels(vaultEnabled = false, passkeyEnabled = false),
        )
        assertEquals(
            SettingsSelectionLabels("Vault: Enabled", "Passkey Authenticator: Disabled"),
            settingsSelectionLabels(vaultEnabled = true, passkeyEnabled = false),
        )
        assertEquals(
            SettingsSelectionLabels("Vault: Disabled", "Passkey Authenticator: Enabled"),
            settingsSelectionLabels(vaultEnabled = false, passkeyEnabled = true),
        )
        assertEquals(
            SettingsSelectionLabels("Vault: Enabled", "Passkey Authenticator: Enabled"),
            settingsSelectionLabels(vaultEnabled = true, passkeyEnabled = true),
        )
    }
}
