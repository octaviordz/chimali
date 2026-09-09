package com.chimali.feature.vault.ui

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.inputmethod.EditorInfoCompat
import com.chimali.feature.vault.api.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Audits the actual editor-to-Android boundary, not third-party keyboard compliance. */
@RunWith(Parameterized::class)
class VaultInputPolicyInstrumentationTest(
    private val type: VaultType,
) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun entryEditorRequestsRestrictedKeyboardRetention() {
        compose.setContent {
            MaterialTheme {
                when (type) {
                    VaultType.PASSWORD -> PasswordEntryScreen(onSave = {}, onCancel = {})
                    VaultType.CREDIT_CARD -> CreditCardEntryScreen(onSave = {}, onCancel = {})
                    VaultType.NOTE -> SecureNoteEntryScreen(onSave = {}, onCancel = {})
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("synthetic-sensitive-title")
        compose.onAllNodes(hasSetTextAction())[0].performClick()
        compose.runOnIdle {
            val info = EditorInfo()
            val connection = compose.activity.currentFocus?.onCreateInputConnection(info)
            assertNotNull("Editor must expose its configured Android input connection", connection)
            val required =
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            assertEquals(required, info.imeOptions and required)
            assertEquals(0, info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            assertEquals(0, info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
            assertEquals(
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            )
            assertEquals("", EditorInfoCompat.getInitialTextBeforeCursor(info, 100, 0)?.toString().orEmpty())
            assertEquals("", EditorInfoCompat.getInitialTextAfterCursor(info, 100, 0)?.toString().orEmpty())
            assertEquals(
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                compose.activity.window.decorView.importantForAutofill,
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun types(): List<Array<VaultType>> = VaultType.entries.map { arrayOf(it) }
    }
}
