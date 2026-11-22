package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModelStore
import com.chimali.core.clipboard.CleanupOrigin
import com.chimali.core.clipboard.CleanupOutcome
import com.chimali.core.clipboard.ClipboardCleanupStatus
import com.chimali.core.clipboard.ClipboardCopyReceipt
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.clipboard.PublicationStatus
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.ui.model.LabelUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        coEvery { vaultService.setItemLabels(any(), any()) } returns Outcome.Success(Unit)

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

            coVerify { vaultCryptoService.encryptPassword(any(), payload, any()) }
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
    fun `decryptItem clears unselected records and clears selected record when detail closes`() =
        runTest {
            val itemId = UUID.randomUUID()

            fun item(
                id: UUID,
                marker: Byte,
            ) = VaultItem(
                id = id,
                type = VaultType.PASSWORD,
                title = charArrayOf('t', marker.toInt().toChar()),
                payload = byteArrayOf(marker, marker),
                crdtState = byteArrayOf(marker),
                dateCreated = "",
                dateModified = "",
                lastBackedUpAt = null,
                identityId = UUID.randomUUID(),
            )

            val listSnapshot = item(itemId, 1)
            val selectedRecord = item(itemId, 2)
            val unrelatedRecord = item(UUID.randomUUID(), 3)
            val decryptedPayload =
                PasswordPayload(
                    title = "Selected",
                    username = "user".toCharArray(),
                    password = "password".toCharArray(),
                    uri = "https://example.test",
                )
            coEvery { vaultService.getItems(any()) } returnsMany
                listOf(
                    Outcome.Success(listOf(listSnapshot)),
                    Outcome.Success(listOf(selectedRecord, unrelatedRecord)),
                )
            coEvery { vaultCryptoService.decryptPassword(selectedRecord) } returns Outcome.Success(decryptedPayload)

            viewModel.processIntent(VaultIntent.LoadItems())
            advanceUntilIdle()
            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(0, unrelatedRecord.title.count { it != '\u0000' })
            assertEquals(0, unrelatedRecord.payload.count { it != 0.toByte() })
            assertEquals(0, unrelatedRecord.crdtState.count { it != 0.toByte() })
            assertEquals('t', selectedRecord.title[0])
            assertEquals(2.toByte(), selectedRecord.payload[0])
            assertEquals(2.toByte(), selectedRecord.crdtState[0])

            viewModel.processIntent(VaultIntent.ClearSelectedItem)

            assertEquals(0, selectedRecord.title.count { it != '\u0000' })
            assertEquals(0, selectedRecord.payload.count { it != 0.toByte() })
            assertEquals(0, selectedRecord.crdtState.count { it != 0.toByte() })
        }

    @Test
    fun `decryptItem clears fetched records and decrypted payload after selection becomes stale`() =
        runTest {
            val itemId = UUID.randomUUID()
            val selectedRecord =
                VaultItem(
                    id = itemId,
                    type = VaultType.PASSWORD,
                    title = "selected".toCharArray(),
                    payload = byteArrayOf(1, 2, 3),
                    crdtState = byteArrayOf(4, 5),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            val decryptedPayload =
                PasswordPayload(
                    title = "Selected",
                    username = "user".toCharArray(),
                    password = "password".toCharArray(),
                    uri = "https://example.test",
                )
            val decryptResult =
                CompletableDeferred<Outcome<PasswordPayload, com.chimali.core.common.result.DomainError>>()
            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(selectedRecord))
            coEvery { vaultCryptoService.decryptPassword(selectedRecord) } coAnswers { decryptResult.await() }
            coEvery { vaultService.getItemLabelIds(itemId) } returns Outcome.Success(emptyList())

            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            runCurrent()
            viewModel.processIntent(VaultIntent.ClearSelectedItem)
            decryptResult.complete(Outcome.Success(decryptedPayload))
            advanceUntilIdle()

            assertEquals(0, selectedRecord.title.count { it != '\u0000' })
            assertEquals(0, selectedRecord.payload.count { it != 0.toByte() })
            assertEquals(0, selectedRecord.crdtState.count { it != 0.toByte() })
            assertEquals(0, decryptedPayload.password.count { it != '\u0000' })
            assertNull(viewModel.state.value.selectedItem)
        }

    @Test
    fun `clearing ViewModel erases list item buffers`() =
        runTest {
            val title = "listed account".toCharArray()
            val payload = byteArrayOf(1, 2, 3)
            val crdtState = byteArrayOf(4, 5)
            val item =
                VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.PASSWORD,
                    title = title,
                    payload = payload,
                    crdtState = crdtState,
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(item))

            viewModel.processIntent(VaultIntent.LoadItems())
            advanceUntilIdle()
            val store = ViewModelStore()
            store.put("vault", viewModel)
            store.clear()

            assertEquals(0, title.count { it != '\u0000' })
            assertEquals(0, payload.count { it != 0.toByte() })
            assertEquals(0, crdtState.count { it != 0.toByte() })
        }

    @Test
    fun `clearing list items for background erases buffers and removes state references`() =
        runTest {
            val title = "listed account".toCharArray()
            val payload = byteArrayOf(1, 2, 3)
            val crdtState = byteArrayOf(4, 5)
            val item =
                VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.PASSWORD,
                    title = title,
                    payload = payload,
                    crdtState = crdtState,
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(item))
            coEvery { vaultService.getLabels() } returns
                Outcome.Success(listOf(LabelUiModel(UUID.randomUUID(), "synthetic category", "#123456")))

            viewModel.processIntent(VaultIntent.LoadItems())
            viewModel.processIntent(VaultIntent.LoadLabels)
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.labels.size)
            viewModel.processIntent(VaultIntent.ClearListData)

            assertEquals(emptyList<VaultItem>(), viewModel.state.value.items)
            assertEquals(emptyList<LabelUiModel>(), viewModel.state.value.labels)
            assertEquals(0, title.count { it != '\u0000' })
            assertEquals(0, payload.count { it != 0.toByte() })
            assertEquals(0, crdtState.count { it != 0.toByte() })
        }

    @Test
    fun `list response completing after background cleanup is erased instead of restored`() =
        runTest {
            val title = "late listed account".toCharArray()
            val payload = byteArrayOf(1, 2, 3)
            val crdtState = byteArrayOf(4, 5)
            val item =
                VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.PASSWORD,
                    title = title,
                    payload = payload,
                    crdtState = crdtState,
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            val pendingItems =
                CompletableDeferred<Outcome<List<VaultItem>, com.chimali.core.common.result.DomainError>>()
            val label = LabelUiModel(UUID.randomUUID(), "synthetic category", "#123456")
            val pendingLabels =
                CompletableDeferred<Outcome<List<LabelUiModel>, com.chimali.core.common.result.DomainError>>()
            coEvery { vaultService.getItems(any()) } coAnswers { pendingItems.await() }
            coEvery { vaultService.getLabels() } coAnswers { pendingLabels.await() }

            viewModel.processIntent(VaultIntent.LoadItems())
            viewModel.processIntent(VaultIntent.LoadLabels)
            runCurrent()
            viewModel.processIntent(VaultIntent.ClearListData)
            pendingItems.complete(Outcome.Success(listOf(item)))
            pendingLabels.complete(Outcome.Success(listOf(label)))
            advanceUntilIdle()

            assertEquals(emptyList<VaultItem>(), viewModel.state.value.items)
            assertEquals(emptyList<LabelUiModel>(), viewModel.state.value.labels)
            assertEquals(0, title.count { it != '\u0000' })
            assertEquals(0, payload.count { it != 0.toByte() })
            assertEquals(0, crdtState.count { it != 0.toByte() })
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
            val openedCreditCard = payload.copyForEditing()
            coEvery { vaultCryptoService.decryptCreditCard(vaultItem) } returns Outcome.Success(openedCreditCard)

            viewModel.processIntent(VaultIntent.SaveCreditCard(payload = payload))
            advanceUntilIdle()
            coVerify { vaultService.saveItem(vaultItem) }

            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(openedCreditCard, viewModel.state.value.selectedCreditCardPayload)
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
            val openedSecureNote = payload.copyForEditing()
            coEvery { vaultCryptoService.decryptSecureNote(vaultItem) } returns Outcome.Success(openedSecureNote)

            viewModel.processIntent(VaultIntent.SaveSecureNote(payload = payload))
            advanceUntilIdle()
            coVerify { vaultService.saveItem(vaultItem) }

            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(openedSecureNote, viewModel.state.value.selectedSecureNotePayload)
        }

    @Test
    fun `duplicate password saves are ignored while first save is pending`() =
        runTest {
            val payload = PasswordPayload("Vault", charArrayOf(), "secret".toCharArray(), "")
            val result = CompletableDeferred<Outcome<VaultItem, com.chimali.core.common.result.DomainError>>()
            coEvery { vaultCryptoService.encryptPassword(any(), any(), any()) } coAnswers { result.await() }

            viewModel.processIntent(VaultIntent.SavePassword(payload = payload))
            runCurrent()
            viewModel.processIntent(VaultIntent.SavePassword(payload = payload))

            assertEquals(com.chimali.feature.vault.api.VaultMutationState.PENDING, viewModel.state.value.mutationState)
            coVerify(exactly = 1) { vaultCryptoService.encryptPassword(any(), any(), any()) }

            result.complete(
                Outcome.Error(
                    com.chimali.core.common.result.DomainError
                        .CryptoError("failed"),
                ),
            )
            advanceUntilIdle()
        }

    @Test
    fun `failed password save retains error state for retry`() =
        runTest {
            val payload = PasswordPayload("Vault", charArrayOf(), "secret".toCharArray(), "")
            coEvery {
                vaultCryptoService.encryptPassword(any(), any(), any())
            } returns
                Outcome.Error(
                    com.chimali.core.common.result.DomainError
                        .CryptoError("VaultPayloadMarker private-profile-path"),
                )

            viewModel.processIntent(VaultIntent.SavePassword(payload = payload))
            advanceUntilIdle()

            assertEquals(com.chimali.feature.vault.api.VaultMutationState.FAILED, viewModel.state.value.mutationState)
            assertEquals("Unable to complete the requested vault operation.", viewModel.state.value.errorMessage)
            assertFalse(
                viewModel.state.value.errorMessage
                    .orEmpty()
                    .contains("VaultPayloadMarker"),
            )
        }

    @Test
    fun `retrying an edit preserves the existing identity`() =
        runTest {
            val itemId = UUID.randomUUID()
            val payload = PasswordPayload("Vault", charArrayOf(), "secret".toCharArray(), "")
            val item =
                VaultItem(
                    id = itemId,
                    type = VaultType.PASSWORD,
                    title = "Vault",
                    payload = byteArrayOf(1),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            coEvery {
                vaultCryptoService.encryptPassword(itemId, payload, any())
            } returnsMany
                listOf(
                    Outcome.Error(
                        com.chimali.core.common.result.DomainError
                            .CryptoError("retry"),
                    ),
                    Outcome.Success(item),
                )
            coEvery { vaultService.saveItem(item) } returns Outcome.Success(Unit)

            viewModel.processIntent(VaultIntent.SavePassword(itemId, payload))
            advanceUntilIdle()
            viewModel.processIntent(VaultIntent.SavePassword(itemId, payload))
            advanceUntilIdle()

            coVerify(exactly = 2) {
                vaultCryptoService.encryptPassword(itemId, payload, any())
            }
            coVerify(exactly = 1) { vaultService.saveItem(item) }
        }

    @Test
    fun `cancellation during password save is not converted to failure`() =
        runTest {
            val payload = PasswordPayload("Vault", charArrayOf(), "secret".toCharArray(), "")
            coEvery { vaultCryptoService.encryptPassword(any(), any(), any()) } throws CancellationException()

            viewModel.processIntent(VaultIntent.SavePassword(payload = payload))
            runCurrent()

            assertEquals(com.chimali.feature.vault.api.VaultMutationState.IDLE, viewModel.state.value.mutationState)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `copy password reports success only after clipboard service succeeds`() =
        runTest {
            every { clipboardManager.cleanupStatuses } returns
                MutableStateFlow(
                    mapOf(1L to ClipboardCleanupStatus(1L, CleanupOutcome.SkippedReplaced, CleanupOrigin.Automatic)),
                )
            every { clipboardManager.acknowledge(any()) } returns Unit
            coEvery {
                clipboardManager.copySensitiveData(
                    "Password",
                    match { it.concatToString() == "secret" },
                )
            } returns
                Result.success(ClipboardCopyReceipt(1L, 30_000L, PublicationStatus.MetadataConfirmed))

            viewModel.processIntent(VaultIntent.CopyPassword("secret".toCharArray()))
            advanceUntilIdle()

            assertEquals(
                "Password copied. We'll try to clear the clipboard in 30 seconds.",
                viewModel.state.value.copyMessage,
            )
            coVerify { clipboardManager.copySensitiveData("Password", any()) }
        }

    @Test
    fun `scheduled clear rejection reports cleanup unconfirmed and acknowledges receipt`() =
        runTest {
            every { clipboardManager.cleanupStatuses } returns
                MutableStateFlow(
                    mapOf(1L to ClipboardCleanupStatus(1L, CleanupOutcome.ClearRejected, CleanupOrigin.Automatic)),
                )
            every { clipboardManager.acknowledge(any()) } returns Unit
            coEvery {
                clipboardManager.copySensitiveData("Password", any())
            } returns Result.success(ClipboardCopyReceipt(1L, 30_000L, PublicationStatus.MetadataConfirmed))

            viewModel.processIntent(VaultIntent.CopyPassword("secret".toCharArray()))
            advanceUntilIdle()

            assertEquals("Clipboard cleanup couldn't be confirmed.", viewModel.state.value.copyMessage)
            verify { clipboardManager.acknowledge(1L) }
        }

    @Test
    fun `copy password reports failure when clipboard service fails`() =
        runTest {
            coEvery {
                clipboardManager.copySensitiveData("Password", any())
            } returns Result.failure(IllegalStateException("clipboard unavailable"))

            viewModel.processIntent(VaultIntent.CopyPassword("secret".toCharArray()))
            advanceUntilIdle()

            assertEquals("Unable to copy password to clipboard.", viewModel.state.value.copyMessage)
        }

    @Test
    fun `password save persists selected labels`() =
        runTest {
            val payload = PasswordPayload("Vault", charArrayOf(), "secret".toCharArray(), "")
            val item =
                VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.PASSWORD,
                    title = "Vault",
                    payload = byteArrayOf(1),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            val labelIds = listOf(UUID.randomUUID(), UUID.randomUUID())
            coEvery { vaultCryptoService.encryptPassword(any(), any(), any()) } returns Outcome.Success(item)
            coEvery { vaultService.saveItem(item) } returns Outcome.Success(Unit)
            coEvery { vaultService.setItemLabels(item.id, labelIds) } returns Outcome.Success(Unit)

            viewModel.processIntent(VaultIntent.SavePassword(payload = payload, labelIds = labelIds))
            advanceUntilIdle()

            coVerify { vaultService.setItemLabels(item.id, labelIds) }
        }

    @Test
    fun `decryptItem loads existing labels for edit`() =
        runTest {
            val itemId = UUID.randomUUID()
            val item =
                VaultItem(
                    id = itemId,
                    type = VaultType.PASSWORD,
                    title = "Vault",
                    payload = byteArrayOf(1),
                    crdtState = byteArrayOf(),
                    dateCreated = "",
                    dateModified = "",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            val payload = PasswordPayload("Vault", charArrayOf(), "secret".toCharArray(), "")
            val labelIds = listOf(UUID.randomUUID())
            coEvery { vaultService.getItems(any()) } returns Outcome.Success(listOf(item))
            coEvery { vaultService.getItemLabelIds(itemId) } returns Outcome.Success(labelIds)
            coEvery { vaultCryptoService.decryptPassword(item) } returns Outcome.Success(payload)

            viewModel.processIntent(VaultIntent.LoadItems())
            advanceUntilIdle()
            viewModel.processIntent(VaultIntent.DecryptItem(itemId))
            advanceUntilIdle()

            assertEquals(labelIds.toSet(), viewModel.state.value.selectedItemLabelIds)
        }
}
