package com.chimali.feature.settings.ui

internal data class SettingsSelectionLabels(
    val vault: String,
    val passkey: String,
)

internal fun settingsSelectionLabels(
    vaultEnabled: Boolean,
    passkeyEnabled: Boolean,
) =
    SettingsSelectionLabels(
        vault = "Vault: ${if (vaultEnabled) "Enabled" else "Disabled"}",
        passkey = "Passkey Authenticator: ${if (passkeyEnabled) "Enabled" else "Disabled"}",
    )
