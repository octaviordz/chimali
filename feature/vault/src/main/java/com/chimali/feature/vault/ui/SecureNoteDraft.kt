package com.chimali.feature.vault.ui

import com.chimali.feature.vault.internal.payload.SecureNotePayload

/** T056: typed mutable field ownership for one editor; framework display remains gated. */
internal class SecureNoteDraft(
    initial: SecureNotePayload?,
) {
    val title = MutableDraftField(initial?.title)
    val content = MutableDraftField(initial?.content)
    val fields = listOf(title, content)

    val isClosed: Boolean get() = title.isClosed

    fun hasTextChanges(initial: SecureNotePayload?): Boolean =
        if (initial == null) {
            fields.any { it.isNotBlank() }
        } else {
            !matchesDraftText(title, initial.title) ||
                !matchesDraftText(content, initial.content)
        }

    fun clear() {
        fields.forEach { it.clear() }
    }

    override fun toString(): String = "SecureNoteDraft([redacted])"
}
