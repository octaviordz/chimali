package com.chimali.core.clipboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.koin.core.annotation.Single
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIPasteboard

/**
 * iOS pasteboard adapter. UIPasteboard.changeCount is metadata only; this adapter never reads
 * clipboard text to decide ownership. It cannot provide Android's preview-sensitivity flag.
 */
@Single(binds = [ClipboardManagerService::class])
class IosClipboardManagerService : ClipboardManagerService {
    private val pasteboard = UIPasteboard.generalPasteboard
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operation = Mutex()
    private val receiptLock = Any()
    private var generation = 0L
    private val jobs = mutableMapOf<Long, Job>()
    private val expectedChangeCounts = mutableMapOf<Long, Int>()
    private val unconfirmed = mutableSetOf<Long>()
    private val custodianOwned = mutableSetOf<Long>()
    private val statuses = MutableStateFlow<Map<Long, ClipboardCleanupStatus>>(emptyMap())
    override val cleanupStatuses = statuses.asStateFlow()

    override suspend fun copySensitiveData(
        label: String,
        text: CharArray,
    ): Result<ClipboardCopyReceipt> =
        operation.withLock {
            val receiptGeneration = ++generation
            val admitted =
                synchronized(receiptLock) {
                    if (statuses.value.size >= MAX_RECEIPTS || unconfirmed.isNotEmpty()) {
                        false
                    } else {
                        statuses.value =
                            statuses.value +
                            (
                                receiptGeneration to
                                    ClipboardCleanupStatus(
                                        receiptGeneration,
                                        CleanupOutcome.Pending,
                                        CleanupOrigin.Automatic,
                                    )
                            )
                        true
                    }
                }
            if (!admitted) return@withLock Result.failure(ClipboardError.CopyAdmissionRejected())
            val beforeCount =
                try {
                    pasteboard.changeCount
                } catch (cancellation: CancellationException) {
                    synchronized(receiptLock) { statuses.value = statuses.value - receiptGeneration }
                    throw cancellation
                } catch (_: Exception) {
                    synchronized(receiptLock) { statuses.value = statuses.value - receiptGeneration }
                    return@withLock Result.failure(ClipboardError.CopyFailed("clipboard metadata unavailable"))
                }
            try {
                // UIKit requires an immutable text value at this platform transfer boundary.
                pasteboard.string = String(text)
            } catch (cancellation: CancellationException) {
                synchronized(receiptLock) { statuses.value = statuses.value - receiptGeneration }
                throw cancellation
            } catch (_: Exception) {
                synchronized(receiptLock) { statuses.value = statuses.value - receiptGeneration }
                return@withLock Result.failure(ClipboardError.CopyFailed("clipboard publication unavailable"))
            }

            val copiedAt = elapsedRealtimeMs()
            val expectedCount = beforeCount + CHANGE_COUNT_INCREMENT
            expectedChangeCounts[receiptGeneration] = expectedCount
            jobs[receiptGeneration] =
                scope.launch {
                    try {
                        val remainingDelay = copiedAt + CLIPBOARD_CLEANUP_DELAY_MS - elapsedRealtimeMs()
                        if (remainingDelay > 0L) delay(remainingDelay)
                        attemptAutomatic(receiptGeneration, expectedCount)
                    } catch (cancellation: CancellationException) {
                        recordInterruptedCleanup(receiptGeneration, expectedCount)
                        throw cancellation
                    }
                }

            try {
                // UIKit updates changeCount at the end of the event loop. The timer is already
                // retained before yielding, so caller cancellation cannot lose cleanup work.
                yield()
                val confirmed =
                    try {
                        pasteboard.changeCount == expectedCount
                    } catch (cancellation: CancellationException) {
                        unconfirmed.add(receiptGeneration)
                        transferToCustodian(receiptGeneration)
                        throw cancellation
                    } catch (_: Exception) {
                        false
                    }
                if (confirmed) {
                    expectedChangeCounts.keys.toList().filter { it != receiptGeneration }.forEach { previous ->
                        expectedChangeCounts.remove(previous)
                        unconfirmed.remove(previous)
                        jobs.remove(previous)?.cancel()
                        finishReceipt(previous, CleanupOutcome.Superseded, CleanupOrigin.Automatic)
                    }
                } else {
                    unconfirmed.add(receiptGeneration)
                }
                Result.success(
                    ClipboardCopyReceipt(
                        receiptGeneration,
                        copiedAt + CLIPBOARD_CLEANUP_DELAY_MS,
                        if (confirmed) PublicationStatus.MetadataConfirmed else PublicationStatus.Unconfirmed,
                    ),
                )
            } catch (cancellation: CancellationException) {
                unconfirmed.add(receiptGeneration)
                transferToCustodian(receiptGeneration)
                throw cancellation
            }
        }

