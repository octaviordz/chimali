package com.chimali.fido2.util.performance

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Lightweight local-only profiler for measuring FIDO2 HID operation latency.
 *
 * Records adjusted app-side latency through local Bluetooth HID API acceptance. This is not the
 * RP-to-verified-response end-to-end boundary and is not the 200ms internal-processing metric.
 * Results are emitted only to Timber (local Logcat) and never uploaded.
 *
 * Usage:
 * ```kotlin
 * LatencyProfiler.start(LatencyOperation.MAKE_CREDENTIAL)
 * // ... operation ...
 * LatencyProfiler.end(LatencyOperation.MAKE_CREDENTIAL, responseAccepted)
 * ```
 */
enum class LatencyOperation(
    val logLabel: String,
) {
    MAKE_CREDENTIAL("MakeCredential"),
    GET_ASSERTION("GetAssertion"),
    GET_INFO("GetInfo"),
    OTHER("Other CTAP operation"),
}

object LatencyProfiler {
    private val startTimes = java.util.concurrent.ConcurrentHashMap<LatencyOperation, Long>()
    private val userInteractionStarts = java.util.concurrent.ConcurrentHashMap<LatencyOperation, Long>()
    private val userAccumulatedMs = java.util.concurrent.ConcurrentHashMap<LatencyOperation, Long>()

    private const val NS_TO_MS_CONVERSION = 1_000_000L

    /**
     * Records the start time for a fixed [LatencyOperation].
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun start(operation: LatencyOperation) {
        BaselineMeasurements.start(operation)
        startTimes[operation] = System.nanoTime()
        userAccumulatedMs[operation] = 0L
    }

    /**
     * Starts timing a user interaction window (e.g. BiometricPrompt).
     * This time will be subtracted from the total in [end].
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun startUserInteraction(operation: LatencyOperation) {
        BaselineMeasurements.startUserInteraction(operation)
        userInteractionStarts[operation] = System.nanoTime()
    }

    /**
     * Ends a user interaction window and accumulates the duration.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun endUserInteraction(operation: LatencyOperation) {
        BaselineMeasurements.endUserInteraction(operation)
        val startNs = userInteractionStarts.remove(operation) ?: return
        val durationMs = (System.nanoTime() - startNs) / NS_TO_MS_CONVERSION
        val current = userAccumulatedMs[operation] ?: 0L
        userAccumulatedMs[operation] = current + durationMs
    }

    /**
     * Excludes only the time up to the prompt's confirmation/verification signal. The operation
     * block continues to include derivation, signing, persistence, and response construction.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun <T> untilUserInteractionCompletes(
        latencyOperation: LatencyOperation,
        interactionCompleted: CompletableDeferred<Unit>,
        operation: suspend () -> T,
    ): T = withUserInteractionWindow(interactionCompleted, { endUserInteraction(latencyOperation) }, operation)

    /**
     * Computes and logs the elapsed local HID handoff time since [start] was called for [operation],
     * subtracting any recorded user interaction time. [responseAccepted] means
     * Android's HID wrapper accepted each final response report; it does not
     * confirm that the remote host received or processed those reports.
     *
     * @return Pure system latency in milliseconds.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun end(
        operation: LatencyOperation,
        responseAccepted: Boolean,
    ): Long {
        val startNs =
            startTimes.remove(operation) ?: run {
                Logger.w { "LatencyProfiler.end called without matching start" }
                return -1L
            }
        val userMs = userAccumulatedMs.remove(operation) ?: 0L
        val totalMs = (System.nanoTime() - startNs) / NS_TO_MS_CONVERSION
        val pureMs = maxOf(0L, totalMs - userMs)

        Logger.d {
            "[NFR-PERF-030][local-handoff] ${operation.logLabel} = ${pureMs}ms " +
                "(Total: ${totalMs}ms, User: ${userMs}ms, HID reports accepted: $responseAccepted)"
        }
        return pureMs
    }
}

/** Testable signal boundary shared by CTAP response waiting and user-window accounting. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal suspend fun <T> withUserInteractionWindow(
    interactionCompleted: CompletableDeferred<Unit>,
    closeWindow: () -> Unit,
    operation: suspend () -> T,
): T =
    coroutineScope {
        val closed = AtomicBoolean(false)
        val closeOnce = { if (closed.compareAndSet(false, true)) closeWindow() }
        val signalWatcher =
            launch {
                interactionCompleted.await()
                closeOnce()
            }
        try {
            operation()
        } finally {
            interactionCompleted.complete(Unit)
            closeOnce()
            signalWatcher.cancelAndJoin()
        }
    }
