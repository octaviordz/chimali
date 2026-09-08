package com.chimali.feature.vault.api

import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID

data class VaultState(
    val items: List<VaultItem> = emptyList(),
    val labels: List<LabelUiModel> = emptyList(),
    val selectedLabelId: UUID? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedItem: VaultItem? = null,
    val selectedPasswordPayload: PasswordPayload? = null,
    val selectedCreditCardPayload: CreditCardPayload? = null,
    val selectedSecureNotePayload: SecureNotePayload? = null,
)

sealed interface VaultIntent {
    data class LoadItems(
        val filterLabelId: UUID? = null,
    ) : VaultIntent

    data object LoadLabels : VaultIntent

    data class CreateLabel(
        val name: String,
        val colorHex: String,
    ) : VaultIntent

    data class DeleteLabel(
        val id: UUID,
    ) : VaultIntent

    data class SaveItem(
        val item: VaultItem,
    ) : VaultIntent

    data class SavePassword(
        val id: UUID? = null,
        val payload: PasswordPayload,
        val identityId: UUID = UUID(0, 0),
    ) : VaultIntent

    data class SaveCreditCard(
        val id: UUID? = null,
        val payload: CreditCardPayload,
        val identityId: UUID = UUID(0, 0),
    ) : VaultIntent

    data class SaveSecureNote(
        val id: UUID? = null,
        val payload: SecureNotePayload,
        val identityId: UUID = UUID(0, 0),
    ) : VaultIntent

    data class DeleteItem(
        val id: UUID,
    ) : VaultIntent

    data class DecryptItem(
        val id: UUID,
    ) : VaultIntent

    data object ClearSelectedItem : VaultIntent

    data object ClearClipboard : VaultIntent
}
