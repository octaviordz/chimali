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

/** Synthetic exploratory pilot: one process supplies one clock and two paired controls. */
@RunWith(AndroidJUnit4::class)
class FilteredMeasurementPilotDeviceTest {
    @Test
    fun collectOnePilotSession() =
        runBlocking {
            assumeTrue(
                "Android Q and enabled tracing required",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled(),
            )
            val arguments = InstrumentationRegistry.getArguments()
            val runId = requireNotNull(arguments.getString("run_id"))
            require(runId.matches(Regex("f062-measure-pilot-01-(quiet|cpu_load)-[0-9]{3}")))
            val condition = requireNotNull(arguments.getString("condition"))
            require(condition in listOf("quiet", "cpu_load") && runId.contains("-$condition-"))
            val sampleIndex = runId.substringAfterLast('-').toInt()
            val output = File(InstrumentationRegistry.getInstrumentation().context.filesDir, "$runId.csv")
            check(!output.exists()) { "Refusing to overwrite a pilot session" }
            val session = UUID.randomUUID().toString()
            val rows =
                StringBuilder(
                    "run_id,session,condition,kind,scenario,index,cookie,start_ns,end_ns," +
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
                        name = "f062-measure-pilot-load"
                        start()
                    }
                } else {
                    null
                }
            try {
                var previous = SystemClock.elapsedRealtimeNanos()
                repeat(INCREMENTS) { index ->
                    val current = SystemClock.elapsedRealtimeNanos()
                    rows.append("$runId,$session,$condition,clock_increment,none,$index,0,$previous,$current,,,,\n")
                    previous = current
                }
                val warmupCookie = FIRST_CLOCK_COOKIE + sampleIndex * 2
                appendClock(rows, runId, session, condition, "clock_warmup", warmupCookie)
                appendClock(rows, runId, session, condition, "clock", warmupCookie + 1)
                var operationCookie = 1
                for (handoff in listOf(false, true)) {
                    val scenario = if (handoff) "dispatcher_handoff" else "immediate"
                    for (warmup in listOf(true, false)) {
                        val enabledFirst = (sampleIndex % 2 == 0) xor warmup
                        val enabled: Long
                        val disabled: Long
                        if (enabledFirst) {
                            enabled = measure(session, operationCookie++, true, handoff)
                            disabled = measure(session, operationCookie++, false, handoff)
                        } else {
                            disabled = measure(session, operationCookie++, false, handoff)
                            enabled = measure(session, operationCookie++, true, handoff)
                        }
                        val kind = if (warmup) "control_warmup" else "control"
                        rows.append("$runId,$session,$condition,$kind,$scenario,0,0,,,$enabledFirst,")
                        rows.append("$enabled,$disabled,${enabled - disabled}\n")
                    }
                }
                output.writeText(rows.toString())
            } finally {
                running.set(false)
                worker?.join()
            }
        }

    private fun appendClock(
        rows: StringBuilder,
        runId: String,
        session: String,
        condition: String,
        kind: String,
        cookie: Int,
    ) {
        val start = SystemClock.elapsedRealtimeNanos()
        val marker = "$CLOCK_MARKER.$cookie"
        Trace.beginAsyncSection(marker, cookie)
        Trace.endAsyncSection(marker, cookie)
        val end = SystemClock.elapsedRealtimeNanos()
        rows.append("$runId,$session,$condition,$kind,none,0,$cookie,$start,$end,,,,\n")
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
        const val CLOCK_MARKER = "Fido2.Calibration.ClockBracket"
        const val FIRST_CLOCK_COOKIE = 8_500_000
        const val INCREMENTS = 1_000
        const val CPU_LOAD_MULTIPLIER = 1_664_525L
        const val CPU_LOAD_INCREMENT = 1_013_904_223L
    }
}
