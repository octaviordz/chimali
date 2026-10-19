package com.chimali.fido2.data.service

import com.chimali.core.security.api.EncryptedMetadataService
import com.chimali.core.security.api.MetadataLookupTokenService
import com.chimali.core.security.memory.ErasableCharBuffer
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.CharacterCodingException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** @see NFR-SEC-041 and TR-AUTH-031 */
class CredentialMetadataProtectionServiceOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `relying party metadata serializes into erasable buffers`() {
        val owners = mutableListOf<ErasableCharBuffer>()
        val arrays = mutableListOf<CharArray>()
        val field = ErasableCharBuffer::class.java.getDeclaredField("characters").apply { isAccessible = true }
        val encryption = mockk<EncryptedMetadataService>()
        var plaintext: ByteArray? = null
        var plaintextSnapshot: ByteArray? = null
        var aad: ByteArray? = null
        every { encryption.encrypt(any(), any()) } answers {
            plaintext = firstArg<ByteArray>()
            plaintextSnapshot = firstArg<ByteArray>().copyOf()
            aad = secondArg()
            byteArrayOf(1, 2, 3)
        }
        val service =
            CredentialMetadataProtectionService(mockk(relaxed = true), encryption) {
                ErasableCharBuffer(2).also {
                    owners.add(it)
                    arrays.add(field.get(it) as CharArray)
                }
            }

        assertContentEquals(
            byteArrayOf(1, 2, 3),
            service.encryptRelyingPartyMetadata("example.com", "A\"cc"),
        )

