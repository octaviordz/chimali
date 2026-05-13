package com.chimali.core.domain.eventsourcing.passkey

import kotlinx.serialization.Serializable

/**
 * Materialized state of a PasskeyCredential aggregate.
 */
@Serializable
data class PasskeyState(
    val id: String = "",
    val rpId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userDisplayName: String = "",
    val credentialId: ByteArray = byteArrayOf(),
    val publicKey: String = "",
    val aaguid: String = "",
    val signCount: Long = 0,
    val isDeleted: Boolean = false,
    val sequenceNumber: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyState) return false

        if (id != other.id) return false
        if (rpId != other.rpId) return false
        if (userId != other.userId) return false
        if (userName != other.userName) return false
        if (userDisplayName != other.userDisplayName) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (publicKey != other.publicKey) return false
        if (aaguid != other.aaguid) return false
        if (signCount != other.signCount) return false
        if (isDeleted != other.isDeleted) return false
        if (sequenceNumber != other.sequenceNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + userName.hashCode()
        result = 31 * result + userDisplayName.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + aaguid.hashCode()
        result = 31 * result + signCount.hashCode().toInt()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + sequenceNumber.hashCode().toInt()
        return result
    }
}
