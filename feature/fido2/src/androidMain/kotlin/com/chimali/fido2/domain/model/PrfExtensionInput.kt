package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req

/**
 * T042 — PRF extension domain model.
 *
 * Models the `hmac-secret` / `prf` CTAP2 extension inputs and outputs
 * per WebAuthn L3 §10.1.4 and CTAP2 spec §12.4.
 *
 * Constraints:
 * - 1–2 salts allowed (MAX_SALTS = 2)
 * - Each salt must be exactly 32 bytes
 * - Each derived output is exactly 32 bytes (HMAC-SHA-256)
 *
 * @property salts Retains the supplied list and arrays without copying; callers must keep them
 * valid through derivation and erase them when their ownership ends.
 */
data class PrfExtensionInput(
    /**
     * Ordered list of 32-byte salt inputs; must contain 1 or 2 entries.
     * CTAP2 encodes these as map keys 0x01 (salt1) and 0x02 (salt2 optional).
     */
    @Req("NFR-SEC-010", "FR-VAL-020")
    val salts: List<ByteArray>,
) {
    init {
        require(salts.isNotEmpty()) {
            "PRF extension requires at least $MIN_SALTS salt; got 0"
        }
        require(salts.size <= MAX_SALTS) {
            "PRF extension supports at most $MAX_SALTS salts; got ${salts.size}"
        }
        salts.forEachIndexed { i, salt ->
            require(salt.size == SALT_SIZE_BYTES) {
                "PRF salt[$i] must be exactly $SALT_SIZE_BYTES bytes; got ${salt.size}"
            }
        }
    }

    /** Borrows the first validated 32-byte salt from [salts]; no defensive copy is made. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    val salt1: ByteArray get() = salts[0]

    /** Borrows the optional second validated 32-byte salt from [salts]; no copy is made. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    val salt2: ByteArray? get() = salts.getOrNull(1)

    companion object {
        /** Minimum number of salts accepted for a PRF input. */
        @Req("NFR-SEC-010", "FR-VAL-020")
        const val MIN_SALTS = 1

        /** Maximum number of salts representable by the CTAP2 extension. */
        @Req("NFR-SEC-010", "FR-VAL-020")
        const val MAX_SALTS = 2

        /** Required byte length for each PRF salt. */
        @Req("NFR-SEC-010", "FR-VAL-020")
        const val SALT_SIZE_BYTES = 32

        /** Required byte length for each derived PRF output. */
        @Req("NFR-SEC-010", "FR-VAL-020")
        const val MAX_OUTPUT_BYTES = 32

        /** Factory for a single-salt PRF input. */
        @Req("NFR-SEC-010", "FR-VAL-020")
        fun single(salt: ByteArray): PrfExtensionInput = PrfExtensionInput(listOf(salt))

        /** Factory for a dual-salt PRF input. */
        @Req("NFR-SEC-010", "FR-VAL-020")
        fun dual(
            salt1: ByteArray,
            salt2: ByteArray,
        ): PrfExtensionInput = PrfExtensionInput(listOf(salt1, salt2))
    }
}

/**
 * T042 — PRF extension output domain model.
 *
 * Contains 1 or 2 32-byte HMAC-SHA-256 outputs, corresponding to the
 * salts in the original [PrfExtensionInput].
 *
 * CTAP2 encodes outputs with integer keys:
 *   - key 1 → output1 (always present)
 *   - key 2 → output2 (only when 2 salts were provided)
 */
data class PrfExtensionOutput(
    /** Borrowed 32-byte output buffer, erased by [clear] and exposed directly to CTAP encoding. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    val output1: ByteArray,
    /** Optional borrowed 32-byte second output buffer, erased by [clear]. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    val output2: ByteArray? = null,
) {
    init {
        require(output1.size == PrfExtensionInput.MAX_OUTPUT_BYTES) {
            "PRF output1 must be exactly ${PrfExtensionInput.MAX_OUTPUT_BYTES} bytes; got ${output1.size}"
        }
        output2?.let { out ->
            require(out.size == PrfExtensionInput.MAX_OUTPUT_BYTES) {
                "PRF output2 must be exactly ${PrfExtensionInput.MAX_OUTPUT_BYTES} bytes; got ${out.size}"
            }
        }
    }

    /** Converts this output to a CTAP2 CBOR map with integer keys 1 and 2. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun toCborMap(): Map<Int, ByteArray> =
        buildMap {
            put(1, output1)
            output2?.let { put(2, it) }
        }

    /**
     * T043a — Explicitly zero out the output buffers after use.
     * Call this when the result is no longer needed to prevent sensitive
     * key material from lingering in memory (Constitution §I).
     */
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun clear() {
        output1.fill(0)
        output2?.fill(0)
    }

    /** Compares output bytes by content; this ordinary model equality is not constant-time. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrfExtensionOutput) return false
        if (!output1.contentEquals(other.output1)) return false
        if (output2 != null && other.output2 != null && !output2.contentEquals(other.output2)) return false
        if ((output2 == null) != (other.output2 == null)) return false
        return true
    }

    /** Returns a content hash consistent with [equals]. */
    @Req("NFR-SEC-010", "FR-VAL-020")
    override fun hashCode(): Int {
        var result = output1.contentHashCode()
        result = 31 * result + (output2?.contentHashCode() ?: 0)
        return result
    }
}
