package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Req("NFR-SEC-041", "TR-AUTH-031")
class P256ParentKeyDerivationTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RFC 9180 appendix A3 point one DeriveKeyPair matches published vector`() {
        val ikm = "4270e54ffd08d79d5928020af4686d8f6b7d35dbe470265f1f5aa22816ce860e".hexToBytes()
        val inputBefore = ikm.copyOf()
        val expectedPrivate = "4995788ef4b9d6132b249ce59a77281493eb39af373d236a1fe415cb0c2d7beb".hexToBytes()
        val expectedPublic =
            "04a92719c6195d5085104f469a8b9814d5838ff72b60501e2c4466e5e67b325ac9" +
                "8536d7b61a1af4b78e5b7f951c0900be863c403ce65c9bfcb9382657222d18c4"

        val result = P256ParentKeyDerivation.derive(ikm)
        try {
            assertContentEquals(expectedPrivate, result.privateScalar)
            assertContentEquals(expectedPublic.hexToBytes(), result.publicKey)
            assertEquals(0, result.candidateCounter)
            assertContentEquals(inputBefore, ikm)
        } finally {
            result.privateScalar.fill(0)
            ikm.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `sampler accepts a valid scalar at counter 255 and erases each candidate and PRK`() {
        val ikm = ByteArray(32) { (it + 1).toByte() }
        val candidateBuffers = mutableListOf<ByteArray>()
        var observedPrk: ByteArray? = null

        val result =
            P256ParentKeyDerivation.derive(ikm) { dkpPrk, counter ->
                observedPrk = dkpPrk
                ByteArray(32).also { candidate ->
                    if (counter == 255) candidate[31] = 1
                    candidateBuffers += candidate
                }
            }
        try {
            assertEquals(255, result.candidateCounter)
            assertContentEquals(ByteArray(32).also { it[31] = 1 }, result.privateScalar)
            assertContentEquals(P256Group.serializeElement(P256Group.G), result.publicKey)
            assertEquals(256, candidateBuffers.size)
            candidateBuffers.forEach { assertContentEquals(ByteArray(32), it) }
            assertContentEquals(ByteArray(32), observedPrk ?: error("Sampler did not receive the dkp_prk"))
        } finally {
            result.privateScalar.fill(0)
            ikm.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `exhausted sampler rejects invalid candidates and erases every candidate`() {
        val ikm = ByteArray(32) { 0x36 }
        val candidates = mutableListOf<ByteArray>()
        val orderCandidate =
            P256Group.ORDER.toByteArray().let { encoded ->
                ByteArray(32).also { output ->
                    val unsigned = if (encoded.size == 33) encoded.copyOfRange(1, encoded.size) else encoded
                    unsigned.copyInto(output, 32 - unsigned.size)
                }
            }

        assertFailsWith<IllegalStateException> {
            P256ParentKeyDerivation.derive(ikm) { _, counter ->
                ByteArray(32).also { candidate ->
                    if (counter % 2 == 1) orderCandidate.copyInto(candidate)
                    candidates += candidate
                }
            }
        }

        assertEquals(256, candidates.size)
        candidates.forEach { assertContentEquals(ByteArray(32), it) }
        orderCandidate.fill(0)
        ikm.fill(0)
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
