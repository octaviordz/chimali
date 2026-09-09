package com.chimali.feature.vault.ui

import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-VAULT-026: rejected handoff and independent custom-field editor ownership. */
class DraftOwnershipTest {
    @Test
    fun callbackFailureErasesSubmissionButPreservesRetryDraft() {
        val draft = PasswordPayload("title", "user".toCharArray(), "secret".toCharArray(), "uri")
        val attempt = draft.copyForEditing()
        try {
            assertThrows(IllegalStateException::class.java) {
                submitOwned(attempt) { error("dispatch unavailable") }
            }
            assertTrue(attempt.title.all { it == '\u0000' })
            assertTrue(attempt.password.all { it == '\u0000' })
            assertEquals("secret", String(draft.password))
        } finally {
            draft.clearMemory()
        }
    }

    @Test
    fun acceptedHandoffBelongsToReceiverUntilReceiverClearsIt() {
        val attempt = PasswordPayload("title", "user".toCharArray(), "secret".toCharArray(), "uri")
        var received: PasswordPayload? = null
        submitOwned(attempt) { received = it }
        try {
            assertEquals("secret", String(received!!.password))
        } finally {
            received!!.clearMemory()
        }
        assertTrue(attempt.password.all { it == '\u0000' })
    }

    @Test
    fun detailDisposalAndSubmissionCleanupDoNotEraseEditedCustomFields() {
        val original = CustomField("field", "value".toCharArray(), false)
        val draft = copyDraftFields(listOf(original)).single()
        original.clearMemory()
        draft.value[0] = 'X'
        val submission = draft.copyForEditing()
        submission.clearMemory()
        try {
            assertEquals("field", String(draft.name))
            assertEquals("Xalue", String(draft.value))
        } finally {
            draft.clearMemory()
        }
    }

    @Test
    fun dirtyComparisonHandlesEmptyNullAndChangedContentsWithoutCopies() {
        assertTrue(matchesDraftText("", null))
        assertTrue(matchesDraftText("same", "same".toCharArray()))
        org.junit.Assert.assertFalse(matchesDraftText("same", "other".toCharArray()))
        org.junit.Assert.assertFalse(matchesDraftText("same", "sAme".toCharArray()))
    }
}
