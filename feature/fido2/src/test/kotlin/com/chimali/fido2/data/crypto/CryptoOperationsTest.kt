package com.chimali.fido2.data.crypto

import com.chimali.core.domain.valueobject.CredentialId
import java.security.MessageDigest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * T061 � Unit tests for Core Crypto operations.
 *
 * Tests run on JVM (no Android dependency needed since these classes use only
 * java.security APIs for the testable public contract).
 * Key-generation and signing against Android KeyStore are integration tests
 * and are covered in AndroidKeyStoreWrapperTest (instrumented).
 */
class CryptoOperationsTest {
    // -- ClientDataHashService -------------------------------------------------

    private lateinit var clientDataHashService: ClientDataHashService

    @BeforeTest
    fun setUp() {
        clientDataHashService = ClientDataHashService()
    }

    private companion object {
        private const val HASH_SIZE_32 = 32
        private const val CHALLENGE_SIZE_16 = 16
        private const val CHALLENGE_SIZE_32 = 32
        private const val PUB_KEY_SIZE_65 = 65
        private const val COSE_ES256_VAL = -7
        private const val DUMMY_BYTE_AB = 0xAB.toByte()
        private const val DUMMY_BYTE_AA = 0xAA.toByte()
        private const val DUMMY_BYTE_BB = 0xBB.toByte()
        private const val DUMMY_BYTE_11 = 0x11.toByte()
        private const val DUMMY_BYTE_22 = 0x22.toByte()
    }

    // -- ClientDataHashService tests -------------------------------------------

    @Test
    fun `computeHash returns 32 bytes`() {
        val hash =
            clientDataHashService.computeHash(
                type = "webauthn.create",
                challenge = ByteArray(CHALLENGE_SIZE_16) { DUMMY_BYTE_AB },
                origin = "https://example.com",
                crossOrigin = false,
            )
        assertEquals(HASH_SIZE_32, hash.size)
    }

    @Test
    fun `computeHash is deterministic for same inputs`() {
        val challenge = ByteArray(CHALLENGE_SIZE_32) { it.toByte() }
        val h1 = clientDataHashService.computeHash("webauthn.create", challenge, "https://example.com")
        val h2 = clientDataHashService.computeHash("webauthn.create", challenge, "https://example.com")
        assertContentEquals(h1, h2)
    }

    @Test
    fun `computeHash differs for different types`() {
        val challenge = ByteArray(CHALLENGE_SIZE_16) { DUMMY_BYTE_11 }
        val create = clientDataHashService.computeHash("webauthn.create", challenge, "https://example.com")
        val get = clientDataHashService.computeHash("webauthn.get", challenge, "https://example.com")
        assertFalse(create.contentEquals(get))
    }

    @Test
    fun `computeHash differs for different origins`() {
        val challenge = ByteArray(CHALLENGE_SIZE_16) { DUMMY_BYTE_22 }
        val h1 = clientDataHashService.computeHash("webauthn.create", challenge, "https://example.com")
        val h2 = clientDataHashService.computeHash("webauthn.create", challenge, "https://other.com")
        assertFalse(h1.contentEquals(h2))
    }

    @Test
    fun `computeHash differs for different challenges`() {
        val h1 =
            clientDataHashService.computeHash(
                "webauthn.create",
                ByteArray(CHALLENGE_SIZE_16) { DUMMY_BYTE_AA },
                "https://example.com",
            )
        val h2 =
            clientDataHashService.computeHash(
                "webauthn.create",
                ByteArray(CHALLENGE_SIZE_16) { DUMMY_BYTE_BB },
                "https://example.com",
            )
        assertFalse(h1.contentEquals(h2))
    }

    @Test
    fun `computeHashFromJson matches SHA-256 of same string`() {
        val json =
            """{"type":"webauthn.create","challenge":"AAEC",""" +
                """"origin":"https://example.com","crossOrigin":false}"""
        val expected = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        val actual = clientDataHashService.computeHashFromJson(json)
        assertContentEquals(expected, actual)
    }

    @Test
    fun `buildClientDataJson produces valid JSON with base64url challenge`() {
        val challenge = byteArrayOf(0x00, 0x01, 0x02.toByte())
        val json =
            clientDataHashService.buildClientDataJson(
                type = "webauthn.create",
                challenge = challenge,
                origin = "https://example.com",
                crossOrigin = false,
            )
        assertTrue(json.startsWith("{"), "Must start with {")
        assertTrue(json.contains("\"type\":\"webauthn.create\""))
        assertTrue(json.contains("\"origin\":\"https://example.com\""))
        assertTrue(json.contains("\"crossOrigin\":false"))
        // AAE= without padding → AAEC→AAE (padding-free base64url should not contain '=')
        assertFalse(json.contains("="), "Base64URL must have no padding")
    }

    // -- ClientDataHashService.Companion (rpIdHash) ---------------------------

    @Test
    fun `rpIdHash produces 32-byte SHA-256 of rpId`() {
        val rpId = "example.com"
        val expected = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8))
        val actual = ClientDataHashService.rpIdHash(rpId)
        assertContentEquals(expected, actual)
    }

    @Test
    fun `sha256 companion helper is deterministic`() {
        val data = "test data".toByteArray()
        val h1 = ClientDataHashService.sha256(data)
        val h2 = ClientDataHashService.sha256(data)
        assertContentEquals(h1, h2)
        assertEquals(HASH_SIZE_32, h1.size)
    }

    @Test
    fun `sha256 companion helper matches MessageDigest`() {
        val data = "hello fido2".toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertContentEquals(expected, ClientDataHashService.sha256(data))
    }

    // -- Fido2CryptoService constants ------------------------------------------

    @Test
    fun `credentialAlias follows expected prefix`() {
        val credId = CredentialId.fromByteArray("abc123".encodeToByteArray())
        val alias = Fido2CryptoService.credentialAlias(credId)
        assertTrue(alias.startsWith("device-key/1179206706/"))
    }

    @Test
    fun `COSE_ES256 constant is minus 7`() {
        assertEquals(COSE_ES256_VAL, Fido2CryptoService.COSE_ES256)
    }

    // -- Fido2KeyPair ----------------------------------------------------------

    @Test
    fun `Fido2KeyPair equality based on alias and bytes`() {
        val bytes = ByteArray(PUB_KEY_SIZE_65) { it.toByte() }
        val kp1 = Fido2KeyPair("alias_a", bytes)
        val kp2 = Fido2KeyPair("alias_a", bytes.copyOf())
        val kp3 = Fido2KeyPair("alias_b", bytes)
        assertEquals(kp1, kp2)
        assertNotEquals(kp1, kp3)
    }

    @Test
    fun `Fido2KeyPair toString contains alias and length`() {
        val kp = Fido2KeyPair("my_alias", ByteArray(PUB_KEY_SIZE_65))
        val str = kp.toString()
        assertTrue(str.contains("my_alias"))
        assertTrue(str.contains(PUB_KEY_SIZE_65.toString()))
    }
}