        val encryptedInput = requireNotNull(plaintext)
        assertContentEquals("""{"id":"example.com","name":"A\"cc"}""".toByteArray(), plaintextSnapshot)
        assertContentEquals(ByteArray(encryptedInput.size), encryptedInput)
        assertEquals(null, aad)
        owners.forEach {
            assertEquals(0, it.length)
            val current = field.get(it) as CharArray
            assertContentEquals(CharArray(current.size), current)
        }
        arrays.forEach { assertContentEquals(CharArray(it.size), it) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `partial parsing and encryption cancellation erase retained character owners`() {
        val owners = mutableListOf<ErasableCharBuffer>()
        val initialArrays = mutableListOf<CharArray>()
        val field = ErasableCharBuffer::class.java.getDeclaredField("characters").apply { isAccessible = true }
        val encryption = mockk<EncryptedMetadataService>()
        var encryptInput: ByteArray? = null
        every { encryption.encrypt(any(), any()) } answers {
            encryptInput = firstArg()
            throw CancellationException("synthetic encryption cancellation")
        }
        val partialJson = """{"rpId":"example.com","userId":"partial\x"}""".toByteArray()
        every { encryption.decrypt(any(), any()) } returns partialJson
        val service =
            CredentialMetadataProtectionService(mockk(relaxed = true), encryption) {
                ErasableCharBuffer(2).also {
                    owners.add(it)
                    initialArrays.add(field.get(it) as CharArray)
                }
            }

        assertFailsWith<CancellationException> { service.encryptCredentialMetadata("example.com", "用戶".repeat(40)) }
        assertFailsWith<IllegalArgumentException> { service.decryptCredentialMetadata(byteArrayOf(1), "example.com") }

        assertEquals(5, owners.size) // serialization, two names and two values (the last is partial)
        owners.forEach {
            assertEquals(0, it.length)
            val current = field.get(it) as CharArray
            assertContentEquals(CharArray(current.size), current)
        }
        initialArrays.forEach { assertContentEquals(CharArray(it.size), it) }
        assertContentEquals(ByteArray(requireNotNull(encryptInput).size), encryptInput)
        assertContentEquals(ByteArray(partialJson.size), partialJson)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["raw-control", "duplicate-field", "missing-field", "trailing-data", "bad-escape"])
    fun `malformed JSON is rejected and decrypted bytes are erased`(failure: String) {
        val json =
            when (failure) {
                "raw-control" -> """{"rpId":"example.com","userId":"first${'\n'}second"}"""
                "duplicate-field" -> """{"rpId":"example.com","rpId":"other.com","userId":"user"}"""
                "missing-field" -> """{"rpId":"example.com"}"""
                "trailing-data" -> """{"rpId":"example.com","userId":"user"}x"""
                "bad-escape" -> """{"rpId":"example.com","userId":"\x"}"""
                else -> error("Unknown fixture")
            }
        val plaintext = json.toByteArray()
        val encryption = mockk<EncryptedMetadataService>()
        every { encryption.decrypt(any(), any()) } returns plaintext
        val service = CredentialMetadataProtectionService(mockk(relaxed = true), encryption)

        assertFailsWith<IllegalArgumentException> { service.decryptCredentialMetadata(byteArrayOf(1), "example.com") }
        assertContentEquals(ByteArray(plaintext.size), plaintext)
    }

    @Test
    fun `credential metadata encryption and decryption erase owned plaintext buffers`() {
        val encryption = mockk<EncryptedMetadataService>()
        val lookup = mockk<MetadataLookupTokenService>(relaxed = true)
        var encryptedInput: ByteArray? = null
        var storedPayload: ByteArray? = null
        every { encryption.encrypt(any(), any()) } answers {
            encryptedInput = firstArg<ByteArray>()
            storedPayload = firstArg<ByteArray>().copyOf()
            byteArrayOf(7, 8, 9)
        }
        every { encryption.decrypt(any(), any()) } answers {
            requireNotNull(storedPayload).copyOf().also { storedPayload = it }
        }
        val service = CredentialMetadataProtectionService(lookup, encryption)

        val ciphertext = service.encryptCredentialMetadata("ex", "1")
        assertContentEquals(byteArrayOf(7, 8, 9), ciphertext)
        val encryptedPlaintext = requireNotNull(encryptedInput)
        assertContentEquals(ByteArray(encryptedPlaintext.size), encryptedPlaintext)

        val metadata = service.decryptCredentialMetadata(ciphertext, "ex")
        assertContentEquals("ex".toCharArray(), metadata.rpId)
        assertContentEquals("1".toCharArray(), metadata.userId)
        metadata.erase()
        assertContentEquals(CharArray(2), metadata.rpId)
        assertContentEquals(CharArray(1), metadata.userId)
        val decryptedPlaintext = requireNotNull(storedPayload)
        assertContentEquals(ByteArray(decryptedPlaintext.size), decryptedPlaintext)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `long escaped unicode metadata round trips with independently erasable field owners`() {
        val rpId = "example.com"
        val userId = "\\\"\b\u000C\n\r\t用戶😃".repeat(90)
        val encryption = mockk<EncryptedMetadataService>()
        var encoded: ByteArray? = null
        var decoded: ByteArray? = null
        val aadOwners = mutableListOf<ByteArray>()
        every { encryption.encrypt(any(), any()) } answers {
            aadOwners.add(secondArg())
            firstArg<ByteArray>().also { encoded = it }.copyOf()
        }
        every { encryption.decrypt(any(), any()) } answers {
            aadOwners.add(secondArg())
            firstArg<ByteArray>().copyOf().also { decoded = it }
        }
        val service = CredentialMetadataProtectionService(mockk(relaxed = true), encryption)

        val ciphertext = service.encryptCredentialMetadata(rpId, userId)
        val fields = service.decryptCredentialMetadata(ciphertext, rpId)
        try {
            assertContentEquals(rpId.toCharArray(), fields.rpId)
            assertContentEquals(userId.toCharArray(), fields.userId)
            assertEquals(true, fields.matches(rpId, userId))
            assertEquals(false, fields.matches(rpId, "$userId-different"))
            assertContentEquals(ByteArray(requireNotNull(encoded).size), encoded)
            assertContentEquals(ByteArray(requireNotNull(decoded).size), decoded)
            aadOwners.forEach { assertContentEquals(ByteArray(it.size), it) }
        } finally {
            fields.erase()
            ciphertext.fill(0)
        }
        assertContentEquals(CharArray(fields.rpId.size), fields.rpId)
        assertContentEquals(CharArray(fields.userId.size), fields.userId)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `escaped unicode codepoints parse without immutable intermediate fields`() {
        val plaintext = """{"userId":"\u7528\u6237","rpId":"example.com"}""".toByteArray()
        val encryption = mockk<EncryptedMetadataService>()
        every { encryption.decrypt(any(), any()) } returns plaintext
        val service = CredentialMetadataProtectionService(mockk(relaxed = true), encryption)
        val fields = service.decryptCredentialMetadata(byteArrayOf(1), "example.com")
        try {
            assertContentEquals("用户".toCharArray(), fields.userId)
            assertContentEquals(ByteArray(plaintext.size), plaintext)
        } finally {
            fields.erase()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed encryption and malformed decryption still erase owned buffers`() {
        val encryption = mockk<EncryptedMetadataService>()
        var encryptFailure = true
        var encryptionInput: ByteArray? = null
        var decryptionInput: ByteArray? = null
        every { encryption.encrypt(any(), any()) } answers {
            encryptionInput = firstArg<ByteArray>()
            check(!encryptFailure) { "synthetic encryption failure" }
            byteArrayOf(1)
        }
        every { encryption.decrypt(any(), any()) } answers {
            byteArrayOf(0xC3.toByte(), 0x28).also { decryptionInput = it }
        }
        val service = CredentialMetadataProtectionService(mockk(relaxed = true), encryption)

        assertFailsWith<IllegalStateException> { service.encryptCredentialMetadata("example.com", "user") }
        val failedPlaintext = requireNotNull(encryptionInput)
        assertContentEquals(ByteArray(failedPlaintext.size), failedPlaintext)
        encryptFailure = false
        assertFailsWith<CharacterCodingException> { service.decryptCredentialMetadata(byteArrayOf(1), "example.com") }
        val decrypted = requireNotNull(decryptionInput)
        assertContentEquals(ByteArray(decrypted.size), decrypted)
    }
}
