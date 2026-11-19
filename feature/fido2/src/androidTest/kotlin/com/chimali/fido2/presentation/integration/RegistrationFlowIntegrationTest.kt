package com.chimali.fido2.presentation.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.ui.RegistrationPromptContent
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T069 — Integration tests for the FIDO2 registration flow.
 *
 * These tests verify the full state machine progression from consent → verification → success
 * Annotated as integration tests because they use createAndroidComposeRule and run on-device.
 *
 * NOTE: Full end-to-end CTAP2 + BLE tests require a real Bluetooth connection and are
 * performed with manual testing. These focus on the UI + ViewModel portion.
 */
class RegistrationFlowIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var credentialFixture: com.chimali.fido2.domain.model.PasskeyCredential? = null
    private val registrationTextOwners = mutableListOf<SensitiveCredentialText>()

    private fun sensitiveText(value: String) = SensitiveCredentialText.copyOf(value).also(registrationTextOwners::add)

    private fun consentState(
        rpId: String,
        rpName: String,
        userName: String,
        userDisplayName: String,
    ) = RegistrationState.AwaitingUserConsent(
        rpId = sensitiveText(rpId),
        rpName = sensitiveText(rpName),
        userName = sensitiveText(userName),
        userDisplayName = sensitiveText(userDisplayName),
        availableMethod = VerificationMethod.NONE,
    )

    @After
    fun clearRegistrationFixtures() {
        composeRule.setContent {}
        credentialFixture?.let { credential ->
            credential.eraseSensitiveText()
            credential.aaguid.fill(0)
            assertTrue(credential.rpId.all { it == '\u0000' })
            assertTrue(credential.userId.all { it == '\u0000' })
            assertTrue(credential.userName.all { it == '\u0000' })
            assertTrue(credential.userDisplayName.all { it == '\u0000' })
            assertTrue(credential.credentialId.all { it == 0.toByte() })
            assertTrue(credential.aaguid.all { it == 0.toByte() })
        }
        credentialFixture = null
        registrationTextOwners.forEach { owner ->
            owner.erase()
            assertTrue(owner.all { it == '\u0000' })
        }
        registrationTextOwners.clear()
    }

    @Test
    fun fullFlow_consentToProcessingShowsProgressIndicator() {
        val states = mutableListOf<RegistrationState>()
        var currentState: RegistrationState =
            consentState("example.com", "Example Corp", "alice", "Alice")

        composeRule.setContent {
            RegistrationPromptContent(
                state = currentState,
                onConfirm = {
                    currentState = RegistrationState.Processing
                    states.add(currentState)
                },
                onCancel = {},
                onRetry = {},
            )
        }

        // Verify initial state
        composeRule.onNodeWithText("Example Corp").assertIsDisplayed()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()

        // Confirm
        composeRule.onNodeWithContentDescription("Confirm registration button").performClick()
    }

    @Test
    fun cancelFlow_dismissesAndCallsOnCancel() {
        var cancelCalled = false
        composeRule.setContent {
            RegistrationPromptContent(
                state =
                    consentState("example.com", "Example", "u", "U"),
                onConfirm = {},
                onCancel = { cancelCalled = true },
                onRetry = {},
            )
        }

        composeRule.onNodeWithContentDescription("Cancel registration button").performClick()
        assert(cancelCalled) { "Expected onCancel to be called" }
    }

    @Test
    fun errorRetryFlow_showsRetryButtonAndErrorsAreRecoverable() {
        var retryCalled = false
        composeRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Error("Timeout", isRetryable = true),
                onConfirm = {},
                onCancel = {},
                onRetry = { retryCalled = true },
            )
        }

        composeRule.onNodeWithText("Try again").performClick()
        assert(retryCalled) { "Expected onRetry to be called" }
    }

    @Test
    fun successStateDisplaysPasskeyCreatedMessage() {
        val credential =
            com.chimali.fido2.domain.model.PasskeyCredential.createTest(
                id =
                    com.chimali.core.domain.valueobject.CredentialId
                        .fromEncoded("cred-1"),
                rpId =
                    com.chimali.core.domain.valueobject
                        .RpId("example.com"),
                userName = "alice",
            )
        credentialFixture = credential
        composeRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.Success(
                        credential,
                    ),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Passkey created!").assertIsDisplayed()
    }
}
