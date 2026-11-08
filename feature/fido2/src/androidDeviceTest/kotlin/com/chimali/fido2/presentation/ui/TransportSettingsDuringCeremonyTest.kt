package com.chimali.fido2.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.viewmodel.AuthenticationState
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransportSettingsDuringCeremonyTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun authenticationConsentOpensTransportSettingsWithoutConfirmingOrCancelling() {
        var settingsOpened = false
        var confirmed = false
        var cancelled = false

        composeTestRule.setContent {
            AuthenticationPromptContent(
                state =
                    AuthenticationState.AwaitingUserConsent(
                        rpId = sensitiveText("https://example.com"),
                        rpName = sensitiveText("Example"),
                        availableMethod = VerificationMethod.BIOMETRIC,
                        credentialCount = 1,
                    ),
                onConfirm = { confirmed = true },
                onCancel = { cancelled = true },
                onSelectCredential = {},
                onRetry = {},
                onOpenSettings = { settingsOpened = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Open transport settings").performClick()

        assertTrue(settingsOpened)
        assertFalse(confirmed)
        assertFalse(cancelled)
        composeTestRule.onNodeWithText("Sign in with Passkey").assertIsDisplayed()
    }

    @Test
    fun registrationConsentOpensTransportSettingsWithoutConfirmingOrCancelling() {
        var settingsOpened = false
        var confirmed = false
        var cancelled = false

        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.AwaitingUserConsent(
                        rpId = sensitiveText("example.com"),
                        rpName = sensitiveText("Example"),
                        userName = sensitiveText("user@example.com"),
                        userDisplayName = sensitiveText("User"),
                        availableMethod = VerificationMethod.NONE,
                    ),
                onConfirm = { confirmed = true },
                onCancel = { cancelled = true },
                onRetry = {},
                onOpenSettings = { settingsOpened = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Open transport settings").performClick()

        assertTrue(settingsOpened)
        assertFalse(confirmed)
        assertFalse(cancelled)
        composeTestRule.onNodeWithContentDescription("Confirm registration button").assertIsDisplayed()
    }

    private fun sensitiveText(value: String): SensitiveCredentialText {
        val bytes = value.encodeToByteArray()
        return try {
            SensitiveCredentialText.fromUtf8Bytes(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
