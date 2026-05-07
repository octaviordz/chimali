package com.chimali.feature.vault.ui.model

import java.util.UUID

/**
 * UI representation of a Label for categorizing vault items.
 */
data class LabelUiModel(
    val id: UUID,
    val name: String,
    val colorHex: String,
)
