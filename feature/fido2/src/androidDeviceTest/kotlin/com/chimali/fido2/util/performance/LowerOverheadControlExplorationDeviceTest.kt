package com.chimali.fido2.util.performance

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

/** Paired, synthetic-only diagnostic. Results cannot count as a holdout or registration sample. */
@RunWith(AndroidJUnit4::class)
class LowerOverheadControlExplorationDeviceTest {
    @Test
    fun collectMatchedControls() =
        runBlocking {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()) {
                "Android Q and enabled tracing required"
            }
            val args = InstrumentationRegistry.getArguments()
            val runId = requireNotNull(args.getString("run_id"))
            require(runId.matches(Regex("f062-overhead-explore-07-(quiet|cpu_load)")))
            val condition = requireNotNull(args.getString("condition"))
            require(condition in listOf("quiet", "cpu_load") && runId.endsWith("-$condition"))
            val orderSeed =
                requireNotNull(args.getString("order_seed")).toLongOrNull()
                    ?: error("Set -e order_seed=<frozen integer>")

            val output = File(InstrumentationRegistry.getInstrumentation().context.filesDir, "$runId.csv")
            check(!output.exists()) { "Refusing to overwrite an exploratory session" }
            val processSession = UUID.randomUUID().toString()
            val rows =
                StringBuilder(
                    "run_id,process_session,condition,scenario,pair_index,warmup,order_seed,enabled_first," +
                        "enabled_operation_cookie,disabled_operation_cookie,enabled_start_ns,enabled_end_ns," +
                        "enabled_ns,disabled_start_ns,disabled_end_ns,disabled_ns,delta_ns\n",
                )
            val running = AtomicBoolean(condition == "cpu_load")
            val worker = if (running.get()) startCpuLoad(running) else null
            try {
                var nextOperationCookie = FIRST_OPERATION_COOKIE
                for (scenario in SCENARIOS) {
                    val measuredOrder = balancedOrder(orderSeed xor scenario.orderSalt)
                    repeat(WARMUPS + PAIRS) { pairIndex ->
                        val warmup = pairIndex < WARMUPS
                        val enabledFirst =
                            if (warmup) {
                                (pairIndex + scenario.orderSalt.toInt()) % 2 == 0
                            } else {
                                measuredOrder[pairIndex - WARMUPS]
                            }
                        val enabled: TimedArm
                        val disabled: TimedArm
                        if (enabledFirst) {
                            enabled = measure(processSession, nextOperationCookie++, true, scenario)
                            disabled = measure(processSession, nextOperationCookie++, false, scenario)
                        } else {
                            disabled = measure(processSession, nextOperationCookie++, false, scenario)
                            enabled = measure(processSession, nextOperationCookie++, true, scenario)
                        }
                        rows.append(
                            "$runId,$processSession,$condition,${scenario.name},$pairIndex,$warmup," +
                                "${orderSeed xor scenario.orderSalt},$enabledFirst,${enabled.operationCookie}," +
                                "${disabled.operationCookie},${enabled.startNs},${enabled.endNs}," +
                                "${enabled.durationNs},${disabled.startNs},${disabled.endNs}," +
                                "${disabled.durationNs}," +
                                "${enabled.durationNs - disabled.durationNs}\n",
                        )
                    }
                }
                output.writeText(rows.toString())
            } finally {
                running.set(false)
                worker?.join()
            }
        }

    private suspend fun measure(
        processSession: String,
        operationCookie: Int,
        enabled: Boolean,
        scenario: Scenario,
    ): TimedArm =
        Fido2OperationTraceContext(processSession, operationCookie).let { operationContext ->
            withContext(operationContext) {
                val start = SystemClock.elapsedRealtimeNanos()
                if (enabled) {
                    PerformanceTrace.section(LOOKUP_SECTION) { runWorkload(scenario) }
                } else {
                    runWorkload(scenario)
                }
                val end = SystemClock.elapsedRealtimeNanos()
                if (enabled) {
                    check(operationContext.correlation.hasLookupMapping()) {
                        "Enabled operation did not emit its lookup mapping"
                    }
                    // The production terminal event may be deferred until the final
                    // restoreThreadContext callback emits its suspend marker. Keep that
                    // asynchronous completion wait outside the measured interval above.
                    withTimeout(CORRELATION_COMPLETION_TIMEOUT_MS) {
                        while (!operationContext.correlation.isComplete()) delay(1)
                    }
                } else {
                    check(
                        !operationContext.correlation.hasLookupMapping() && !operationContext.correlation.isComplete(),
                    ) {
                        "Disabled control unexpectedly emitted lookup correlation"
                    }
                }
                TimedArm(operationCookie, start, end, end - start)
            }
        }

    /** Both arms call this same routine; only the enabled arm installs production trace hooks. */
    private suspend fun runWorkload(scenario: Scenario) {
        when (scenario.name) {
            "historical_yield" -> withContext(Dispatchers.IO) { Thread.yield() }
            "fixed_lifecycle" -> {
                repeat(FIXED_IO_HANDOFFS) { withContext(Dispatchers.IO) { Unit } }
                suspendCancellableCoroutine<Unit> { continuation ->
                    Dispatchers.IO.dispatch(continuation.context) { continuation.resume(Unit) }
                }
            }
            else -> error("Unknown scenario: ${scenario.name}")
        }
    }

    private fun balancedOrder(seed: Long): List<Boolean> {
        val order =
            (MutableList(PAIRS / 2) { true } + MutableList(PAIRS / 2) { false }).toMutableList()
        val random = Random(seed)
        for (index in order.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = order[index]
            order[index] = order[other]
            order[other] = value
        }
        return order
    }

    private fun startCpuLoad(running: AtomicBoolean): Thread =
        Thread {
            var value = 1L
            while (running.get()) value = value * CPU_LOAD_MULTIPLIER + CPU_LOAD_INCREMENT
            check(value != Long.MIN_VALUE)
        }.apply {
            name = "f062-overhead-explore-load"
            isDaemon = true
            start()
        }

    private data class Scenario(
        val name: String,
        val orderSalt: Long,
    )

    private data class TimedArm(
        val operationCookie: Int,
        val startNs: Long,
        val endNs: Long,
        val durationNs: Long,
    )

    private companion object {
        const val LOOKUP_SECTION = "Fido2.Registration.SettingsLookup"
        const val FIRST_OPERATION_COOKIE = 9_300_000
        const val WARMUPS = 5
        const val PAIRS = 40
        const val FIXED_IO_HANDOFFS = 3
        const val CORRELATION_COMPLETION_TIMEOUT_MS = 5_000L
        const val CPU_LOAD_MULTIPLIER = 1_664_525L
        const val CPU_LOAD_INCREMENT = 1_013_904_223L
        val SCENARIOS =
            listOf(
                Scenario("historical_yield", 0x103L),
                Scenario("fixed_lifecycle", 0x205L),
            )
    }
}
