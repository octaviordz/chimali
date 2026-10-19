package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import java.math.BigInteger

/** RFC 9180 §7.1.3 DHKEM(P-256, HKDF-SHA256) deterministic parent key generation. */
@Req("NFR-SEC-041", "TR-AUTH-031")
object P256ParentKeyDerivation {
    private const val IKM_MIN_LENGTH = 32
    private const val SCALAR_LENGTH = 32
    private const val FIRST_CANDIDATE = 0
    private const val LAST_CANDIDATE = 255
    private const val KEM_ID = 0x0010
    private const val HPKE_VERSION_LABEL = "HPKE-v1"
    private const val KEM_LABEL = "KEM"
    private const val DKP_PRK_LABEL = "dkp_prk"
    private const val CANDIDATE_LABEL = "candidate"

    private val suiteId =
        byteArrayOf(
            KEM_LABEL[0].code.toByte(),
            KEM_LABEL[1].code.toByte(),
            KEM_LABEL[2].code.toByte(),
            (KEM_ID ushr 8).toByte(),
            KEM_ID.toByte(),
        )
    private val hpkeVersion = HPKE_VERSION_LABEL.toByteArray(Charsets.US_ASCII)

    /** Owned scalar/public-point result; callers must erase [privateScalar] after use.
     * @property privateScalar Owned 32-byte big-endian P-256 scalar; caller erases it after use.
     * @property publicKey Owned uncompressed SEC1 P-256 point (65 bytes); public and not erased.
     * @property candidateCounter Accepted RFC 9180 candidate number in the inclusive range 0..255.
     */
    class Result(
        val privateScalar: ByteArray,
        val publicKey: ByteArray,
        val candidateCounter: Int,
    ) {
        /** Erases the owned private scalar; [publicKey] and [candidateCounter] are public results. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun erasePrivateScalar() = privateScalar.fill(0)
    }

    /**
     * Derives a parent pair from borrowed IKM. Candidate injection is internal test seam only;
     * each returned candidate buffer transfers to this function and is erased before return.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun derive(ikm: ByteArray): Result = derive(ikm, ::deriveCandidate)

    /** Derives and erases the owned 32-byte parent IKM produced from the wallet PRK. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deriveFromPrk(prk: ByteArray): Result {
        val parentInput = HkdfSha256.deriveP256ParentInput(prk)
        return try {
            derive(parentInput)
        } finally {
            parentInput.fill(0)
        }
    }

    /** Uses an internal candidate source; [ikm] is borrowed and each returned candidate is owned and erased here. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun derive(
        ikm: ByteArray,
        candidateSource: (dkpPrk: ByteArray, counter: Int) -> ByteArray,
    ): Result {
        require(ikm.size >= IKM_MIN_LENGTH) { "RFC 9180 P-256 parent IKM must be at least 32 bytes" }
        val dkpPrk = labeledExtractEmptySalt(DKP_PRK_LABEL, ikm)
        try {
            for (counter in FIRST_CANDIDATE..LAST_CANDIDATE) {
                val candidate = candidateSource(dkpPrk, counter)
                try {
                    require(candidate.size == SCALAR_LENGTH) { "RFC 9180 candidate must be 32 bytes" }
                    // P-256's RFC 9180 bitmask is 0xff, so this is intentionally a no-op.
                    val scalar = BigInteger(1, candidate)
                    if (scalar.signum() > 0 && scalar < P256Group.ORDER) {
                        return createResult(candidate, scalar, counter)
                    }
                } finally {
                    candidate.fill(0)
                }
            }
            error("RFC 9180 P-256 parent derivation exhausted 256 candidates")
        } finally {
            dkpPrk.fill(0)
        }
    }

    private fun createResult(
        candidate: ByteArray,
        scalar: BigInteger,
        counter: Int,
    ): Result {
        var privateCopy: ByteArray? = candidate.copyOf()
        try {
            val publicKey = P256Group.serializeElement(P256Group.scalarBaseMult(scalar))
            return Result(requireNotNull(privateCopy), publicKey, counter).also { privateCopy = null }
        } finally {
            privateCopy?.fill(0)
        }
    }

    private fun deriveCandidate(
        dkpPrk: ByteArray,
        counter: Int,
    ): ByteArray {
        val counterBytes = byteArrayOf(counter.toByte())
        return try {
            labeledExpand(dkpPrk, CANDIDATE_LABEL, counterBytes, SCALAR_LENGTH)
        } finally {
            counterBytes.fill(0)
        }
    }

    private fun labeledExtractEmptySalt(
        label: String,
        ikm: ByteArray,
    ): ByteArray {
        val labeledIkm = concat(hpkeVersion, suiteId, label.toByteArray(Charsets.US_ASCII), ikm)
        return try {
            HkdfSha256.extract(byteArrayOf(), labeledIkm)
        } finally {
            labeledIkm.fill(0)
        }
    }

    private fun labeledExpand(
        prk: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val labelBytes = label.toByteArray(Charsets.US_ASCII)
        val labeledInfo =
            concat(
                byteArrayOf((length ushr 8).toByte(), length.toByte()),
                hpkeVersion,
                suiteId,
                labelBytes,
                info,
            )
        return try {
            HkdfSha256.expand(prk, labeledInfo, length)
        } finally {
            labeledInfo.fill(0)
            labelBytes.fill(0)
        }
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val result = ByteArray(parts.sumOf(ByteArray::size))
        var offset = 0
        parts.forEach { part ->
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }
}
