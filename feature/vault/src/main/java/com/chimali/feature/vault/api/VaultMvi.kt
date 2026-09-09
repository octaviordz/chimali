package com.chimali.feature.vault.api

import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID

enum class VaultMutationState {
    IDLE,
    PENDING,
    SUCCEEDED,
    FAILED,
}

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
    val mutationState: VaultMutationState = VaultMutationState.IDLE,
    val copyMessage: String? = null,
    val selectedItemLabelIds: Set<UUID> = emptySet(),
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
        val labelIds: List<UUID> = emptyList(),
    ) : VaultIntent

    data class SaveCreditCard(
        val id: UUID? = null,
        val payload: CreditCardPayload,
        val identityId: UUID = UUID(0, 0),
        val labelIds: List<UUID> = emptyList(),
    ) : VaultIntent

    data class SaveSecureNote(
        val id: UUID? = null,
        val payload: SecureNotePayload,
        val identityId: UUID = UUID(0, 0),
        val labelIds: List<UUID> = emptyList(),
    ) : VaultIntent

    data class DeleteItem(
        val id: UUID,
    ) : VaultIntent

    data class DecryptItem(
        val id: UUID,
    ) : VaultIntent

    data object ClearSelectedItem : VaultIntent

    data object ResetMutation : VaultIntent

    /** Disposes a save session; late completion cannot navigate another editor. */
    data object AbandonMutation : VaultIntent

    data object ClearClipboard : VaultIntent

    data object ClearCopyMessage : VaultIntent

    /** Consumes an independent mutable copy; never pass the detail owner's array. */
    class CopyPassword(
        val password: CharArray,
    ) : VaultIntent {
        override fun toString(): String = "CopyPassword([redacted])"
    }
}
