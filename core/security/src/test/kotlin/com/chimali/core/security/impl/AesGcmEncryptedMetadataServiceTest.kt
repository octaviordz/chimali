package com.chimali.core.security.impl

import com.chimali.core.security.api.EncryptedMetadataService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertFailsWith

class AesGcmEncryptedMetadataServiceTest {

    private lateinit var service: AesGcmEncryptedMetadataService
    private lateinit var testKey: ByteArray

    @BeforeEach
    fun setup() {
        service = AesGcmEncryptedMetadataService()
        testKey = ByteArray(32)
        SecureRandom().nextBytes(testKey)
        service.provisionKey(testKey)
    }

    @Test
    fun `encrypt and decrypt with associated data succeeds`() {
        val plaintext = "test_value".toByteArray()
        val associatedData = "rp_id".toByteArray()

        val ciphertext = service.encrypt(plaintext, associatedData)
        val decrypted = service.decrypt(ciphertext, associatedData)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt without associated data succeeds`() {
        val plaintext = "test_value".toByteArray()

        val ciphertext = service.encrypt(plaintext, null)
        val decrypted = service.decrypt(ciphertext, null)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt fails with different associated data`() {
        val plaintext = "test_value".toByteArray()
        val associatedData = "rp_id".toByteArray()
        val wrongAssociatedData = "other_rp_id".toByteArray()

        val ciphertext = service.encrypt(plaintext, associatedData)

        val exception = assertFailsWith<SecurityException> {
            service.decrypt(ciphertext, wrongAssociatedData)
        }
        assertEquals("Authentication failed: data may be tampered", exception.message)
    }

    @Test
    fun `encrypt uses unique nonces`() {
        val plaintext = "test_value".toByteArray()
        val associatedData = "rp_id".toByteArray()

        val ciphertext1 = service.encrypt(plaintext, associatedData)
        val ciphertext2 = service.encrypt(plaintext, associatedData)

        // Version is byte 0, nonce is bytes 1-12. If nonces are unique, ciphertexts will differ.
        assertFalse(ciphertext1.contentEquals(ciphertext2))
    }

    @Test
    fun `tampering with ciphertext fails decryption`() {
        val plaintext = "test_value".toByteArray()
        val associatedData = "rp_id".toByteArray()

        val ciphertext = service.encrypt(plaintext, associatedData)
        
        // Flip one bit in the ciphertext part
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1].toInt() xor 1).toByte()

        val exception = assertFailsWith<SecurityException> {
            service.decrypt(ciphertext, associatedData)
        }
        assertEquals("Authentication failed: data may be tampered", exception.message)
    }

    @Test
    fun `tampering with nonce fails decryption`() {
        val plaintext = "test_value".toByteArray()
        val associatedData = "rp_id".toByteArray()

        val ciphertext = service.encrypt(plaintext, associatedData)
        
        // Modify the nonce
        ciphertext[1] = (ciphertext[1].toInt() xor 1).toByte()

        val exception = assertFailsWith<SecurityException> {
            service.decrypt(ciphertext, associatedData)
        }
        assertEquals("Authentication failed: data may be tampered", exception.message)
    }

    @Test
    fun `unsupported version fails decryption`() {
        val plaintext = "test_value".toByteArray()
        val associatedData = "rp_id".toByteArray()

        val ciphertext = service.encrypt(plaintext, associatedData)
        
        // Modify the version byte
        ciphertext[0] = (EncryptedMetadataService.VERSION_1 + 1).toByte()

        assertFailsWith<IllegalArgumentException> {
            service.decrypt(ciphertext, associatedData)
        }
    }
}
