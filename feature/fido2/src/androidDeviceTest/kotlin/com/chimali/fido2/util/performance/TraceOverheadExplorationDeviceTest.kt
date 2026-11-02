package com.chimali.fido2.util.performance

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exploratory paired synthetic controls. No sample is a calibration holdout. */
@RunWith(AndroidJUnit4::class)
class TraceOverheadExplorationDeviceTest {
    @Test
    fun collectMatchedControls() =
        runBlocking {
            assumeTrue(
                "Android Q and enabled tracing required",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled(),
            )
            val args = InstrumentationRegistry.getArguments()
            val runId = requireNotNull(args.getString("run_id"))
            require(runId.matches(Regex("f062-overhead-explore-[0-9]{2}-(quiet|cpu_load)")))
            val condition = requireNotNull(args.getString("condition"))
            require(condition in listOf("quiet", "cpu_load") && runId.endsWith("-$condition"))
            val output = File(InstrumentationRegistry.getInstrumentation().context.filesDir, "$runId.csv")
            check(!output.exists()) { "Refusing to overwrite an exploratory session" }
            val session = UUID.randomUUID().toString()
            val rows =
                StringBuilder(
                    "run_id,session,condition,scenario,index,warmup," +
                        "enabled_first,enabled_ns,disabled_ns,delta_ns\n",
                )
            val running = AtomicBoolean(condition == "cpu_load")
            val worker =
                if (running.get()) {
                    Thread {
                        var value = 1L
                        while (running.get()) value = value * CPU_LOAD_MULTIPLIER + CPU_LOAD_INCREMENT
                        check(value != Long.MIN_VALUE)
                    }.apply {
                        name = "f062-explore-load"
                        start()
                    }
                } else {
                    null
                }
            try {
                var cookie = 1
                for (handoff in listOf(false, true)) {
                    repeat(WARMUPS + PAIRS) { index ->
                        val enabledFirst = index % 2 == 0
                        val enabled: Long
                        val disabled: Long
                        if (enabledFirst) {
                            enabled = measure(session, cookie++, true, handoff)
                            disabled = measure(session, cookie++, false, handoff)
                        } else {
                            disabled = measure(session, cookie++, false, handoff)
                            enabled = measure(session, cookie++, true, handoff)
                        }
                        val scenario = if (handoff) "dispatcher_handoff" else "immediate"
                        rows.append("$runId,$session,$condition,$scenario,$index,${index < WARMUPS},")
                        rows.append("$enabledFirst,$enabled,$disabled,${enabled - disabled}\n")
                    }
                }
                output.writeText(rows.toString())
            } finally {
                running.set(false)
                worker?.join()
            }
        }

    private suspend fun measure(
        session: String,
        cookie: Int,
        enabled: Boolean,
        handoff: Boolean,
    ): Long =
        withContext(Fido2OperationTraceContext(session, cookie)) {
            val start = SystemClock.elapsedRealtimeNanos()
            if (enabled) {
                PerformanceTrace.section(LOOKUP_SECTION) { workload(handoff) }
            } else {
                workload(handoff)
            }
            SystemClock.elapsedRealtimeNanos() - start
        }

    private suspend fun workload(handoff: Boolean) {
        if (handoff) {
            withContext(Dispatchers.IO) { yield() }
        }
    }

    private companion object {
        const val LOOKUP_SECTION = "Fido2.Registration.SettingsLookup"
        const val WARMUPS = 5
        const val PAIRS = 40
        const val CPU_LOAD_MULTIPLIER = 1_664_525L
        const val CPU_LOAD_INCREMENT = 1_013_904_223L
    }
}
