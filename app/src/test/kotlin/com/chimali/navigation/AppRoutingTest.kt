package com.chimali.navigation

import com.chimali.core.common.datastore.UserPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppRoutingTest {
    @Test
    fun `from maps onboarding and selection flags`() {
        val preferences =
            UserPreferences(
                onboardingCompleted = true,
                vaultFeatureEnabled = true,
                passkeyAuthenticatorFeatureEnabled = false,
                lastVisitedMainScreen = AppDestinations.VAULT_ROUTE,
            )

        val routingState = AppRoutingState.from(preferences)

        assertTrue(routingState.onboardingCompleted)
        assertTrue(routingState.vaultEnabled)
        assertFalse(routingState.passkeyEnabled)
        assertEquals(AppDestinations.VAULT_ROUTE, routingState.mainRoute)
    }

    @Test
    fun `normalizedLastVisitedMainScreen falls back to passkey when both features are enabled`() {
        val routingState =
            AppRoutingState(
                onboardingCompleted = true,
                vaultEnabled = true,
                passkeyEnabled = true,
                lastVisitedMainScreen = "",
            )

        assertEquals("fido2/home", routingState.normalizedLastVisitedMainScreen())
        assertEquals("fido2/home", routingState.mainRoute)
    }
}
