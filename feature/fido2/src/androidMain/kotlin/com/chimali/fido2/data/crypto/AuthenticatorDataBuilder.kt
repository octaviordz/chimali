package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single

private const val AUTH_DATA_MIN_LENGTH = 37 // rpIdHash(32) + flags(1) + counter(4)

/**
 * T092 — Builds CTAP2 authenticatorData byte arrays.
 *
 * AuthenticatorData layout (FIDO2 spec §6.1):
 * ```
 * rpIdHash   [32 bytes]   SHA-256 of the RP ID
 * flags      [ 1 byte ]   UP | RFU | UV | BE | BS | RFU | AT | ED
 * signCount  [ 4 bytes]   big-endian uint32
 * [attestedCredentialData]   present only during MakeCredential (AT flag set)
 * [extensions]               present only when ED flag set
 * ```
 *
 * Flag bits:
 * - 0x01 (UP)  User Present
 * - 0x04 (UV)  User Verified
 * - 0x08 (BE)  Backup Eligible
 * - 0x10 (BS)  Backup State
 * - 0x40 (AT)  Attested Credential Data present (MakeCredential only)
 * - 0x80 (ED)  Extension Data present
 */
@Single
class AuthenticatorDataBuilder {
    // ── Assertion (GetAssertion) ───────────────────────────────────────────────

    /**
     * Builds a 37-byte authenticatorData for GetAssertion responses.
     *
     * @param rpId         Relying Party ID — SHA-256'd into rpIdHash.
     * @param userPresent  True when UP flag should be set (CTAP§6.2: always true for HID).
     * @param userVerified True when UV flag should be set (biometric/PIN was performed).
     * @param signCount    Current sign count, incremented before calling this.
     * @param extensions   Optional CBOR-encoded extension data (not yet wired; reserved).
     */
    fun buildAssertionAuthData(
        rpId: String,
        userPresent: Boolean = true,
        userVerified: Boolean = false,
        signCount: Long,
        extensions: ByteArray? = null,
    ): ByteArray {
        Logger.d { "Building assertion authData for rpId=$rpId signCount=$signCount" }
        val rpIdHash = rpIdHash(rpId)
        val flags =
            assembleFlags(
                up = userPresent,
                uv = userVerified,
                at = false,
                ed = extensions != null,
            )
        return rpIdHash + byteArrayOf(flags) + encodeCounter(signCount) +
            (extensions ?: ByteArray(0))
    }

    // ── Attestation (MakeCredential) ──────────────────────────────────────────

    /**
     * Builds authenticatorData for MakeCredential responses.
     * Includes attestedCredentialData (credentialId + COSE public key).
     *
     * @param rpId          Relying Party ID.
     * @param userPresent   UP flag.
     * @param userVerified  UV flag.
     * @param signCount     Starts at 0 for new credentials.
     * @param aaguid        16-byte Authenticator Attestation GUID.
     * @param credentialId  The credential ID bytes.
     * @param cosePublicKey CBOR-encoded COSE public key.
     * @param extensions    Optional CBOR extension data.
     */
    fun buildAttestationAuthData(
        rpId: String,
        userPresent: Boolean = true,
        userVerified: Boolean = false,
        signCount: Long = 0L,
        aaguid: ByteArray,
        credentialId: ByteArray,
        cosePublicKey: ByteArray,
        extensions: ByteArray? = null,
    ): ByteArray {
        Logger.d { "Building attestation authData for rpId=$rpId credLen=${credentialId.size}" }
        require(aaguid.size == AAGUID_SIZE) { "AAGUID must be $AAGUID_SIZE bytes" }

        val rpIdHash = rpIdHash(rpId)
        val flags = assembleFlags(up = userPresent, uv = userVerified, at = true, ed = extensions != null)
        val attestedCredData = buildAttestedCredentialData(aaguid, credentialId, cosePublicKey)

        return rpIdHash + byteArrayOf(flags) + encodeCounter(signCount) +
            attestedCredData + (extensions ?: ByteArray(0))
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun rpIdHash(rpId: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8))

    private fun assembleFlags(
        up: Boolean,
        uv: Boolean,
        at: Boolean,
        ed: Boolean,
    ): Byte {
        var flags = 0
        if (up) flags = flags or FLAG_UP
        if (uv) flags = flags or FLAG_UV
        if (at) flags = flags or FLAG_AT
        if (ed) flags = flags or FLAG_ED
        return flags.toByte()
    }

    private fun encodeCounter(signCount: Long): ByteArray =
        byteArrayOf(
            ((signCount shr SHIFT_24) and MASK_BYTE_LONG).toByte(),
            ((signCount shr SHIFT_16) and MASK_BYTE_LONG).toByte(),
            ((signCount shr SHIFT_8) and MASK_BYTE_LONG).toByte(),
            (signCount and MASK_BYTE_LONG).toByte(),
        )

    /**
     * Builds the attestedCredentialData section per FIDO2 §6.5.2:
     * aaguid (16) | credentialIdLength (2, BE) | credentialId (n) | credentialPublicKey (CBOR)
     */
    private fun buildAttestedCredentialData(
        aaguid: ByteArray,
        credentialId: ByteArray,
        cosePublicKey: ByteArray,
    ): ByteArray {
        val credIdLen =
            byteArrayOf(
                ((credentialId.size shr SHIFT_8) and MASK_BYTE).toByte(),
                (credentialId.size and MASK_BYTE).toByte(),
            )
        return aaguid + credIdLen + credentialId + cosePublicKey
    }

    companion object {
        private const val FLAG_UP = 0x01
        private const val FLAG_UV = 0x04
        private const val FLAG_AT = 0x40
        private const val FLAG_ED = 0x80

        private const val MASK_BYTE = 0xFF
        private const val MASK_BYTE_LONG = 0xFFL
        private const val SHIFT_24 = 24
        private const val SHIFT_16 = 16
        private const val SHIFT_8 = 8

        private const val AAGUID_SIZE = 16

        fun minimumAuthDataLength() = AUTH_DATA_MIN_LENGTH
    }
}
