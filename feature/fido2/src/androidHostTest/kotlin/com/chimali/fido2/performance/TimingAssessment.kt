package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary

enum class OperationDecision { PASS, FAIL, INCOMPLETE }

data class ExclusionRecord(
    val kind: Kind,
    val interval: MonotonicInterval,
) {
    enum class Kind { USER_VERIFICATION, BLUETOOTH_RESPONSE_HANDOFF }
}

data class OperationAssessment(
    val operation: OperationRecord,
    val exclusions: List<ExclusionRecord>,
    val eligibleForDecision: Boolean,
    val rawDurationNanos: Long?,
    val excludedDurationNanos: Long?,
    val assessedDurationNanos: Long?,
    val decision: OperationDecision,
    val reason: String? = null,
)

/** Uses actual System.nanoTime readings; no virtual or injected clock is used for elapsed results. */
object MonotonicMeasurement {
    const val CLOCK_DOMAIN = "jvm:System.nanoTime"
    private const val MAX_CLOCK_DIVERGENCE_NANOS = 5_000_000L

    class Reading internal constructor(
        val monotonicNanos: Long,
        val suspendAwareNanos: Long?,
    )

    fun nowNanos(): Long = System.nanoTime()

    /** Captures both clocks; System.nanoTime remains the sole source of reported elapsed durations. */
    fun begin(): Reading = capture()

    /** Captures both clocks at the end boundary. */
    fun end(): Reading = capture()

    fun interval(
        startNanos: Long,
        endNanos: Long,
    ): MonotonicInterval = MonotonicInterval(startNanos, endNanos, CLOCK_DOMAIN)

    fun interval(
        start: Reading,
        end: Reading,
    ): MonotonicInterval {
        val elapsed = end.monotonicNanos - start.monotonicNanos
        val suspendAwareElapsed =
            if (start.suspendAwareNanos != null && end.suspendAwareNanos != null) {
                end.suspendAwareNanos - start.suspendAwareNanos
            } else {
                null
            }
        val divergence = suspendAwareElapsed?.let { elapsed - it }
        val continuity =
            when {
                elapsed < 0L || suspendAwareElapsed?.let { it < 0L } == true -> ClockContinuity.CLOCK_DISCONTINUITY
                divergence == null -> ClockContinuity.UNAVAILABLE
                divergence > MAX_CLOCK_DIVERGENCE_NANOS -> ClockContinuity.SUSPEND_DETECTED
                divergence < -MAX_CLOCK_DIVERGENCE_NANOS -> ClockContinuity.CLOCK_DISCONTINUITY
                else -> ClockContinuity.VERIFIED
            }
        return MonotonicInterval(
            startNanos = start.monotonicNanos,
            endNanos = end.monotonicNanos,
            clockDomain = CLOCK_DOMAIN,
            continuity = continuity,
            suspendAwareElapsedNanos = suspendAwareElapsed,
            clockDivergenceNanos = divergence,
        )
    }

    private fun capture(): Reading {
        val before = System.nanoTime()
        val suspendAwareNanos = WindowsSuspendClock.readNanosOrNull()
        val after = System.nanoTime()
        return Reading(before + (after - before) / 2L, suspendAwareNanos)
    }

    private object WindowsSuspendClock {
        private val api: QueryUnbiasedInterruptTime? by lazy {
            if (!System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
                null
            } else {
                Native.load(
                    "kernelbase",
                    QueryUnbiasedInterruptTime::class.java,
                    mapOf(
                        Library.OPTION_FUNCTION_MAPPER to
                            FunctionMapper { _, method ->
                                if (method.name == "queryUnbiasedInterruptTimePrecise") {
                                    "QueryUnbiasedInterruptTimePrecise"
                                } else {
                                    method.name
                                }
                            },
                    ),
                )
            }
        }

        fun readNanosOrNull(): Long? {
            return readSuspendClockNanosOrNull {
                val clock = api ?: return@readSuspendClockNanosOrNull null
                val ticks = LongByReference()
                clock.queryUnbiasedInterruptTimePrecise(ticks)
                Math.multiplyExact(ticks.value, 100L)
            }
        }

        private interface QueryUnbiasedInterruptTime : StdCallLibrary {
            fun queryUnbiasedInterruptTimePrecise(unbiasedTime: LongByReference)
        }
    }
}

