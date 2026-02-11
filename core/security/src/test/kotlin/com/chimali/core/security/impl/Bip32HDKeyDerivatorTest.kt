package com.chimali.core.security.impl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class Bip32HDKeyDerivatorTest {

    private val derivator = Bip32HDKeyDerivator()
    private val testSeed = "000102030405060708090a0b0c0d0e0f".toByteArray()

    @Test
    fun `deriveKey should return non-null key for master path`() {
        val masterKey = derivator.deriveKey(testSeed, "m")
        assertNotNull(masterKey)
        assertArrayEquals(32, masterKey.size.toLong())
    }

    @Test
    fun `deriveKey should be deterministic for same path`() {
        val key1 = derivator.deriveKey(testSeed, "m/44'/0'/0'/0/0")
        val key2 = derivator.deriveKey(testSeed, "m/44'/0'/0'/0/0")
        
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `deriveKey should return different keys for different paths`() {
        val key1 = derivator.deriveKey(testSeed, "m/44'/0'/0'/0/0")
        val key2 = derivator.deriveKey(testSeed, "m/44'/0'/0'/0/1")
        
        assertNotNull(key1)
        assertNotNull(key2)
        assertFalse(key1.contentEquals(key2))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `deriveKey with unhardened path should throw exception for now`() {
        derivator.deriveKey(testSeed, "m/44/0/0/0/0")
    }
}

private fun assertArrayEquals(expectedSize: Long, actualSize: Long) {
    assert(expectedSize == actualSize)
}
