package com.chimali.fido2.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import org.junit.After
import org.junit.Assert.assertTrue
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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    private val textOwners = mutableListOf<SensitiveCredentialText>()

    private fun sensitiveText(value: String) = SensitiveCredentialText.copyOf(value).also(textOwners::add)

    private fun consentState(
        rpId: String,
        rpName: String,
        userName: String,
        userDisplayName: String,
        availableMethod: VerificationMethod,
    ) = RegistrationState.AwaitingUserConsent(
        rpId = sensitiveText(rpId),
        rpName = sensitiveText(rpName),
        userName = sensitiveText(userName),
        userDisplayName = sensitiveText(userDisplayName),
        availableMethod = availableMethod,
    )

    @After
    fun clearSyntheticConsentState() {
        composeTestRule.setContent {}
        textOwners.forEach { owner ->
            owner.erase()
            assertTrue(owner.all { it == '\u0000' })
        }
        textOwners.clear()
    }

    // ── AwaitingUserConsent state ─────────────────────────────────────────────

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
    fun showsRpNameAndUserNameInConsentState() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    consentState(
                        "example.com",
                        "Example Corp",
                        "alice@example.com",
                        "Alice",
                        VerificationMethod.BIOMETRIC,
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

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
    fun rpAndUserCardsHaveMergedDescendantsForTalkBack() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    consentState("example.com", "Example Corp", "alice@example.com", "Alice", VerificationMethod.NONE),
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

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
    fun showsCreatePasskeyButtonAndCancelButtonInConsentState() {
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    consentState("example.com", "Example", "user@example.com", "User", VerificationMethod.NONE),
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm registration button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel registration button").assertIsDisplayed()
    }

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
    fun confirmButtonClickTriggersOnConfirmCallback() {
        var confirmCalled = false
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    consentState("example.com", "Example", "u", "U", VerificationMethod.NONE),
                onConfirm = { confirmCalled = true },
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Confirm registration button").performClick()
        assert(confirmCalled)
    }

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
    fun cancelButtonClickTriggersOnCancelCallback() {
        var cancelCalled = false
        composeTestRule.setContent {
            RegistrationPromptContent(
                state =
                    consentState("example.com", "Example", "u", "U", VerificationMethod.NONE),
                onConfirm = {},
                onCancel = { cancelCalled = true },
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Cancel registration button").performClick()
        assert(cancelCalled)
    }

    // ── Processing state ──────────────────────────────────────────────────────

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
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

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
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

    /**
     * @see FR-HID-030
     * @see FR-AUTH-010
     */
    @Test
    @Req("FR-HID-030", "FR-AUTH-010")
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
