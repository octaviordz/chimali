package com.chimali.fido2.data.crypto

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoUtilsTest {
    private lateinit var cborCodec: CborCodec
    private val testOwnedBuffers = mutableListOf<ByteArray>()

    @BeforeTest
    fun setUp() {
        cborCodec = CborCodec()
    }

    @AfterTest
    fun eraseSyntheticCborBuffers() {
        testOwnedBuffers.forEach { it.fill(0) }
        testOwnedBuffers.clear()
    }

    private fun testBytes(
        size: Int,
        value: Byte = 0,
    ): ByteArray = ByteArray(size) { value }.also(testOwnedBuffers::add)

    private fun trackBuffer(buffer: ByteArray): ByteArray = buffer.also(testOwnedBuffers::add)

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
    fun `test CBOR encoding and decoding`() {
        val testData =
            mapOf(
                "rpId" to "example.com",
                "userId" to "user123",
                "counter" to TEST_COUNTER_42,
            )

        val encoded = trackBuffer(cborCodec.encodeToFido2Format(testData))
        val decoded = cborCodec.decodeFromFido2Format(encoded)
        try {
            assertNotNull(encoded)
            assertNotNull(decoded)
            assertEquals(testData["rpId"], decoded["rpId"])
            assertEquals(testData["userId"], decoded["userId"])
            assertEquals(testData["counter"], decoded["counter"])
        } finally {
            cborCodec.eraseDecodedRequestValues(decoded)
        }
    }

    @Test
    fun `test CBOR attestation object encoding`() {
        val rpIdHash = testBytes(HASH_SIZE_32, DUMMY_BYTE_1)
        val flags: Byte = FLAG_USER_PRESENT
        val counter = TEST_COUNTER_12345
        val aaguid = testBytes(AAGUID_SIZE_16, DUMMY_BYTE_2)
        val credentialId = testBytes(CRED_ID_SIZE_16, DUMMY_BYTE_3)
        val publicKeyBytes = testBytes(PUB_KEY_SIZE_32, DUMMY_BYTE_4)

        val encoded =
            trackBuffer(
                cborCodec.encodeAttestationObject(
                    rpIdHash,
                    flags,
                    counter,
                    aaguid,
                    credentialId,
                    publicKeyBytes,
                ),
            )

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `test CBOR authenticator data encoding`() {
        val rpIdHash = testBytes(HASH_SIZE_32, DUMMY_BYTE_1)
        val flags: Byte = FLAG_USER_PRESENT
        val counter = TEST_COUNTER_12345

        val encoded = trackBuffer(cborCodec.encodeAuthenticatorData(rpIdHash, flags, counter))

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `test CBOR client data JSON encoding`() {
        val type = "webauthn.create"
        val challenge = testBytes(CHALLENGE_SIZE_32, DUMMY_BYTE_5)
        val origin = "https://example.com"

        val encoded = trackBuffer(cborCodec.encodeClientDataJson(type, challenge, origin))

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }
}
