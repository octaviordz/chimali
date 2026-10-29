package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req

/** Host-only, synthetic workload metadata and evidence records for TR-PERF-010. */
@Req("TR-PERF-010")
data class SimulatedWorkload(
    val version: String,
    val warmupCount: Int,
    val repetitionCount: Int,
    val summary: String,
    val eligibleAfterWarmup: Boolean,
    val operationTypes: Set<String>,
    val exercisedOperationTypes: Set<String>,
    val host: String,
    val runtime: String,
    val sourceRevision: String,
    val realComponents: Set<String>,
    val simulatedComponents: Set<String>,
    val clockDomain: String = MonotonicMeasurement.CLOCK_DOMAIN,
    val outlierPolicy: String = "retain and report; no removal",
    val limitations: List<String> = emptyList(),
) {
    init {
        require(version.isNotBlank())
        require(warmupCount >= 0)
        require(repetitionCount > 0)
        require(summary.isNotBlank())
        require(operationTypes.none(String::isBlank))
        require(exercisedOperationTypes.all(operationTypes::contains))
        require(host.isNotBlank())
        require(runtime.isNotBlank())
        require(sourceRevision.isNotBlank())
        require(clockDomain.isNotBlank())
        require(outlierPolicy.isNotBlank())
        require(limitations.none(String::isBlank))
    }

    val unmeasuredOperationTypes: Set<String>
        get() = operationTypes - exercisedOperationTypes
}

enum class CeremonyKind { REGISTRATION, AUTHENTICATION }

enum class CeremonyOutcome { VERIFIED, REJECTED, CANCELLED, FAILED, TIMED_OUT, INCOMPLETE }

enum class VerificationOutcome { SUCCESS, DENIAL, CANCELLED, TIMEOUT, ERROR, NOT_INVOKED }

enum class AttemptKind { WARMUP, MEASURED }

/** An endpoint pair from one System.nanoTime clock domain; values are never wall-clock timestamps. */
@Req("TR-PERF-010")
data class MonotonicInterval(
    val startNanos: Long,
    val endNanos: Long,
    val clockDomain: String,
    val continuity: ClockContinuity = ClockContinuity.NOT_MONITORED,
    val suspendAwareElapsedNanos: Long? = null,
    val clockDivergenceNanos: Long? = null,
) {
    init {
        require(clockDomain.isNotBlank())
    }

    val elapsedNanos: Long?
        get() = (endNanos - startNanos).takeIf { it >= 0L }
}

/** Independent evidence that a real monotonic interval did not span system suspend or clock divergence. */
enum class ClockContinuity { NOT_MONITORED, VERIFIED, SUSPEND_DETECTED, CLOCK_DISCONTINUITY, UNAVAILABLE }

@Req("TR-PERF-010")
data class OperationRecord(
    val operationId: String,
    val actionId: String,
    val type: String,
    val interval: MonotonicInterval,
    val terminalOutcome: String,
) {
    init {
        require(operationId.isNotBlank())
        require(actionId.isNotBlank())
        require(type.isNotBlank())
        require(terminalOutcome.isNotBlank())
    }
}

@Req("TR-PERF-010")
data class VerificationRecord(
    val outcome: VerificationOutcome,
    val interval: MonotonicInterval?,
) {
    init {
        require((outcome == VerificationOutcome.NOT_INVOKED) == (interval == null))
    }
}

@Req("TR-PERF-010")
data class CeremonyAttempt(
    val runId: String,
    val attemptId: String,
    val actionId: String,
    val kind: CeremonyKind,
    val attemptKind: AttemptKind,
    val outcome: CeremonyOutcome,
    val ceremonyInterval: MonotonicInterval?,
    val verifiedResponse: Boolean,
    val verification: VerificationRecord,
    val operations: List<OperationRecord>,
    val bluetoothHandoff: MonotonicInterval? = null,
    val operationAssessments: List<OperationAssessment> = emptyList(),
    val failureReason: String? = null,
) {
    init {
        require(runId.isNotBlank())
        require(attemptId.isNotBlank())
        require(actionId.isNotBlank())
        require(operations.all { it.actionId == actionId })
        require(
            operationAssessments.all { assessment ->
                operations.any {
                    it.operationId == assessment.operation.operationId &&
                        it.actionId == assessment.operation.actionId
                }
            },
        )
        require((outcome == CeremonyOutcome.VERIFIED) == verifiedResponse)
        require(failureReason == null || failureReason.isNotBlank())
    }
}
