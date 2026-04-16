package com.chimali.fido2.util.performance

import timber.log.Timber

/**
 * Lightweight local-only profiler for measuring FIDO2 HID operation latency.
 *
 * Used to verify compliance with NFR-PERF-030 (<200ms end-to-end HID latency).
 * Results are emitted only to Timber (local Logcat) and never uploaded.
 *
 * Usage:
 * ```kotlin
 * LatencyProfiler.start("MakeCredential")
 * // ... operation ...
 * LatencyProfiler.end("MakeCredential")
 * ```
 */
object LatencyProfiler {
    private val startTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val userInteractionStarts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val userAccumulatedMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Records the start time for an operation identified by [id].
     */
    fun start(id: String) {
        startTimes[id] = System.nanoTime()
        userAccumulatedMs[id] = 0L
    }

    /**
     * Starts timing a user interaction window (e.g. BiometricPrompt).
     * This time will be subtracted from the total in [end].
     */
    fun startUserInteraction(id: String) {
        userInteractionStarts[id] = System.nanoTime()
    }

    /**
     * Ends a user interaction window and accumulates the duration.
     */
    fun endUserInteraction(id: String) {
        val startNs = userInteractionStarts.remove(id) ?: return
        val durationMs = (System.nanoTime() - startNs) / 1_000_000L
        val current = userAccumulatedMs[id] ?: 0L
        userAccumulatedMs[id] = current + durationMs
    }

    /**
     * Computes and logs the elapsed time since [start] was called for [id],
     * subtracting any recorded user interaction time.
     *
     * @return Pure system latency in milliseconds.
     */
    fun end(id: String): Long {
        val startNs =
            startTimes.remove(id) ?: run {
                Timber.w("LatencyProfiler.end called without matching start for id='%s'", id)
                return -1L
            }
        val userMs = userAccumulatedMs.remove(id) ?: 0L
        val totalMs = (System.nanoTime() - startNs) / 1_000_000L
        val pureMs = maxOf(0L, totalMs - userMs)

        val compliance = if (pureMs < 200) "✅ PASS" else "❌ OVER BUDGET"
        Timber.d(
            "[NFR-PERF-030] %s | %s = %dms (Total: %dms, User: %dms)",
            compliance,
            id,
            pureMs,
            totalMs,
            userMs,
        )
        return pureMs
    }
}
