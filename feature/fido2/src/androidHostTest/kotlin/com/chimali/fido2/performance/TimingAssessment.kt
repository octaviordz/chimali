package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req

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

    fun nowNanos(): Long = System.nanoTime()

    fun interval(
        startNanos: Long,
        endNanos: Long,
    ): MonotonicInterval = MonotonicInterval(startNanos, endNanos, CLOCK_DOMAIN)
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
