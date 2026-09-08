package com.chimali.feature.vault.internal

import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    private val vaultService: VaultService = mockk(relaxed = true)
    private val clipboardManager: ClipboardManagerService = mockk(relaxed = true)
    private val vaultCryptoService: VaultCryptoService = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: VaultViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { vaultService.getItems(any()) } returns Outcome.Success(emptyList())
        coEvery { vaultService.getLabels() } returns Outcome.Success(emptyList())

        viewModel = VaultViewModel(vaultService, clipboardManager, vaultCryptoService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `savePassword encrypts and saves item`() =
        runTest {
            val payload =
                PasswordPayload(
                    title = "Google",
                    username = "user".toCharArray(),
                    password = "pwd".toCharArray(),
                    uri = "https://google.com",
                )
            val vaultItem =
                VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.PASSWORD,
                    title = "Google",
                    payload = byteArrayOf(1, 2, 3),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )

            coEvery { vaultCryptoService.encryptPassword(any(), any(), any()) } returns Outcome.Success(vaultItem)
            coEvery { vaultService.saveItem(vaultItem) } returns Outcome.Success(Unit)

            viewModel.processIntent(VaultIntent.SavePassword(payload = payload))
            advanceUntilIdle()

            coVerify { vaultCryptoService.encryptPassword(null, payload, any()) }
            coVerify { vaultService.saveItem(vaultItem) }
        }

    @Test
    fun `decryptItem decrypts password and updates state`() =
        runTest {
            val itemId = UUID.randomUUID()
            val vaultItem =
                VaultItem(
                    id = itemId,
                    type = VaultType.PASSWORD,
                    title = "Google",
                    payload = byteArrayOf(1, 2, 3),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            val decryptedPayload =
                PasswordPayload(
                    title = "Google",
                    username = "user".toCharArray(),
                    password = "pwd".toCharArray(),
                    uri = "https://google.com",
                )

            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(vaultItem))
            coEvery { vaultCryptoService.decryptPassword(vaultItem) } returns Outcome.Success(decryptedPayload)

            viewModel.processIntent(VaultIntent.LoadItems())
            advanceUntilIdle()

            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(decryptedPayload, viewModel.state.value.selectedPasswordPayload)
            assertEquals(vaultItem, viewModel.state.value.selectedItem)
        }

    @Test
    fun `deleteItem calls vaultService deleteItem and clears selection`() =
        runTest {
            val itemId = UUID.randomUUID()
            coEvery { vaultService.deleteItem(itemId) } returns Outcome.Success(Unit)

            viewModel.processIntent(VaultIntent.DeleteItem(itemId))
            advanceUntilIdle()

            coVerify { vaultService.deleteItem(itemId) }
            assertNull(viewModel.state.value.selectedItem)
            assertNull(viewModel.state.value.selectedPasswordPayload)
        }

    @Test
    fun `saveCreditCard and decryptCreditCard work as expected`() =
        runTest {
            val payload =
                CreditCardPayload(
                    title = "Visa",
                    cardholderName = "Alice".toCharArray(),
                    cardNumber = "1234".toCharArray(),
                    expirationDate = "10/30",
                    cvv = "123".toCharArray(),
                )
            val itemId = UUID.randomUUID()
            val vaultItem =
                VaultItem(
                    id = itemId,
                    type = VaultType.CREDIT_CARD,
                    title = "Visa",
                    payload = byteArrayOf(4, 5, 6),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )

            coEvery { vaultCryptoService.encryptCreditCard(any(), any(), any()) } returns Outcome.Success(vaultItem)
            coEvery { vaultService.saveItem(vaultItem) } returns Outcome.Success(Unit)
            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(vaultItem))
            coEvery { vaultCryptoService.decryptCreditCard(vaultItem) } returns Outcome.Success(payload)

            viewModel.processIntent(VaultIntent.SaveCreditCard(payload = payload))
            advanceUntilIdle()
            coVerify { vaultService.saveItem(vaultItem) }

            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(payload, viewModel.state.value.selectedCreditCardPayload)
        }

    @Test
    fun `saveSecureNote and decryptSecureNote work as expected`() =
        runTest {
            val payload =
                SecureNotePayload(
                    title = "Secret",
                    content = "Hello".toCharArray(),
                )
            val itemId = UUID.randomUUID()
            val vaultItem =
                VaultItem(
                    id = itemId,
                    type = VaultType.NOTE,
                    title = "Secret",
                    payload = byteArrayOf(7, 8, 9),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )

            coEvery { vaultCryptoService.encryptSecureNote(any(), any(), any()) } returns Outcome.Success(vaultItem)
            coEvery { vaultService.saveItem(vaultItem) } returns Outcome.Success(Unit)
            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(vaultItem))
            coEvery { vaultCryptoService.decryptSecureNote(vaultItem) } returns Outcome.Success(payload)

            viewModel.processIntent(VaultIntent.SaveSecureNote(payload = payload))
            advanceUntilIdle()
            coVerify { vaultService.saveItem(vaultItem) }

            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(payload, viewModel.state.value.selectedSecureNotePayload)
        }
}
