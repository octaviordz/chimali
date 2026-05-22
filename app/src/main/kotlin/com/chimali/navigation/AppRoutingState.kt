package com.chimali.navigation

import com.chimali.core.common.datastore.UserPreferences
import com.chimali.fido2.presentation.navigation.Fido2Destinations

data class AppRoutingState(
    val onboardingCompleted: Boolean,
    val vaultEnabled: Boolean,
    val passkeyEnabled: Boolean,
    val lastVisitedMainScreen: String,
) {
    val hasVaultAndPasskeyEnabled: Boolean = vaultEnabled && passkeyEnabled

    val mainRoute: String
        get() =
            when {
                !vaultEnabled && !passkeyEnabled -> Fido2Destinations.HOME_ROUTE
                hasVaultAndPasskeyEnabled -> normalizedLastVisitedMainScreen()
                vaultEnabled -> AppDestinations.VAULT_ROUTE
                else -> Fido2Destinations.HOME_ROUTE
            }

    fun normalizedLastVisitedMainScreen(): String =
        when (lastVisitedMainScreen) {
            AppDestinations.VAULT_ROUTE,
            Fido2Destinations.HOME_ROUTE,
            -> lastVisitedMainScreen
            else -> AppDestinations.defaultMainRoute(vaultEnabled, passkeyEnabled)
        }

    companion object {
        fun from(preferences: UserPreferences): AppRoutingState =
            AppRoutingState(
                onboardingCompleted = preferences.onboardingCompleted,
                vaultEnabled = preferences.vaultFeatureEnabled,
                passkeyEnabled = preferences.passkeyAuthenticatorFeatureEnabled,
                lastVisitedMainScreen = preferences.lastVisitedMainScreen,
            )
    }
}
