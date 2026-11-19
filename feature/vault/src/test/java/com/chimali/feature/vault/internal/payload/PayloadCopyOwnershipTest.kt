package com.chimali.feature.vault.internal.payload

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class PayloadCopyOwnershipTest {
    @Test
    fun customFieldCopyDoesNotShareUnchangedSecretArray() {
        val original = CustomField("field".toCharArray(), "secret-value".toCharArray(), isConcealed = true)
        val edited = original.copy(name = "renamed".toCharArray())

        original.clearMemory()

        assertNotSame(original.value, edited.value)
        assertArrayEquals("renamed".toCharArray(), edited.name)
        assertArrayEquals("secret-value".toCharArray(), edited.value)
    }

    @Test
    fun payloadCopiesOwnIndependentArraysAndNestedFields() {
        val passwordField = CustomField("p-name".toCharArray(), "p-value".toCharArray(), isConcealed = true)
        val password =
            PasswordPayload(
                title = "p-title".toCharArray(),
                username = "p-user".toCharArray(),
                password = "p-secret".toCharArray(),
                uri = "p-uri".toCharArray(),
                notes = "p-notes".toCharArray(),
                customFields = listOf(passwordField),
            )
        val passwordCopy = password.copy()

        val noteField = CustomField("n-name".toCharArray(), "n-value".toCharArray(), isConcealed = false)
        val note = SecureNotePayload("n-title".toCharArray(), "n-secret".toCharArray(), listOf(noteField))
        val noteCopy = note.copy()

        val cardField = CustomField("c-name".toCharArray(), "c-value".toCharArray(), isConcealed = true)
        val card =
            CreditCardPayload(
                title = "c-title".toCharArray(),
                cardholderName = "c-holder".toCharArray(),
                cardNumber = "4111111111111111".toCharArray(),
                expirationDate = "12/30".toCharArray(),
                cvv = "123".toCharArray(),
                notes = "c-notes".toCharArray(),
                customFields = listOf(cardField),
            )
        val cardCopy = card.copy()

        password.clearMemory()
        note.clearMemory()
        card.clearMemory()

        val passwordCustomField = requireNotNull(passwordCopy.customFields).single()
        val noteCustomField = requireNotNull(noteCopy.customFields).single()
        val cardCustomField = requireNotNull(cardCopy.customFields).single()

        assertNotSame(password.password, passwordCopy.password)
        assertNotSame(password.customFields?.single()?.value, passwordCopy.customFields?.single()?.value)
        assertArrayEquals("p-title".toCharArray(), passwordCopy.title)
        assertArrayEquals("p-user".toCharArray(), passwordCopy.username)
        assertArrayEquals("p-secret".toCharArray(), passwordCopy.password)
        assertArrayEquals("p-uri".toCharArray(), passwordCopy.uri)
        assertArrayEquals("p-notes".toCharArray(), passwordCopy.notes)
        assertArrayEquals("p-name".toCharArray(), passwordCustomField.name)
        assertArrayEquals("p-value".toCharArray(), passwordCustomField.value)

        assertNotSame(note.content, noteCopy.content)
        assertNotSame(note.customFields?.single()?.value, noteCopy.customFields?.single()?.value)
        assertArrayEquals("n-title".toCharArray(), noteCopy.title)
        assertArrayEquals("n-secret".toCharArray(), noteCopy.content)
        assertArrayEquals("n-name".toCharArray(), noteCustomField.name)
        assertArrayEquals("n-value".toCharArray(), noteCustomField.value)

        assertNotSame(card.cardNumber, cardCopy.cardNumber)
        assertNotSame(card.customFields?.single()?.value, cardCopy.customFields?.single()?.value)
        assertArrayEquals("c-title".toCharArray(), cardCopy.title)
        assertArrayEquals("c-holder".toCharArray(), cardCopy.cardholderName)
        assertArrayEquals("4111111111111111".toCharArray(), cardCopy.cardNumber)
        assertArrayEquals("12/30".toCharArray(), cardCopy.expirationDate)
        assertArrayEquals("123".toCharArray(), cardCopy.cvv)
        assertArrayEquals("c-notes".toCharArray(), cardCopy.notes)
        assertArrayEquals("c-name".toCharArray(), cardCustomField.name)
        assertArrayEquals("c-value".toCharArray(), cardCustomField.value)
    }
}
