package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@Req("NFR-SEC-041", "TR-AUTH-031")
class HkdfSha256Test {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RFC 5869 appendix A1 extract and expand match published vector`() {
        val ikm = "0b".repeat(22).hexToBytes()
        val salt = "000102030405060708090a0b0c".hexToBytes()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes()
        val expectedPrk = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5".hexToBytes()
        val expectedOkm =
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865".hexToBytes()

        val prk = HkdfSha256.extract(salt, ikm)
        val okm = HkdfSha256.expand(prk, info, 42)

        assertContentEquals(expectedPrk, prk)
        assertContentEquals(expectedOkm, okm)
        assertEquals(32, prk.size)
        assertEquals(42, okm.size)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `empty salt uses RFC 5869 zero salt and input buffers remain borrowed`() {
        val ikm = "0b".repeat(22).hexToBytes()
        val inputBefore = ikm.copyOf()
        val zeroSaltPrk = HkdfSha256.extract(byteArrayOf(), ikm)
        val explicitZeroSaltPrk = HkdfSha256.extract(ByteArray(32), ikm)

        assertContentEquals(explicitZeroSaltPrk, zeroSaltPrk)
        assertContentEquals(inputBefore, ikm)
        assertNotSame(ikm, zeroSaltPrk)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `full synthetic BIP39 seed produces frozen local wallet PRK and parent input`() {
        val syntheticSeed = ByteArray(64) { it.toByte() }
        val expectedPrk = "aa6ff6f8d8e560bb6a8f8ff11f88b7684e184c480f30f17ac64a696730aacea2".hexToBytes()
        val expectedParentInput = "06110d9898841c82eda552a2608a654623c6c4bc08d5c4561cfe1a868cd2f664".hexToBytes()

        val prk = HkdfSha256.deriveWalletPrk(syntheticSeed)
        val parentInput = HkdfSha256.deriveP256ParentInput(prk)

        assertEquals(64, syntheticSeed.size)
        assertContentEquals(expectedPrk, prk)
        assertContentEquals(expectedParentInput, parentInput)
        assertTrue(prk !== syntheticSeed)
        prk.fill(0)
        parentInput.fill(0)
        syntheticSeed.fill(0)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential info uses exact label zero delimiter uint16 length and full borrowed identifier`() {
        val credentialId = ByteArray(16) { it.toByte() }
        val borrowedCopy = credentialId.copyOf()
        val info = HkdfSha256.credentialInfo(HkdfSha256.ED25519_CREDENTIAL_LABEL, credentialId)
        try {
            val label = HkdfSha256.ED25519_CREDENTIAL_LABEL.toByteArray(Charsets.US_ASCII)
            val expected = label + byteArrayOf(0, 0, 16) + credentialId
            assertContentEquals(expected, info)
            assertContentEquals(borrowedCopy, credentialId)
            assertEquals(1023, HkdfSha256.credentialInfo("label", ByteArray(1023)).takeLast(1023).size)
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.credentialInfo("label", ByteArray(15))
            }
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.credentialInfo("label", ByteArray(1024))
            }
        } finally {
            credentialId.fill(0)
            borrowedCopy.fill(0)
            info.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `expand rejects lengths beyond RFC 5869 maximum`() {
        val prk = ByteArray(32) { 0x5a }
        try {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.expand(prk, byteArrayOf(), 255 * 32 + 1)
            }
        } finally {
            prk.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `expand accepts zero and maximum RFC output lengths without mutating borrowed inputs`() {
        val prk = ByteArray(32) { it.toByte() }
        val info = "boundary-info".encodeToByteArray()
        val prkBefore = prk.copyOf()
        val infoBefore = info.copyOf()

        val empty = HkdfSha256.expand(prk, info, 0)
        val maximum = HkdfSha256.expand(prk, info, 255 * HkdfSha256.HASH_LENGTH)

        assertEquals(0, empty.size)
        assertEquals(255 * HkdfSha256.HASH_LENGTH, maximum.size)
        assertTrue(maximum.any { it != 0.toByte() })
        assertContentEquals(prkBefore, prk)
        assertContentEquals(infoBefore, info)
        empty.fill(0)
        maximum.fill(0)
        prk.fill(0)
        info.fill(0)
        prkBefore.fill(0)
        infoBefore.fill(0)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `expand rejects malformed PRKs and negative output length`() {
        val validPrk = ByteArray(HkdfSha256.HASH_LENGTH) { 0x5a }
        try {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.expand(ByteArray(HkdfSha256.HASH_LENGTH - 1), byteArrayOf(), 1)
            }
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.expand(ByteArray(HkdfSha256.HASH_LENGTH + 1), byteArrayOf(), 1)
            }
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.expand(validPrk, byteArrayOf(), -1)
            }
        } finally {
            validPrk.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential info rejects empty and non ASCII labels without mutating borrowed identifier`() {
        val credentialId = ByteArray(16) { it.toByte() }
        val before = credentialId.copyOf()
        try {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.credentialInfo("", credentialId)
            }
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                HkdfSha256.credentialInfo("label-π", credentialId)
            }
            assertContentEquals(before, credentialId)
        } finally {
            credentialId.fill(0)
            before.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `wallet derivation rejects seeds that are not the complete BIP39 seed`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            HkdfSha256.deriveWalletPrk(ByteArray(63))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            HkdfSha256.deriveWalletPrk(ByteArray(65))
        }
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
