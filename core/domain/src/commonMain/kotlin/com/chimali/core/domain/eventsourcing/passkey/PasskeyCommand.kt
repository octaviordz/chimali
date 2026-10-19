package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.common.annotation.Req

/**
 * Commands for the PasskeyCredential aggregate.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
sealed interface PasskeyCommand {
    /**
     * Request to register a new passkey.
     *
     * @property id Aggregate identity for this credential.
     * @property rpId Mutable relying-party identifier retained as supplied and overwritten by [eraseSensitiveMetadata].
     * @property userId Mutable user identifier retained as supplied and overwritten by [eraseSensitiveMetadata].
     * @property userName Mutable account name retained as supplied and overwritten by [eraseSensitiveMetadata].
     * @property userDisplayName Mutable display name retained as supplied and overwritten by [eraseSensitiveMetadata].
     * @property credentialId Credential identifier bytes retained for event creation.
     * @property publicKey Encoded registered public key.
     * @property aaguid Authenticator model identifier recorded with registration.
     * @property signCount Initial authenticator signature counter.
     * @property coseAlgorithm Persisted COSE algorithm identifier for credential reconstruction.
     * @property derivationProfile Exact local derivation profile identifier paired with the algorithm.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    data class Register(
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val id: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val rpId: CharArray,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val userId: CharArray,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val userName: CharArray,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val userDisplayName: CharArray,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val credentialId: ByteArray,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val publicKey: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val aaguid: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val signCount: Long,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val coseAlgorithm: Int,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val derivationProfile: String,
    ) : PasskeyCommand {
        /** Converts text fields to mutable arrays; the resulting command retains those arrays for cleanup. */
        constructor(
            id: String,
            rpId: String,
            userId: String,
            userName: String,
            userDisplayName: String,
            credentialId: ByteArray,
            publicKey: String,
            aaguid: String,
            signCount: Long,
            coseAlgorithm: Int,
            derivationProfile: String,
        ) : this(
            id = id,
            rpId = rpId.toCharArray(),
            userId = userId.toCharArray(),
            userName = userName.toCharArray(),
            userDisplayName = userDisplayName.toCharArray(),
            credentialId = credentialId,
            publicKey = publicKey,
            aaguid = aaguid,
            signCount = signCount,
            coseAlgorithm = coseAlgorithm,
            derivationProfile = derivationProfile,
        )

        /** Overwrites the owned mutable account metadata arrays with zero characters. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun eraseSensitiveMetadata() {
            rpId.fill('\u0000')
            userId.fill('\u0000')
            userName.fill('\u0000')
            userDisplayName.fill('\u0000')
        }

        /** Compares all command fields by value; mutable arrays use content equality. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Register) return false
            if (id != other.id) return false
            if (!rpId.contentEquals(other.rpId)) return false
            if (!userId.contentEquals(other.userId)) return false
            if (!userName.contentEquals(other.userName)) return false
            if (!userDisplayName.contentEquals(other.userDisplayName)) return false
            if (!credentialId.contentEquals(other.credentialId)) return false
            if (publicKey != other.publicKey) return false
            if (aaguid != other.aaguid) return false
            if (signCount != other.signCount) return false
            if (coseAlgorithm != other.coseAlgorithm) return false
            if (derivationProfile != other.derivationProfile) return false
            return true
        }

        /** Returns a hash consistent with [equals], including content hashes for arrays. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + rpId.contentHashCode()
            result = 31 * result + userId.contentHashCode()
            result = 31 * result + userName.contentHashCode()
            result = 31 * result + userDisplayName.contentHashCode()
            result = 31 * result + credentialId.contentHashCode()
            result = 31 * result + publicKey.hashCode()
            result = 31 * result + aaguid.hashCode()
            result = 31 * result + signCount.hashCode()
            result = 31 * result + coseAlgorithm
            result = 31 * result + derivationProfile.hashCode()
            return result
        }

        /** Returns diagnostic fields while redacting account, credential and key material. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun toString(): String =
            "Register(id=$id, rpId=[redacted], userId=[redacted], userName=[redacted], " +
                "userDisplayName=[redacted], credentialId=[redacted], publicKey=[redacted], aaguid=[redacted], " +
                "signCount=$signCount, coseAlgorithm=$coseAlgorithm, derivationProfile=$derivationProfile)"
    }

    /**
     * Request to record a successful authentication.
     *
     * @property id Aggregate identity for the authenticated credential.
     * @property newSignCount Counter value produced by the successful assertion.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    data class Authenticate(
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val id: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val newSignCount: Long,
    ) : PasskeyCommand

    /**
     * Request to delete a passkey.
     *
     * @property id Aggregate identity of the credential to delete.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    data class Delete(
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val id: String,
    ) : PasskeyCommand
}
