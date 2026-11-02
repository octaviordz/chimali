package com.chimali.fido2.util.performance

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.chimali.core.common.annotation.Req
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Secret-free empirical calibration runner; never invokes an RP or registration path. */
@RunWith(AndroidJUnit4::class)
class EmpiricalCalibrationDeviceTest {
    @Test
    @Req("NFR-PERF-030", "TR-AUTH-031")
    fun collectOneIndependentStatisticalHoldoutSession() =
        runBlocking {
            assumeTrue(
                "Calibration requires Android Q or newer",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            )
            assumeTrue("Start Perfetto with com.chimali.fido2.test in atrace_apps", Trace.isEnabled())

            val arguments = InstrumentationRegistry.getArguments()
            val phase =
                arguments.getString("calibration_phase")
                    ?: error("Set -e calibration_phase=holdout")
            require(
                phase == "holdout",
            ) { "The approved statistical protocol accepts independent holdout sessions only" }
            val candidateC = arguments.getString("candidate_c_ns")?.toLongOrNull()
            val candidateH = arguments.getString("candidate_h_ns")?.toLongOrNull()
            require(candidateC == FROZEN_CANDIDATE_C_NS && candidateH == FROZEN_CANDIDATE_H_NS) {
                "Supply the approved frozen candidates C=$FROZEN_CANDIDATE_C_NS and H=$FROZEN_CANDIDATE_H_NS"
            }
            val runId = arguments.getString("run_id") ?: error("Set -e run_id=f062-asus35-calibration-NN")
            require(runId.matches(Regex("f062-asus35-calibration-[0-9]{2}-(quiet|cpu_load)-[0-9]{3}"))) {
                "Invalid independent holdout run ID"
            }
            val condition =
                arguments.getString("calibration_condition")
                    ?: error("Set -e calibration_condition=quiet or cpu_load")
            require(condition == "quiet" || condition == "cpu_load") { "Unsupported calibration condition" }
            require(runId.contains("-$condition-")) { "Run ID condition must match calibration_condition" }
            val output =
                File(
                    InstrumentationRegistry.getInstrumentation().context.filesDir,
                    "$runId-$phase.csv",
                )
            output.parentFile?.mkdirs()
            val processSession = UUID.randomUUID().toString()
            output.writeText(
                "run_id,process_session,batch,condition,kind,index,cookie,start_ns,end_ns," +
                    "enabled_ns,disabled_ns,delta_ns,enabled_first\n",
            )
            output.appendText("$runId,$processSession,holdout,declared,allowance,0,0,,,$candidateC,$candidateH,\n")
            var nextCookie = FIRST_COOKIE

            val sampleId = runId.substringAfterLast('-')
            val sampleIndex = sampleId.toInt()
            val batch = Batch("holdout-$condition-$sampleId", condition)
            run {
                val load = if (batch.condition == "cpu_load") startCpuLoad() else null
                try {
                    output.appendText(
                        "$runId,$processSession,${batch.name},${batch.condition},batch_start,0,0," +
                            "${SystemClock.elapsedRealtimeNanos()},,,,\n",
                    )
                    val warmupCookie = nextCookie++
                    val warmupStart = SystemClock.elapsedRealtimeNanos()
                    val warmupMarker = "$CLOCK_MARKER.$warmupCookie"
                    Trace.beginAsyncSection(warmupMarker, warmupCookie)
                    Trace.endAsyncSection(warmupMarker, warmupCookie)
                    val warmupEnd = SystemClock.elapsedRealtimeNanos()
                    output.appendText(
                        "$runId,$processSession,${batch.name},${batch.condition},clock_warmup,0," +
                            "$warmupCookie,$warmupStart,$warmupEnd,,,,\n",
                    )
                    val warmupEnabled = timedEnabledControl(processSession, nextCookie++)
                    val warmupDisabled = timedDisabledControl()
                    val warmupControlPrefix =
                        "$runId,$processSession,${batch.name},${batch.condition},control_warmup,0,${nextCookie - 1},,,"
                    output.appendText(
                        "$warmupControlPrefix$warmupEnabled,$warmupDisabled," +
                            "${warmupEnabled - warmupDisabled},true\n",
                    )
                    var previousClock = SystemClock.elapsedRealtimeNanos()
                    val clockIncrements = StringBuilder()
                    repeat(CLOCK_INCREMENT_PROBE_COUNT) { index ->
                        val currentClock = SystemClock.elapsedRealtimeNanos()
                        clockIncrements.append(
                            "$runId,$processSession,${batch.name},${batch.condition},clock_increment,$index,0," +
                                "$previousClock,$currentClock,,,,\n",
                        )
                        previousClock = currentClock
                    }
                    output.appendText(clockIncrements.toString())
                    repeat(CLOCK_BRACKET_COUNT) { index ->
                        val cookie = nextCookie++
                        val start = SystemClock.elapsedRealtimeNanos()
                        val markerName = "$CLOCK_MARKER.$cookie"
                        Trace.beginAsyncSection(markerName, cookie)
                        Trace.endAsyncSection(markerName, cookie)
                        val end = SystemClock.elapsedRealtimeNanos()
                        output.appendText(
                            "$runId,$processSession,${batch.name},${batch.condition},clock," +
                                "$index,$cookie,$start,$end,,,,\n",
                        )
                        if ((index + 1) % FLUSH_EVERY == 0) publish(output)
                    }
                    repeat(PAIRED_CONTROL_COUNT) { index ->
                        val enabledFirst = (sampleIndex + index) % 2 == 0
                        val enabled: Long
                        val disabled: Long
                        if (enabledFirst) {
                            enabled = timedEnabledControl(processSession, nextCookie++)
                            disabled = timedDisabledControl()
                        } else {
                            disabled = timedDisabledControl()
                            enabled = timedEnabledControl(processSession, nextCookie++)
                        }
                        val rowPrefix =
                            "$runId,$processSession,${batch.name},${batch.condition},control," +
                                "$index,${nextCookie - 1},,,"
                        val row = "$rowPrefix$enabled,$disabled,${enabled - disabled},$enabledFirst\n"
                        output.appendText(row)
                        if ((index + 1) % FLUSH_CONTROL_EVERY == 0) publish(output)
                    }
                    output.appendText(
                        "$runId,$processSession,${batch.name},${batch.condition},batch_end,0,0," +
                            "${SystemClock.elapsedRealtimeNanos()},,,,\n",
                    )
                    publish(output)
                } finally {
                    load?.set(false)
                    load?.thread?.join()
                }
            }
        }

