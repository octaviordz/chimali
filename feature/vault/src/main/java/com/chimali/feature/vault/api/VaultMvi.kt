package com.chimali.feature.vault.api

import java.util.UUID

data class VaultState(
    val items: List<VaultItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedItem: VaultItem? = null,
)

sealed interface VaultIntent {
    data class LoadItems(
        val filterLabelId: UUID? = null,
    ) : VaultIntent

    data class SaveItem(
        val item: VaultItem,
    ) : VaultIntent

    data class DeleteItem(
        val id: UUID,
    ) : VaultIntent

    data class DecryptItem(
        val id: UUID,
    ) : VaultIntent

    data object ClearClipboard : VaultIntent
}
