package com.chimali.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass") // Clipboard lifecycle scenarios share static platform fakes and one virtual-time fixture.
class AndroidClipboardManagerServiceTest {
    private val context: Context = mockk()
    private val clipboardManager: ClipboardManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var service: AndroidClipboardManagerService
    private lateinit var mockDescription: ClipDescription
    private var forcedReadbackToken: String? = null
    private val writtenOwnershipTokens = mutableListOf<String>()

    companion object {
        private const val TEST_LABEL = "Test Label"
        private const val TEST_SENSITIVE_DATA = "Sensitive123"
        private const val TEST_SHORT = "Test"
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager

        // Mock static Android methods to prevent "Stub!" crashes
        mockkStatic(ClipData::class)
        mockkStatic(SystemClock::class)
        mockkConstructor(PersistableBundle::class)
        val extras = mutableMapOf<String, Any>()
        writtenOwnershipTokens.clear()
        every { anyConstructed<PersistableBundle>().putBoolean(any(), any()) } answers
            { extras[firstArg()] = secondArg() }
        every { anyConstructed<PersistableBundle>().putString(any(), any()) } answers {
            extras[firstArg()] = secondArg()
            if (firstArg<String>() == "com.chimali.clipboard.OWNERSHIP_TOKEN") writtenOwnershipTokens.add(secondArg())
        }
        every { anyConstructed<PersistableBundle>().getString(any()) } answers {
            forcedReadbackToken ?: extras[firstArg<String>()] as? String
        }
        every { anyConstructed<PersistableBundle>().getBoolean(any()) } answers
            { extras[firstArg<String>()] as? Boolean ?: false }
        every { SystemClock.elapsedRealtime() } answers { testDispatcher.scheduler.currentTime }

        mockDescription = mockk<ClipDescription>(relaxed = true)
        val extrasSlot = slot<PersistableBundle>()

        every { mockDescription.extras = capture(extrasSlot) } just Runs
        every { mockDescription.extras } answers { extrasSlot.captured }
        every { clipboardManager.primaryClipDescription } returns mockDescription

        val mockClipData =
            mockk<ClipData>(relaxed = true) {
                every { description } returns mockDescription
            }

        every { ClipData.newPlainText(any(), any()) } returns mockClipData

        service = AndroidClipboardManagerService(context)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(ClipData::class)
        unmockkStatic(SystemClock::class)
        unmockkConstructor(PersistableBundle::class)
        clearAllMocks()
    }

    @Test
    fun `sensitive copy requests Android clipboard preview suppression`() =
        runTest(testDispatcher) {
            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray())
            runCurrent()
            verify(exactly = 1) {
                anyConstructed<PersistableBundle>().putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            verify(
                exactly = 1,
            ) { anyConstructed<PersistableBundle>().putString("com.chimali.clipboard.OWNERSHIP_TOKEN", any()) }
            verifyOrder {
                anyConstructed<PersistableBundle>().putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                anyConstructed<PersistableBundle>().putString("com.chimali.clipboard.OWNERSHIP_TOKEN", any())
                clipboardManager.setPrimaryClip(any())
            }
        }

    @Test
    fun `caller supplied label is replaced with fixed non-identifying clipboard label`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            val identifyingLabel = "synthetic-private-account-label"

            service.copySensitiveData(identifyingLabel, TEST_SENSITIVE_DATA.toCharArray()).getOrThrow()
            runCurrent()

