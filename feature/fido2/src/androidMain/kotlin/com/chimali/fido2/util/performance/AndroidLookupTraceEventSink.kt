package com.chimali.fido2.util.performance

import android.os.Build
import android.os.Trace
import android.util.Log
import com.chimali.core.common.annotation.Req

/** Android trace/logcat sink for secret-free, process-local lookup correlation. */
internal object AndroidLookupTraceEventSink : CoroutineTraceEventSink {
    private const val TAG = "ChimaliT062"
    private const val SEGMENT_NAME = "Fido2.Registration.SettingsLookup.Coroutine"

    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun mapping(
        context: Fido2OperationTraceContext,
        lookupCookie: Int,
        accepted: Boolean,
    ) {
        emit(
            "event=lookup_mapping process_session=${context.processSession} " +
                "operation_cookie=${context.operationCookie} lookup_trace_cookie=$lookupCookie accepted=$accepted",
        )
    }

    @Req("NFR-PERF-030", "TR-AUTH-031")
    override fun resumed(
        context: Fido2OperationTraceContext,
        traceCookie: Int,
        threadId: Long,
        paired: Boolean,
    ) {
        if (paired) trace("begin", traceCookie)
        emit(
            "event=coroutine_resume process_session=${context.processSession} " +
                "operation_cookie=${context.operationCookie} " +
                "lookup_trace_cookie=${context.correlation.lookupCookie()} " +
                "segment_cookie=$traceCookie thread_id=$threadId paired=$paired",
        )
    }

    @Req("NFR-PERF-030", "TR-AUTH-031")
    override fun suspended(
        context: Fido2OperationTraceContext,
        traceCookie: Int,
        threadId: Long,
        paired: Boolean,
    ) {
        if (paired) trace("end", traceCookie)
        emit(
            "event=coroutine_suspend process_session=${context.processSession} " +
                "operation_cookie=${context.operationCookie} " +
                "lookup_trace_cookie=${context.correlation.lookupCookie()} " +
                "segment_cookie=$traceCookie thread_id=$threadId paired=$paired",
        )
    }

    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun terminal(
        context: Fido2OperationTraceContext,
        outcome: String,
        valid: Boolean,
        complete: Boolean,
    ) {
        emit(
            "event=lookup_terminal process_session=${context.processSession} " +
                "operation_cookie=${context.operationCookie} " +
                "lookup_trace_cookie=${context.correlation.lookupCookie()} " +
                "outcome=$outcome valid=$valid complete=$complete",
        )
    }

    private fun trace(
        action: String,
        cookie: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !Trace.isEnabled()) {
            return
        }
        runCatching {
            if (action == "begin") {
                Trace.beginAsyncSection(SEGMENT_NAME, cookie)
            } else {
                Trace.endAsyncSection(SEGMENT_NAME, cookie)
            }
        }
    }

    private fun emit(message: String) {
        runCatching { Log.println(Log.INFO, TAG, message) }
    }
}
