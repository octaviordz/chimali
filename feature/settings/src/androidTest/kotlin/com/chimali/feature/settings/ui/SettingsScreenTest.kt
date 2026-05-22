package com.chimali.feature.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `retake onboarding button invokes callback`() {
        var invoked = false

        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = false,
                onRetakeOnboarding = { invoked = true },
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Re-take Onboarding").assertIsDisplayed().performClick()

        assertTrue(invoked)
    }
}
