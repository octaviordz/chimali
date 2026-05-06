package com.chimali.fido2.domain.model

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
 */
data class PrfExtensionInput(
    /**
     * Ordered list of 32-byte salt inputs; must contain 1 or 2 entries.
     * CTAP2 encodes these as map keys 0x01 (salt1) and 0x02 (salt2 optional).
     */
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

    val salt1: ByteArray get() = salts[0]
    val salt2: ByteArray? get() = salts.getOrNull(1)

    companion object {
        const val MIN_SALTS = 1
        const val MAX_SALTS = 2
        const val SALT_SIZE_BYTES = 32
        const val MAX_OUTPUT_BYTES = 32

        /** Factory for a single-salt PRF input. */
        fun single(salt: ByteArray): PrfExtensionInput = PrfExtensionInput(listOf(salt))

        /** Factory for a dual-salt PRF input. */
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
    val output1: ByteArray,
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
    fun clear() {
        output1.fill(0)
        output2?.fill(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrfExtensionOutput) return false
        if (!output1.contentEquals(other.output1)) return false
        if (output2 != null && other.output2 != null && !output2.contentEquals(other.output2)) return false
        if ((output2 == null) != (other.output2 == null)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = output1.contentHashCode()
        result = 31 * result + (output2?.contentHashCode() ?: 0)
        return result
    }
}
