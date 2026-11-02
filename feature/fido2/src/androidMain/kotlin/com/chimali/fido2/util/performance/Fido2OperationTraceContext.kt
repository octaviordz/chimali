package com.chimali.fido2.util.performance

import com.chimali.core.common.annotation.Req
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Secret-free correlation context propagated from one CTAP MakeCredential dispatch. */
internal class Fido2OperationTraceContext(
    val processSession: String,
    val operationCookie: Int,
) : AbstractCoroutineContextElement(Key) {
    val correlation = LookupTraceCorrelation()

    companion object Key : CoroutineContext.Key<Fido2OperationTraceContext>
}

/**
 * Validates one lookup mapping and the paired lifecycle segments for a single operation.
 *
 * A pending-terminal callback handles the race where [recordTerminal] is called from the lookup
 * section's finally block while the final [restoreThreadContext] has not yet emitted its suspend
 * marker. When [recordTerminal] observes active or closing segments, it stores the callback; the
 * last [finishSegmentRestore] call invokes it after the marker has been emitted. The terminal is
 * emitted exactly once regardless of ordering.
 */
internal class LookupTraceCorrelation {
    private var lookupTraceCookie: Int? = null
    private val startedSegments = ConcurrentHashMap.newKeySet<Int>()
    private val activeSegments = ConcurrentHashMap.newKeySet<Int>()

    // A segment leaves active state before its suspend marker is emitted. Keep it here until
    // that marker has been written so terminal cannot overtake the final lifecycle event.
    private val closingSegments = ConcurrentHashMap.newKeySet<Int>()
    private val closedSegments = ConcurrentHashMap.newKeySet<Int>()
    private val terminal = AtomicBoolean(false)

    /**
     * Pending terminal callback set by [recordTerminal] when active segments remain.
     * Consumed and invoked by [finishSegmentRestore] when active and closing segments drain.
     */
    private val pendingTerminalCallback = AtomicReference<(() -> Unit)?>(null)

    @Synchronized
    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun recordLookupCookie(traceCookie: Int): Boolean {
        if (traceCookie <= 0 || lookupTraceCookie != null || terminal.get()) return false
        lookupTraceCookie = traceCookie
        return true
    }

    @Req("NFR-PERF-030", "TR-AUTH-031")
    @Synchronized
    fun startSegment(traceCookie: Int): Boolean {
        if (lookupTraceCookie == null || traceCookie <= 0 || terminal.get()) return false
        if (!startedSegments.add(traceCookie)) return false
        return activeSegments.add(traceCookie)
    }

    /** Begins closing a segment; completion waits until its suspend marker has been emitted. */
    @Req("NFR-PERF-030", "TR-AUTH-031")
    @Synchronized
    fun beginSegmentRestore(traceCookie: Int): Boolean {
        if (!activeSegments.remove(traceCookie)) return false
        return closingSegments.add(traceCookie)
    }

    /** Marks a segment fully closed after its suspend diagnostic has been emitted. */
    @Req("NFR-PERF-030", "TR-AUTH-031")
    @Synchronized
    fun finishSegmentRestore(traceCookie: Int): Boolean {
        if (!closingSegments.remove(traceCookie)) return false
        closedSegments.add(traceCookie)
        emitPendingTerminalIfDrained()
        return true
    }

    /** Convenience close for correlation-only callers that have no lifecycle marker to emit. */
    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun endSegment(traceCookie: Int): Boolean {
        if (!beginSegmentRestore(traceCookie)) return false
        return finishSegmentRestore(traceCookie)
    }

    private fun emitPendingTerminalIfDrained() {
        if (activeSegments.isEmpty() && closingSegments.isEmpty()) {
            pendingTerminalCallback.getAndSet(null)?.invoke()
        }
    }

    /**
     * Records the terminal event for this lookup. Returns [TerminalResult] to let the caller
     * distinguish the no-mapping/duplicate-reject case from the deferred-emit race case.
     *
     * - [TerminalResult.IMMEDIATE]: all segments were already closed; caller emits inline.
     * - [TerminalResult.DEFERRED]: segments are active or their suspend markers are being emitted;
     *   [onTerminalReady] is stored and invoked after the last marker. Caller suppresses inline emission.
     * - [TerminalResult.REJECTED]: lookup mapping absent or terminal already set; caller emits
     *   with valid=false to record the rejection.
     */
    @Req("NFR-PERF-030", "TR-AUTH-031")
    @Synchronized
    fun recordTerminal(onTerminalReady: (() -> Unit)? = null): TerminalResult {
        if (lookupTraceCookie == null || !terminal.compareAndSet(false, true)) {
            return TerminalResult.REJECTED
        }
        if (activeSegments.isEmpty() && closingSegments.isEmpty()) {
            return TerminalResult.IMMEDIATE
        }
        // Return/restore race: active or closing segments remain. Store the callback until both
        // sets drain, so terminal cannot overtake a suspend marker.
        if (onTerminalReady != null) {
            pendingTerminalCallback.set(onTerminalReady)
            // Segment restore may finish between the initial check and callback installation.
            // Recheck after storing so that this interleaving cannot strand the callback.
            emitPendingTerminalIfDrained()
        }
        return TerminalResult.DEFERRED
    }

    /** Three-way outcome of [recordTerminal]. */
    enum class TerminalResult { IMMEDIATE, DEFERRED, REJECTED }

    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun isComplete(): Boolean =
        lookupTraceCookie != null &&
            terminal.get() &&
            activeSegments.isEmpty() &&
            closingSegments.isEmpty() &&
            startedSegments == closedSegments

    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun lookupCookie(): Int? = lookupTraceCookie

    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun hasLookupMapping(): Boolean = lookupTraceCookie != null
}
