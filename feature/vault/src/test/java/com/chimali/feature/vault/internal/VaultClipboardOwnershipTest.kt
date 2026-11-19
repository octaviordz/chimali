package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModelStore
import com.chimali.core.clipboard.CleanupOrigin
import com.chimali.core.clipboard.CleanupOutcome
import com.chimali.core.clipboard.ClipboardCleanupStatus
import com.chimali.core.clipboard.ClipboardCopyReceipt
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.clipboard.PublicationStatus
import com.chimali.feature.vault.api.VaultIntent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** FR-VAULT-026/027: a copy request owns a mutable operation copy until the platform adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultClipboardOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    private val clipboard = mockk<ClipboardManagerService>()
    private val store = ViewModelStore()
    private val testBuffers = mutableListOf<CharArray>()
    private lateinit var vm: VaultViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { clipboard.cleanupStatuses } returns
            MutableStateFlow(
                mapOf(1L to ClipboardCleanupStatus(1L, CleanupOutcome.SkippedReplaced, CleanupOrigin.Automatic)),
            )
        every { clipboard.transferToCustodian(any()) } just Runs
        every { clipboard.acknowledge(any()) } just Runs
        vm = VaultViewModel(mockk(), clipboard, mockk())
        store.put("vault", vm)
    }

    @After
    fun cleanup() {
        try {
            store.clear()
        } finally {
            testBuffers.forEach { it.fill('\u0000') }
            testBuffers.clear()
            Dispatchers.resetMain()
        }
    }

    private fun copyBuffer(value: String): CharArray = value.toCharArray().also(testBuffers::add)

    @Test
    fun completionAndFailureEraseCopyWithoutErasingDetails() =
        runTest {
            for (fail in listOf(false, true)) {
                coEvery {
                    clipboard.copySensitiveData(
                        "Password",
                        match { it.concatToString() == "synthetic" },
                    )
                } returns
                    if (fail) {
                        Result.failure(IllegalStateException("synthetic failure"))
                    } else {
                        Result.success(ClipboardCopyReceipt(1L, 30_000L, PublicationStatus.MetadataConfirmed))
                    }
                val detail = copyBuffer("synthetic")
                val copy = detail.copyOf()
                val intent = VaultIntent.CopyPassword(copy)
                assertFalse(intent.toString().contains("synthetic"))
                vm.processIntent(intent)
                advanceUntilIdle()
                assertTrue(copy.all { it == '\u0000' })
                assertTrue(detail.any { it != '\u0000' })
                detail.fill('\u0000')
            }
        }

    @Test
    fun neverStartedCopyIsErasedWithoutCallingClipboard() =
        runTest {
            val copy = copyBuffer("synthetic")
            vm.processIntent(VaultIntent.CopyPassword(copy))
            store.clear()
            advanceUntilIdle()
            assertTrue(copy.all { it == '\u0000' })
            coVerify(exactly = 0) { clipboard.copySensitiveData(any(), any()) }
        }

    @Test
    fun cancelledPlatformCopyErasesItsOperationBuffer() =
        runTest {
            coEvery { clipboard.copySensitiveData(any(), any()) } throws CancellationException()
            val copy = copyBuffer("synthetic")
            vm.processIntent(VaultIntent.CopyPassword(copy))
            advanceUntilIdle()
            assertTrue(copy.all { it == '\u0000' })
        }

    @Test
    fun cleanupOutcomeUpdatesOnlyItsReceiptAndAcknowledgesIt() =
        runTest {
            val statuses =
                MutableStateFlow(
                    mapOf(2L to ClipboardCleanupStatus(2L, CleanupOutcome.Pending, CleanupOrigin.Automatic)),
                )
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.success(ClipboardCopyReceipt(2L, 30_000L, PublicationStatus.MetadataConfirmed))

            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            runCurrent()
            statuses.value =
                mapOf(
                    2L to
                        ClipboardCleanupStatus(2L, CleanupOutcome.SkippedOwnershipUnavailable, CleanupOrigin.Automatic),
                )
            advanceUntilIdle()

            assertTrue(
                vm.state.value.copyMessage
                    ?.contains("couldn't be confirmed") == true,
            )
            io.mockk.verify(exactly = 1) { clipboard.acknowledge(2L) }
        }

    @Test
    fun clearRequestSubmissionIsPresentedAsUnconfirmedRemoval() =
        runTest {
            val statuses =
                MutableStateFlow(
                    mapOf(22L to ClipboardCleanupStatus(22L, CleanupOutcome.Pending, CleanupOrigin.Automatic)),
                )
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.success(ClipboardCopyReceipt(22L, 30_000L, PublicationStatus.MetadataConfirmed))

            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            runCurrent()
            statuses.value =
                mapOf(22L to ClipboardCleanupStatus(22L, CleanupOutcome.ClearRequestSubmitted, CleanupOrigin.Automatic))
            advanceUntilIdle()

            val message =
                vm.state.value.copyMessage
                    .orEmpty()
            assertTrue(message.contains("cleanup couldn't be confirmed"))
            assertFalse(message.contains("cleared", ignoreCase = true))
            assertFalse(message.contains("removed", ignoreCase = true))
            io.mockk.verify(exactly = 1) { clipboard.acknowledge(22L) }
        }

    @Test
    fun interruptedCleanupIsPresentedAsUnconfirmedRemoval() =
        runTest {
            val statuses =
                MutableStateFlow(
                    mapOf(23L to ClipboardCleanupStatus(23L, CleanupOutcome.Pending, CleanupOrigin.Automatic)),
                )
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.success(ClipboardCopyReceipt(23L, 30_000L, PublicationStatus.MetadataConfirmed))

            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            runCurrent()
            statuses.value =
                mapOf(23L to ClipboardCleanupStatus(23L, CleanupOutcome.Interrupted, CleanupOrigin.Automatic))
            advanceUntilIdle()

            assertTrue(
                vm.state.value.copyMessage
                    .orEmpty()
                    .contains("cleanup couldn't be confirmed"),
            )
            assertFalse(
                vm.state.value.copyMessage
                    .orEmpty()
                    .contains("cleared", ignoreCase = true),
            )
            io.mockk.verify(exactly = 1) { clipboard.acknowledge(23L) }
        }

    @Test
    fun disposedViewModelTransfersPendingReceiptToCustodian() =
        runTest {
            val statuses =
                MutableStateFlow(
                    mapOf(3L to ClipboardCleanupStatus(3L, CleanupOutcome.Pending, CleanupOrigin.Automatic)),
                )
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.success(ClipboardCopyReceipt(3L, 30_000L, PublicationStatus.MetadataConfirmed))

            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            runCurrent()
            val messageAtDisposal = vm.state.value.copyMessage
            store.clear()

            io.mockk.verify(exactly = 1) { clipboard.transferToCustodian(3L) }
            coVerify(exactly = 0) { clipboard.acknowledge(3L) }
            statuses.value =
                mapOf(3L to ClipboardCleanupStatus(3L, CleanupOutcome.ClearRequestSubmitted, CleanupOrigin.Automatic))
            advanceUntilIdle()
            assertEquals(
                "Disposed ViewModel must not receive cleanup feedback",
                messageAtDisposal,
                vm.state.value.copyMessage,
            )
            coVerify(exactly = 0) { clipboard.acknowledge(3L) }
        }

    @Test
    fun unconfirmedPublicationUsesHonestBestEffortFeedback() =
        runTest {
            val statuses =
                MutableStateFlow(
                    mapOf(4L to ClipboardCleanupStatus(4L, CleanupOutcome.Pending, CleanupOrigin.Automatic)),
                )
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.success(ClipboardCopyReceipt(4L, 30_000L, PublicationStatus.Unconfirmed))

            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            runCurrent()

            val message =
                vm.state.value.copyMessage
                    .orEmpty()
            assertTrue(message.contains("Copy couldn't be confirmed"))
            assertTrue(message.contains("If copied"))
            assertTrue(message.contains("30 seconds"))
            assertFalse(message.contains("Password copied"))
        }

    @Test
    fun admissionRejectionUsesStateNeutralRetryFeedback() =
        runTest {
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.failure(
                    com.chimali.core.clipboard.ClipboardError
                        .CopyAdmissionRejected(),
                )
            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            advanceUntilIdle()

            assertTrue(
                vm.state.value.copyMessage
                    ?.contains("temporarily unavailable") == true,
            )
            assertFalse(
                vm.state.value.copyMessage
                    ?.contains("synthetic") == true,
            )
        }

    @Test
    fun admissionRejectionIsStateNeutralForPendingAndFullReceiptStates() =
        runTest {
            val pendingCandidate =
                mapOf(31L to ClipboardCleanupStatus(31L, CleanupOutcome.Pending, CleanupOrigin.Automatic))
            val fullTerminalCapacity =
                mapOf(
                    32L to ClipboardCleanupStatus(32L, CleanupOutcome.SkippedReplaced, CleanupOrigin.Automatic),
                    33L to ClipboardCleanupStatus(33L, CleanupOutcome.ClearRequestSubmitted, CleanupOrigin.Automatic),
                )
            val statuses = MutableStateFlow(pendingCandidate)
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.failure(
                    com.chimali.core.clipboard.ClipboardError
                        .CopyAdmissionRejected(),
                )

            for (scenario in listOf(pendingCandidate, fullTerminalCapacity)) {
                statuses.value = scenario
                val borrowedCopy = copyBuffer("synthetic")
                vm.processIntent(VaultIntent.CopyPassword(borrowedCopy))
                advanceUntilIdle()

                assertTrue(borrowedCopy.all { it == '\u0000' })
                assertTrue(
                    vm.state.value.copyMessage
                        ?.contains("temporarily unavailable") == true,
                )
                assertFalse(
                    vm.state.value.copyMessage
                        ?.contains("copied", ignoreCase = true) == true,
                )
                assertFalse(vm.state.value.selectedPasswordPayload != null)
                assertEquals(scenario.keys, statuses.value.keys)
            }
        }

    @Test
    fun rejectedManualClearReportsUnconfirmedStateWithoutClaimingSuccess() =
        runTest {
            coEvery { clipboard.clearClipboard() } returns
                Result.failure(
                    com.chimali.core.clipboard.ClipboardError
                        .ClearFailed("synthetic platform text"),
                )
            vm.processIntent(VaultIntent.ClearClipboard)
            advanceUntilIdle()

            assertTrue(
                vm.state.value.copyMessage
                    ?.contains("may still be available") == true,
            )
            assertFalse(
                vm.state.value.copyMessage
                    ?.contains("synthetic platform text") == true,
            )
        }

    @Test
    fun backToBackTerminalReceiptsAreBothAcknowledgedByGeneration() =
        runTest {
            val statuses = MutableStateFlow<Map<Long, ClipboardCleanupStatus>>(emptyMap())
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } coAnswers {
                val generation = if (statuses.value.isEmpty()) 11L else 12L
                statuses.value =
                    statuses.value +
                    (
                        generation to
                            ClipboardCleanupStatus(generation, CleanupOutcome.Pending, CleanupOrigin.Automatic)
                    )
                statuses.value = statuses.value +
                    (
                        generation to
                            ClipboardCleanupStatus(generation, CleanupOutcome.SkippedReplaced, CleanupOrigin.Automatic)
                    )
                Result.success(ClipboardCopyReceipt(generation, 30_000L, PublicationStatus.MetadataConfirmed))
            }

            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("one")))
            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("two")))
            advanceUntilIdle()

            io.mockk.verify(exactly = 1) { clipboard.acknowledge(11L) }
            io.mockk.verify(exactly = 1) { clipboard.acknowledge(12L) }
        }

    @Test
    fun recreatedViewModelDoesNotRestoreCopyFeedbackOrSecretWork() =
        runTest {
            val statuses =
                MutableStateFlow(
                    mapOf(21L to ClipboardCleanupStatus(21L, CleanupOutcome.Pending, CleanupOrigin.Automatic)),
                )
            every { clipboard.cleanupStatuses } returns statuses
            coEvery { clipboard.copySensitiveData(any(), any()) } returns
                Result.success(ClipboardCopyReceipt(21L, 30_000L, PublicationStatus.MetadataConfirmed))
            vm.processIntent(VaultIntent.CopyPassword(copyBuffer("synthetic")))
            runCurrent()
            store.clear()

            val replacement = VaultViewModel(mockk(), clipboard, mockk())
            val replacementStore = ViewModelStore().apply { put("replacement", replacement) }
            assertFalse(replacement.state.value.copyMessage != null)
            assertFalse(replacement.state.value.selectedPasswordPayload != null)
            coVerify(exactly = 1) { clipboard.copySensitiveData(any(), any()) }
            replacementStore.clear()
        }
}