/** Native clock loading and invocation are optional evidence sources; failures leave timing incomplete. */
internal fun readSuspendClockNanosOrNull(read: () -> Long?): Long? =
    try {
        read()
    } catch (_: LinkageError) {
        null
    } catch (_: RuntimeException) {
        null
    }

@Req("TR-PERF-010")
object TimingAssessment {
    private const val BUDGET_NANOS = 200_000_000L

    fun assess(
        operation: OperationRecord,
        exclusions: List<ExclusionRecord>,
        eligibleForDecision: Boolean = true,
        responseVerified: Boolean = true,
    ): OperationAssessment {
        val eligible =
            eligibleForDecision && responseVerified && operation.terminalOutcome == "completed"
        if (requiresContinuityEvidence(operation.interval) &&
            operation.interval.continuity != ClockContinuity.VERIFIED
        ) {
            return incomplete(operation, exclusions, eligible, continuityFailure(operation.interval))
        }
        val invalidExclusion =
            exclusions.firstOrNull {
                requiresContinuityEvidence(it.interval) && it.interval.continuity != ClockContinuity.VERIFIED
            }
        if (invalidExclusion != null) {
            return incomplete(
                operation,
                exclusions,
                eligible,
                continuityFailure(invalidExclusion.interval),
            )
        }
        val raw =
            operation.interval.elapsedNanos
                ?: return incomplete(operation, exclusions, eligible, "invalid operation interval")
        val compatible = exclusions.map { it.interval }
        if (compatible.any { it.clockDomain != operation.interval.clockDomain }) {
            return incomplete(operation, exclusions, eligible, "incompatible clock domain")
        }
        val intervals = mutableListOf<Pair<Long, Long>>()
        for (excluded in compatible) {
            val start = maxOf(operation.interval.startNanos, excluded.startNanos)
            val end = minOf(operation.interval.endNanos, excluded.endNanos)
            if (excluded.elapsedNanos ==
                null
            ) {
                return incomplete(operation, exclusions, eligible, "invalid exclusion interval")
            }
            if (start < end) intervals += start to end
        }
        intervals.sortBy { it.first }
        var union = 0L
        var activeStart: Long? = null
        var activeEnd: Long? = null
        for ((start, end) in intervals) {
            val previousEnd = activeEnd
            if (activeStart == null || previousEnd == null) {
                activeStart = start
                activeEnd = end
            } else if (start <= previousEnd) {
                activeEnd = maxOf(previousEnd, end)
            } else {
                union += previousEnd - activeStart
                activeStart = start
                activeEnd = end
            }
        }
        if (activeStart != null && activeEnd != null) union += activeEnd - activeStart
        if (union >
            raw
        ) {
            return incomplete(
                operation,
                exclusions,
                eligible,
                "excluded overlap exceeds operation duration",
            )
        }
        val assessed = raw - union
        val reason =
            when {
                !responseVerified -> "ceremony response was not independently verified"
                operation.terminalOutcome != "completed" -> "operation did not complete: ${operation.terminalOutcome}"
                else -> null
            }
        return OperationAssessment(
            operation = operation,
            exclusions = exclusions.toList(),
            eligibleForDecision = eligible,
            rawDurationNanos = raw,
            excludedDurationNanos = union,
            assessedDurationNanos = assessed,
            decision =
                when {
                    reason != null -> OperationDecision.INCOMPLETE
                    assessed < BUDGET_NANOS -> OperationDecision.PASS
                    else -> OperationDecision.FAIL
                },
            reason = reason,
        )
    }

    private fun requiresContinuityEvidence(interval: MonotonicInterval): Boolean =
        interval.clockDomain == MonotonicMeasurement.CLOCK_DOMAIN

    private fun continuityFailure(interval: MonotonicInterval): String =
        when (interval.continuity) {
            ClockContinuity.SUSPEND_DETECTED -> "host suspend detected during interval"
            ClockContinuity.CLOCK_DISCONTINUITY -> "monotonic clock discontinuity detected"
            ClockContinuity.UNAVAILABLE -> "suspend detection is unavailable on this host"
            ClockContinuity.NOT_MONITORED -> "interval lacks suspend-continuity evidence"
            ClockContinuity.VERIFIED -> ""
        }

    private fun incomplete(
        operation: OperationRecord,
        exclusions: List<ExclusionRecord>,
        eligibleForDecision: Boolean,
        reason: String,
    ) = OperationAssessment(
        operation,
        exclusions.toList(),
        eligibleForDecision,
        null,
        null,
        null,
        OperationDecision.INCOMPLETE,
        reason,
    )
}
