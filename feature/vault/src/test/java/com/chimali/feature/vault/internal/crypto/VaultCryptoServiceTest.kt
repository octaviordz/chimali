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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VaultCryptoServiceTest {
    private val encryptionManager: EncryptionManager = mockk()
    private val eventStoreKeyProvider: EventStoreKeyProvider = mockk()
    private lateinit var service: VaultCryptoService

    private val testKey = ByteArray(32) { 1 }
    private var capturedEncryptionKey: ByteArray? = null

    @Before
    fun setup() {
        coEvery { eventStoreKeyProvider.getEventStoreKey("chimali_vault_payload_v1") } returns testKey.copyOf()
        // Echo mock encryption / decryption: prepend 12 bytes IV on encrypt, strip on decrypt
        every { encryptionManager.encrypt(any(), any()) } answers {
            val plaintext = firstArg<ByteArray>()
            capturedEncryptionKey = secondArg<ByteArray>()
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
            assertArrayEquals("Test Bank".toCharArray(), vaultItem.title)
            assertEquals(VaultType.PASSWORD, vaultItem.type)

            val decryptResult = service.decryptPassword(vaultItem)
            assertTrue(decryptResult is Outcome.Success)

            val decrypted = (decryptResult as Outcome.Success).data
            assertArrayEquals("Test Bank".toCharArray(), decrypted.title)
            assertArrayEquals("user".toCharArray(), decrypted.username)
            assertArrayEquals("pwd".toCharArray(), decrypted.password)
            assertArrayEquals("https://bank.com".toCharArray(), decrypted.uri)
            assertArrayEquals("note".toCharArray(), decrypted.notes)
            assertEquals(1, decrypted.customFields?.size)
            assertArrayEquals("Security".toCharArray(), decrypted.customFields?.first()?.name)
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
            assertArrayEquals("Visa".toCharArray(), vaultItem.title)
            assertEquals(VaultType.CREDIT_CARD, vaultItem.type)

            val decryptResult = service.decryptCreditCard(vaultItem)
            assertTrue(decryptResult is Outcome.Success)

            val decrypted = (decryptResult as Outcome.Success).data
            assertArrayEquals("Visa".toCharArray(), decrypted.title)
            assertArrayEquals("Alice".toCharArray(), decrypted.cardholderName)
            assertArrayEquals("1234567812345678".toCharArray(), decrypted.cardNumber)
            assertArrayEquals("12/28".toCharArray(), decrypted.expirationDate)
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
            assertArrayEquals("My Secret Note".toCharArray(), vaultItem.title)
            assertEquals(VaultType.NOTE, vaultItem.type)

            val decryptResult = service.decryptSecureNote(vaultItem)
            assertTrue(decryptResult is Outcome.Success)

            val decrypted = (decryptResult as Outcome.Success).data
            assertArrayEquals("My Secret Note".toCharArray(), decrypted.title)
            assertArrayEquals("Secret body text".toCharArray(), decrypted.content)
        }

    @Test
    fun `key provider failure returns safe encryption error`() =
        runBlocking {
            coEvery { eventStoreKeyProvider.getEventStoreKey(any()) } throws IllegalStateException("seed missing")
            val password =
                PasswordPayload(
                    title = "Password",
                    username = "user".toCharArray(),
                    password = "secret".toCharArray(),
                    uri = "",
                )
            val card =
                CreditCardPayload(
                    title = "Card",
                    cardholderName = "Name".toCharArray(),
                    cardNumber = "1234".toCharArray(),
                    expirationDate = "12/28",
                    cvv = "999".toCharArray(),
                )
            val note = SecureNotePayload("Note", "body".toCharArray())

            val passwordResult = service.encryptPassword(null, password, UUID.randomUUID())
            val cardResult = service.encryptCreditCard(null, card, UUID.randomUUID())
            val noteResult = service.encryptSecureNote(null, note, UUID.randomUUID())

            listOf(passwordResult, cardResult, noteResult).forEach { result ->
                assertTrue(result is Outcome.Error)
                assertEquals("Vault encryption is not ready", (result as Outcome.Error).error.message)
            }
            assertTrue(password.username.all { it == '\u0000' })
            assertTrue(password.password.all { it == '\u0000' })
            assertTrue(card.cardholderName.all { it == '\u0000' })
            assertTrue(card.cardNumber.all { it == '\u0000' })
            assertTrue(card.cvv.all { it == '\u0000' })
            assertTrue(note.content.all { it == '\u0000' })
        }

    @Test
    fun `encryption key is cleared after encryption`() =
        runBlocking {
            val payload = SecureNotePayload("Note", "body".toCharArray())
            val result =
                service.encryptSecureNote(
                    null,
                    payload,
                    UUID.randomUUID(),
                )

            assertTrue(result is Outcome.Success)
            assertTrue(capturedEncryptionKey!!.all { it == 0.toByte() })
            assertTrue(payload.content.all { it == '\u0000' })
        }

    @Test
    fun `clearMemory overwrites every mutable secret buffer`() {
        val password =
            PasswordPayload(
                title = "Bank",
                username = "user".toCharArray(),
                password = "secret".toCharArray(),
                uri = "https://example.test",
                notes = "note".toCharArray(),
                customFields = listOf(CustomField("answer", "value".toCharArray(), true)),
            )
        val card =
            CreditCardPayload(
                title = "Card",
                cardholderName = "Alice".toCharArray(),
                cardNumber = "4111111111111111".toCharArray(),
                expirationDate = "12/28",
                cvv = "123".toCharArray(),
                notes = "billing".toCharArray(),
            )
        val note = SecureNotePayload("Note", "body".toCharArray())

        password.clearMemory()
        card.clearMemory()
        note.clearMemory()

        assertTrue(password.username.all { it == '\u0000' })
        assertTrue(password.password.all { it == '\u0000' })
        assertTrue(password.notes!!.all { it == '\u0000' })
        assertTrue(
            password
                .customFields!!
                .single()
                .value
                .all { it == '\u0000' },
        )
        assertTrue(card.cardNumber.all { it == '\u0000' })
        assertTrue(card.cvv.all { it == '\u0000' })
        assertTrue(card.notes!!.all { it == '\u0000' })
        assertTrue(note.content.all { it == '\u0000' })
    }

    @Test
    fun `malformed decrypted payload returns safe error`() =
        runBlocking {
            every { encryptionManager.decrypt(any(), any()) } returns ByteArray(12) { 0 } + "not-json".toByteArray()

            val item =
                com.chimali.feature.vault.api.VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.PASSWORD,
                    title = "Broken",
                    payload = byteArrayOf(1),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )

            val result = service.decryptPassword(item)

            assertTrue(result is Outcome.Error)
            assertEquals("Unable to open password payload", (result as Outcome.Error).error.message)
        }

    @Test
    fun `cancellation from key provider is propagated`() {
        runBlocking {
            coEvery { eventStoreKeyProvider.getEventStoreKey(any()) } throws CancellationException()
            try {
                service.encryptSecureNote(null, SecureNotePayload("Note", charArrayOf()), UUID.randomUUID())
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                // Expected: cancellation must not be converted into an Outcome.Error.
            }
        }
    }
}
