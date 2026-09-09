package com.chimali.feature.vault.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-VAULT-026: retention-control flags must preserve editing actions and input variation. */
class VaultInputPolicyTest {
    @Test
    fun policyPreservesActionAndDisablesLearningAndExtractUi() {
        val flags = protectedImeOptions(EditorInfo.IME_ACTION_DONE)
        assertEquals(EditorInfo.IME_ACTION_DONE, flags and EditorInfo.IME_MASK_ACTION)
        for (flag in listOf(
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            EditorInfo.IME_FLAG_NO_EXTRACT_UI,
            EditorInfo.IME_FLAG_NO_FULLSCREEN,
        )) {
            assertTrue(flags and flag != 0)
        }
    }

    @Test
    fun textPolicyPreservesMultilineAndPasswordVariationWhileDisablingSuggestions() {
        val type =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        val protected = protectedInputType(type)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, protected and InputType.TYPE_MASK_VARIATION)
        assertTrue(protected and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
        assertTrue(protected and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        assertEquals(0, protected and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertEquals(0, protected and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        assertEquals(InputType.TYPE_CLASS_NUMBER, protectedInputType(InputType.TYPE_CLASS_NUMBER))
    }
}
