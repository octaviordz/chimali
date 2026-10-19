package com.chimali.fido2.presentation.ui

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.data.crypto.WalletMasterSeedProvider
import com.chimali.fido2.presentation.viewmodel.DevToolsIntent
import com.chimali.fido2.presentation.viewmodel.DevToolsUiState
import com.chimali.fido2.presentation.viewmodel.DevToolsViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T143 — Accessibility tests for [DevelopmentToolsScreen].
 */
class DevelopmentToolsScreenTest {
    private companion object {
        const val SURROUNDING_TEXT_LIMIT = 100
        const val MNEMONIC_FIRST_WORDS =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        const val MNEMONIC_LAST_WORDS =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun devToolsTitlesHaveHeadingRole() {
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
        composeTestRule
            .onNodeWithText("Development Tools")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))

        // Section title
        composeTestRule
            .onNodeWithText("Test & Debug Utilities")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun mnemonicGridItemsHaveMergedDescendants() {
        val testWords = "apple banana cherry".toCharArray()
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

        // The grid intentionally merges each index and word into one focusable item.
        composeTestRule
            .onNode(hasText("1.", substring = true) and hasText("apple", substring = true))
            .assertExists()
        testWords.fill('\u0000')
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun qrHideAndClearUseExplicitUserActions() {
        val testWords = "apple banana cherry".toCharArray()
        val intents = mutableListOf<DevToolsIntent>()
        composeTestRule.setContent {
            MaterialTheme {
                DevelopmentToolsContent(
                    state = DevToolsUiState(mnemonicWords = testWords, isMnemonicVisible = true),
                    snackbarHostState = remember { SnackbarHostState() },
                    onIntent = { intents.add(it) },
                    onHomeTestRegistration = {},
                )
            }
        }

        composeTestRule.onNodeWithText("QR").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Hide QR").performScrollTo().assertExists()
        composeTestRule
            .onNodeWithContentDescription("Master seed QR code", useUnmergedTree = true)
            .performScrollTo()
            .assertExists()
        composeTestRule.onNodeWithText("Copy").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Hide QR").performScrollTo().performClick()
        composeTestRule
            .onNodeWithContentDescription("Master seed QR code", useUnmergedTree = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear").performScrollTo().performClick()

        assertEquals(
            listOf(DevToolsIntent.CopyToClipboard, DevToolsIntent.ClearMnemonic),
            intents,
        )
        testWords.fill('\u0000')
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun manualRecoveryTransfersOneMutablePhraseBuffer() {
        val intents = mutableListOf<DevToolsIntent>()
        val words = (1..24).map { "word$it" }
        composeTestRule.setContent {
            MaterialTheme {
                DevelopmentToolsContent(
                    state = DevToolsUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onIntent = { intents.add(it) },
                    onHomeTestRegistration = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Toggle recover form").performClick()
        val grid = composeTestRule.onNodeWithTag("manual-recovery-grid")
        words.forEachIndexed { index, word ->
            val fieldTag = "manual-recovery-word-$index"
            grid.performScrollToNode(hasTestTag(fieldTag))
            composeTestRule.onNodeWithTag(fieldTag).performTextInput(word)
        }
        composeTestRule.onNodeWithText("Recover Seed").performClick()

        val intent = intents.single() as DevToolsIntent.RecoverFromSeed
        assertEquals(words.joinToString(" "), String(intent.mnemonic))
        intent.mnemonic.fill('\u0000')
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun manualRecoveryDisablesImeLearningAutofillAndExtractedText() {
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

        composeTestRule.onNodeWithContentDescription("Toggle recover form").performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("synthetic")
        composeTestRule.onAllNodes(hasSetTextAction())[0].performClick()
        composeTestRule.runOnIdle {
            val editorInfo = EditorInfo()
            val connection = composeTestRule.activity.currentFocus?.onCreateInputConnection(editorInfo)
            org.junit.Assert.assertNotNull("Mnemonic editor must expose its Android input connection", connection)
            val requiredFlags =
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            assertEquals(requiredFlags, editorInfo.imeOptions and requiredFlags)
            assertEquals("No custom IME action is registered for mnemonic input", 0, editorInfo.actionId)
            assertNull("No custom IME action label should expose mnemonic content", editorInfo.actionLabel)
            assertEquals(0, editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            assertEquals(0, editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
            assertEquals(
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                editorInfo.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            )
            assertEquals(
                "",
                EditorInfoCompat
                    .getInitialTextBeforeCursor(editorInfo, SURROUNDING_TEXT_LIMIT, 0)
                    ?.toString()
                    .orEmpty(),
            )
            assertEquals(
                "",
                EditorInfoCompat
                    .getInitialTextAfterCursor(editorInfo, SURROUNDING_TEXT_LIMIT, 0)
                    ?.toString()
                    .orEmpty(),
            )
            assertEquals(
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                composeTestRule.activity.window.decorView.importantForAutofill,
            )
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun masterSeedScreenRevealsAfterAuthenticationAndErasesOnDisposal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString()
        val recoveryFile = File(context.filesDir, "hdk-device-tests/$testId/user-preferences.pb")
        recoveryFile.parentFile?.mkdirs()
        val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
        val provider: MasterSeedProvider =
            WalletMasterSeedProvider(
                masterSeedGenerator = Bip39MasterSeedGenerator(),
                dataStore = dataStore,
                encryptionWrapper = EncryptionWrapper(),
                lookupTokenService = HmacMetadataLookupTokenService(),
                encryptedMetadataService = AesGcmEncryptedMetadataService(),
            )
        val importBuffer = "$MNEMONIC_FIRST_WORDS $MNEMONIC_LAST_WORDS".toCharArray()
        val importResult = runBlocking { provider.importMnemonic(importBuffer) }
        assertEquals(ImportMnemonicResult.Created, importResult)
        assertTrue(importBuffer.all { it == '\u0000' })

        val clipboard =
            object : ClipboardManagerService {
                override suspend fun copySensitiveData(
                    label: String,
                    text: String,
                    clearDelayMs: Long,
                ): Result<Unit> = Result.success(Unit)

                override suspend fun clearClipboard(): Result<Unit> = Result.success(Unit)
            }
        val viewModel = DevToolsViewModel(provider, clipboard)
        val viewModelStore = ViewModelStore().apply { put("dev-tools", viewModel) }
        val screenVisible = mutableStateOf(true)
        var biometricSuccess: (() -> Unit)? = null

        try {
            composeTestRule.setContent {
                if (screenVisible.value) {
                    MaterialTheme {
                        DevelopmentToolsScreen(
                            devToolsViewModel = viewModel,
                            onHomeTestRegistration = {},
                            authenticateMnemonic = { activity, onSuccess ->
                                assertEquals(composeTestRule.activity, activity)
                                biometricSuccess = onSuccess
                            },
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("View Master Seed").performScrollTo().performClick()
            assertEquals(
                "Starting authentication must not reveal the mnemonic",
                null,
                viewModel.state.value.mnemonicWords,
            )
            composeTestRule.onNodeWithText("abandon").assertDoesNotExist()

            requireNotNull(biometricSuccess).invoke()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.state.value.isMnemonicVisible && viewModel.state.value.mnemonicWords != null
            }
            composeTestRule
                .onNode(hasText("24.", substring = true) and hasText("art", substring = true))
                .assertExists()
            val revealedBuffer = requireNotNull(viewModel.state.value.mnemonicWords)

            composeTestRule.runOnIdle { screenVisible.value = false }
            composeTestRule.waitUntil(timeoutMillis = 10_000) { viewModel.state.value.mnemonicWords == null }
            assertTrue("Screen disposal must erase the ViewModel-owned phrase", revealedBuffer.all { it == '\u0000' })
        } finally {
            screenVisible.value = false
            viewModelStore.clear()
            provider.clearSession()
            recoveryFile.parentFile?.deleteRecursively()
        }
    }
}
