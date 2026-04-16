package com.chimali.fido2.presentation.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.viewmodel.AuthenticationState
import org.junit.Rule
import org.junit.Test

/**
 * T099 — Compose UI tests for [AuthenticationPromptScreen].
 *
 * Uses the stateless [AuthenticationPromptContent] composable directly so
 * we can inject any [AuthenticationState] without Hilt/ViewModel wiring.
 */
class AuthenticationPromptScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── AwaitingUserConsent state ────────────────────────────────────────────

    @Test
    fun awaitingUserConsent_displaysRpIdAndConfirmButton() {
        composeTestRule.setContent {
            AuthenticationPromptContent(
                state =
                    AuthenticationState.AwaitingUserConsent(
                        rpId = "https://example.com",
                        rpName = "Example",
                        availableMethod = VerificationMethod.BIOMETRIC,
                        credentialCount = 1,
                    ),
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("https://example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // T143: Verify heading role for screen title
        composeTestRule.onNodeWithText("Sign in with Passkey")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun awaitingUserConsent_showsMultiplePasskeysHint() {
        composeTestRule.setContent {
            AuthenticationPromptContent(
                state =
                    AuthenticationState.AwaitingUserConsent(
                        rpId = "https://example.com",
                        rpName = "Example",
                        availableMethod = VerificationMethod.BIOMETRIC,
                        credentialCount = 3,
                    ),
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("3 passkeys available").assertIsDisplayed()
    }

    // ── Processing state ────────────────────────────────────────────────────

    @Test
    fun processingState_showsProgressIndicator() {
        composeTestRule.setContent {
            AuthenticationPromptContent(
                state = AuthenticationState.Processing,
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Authentication in progress").assertIsDisplayed()
    }

    // ── Success state ───────────────────────────────────────────────────────

    @Test
    fun successState_showsSignedInMessage() {
        val testAssertion = AssertionObject.createTest("cred1", "https://example.com")

        composeTestRule.setContent {
            AuthenticationPromptContent(
                state = AuthenticationState.Success(testAssertion),
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Signed in!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Authentication successful.").assertIsDisplayed()

        // T143: Verify live region and heading for success state
        composeTestRule.onNodeWithText("Signed in!")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))

        composeTestRule.onNode(hasAnyDescendant(hasText("Signed in!")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    // ── Error state ─────────────────────────────────────────────────────────

    @Test
    fun errorState_showsErrorMessageAndRetryButton() {
        composeTestRule.setContent {
            AuthenticationPromptContent(
                state = AuthenticationState.Error("Network timeout", isRetryable = true),
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Authentication failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network timeout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // T143: Verify live region and heading for error state
        composeTestRule.onNodeWithText("Authentication failed")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))

        composeTestRule.onNode(hasAnyDescendant(hasText("Authentication failed")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun nonRetryableError_hidesRetryButton() {
        composeTestRule.setContent {
            AuthenticationPromptContent(
                state = AuthenticationState.Error("Fatal error", isRetryable = false),
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Fatal error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    // ── Awaiting verification states ────────────────────────────────────────

    @Test
    fun awaitingBiometric_showsVerifyingIdentity() {
        composeTestRule.setContent {
            AuthenticationPromptContent(
                state = AuthenticationState.AwaitingUserVerification,
                onConfirm = {},
                onCancel = {},
                onSelectCredential = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Awaiting verification").assertIsDisplayed()
    }
}
