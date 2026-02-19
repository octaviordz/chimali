package com.chimali.core.security.hdkeys

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/**
 * Tests for HdkEcdhP256 — the main HDK-ECDH-P256 instantiation.
 */
class HdkEcdhP256Test {

    private val hdk = HdkEcdhP256()

    @Test
    fun `Local derivation - root HDK is consistent with blind private key`() {
        // Derive HDK at path [0], then verify:
        // pk_blinded == ScalarBaseMult(BlindPrivateKey(sk, bf))
        val (sk, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val (blindedPk, _, bf) = hdk.hdk(0, pk, seed)

        val blindedSk = MultiplicativeBlinding.blindPrivateKey(sk, bf)
        val expectedPk = P256Group.scalarBaseMult(blindedSk)

        assertEquals(
            "Blinded public key must match ScalarBaseMult(BlindPrivateKey(sk, bf))",
            expectedPk.normalize(), blindedPk.normalize()
        )
    }

    @Test
    fun `Deterministic derivation - same inputs produce same outputs`() {
        val (_, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val result1 = hdk.hdk(42, pk, seed)
        val result2 = hdk.hdk(42, pk, seed)

        assertEquals(result1.first.normalize(), result2.first.normalize())
        assertArrayEquals(result1.second, result2.second)
        assertEquals(result1.third, result2.third)
    }

    @Test
    fun `Different indices produce different HDK nodes`() {
        val (_, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val result0 = hdk.hdk(0, pk, seed)
        val result1 = hdk.hdk(1, pk, seed)

        assertNotEquals(
            "Different indices must produce different public keys",
            result0.first.normalize(), result1.first.normalize()
        )
        assertFalse(result0.second.contentEquals(result1.second))
    }

    @Test
    fun `Multi-level derivation via fold`() {
        val (sk, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val (finalPk, finalSalt, finalBf) = hdk.fold(listOf(0, 1, 2), pk, seed)

        // Manual step-by-step derivation should match
        val (pk1, salt1, bf1) = hdk.hdk(0, pk, seed)
        val (pk2, salt2, bf2) = hdk.hdk(1, pk1, salt1, bf1)
        val (pk3, salt3, bf3) = hdk.hdk(2, pk2, salt2, bf2)

        assertEquals(pk3.normalize(), finalPk.normalize())
        assertArrayEquals(salt3, finalSalt)
        assertEquals(bf3, finalBf)
    }

    @Test
    fun `Proof of possession - BlindDH equals reader shared secret`() {
        val (skDevice, pkDevice) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        // Derive HDK at root path [0]
        val (blindedPk, _, bf) = hdk.hdk(0, pkDevice, seed)

        // Reader generates a key pair
        val (skReader, pkReader) = P256Group.generateKeyPair()

        // Device computes BlindDH
        val deviceSecret = MultiplicativeBlinding.blindDh(skDevice, bf, pkReader)

        // Reader computes CreateSharedSecret(skReader, blindedPk)
        val readerSecret = P256Group.createSharedSecret(skReader, blindedPk)

        assertArrayEquals(
            "Proof of possession: device and reader shared secrets must match",
            deviceSecret, readerSecret
        )
    }

    @Test
    fun `Remote derivation - Encap, Decap, and HDK round-trip`() {
        val (_, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        // Derive root HDK
        val (rootPk, rootSalt, rootBf) = hdk.hdk(0, pk, seed)

        // Request remote derivation: device derives KEM key pair from salt
        val (skKem, pkKem) = DhKem.deriveKeyPair(rootSalt)

        // Issuer encaps a shared secret using the KEM public key
        val (issuedSalt, keyHandle) = DhKem.encap(pkKem)

        // Issuer computes the expected derived HDK
        val index = 42
        val (expectedPk, _, _) = hdk.hdk(index, rootPk, issuedSalt)

        // Device decaps and derives the HDK
        val decappedSalt = DhKem.decap(keyHandle, skKem)
        assertArrayEquals("Decapped salt must match issued salt", issuedSalt, decappedSalt)

        val (derivedPk, _, _) = hdk.hdk(index, rootPk, decappedSalt)
        assertEquals(
            "Remote derivation: derived pk must match expected pk",
            expectedPk.normalize(), derivedPk.normalize()
        )
    }

    @Test
    fun `HdkManager deriveHdk via public API`() {
        val keyPair = hdk.generateDeviceKeyPair()
        val seed = hdk.generateSeed()
        val pkBytes = P256Group.serializeElement(keyPair.publicKey)

        val result = hdk.deriveHdk(pkBytes, seed, listOf(0))
        assertNotNull(result.publicKey)
        assertEquals(32, result.salt.size)
        assertTrue(result.blindingFactor > BigInteger.ZERO)
    }

    @Test
    fun `HdkManager blindPrivateKey via public API`() {
        val keyPair = hdk.generateDeviceKeyPair()
        val bf = P256Group.randomScalar()

        val skBytes = P256Group.serializeScalar(keyPair.privateKey)
        val bfBytes = P256Group.serializeScalar(bf)

        val blindedSkBytes = hdk.blindPrivateKey(skBytes, bfBytes)
        assertEquals(32, blindedSkBytes.size)

        // Verify consistency: pk' == ScalarBaseMult(sk')
        val blindedSk = P256Group.deserializeScalar(blindedSkBytes)
        val blindedPk = P256Group.scalarBaseMult(blindedSk)
        val expectedPk = P256Group.scalarMult(keyPair.publicKey, bf)
        assertEquals(expectedPk.normalize(), blindedPk.normalize())
    }

    @Test
    fun `CreateContext includes ID and index`() {
        val ctx = hdk.createContext(42)
        // Should be ID (16 bytes for "HDK-ECDH-P256-v1") + index (4 bytes)
        assertEquals(HdkEcdhP256.ID.size + 4, ctx.size)
    }

    @Test
    fun `GenerateSeed produces 32 bytes`() {
        val seed = hdk.generateSeed()
        assertEquals(32, seed.size)
    }
}
