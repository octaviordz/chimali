package com.chimali.core.security.hdkeys

import kotlin.test.*
import kotlin.test.Test
import java.math.BigInteger

/**
 * Tests for DhKem — DHKEM(P-256, HKDF-SHA256) per RFC 9180.
 */
class DhKemTest {
    private companion object {
        private const val HEX_RADIX = 16
        private const val HEX_BYTE_SIZE = 2
        private const val OKM_LENGTH = 42
        private const val SHARED_SECRET_SIZE = 32
    }


    @Test
    fun `HKDF extract and expand - RFC 5869 Test Case 1`() {
        val ikm = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hexToBytes("000102030405060708090a0b0c")
        val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")

        val prk = DhKem.extract(salt, ikm)
        val expectedPrk = hexToBytes("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5")
        assertContentEquals(expectedPrk, prk, "PRK mismatch")

        val okm = DhKem.expand(prk, info, OKM_LENGTH)
        val expectedOkm = hexToBytes(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        )
        assertContentEquals(expectedOkm, okm, "OKM mismatch")
    }

    @Test
    fun `DeriveKeyPair - known test vector from reference impl`() {
        val ikm = hexToBytes("4270e54ffd08d79d5928020af4686d8f6b7d35dbe470265f1f5aa22816ce860e")
        val (sk, pk) = DhKem.deriveKeyPair(ikm)

        val expectedSk = BigInteger(
            "4995788ef4b9d6132b249ce59a77281493eb39af373d236a1fe415cb0c2d7beb", HEX_RADIX
        )
        assertEquals(expectedSk, sk, "DeriveKeyPair sk mismatch")

        // Verify pk = sk * G
        val expectedPk = P256Group.scalarBaseMult(expectedSk)
        assertEquals(expectedPk.normalize(), pk.normalize())
    }

    @Test
    fun `Encap and Decap round-trip`() {
        // Derive a deterministic key pair for the recipient
        val ikm = hexToBytes("0000000000000001")
        val (skR, pkR) = DhKem.deriveKeyPair(ikm)

        // Encap: generate shared secret + encapsulated key
        val (sharedSecret, enc) = DhKem.encap(pkR)

        // Decap: recover shared secret
        val recoveredSecret = DhKem.decap(enc, skR)

        assertContentEquals(sharedSecret, recoveredSecret
        , "Encap/Decap round-trip: shared secrets must match")
        assertEquals(SHARED_SECRET_SIZE, sharedSecret.size)
    }

    @Test
    fun `DeriveKeyPair produces valid key pair`() {
        val (sk, pk) = DhKem.deriveKeyPair("test-derive".toByteArray())
        assertTrue(sk > BigInteger.ZERO)
        assertTrue(sk < P256Group.ORDER)
        val expectedPk = P256Group.scalarBaseMult(sk)
        assertEquals(expectedPk.normalize(), pk.normalize())
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(HEX_BYTE_SIZE).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
    }
}
