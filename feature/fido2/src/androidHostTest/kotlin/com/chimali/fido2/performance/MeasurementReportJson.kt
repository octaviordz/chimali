package com.chimali.fido2.performance

import java.time.Instant

/** Privacy-bounded JSON evidence writer for host assessment output. */
internal object MeasurementReportJson {
    fun encode(report: AssessmentReport): String =
        buildString {
            append("{\n  \"generated_at_utc\": ").append(quoted(Instant.now().toString()))
            append(",\n  \"disposition\": ").append(quoted(report.disposition.name))
            append(",\n  \"reasons\": ").append(array(report.reasons.map(::quoted)))
            append(",\n  \"workload\": {")
            append("\n    \"version\": ").append(quoted(report.workload.version))
            append(",\n    \"summary\": ").append(quoted(report.workload.summary))
            append(",\n    \"warmup_count_per_ceremony\": ").append(report.workload.warmupCount)
            append(",\n    \"measured_repetitions_per_ceremony\": ").append(report.workload.repetitionCount)
            append(",\n    \"eligible_after_warmup\": ").append(report.workload.eligibleAfterWarmup)
            append(",\n    \"operation_types\": ").append(
                array(
                    report.workload.operationTypes
                        .sorted()
                        .map(::quoted),
                ),
            )
            append(
                ",\n    \"exercised_operation_types\": ",
            ).append(
                array(
                    report.workload.exercisedOperationTypes
                        .sorted()
                        .map(::quoted),
                ),
            )
            append(
                ",\n    \"unmeasured_operation_types\": ",
            ).append(
                array(
                    report.workload.unmeasuredOperationTypes
                        .sorted()
                        .map(::quoted),
                ),
            )
            append(",\n    \"host\": ").append(quoted(report.workload.host))
            append(",\n    \"runtime\": ").append(quoted(report.workload.runtime))
            append(",\n    \"source_revision\": ").append(quoted(report.workload.sourceRevision))
            append(",\n    \"clock_domain\": ").append(quoted(report.workload.clockDomain))
            append(",\n    \"outlier_policy\": ").append(quoted(report.workload.outlierPolicy))
            append(",\n    \"limitations\": ").append(array(report.workload.limitations.map(::quoted)))
            append(",\n    \"real_components\": ").append(
                array(
                    report.workload.realComponents
                        .sorted()
                        .map(::quoted),
                ),
            )
            append(
                ",\n    \"simulated_components\": ",
            ).append(
                array(
                    report.workload.simulatedComponents
                        .sorted()
                        .map(::quoted),
                ),
            )
            append("\n  },\n  \"attempts\": [")
            report.attempts.forEachIndexed { index, attempt ->
                append(if (index == 0) "\n" else ",\n")
                append("    {\"run_id\": ").append(quoted(attempt.runId))
                append(", \"attempt_id\": ").append(quoted(attempt.attemptId))
                append(", \"action_id\": ").append(quoted(attempt.actionId))
                append(", \"ceremony\": ").append(quoted(attempt.kind.name))
                append(", \"attempt_kind\": ").append(quoted(attempt.attemptKind.name))
                append(", \"outcome\": ").append(quoted(attempt.outcome.name))
                append(", \"verified_response\": ").append(attempt.verifiedResponse)
                append(", \"ceremony_interval\": ").append(interval(attempt.ceremonyInterval))
                append(", \"verification\": {\"outcome\": ").append(quoted(attempt.verification.outcome.name))
                append(", \"interval\": ").append(interval(attempt.verification.interval)).append("}")
                append(", \"bluetooth_handoff\": ").append(interval(attempt.bluetoothHandoff))
                append(", \"failure_reason\": ").append(attempt.failureReason?.let(::quoted) ?: "null")
                append(", \"operations\": [")
                attempt.operations.forEachIndexed { operationIndex, operation ->
                    val assessment =
                        attempt.operationAssessments.firstOrNull {
                            it.operation.operationId ==
                                operation.operationId
                        }
                    append(if (operationIndex == 0) "" else ",")
                    append("{\"operation_id\": ").append(quoted(operation.operationId))
                    append(", \"action_id\": ").append(quoted(operation.actionId))
                    append(", \"type\": ").append(quoted(operation.type))
                    append(", \"terminal_outcome\": ").append(quoted(operation.terminalOutcome))
                    append(", \"interval\": ").append(interval(operation.interval))
                    append(", \"assessment\": ")
                    if (assessment == null) {
                        append("null")
                    } else {
                        append("{\"eligible\": ").append(assessment.eligibleForDecision)
                        append(", \"raw_duration_ns\": ").append(number(assessment.rawDurationNanos))
                        append(", \"excluded_duration_ns\": ").append(number(assessment.excludedDurationNanos))
                        append(", \"assessed_duration_ns\": ").append(number(assessment.assessedDurationNanos))
                        append(", \"decision\": ").append(quoted(assessment.decision.name))
                        append(", \"reason\": ").append(assessment.reason?.let(::quoted) ?: "null")
                        append(", \"exclusions\": [")
                        assessment.exclusions.forEachIndexed { exclusionIndex, exclusion ->
                            append(if (exclusionIndex == 0) "" else ",")
                            append("{\"kind\": ").append(quoted(exclusion.kind.name))
                            append(", \"interval\": ").append(interval(exclusion.interval)).append("}")
                        }
                        append("]}")
                    }
                    append("}")
                }
                append("]}")
            }
            append("\n  ],\n  \"operation_summaries\": ")
            append(summaryMap(report.operationSummaries.mapKeys { it.key }))
            append(",\n  \"ceremony_summaries\": ")
            append(summaryMap(report.ceremonySummaries.mapKeys { it.key.name }))
            append("\n}\n")
        }

    private fun summaryMap(values: Map<String, OperationSummary>): String =
        buildString {
            append("{")
            values.toSortedMap().entries.forEachIndexed { index, (name, summary) ->
                append(if (index == 0) "" else ",")
                append(quoted(name)).append(":{\"occurrence_count\":").append(summary.occurrenceCount)
                append(",\"minimum_ns\":").append(summary.minimumNanos)
                append(",\"median_ns\":").append(summary.medianNanos)
                append(",\"maximum_ns\":").append(summary.maximumNanos).append("}")
            }
            append("}")
        }

    private fun interval(value: MonotonicInterval?): String = value?.let(::encodeInterval) ?: "null"

    private fun encodeInterval(interval: MonotonicInterval): String =
        buildString {
            append("{\"start_ns\":").append(interval.startNanos)
            append(",\"end_ns\":").append(interval.endNanos)
            append(",\"elapsed_ns\":").append(interval.elapsedNanos ?: "null")
            append(",\"clock_domain\":").append(quoted(interval.clockDomain))
            append(",\"clock_continuity\":").append(quoted(interval.continuity.name))
            append(",\"suspend_aware_elapsed_ns\":").append(interval.suspendAwareElapsedNanos ?: "null")
            append(",\"clock_divergence_ns\":").append(interval.clockDivergenceNanos ?: "null").append("}")
        }

    private fun number(value: Long?): String = value?.toString() ?: "null"

    private fun array(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]")

    private fun quoted(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
            append('"')
        }
}
