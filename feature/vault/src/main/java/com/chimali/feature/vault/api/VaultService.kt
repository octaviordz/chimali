package com.chimali.feature.vault.api

import java.util.UUID

enum class VaultType {
    PASSWORD,
    CREDIT_CARD,
    NOTE
}

data class VaultItem(
    val id: UUID,
    val type: VaultType,
    val title: String,
    val payload: ByteArray,
    val crdtState: ByteArray,
    val dateCreated: String,
    val dateModified: String,
    val lastBackedUpAt: String?,
    val identityId: UUID
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultItem

        if (id != other.id) return false
        if (type != other.type) return false
        if (title != other.title) return false
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
        result = 31 * result + title.hashCode()
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
    suspend fun getItems(labelId: UUID?): List<VaultItem>
    suspend fun saveItem(item: VaultItem)
    suspend fun deleteItem(id: UUID)
}
