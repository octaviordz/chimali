package com.chimali.feature.vault.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MutableDraftFieldTest {
    @Test
    fun lateInputAfterClosureIsIgnoredWithoutReadingOrCopyingIt() {
        val field = MutableDraftField("synthetic".toCharArray())
        assertTrue(!field.isClosed)
        field.clear()
        assertTrue(field.isClosed)
        val late =
            object : CharSequence {
                override val length: Int get() = error("Late input must not be read")

                override fun get(index: Int): Char = error("Late input must not be read")

                override fun subSequence(
                    startIndex: Int,
                    endIndex: Int,
                ): CharSequence = error("Late input must not be read")
            }
        field.replace(late)
        assertEquals(0, field.length)
    }

    @Test
    fun failedReplacementPreservesRetryValueAndBorrowedSlicesAreWiped() {
        val field = MutableDraftField("original".toCharArray())
        val slice = field.subSequence(1, 4)
        val throwingInput =
            object : CharSequence {
                override val length = 2

                override fun get(index: Int): Char {
                    if (index == 1) error("synthetic input failure")
                    return 's'
                }

                override fun subSequence(
                    startIndex: Int,
                    endIndex: Int,
                ): CharSequence = error("Not used")
            }
        assertThrows(IllegalStateException::class.java) { field.replace(throwingInput) }
        assertEquals('o', field[0])
        assertEquals("original", field.displayText())
        field.replace("retry")
        assertTrue(slice.all { it == '\u0000' })
        field.clear()
    }

    @Test
    fun replacementErasesPreviousStorageAndSubmissionRemainsIndependent() {
        val original = "synthetic".toCharArray()
        val field = MutableDraftField(original)
        val retired = field.borrowChars()
        val submission = field.copyChars()
        field.replace("replacement")
        assertTrue(retired.all { it == '\u0000' })
        assertArrayEquals(original, submission)
        assertArrayEquals(original, "synthetic".toCharArray())
        val current = field.borrowChars()
        field.clear()
        field.clear()
        assertTrue(field.isClosed)
        assertTrue(current.all { it == '\u0000' })
        assertEquals(0, field.length)
        field.replace("late")
        assertEquals(0, field.length)
        submission.fill('\u0000')
        original.fill('\u0000')
    }

    @Test
    fun typedDraftsOwnAllMainFieldsAndRedactDiagnostics() {
        val password = PasswordDraft(null)
        val card = CreditCardDraft(null)
        val note = SecureNoteDraft(null)
        val fields = password.fields + card.fields + note.fields
        fields.forEach { it.replace("synthetic") }
        val refs = fields.map { it.borrowChars() }
        assertTrue(fields.all { !it.toString().contains("synthetic") })
        password.clear()
        card.clear()
        note.clear()
        refs.forEach { assertTrue(it.all { c -> c == '\u0000' }) }
    }
}
