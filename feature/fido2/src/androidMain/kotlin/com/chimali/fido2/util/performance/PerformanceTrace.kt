package com.chimali.fido2.util.performance

import android.os.Build
import android.os.Trace
import com.chimali.core.common.annotation.Req
import java.util.concurrent.atomic.AtomicInteger

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
        try {
            return block()
        } finally {
            if (cookie != null) Trace.endAsyncSection(name, cookie)
        }
    }
}
