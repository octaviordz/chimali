package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.vault.SensitiveCharArraySerializer
import kotlinx.serialization.Serializable

/**
 * Materialized state of a PasskeyCredential aggregate.
 *
 * @property id Credential aggregate identity.
 * @property rpId Mutable relying-party identifier retained by this state and erased by [eraseSensitiveMetadata].
 * @property userId Mutable user identifier retained by this state and erased by [eraseSensitiveMetadata].
 * @property userName Mutable account name retained by this state and erased by [eraseSensitiveMetadata].
 * @property userDisplayName Mutable display name retained by this state and erased by [eraseSensitiveMetadata].
 * @property credentialId Credential identifier bytes used to reconstruct the registered key.
 * @property publicKey Encoded registered public key.
 * @property aaguid Authenticator model identifier recorded with registration.
 * @property signCount Latest authenticator signature counter.
 * @property isDeleted Whether a deletion event has been applied.
 * @property sequenceNumber Position of the latest applied event.
 * @property coseAlgorithm Persisted COSE algorithm identifier for credential reconstruction.
 * @property derivationProfile Exact local derivation profile identifier paired with the algorithm.
 */
@Serializable
@Req("NFR-SEC-041", "TR-AUTH-031")
data class PasskeyState(
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val id: String = "",
    @Serializable(with = SensitiveCharArraySerializer::class)
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val rpId: CharArray = charArrayOf(),
    @Serializable(with = SensitiveCharArraySerializer::class)
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val userId: CharArray = charArrayOf(),
    @Serializable(with = SensitiveCharArraySerializer::class)
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val userName: CharArray = charArrayOf(),
    @Serializable(with = SensitiveCharArraySerializer::class)
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val userDisplayName: CharArray = charArrayOf(),
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val credentialId: ByteArray = byteArrayOf(),
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val publicKey: String = "",
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val aaguid: String = "",
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val signCount: Long = 0,
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val isDeleted: Boolean = false,
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val sequenceNumber: Long = 0,
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val coseAlgorithm: Int = 0,
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val derivationProfile: String = "",
) {
    /** Overwrites the state's owned mutable account metadata arrays with zero characters. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveMetadata() {
        rpId.fill('\u0000')
        userId.fill('\u0000')
        userName.fill('\u0000')
        userDisplayName.fill('\u0000')
    }

    /** Compares state fields by value; mutable arrays use content equality. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyState) return false

        if (id != other.id) return false
        if (!rpId.contentEquals(other.rpId)) return false
        if (!userId.contentEquals(other.userId)) return false
        if (!userName.contentEquals(other.userName)) return false
        if (!userDisplayName.contentEquals(other.userDisplayName)) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (publicKey != other.publicKey) return false
        if (aaguid != other.aaguid) return false
        if (signCount != other.signCount) return false
        if (isDeleted != other.isDeleted) return false
        if (sequenceNumber != other.sequenceNumber) return false
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
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + coseAlgorithm
        result = 31 * result + derivationProfile.hashCode()
        return result
    }

    /** Returns a diagnostic summary that redacts relying-party, account, credential and public-key fields. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String =
        "PasskeyState(id=$id, rpId=[redacted], userId=[redacted], userName=[redacted], " +
            "userDisplayName=[redacted], credentialId=[redacted], publicKey=[redacted], aaguid=[redacted], " +
            "signCount=$signCount, isDeleted=$isDeleted, sequenceNumber=$sequenceNumber, " +
            "coseAlgorithm=$coseAlgorithm, derivationProfile=$derivationProfile)"
}
