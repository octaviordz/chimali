package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.domain.eventsourcing.DomainEvent
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Domain events for the PasskeyCredential aggregate.
 */
@Serializable
sealed interface PasskeyEvent : DomainEvent {
    /**
     * Emitted when a new passkey is registered.
     */
    @Serializable
    data class Registered(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
        val rpId: String,
        val userId: String,
        val userName: String,
        val userDisplayName: String,
        val credentialId: ByteArray,
        val publicKey: String,
        val aaguid: String,
        val signCount: Long,
    ) : PasskeyEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Registered) return false
            if (aggregateId != other.aggregateId) return false
            if (sequenceNumber != other.sequenceNumber) return false
            if (timestamp != other.timestamp) return false
            if (rpId != other.rpId) return false
            if (userId != other.userId) return false
            if (userName != other.userName) return false
            if (userDisplayName != other.userDisplayName) return false
            if (!credentialId.contentEquals(other.credentialId)) return false
            if (publicKey != other.publicKey) return false
            if (aaguid != other.aaguid) return false
            if (signCount != other.signCount) return false
            return true
        }

        override fun hashCode(): Int {
            var result = aggregateId.hashCode()
            result = 31 * result + sequenceNumber.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + rpId.hashCode()
            result = 31 * result + userId.hashCode()
            result = 31 * result + userName.hashCode()
            result = 31 * result + userDisplayName.hashCode()
            result = 31 * result + credentialId.contentHashCode()
            result = 31 * result + publicKey.hashCode()
            result = 31 * result + aaguid.hashCode()
            result = 31 * result + signCount.hashCode()
            return result
        }
    }

    /**
     * Emitted when a passkey is successfully used for authentication.
     */
    @Serializable
    data class Authenticated(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
        val newSignCount: Long,
    ) : PasskeyEvent

    /**
     * Emitted when a passkey is deleted.
     */
    @Serializable
    data class Deleted(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
    ) : PasskeyEvent
}
