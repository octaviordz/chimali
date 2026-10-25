package com.chimali.fido2.data.crypto

import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.service.CredentialStorageService
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CredentialEncryptionServiceTest {
    private val mockStorage =
        mockk<CredentialStorageService>(relaxed = true) {
            coEvery { keyExists(any()) } returns true
        }
    private val service = CredentialEncryptionService(mockStorage)

    @Test
    fun `encrypt and decrypt round trip with associated data succeeds`() =
        runTest {
            val plaintext = "Secret credential payload".toByteArray(Charsets.UTF_8)
            val aad = "relying-party-id".toByteArray(Charsets.UTF_8)

            val encryptResult = service.encrypt(plaintext, aad)
            assertTrue(encryptResult is Outcome.Success)
            val encryptedData = encryptResult.data
            assertNotNull(encryptedData.data)
            assertEquals(12, encryptedData.iv.size)

            val decryptResult = service.decrypt(encryptedData, aad)
            assertTrue(decryptResult is Outcome.Success)
            val decrypted = decryptResult.data
            assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
        }

    @Test
    fun `encrypt and decrypt round trip without associated data succeeds`() =
        runTest {
            val plaintext = "Secret payload without AAD".toByteArray(Charsets.UTF_8)

            val encryptResult = service.encrypt(plaintext, null)
            assertTrue(encryptResult is Outcome.Success)
            val encryptedData = encryptResult.data

            val decryptResult = service.decrypt(encryptedData, null)
            assertTrue(decryptResult is Outcome.Success)
            val decrypted = decryptResult.data
            assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
        }

    @Test
    fun `decrypt with mismatched associated data returns error`() =
        runTest {
            val plaintext = "Sensitive data".toByteArray(Charsets.UTF_8)
            val correctAad = "correct-aad".toByteArray(Charsets.UTF_8)
            val wrongAad = "wrong-aad".toByteArray(Charsets.UTF_8)

            val encryptResult = service.encrypt(plaintext, correctAad)
            assertTrue(encryptResult is Outcome.Success)
            val encryptedData = encryptResult.data

            val decryptResult = service.decrypt(encryptedData, wrongAad)
            assertTrue(decryptResult is Outcome.Error)
        }

    @Test
    fun `encryptString and decryptString round trip succeeds`() =
        runTest {
            val original = "Test string to encrypt"
            val aad = "rp-origin".toByteArray(Charsets.UTF_8)

            val encryptResult = service.encryptString(original, aad)
            assertTrue(encryptResult is Outcome.Success)
            val base64String = encryptResult.data
            assertTrue(base64String.isNotEmpty())

            val decryptResult = service.decryptString(base64String, aad)
            assertTrue(decryptResult is Outcome.Success)
            val decrypted = decryptResult.data
            assertEquals(original, decrypted)
        }

    @Test
    fun `decryptString with payload shorter than IV returns error`() =
        runTest {
            val shortBase64 = "YWJj" // "abc" = 3 bytes

            val decryptResult = service.decryptString(shortBase64)
            assertTrue(decryptResult is Outcome.Error)
            assertEquals("Encrypted data too short", decryptResult.error.message)
        }

    @Test
    fun `decryptString with invalid base64 returns error`() =
        runTest {
            val invalidBase64 = "Not a valid base64 string @#$%"

            val decryptResult = service.decryptString(invalidBase64)
            assertTrue(decryptResult is Outcome.Error)
        }

    @Test
    fun `encryptCredentialMetadata and decryptCredentialMetadata round trip succeeds`() =
        runTest {
            val rpId = RpId("example.com")
            val userId = UserId.fromByteArray(byteArrayOf(1, 2, 3, 4, 5))
            val userName = "alice@example.com"
            val userDisplayName = "Alice Liddell"

            val encryptResult = service.encryptCredentialMetadata(rpId, userId, userName, userDisplayName)
            assertTrue(encryptResult is Outcome.Success)
            val encryptedMeta = encryptResult.data
            assertTrue(encryptedMeta.encryptedData.isNotEmpty())
            assertTrue(encryptedMeta.iv.isNotEmpty())
            assertTrue(encryptedMeta.rpIdHash.isNotEmpty())

            val decryptResult = service.decryptCredentialMetadata(encryptedMeta, rpId)
            assertTrue(decryptResult is Outcome.Success)
            val decryptedMeta = decryptResult.data
            assertEquals(rpId.value, decryptedMeta.rpId)
            assertEquals(userId.value, decryptedMeta.userId)
            assertEquals(userName, decryptedMeta.userName)
            assertEquals(userDisplayName, decryptedMeta.userDisplayName)
        }

    @Test
    fun `decryptCredentialMetadata with mismatched RP ID returns error`() =
        runTest {
            val rpId = RpId("example.com")
            val wrongRpId = RpId("evil.com")
            val userId = UserId.fromByteArray(byteArrayOf(1, 2, 3, 4, 5))

            val encryptResult = service.encryptCredentialMetadata(rpId, userId, "alice", "Alice")
            assertTrue(encryptResult is Outcome.Success)
            val encryptedMeta = encryptResult.data

            val decryptResult = service.decryptCredentialMetadata(encryptedMeta, wrongRpId)
            assertTrue(decryptResult is Outcome.Error)
            assertTrue(decryptResult.error.message.startsWith("RP ID mismatch"))
        }

    @Test
    fun `deriveCredentialKey derives functional AES key`() =
        runTest {
            val credentialId = CredentialId.fromByteArray(ByteArray(16) { (it + 1).toByte() })
            val rpId = RpId("secure.example.com")

            val deriveResult = service.deriveCredentialKey(credentialId, rpId)
            assertTrue(deriveResult is Outcome.Success)
            val key = deriveResult.data
            assertEquals("AES", key.algorithm)
            assertEquals(32, key.encoded.size) // 256 bits
        }

    @Test
    fun `encryptWithCredentialKey and decryptWithCredentialKey round trip succeeds`() =
        runTest {
            val credentialId = CredentialId.fromByteArray(ByteArray(16) { (it + 2).toByte() })
            val rpId = RpId("crypto.example.com")
            val data = "High security key data".toByteArray(Charsets.UTF_8)

            val encryptResult = service.encryptWithCredentialKey(data, credentialId, rpId)
            assertTrue(encryptResult is Outcome.Success)
            val encryptedData = encryptResult.data

            val decryptResult = service.decryptWithCredentialKey(encryptedData, credentialId, rpId)
            assertTrue(decryptResult is Outcome.Success)
            val decrypted = decryptResult.data
            assertEquals(String(data, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
        }

    @Test
    fun `rotateMasterKey succeeds`() =
        runTest {
            val result = service.rotateMasterKey()
            assertTrue(result is Outcome.Success)
        }

    @Test
    fun `credential metadata data classes verify contracts`() {
        val meta1 = CredentialEncryptionService.CredentialMetadata("rp1", "user1", "alice", "Alice A")
        val meta2 = CredentialEncryptionService.CredentialMetadata("rp1", "user1", "alice", "Alice A")
        val meta3 = CredentialEncryptionService.CredentialMetadata("rp2", "user2", "bob", "Bob B")

        assertEquals(meta1, meta2)
        assertEquals(meta1.hashCode(), meta2.hashCode())
        assertFalse(meta1 == meta3)
        assertTrue(meta1.toString().contains("Alice A"))

        val copied = meta1.copy(userDisplayName = "Alice Modified")
        assertEquals("Alice Modified", copied.userDisplayName)
        assertEquals("user1", copied.userId)

        val encMeta1 = CredentialEncryptionService.EncryptedCredentialMetadata("enc", "iv", "hash")
        val encMeta2 = CredentialEncryptionService.EncryptedCredentialMetadata("enc", "iv", "hash")
        val encMeta3 = CredentialEncryptionService.EncryptedCredentialMetadata("enc2", "iv", "hash")

        assertEquals(encMeta1, encMeta2)
        assertEquals(encMeta1.hashCode(), encMeta2.hashCode())
        assertFalse(encMeta1 == encMeta3)
        assertTrue(encMeta1.toString().contains("enc"))
    }
}
