package com.chimali.fido2.util.performance

import android.os.Process
import com.chimali.core.common.annotation.Req
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/** Injectable event boundary so lifecycle pairing and dispatcher propagation can be tested on host. */
internal interface CoroutineTraceEventSink {
    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun resumed(
        context: Fido2OperationTraceContext,
        traceCookie: Int,
        threadId: Long,
        paired: Boolean,
    )

    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun suspended(
        context: Fido2OperationTraceContext,
        traceCookie: Int,
        threadId: Long,
        paired: Boolean,
    )
}

/** Marks each execution segment of the lookup coroutine, including dispatcher-thread changes. */
internal class CoroutineTraceLifecycle(
    private val operationContext: Fido2OperationTraceContext,
    private val nextTraceCookie: AtomicInteger,
    private val eventSink: CoroutineTraceEventSink,
    private val threadIdProvider: () -> Long = { Process.myTid().toLong() },
) : AbstractCoroutineContextElement(Key),
    ThreadContextElement<CoroutineTraceLifecycle.SegmentState> {
    internal data class SegmentState(
        val traceCookie: Int,
        val threadId: Long,
        val paired: Boolean,
    )

    companion object Key : CoroutineContext.Key<CoroutineTraceLifecycle>

    @Req("NFR-PERF-030", "TR-AUTH-031")
    override fun updateThreadContext(context: CoroutineContext): SegmentState {
        val traceCookie = nextTraceCookie.getAndIncrement()
        val threadId = threadIdProvider()
        val paired = operationContext.correlation.startSegment(traceCookie)
        eventSink.resumed(operationContext, traceCookie, threadId, paired)
        return SegmentState(traceCookie, threadId, paired)
    }

    @Req("NFR-PERF-030", "TR-AUTH-031")
    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: SegmentState,
    ) {
        val closing = operationContext.correlation.beginSegmentRestore(oldState.traceCookie)
        val paired = closing && oldState.paired
        try {
            eventSink.suspended(operationContext, oldState.traceCookie, threadIdProvider(), paired)
        } finally {
            if (closing) operationContext.correlation.finishSegmentRestore(oldState.traceCookie)
        }
    }
}
