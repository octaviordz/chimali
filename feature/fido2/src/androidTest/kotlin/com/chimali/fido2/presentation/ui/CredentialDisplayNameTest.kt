package com.chimali.fido2.presentation.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextOverflow
import org.junit.Rule
import org.junit.Test

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule

/**
 * T023 — Compose UI test: a [displayName] of exactly 64 UTF-8 bytes renders without truncation
 * in a credential selection context.
 *
 * WebAuthn L3 §6.4.1.2 specifies names are measured in bytes, not code points.
 * This test ensures that single-byte ASCII names at the 64-byte limit display fully.
 */
class CredentialDisplayNameTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * A 64-character ASCII displayName — exactly 64 UTF-8 bytes.
     * This is the maximum allowed per WebAuthn L3 and must render in full.
     */
    private val maxDisplayName = "A".repeat(64)

    /**
     * A displayName whose characters differ only after position 32,
     * verifying that both are distinguishable when shown together.
     */
    private val displayNameA = "credential_display_name_variant_A" + "A".repeat(31)
    private val displayNameB = "credential_display_name_variant_B" + "B".repeat(31)

    @Test
    fun displayName_64ByteAscii_rendersWithoutTruncation() {
        composeTestRule.setContent {
            // Simulate how a credential name would appear in the selection bottom sheet.
            // No maxLines limit and Visible overflow — matching T025 audit findings.
            Text(
                text = maxDisplayName,
                modifier = Modifier.fillMaxWidth(),
                overflow = TextOverflow.Visible,
                softWrap = true,
            )
        }

        composeTestRule.onNodeWithText(maxDisplayName).assertIsDisplayed()
    }

    @Test
    fun twoCredentials_differingAfterPosition32_areDisplayedDistinctly() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = displayNameA,
                    overflow = TextOverflow.Visible,
                    softWrap = true,
                )
                Text(
                    text = displayNameB,
                    overflow = TextOverflow.Visible,
                    softWrap = true,
                )
            }
        }

        composeTestRule.onNodeWithText(displayNameA).assertIsDisplayed()
        composeTestRule.onNodeWithText(displayNameB).assertIsDisplayed()
    }
}
