package com.chimali.feature.vault.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chimali.core.clipboard.AndroidClipboardManagerService
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.PasswordPayload
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * T143 — Accessibility tests for [VaultListScreen].
 */
class VaultListScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun simpleSmokeTest() {
        composeTestRule.setContent {
            androidx.compose.material3.Text("Hello World")
        }
        composeTestRule.onNodeWithText("Hello World").assertExists()
    }

    @Test
    fun passwordDetailSmokeTest() {
        val payload = PasswordPayload("entry", "user".toCharArray(), "synthetic-secret".toCharArray(), "uri")
        composeTestRule.setContent {
            MaterialTheme {
                PasswordDetailScreen(
                    payload = payload,
                    onEdit = {},
                    onDelete = {},
                    onBack = {},
                    onCopyPassword = {},
                    onCopyMessage = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Copy password").assertExists()
    }

    @Test
    fun passwordDetailCopiesToSystemClipboardAndClearsAfterSixtySeconds() {
        val service = AndroidClipboardManagerService(composeTestRule.activity)
        val clipboard = composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var copyMessage by mutableStateOf<String?>(null)
        val payload = PasswordPayload("entry", "user".toCharArray(), "synthetic-secret".toCharArray(), "uri")
        composeTestRule.setContent {
            MaterialTheme {
                PasswordDetailScreen(
                    payload = payload,
                    onEdit = {},
                    onDelete = {},
                    onBack = {},
                    onCopyPassword = { copy ->
                        val result = runBlocking { service.copySensitiveData("Password", String(copy)) }
                        copy.fill('\u0000')
                        copyMessage =
                            result.fold(
                                onSuccess = { "Password copied. Clipboard clears in 60s." },
                                onFailure = { "Unable to copy password to clipboard." },
                            )
                    },
                    copyMessage = copyMessage,
                    onCopyMessage = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Copy password").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeTestRule.activity)
                ?.toString() ==
                "synthetic-secret"
        }
        composeTestRule.onNodeWithText("Password copied. Clipboard clears in 60s.").assertExists()
        Thread.sleep(61_000)
        assertNull(clipboard.primaryClip)
    }

    @Test
    fun vaultList_titleIsHeading() {
        composeTestRule.setContent {
            MaterialTheme {
                VaultListScreen(
                    items = emptyList(),
                    labels = emptyList(),
                    selectedLabelId = null,
                    onItemClick = {},
                    onAddClick = {},
                    onLabelFilterClick = {},
                    onManageLabelsClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Credentials Vault")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun vaultItemRow_mergedDescendants() {
        val testItem =
            VaultItem(
                id = UUID.randomUUID(),
                type = VaultType.PASSWORD,
                title = "GitHub",
                payload = byteArrayOf(),
                crdtState = byteArrayOf(),
                dateCreated = "",
                dateModified = "",
                lastBackedUpAt = null,
                identityId = UUID.randomUUID(),
            )

        composeTestRule.setContent {
            MaterialTheme {
                VaultItemRow(item = testItem, onClick = {})
            }
        }

        // Verify that the row is a single focusable node containing both texts
        // In the unmerged tree, "GitHub" and "PASSWORD" are separate.
        // In the merged tree (TalkBack's view), they are grouped into a single node.
        composeTestRule
            .onNode(hasText("GitHub").and(hasText("PASSWORD")), useUnmergedTree = false)
            .assertExists()
            .assertHasClickAction()
    }
}