            verify(exactly = 1) { ClipData.newPlainText("Password", TEST_SENSITIVE_DATA) }
            verify(exactly = 0) { ClipData.newPlainText(identifyingLabel, any()) }
        }

    @Test
    fun `private token source can be replaced by isolated test reflection seam`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            val expectedTestToken = "fresh-synthetic-observer-token"
            val tokenSourceField = service.javaClass.getDeclaredField("ownershipTokenSource")
            tokenSourceField.isAccessible = true
            tokenSourceField.set(service, { expectedTestToken })

            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrThrow()
            runCurrent()

            assertEquals(expectedTestToken, writtenOwnershipTokens.single())
        }

    @Test
    fun `production token source generates a fresh UUID for each copy`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs

            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrThrow()
            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrThrow()
            runCurrent()

            assertEquals(2, writtenOwnershipTokens.size)
            kotlin.test.assertTrue(
                writtenOwnershipTokens.all { token ->
                    runCatching { java.util.UUID.fromString(token) }.isSuccess
                },
            )
            kotlin.test.assertNotEquals(writtenOwnershipTokens[0], writtenOwnershipTokens[1])
        }

    @Test
    fun `copySensitiveData clears at fixed delay only after matching ownership metadata`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val receipt =
                requireNotNull(service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrNull())
            runCurrent()

            // Verify it was copied
            verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
            verify(exactly = 1) { ClipData.newPlainText("Password", TEST_SENSITIVE_DATA) }
            verify(exactly = 0) { clipboardManager.primaryClip }

            // Verify it IS NOT cleared yet
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }

            // Advance time to 29,999ms
            advanceTimeBy(29_999L)
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }

            // Advance time to 30,000ms
            advanceTimeBy(1L)
            runCurrent()
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
            val timing = requireNotNull(service.cleanupStatuses.value[receipt.generation])
            kotlin.test.assertTrue(timing.callbackElapsedMs!! >= receipt.scheduledElapsedMs)
            kotlin.test.assertTrue(timing.ownershipCheckElapsedMs!! >= timing.callbackElapsedMs)
            kotlin.test.assertTrue(timing.clearAttemptElapsedMs!! >= timing.ownershipCheckElapsedMs)
        }

    @Test
    fun `automatic cleanup skips when another entry owns the clipboard`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs
            every { anyConstructed<PersistableBundle>().getString("com.chimali.clipboard.OWNERSHIP_TOKEN") } returns
                "different-entry"

            val receipt =
                requireNotNull(service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrNull())
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
        }

    @Test
    fun `automatic cleanup skips when ownership metadata cannot be read`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.primaryClipDescription } throws SecurityException("synthetic unreadable metadata")

            val receipt =
                requireNotNull(service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrNull())
            runCurrent()
            kotlin.test.assertEquals(PublicationStatus.Unconfirmed, receipt.publication)

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedOwnershipUnavailable,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
        }

    @Test
    fun `missing ownership token in readable metadata is treated as replaced`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { anyConstructed<PersistableBundle>().getString("com.chimali.clipboard.OWNERSHIP_TOKEN") } returns
                null
            every { clipboardManager.clearPrimaryClip() } just Runs

            val receipt = service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
        }

    @Test
    fun `null description and malformed token metadata are unknown and never cleared`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            every { clipboardManager.primaryClipDescription } returns null
            val nullDescription = service.copySensitiveData(TEST_LABEL, "null-description".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedOwnershipUnavailable,
                service.cleanupStatuses.value[nullDescription.generation]?.outcome,
            )

            every { clipboardManager.primaryClipDescription } throws SecurityException("metadata unavailable")
            val malformed = service.copySensitiveData(TEST_LABEL, "malformed".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedOwnershipUnavailable,
                service.cleanupStatuses.value[malformed.generation]?.outcome,
            )
        }

    @Test
    fun `failed manual clear preserves scheduled automatic attempt`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            var clearCalls = 0
            every { clipboardManager.clearPrimaryClip() } answers {
                clearCalls++
                if (clearCalls == 1) throw SecurityException("synthetic rejection")
            }

            val receipt =
                requireNotNull(service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrNull())
            runCurrent()
            val manual = service.clearClipboard()
            kotlin.test.assertTrue(manual.isFailure)
            val manualError = requireNotNull(manual.exceptionOrNull())
            kotlin.test.assertEquals("Clear failed", manualError.message)
            kotlin.test.assertEquals(null, manualError.cause)
            kotlin.test.assertFalse(manualError.toString().contains("synthetic rejection"))
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[receipt.generation]?.outcome)

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            kotlin.test.assertEquals(2, clearCalls)
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
        }

    @Test
    fun `custodian handoff preserves pending timer then records and releases abandonment`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val receipt =
                requireNotNull(service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrNull())
            runCurrent()
            service.transferToCustodian(receipt.generation)
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[receipt.generation]?.outcome)

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertFalse(service.cleanupStatuses.value.containsKey(receipt.generation))
        }

    @Test
    fun `terminal receipt transferred to custodian is abandoned and releases capacity`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs

            val first = service.copySensitiveData(TEST_SHORT, "one".toCharArray()).getOrThrow()
            val second = service.copySensitiveData(TEST_SHORT, "two".toCharArray()).getOrThrow()
            kotlin.test.assertEquals(
                CleanupOutcome.Superseded,
                service.cleanupStatuses.value[first.generation]?.outcome,
            )
            service.transferToCustodian(first.generation)

            kotlin.test.assertFalse(service.cleanupStatuses.value.containsKey(first.generation))
            kotlin.test.assertTrue(service.cleanupStatuses.value.containsKey(second.generation))
            kotlin.test.assertTrue(service.copySensitiveData(TEST_SHORT, "three".toCharArray()).isSuccess)
        }

    // The worker must marshal any failure type back to the test thread without losing the assertion cause.
    @Suppress("TooGenericExceptionCaught")
    @Test
    fun `terminal acknowledgement racing permanent disposal releases receipt exactly once`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val receipt = service.copySensitiveData(TEST_SHORT, "race-marker".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )

            val start = java.util.concurrent.CountDownLatch(1)
            val failure =
                java.util.concurrent.atomic
                    .AtomicReference<Throwable?>()
            val disposal =
                Thread {
                    try {
                        start.await()
                        service.transferToCustodian(receipt.generation)
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        failure.set(error)
                    } catch (error: RuntimeException) {
                        failure.set(error)
                    }
                }
            disposal.start()
            start.countDown()
            service.acknowledge(receipt.generation)
            disposal.join()

            failure.get()?.let { throw it }
            kotlin.test.assertFalse(service.cleanupStatuses.value.containsKey(receipt.generation))
            kotlin.test.assertTrue(service.copySensitiveData(TEST_SHORT, "next".toCharArray()).isSuccess)
        }

    @Test
    fun `unconfirmed candidate preserves predecessor and blocks another publication`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val first = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            runCurrent()
            forcedReadbackToken = "external-entry"
            val second = service.copySensitiveData(TEST_SHORT, "second".toCharArray()).getOrThrow()
            runCurrent()
            kotlin.test.assertEquals(PublicationStatus.Unconfirmed, second.publication)
            val rejected = service.copySensitiveData(TEST_SHORT, "third".toCharArray())

            kotlin.test.assertTrue(rejected.isFailure)
            verify(exactly = 2) { clipboardManager.setPrimaryClip(any()) }
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[first.generation]?.outcome,
            )
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[second.generation]?.outcome,
            )
        }

    @Test
    fun `terminal receipt acknowledgement releases a bounded capacity slot`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            val first = service.copySensitiveData(TEST_SHORT, "one".toCharArray()).getOrThrow()
            val second = service.copySensitiveData(TEST_SHORT, "two".toCharArray()).getOrThrow()
            kotlin.test.assertEquals(
                CleanupOutcome.Superseded,
                service.cleanupStatuses.value[first.generation]?.outcome,
            )
            kotlin.test.assertTrue(service.copySensitiveData(TEST_SHORT, "three".toCharArray()).isFailure)

            service.acknowledge(first.generation)
            val third = service.copySensitiveData(TEST_SHORT, "three".toCharArray()).getOrThrow()

            kotlin.test.assertTrue(third.generation > second.generation)
            kotlin.test.assertEquals(2, service.cleanupStatuses.value.size)
        }

    @Test
    fun `publication interleaved with acknowledgement preserves new receipt and recovers capacity`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs
            val terminal = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[terminal.generation]?.outcome,
            )

            every { clipboardManager.setPrimaryClip(any()) } answers {
                service.acknowledge(terminal.generation)
            }
            val current = service.copySensitiveData(TEST_SHORT, "second".toCharArray()).getOrThrow()

            kotlin.test.assertFalse(service.cleanupStatuses.value.containsKey(terminal.generation))
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[current.generation]?.outcome)
            kotlin.test.assertEquals(setOf(current.generation), service.cleanupStatuses.value.keys)

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            service.acknowledge(current.generation)
            val next = service.copySensitiveData(TEST_SHORT, "third".toCharArray()).getOrThrow()
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[next.generation]?.outcome)
            kotlin.test.assertEquals(setOf(next.generation), service.cleanupStatuses.value.keys)
        }

    @Test
    fun `back to back terminal outcomes remain independently addressable until acknowledgement`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val first = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            val second = service.copySensitiveData(TEST_SHORT, "second".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            kotlin.test.assertEquals(
                CleanupOutcome.Superseded,
                service.cleanupStatuses.value[first.generation]?.outcome,
            )
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[second.generation]?.outcome,
            )
            kotlin.test.assertEquals(2, service.cleanupStatuses.value.size)
            service.acknowledge(first.generation)
            kotlin.test.assertEquals(1, service.cleanupStatuses.value.size)
            kotlin.test.assertTrue(service.cleanupStatuses.value.containsKey(second.generation))
        }

    @Test
    fun `explicit terminal abandonment releases only its receipt slot`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs

            val first = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            val second = service.copySensitiveData(TEST_SHORT, "second".toCharArray()).getOrThrow()
            service.abandon(first.generation)

            kotlin.test.assertFalse(service.cleanupStatuses.value.containsKey(first.generation))
            kotlin.test.assertTrue(service.cleanupStatuses.value.containsKey(second.generation))
            kotlin.test.assertTrue(service.copySensitiveData(TEST_SHORT, "third".toCharArray()).isSuccess)
        }

    @Test
    fun `manual clear keeps receipt capacity occupied until acknowledgement`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val manuallyCleared = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            runCurrent()
            service.clearClipboard().getOrThrow()

            kotlin.test.assertEquals(
                CleanupOutcome.ManualClearSubmitted,
                service.cleanupStatuses.value[manuallyCleared.generation]?.outcome,
            )
            val second = service.copySensitiveData(TEST_SHORT, "second".toCharArray()).getOrThrow()
            runCurrent()
            kotlin.test.assertTrue(service.copySensitiveData(TEST_SHORT, "third".toCharArray()).isFailure)

            service.acknowledge(manuallyCleared.generation)
            kotlin.test.assertFalse(service.cleanupStatuses.value.containsKey(manuallyCleared.generation))
            kotlin.test.assertTrue(service.copySensitiveData(TEST_SHORT, "fourth".toCharArray()).isSuccess)
            kotlin.test.assertTrue(service.cleanupStatuses.value.containsKey(second.generation))
        }

    @Test
    fun `stale due callback cannot clear a newer publication`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val first = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            val second = service.copySensitiveData(TEST_SHORT, "second".toCharArray()).getOrThrow()
            runCurrent()

            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.Superseded,
                service.cleanupStatuses.value[first.generation]?.outcome,
            )
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[second.generation]?.outcome)
        }

    @Test
    fun `rejected new publication preserves the predecessor cleanup`() =
        runTest(testDispatcher) {
            var publications = 0
            every { clipboardManager.setPrimaryClip(any()) } answers {
                publications++
                if (publications == 2) throw SecurityException("synthetic publication failure")
            }
            every { clipboardManager.clearPrimaryClip() } just Runs

            val first = service.copySensitiveData(TEST_SHORT, "first".toCharArray()).getOrThrow()
            runCurrent()
            val firstToken = writtenOwnershipTokens.first()
            val rejected = service.copySensitiveData(TEST_SHORT, "second".toCharArray())
            kotlin.test.assertTrue(rejected.isFailure)
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[first.generation]?.outcome)

            // The setup's ClipData mock reuses a description object; force the observable
            // metadata back to the actually published predecessor after the rejected write.
            forcedReadbackToken = firstToken
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[first.generation]?.outcome,
            )
        }

    @Test
    fun `returned but ignored publication remains unconfirmed and preserves predecessor cleanup`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val predecessor = service.copySensitiveData(TEST_SHORT, "predecessor".toCharArray()).getOrThrow()
            runCurrent()
            val predecessorToken = writtenOwnershipTokens.first()

            // Model Android returning normally while the observable clipboard still has
            // the predecessor's metadata (the candidate write was silently ignored).
            forcedReadbackToken = predecessorToken
            val candidate = service.copySensitiveData(TEST_SHORT, "candidate".toCharArray()).getOrThrow()
            runCurrent()

            kotlin.test.assertEquals(PublicationStatus.Unconfirmed, candidate.publication)
            kotlin.test.assertEquals(
                CleanupOutcome.Pending,
                service.cleanupStatuses.value[predecessor.generation]?.outcome,
            )
            kotlin.test.assertEquals(
                CleanupOutcome.Pending,
                service.cleanupStatuses.value[candidate.generation]?.outcome,
            )

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[predecessor.generation]?.outcome,
            )
            kotlin.test.assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[candidate.generation]?.outcome,
            )
        }

    @Test
    fun `publication cancellation propagates instead of becoming a failure result`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } throws
                kotlinx.coroutines.CancellationException("synthetic")

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                service.copySensitiveData(TEST_SHORT, "synthetic".toCharArray())
            }
            kotlin.test.assertTrue(service.cleanupStatuses.value.isEmpty())
        }

    @Test
    fun `ownership token failure releases only its reservation and preserves prior deadline`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs
            val prior = service.copySensitiveData(TEST_SHORT, "prior-secret".toCharArray()).getOrThrow()
            runCurrent()
            val tokenSourceField =
                service.javaClass.getDeclaredField("ownershipTokenSource").apply { isAccessible = true }
            tokenSourceField.set(
                service,
                {
                    throw SecurityException("synthetic ownership token failure")
                },
            )

            val failed = service.copySensitiveData(TEST_SHORT, "rejected-secret".toCharArray())
            val error = failed.exceptionOrNull()

            kotlin.test.assertIs<ClipboardError.CopyFailed>(error)
            kotlin.test.assertEquals("Copy failed", error.message)
            kotlin.test.assertFalse(error.message.orEmpty().contains("synthetic"))
            kotlin.test.assertFalse(error.message.orEmpty().contains("secret"))
            assertEquals(setOf(prior.generation), service.cleanupStatuses.value.keys)
            verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS - 1)
            runCurrent()
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            advanceTimeBy(1)
            runCurrent()
            assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[prior.generation]?.outcome,
            )
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }

            tokenSourceField.set(service, { "later-synthetic-token" })
            val later = service.copySensitiveData(TEST_SHORT, "later-secret".toCharArray()).getOrThrow()
            runCurrent()
            kotlin.test.assertEquals(PublicationStatus.MetadataConfirmed, later.publication)
            assertEquals(2, service.cleanupStatuses.value.size)
            verify(exactly = 2) { clipboardManager.setPrimaryClip(any()) }
        }

    @Test
    fun `ownership token cancellation propagates releases reservation and preserves prior deadline`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs
            val prior = service.copySensitiveData(TEST_SHORT, "prior-secret".toCharArray()).getOrThrow()
            runCurrent()
            val tokenSourceField =
                service.javaClass.getDeclaredField("ownershipTokenSource").apply { isAccessible = true }
            tokenSourceField.set(
                service,
                {
                    throw kotlinx.coroutines.CancellationException("synthetic cancellation")
                },
            )

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                service.copySensitiveData(TEST_SHORT, "cancelled-secret".toCharArray())
            }
            assertEquals(setOf(prior.generation), service.cleanupStatuses.value.keys)
            verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS - 1)
            runCurrent()
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            advanceTimeBy(1)
            runCurrent()
            assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[prior.generation]?.outcome,
            )
            tokenSourceField.set(service, { "recovered-synthetic-token" })
            val later = service.copySensitiveData(TEST_SHORT, "later-secret".toCharArray()).getOrThrow()
            runCurrent()
            kotlin.test.assertEquals(PublicationStatus.MetadataConfirmed, later.publication)
            assertEquals(2, service.cleanupStatuses.value.size)
            verify(exactly = 2) { clipboardManager.setPrimaryClip(any()) }
        }

    @Test
    fun `postpublication metadata cancellation retains scheduled cleanup under service custody`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs
            val predecessor = service.copySensitiveData(TEST_SHORT, "predecessor-secret".toCharArray()).getOrThrow()
            runCurrent()
            advanceTimeBy(5_000L)
            every { clipboardManager.primaryClipDescription } throws
                kotlinx.coroutines.CancellationException("synthetic metadata cancellation") andThen
                mockDescription

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                service.copySensitiveData(TEST_SHORT, "synthetic-postpublication-secret".toCharArray())
            }
            runCurrent()
            assertEquals(2, service.cleanupStatuses.value.size)
            assertEquals(
                CleanupOutcome.Pending,
                service.cleanupStatuses.value[predecessor.generation]?.outcome,
            )
            verify(exactly = 2) { clipboardManager.setPrimaryClip(any()) }

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS - 5_001L)
            runCurrent()
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            advanceTimeBy(1)
            runCurrent()
            assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[predecessor.generation]?.outcome,
            )
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            advanceTimeBy(4_999L)
            runCurrent()
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
            assertEquals(2, service.cleanupStatuses.value.size)
            assertEquals(
                CleanupOutcome.Pending,
                service.cleanupStatuses.value[predecessor.generation + 1]?.outcome,
            )
            advanceTimeBy(1L)
            runCurrent()
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
            assertEquals(
                CleanupOutcome.SkippedReplaced,
                service.cleanupStatuses.value[predecessor.generation]?.outcome,
            )
            assertEquals(setOf(predecessor.generation), service.cleanupStatuses.value.keys)
            val later = service.copySensitiveData(TEST_SHORT, "later-secret".toCharArray()).getOrThrow()
            runCurrent()
            assertEquals(PublicationStatus.MetadataConfirmed, later.publication)
        }

    @Test
    fun `cancelled scheduled cleanup reports interruption without claiming removal`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            val receipt = service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray()).getOrThrow()
            runCurrent()
            val jobsField = service.javaClass.getDeclaredField("jobs").apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val scheduledJobs = jobsField.get(service) as Map<Long, kotlinx.coroutines.Job>

            scheduledJobs.getValue(receipt.generation).cancel()
            runCurrent()

            assertEquals(
                CleanupOutcome.Interrupted,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
        }

    @Test
    fun `publication failure is sanitized and does not expose clipboard content or platform text`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } throws SecurityException("synthetic-platform-marker")

            val result = service.copySensitiveData(TEST_LABEL, "synthetic-secret-marker".toCharArray())

            val error = result.exceptionOrNull()
            kotlin.test.assertIs<ClipboardError.CopyFailed>(error)
            kotlin.test.assertFalse(error.message.orEmpty().contains("synthetic-platform-marker"))
            kotlin.test.assertFalse(error.message.orEmpty().contains("synthetic-secret-marker"))
            kotlin.test.assertTrue(service.cleanupStatuses.value.isEmpty())
        }

    @Test
    fun `manual clear failure is sanitized and retains pending cleanup`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } throws SecurityException("synthetic-platform-marker")
            val receipt = service.copySensitiveData(TEST_LABEL, "synthetic-secret-marker".toCharArray()).getOrThrow()
            runCurrent()

            val result = service.clearClipboard()

            val error = result.exceptionOrNull()
            kotlin.test.assertIs<ClipboardError.ClearFailed>(error)
            kotlin.test.assertFalse(error.message.orEmpty().contains("synthetic-platform-marker"))
            kotlin.test.assertFalse(error.message.orEmpty().contains("synthetic-secret-marker"))
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[receipt.generation]?.outcome)
        }

    @Test
    fun `manual clear cancellation propagates and leaves automatic work scheduled`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            var clearCalls = 0
            every { clipboardManager.clearPrimaryClip() } answers {
                clearCalls++
                if (clearCalls == 1) throw kotlinx.coroutines.CancellationException("synthetic")
            }
            val receipt = service.copySensitiveData(TEST_SHORT, "synthetic".toCharArray()).getOrThrow()
            runCurrent()

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { service.clearClipboard() }
            kotlin.test.assertEquals(CleanupOutcome.Pending, service.cleanupStatuses.value[receipt.generation]?.outcome)

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()
            kotlin.test.assertEquals(2, clearCalls)
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRequestSubmitted,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
        }

    @Test
    fun `automatic clear failure is recorded as rejected and is not retried`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } throws SecurityException("synthetic-platform-marker")
            val receipt = service.copySensitiveData(TEST_SHORT, "synthetic-secret-marker".toCharArray()).getOrThrow()
            runCurrent()

            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS)
            runCurrent()

            kotlin.test.assertEquals(
                CleanupOutcome.ClearRejected,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
            advanceTimeBy(CLIPBOARD_CLEANUP_DELAY_MS * 2)
            runCurrent()
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
            kotlin.test.assertEquals(
                CleanupOutcome.ClearRejected,
                service.cleanupStatuses.value[receipt.generation]?.outcome,
            )
        }

    @Test
    fun `clearClipboard explicitly clears clipboard and cancels pending job`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA.toCharArray())
            runCurrent()

            // Explicitly clear
            service.clearClipboard()
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }

            // Advance beyond the fixed 30-second deadline; a cancelled job must not clear again.
            advanceTimeBy(31_000L)
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() } // Still just 1 call total
        }

    @Test
    fun `successive copies reset the clear timer`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            // First copy
            service.copySensitiveData(TEST_SHORT, "Data1".toCharArray())
            runCurrent()
            advanceTimeBy(15_000L) // Wait half the time

            // Second copy
            service.copySensitiveData(TEST_SHORT, "Data2".toCharArray())
            runCurrent()

            advanceTimeBy(15_000L) // First deadline would have elapsed
            // Clear should NOT be called yet because timer reset
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }

            advanceTimeBy(15_001L) // Reach the second copy deadline
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
        }
}