    private suspend fun timedEnabledControl(
        processSession: String,
        operationCookie: Int,
    ): Long =
        withContext(Fido2OperationTraceContext(processSession, operationCookie)) {
            val start = SystemClock.elapsedRealtimeNanos()
            PerformanceTrace.section(LOOKUP_SECTION) {
                withContext(Dispatchers.IO) { Thread.yield() }
            }
            SystemClock.elapsedRealtimeNanos() - start
        }

    private suspend fun timedDisabledControl(): Long =
        SystemClock.elapsedRealtimeNanos().let { start ->
            withContext(Dispatchers.IO) { Thread.yield() }
            SystemClock.elapsedRealtimeNanos() - start
        }

    private fun publish(file: File) {
        PlatformTestStorageRegistry
            .getInstance()
            .openOutputFile(file.name, false)
            .use { it.write(file.readBytes()) }
    }

    private fun startCpuLoad(): LoadWorker {
        val running = AtomicBoolean(true)
        val worker =
            Thread {
                var value = 1L
                while (running.get()) {
                    value = value * CPU_LOAD_MULTIPLIER + CPU_LOAD_INCREMENT
                }
                check(value != Long.MIN_VALUE)
            }.apply {
                name = "f062-calibration-load"
                isDaemon = true
                start()
            }
        return LoadWorker(running, worker)
    }

    private data class LoadWorker(
        val running: AtomicBoolean,
        val thread: Thread,
    ) {
        fun set(value: Boolean) = running.set(value)
    }

    private data class Batch(
        val name: String,
        val condition: String,
    )

    private companion object {
        const val LOOKUP_SECTION = "Fido2.Registration.SettingsLookup"
        const val CLOCK_MARKER = "Fido2.Calibration.ClockBracket"
        const val FIRST_COOKIE = 8_400_000
        const val FROZEN_CANDIDATE_C_NS = 530_677L
        const val FROZEN_CANDIDATE_H_NS = 19_442_969L
        const val CLOCK_BRACKET_COUNT = 1
        const val PAIRED_CONTROL_COUNT = 1
        const val CLOCK_INCREMENT_PROBE_COUNT = 1_000
        const val FLUSH_EVERY = 100
        const val FLUSH_CONTROL_EVERY = 10
        const val CPU_LOAD_MULTIPLIER = 1_664_525L
        const val CPU_LOAD_INCREMENT = 1_013_904_223L
    }
}
