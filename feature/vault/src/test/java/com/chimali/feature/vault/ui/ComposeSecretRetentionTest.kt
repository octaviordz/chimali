package com.chimali.feature.vault.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import java.nio.CharBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T055 characterization, NOT a security acceptance test: logical clearing retains old immutable text. */
class ComposeSecretRetentionTest {
    @Test
    fun resolvedComposeTextFieldStateRetainsImmutableSnapshotAfterClear() {
        val input = "T055-synthetic-marker".toCharArray()
        val state = TextFieldState()
        state.edit { append(CharBuffer.wrap(input)) }
        val priorFrameworkSnapshot = state.text
        input.fill('\u0000')
        state.clearText()
        assertTrue(state.text.isEmpty())
        assertTrue(priorFrameworkSnapshot is String)
        assertEquals("T055-synthetic-marker", priorFrameworkSnapshot)
    }
}
