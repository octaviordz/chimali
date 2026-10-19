package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.vault.SensitiveCharArraySerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Domain events for the PasskeyCredential aggregate.
 */
@Serializable
@Req("NFR-SEC-041", "TR-AUTH-031")
sealed interface PasskeyEvent : DomainEvent {
    /**
     * Emitted when a new passkey is registered.
     *
     * @property aggregateId Credential aggregate identity.
     * @property sequenceNumber Position of this event in the aggregate stream.
     * @property timestamp Time at which the event was created.
     * @property rpId Mutable relying-party identifier retained by the event and erased by [eraseSensitiveMetadata].
     * @property userId Mutable user identifier retained by the event and erased by [eraseSensitiveMetadata].
     * @property userName Mutable account name retained by the event and erased by [eraseSensitiveMetadata].
     * @property userDisplayName Mutable display name retained by the event and erased by [eraseSensitiveMetadata].
     * @property credentialId Credential identifier bytes recorded for reconstruction.
     * @property publicKey Encoded registered public key.
     * @property aaguid Authenticator model identifier recorded with registration.
     * @property signCount Initial authenticator signature counter.
     * @property coseAlgorithm Persisted COSE algorithm identifier for credential reconstruction.
     * @property derivationProfile Exact local derivation profile identifier paired with the algorithm.
     */
    @Serializable
    @Req("NFR-SEC-041", "TR-AUTH-031")
    data class Registered(
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val aggregateId: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val sequenceNumber: Long,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val timestamp: Instant,
        @Serializable(with = SensitiveCharArraySerializer::class)
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val rpId: CharArray,
        @Serializable(with = SensitiveCharArraySerializer::class)
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val userId: CharArray,
        @Serializable(with = SensitiveCharArraySerializer::class)
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val userName: CharArray,
        @Serializable(with = SensitiveCharArraySerializer::class)
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
        val coseAlgorithm: Int = 0,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val derivationProfile: String = "",
    ) : PasskeyEvent {
        /** Converts text fields to mutable arrays retained by this event for later cleanup. */
        constructor(
            aggregateId: String,
            sequenceNumber: Long,
            timestamp: Instant,
            rpId: String,
            userId: String,
            userName: String,
            userDisplayName: String,
            credentialId: ByteArray,
            publicKey: String,
            aaguid: String,
            signCount: Long,
            coseAlgorithm: Int = 0,
            derivationProfile: String = "",
        ) : this(
            aggregateId = aggregateId,
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
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

        /** Overwrites the event's owned mutable account metadata arrays with zero characters. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun eraseSensitiveMetadata() {
            rpId.fill('\u0000')
            userId.fill('\u0000')
            userName.fill('\u0000')
            userDisplayName.fill('\u0000')
        }

        /** Compares event fields by value; mutable arrays use content equality. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Registered) return false
            if (aggregateId != other.aggregateId) return false
            if (sequenceNumber != other.sequenceNumber) return false
            if (timestamp != other.timestamp) return false
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
            var result = aggregateId.hashCode()
            result = 31 * result + sequenceNumber.hashCode()
            result = 31 * result + timestamp.hashCode()
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
            "Registered(aggregateId=$aggregateId, sequenceNumber=$sequenceNumber, timestamp=$timestamp, " +
                "rpId=[redacted], userId=[redacted], userName=[redacted], userDisplayName=[redacted], " +
                "credentialId=[redacted], publicKey=[redacted], aaguid=[redacted], signCount=$signCount, " +
                "coseAlgorithm=$coseAlgorithm, derivationProfile=$derivationProfile)"
    }

    /**
     * Emitted when a passkey is successfully used for authentication.
     *
     * @property aggregateId Credential aggregate identity.
     * @property sequenceNumber Position of this event in the aggregate stream.
     * @property timestamp Time at which the event was created.
     * @property newSignCount Counter value produced by the successful assertion.
     */
    @Serializable
    @Req("NFR-SEC-041", "TR-AUTH-031")
    data class Authenticated(
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val aggregateId: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val sequenceNumber: Long,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val timestamp: Instant,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        val newSignCount: Long,
    ) : PasskeyEvent

    /**
     * Emitted when a passkey is deleted.
     *
     * @property aggregateId Credential aggregate identity.
     * @property sequenceNumber Position of this event in the aggregate stream.
     * @property timestamp Time at which the event was created.
     */
    @Serializable
    @Req("NFR-SEC-041", "TR-AUTH-031")
    data class Deleted(
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val aggregateId: String,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val sequenceNumber: Long,
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val timestamp: Instant,
    ) : PasskeyEvent
}
