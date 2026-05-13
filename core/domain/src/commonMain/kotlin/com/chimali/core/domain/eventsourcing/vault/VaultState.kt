package com.chimali.core.domain.eventsourcing.vault

import kotlinx.serialization.Serializable

/**
 * Materialized state of a VaultEntry aggregate.
 */
@Serializable
data class VaultState(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val payload: ByteArray = byteArrayOf(),
    val identityId: String = "",
    val isDeleted: Boolean = false,
    val sequenceNumber: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultState) return false

        if (id != other.id) return false
        if (type != other.type) return false
        if (title != other.title) return false
        if (!payload.contentEquals(other.payload)) return false
        if (identityId != other.identityId) return false
        if (isDeleted != other.isDeleted) return false
        if (sequenceNumber != other.sequenceNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + identityId.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + sequenceNumber.hashCode().toInt()
        return result
    }
}
