package com.chimali.core.security.hdkeys

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/**
 * Tests for P256Group — P-256 elliptic curve operations.
 */
class P256GroupTest {

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
        assertTrue("ScalarMult(G, n) should be point at infinity", result.isInfinity)
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
        assertEquals(65, serialized.size) // uncompressed: 04 || x(32) || y(32)
        assertEquals(0x04, serialized[0].toInt() and 0xFF)
        val deserialized = P256Group.deserializeElement(serialized)
        assertEquals(pk.normalize(), deserialized.normalize())
    }

    @Test
    fun `SerializeScalar and deserializeScalar round-trip`() {
        val scalar = P256Group.randomScalar()
        val serialized = P256Group.serializeScalar(scalar)
        assertEquals(32, serialized.size)
        val deserialized = P256Group.deserializeScalar(serialized)
        assertEquals(scalar, deserialized)
    }

    @Test
    fun `CreateSharedSecret is consistent`() {
        val (skA, pkA) = P256Group.generateKeyPair()
        val (skB, pkB) = P256Group.generateKeyPair()
        val secretAB = P256Group.createSharedSecret(skA, pkB)
        val secretBA = P256Group.createSharedSecret(skB, pkA)
        assertArrayEquals(secretAB, secretBA)
        assertEquals(32, secretAB.size)
    }
}
