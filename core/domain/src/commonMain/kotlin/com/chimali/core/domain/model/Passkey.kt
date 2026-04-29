package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.PasskeyId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Passkey(
    val id: PasskeyId,
    val relyingParty: String,
    val username: String,
    val credentialId: ByteArray,
    val publicKey: ByteArray,
    val signatureCounter: Long,
    val createdAt: Instant,
    val lastUsed: Instant?,
) : DomainModel {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Passkey

        if (id != other.id) return false
        if (relyingParty != other.relyingParty) return false
        if (username != other.username) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (signatureCounter != other.signatureCounter) return false
        if (createdAt != other.createdAt) return false
        if (lastUsed != other.lastUsed) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + relyingParty.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + signatureCounter.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastUsed?.hashCode() ?: 0)
        return result
    }
}
