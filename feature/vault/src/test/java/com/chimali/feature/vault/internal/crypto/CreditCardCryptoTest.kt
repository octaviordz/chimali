package com.chimali.feature.vault.internal.crypto

import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CreditCardCryptoTest {
    @Test
    fun `payload clears memory correctly`() {
        val notesArray = charArrayOf('s', 'e', 'c', 'r', 'e', 't')
        val cardholderNameArray = charArrayOf('J', 'o', 'h', 'n')
        val cardNumberArray = charArrayOf('4', '1', '1', '1')
        val cvvArray = charArrayOf('1', '2', '3')
        val customFieldValue = charArrayOf('1', '2', '3', '4', '5')

        val payload =
            CreditCardPayload(
                title = "My Visa",
                cardholderName = cardholderNameArray,
                cardNumber = cardNumberArray,
                expirationDate = "12/26",
                cvv = cvvArray,
                notes = notesArray,
                customFields =
                    listOf(
                        CustomField("Zip Code", customFieldValue, true),
                    ),
            )

        // Clear memory
        payload.clearMemory()

        // Verify arrays are zeroed out
        assertArrayEquals(CharArray(4), cardholderNameArray)
        assertArrayEquals(CharArray(4), cardNumberArray)
        assertArrayEquals(CharArray(3), cvvArray)
        assertArrayEquals(CharArray(6), notesArray)
        assertArrayEquals(CharArray(5), customFieldValue)
    }
}
