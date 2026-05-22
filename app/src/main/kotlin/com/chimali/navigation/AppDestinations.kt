package com.chimali.navigation

import com.chimali.fido2.presentation.navigation.Fido2Destinations

object AppDestinations {
    const val LOADING_ROUTE = "app/loading"
    const val ONBOARDING_ROUTE = "app/onboarding"
    const val SHELL_ROUTE = "app/shell"
    const val SETTINGS_ROUTE = "app/settings"

    const val VAULT_ROUTE = "vault/home"

    fun defaultMainRoute(
        vaultEnabled: Boolean,
        passkeyEnabled: Boolean,
    ): String =
        when {
            vaultEnabled && passkeyEnabled -> Fido2Destinations.HOME_ROUTE
            vaultEnabled -> VAULT_ROUTE
            passkeyEnabled -> Fido2Destinations.HOME_ROUTE
            else -> Fido2Destinations.HOME_ROUTE
        }
}
