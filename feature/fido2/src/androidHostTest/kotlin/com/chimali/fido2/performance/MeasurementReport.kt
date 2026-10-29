package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req

enum class ProjectDisposition { PASS, FAIL, INCOMPLETE }

data class AssessmentReport(
    val workload: SimulatedWorkload,
    val attempts: List<CeremonyAttempt>,
    val operationAssessments: List<OperationAssessment>,
    val operationSummaries: Map<String, OperationSummary>,
    val ceremonySummaries: Map<CeremonyKind, OperationSummary>,
    val disposition: ProjectDisposition,
    val reasons: List<String>,
)

/** Descriptive statistics over assessed durations; these never replace per-occurrence decisions. */
data class OperationSummary(
    val occurrenceCount: Int,
    val minimumNanos: Long,
    val medianNanos: Long,
    val maximumNanos: Long,
)

@Req("TR-PERF-010")
object MeasurementReport {
    fun create(
        workload: SimulatedWorkload,
        attempts: List<CeremonyAttempt>,
        assessments: List<OperationAssessment> = attempts.flatMap { it.operationAssessments },
    ): AssessmentReport {
        val eligibleAssessments = assessments.filter { it.eligibleForDecision }
        val reasons = createReasons(workload, attempts, assessments, eligibleAssessments)
        val disposition =
            when {
                assessments.any {
                    it.eligibleForDecision && it.decision == OperationDecision.FAIL
                } -> ProjectDisposition.FAIL
                reasons.isNotEmpty() -> ProjectDisposition.INCOMPLETE
                else -> ProjectDisposition.PASS
            }
        val summaries =
            eligibleAssessments
                .mapNotNull { it.assessedDurationNanos?.let { duration -> it.operation.type to duration } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> summarize(values) }
        val ceremonySummaries =
            attempts
                .filter { it.attemptKind == AttemptKind.MEASURED && it.verifiedResponse }
                .mapNotNull { attempt -> attempt.ceremonyInterval?.elapsedNanos?.let { attempt.kind to it } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> summarize(values) }
        return AssessmentReport(
            workload,
            attempts.toList(),
            assessments.toList(),
            summaries,
            ceremonySummaries,
            disposition,
            reasons,
        )
    }

    private fun summarize(values: List<Long>): OperationSummary {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        val median =
            if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                val lower = sorted[middle - 1]
                val upper = sorted[middle]
                lower + (upper - lower) / 2
            }
        return OperationSummary(sorted.size, sorted.first(), median, sorted.last())
    }

    private fun createReasons(
        workload: SimulatedWorkload,
        attempts: List<CeremonyAttempt>,
        assessments: List<OperationAssessment>,
        eligibleAssessments: List<OperationAssessment>,
    ): List<String> =
        buildList {
            addAll(workloadReasons(workload, attempts, eligibleAssessments))
            addAll(identityReasons(attempts, assessments))
        }

    private fun workloadReasons(
        workload: SimulatedWorkload,
        attempts: List<CeremonyAttempt>,
        eligibleAssessments: List<OperationAssessment>,
    ): List<String> =
        buildList {
            if (attempts.isEmpty()) add("no attempts recorded")
            listOf(CeremonyKind.REGISTRATION, CeremonyKind.AUTHENTICATION).forEach { kind ->
                val verifiedCount =
                    attempts.count {
                        it.kind == kind && it.attemptKind == AttemptKind.MEASURED && it.verifiedResponse
                    }
                if (verifiedCount < workload.repetitionCount) {
                    add("insufficient verified measured $kind samples: $verifiedCount/${workload.repetitionCount}")
                }
            }
            val verifiedKinds =
                attempts
                    .mapNotNull { attempt ->
                        attempt.kind.takeIf {
                            attempt.kind != CeremonyKind.OPERATION_PROBE &&
                                attempt.attemptKind == AttemptKind.MEASURED &&
                                attempt.verifiedResponse
                        }
                    }.toSet()
            val missingKinds = setOf(CeremonyKind.REGISTRATION, CeremonyKind.AUTHENTICATION) - verifiedKinds
            if (missingKinds.isNotEmpty()) {
                add("no verified ceremony for: ${missingKinds.sortedBy { it.name }.joinToString()}")
            }
            if (workload.unmeasuredOperationTypes.isNotEmpty()) {
                add("unmeasured operation types: ${workload.unmeasuredOperationTypes.sorted().joinToString()}")
            }
            val seen = eligibleAssessments.map { it.operation.type }.toSet()
            val missing = workload.operationTypes - seen
            if (missing.isNotEmpty()) add("operation types have no assessment: ${missing.sorted().joinToString()}")
            if (eligibleAssessments.any { it.decision == OperationDecision.INCOMPLETE }) {
                add("one or more eligible operation assessments are incomplete")
            }
            if (attempts.any { it.attemptKind == AttemptKind.MEASURED && it.outcome == CeremonyOutcome.INCOMPLETE }) {
                add("one or more measured ceremony attempts are incomplete")
            }
            if (attempts.any {
                    it.attemptKind == AttemptKind.MEASURED &&
                        it.verifiedResponse &&
                        it.ceremonyInterval?.elapsedNanos == null
                }
            ) {
                add("one or more verified measured ceremony intervals are invalid")
            }
        }

    private fun identityReasons(
        attempts: List<CeremonyAttempt>,
        assessments: List<OperationAssessment>,
    ): List<String> =
        buildList {
            if (attempts.map { it.attemptId }.toSet().size != attempts.size) add("attempt identities are duplicated")
            if (attempts.map { it.actionId }.toSet().size != attempts.size) {
                add("action identities are duplicated or ambiguous")
            }
            if (attempts.map { it.runId }.toSet().size > 1) add("attempts belong to multiple runs")
            if (assessments.map { it.operation.operationId }.toSet().size != assessments.size) {
                add("operation identities are duplicated")
            }
            val operationsById = attempts.flatMap { it.operations }.associateBy { it.operationId }
            val assessedById = assessments.associateBy { it.operation.operationId }
            if (operationsById.size != attempts.sumOf { it.operations.size }) {
                add("operation identities are duplicated across attempts")
            }
            if (operationsById.keys != assessedById.keys) add("operation records and assessments are unmatched")
            if (assessments.any { assessment -> assessmentHasAmbiguousAttribution(assessment, operationsById) }) {
                add("operation assessment attribution is ambiguous")
            }
        }

    private fun assessmentHasAmbiguousAttribution(
        assessment: OperationAssessment,
        operationsById: Map<String, OperationRecord>,
    ): Boolean {
        val source = operationsById[assessment.operation.operationId] ?: return false
        return source.actionId != assessment.operation.actionId || source.type != assessment.operation.type
    }
}
