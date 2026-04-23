package com.chimali.fido2.presentation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chimali.fido2.presentation.viewmodel.DevToolsUiState
import org.junit.Rule
import org.junit.Test

/**
 * T143 — Accessibility tests for [DevelopmentToolsScreen].
 */
class DevelopmentToolsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun devTools_titlesHaveHeadingRole() {
        composeTestRule.setContent {
            MaterialTheme {
                DevelopmentToolsContent(
                    state = DevToolsUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onIntent = {},
                    onHomeTestRegistration = {},
                )
            }
        }

        // Top app bar title
        composeTestRule.onNodeWithText("Development Tools")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))

        // Section title
        composeTestRule.onNodeWithText("Test & Debug Utilities")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun mnemonicGrid_itemsHaveMergedDescendants() {
        val testWords = listOf("apple", "banana", "cherry")
        composeTestRule.setContent {
            MaterialTheme {
                DevelopmentToolsContent(
                    state = DevToolsUiState(mnemonicWords = testWords),
                    snackbarHostState = remember { SnackbarHostState() },
                    onIntent = {},
                    onHomeTestRegistration = {},
                )
            }
        }

        // Verify that "1. apple" is a single focusable node
        // We find by index text and check if it contains the word as a child/descendant
        composeTestRule.onNodeWithText("1.", useUnmergedTree = false)
            .assertExists()
            .assert(hasAnyDescendant(hasText("apple")))
    }
}
