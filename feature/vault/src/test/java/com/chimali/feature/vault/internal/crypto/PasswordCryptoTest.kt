package com.chimali.feature.vault.internal.crypto

import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PasswordCryptoTest {

    @Test
    fun `payload clears memory correctly`() {
        val notesArray = charArrayOf('s', 'e', 'c', 'r', 'e', 't')
        val passwordArray = charArrayOf('p', 'a', 's', 's')
        val usernameArray = charArrayOf('u', 's', 'e', 'r')
        val customFieldValue = charArrayOf('a', 'n', 's', 'w', 'e', 'r')

        val payload = PasswordPayload(
            title = "My Bank",
            username = usernameArray,
            password = passwordArray,
            uri = "https://bank.com",
            notes = notesArray,
            customFields = listOf(
                CustomField("Security Question", customFieldValue, true)
            )
        )

        // Clear memory
        payload.clearMemory()

        // Verify arrays are zeroed out
        assertArrayEquals(charArrayOf('0', '0', '0', '0'), usernameArray)
        assertArrayEquals(charArrayOf('0', '0', '0', '0'), passwordArray)
        assertArrayEquals(charArrayOf('0', '0', '0', '0', '0', '0'), notesArray)
        assertArrayEquals(charArrayOf('0', '0', '0', '0', '0', '0'), customFieldValue)
    }
}
