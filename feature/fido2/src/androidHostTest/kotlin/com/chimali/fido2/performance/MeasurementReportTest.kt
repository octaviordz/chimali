package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Req("TR-PERF-010")
class MeasurementReportTest {
    private val workload =
        SimulatedWorkload(
            version = "1",
            warmupCount = 0,
            repetitionCount = 1,
            summary = "each occurrence",
            eligibleAfterWarmup = true,
            operationTypes = setOf("makeCredential"),
            exercisedOperationTypes = setOf("makeCredential"),
            host = "host-test",
            runtime = "JVM",
            sourceRevision = "synthetic-test-revision",
            realComponents = setOf("operation calculator"),
            simulatedComponents = setOf("RP", "transport", "verification"),
        )

    private fun attempt(
        kind: CeremonyKind,
        id: String,
    ) = CeremonyAttempt(
        runId = "run-1",
        attemptId = id,
        actionId = "action-$id",
        kind = kind,
        attemptKind = AttemptKind.MEASURED,
        outcome = CeremonyOutcome.VERIFIED,
        ceremonyInterval = MonotonicInterval(1L, 2L, "test"),
        verifiedResponse = true,
        verification = VerificationRecord(VerificationOutcome.SUCCESS, MonotonicInterval(1L, 2L, "test")),
        operations =
            listOf(
                OperationRecord(
                    "operation-$id",
                    "action-$id",
                    "makeCredential",
                    MonotonicInterval(1L, 2L, "test"),
                    "completed",
                ),
            ),
    )

    private fun assessment(decision: OperationDecision) =
        OperationAssessment(
            operation = attempt(CeremonyKind.REGISTRATION, "1").operations.single(),
            exclusions = emptyList(),
            eligibleForDecision = true,
            rawDurationNanos = 1L,
            excludedDurationNanos = 0L,
            assessedDurationNanos = 1L,
            decision = decision,
        )

    @Test
    fun `unmeasured operation prevents complete pass`() {
        val attempts = listOf(attempt(CeremonyKind.REGISTRATION, "1"), attempt(CeremonyKind.AUTHENTICATION, "2"))
        val report =
            MeasurementReport.create(
                workload.copy(exercisedOperationTypes = emptySet()),
                attempts,
                listOf(assessment(OperationDecision.PASS)),
            )
        assertEquals(ProjectDisposition.INCOMPLETE, report.disposition)
    }

    @Test
    fun `a measured failing occurrence fails report`() {
        val attempts = listOf(attempt(CeremonyKind.REGISTRATION, "1"), attempt(CeremonyKind.AUTHENTICATION, "2"))
        val report = MeasurementReport.create(workload, attempts, listOf(assessment(OperationDecision.FAIL)))
        assertEquals(ProjectDisposition.FAIL, report.disposition)
    }

    @Test
    fun `not invoked verification has no execution duration`() {
        val value = VerificationRecord(VerificationOutcome.NOT_INVOKED, null)
        assertEquals(null, value.interval)
    }

    @Test
    fun `attempt IDs are unique and registration and authentication both need verified evidence`() {
        val registration = attempt(CeremonyKind.REGISTRATION, "same")
        val report =
            MeasurementReport.create(
                workload,
                listOf(registration, registration),
                listOf(assessment(OperationDecision.PASS)),
            )
        assertEquals(ProjectDisposition.INCOMPLETE, report.disposition)
    }

    @Test
    fun `report rejects unassociated operation assessments and duplicate actions`() {
        val registration = attempt(CeremonyKind.REGISTRATION, "1")
        val authentication = attempt(CeremonyKind.AUTHENTICATION, "2")
        val mismatchedAssessment =
            assessment(OperationDecision.PASS).copy(
                operation = assessment(OperationDecision.PASS).operation.copy(actionId = "unrelated-action"),
            )

        val unmatched =
            MeasurementReport.create(
                workload,
                listOf(registration, authentication),
                listOf(mismatchedAssessment),
            )
        val duplicateAction =
            MeasurementReport.create(
                workload,
                listOf(
                    registration,
                    authentication.copy(
                        actionId = registration.actionId,
                        operations = authentication.operations.map { it.copy(actionId = registration.actionId) },
                    ),
                ),
                listOf(assessment(OperationDecision.PASS)),
            )

        assertEquals(ProjectDisposition.INCOMPLETE, unmatched.disposition)
        assertFalse(unmatched.reasons.none { it.contains("unmatched") || it.contains("attribution") })
        assertEquals(ProjectDisposition.INCOMPLETE, duplicateAction.disposition)
        assertFalse(duplicateAction.reasons.none { it.contains("action identities") })
    }

