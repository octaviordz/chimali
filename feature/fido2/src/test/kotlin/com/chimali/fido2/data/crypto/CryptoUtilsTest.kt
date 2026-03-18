package com.chimali.fido2.data.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

class CryptoUtilsTest {

    private lateinit var hdKeyDerivation: HdKeyDerivation
    private lateinit var memoryUtils: MemoryUtils
    private lateinit var cborCodec: CborCodec

    @BeforeEach
    fun setUp() {
        hdKeyDerivation = HdKeyDerivation()
        memoryUtils = MemoryUtils()
        cborCodec = CborCodec()
    }

    @Test
    fun `test chain code generation`() {
        val chainCode1 = hdKeyDerivation.generateChainCode()
        val chainCode2 = hdKeyDerivation.generateChainCode()
        
        assertEquals(32, chainCode1.size)
        assertEquals(32, chainCode2.size)
        assertFalse(chainCode1.contentEquals(chainCode2))
    }

    @Test
    fun `test child key derivation`() {
        val parentKeyPair = generateTestKeyPair()
        val chainCode = hdKeyDerivation.generateChainCode()
        
        val childKeyPair = hdKeyDerivation.deriveChildKey(parentKeyPair, chainCode, 0)
        
        assertNotNull(childKeyPair.public)
        assertNotNull(childKeyPair.private)
        assertNotEquals(parentKeyPair.public.encoded, childKeyPair.public.encoded)
    }

    @Test
    fun `test key agreement`() {
        val keyPair1 = generateTestKeyPair()
        val keyPair2 = generateTestKeyPair()
        
        val sharedSecret1 = hdKeyDerivation.performKeyAgreement(keyPair1.private, keyPair2.public)
        val sharedSecret2 = hdKeyDerivation.performKeyAgreement(keyPair2.private, keyPair1.public)
        
        assertNotNull(sharedSecret1)
        assertNotNull(sharedSecret2)
        assertArrayEquals(sharedSecret1, sharedSecret2)
    }

    @Test
    fun `test data signing`() {
        val keyPair = generateTestKeyPair()
        val testData = "test data to sign".toByteArray()
        
        val signature = hdKeyDerivation.signData(keyPair.private, testData)
        
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test signature verification`() {
        val keyPair = generateTestKeyPair()
        val testData = "test data to sign".toByteArray()
        
        val signature = hdKeyDerivation.signData(keyPair.private, testData)
        val isValid = hdKeyDerivation.verifySignature(keyPair.public, testData, signature)
        
        assertTrue(isValid)
    }

    @Test
    fun `test signature verification with wrong data`() {
        val keyPair = generateTestKeyPair()
        val testData = "test data to sign".toByteArray()
        val wrongData = "wrong data".toByteArray()
        
        val signature = hdKeyDerivation.signData(keyPair.private, testData)
        val isValid = hdKeyDerivation.verifySignature(keyPair.public, wrongData, signature)
        
        assertFalse(isValid)
    }

    @Test
    fun `test memory zeroing byte array`() {
        val sensitiveData = "sensitive data".toByteArray()
        
        // Verify data exists
        assertTrue(sensitiveData.isNotEmpty())
        
        // Zero the data
        memoryUtils.zeroByteArray(sensitiveData)
        
        // Verify data is zeroed
        assertTrue(sensitiveData.all { it == 0.toByte() })
    }

    @Test
    fun `test memory zeroing char array`() {
        val sensitiveData = "sensitive data".toCharArray()
        
        // Verify data exists
        assertTrue(sensitiveData.isNotEmpty())
        
        // Zero the data
        memoryUtils.zeroCharArray(sensitiveData)
        
        // Verify data is zeroed
        assertTrue(sensitiveData.all { it == '\u0000' })
    }

    @Test
    fun `test secure string to chars conversion`() {
        val originalString = "secure password"
        val chars = memoryUtils.secureStringToChars(originalString)
        
        assertEquals(originalString.length, chars.size)
        assertArrayEquals(originalString.toCharArray(), chars)
    }

    @Test
    fun `test secure string to bytes conversion`() {
        val originalString = "secure password"
        val bytes = memoryUtils.secureStringToBytes(originalString)
        
        assertEquals(originalString.toByteArray().size, bytes.size)
        assertArrayEquals(originalString.toByteArray(), bytes)
    }

    @Test
    fun `test secure array copy byte array`() {
        val original = "original data".toByteArray()
        val copy = memoryUtils.createSecureCopy(original)
        
        assertArrayEquals(original, copy)
        assertNotSame(original, copy)
    }

    @Test
    fun `test secure array copy char array`() {
        val original = "original data".toCharArray()
        val copy = memoryUtils.createSecureCopy(original)
        
        assertArrayEquals(original, copy)
        assertNotSame(original, copy)
    }

    @Test
    fun `test secure byte array comparison equal`() {
        val array1 = "test data".toByteArray()
        val array2 = "test data".toByteArray()
        
        assertTrue(memoryUtils.compareSecureArrays(array1, array2))
    }

    @Test
    fun `test secure byte array comparison not equal`() {
        val array1 = "test data".toByteArray()
        val array2 = "different data".toByteArray()
        
        assertFalse(memoryUtils.compareSecureArrays(array1, array2))
    }

    @Test
    fun `test secure byte array comparison different lengths`() {
        val array1 = "short".toByteArray()
        val array2 = "much longer data".toByteArray()
        
        assertFalse(memoryUtils.compareSecureArrays(array1, array2))
    }

    @Test
    fun `test CBOR encoding and decoding`() {
        val testData = mapOf(
            "rpId" to "example.com",
            "userId" to "user123",
            "counter" to 42L
        )
        
        val encoded = cborCodec.encodeToFido2Format(testData)
        val decoded = cborCodec.decodeFromFido2Format(encoded)
        
        assertNotNull(encoded)
        assertNotNull(decoded)
        assertEquals(testData["rpId"], decoded["rpId"])
        assertEquals(testData["userId"], decoded["userId"])
        assertEquals(testData["counter"], decoded["counter"])
    }

    @Test
    fun `test CBOR attestation object encoding`() {
        val rpIdHash = ByteArray(32) { 0x01 }
        val flags: Byte = 0x01
        val counter = 12345
        val aaguid = ByteArray(16) { 0x02 }
        val credentialId = ByteArray(16) { 0x03 }
        val publicKeyBytes = ByteArray(32) { 0x04 }
        
        val encoded = cborCodec.encodeAttestationObject(
            rpIdHash, flags, counter, aaguid, credentialId, publicKeyBytes
        )
        
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `test CBOR authenticator data encoding`() {
        val rpIdHash = ByteArray(32) { 0x01 }
        val flags: Byte = 0x01
        val counter = 12345
        
        val encoded = cborCodec.encodeAuthenticatorData(rpIdHash, flags, counter)
        
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `test CBOR client data JSON encoding`() {
        val type = "webauthn.create"
        val challenge = ByteArray(32) { 0x05 }
        val origin = "https://example.com"
        
        val encoded = cborCodec.encodeClientDataJson(type, challenge, origin)
        
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    private fun generateTestKeyPair(): KeyPair {
        val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }
}
