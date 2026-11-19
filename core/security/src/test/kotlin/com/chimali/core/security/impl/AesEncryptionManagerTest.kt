package com.chimali.core.security.impl

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.jupiter.api.AfterEach

class AesEncryptionManagerTest {
    private companion object {
        private const val AES_256_KEY_SIZE = 32
    }

    private val observedBuffers = mutableListOf<ByteArray>()
    private val encryptionManager = AesEncryptionManager().apply { temporaryBufferObserver = observedBuffers::add }
    private val testKey = ByteArray(AES_256_KEY_SIZE).apply { Random().nextBytes(this) }
    private val testPlaintext = "Hello, Chimali!".toByteArray()

    @AfterEach
    fun clearTestOwners() {
        testKey.fill(0)
        testPlaintext.fill(0)
        observedBuffers.forEach { it.fill(0) }
        observedBuffers.clear()
        encryptionManager.temporaryBufferObserver = null
    }

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        try {
            assertObservedBuffersErased()
            val decrypted = encryptionManager.decrypt(ciphertext, testKey)
            try {
                assertContentEquals(testPlaintext, decrypted)
                assertObservedBuffersErased()
            } finally {
                decrypted.fill(0)
            }
        } finally {
            ciphertext.fill(0)
        }
    }

    @Test
    fun `encrypt erases its temporary IV when key initialization fails`() {
        val invalidKey = byteArrayOf(1)
        try {
            assertFailsWith<Exception> { encryptionManager.encrypt(testPlaintext, invalidKey) }

            assertObservedBuffersErased()
        } finally {
            invalidKey.fill(0)
        }
    }

    @Test
    fun `ciphertext should not be equal to plaintext`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        try {
            assertFalse(testPlaintext.contentEquals(ciphertext))
            assertObservedBuffersErased()
        } finally {
            ciphertext.fill(0)
        }
    }

    @Test
    fun `decrypt with wrong key should throw exception`() {
        val ciphertext = encryptionManager.encrypt(testPlaintext, testKey)
        val wrongKey = ByteArray(AES_256_KEY_SIZE).apply { Random().nextBytes(this) }
        observedBuffers.forEach { it.fill(0) }
        observedBuffers.clear()
        try {
            assertFailsWith<Exception> {
                encryptionManager.decrypt(ciphertext, wrongKey)
            }
            assertObservedBuffersErased()
        } finally {
            wrongKey.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun assertObservedBuffersErased() {
        assertFalse(observedBuffers.isEmpty(), "Operation must expose at least one app-owned temporary")
        observedBuffers.forEach { buffer ->
            assertContentEquals(ByteArray(buffer.size), buffer, "Operation-owned temporary must be erased")
        }
    }
}