    @Test
    fun `per type summary is descriptive and keeps every occurrence`() {
        val attempts = listOf(attempt(CeremonyKind.REGISTRATION, "1"), attempt(CeremonyKind.AUTHENTICATION, "2"))
        val values =
            listOf(1L, 3L, 5L).mapIndexed { index, duration ->
                val operation = attempt(CeremonyKind.REGISTRATION, "summary-$index").operations.single()
                OperationAssessment(operation, emptyList(), true, duration, 0L, duration, OperationDecision.PASS)
            }

        val report = MeasurementReport.create(workload, attempts, values)
        val summary = report.operationSummaries.getValue("makeCredential")

        assertEquals(3, summary.occurrenceCount)
        assertEquals(1L, summary.minimumNanos)
        assertEquals(3L, summary.medianNanos)
        assertEquals(5L, summary.maximumNanos)
    }

    @Test
    fun `evidence records have no fields for secrets or real identity data`() {
        val evidenceTypes =
            listOf(
                SimulatedWorkload::class.java,
                CeremonyAttempt::class.java,
                OperationRecord::class.java,
                VerificationRecord::class.java,
                AssessmentReport::class.java,
                OperationAssessment::class.java,
            )
        val forbiddenNames = listOf("privateKey", "credentialSecret", "biometricData", "accountIdentifier")
        val fieldNames = evidenceTypes.flatMap { type -> type.declaredFields.map { it.name } }

        assertTrue(
            fieldNames.none { name -> forbiddenNames.any { forbidden -> name.contains(forbidden, ignoreCase = true) } },
        )
    }

    @Test
    fun `warmup failure is retained but excluded from acceptance decision`() {
        val registration = attempt(CeremonyKind.REGISTRATION, "measured-registration")
        val authentication = attempt(CeremonyKind.AUTHENTICATION, "measured-authentication")
        val warmupBase = attempt(CeremonyKind.REGISTRATION, "warmup-registration")
        val warmupOperation =
            warmupBase.operations.single().copy(
                interval = MonotonicInterval(0L, 200_000_000L, "test"),
            )
        val warmup =
            warmupBase.copy(
                attemptKind = AttemptKind.WARMUP,
                operations = listOf(warmupOperation),
                operationAssessments =
                    listOf(
                        TimingAssessment.assess(warmupOperation, emptyList(), eligibleForDecision = false),
                    ),
            )
        val measuredAttempts =
            listOf(registration, authentication).map { attempt ->
                val operation = attempt.operations.single()
                attempt.copy(operationAssessments = listOf(TimingAssessment.assess(operation, emptyList())))
            }

        val report = MeasurementReport.create(workload, measuredAttempts + warmup)

        assertEquals(OperationDecision.FAIL, warmup.operationAssessments.single().decision)
        assertEquals(false, warmup.operationAssessments.single().eligibleForDecision)
        assertEquals(ProjectDisposition.PASS, report.disposition)
        assertEquals(2, report.operationSummaries.getValue("makeCredential").occurrenceCount)
        assertEquals(3, report.attempts.size)
    }

    @Test
    fun `rejected attempt duration is retained but excluded from passing summaries`() {
        val registrationBase = attempt(CeremonyKind.REGISTRATION, "verified-registration")
        val registrationOperation = registrationBase.operations.single()
        val registration =
            registrationBase.copy(
                operationAssessments = listOf(TimingAssessment.assess(registrationOperation, emptyList())),
            )
        val authenticationBase = attempt(CeremonyKind.AUTHENTICATION, "verified-authentication")
        val authenticationOperation = authenticationBase.operations.single()
        val authentication =
            authenticationBase.copy(
                operationAssessments = listOf(TimingAssessment.assess(authenticationOperation, emptyList())),
            )
        val rejectedBase = attempt(CeremonyKind.REGISTRATION, "rejected-registration")
        val rejectedOperation = rejectedBase.operations.single()
        val rejectedAssessment =
            TimingAssessment.assess(
                rejectedOperation,
                emptyList(),
                responseVerified = false,
            )
        val rejected =
            rejectedBase.copy(
                outcome = CeremonyOutcome.REJECTED,
                verifiedResponse = false,
                operations = listOf(rejectedOperation),
                operationAssessments = listOf(rejectedAssessment),
            )

        val report = MeasurementReport.create(workload, listOf(registration, authentication, rejected))

        assertEquals(OperationDecision.INCOMPLETE, rejectedAssessment.decision)
        assertFalse(rejectedAssessment.eligibleForDecision)
        assertTrue(rejectedAssessment.rawDurationNanos != null)
        assertEquals(ProjectDisposition.PASS, report.disposition)
        assertEquals(2, report.operationSummaries.getValue("makeCredential").occurrenceCount)
        assertEquals(3, report.attempts.size)
    }
}
