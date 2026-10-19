package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 5869 HKDF-SHA256 and the byte labels for Chimali's local profile. */
@Req("NFR-SEC-041", "TR-AUTH-031")
object HkdfSha256 {
    /** Output length, in bytes, of SHA-256 and HKDF-SHA256 pseudorandom keys. */
    const val HASH_LENGTH: Int = 32
    private const val MAX_HKDF_BLOCK_COUNT = 255
    private const val HKDF_COUNTER_LENGTH = 1
    private const val FIRST_HKDF_BLOCK_COUNTER = 1
    private const val MAX_OUTPUT_LENGTH = MAX_HKDF_BLOCK_COUNT * HASH_LENGTH
    private const val BIP39_SEED_LENGTH = 64
    private const val CREDENTIAL_ID_MIN_LENGTH = 16
    private const val CREDENTIAL_ID_MAX_LENGTH = 1023
    private const val CREDENTIAL_INFO_SEPARATOR = 0
    private const val CREDENTIAL_INFO_SEPARATOR_LENGTH = 1
    private const val CREDENTIAL_ID_LENGTH_PREFIX_BYTES = 2
    private const val CREDENTIAL_INFO_FRAMING_LENGTH =
        CREDENTIAL_INFO_SEPARATOR_LENGTH + CREDENTIAL_ID_LENGTH_PREFIX_BYTES
    private const val ASCII_MAX_VALUE = 0x7f

    /** Fixed ASCII root salt label separating Chimali's local derivation profile. */
    const val ROOT_SALT_LABEL = "chimali-local-key-derivation-v1"

    /** Fixed ASCII HKDF info label for deriving the P-256 parent input. */
    const val P256_PARENT_LABEL = "chimali-local-hdk-p256-v1/parent"

    /** Fixed ASCII HKDF info label for P-256 credential-secret derivation. */
    const val P256_CREDENTIAL_LABEL = "chimali-local-hdk-p256-v1/credential-secret"

    /** Fixed ASCII HKDF info label for Ed25519 credential-seed derivation. */
    const val ED25519_CREDENTIAL_LABEL = "chimali-local-ed25519-v1/credential-seed"

    /** Fixed ASCII HKDF info label for ML-DSA-65 credential-seed derivation. */
    const val MLDSA65_CREDENTIAL_LABEL = "chimali-local-mldsa65-v1/credential-seed"

    /** Extracts an owned 32-byte PRK. [salt] and [ikm] are borrowed and remain unchanged. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun extract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        return try {
            hmacSha256(effectiveSalt, ikm)
        } finally {
            if (effectiveSalt !== salt) effectiveSalt.fill(0)
        }
    }

    /** Expands a borrowed PRK and info to a new output buffer, per RFC 5869 §2.3. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun expand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(prk.size == HASH_LENGTH) { "HKDF-SHA256 PRK must be 32 bytes" }
        require(length in 0..MAX_OUTPUT_LENGTH) { "HKDF output length must be in 0..8160 bytes" }

        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var completed = false
        try {
            var counter = FIRST_HKDF_BLOCK_COUNTER
            while (offset < length) {
                val blockInput = ByteArray(previous.size + info.size + HKDF_COUNTER_LENGTH)
                try {
                    previous.copyInto(blockInput)
                    info.copyInto(blockInput, previous.size)
                    blockInput[blockInput.lastIndex] = counter.toByte()
                    val next = hmacSha256(prk, blockInput)
                    previous.fill(0)
                    previous = next
                } finally {
                    blockInput.fill(0)
                }
                val copyLength = minOf(HASH_LENGTH, length - offset)
                previous.copyInto(output, offset, 0, copyLength)
                offset += copyLength
                counter++
            }
            completed = true
            return output
        } finally {
            if (!completed) output.fill(0)
            previous.fill(0)
        }
    }

    /** Encodes the named, fixed public root salt. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun rootSalt(): ByteArray = ROOT_SALT_LABEL.toByteArray(Charsets.US_ASCII)

    /** Extracts the wallet PRK from the complete 64-byte BIP39 seed [seed]. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deriveWalletPrk(seed: ByteArray): ByteArray {
        require(seed.size == BIP39_SEED_LENGTH) { "BIP39 seed must be the complete 64-byte value" }
        val salt = rootSalt()
        return try {
            extract(salt, seed)
        } finally {
            salt.fill(0)
        }
    }

    /** Encodes the named RFC 9180 P-256 parent expansion label. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun p256ParentInfo(): ByteArray = P256_PARENT_LABEL.toByteArray(Charsets.US_ASCII)

    /** Expands the deterministic RFC 9180 P-256 parent input from a borrowed wallet PRK. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deriveP256ParentInput(prk: ByteArray): ByteArray {
        val info = p256ParentInfo()
        return try {
            expand(prk, info, HASH_LENGTH)
        } finally {
            info.fill(0)
        }
    }

    /** Derives one algorithm-specific credential seed from the full decoded identifier. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deriveCredentialSeed(
        prk: ByteArray,
        label: String,
        credentialId: ByteArray,
    ): ByteArray {
        val info = credentialInfo(label, credentialId)
        return try {
            expand(prk, info, HASH_LENGTH)
        } finally {
            info.fill(0)
        }
    }

    /**
     * Builds `ASCII(label) || 0x00 || I2OSP(length(credentialId), 2) || credentialId`.
     * The credential ID is borrowed; the returned framing buffer is owned by the caller.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun credentialInfo(
        label: String,
        credentialId: ByteArray,
    ): ByteArray {
        require(credentialId.size in CREDENTIAL_ID_MIN_LENGTH..CREDENTIAL_ID_MAX_LENGTH) {
            "Credential ID must be 16..1023 bytes"
        }
        require(label.isNotEmpty() && label.all { it.code <= ASCII_MAX_VALUE }) {
            "HKDF label must be non-empty ASCII"
        }
        val labelBytes = label.toByteArray(Charsets.US_ASCII)
        val result = ByteArray(labelBytes.size + CREDENTIAL_INFO_FRAMING_LENGTH + credentialId.size)
        labelBytes.copyInto(result)
        var offset = labelBytes.size
        result[offset++] = CREDENTIAL_INFO_SEPARATOR.toByte()
        result[offset++] = (credentialId.size ushr 8).toByte()
        result[offset++] = credentialId.size.toByte()
        credentialId.copyInto(result, offset)
        labelBytes.fill(0)
        return result
    }

    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
