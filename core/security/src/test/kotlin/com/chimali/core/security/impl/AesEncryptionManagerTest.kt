package com.chimali.core.security.impl

import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertFailsWith
import java.util.*

class AesEncryptionManagerTest {

    private val encryptionManager = AesEncryptionManager()
    private val testKey = ByteArray(32).apply { Random().nextBytes(this) }
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
        val wrongKey = ByteArray(32).apply { Random().nextBytes(this) }
        
        assertFailsWith<Exception> {
            encryptionManager.decrypt(ciphertext, wrongKey)
        }
    }
}
