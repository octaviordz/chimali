package com.chimali.feature.vault.internal.crypto

import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SecureNoteCryptoTest {
    @Test
    fun `payload clears memory correctly`() {
        val contentArray = charArrayOf('m', 'y', ' ', 's', 'e', 'c', 'r', 'e', 't')
        val customFieldValue = charArrayOf('m', 'o', 'r', 'e')

        val payload =
            SecureNotePayload(
                title = "My Note",
                content = contentArray,
                customFields =
                    listOf(
                        CustomField("Extra Note", customFieldValue, true),
                    ),
            )

        // Clear memory
        payload.clearMemory()

        // Verify arrays are zeroed out
        assertArrayEquals(CharArray(9), contentArray)
        assertArrayEquals(CharArray(4), customFieldValue)
    }
}
