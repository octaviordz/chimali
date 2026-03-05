package com.chimali.fido2.data.storage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

class AndroidKeyStoreWrapperTest {

    private lateinit var keyStoreWrapper: AndroidKeyStoreWrapper
    private val testAlias = "test-key-alias"

    @BeforeEach
    fun setUp() {
        keyStoreWrapper = AndroidKeyStoreWrapper()
    }

    @AfterEach
    fun tearDown() = runTest {
        // Clean up test keys if they were created
        try {
            keyStoreWrapper.deleteKey(testAlias)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `test key pair generation success`() = runTest {
        val result = keyStoreWrapper.generateKeyPair(testAlias)
        
        assertTrue(result.isSuccess)
        val keyPair = result.getOrNull()
        assertNotNull(keyPair)
        assertNotNull(keyPair?.public)
        assertNotNull(keyPair?.private)
    }

    @Test
    fun `test key pair generation duplicate alias`() = runTest {
        // Generate first key
        val firstResult = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(firstResult.isSuccess)
        
        // Try to generate with same alias
        val secondResult = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(secondResult.isFailure)
    }

    @Test
    fun `test get public key after generation`() = runTest {
        // Generate key pair
        val generateResult = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(generateResult.isSuccess)
        
        // Retrieve public key
        val publicKey = keyStoreWrapper.getPublicKey(testAlias)
        assertNotNull(publicKey)
        
        // Verify it's the same key
        val originalPublicKey = generateResult.getOrNull()?.public
        assertEquals(originalPublicKey?.encoded, publicKey?.encoded)
    }

    @Test
    fun `test get private key after generation`() = runTest {
        // Generate key pair
        val generateResult = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(generateResult.isSuccess)
        
        // Retrieve private key
        val privateKey = keyStoreWrapper.getPrivateKey(testAlias)
        assertNotNull(privateKey)
        
        // Verify it's the same key
        val originalPrivateKey = generateResult.getOrNull()?.private
        assertEquals(originalPrivateKey?.encoded, privateKey?.encoded)
    }

    @Test
    fun `test get non-existent public key returns null`() = runTest {
        val publicKey = keyStoreWrapper.getPublicKey("non-existent-alias")
        assertNull(publicKey)
    }

    @Test
    fun `test get non-existent private key returns null`() = runTest {
        val privateKey = keyStoreWrapper.getPrivateKey("non-existent-alias")
        assertNull(privateKey)
    }

    @Test
    fun `test key exists after generation`() = runTest {
        // Initially should not exist
        assertFalse(keyStoreWrapper.keyExists(testAlias))
        
        // Generate key
        val generateResult = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(generateResult.isSuccess)
        
        // Should exist now
        assertTrue(keyStoreWrapper.keyExists(testAlias))
    }

    @Test
    fun `test key does not exist for non-existent alias`() = runTest {
        assertFalse(keyStoreWrapper.keyExists("non-existent-alias"))
    }

    @Test
    fun `test delete key success`() = runTest {
        // Generate key
        val generateResult = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(generateResult.isSuccess)
        assertTrue(keyStoreWrapper.keyExists(testAlias))
        
        // Delete key
        val deleteResult = keyStoreWrapper.deleteKey(testAlias)
        assertTrue(deleteResult.isSuccess)
        
        // Should not exist anymore
        assertFalse(keyStoreWrapper.keyExists(testAlias))
        assertNull(keyStoreWrapper.getPublicKey(testAlias))
        assertNull(keyStoreWrapper.getPrivateKey(testAlias))
    }

    @Test
    fun `test delete non-existent key`() = runTest {
        val deleteResult = keyStoreWrapper.deleteKey("non-existent-alias")
        // Should succeed even if key doesn't exist (idempotent operation)
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun `test key pair generation uses correct algorithm`() = runTest {
        val result = keyStoreWrapper.generateKeyPair(testAlias)
        assertTrue(result.isSuccess)
        
        val keyPair = result.getOrNull()
        assertEquals("EC", keyPair?.public?.algorithm)
        assertEquals("EC", keyPair?.private?.algorithm)
    }
}
