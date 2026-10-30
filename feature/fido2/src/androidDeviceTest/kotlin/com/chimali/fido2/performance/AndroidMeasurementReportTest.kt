package com.chimali.fido2.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.core.common.annotation.Req
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device checks that performance outcomes remain separate from collection validity and host acceptance. */
@RunWith(AndroidJUnit4::class)
@Suppress("MagicNumber", "StringLiteralDuplication")
class AndroidMeasurementReportTest {
    @Test
    @Req("TR-PERF-010")
    fun deviceReportPassesOnlyWhenEveryOccurrenceIsBelowBudget() {
        assertEquals(ProjectDisposition.PASS, report().disposition)
    }

    @Test
    @Req("TR-PERF-010")
    fun thresholdEqualityIsDeviceFailAndRetainsMeasurements() {
        val report = report(overBudgetOperation = "MakeCredential")

        assertEquals(ProjectDisposition.FAIL, report.disposition)
        assertEquals(10, report.operationSummaries.getValue("MakeCredential").occurrenceCount)
        assertEquals(
            OperationDecision.FAIL,
            report.operationAssessments
                .first {
                    it.operation.type == "MakeCredential"
                }.decision,
        )
    }

    @Test
    @Req("TR-PERF-010")
    fun missingContinuityEvidenceRemainsIncomplete() {
        assertEquals(ProjectDisposition.INCOMPLETE, report(unavailableClock = true).disposition)
    }

    @Test
    @Req("TR-PERF-010")
    fun performanceFailureAndIncompleteEvidenceRemainVisibleTogether() {
        val report = report(overBudgetOperation = "MakeCredential", unavailableClock = true)

        assertEquals(ProjectDisposition.FAIL, report.disposition)
        assertTrue(report.operationAssessments.any { it.decision == OperationDecision.FAIL })
        assertTrue(report.operationAssessments.any { it.decision == OperationDecision.INCOMPLETE })
    }

    @Test
    @Req("TR-PERF-010")
    fun orphanedOperationAssessmentMakesReportIncomplete() {
        val interval = MonotonicInterval(0L, 1L, MonotonicMeasurement.CLOCK_DOMAIN, ClockContinuity.VERIFIED)
        val orphan = OperationRecord("orphan-operation", "unmatched-action", "MakeCredential", interval, "completed")
        val result =
            MeasurementReport.create(
                workload(),
                emptyList(),
                listOf(TimingAssessment.assess(orphan, emptyList())),
            )

        assertEquals(ProjectDisposition.INCOMPLETE, result.disposition)
        assertTrue(result.reasons.any { it.contains("unmatched") })
    }

    private fun report(
        overBudgetOperation: String? = null,
        unavailableClock: Boolean = false,
    ): AssessmentReport {
        val runId = UUID.randomUUID().toString()
        val attempts =
            buildList {
                repeat(REPETITIONS) { index ->
                    add(
                        attempt(
                            runId,
                            CeremonyKind.REGISTRATION,
                            index,
                            listOf("GetInfo", "MakeCredential"),
                            overBudgetOperation,
                            unavailableClock,
                        ),
                    )
                    add(
                        attempt(
                            runId,
                            CeremonyKind.AUTHENTICATION,
                            index,
                            listOf("GetAssertion", "ResetAuthenticator"),
                            overBudgetOperation,
                            unavailableClock,
                        ),
                    )
                }
            }
        return MeasurementReport.create(workload(), attempts)
    }

    private fun workload() =
        SimulatedWorkload(
            version = "060-device-report-test",
            warmupCount = 0,
            repetitionCount = REPETITIONS,
            summary = "synthetic report decision test",
            eligibleAfterWarmup = true,
            operationTypes = OPERATION_TYPES,
            exercisedOperationTypes = OPERATION_TYPES,
            host = "test device",
            runtime = "Android device test",
            sourceRevision = "test fingerprint",
            realComponents = setOf("operation timing"),
            simulatedComponents = setOf("fake RP", "verification double"),
            clockDomain = MonotonicMeasurement.CLOCK_DOMAIN,
        )

    private fun attempt(
        runId: String,
        kind: CeremonyKind,
        index: Int,
        operationTypes: List<String>,
        overBudgetOperation: String?,
        unavailableClock: Boolean,
    ): CeremonyAttempt {
        val attemptId = "${kind.name.lowercase()}-$index-${UUID.randomUUID()}"
        val actionId = UUID.randomUUID().toString()
        var time = index * 1_000_000_000L
        val operations =
            operationTypes.mapIndexed { operationIndex, type ->
                val duration = if (type == overBudgetOperation && index == 0) OPERATION_BUDGET_NANOS else 1_000L
                val interval =
                    MonotonicInterval(
                        startNanos = time,
                        endNanos = time + duration,
                        clockDomain = MonotonicMeasurement.CLOCK_DOMAIN,
                        continuity =
                            if (unavailableClock && index == 0 && operationIndex == 0) {
                                ClockContinuity.UNAVAILABLE
                            } else {
                                ClockContinuity.VERIFIED
                            },
                        suspendAwareElapsedNanos =
                            if (unavailableClock &&
                                index == 0 &&
                                operationIndex == 0
                            ) {
                                null
                            } else {
                                duration
                            },
                        clockDivergenceNanos = if (unavailableClock && index == 0 && operationIndex == 0) null else 0L,
                    )
                time += duration + 1L
                OperationRecord("$attemptId-op-$operationIndex", actionId, type, interval, "completed")
            }
        val assessments = operations.map { TimingAssessment.assess(it, emptyList()) }
        val verificationInterval =
            MonotonicInterval(1L, 2L, MonotonicMeasurement.CLOCK_DOMAIN, ClockContinuity.VERIFIED, 1L, 0L)
        return CeremonyAttempt(
            runId = runId,
            attemptId = attemptId,
            actionId = actionId,
            kind = kind,
            attemptKind = AttemptKind.MEASURED,
            outcome = CeremonyOutcome.VERIFIED,
            ceremonyInterval = verificationInterval,
            verifiedResponse = true,
            verification = VerificationRecord(VerificationOutcome.SUCCESS, verificationInterval),
            operations = operations,
            operationAssessments = assessments,
        )
    }

    private companion object {
        const val REPETITIONS = 10
        const val OPERATION_BUDGET_NANOS = 200_000_000L
        val OPERATION_TYPES = setOf("MakeCredential", "GetAssertion", "GetInfo", "ResetAuthenticator")
    }
}
