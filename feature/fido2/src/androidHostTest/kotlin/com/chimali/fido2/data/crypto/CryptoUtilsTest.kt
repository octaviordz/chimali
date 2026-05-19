package com.chimali.fido2.data.crypto

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CryptoUtilsTest {
    private lateinit var memoryUtils: MemoryUtils
    private lateinit var cborCodec: CborCodec

    @BeforeTest
    fun setUp() {
        memoryUtils = MemoryUtils()
        cborCodec = CborCodec()
    }

    private companion object {
        private const val TEST_COUNTER_42 = 42L
        private const val TEST_COUNTER_12345 = 12345
        private const val HASH_SIZE_32 = 32
        private const val AAGUID_SIZE_16 = 16
        private const val CRED_ID_SIZE_16 = 16
        private const val PUB_KEY_SIZE_32 = 32
        private const val CHALLENGE_SIZE_32 = 32
        private const val FLAG_USER_PRESENT = 0x01.toByte()
        private const val DUMMY_BYTE_1 = 0x01.toByte()
        private const val DUMMY_BYTE_2 = 0x02.toByte()
        private const val DUMMY_BYTE_3 = 0x03.toByte()
        private const val DUMMY_BYTE_4 = 0x04.toByte()
        private const val DUMMY_BYTE_5 = 0x05.toByte()
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
        assertContentEquals(originalString.toCharArray(), chars)
    }

    @Test
    fun `test secure string to bytes conversion`() {
        val originalString = "secure password"
        val bytes = memoryUtils.secureStringToBytes(originalString)

        assertEquals(originalString.toByteArray().size, bytes.size)
        assertContentEquals(originalString.toByteArray(), bytes)
    }

    @Test
    fun `test secure array copy byte array`() {
        val original = "original data".toByteArray()
        val copy = memoryUtils.createSecureCopy(original)

        assertContentEquals(original, copy)
        assertNotSame(original, copy)
    }

    @Test
    fun `test secure array copy char array`() {
        val original = "original data".toCharArray()
        val copy = memoryUtils.createSecureCopy(original)

        assertContentEquals(original, copy)
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
        val testData =
            mapOf(
                "rpId" to "example.com",
                "userId" to "user123",
                "counter" to TEST_COUNTER_42,
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
        val rpIdHash = ByteArray(HASH_SIZE_32) { DUMMY_BYTE_1 }
        val flags: Byte = FLAG_USER_PRESENT
        val counter = TEST_COUNTER_12345
        val aaguid = ByteArray(AAGUID_SIZE_16) { DUMMY_BYTE_2 }
        val credentialId = ByteArray(CRED_ID_SIZE_16) { DUMMY_BYTE_3 }
        val publicKeyBytes = ByteArray(PUB_KEY_SIZE_32) { DUMMY_BYTE_4 }

        val encoded =
            cborCodec.encodeAttestationObject(
                rpIdHash,
                flags,
                counter,
                aaguid,
                credentialId,
                publicKeyBytes,
            )

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `test CBOR authenticator data encoding`() {
        val rpIdHash = ByteArray(HASH_SIZE_32) { DUMMY_BYTE_1 }
        val flags: Byte = FLAG_USER_PRESENT
        val counter = TEST_COUNTER_12345

        val encoded = cborCodec.encodeAuthenticatorData(rpIdHash, flags, counter)

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `test CBOR client data JSON encoding`() {
        val type = "webauthn.create"
        val challenge = ByteArray(CHALLENGE_SIZE_32) { DUMMY_BYTE_5 }
        val origin = "https://example.com"

        val encoded = cborCodec.encodeClientDataJson(type, challenge, origin)

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }
}
