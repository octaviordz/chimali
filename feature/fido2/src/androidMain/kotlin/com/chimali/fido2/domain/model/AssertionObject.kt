package com.chimali.fido2.domain.model

import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import kotlinx.datetime.Instant

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
    val timestamp: Instant = TimeProvider().now(),
    /** ID of the credential that was used. Convenience field derived from [credential]. */
    val credentialId: String = credential?.getIdBase64Url() ?: "",
) {
    companion object {
        private const val MIN_AUTH_DATA_SIZE = 37
        private const val FLAGS_OFFSET = 32
        private const val SIGN_COUNT_OFFSET = 33
        private const val SIGN_COUNT_BYTE_0_SHIFT = 24
        private const val SIGN_COUNT_BYTE_1_SHIFT = 16
        private const val SIGN_COUNT_BYTE_2_SHIFT = 8
        private const val BYTE_MASK = 0xFF

        private const val FLAG_UP_MASK = 0x01
        private const val FLAG_UV_MASK = 0x04
        private const val TEST_FLAGS_UP_UV = 0x05
        private const val TEST_SIG_SIZE = 64

        private const val SIGN_COUNT_BYTE_OFFSET_1 = 1
        private const val SIGN_COUNT_BYTE_OFFSET_2 = 2
        private const val SIGN_COUNT_BYTE_OFFSET_3 = 3

        /**
         * Creates a minimal AssertionObject for testing without real crypto.
         */
        fun createTest(
            credentialId: CredentialId,
            rpId: RpId,
        ): AssertionObject {
            val rpIdHash =
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(rpId.value.toByteArray())
            val flags = byteArrayOf(TEST_FLAGS_UP_UV.toByte()) // UP | UV
            val counter = byteArrayOf(0, 0, 0, 1)
            val authData = rpIdHash + flags + counter // 37 bytes
            return AssertionObject(
                credential = PublicKeyCredentialDescriptor.create(id = credentialId),
                authData = authData,
                signature = ByteArray(TEST_SIG_SIZE) { it.toByte() },
                user = null,
            )
        }
    }

    init {
        require(authData.size >= MIN_AUTH_DATA_SIZE) {
            "authData must be at least 37 bytes (rpIdHash+flags+counter), got ${authData.size}"
        }
        require(signature.isNotEmpty()) { "signature cannot be empty" }
    }

    /** Extracts the sign-count from bytes 33–36 (big-endian uint32) of authData. */
    @Suppress("unused")
    fun extractSignCount(): Long {
        if (authData.size < MIN_AUTH_DATA_SIZE) return 0L
        return ((authData[SIGN_COUNT_OFFSET].toLong() and BYTE_MASK.toLong()) shl SIGN_COUNT_BYTE_0_SHIFT) or
            (
                (
                    authData[SIGN_COUNT_OFFSET + SIGN_COUNT_BYTE_OFFSET_1].toLong() and
                        BYTE_MASK.toLong()
                ) shl SIGN_COUNT_BYTE_1_SHIFT
            ) or
            (
                (
                    authData[SIGN_COUNT_OFFSET + SIGN_COUNT_BYTE_OFFSET_2].toLong() and
                        BYTE_MASK.toLong()
                ) shl SIGN_COUNT_BYTE_2_SHIFT
            ) or
            (
                (
                    authData[SIGN_COUNT_OFFSET + SIGN_COUNT_BYTE_OFFSET_3].toLong() and
                        BYTE_MASK.toLong()
                )
            )
    }

    /** Returns true if the UP (user present) flag is set in authData byte 32. */
    @Suppress("unused")
    fun isUserPresent(): Boolean =
        authData.size > FLAGS_OFFSET && (authData[FLAGS_OFFSET].toInt() and FLAG_UP_MASK) != 0

    /** Returns true if the UV (user verified) flag is set in authData byte 32. */
    @Suppress("unused")
    fun isUserVerified(): Boolean =
        authData.size > FLAGS_OFFSET && (authData[FLAGS_OFFSET].toInt() and FLAG_UV_MASK) != 0

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
}
