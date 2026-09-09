package com.chimali.feature.vault.ui

import com.chimali.feature.vault.internal.payload.CreditCardPayload

/** T056: typed mutable field ownership for one editor; framework display remains gated. */
internal class CreditCardDraft(
    initial: CreditCardPayload?,
) {
    val title = MutableDraftField(initial?.title)
    val cardholderName = MutableDraftField(initial?.cardholderName)
    val cardNumber = MutableDraftField(initial?.cardNumber)
    val expirationDate = MutableDraftField(initial?.expirationDate)
    val cvv = MutableDraftField(initial?.cvv)
    val notes = MutableDraftField(initial?.notes)
    val fields = listOf(title, cardholderName, cardNumber, expirationDate, cvv, notes)

    val isClosed: Boolean get() = title.isClosed

    fun hasTextChanges(initial: CreditCardPayload?): Boolean =
        if (initial == null) {
            fields.any { it.isNotBlank() }
        } else {
            !matchesDraftText(title, initial.title) ||
                !matchesDraftText(cardholderName, initial.cardholderName) ||
                !matchesDraftText(cardNumber, initial.cardNumber) ||
                !matchesDraftText(expirationDate, initial.expirationDate) ||
                !matchesDraftText(cvv, initial.cvv) ||
                !matchesDraftText(notes, initial.notes)
        }

    fun clear() {
        fields.forEach { it.clear() }
    }

    override fun toString(): String = "CreditCardDraft([redacted])"
}
