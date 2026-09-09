package com.chimali.feature.vault.api

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID

enum class VaultType {
    PASSWORD,
    CREDIT_CARD,
    NOTE,
}

data class VaultItem(
    val id: UUID,
    val type: VaultType,
    val title: CharArray,
    val payload: ByteArray,
    val crdtState: ByteArray,
    val dateCreated: String,
    val dateModified: String,
    val lastBackedUpAt: String?,
    val identityId: UUID,
) {
    /** Compatibility input adapter; the item never retains the supplied String. */
    constructor(
        id: UUID,
        type: VaultType,
        title: String,
        payload: ByteArray,
        crdtState: ByteArray,
        dateCreated: String,
        dateModified: String,
        lastBackedUpAt: String?,
        identityId: UUID,
    ) : this(
        id = id,
        type = type,
        title = title.toCharArray(),
        payload = payload,
        crdtState = crdtState,
        dateCreated = dateCreated,
        dateModified = dateModified,
        lastBackedUpAt = lastBackedUpAt,
        identityId = identityId,
    )

    fun clearMemory() {
        title.fill('\u0000')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultItem

        if (id != other.id) return false
        if (type != other.type) return false
        if (!title.contentEquals(other.title)) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!crdtState.contentEquals(other.crdtState)) return false
        if (dateCreated != other.dateCreated) return false
        if (dateModified != other.dateModified) return false
        if (lastBackedUpAt != other.lastBackedUpAt) return false
        if (identityId != other.identityId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + title.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + crdtState.contentHashCode()
        result = 31 * result + dateCreated.hashCode()
        result = 31 * result + dateModified.hashCode()
        result = 31 * result + (lastBackedUpAt?.hashCode() ?: 0)
        result = 31 * result + identityId.hashCode()
        return result
    }
}

interface VaultService {
    suspend fun getItems(labelId: UUID?): Outcome<List<VaultItem>, DomainError>

    suspend fun saveItem(item: VaultItem): Outcome<Unit, DomainError>

    suspend fun setItemLabels(
        itemId: UUID,
        labelIds: List<UUID>,
    ): Outcome<Unit, DomainError>

    suspend fun getItemLabelIds(itemId: UUID): Outcome<List<UUID>, DomainError>

    suspend fun deleteItem(id: UUID): Outcome<Unit, DomainError>

    suspend fun getLabels(): Outcome<List<LabelUiModel>, DomainError>

    suspend fun createLabel(
        name: String,
        colorHex: String,
    ): Outcome<LabelUiModel, DomainError>

    suspend fun deleteLabel(id: UUID): Outcome<Unit, DomainError>
}
