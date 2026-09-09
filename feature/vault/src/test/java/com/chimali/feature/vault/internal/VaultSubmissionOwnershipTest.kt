package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModelStore
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultMutationState
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** FR-VAULT-026/034: operation ownership independent from the editor and coroutine scheduling. */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultSubmissionOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    private val crypto = mockk<VaultCryptoService>()
    private val service = mockk<VaultService>(relaxed = true)
    private val store = ViewModelStore()
    private lateinit var vm: VaultViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = VaultViewModel(service, mockk<ClipboardManagerService>(), crypto)
        store.put("vault", vm)
        coEvery { service.getItems(any()) } returns Outcome.Success(emptyList())
        coEvery { service.setItemLabels(any(), any()) } returns Outcome.Success(Unit)
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun draft() =
        PasswordPayload(
            "title",
            "user".toCharArray(),
            "secret".toCharArray(),
            "uri",
            "notes".toCharArray(),
            listOf(CustomField("name", "value".toCharArray(), false)),
        )

    @Test
    fun neverStartedJobAndRejectedDuplicateAreErasedWithoutErasingDraft() =
        runTest {
            val draft = draft()
            val first = draft.copyForEditing()
            val duplicate = draft.copyForEditing()
            vm.processIntent(VaultIntent.SavePassword(payload = first))
            vm.processIntent(VaultIntent.SavePassword(payload = duplicate))
            erased(duplicate)
            assertEquals("secret", String(first.password))
            store.clear()
            advanceUntilIdle()
            erased(first)
            assertEquals("value", String(draft.customFields!!.single().value))
            draft.clearMemory()
            coVerify(exactly = 0) { crypto.encryptPassword(any(), any(), any()) }
        }

    @Test
    fun failureConsumesEachAttemptAndRetryKeepsIdentityAndEveryDraftField() =
        runTest {
            val ids = mutableListOf<UUID?>()
            coEvery { crypto.encryptPassword(any(), any(), any()) } coAnswers {
                ids.add(firstArg())
                Outcome.Error(DomainError.CryptoError("retry"))
            }
            val draft = draft()
            val expected = draft.copyForEditing()
            repeat(2) {
                val attempt = draft.copyForEditing()
                vm.processIntent(VaultIntent.SavePassword(payload = attempt))
                advanceUntilIdle()
                erased(attempt)
                assertEquals(expected, draft)
            }
            assertEquals(ids[0], ids[1])
            assertTrue(ids[0] != null)
            draft.clearMemory()
            expected.clearMemory()
        }

    @Test
    fun lateSaveCompletionCannotPublishSuccessIntoAnotherSession() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val item =
                VaultItem(
                    UUID(0, 1),
                    VaultType.PASSWORD,
                    "title",
                    byteArrayOf(1),
                    byteArrayOf(),
                    "",
                    "",
                    null,
                    UUID(0, 2),
                )
            coEvery { crypto.encryptPassword(any(), any(), any()) } returns Outcome.Success(item)
            coEvery { service.saveItem(any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                Outcome.Success(Unit)
            }
            val attempt = draft()
            vm.processIntent(VaultIntent.SavePassword(payload = attempt))
            runCurrent()
            vm.processIntent(VaultIntent.AbandonMutation)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(VaultMutationState.IDLE, vm.state.value.mutationState)
            erased(attempt)
            assertTrue(item.title.all { it == '\u0000' })
            coVerify(exactly = 0) { service.setItemLabels(any(), any()) }
        }

    @Test
    fun cancelledProviderLeavesEditorReadyToRetry() =
        runTest {
            coEvery { crypto.encryptPassword(any(), any(), any()) } throws
                kotlinx.coroutines.CancellationException("synthetic")
            val attempt = draft()
            vm.processIntent(VaultIntent.SavePassword(payload = attempt))
            advanceUntilIdle()
            erased(attempt)
            assertEquals(VaultMutationState.IDLE, vm.state.value.mutationState)
            assertEquals(false, vm.state.value.isLoading)
        }

    @Test
    fun abandonedRefreshCannotOverwriteNewSessionItems() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val item =
                VaultItem(
                    UUID(0, 1),
                    VaultType.PASSWORD,
                    "old",
                    byteArrayOf(1),
                    byteArrayOf(),
                    "",
                    "",
                    null,
                    UUID(0, 2),
                )
            coEvery { crypto.encryptPassword(any(), any(), any()) } returns Outcome.Success(item)
            coEvery { service.saveItem(any()) } returns Outcome.Success(Unit)
            coEvery { service.getItems(any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                Outcome.Success(listOf(item))
            }
            vm.processIntent(VaultIntent.SavePassword(payload = draft()))
            runCurrent()
            vm.processIntent(VaultIntent.AbandonMutation)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(emptyList<VaultItem>(), vm.state.value.items)
            assertEquals(VaultMutationState.IDLE, vm.state.value.mutationState)
        }

    private fun erased(payload: PasswordPayload) {
        listOf(
            payload.title,
            payload.username,
            payload.password,
            payload.uri,
            payload.notes!!,
            payload.customFields!!.single().name,
            payload.customFields.single().value,
        ).forEach {
            assertTrue(it.all { c -> c == '\u0000' })
        }
    }
}
