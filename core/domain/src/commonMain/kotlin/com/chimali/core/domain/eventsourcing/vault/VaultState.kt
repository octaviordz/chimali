package com.chimali.core.domain.eventsourcing.vault

import kotlinx.serialization.Serializable

/**
 * Materialized state of a VaultEntry aggregate.
 */
@Serializable
data class VaultState(
    val id: String = "",
    val type: String = "",
    @Serializable(with = SensitiveCharArraySerializer::class)
    val title: CharArray = charArrayOf(),
    val payload: ByteArray = byteArrayOf(),
    val identityId: String = "",
    val isDeleted: Boolean = false,
    val sequenceNumber: Long = 0,
) {
    constructor(
        id: String = "",
        type: String = "",
        title: String,
        payload: ByteArray = byteArrayOf(),
        identityId: String = "",
        isDeleted: Boolean = false,
        sequenceNumber: Long = 0,
    ) : this(id, type, title.toCharArray(), payload, identityId, isDeleted, sequenceNumber)

    fun clearSensitiveMemory() {
        title.fill('\u0000')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultState) return false

        if (id != other.id) return false
        if (type != other.type) return false
        if (!title.contentEquals(other.title)) return false
        if (!payload.contentEquals(other.payload)) return false
        if (identityId != other.identityId) return false
        if (isDeleted != other.isDeleted) return false
        if (sequenceNumber != other.sequenceNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + title.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + identityId.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + sequenceNumber.hashCode().toInt()
        return result
    }
}
