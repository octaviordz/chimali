package com.chimali.feature.vault.ui

import com.chimali.feature.vault.internal.payload.CustomField

/** FR-VAULT-026: comparisons borrow the UI value and do not allocate secret copies. */
internal fun matchesDraftText(
    text: CharSequence,
    baseline: CharArray?,
): Boolean = text.length == (baseline?.size ?: 0) && text.indices.all { text[it] == baseline!![it] }

internal fun copyDraftFields(fields: List<CustomField>?): List<CustomField> =
    fields.orEmpty().map { it.copyForEditing() }

// Existing unresolved UI boundary (T055/T059), centralized to avoid extra conversion paths.
internal fun initialDraftText(value: CharArray?): String = value?.let { String(it) }.orEmpty()
