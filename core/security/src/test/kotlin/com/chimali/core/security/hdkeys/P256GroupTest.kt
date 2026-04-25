package com.chimali.core.security.hdkeys

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import java.math.BigInteger

/**
 * Tests for P256Group — P-256 elliptic curve operations.
 */
class P256GroupTest {
    private companion object {
        private const val MASK_FF = 0xFF
    }

    @Test
    fun `ScalarBaseMult with 1 equals generator G`() {
        val result = P256Group.scalarBaseMult(BigInteger.ONE)
        assertEquals(P256Group.G.normalize(), result.normalize())
    }

    @Test
    fun `ScalarBaseMult with 2 equals G + G`() {
        val double = P256Group.scalarBaseMult(BigInteger.TWO)
        val added = P256Group.add(P256Group.G, P256Group.G)
        assertEquals(double.normalize(), added.normalize())
    }

    @Test
    fun `ScalarMult with order produces infinity`() {
        val result = P256Group.scalarMult(P256Group.G, P256Group.ORDER)
        assertTrue(result.isInfinity, "ScalarMult(G, n) should be point at infinity")
    }

    @Test
    fun `GenerateKeyPair produces valid key pair`() {
        val (sk, pk) = P256Group.generateKeyPair()
        val expectedPk = P256Group.scalarBaseMult(sk)
        assertTrue(sk > BigInteger.ZERO)
        assertTrue(sk < P256Group.ORDER)
        assertEquals(expectedPk.normalize(), pk.normalize())
    }

    @Test
    fun `SerializeElement and deserializeElement round-trip`() {
        val (_, pk) = P256Group.generateKeyPair()
        val serialized = P256Group.serializeElement(pk)
        assertEquals(P256Group.ELEMENT_UNCOMPRESSED_LENGTH, serialized.size) // uncompressed: 04 || x(32) || y(32)
        assertEquals(P256Group.UNCOMPRESSED_FORMAT_INDICATOR, serialized[0].toInt() and MASK_FF)
        val deserialized = P256Group.deserializeElement(serialized)
        assertEquals(pk.normalize(), deserialized.normalize())
    }

    @Test
    fun `SerializeScalar and deserializeScalar round-trip`() {
        val scalar = P256Group.randomScalar()
        val serialized = P256Group.serializeScalar(scalar)
        assertEquals(P256Group.SCALAR_LENGTH, serialized.size)
        val deserialized = P256Group.deserializeScalar(serialized)
        assertEquals(scalar, deserialized)
    }

    @Test
    fun `CreateSharedSecret is consistent`() {
        val (skA, pkA) = P256Group.generateKeyPair()
        val (skB, pkB) = P256Group.generateKeyPair()
        val secretAB = P256Group.createSharedSecret(skA, pkB)
        val secretBA = P256Group.createSharedSecret(skB, pkA)
        assertContentEquals(secretAB, secretBA)
        assertEquals(P256Group.DH_OUTPUT_LENGTH, secretAB.size)
    }
}
