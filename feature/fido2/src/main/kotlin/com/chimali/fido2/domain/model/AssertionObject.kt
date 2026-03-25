package com.chimali.fido2.domain.model

import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import java.time.Instant

/**
 * T079 — Domain model representing the output of a FIDO2 GetAssertion ceremony.
 *
 * Maps to CTAP2 `authenticatorGetAssertion` response fields (§6.2 table).
 * Returned from [GetAssertionUseCase] and serialised by the CTAP2 response builder
 * before being sent over the BLE HID transport to the host.
 */
data class AssertionObject(
    /**
     * CTAP2 response 0x01 — credential descriptor identifying which credential was used.
     * May be omitted if only one credential was in the allowCredentials list.
     */
    val credential: PublicKeyCredentialDescriptor?,

    /**
     * CTAP2 response 0x02 — serialised authenticatorData.
     * Contains: rpIdHash (32) | flags (1) | signCount (4) | [extensions-CBOR].
     */
    val authData: ByteArray,

    /**
     * CTAP2 response 0x03 — DER-encoded ECDSA P-256 signature over (authData || clientDataHash).
     */
    val signature: ByteArray,

    /**
     * CTAP2 response 0x04 — user entity (optional; present for discoverable credentials).
     */
    val user: PublicKeyCredentialUserEntity?,

    /**
     * CTAP2 response 0x05 — number of credentials in the allowCredentials list
     * that matched this RP. Present only when > 1 match found.
     */
    val numberOfCredentials: Int? = null,

    /** Timestamp of when this assertion was produced. */
    val timestamp: Instant = Instant.now(),

    /** ID of the credential that was used. Convenience field derived from [credential]. */
    val credentialId: String = credential?.getIdBase64Url() ?: ""
) {
    init {
        require(authData.size >= 37) {
            "authData must be at least 37 bytes (rpIdHash+flags+counter), got ${authData.size}"
        }
        require(signature.isNotEmpty()) { "signature cannot be empty" }
    }

    /** Extracts the sign-count from bytes 33–36 (big-endian uint32) of authData. */
    fun extractSignCount(): Long {
        if (authData.size < 37) return 0L
        return ((authData[33].toLong() and 0xFF) shl 24) or
               ((authData[34].toLong() and 0xFF) shl 16) or
               ((authData[35].toLong() and 0xFF) shl 8)  or
               (authData[36].toLong()  and 0xFF)
    }

    /** Returns true if the UP (user present) flag is set in authData byte 32. */
    fun isUserPresent(): Boolean = authData.size > 32 && (authData[32].toInt() and 0x01) != 0

    /** Returns true if the UV (user verified) flag is set in authData byte 32. */
    fun isUserVerified(): Boolean = authData.size > 32 && (authData[32].toInt() and 0x04) != 0

    // ByteArray equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssertionObject) return false
        return authData.contentEquals(other.authData) &&
               signature.contentEquals(other.signature) &&
               credential == other.credential &&
               user == other.user
    }

    override fun hashCode(): Int {
        var result = authData.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + (credential?.hashCode() ?: 0)
        result = 31 * result + (user?.hashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Creates a minimal AssertionObject for testing without real crypto.
         */
        fun createTest(
            credentialId: String,
            rpId: String
        ): AssertionObject {
            val rpIdHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(rpId.toByteArray())
            val flags = byteArrayOf(0x05.toByte()) // UP | UV
            val counter = byteArrayOf(0, 0, 0, 1)
            val authData = rpIdHash + flags + counter // 37 bytes
            return AssertionObject(
                credential = PublicKeyCredentialDescriptor.create(id = credentialId.toByteArray()),
                authData   = authData,
                signature  = ByteArray(64) { it.toByte() },
                user       = null
            )
        }
    }
}
