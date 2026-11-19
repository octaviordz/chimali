package com.chimali.feature.vault.ui.navigation

import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VaultEditingPayloadCleanupTest {
    @Test
    fun clearsAllRetainedEditPayloadOwnersAndNestedCustomFields() {
        val passwordField = CustomField("password-field".toCharArray(), "password-value".toCharArray(), true)
        val password =
            PasswordPayload(
                "password-title".toCharArray(),
                "username".toCharArray(),
                "password".toCharArray(),
                "uri".toCharArray(),
                "notes".toCharArray(),
                listOf(passwordField),
            )
        val cardField = CustomField("card-field".toCharArray(), "card-value".toCharArray(), true)
        val card =
            CreditCardPayload(
                "card-title".toCharArray(),
                "cardholder".toCharArray(),
                "4111".toCharArray(),
                "12/30".toCharArray(),
                "123".toCharArray(),
                "card-notes".toCharArray(),
                listOf(cardField),
            )
        val noteField = CustomField("note-field".toCharArray(), "note-value".toCharArray(), false)
        val note = SecureNotePayload("note-title".toCharArray(), "note-content".toCharArray(), listOf(noteField))

        clearEditingPayloadCopies(password, card, note)
        clearEditingPayloadCopies(password, card, note)

        assertArrayEquals(CharArray(password.title.size), password.title)
        assertArrayEquals(CharArray(password.username.size), password.username)
        assertArrayEquals(CharArray(password.password.size), password.password)
        assertArrayEquals(CharArray(password.uri.size), password.uri)
        assertArrayEquals(CharArray(requireNotNull(password.notes).size), password.notes)
        assertArrayEquals(CharArray(passwordField.name.size), passwordField.name)
        assertArrayEquals(CharArray(passwordField.value.size), passwordField.value)

        assertArrayEquals(CharArray(card.title.size), card.title)
        assertArrayEquals(CharArray(card.cardholderName.size), card.cardholderName)
        assertArrayEquals(CharArray(card.cardNumber.size), card.cardNumber)
        assertArrayEquals(CharArray(card.expirationDate.size), card.expirationDate)
        assertArrayEquals(CharArray(card.cvv.size), card.cvv)
        assertArrayEquals(CharArray(requireNotNull(card.notes).size), card.notes)
        assertArrayEquals(CharArray(cardField.name.size), cardField.name)
        assertArrayEquals(CharArray(cardField.value.size), cardField.value)

        assertArrayEquals(CharArray(note.title.size), note.title)
        assertArrayEquals(CharArray(note.content.size), note.content)
        assertArrayEquals(CharArray(noteField.name.size), noteField.name)
        assertArrayEquals(CharArray(noteField.value.size), noteField.value)
    }
}
