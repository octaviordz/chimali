package com.chimali.feature.vault.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import java.util.UUID
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
