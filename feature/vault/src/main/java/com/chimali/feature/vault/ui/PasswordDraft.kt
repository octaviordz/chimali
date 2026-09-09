package com.chimali.feature.vault.ui

import com.chimali.feature.vault.internal.payload.PasswordPayload

/** T056: typed mutable field ownership for one editor; framework display remains gated. */
internal class PasswordDraft(
    initial: PasswordPayload?,
) {
    val title = MutableDraftField(initial?.title)
    val username = MutableDraftField(initial?.username)
    val password = MutableDraftField(initial?.password)
    val uri = MutableDraftField(initial?.uri)
    val notes = MutableDraftField(initial?.notes)
    val fields = listOf(title, username, password, uri, notes)

    val isClosed: Boolean get() = title.isClosed

    fun hasTextChanges(initial: PasswordPayload?): Boolean =
        if (initial == null) {
            fields.any { it.isNotBlank() }
        } else {
            !matchesDraftText(title, initial.title) ||
                !matchesDraftText(username, initial.username) ||
                !matchesDraftText(password, initial.password) ||
                !matchesDraftText(uri, initial.uri) ||
                !matchesDraftText(notes, initial.notes)
        }

    fun clear() {
        fields.forEach { it.clear() }
    }

    override fun toString(): String = "PasswordDraft([redacted])"
}
