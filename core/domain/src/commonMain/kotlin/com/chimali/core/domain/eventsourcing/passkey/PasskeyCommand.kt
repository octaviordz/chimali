package com.chimali.core.domain.eventsourcing.passkey

/**
 * Commands for the PasskeyCredential aggregate.
 */
sealed interface PasskeyCommand {
    /**
     * Request to register a new passkey.
     */
    data class Register(
        val id: String,
        val rpId: String,
        val userId: String,
        val userName: String,
        val userDisplayName: String,
        val credentialId: ByteArray,
        val publicKey: String,
        val aaguid: String,
        val signCount: Long,
    ) : PasskeyCommand {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Register) return false
            if (id != other.id) return false
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
            var result = id.hashCode()
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
     * Request to record a successful authentication.
     */
    data class Authenticate(
        val id: String,
        val newSignCount: Long,
    ) : PasskeyCommand

    /**
     * Request to delete a passkey.
     */
    data class Delete(
        val id: String,
    ) : PasskeyCommand
}
