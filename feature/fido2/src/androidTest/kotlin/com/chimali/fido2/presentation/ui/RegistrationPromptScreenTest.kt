package com.chimali.fido2.presentation.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import org.junit.Rule
import org.junit.Test

/**
 * T068 — Compose UI tests for the Registration screen.
 *
 * These run on the JVM via Compose test rule (no full activity needed).
 * We test [RegistrationPromptContent] directly, bypassing the ViewModel,
 * which lets us drive the state machine from the outside.
 */
class RegistrationPromptScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── AwaitingUserConsent state ─────────────────────────────────────────────

    @Test
    fun `shows RP name and user name in consent state`() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.AwaitingUserConsent(
                    rpId            = "example.com",
                    rpName          = "Example Corp",
                    userName        = "alice@example.com",
                    userDisplayName = "Alice",
                    availableMethod = VerificationMethod.BIOMETRIC
                ),
                onConfirm   = {},
                onCancel    = {},
                onBiometric = {},
                onPinSubmit = {},
                onRetry     = {}
            )
        }

        composeTestRule.onNodeWithText("Example Corp").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test
    fun `shows Create Passkey button and Cancel button in consent state`() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.AwaitingUserConsent(
                    rpId            = "example.com",
                    rpName          = "Example",
                    userName        = "user@example.com",
                    userDisplayName = "User",
                    availableMethod = VerificationMethod.NONE
                ),
                onConfirm = {}, onCancel = {}, onBiometric = {},
                onPinSubmit = {}, onRetry = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm registration button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel registration button").assertIsDisplayed()
    }

    @Test
    fun `confirm button click triggers onConfirm callback`() {
        var confirmCalled = false
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.AwaitingUserConsent(
                    rpId            = "example.com",
                    rpName          = "Example",
                    userName        = "u",
                    userDisplayName = "U",
                    availableMethod = VerificationMethod.NONE
                ),
                onConfirm = { confirmCalled = true },
                onCancel = {}, onBiometric = {}, onPinSubmit = {}, onRetry = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm registration button").performClick()
        assert(confirmCalled)
    }

    @Test
    fun `cancel button click triggers onCancel callback`() {
        var cancelCalled = false
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.AwaitingUserConsent(
                    rpId            = "example.com",
                    rpName          = "Example",
                    userName        = "u",
                    userDisplayName = "U",
                    availableMethod = VerificationMethod.NONE
                ),
                onConfirm = {}, onCancel = { cancelCalled = true },
                onBiometric = {}, onPinSubmit = {}, onRetry = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Cancel registration button").performClick()
        assert(cancelCalled)
    }

    // ── Processing state ──────────────────────────────────────────────────────

    @Test
    fun `shows progress indicator in processing state`() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Processing,
                onConfirm = {}, onCancel = {}, onBiometric = {},
                onPinSubmit = {}, onRetry = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Registration in progress").assertIsDisplayed()
    }

    // ── Error state ───────────────────────────────────────────────────────────

    @Test
    fun `shows error message in error state`() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Error("Something went wrong", isRetryable = true),
                onConfirm = {}, onCancel = {}, onBiometric = {},
                onPinSubmit = {}, onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun `hides retry button for non-retryable errors`() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Error("Not allowed", isRetryable = false),
                onConfirm = {}, onCancel = {}, onBiometric = {},
                onPinSubmit = {}, onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("Not allowed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    // ── PinEntryDialog tests ──────────────────────────────────────────────────

    @Test
    fun `PinEntryDialog confirm button disabled when pin is empty`() {
        composeTestRule.setContent {
            PinEntryDialog(
                onDismiss = {},
                onPinEnteredAndConfirmed = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm PIN button").assertIsNotEnabled()
    }

    @Test
    fun `PinEntryDialog confirm button enabled after typing minimum digits`() {
        composeTestRule.setContent {
            PinEntryDialog(
                minPinLength = 4,
                onDismiss = {},
                onPinEnteredAndConfirmed = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("PIN input field").performTextInput("1234")
        composeTestRule.onNodeWithContentDescription("Confirm PIN button").assertIsEnabled()
    }

    @Test
    fun `PinEntryDialog show-hide PIN toggle exists`() {
        composeTestRule.setContent {
            PinEntryDialog(
                onDismiss = {},
                onPinEnteredAndConfirmed = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Show PIN").assertIsDisplayed()
    }
}
