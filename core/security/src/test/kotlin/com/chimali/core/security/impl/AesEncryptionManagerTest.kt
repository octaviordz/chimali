package com.chimali.core.security.impl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.*

class AesEncryptionManagerTest {

    private val encryptionManager = AesEncryptionManager()
    private val testKey = ByteArray(32).apply { Random().nextBytes(this) }
    private val testPlaintext = "Hello, Chimali!".toByteArray()

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        val decrypted = encryptionManager.decrypt(ciphertext, testKey)
        
        assertArrayEquals(testPlaintext, decrypted)
    }

    @Test
    fun `ciphertext should not be equal to plaintext`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        
        assertFalse(testPlaintext.contentEquals(ciphertext))
    }

    @Test(expected = Exception::class)
    fun `decrypt with wrong key should throw exception`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        val wrongKey = ByteArray(32).apply { Random().nextBytes(this) }
        
        encryptionManager.decrypt(ciphertext, wrongKey)
    }
}
