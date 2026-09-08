package com.chimali.feature.vault.internal.crypto

import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultCryptoServiceTest {
    private val encryptionManager: EncryptionManager = mockk()
    private val eventStoreKeyProvider: EventStoreKeyProvider = mockk()
    private lateinit var service: VaultCryptoService

    private val testKey = ByteArray(32) { 1 }

    @Before
    fun setup() {
        coEvery { eventStoreKeyProvider.getEventStoreKey("chimali_vault_payload_v1") } returns testKey.copyOf()
        // Echo mock encryption / decryption: prepend 12 bytes IV on encrypt, strip on decrypt
        every { encryptionManager.encrypt(any(), any()) } answers {
            val plaintext = firstArg<ByteArray>()
            ByteArray(12) { 0 } + plaintext
        }
        every { encryptionManager.decrypt(any(), any()) } answers {
            val ciphertext = firstArg<ByteArray>()
            ciphertext.copyOfRange(12, ciphertext.size)
        }

        service = VaultCryptoServiceImpl(encryptionManager, eventStoreKeyProvider)
    }

    @Test
    fun `encrypt and decrypt PasswordPayload succeeds`() =
        runBlocking {
            val payload =
                PasswordPayload(
                    title = "Test Bank",
                    username = "user".toCharArray(),
                    password = "pwd".toCharArray(),
                    uri = "https://bank.com",
                    notes = "note".toCharArray(),
                    customFields = listOf(CustomField("Security", "ans".toCharArray(), true)),
                )
            val identityId = UUID.randomUUID()

            val encryptResult = service.encryptPassword(null, payload, identityId)
            assertTrue("encryptResult was: $encryptResult", encryptResult is Outcome.Success)

            val vaultItem = (encryptResult as Outcome.Success).data
            assertEquals("Test Bank", vaultItem.title)
            assertEquals(VaultType.PASSWORD, vaultItem.type)

            val decryptResult = service.decryptPassword(vaultItem)
            assertTrue(decryptResult is Outcome.Success)

            val decrypted = (decryptResult as Outcome.Success).data
            assertEquals("Test Bank", decrypted.title)
            assertArrayEquals("user".toCharArray(), decrypted.username)
            assertArrayEquals("pwd".toCharArray(), decrypted.password)
            assertEquals("https://bank.com", decrypted.uri)
            assertArrayEquals("note".toCharArray(), decrypted.notes)
            assertEquals(1, decrypted.customFields?.size)
            assertEquals("Security", decrypted.customFields?.first()?.name)
        }

    @Test
    fun `encrypt and decrypt CreditCardPayload succeeds`() =
        runBlocking {
            val payload =
                CreditCardPayload(
                    title = "Visa",
                    cardholderName = "Alice".toCharArray(),
                    cardNumber = "1234567812345678".toCharArray(),
                    expirationDate = "12/28",
                    cvv = "999".toCharArray(),
                )
            val identityId = UUID.randomUUID()

            val encryptResult = service.encryptCreditCard(null, payload, identityId)
            assertTrue(encryptResult is Outcome.Success)

            val vaultItem = (encryptResult as Outcome.Success).data
            assertEquals("Visa", vaultItem.title)
            assertEquals(VaultType.CREDIT_CARD, vaultItem.type)

            val decryptResult = service.decryptCreditCard(vaultItem)
            assertTrue(decryptResult is Outcome.Success)

            val decrypted = (decryptResult as Outcome.Success).data
            assertEquals("Visa", decrypted.title)
            assertArrayEquals("Alice".toCharArray(), decrypted.cardholderName)
            assertArrayEquals("1234567812345678".toCharArray(), decrypted.cardNumber)
            assertEquals("12/28", decrypted.expirationDate)
            assertArrayEquals("999".toCharArray(), decrypted.cvv)
        }

    @Test
    fun `encrypt and decrypt SecureNotePayload succeeds`() =
        runBlocking {
            val payload =
                SecureNotePayload(
                    title = "My Secret Note",
                    content = "Secret body text".toCharArray(),
                )
            val identityId = UUID.randomUUID()

            val encryptResult = service.encryptSecureNote(null, payload, identityId)
            assertTrue(encryptResult is Outcome.Success)

            val vaultItem = (encryptResult as Outcome.Success).data
            assertEquals("My Secret Note", vaultItem.title)
            assertEquals(VaultType.NOTE, vaultItem.type)

            val decryptResult = service.decryptSecureNote(vaultItem)
            assertTrue(decryptResult is Outcome.Success)

            val decrypted = (decryptResult as Outcome.Success).data
            assertEquals("My Secret Note", decrypted.title)
            assertArrayEquals("Secret body text".toCharArray(), decrypted.content)
        }
}
