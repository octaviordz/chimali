package com.chimali.fido2.presentation.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun showsRpNameAndUserNameInConsentState() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.AwaitingUserConsent(
                        rpId = "example.com",
                        rpName = "Example Corp",
                        userName = "alice@example.com",
                        userDisplayName = "Alice",
                        availableMethod = VerificationMethod.BIOMETRIC,
                    ),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Example Corp").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()

        // T143: Verify heading role for screen title
        composeTestRule
            .onNodeWithText("Create Passkey")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun rpAndUserCardsHaveMergedDescendantsForTalkBack() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.AwaitingUserConsent(
                        rpId = "example.com",
                        rpName = "Example Corp",
                        userName = "alice@example.com",
                        userDisplayName = "Alice",
                        availableMethod = VerificationMethod.NONE,
                    ),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        // T143: Verify RP card merges descendants
        // We find by RP name and check if it's a single node containing the ID
        composeTestRule
            .onNode(hasText("Example Corp").and(hasText("example.com")), useUnmergedTree = false)
            .assertExists()
    }

    @Test
    fun showsCreatePasskeyButtonAndCancelButtonInConsentState() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.AwaitingUserConsent(
                        rpId = "example.com",
                        rpName = "Example",
                        userName = "user@example.com",
                        userDisplayName = "User",
                        availableMethod = VerificationMethod.NONE,
                    ),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm registration button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel registration button").assertIsDisplayed()
    }

    @Test
    fun confirmButtonClickTriggersOnConfirmCallback() {
        var confirmCalled = false
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.AwaitingUserConsent(
                        rpId = "example.com",
                        rpName = "Example",
                        userName = "u",
                        userDisplayName = "U",
                        availableMethod = VerificationMethod.NONE,
                    ),
                onConfirm = { confirmCalled = true },
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm registration button").performClick()
        assert(confirmCalled)
    }

    @Test
    fun cancelButtonClickTriggersOnCancelCallback() {
        var cancelCalled = false
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    RegistrationState.AwaitingUserConsent(
                        rpId = "example.com",
                        rpName = "Example",
                        userName = "u",
                        userDisplayName = "U",
                        availableMethod = VerificationMethod.NONE,
                    ),
                onConfirm = {},
                onCancel = { cancelCalled = true },
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Cancel registration button").performClick()
        assert(cancelCalled)
    }

    // ── Processing state ──────────────────────────────────────────────────────

    @Test
    fun showsProgressIndicatorInProcessingState() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Processing,
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Registration in progress").assertIsDisplayed()
    }

    // ── Error state ───────────────────────────────────────────────────────────

    @Test
    fun showsErrorMessageInErrorState() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Error("Something went wrong", isRetryable = true),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()

        // T143: Verify live region for automatic announcement
        composeTestRule
            .onNode(hasAnyDescendant(hasText("Something went wrong")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun hidesRetryButtonForNonRetryableErrors() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state = RegistrationState.Error("Not allowed", isRetryable = false),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Not allowed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }
}