    private suspend fun attemptAutomatic(
        receiptGeneration: Long,
        expectedCount: Int,
    ) = operation.withLock {
        if (expectedChangeCounts[receiptGeneration] != expectedCount) return@withLock
        val currentChangeCount =
            try {
                pasteboard.changeCount
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        val outcome =
            if (currentChangeCount == null) {
                CleanupOutcome.SkippedOwnershipUnavailable
            } else if (currentChangeCount != expectedCount) {
                CleanupOutcome.SkippedReplaced
            } else {
                try {
                    pasteboard.string = null
                    CleanupOutcome.ClearRequestSubmitted
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    CleanupOutcome.ClearRejected
                }
            }
        jobs.remove(receiptGeneration)
        expectedChangeCounts.remove(receiptGeneration)
        unconfirmed.remove(receiptGeneration)
        finishReceipt(receiptGeneration, outcome, CleanupOrigin.Automatic)
    }

    private fun recordInterruptedCleanup(
        receiptGeneration: Long,
        expectedCount: Int,
    ) {
        // Intentional supersede/manual cancellation removes the expected count first.
        // A still-current scheduled job interrupted while this process is active gets an
        // explicit non-success outcome and releases its one-shot cleanup record.
        scope.launch {
            operation.withLock {
                if (expectedChangeCounts[receiptGeneration] != expectedCount) return@withLock
                jobs.remove(receiptGeneration)
                expectedChangeCounts.remove(receiptGeneration)
                unconfirmed.remove(receiptGeneration)
                finishReceipt(receiptGeneration, CleanupOutcome.Interrupted, CleanupOrigin.Automatic)
            }
        }
    }

    private fun elapsedRealtimeMs(): Long = (NSProcessInfo.processInfo.systemUptime * MILLIS_PER_SECOND).toLong()

    override suspend fun clearClipboard(): Result<Unit> =
        operation.withLock {
            try {
                pasteboard.string = null
                jobs.keys.toList().forEach { generation ->
                    expectedChangeCounts.remove(generation)
                    unconfirmed.remove(generation)
                    jobs.remove(generation)?.cancel()
                    finishReceipt(generation, CleanupOutcome.ManualClearSubmitted, CleanupOrigin.Manual)
                }
                Result.success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Result.failure(ClipboardError.ClearFailed("clipboard clear unavailable"))
            }
        }

    override fun acknowledge(generation: Long) = releaseTerminal(generation)

    override fun abandon(generation: Long) {
        synchronized(receiptLock) {
            val current = statuses.value[generation] ?: return
            if (current.outcome != CleanupOutcome.Pending) {
                setOutcome(generation, CleanupOutcome.Abandoned, current.origin)
                releaseTerminal(generation)
            }
        }
    }

    override fun transferToCustodian(generation: Long) {
        synchronized(receiptLock) {
            val current = statuses.value[generation] ?: return
            if (current.outcome == CleanupOutcome.Pending) {
                custodianOwned.add(generation)
            } else {
                setOutcome(generation, CleanupOutcome.Abandoned, current.origin)
                releaseTerminal(generation)
            }
        }
    }

    private fun finishReceipt(
        generation: Long,
        outcome: CleanupOutcome,
        origin: CleanupOrigin,
    ) {
        synchronized(receiptLock) {
            if (statuses.value[generation] == null) return
            if (custodianOwned.remove(generation)) {
                setOutcome(generation, CleanupOutcome.Abandoned, origin)
                releaseTerminal(generation)
            } else {
                setOutcome(generation, outcome, origin)
            }
        }
    }

    private fun releaseTerminal(generation: Long) {
        synchronized(receiptLock) {
            if (statuses.value[generation]?.outcome != CleanupOutcome.Pending) {
                statuses.value = statuses.value - generation
                custodianOwned.remove(generation)
            }
        }
    }

    private fun setOutcome(
        generation: Long,
        outcome: CleanupOutcome,
        origin: CleanupOrigin,
    ) {
        statuses.value = statuses.value + (generation to ClipboardCleanupStatus(generation, outcome, origin))
    }

    private companion object {
        const val MAX_RECEIPTS = 2
        const val CHANGE_COUNT_INCREMENT = 1
        const val MILLIS_PER_SECOND = 1000.0
    }
}
