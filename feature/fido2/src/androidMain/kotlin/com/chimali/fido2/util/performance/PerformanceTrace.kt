package com.chimali.fido2.util.performance

import android.os.Build
import android.os.Trace
import com.chimali.core.common.annotation.Req
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** Adds low-overhead, secret-free async sections to Android system traces. */
internal object PerformanceTrace {
    private val nextCookie = AtomicInteger(1)

    @Req("NFR-PERF-030", "TR-AUTH-031")
    suspend fun <T> section(
        name: String,
        block: suspend () -> T,
    ): T {
        val cookie =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                nextCookie.getAndIncrement().also { Trace.beginAsyncSection(name, it) }
            } else {
                null
            }
        val operationContext =
            if (name == LOOKUP_SECTION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()) {
                currentCoroutineContext()[Fido2OperationTraceContext]
            } else {
                null
            }
        try {
            if (operationContext == null) return block()

            val lookupCookie = cookie ?: return block()
            val mapped = operationContext.correlation.recordLookupCookie(lookupCookie)
            AndroidLookupTraceEventSink.mapping(operationContext, lookupCookie, mapped)
            val lifecycle = CoroutineTraceLifecycle(operationContext, nextCookie, AndroidLookupTraceEventSink)
            var outcome = "returned"
            try {
                val result = runCatching { withContext(lifecycle) { block() } }
                result.exceptionOrNull()?.let { failure ->
                    outcome = if (failure is CancellationException) "cancelled" else "failed"
                }
                return result.getOrThrow()
            } finally {
                // Capture outcome before the finally escapes so the deferred callback uses the
                // correct value even if outcome is mutated by exception handling afterward.
                val capturedOutcome = outcome
                when (
                    operationContext.correlation.recordTerminal {
                        // Deferred path: invoked by the last endSegment call after all
                        // restoreThreadContext calls complete. Emits the terminal diagnostic
                        // in the correct ordering without extending the measured lookup.
                        AndroidLookupTraceEventSink.terminal(
                            operationContext,
                            capturedOutcome,
                            valid = true,
                            complete = operationContext.correlation.isComplete(),
                        )
                    }
                ) {
                    LookupTraceCorrelation.TerminalResult.IMMEDIATE ->
                        AndroidLookupTraceEventSink.terminal(
                            operationContext,
                            outcome,
                            valid = true,
                            complete = operationContext.correlation.isComplete(),
                        )
                    LookupTraceCorrelation.TerminalResult.DEFERRED -> {
                        // Terminal emission delegated to the pending callback in endSegment.
                    }
                    LookupTraceCorrelation.TerminalResult.REJECTED ->
                        // No mapping or duplicate terminal: emit valid=false so the diagnostic
                        // log records the rejection.
                        AndroidLookupTraceEventSink.terminal(
                            operationContext,
                            outcome,
                            valid = false,
                            complete = false,
                        )
                }
            }
        } finally {
            if (cookie != null) Trace.endAsyncSection(name, cookie)
        }
    }

    private const val LOOKUP_SECTION = "Fido2.Registration.SettingsLookup"
}
