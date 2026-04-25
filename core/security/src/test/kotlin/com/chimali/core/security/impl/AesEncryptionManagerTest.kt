package com.chimali.core.security.impl

import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertFailsWith
import java.util.Random

class AesEncryptionManagerTest {
    private companion object {
        private const val AES_256_KEY_SIZE = 32
    }

    private val encryptionManager = AesEncryptionManager()
    private val testKey = ByteArray(AES_256_KEY_SIZE).apply { Random().nextBytes(this) }
    private val testPlaintext = "Hello, Chimali!".toByteArray()

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        val decrypted = encryptionManager.decrypt(ciphertext, testKey)
        
        assertContentEquals(testPlaintext, decrypted)
    }

    @Test
    fun `ciphertext should not be equal to plaintext`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        
        assertFalse(testPlaintext.contentEquals(ciphertext))
    }

    @Test
    fun `decrypt with wrong key should throw exception`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        val wrongKey = ByteArray(AES_256_KEY_SIZE).apply { Random().nextBytes(this) }
        
        assertFailsWith<Exception> {
            encryptionManager.decrypt(ciphertext, wrongKey)
        }
    }
}
