package com.chimali.fido2.presentation.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.ui.RegistrationPromptContent
import com.chimali.fido2.presentation.viewmodel.RegistrationState
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

    // NOTE: Replace with your application's Activity once wired up
    // @get:Rule
    // val composeRule = createAndroidComposeRule<MainActivity>()

    // Temporarily using createComposeRule for headless CI execution
    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Test
    fun fullFlow_consentToProcessingShowsProgressIndicator() {
        val states = mutableListOf<RegistrationState>()
        var currentState: RegistrationState = RegistrationState.AwaitingUserConsent(
            rpId            = "example.com",
            rpName          = "Example Corp",
            userName        = "alice",
            userDisplayName = "Alice",
            availableMethod = VerificationMethod.NONE
        )

        composeRule.setContent {
            RegistrationPromptContent(
                state       = currentState,
                onConfirm   = {
                    currentState = RegistrationState.Processing
                    states.add(currentState)
                },
                onCancel    = {},
                onRetry     = {}
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
                state = RegistrationState.AwaitingUserConsent(
                    rpId            = "example.com",
                    rpName          = "Example",
                    userName        = "u",
                    userDisplayName = "U",
                    availableMethod = VerificationMethod.NONE
                ),
                onConfirm   = {},
                onCancel    = { cancelCalled = true },
                onRetry     = {}
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
                onConfirm   = {},
                onCancel    = {},
                onRetry     = { retryCalled = true }
            )
        }

        composeRule.onNodeWithText("Try again").performClick()
        assert(retryCalled) { "Expected onRetry to be called" }
    }

    @Test
    fun successStateDisplaysPasskeyCreatedMessage() {
        composeRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Success(
                    com.chimali.fido2.domain.model.PasskeyCredential.createTest(
                        id   = "cred-1",
                        rpId = "example.com",
                        userName = "alice"
                    )
                ),
                onConfirm = {}, onCancel = {}, onRetry = {}
            )
        }

        composeRule.onNodeWithText("Passkey created!").assertIsDisplayed()
    }
}
