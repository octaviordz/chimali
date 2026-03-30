package com.chimali.core.security.hdkeys

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/**
 * Tests for MultiplicativeBlinding — key blinding scheme.
 */
class MultiplicativeBlindingTest {

    @Test
    fun `BlindDH correctness - blind DH equals reader computing with blinded key`() {
        // From the reference implementation's test:
        // BlindDH(skX, bf, pkY) == CreateSharedSecret(skY, BlindPublicKey(pkX, bk, ctx))
        val ikm = byteArrayOf(1, 2, 3)
        val ctx = byteArrayOf(4, 5, 6)

        val bk = MultiplicativeBlinding.deriveBlindKey(ikm)
        val bf = MultiplicativeBlinding.deriveBlindingFactor(bk, ctx)

        val (skX, pkX) = P256Group.generateKeyPair()
        val (skY, pkY) = P256Group.generateKeyPair()

        // Device side: BlindDH(skX, bf, pkY)
        val deviceSecret = MultiplicativeBlinding.blindDh(skX, bf, pkY)

        // Reader side: CreateSharedSecret(skY, BlindPublicKey(pkX, bk, ctx))
        val blindedPkX = MultiplicativeBlinding.blindPublicKey(pkX, bk, ctx)
        val readerSecret = P256Group.createSharedSecret(skY, blindedPkX)

        assertArrayEquals(
            "BlindDH must equal reader's shared secret",
            deviceSecret, readerSecret
        )
    }

    @Test
    fun `Combine associativity - chained blinding is equivalent to combined factor`() {
        // From reference test:
        // BlindDH(sk, Combine(bf1, bf2), G) ==
        //   CreateSharedSecret(1, BlindPublicKey(BlindPublicKey(pk, bk, ctx1), bk, ctx2))
        val ikm = byteArrayOf(1, 2, 3)
        val ctx1 = byteArrayOf(4, 5, 6)
        val ctx2 = byteArrayOf(7, 8, 9)

        val bk = MultiplicativeBlinding.deriveBlindKey(ikm)
        val bf1 = MultiplicativeBlinding.deriveBlindingFactor(bk, ctx1)
        val bf2 = MultiplicativeBlinding.deriveBlindingFactor(bk, ctx2)

        val (sk, pk) = P256Group.generateKeyPair()

        // Compute via chained blinding
        val doublyBlinded = MultiplicativeBlinding.blindPublicKey(
            MultiplicativeBlinding.blindPublicKey(pk, bk, ctx1),
            bk, ctx2
        )
        val chainedSecret = P256Group.createSharedSecret(BigInteger.ONE, doublyBlinded)

        // Compute via combined blinding factor
        val combinedBf = MultiplicativeBlinding.combine(bf1, bf2)
        val combinedSecret = MultiplicativeBlinding.blindDh(sk, combinedBf, P256Group.G)

        assertArrayEquals(
            "Chained blinding must equal combined blinding factor",
            chainedSecret, combinedSecret
        )
    }

    @Test
    fun `DeriveBlindKey is deterministic`() {
        val ikm = byteArrayOf(1, 2, 3)
        val bk1 = MultiplicativeBlinding.deriveBlindKey(ikm)
        val bk2 = MultiplicativeBlinding.deriveBlindKey(ikm)
        assertArrayEquals(bk1, bk2)
        assertEquals(32, bk1.size)
    }

    @Test
    fun `BlindPrivateKey produces consistent public key`() {
        // sk' = sk * bf mod n  →  pk' = ScalarBaseMult(sk') = ScalarMult(pk, bf)
        val (sk, pk) = P256Group.generateKeyPair()
        val bf = P256Group.randomScalar()

        val blindedSk = MultiplicativeBlinding.blindPrivateKey(sk, bf)
        val expectedPk = P256Group.scalarBaseMult(blindedSk)
        val actualPk = P256Group.scalarMult(pk, bf)

        assertEquals(expectedPk.normalize(), actualPk.normalize())
    }

    @Test
    fun `t174 BlindPublicKey and BlindPrivateKey consistency KAT`() {
        // ScalarBaseMult(BlindPrivateKey(sk, bf)) == BlindPublicKey(pk, bk, ctx)
        val ikm = byteArrayOf(1, 2, 3, 4, 5)
        val ctx = byteArrayOf(6, 7, 8, 9, 0)

        val bk = MultiplicativeBlinding.deriveBlindKey(ikm)
        val bf = MultiplicativeBlinding.deriveBlindingFactor(bk, ctx)

        val (sk, pk) = P256Group.generateKeyPair()

        // Derive key through the private path
        val blindedSk = MultiplicativeBlinding.blindPrivateKey(sk, bf)
        val pkFromBlindedSk = P256Group.scalarBaseMult(blindedSk)

        // Derive key through the public path
        val blindedPk = MultiplicativeBlinding.blindPublicKey(pk, bk, ctx)

        assertEquals(
            "ScalarBaseMult(BlindPrivateKey(sk, bf)) must equal BlindPublicKey(pk, bk, ctx)",
            pkFromBlindedSk.normalize(), blindedPk.normalize()
        )
    }
}
