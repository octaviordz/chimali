package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req

/** Immediate host double: it returns outcomes without sleeping or invoking a platform prompt. */
@Req("TR-PERF-010")
class SimulatedUserVerification {
    private val recordedInvocations = mutableListOf<VerificationRecord>()

    val invocationCount: Int
        get() = recordedInvocations.size

    val invocations: List<VerificationRecord>
        get() = recordedInvocations.toList()

    fun provide(outcome: VerificationOutcome): VerificationRecord {
        require(outcome != VerificationOutcome.NOT_INVOKED) {
            "Use notInvoked() when the verification boundary was not entered"
        }
        val start = MonotonicMeasurement.begin()
        val end = MonotonicMeasurement.end()
        return VerificationRecord(outcome, MonotonicMeasurement.interval(start, end)).also(recordedInvocations::add)
    }

    fun notInvoked(): VerificationRecord = VerificationRecord(VerificationOutcome.NOT_INVOKED, null)
}
