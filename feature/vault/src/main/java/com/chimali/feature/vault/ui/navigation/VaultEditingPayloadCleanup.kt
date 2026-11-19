package com.chimali.feature.vault.ui.navigation

import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload

/** Clears independent edit-session copies when their owner completes, cancels, or leaves the editor. */
internal fun clearEditingPayloadCopies(
    password: PasswordPayload?,
    creditCard: CreditCardPayload?,
    secureNote: SecureNotePayload?,
) {
    password?.clearMemory()
    creditCard?.clearMemory()
    secureNote?.clearMemory()
}
