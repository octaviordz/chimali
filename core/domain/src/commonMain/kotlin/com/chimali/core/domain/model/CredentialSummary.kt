package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lightweight credential projection used exclusively for the **candidate selection**
 * phase of a `GetAssertion` ceremony.
 */
@Serializable
@Req("NFR-SEC-041", "TR-AUTH-031")
class CredentialSummary private constructor(
    val id: String,
    @SerialName("rpId") private val rpIdOwner: SensitiveText,
    val credentialId: CredentialId,
    val lastUsedAt: Instant,
    val coseAlgorithm: Int,
    val derivationProfile: String,
    val storedPublicKeyEncoding: ByteArray,
    val credProtectPolicy: Int = 1,
) {
    /** Copies a compatibility RP identifier into this summary's independent owner. */
    constructor(
        id: String,
        rpId: RpId,
        credentialId: CredentialId,
        lastUsedAt: Instant,
        coseAlgorithm: Int,
        derivationProfile: String,
        storedPublicKeyEncoding: ByteArray,
        credProtectPolicy: Int = 1,
    ) : this(
        id,
        SensitiveText.copyOf(rpId.value),
        credentialId,
        lastUsedAt,
        coseAlgorithm,
        derivationProfile,
        storedPublicKeyEncoding,
        credProtectPolicy,
    )

    /** Compatibility/display RP value; selection code uses public identity and borrowed rpIdText. */
    val rpId: RpId get() = RpId(rpIdOwner.toBoundaryString())

    /** Borrows the RP association until this result batch is released. */
    val rpIdText: SensitiveText get() = rpIdOwner

    init {
        try {
            CredentialProtectionPolicy.requireSupported(credProtectPolicy)
        } catch (failure: IllegalArgumentException) {
            rpIdOwner.erase()
            throw failure
        }
    }

    /** Releases the summary's RP association without altering public credential identity or key bytes. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() = rpIdOwner.erase()

    /** Deep-copies sensitive metadata so a new result batch can have a separate lifetime. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        id: String = this.id,
        rpId: RpId? = null,
        credentialId: CredentialId = this.credentialId,
        lastUsedAt: Instant = this.lastUsedAt,
        coseAlgorithm: Int = this.coseAlgorithm,
        derivationProfile: String = this.derivationProfile,
        storedPublicKeyEncoding: ByteArray = this.storedPublicKeyEncoding,
        credProtectPolicy: Int = this.credProtectPolicy,
    ): CredentialSummary =
        CredentialSummary(
            id,
            SensitiveText.copyOf(rpId?.value ?: rpIdOwner),
            credentialId,
            lastUsedAt,
            coseAlgorithm,
            derivationProfile,
            storedPublicKeyEncoding,
            credProtectPolicy,
        )

    /** Compares summary identity and mutable metadata by value. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        other is CredentialSummary &&
            id == other.id &&
            rpIdOwner == other.rpIdOwner &&
            credentialId == other.credentialId &&
            lastUsedAt == other.lastUsedAt &&
            coseAlgorithm == other.coseAlgorithm &&
            derivationProfile == other.derivationProfile &&
            storedPublicKeyEncoding.contentEquals(other.storedPublicKeyEncoding) &&
            credProtectPolicy == other.credProtectPolicy

    /** Hashes fields used by equality without creating an RP String. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int =
        listOf(
            id,
            rpIdOwner,
            credentialId,
            lastUsedAt,
            coseAlgorithm,
            derivationProfile,
            storedPublicKeyEncoding.contentHashCode(),
            credProtectPolicy,
        ).hashCode()

    /** Redacts sensitive association metadata from result diagnostics. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "CredentialSummary([sensitive metadata redacted])"

    companion object {
        /** Copies borrowed RP text into a caller-owned result; no key derivation is performed. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromText(
            id: String,
            rpId: CharSequence,
            credentialId: CredentialId,
            lastUsedAt: Instant,
            coseAlgorithm: Int,
            derivationProfile: String,
            storedPublicKeyEncoding: ByteArray,
            credProtectPolicy: Int = 1,
        ): CredentialSummary =
            CredentialSummary(
                id,
                SensitiveText.copyOf(rpId),
                credentialId,
                lastUsedAt,
                coseAlgorithm,
                derivationProfile,
                storedPublicKeyEncoding,
                credProtectPolicy,
            )
    }
}
