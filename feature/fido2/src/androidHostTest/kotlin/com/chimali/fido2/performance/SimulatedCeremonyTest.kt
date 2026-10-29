package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@Req("TR-PERF-010")
class SimulatedCeremonyTest {
    @Test
    fun `records request through verified response and associates internal operations`() =
        runBlocking {
            val verification = SimulatedUserVerification()
            val ceremony = SimulatedCeremony(verification)
            val request = request()
            val attempt =
                ceremony.run(
                    runId = "synthetic-run",
                    attemptId = "registration-1",
                    actionId = "action-1",
                    kind = CeremonyKind.REGISTRATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request },
                    execute = {
                        userVerification(VerificationOutcome.SUCCESS)
                        operation("makeCredential") { "synthetic-response" }
                    },
                    verify = {
                        actualRequest,
                        response,
                        ->
                        actualRequest === request && response == "synthetic-response"
                    },
                )

            assertEquals(CeremonyOutcome.VERIFIED, attempt.outcome)
            assertTrue(attempt.verifiedResponse)
            assertTrue((attempt.ceremonyInterval?.elapsedNanos ?: -1L) >= 0L)
            assertEquals("action-1", attempt.operations.single().actionId)
            assertTrue(
                (
                    attempt.operations
                        .single()
                        .interval.elapsedNanos ?: -1L
                ) >= 0L,
            )
            assertNotNull(attempt.verification.interval)
            val operationAssessment = attempt.operationAssessments.single()
            assertTrue(
                operationAssessment.decision == OperationDecision.PASS ||
                    operationAssessment.decision == OperationDecision.INCOMPLETE,
            )
            if (operationAssessment.decision == OperationDecision.INCOMPLETE) {
                assertTrue(
                    operationAssessment.reason?.contains("suspend") == true ||
                        operationAssessment.reason?.contains("discontinuity") == true,
                )
            }
            assertEquals(
                ExclusionRecord.Kind.USER_VERIFICATION,
                attempt.operationAssessments
                    .single()
                    .exclusions
                    .single()
                    .kind,
            )
            assertEquals(1, verification.invocationCount)
        }

    @Test
    fun `failed operation stays in the attempt evidence and verification is not invoked`() =
        runBlocking {
            val attempt =
                SimulatedCeremony(SimulatedUserVerification()).run(
                    runId = "synthetic-run",
                    attemptId = "authentication-1",
                    actionId = "action-2",
                    kind = CeremonyKind.AUTHENTICATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request() },
                    execute = {
                        operation<String>("getAssertion") { error("synthetic failure") }
                    },
                    verify = { _, _ -> false },
                )

            assertEquals(CeremonyOutcome.FAILED, attempt.outcome)
            assertEquals("failed:IllegalStateException", attempt.operations.single().terminalOutcome)
            assertEquals(VerificationOutcome.NOT_INVOKED, attempt.verification.outcome)
            assertEquals(null, attempt.verification.interval)
        }

    @Test
    fun `independently rejected response is not a verified sample`() =
        runBlocking {
            val attempt =
                SimulatedCeremony(SimulatedUserVerification()).run(
                    runId = "synthetic-run",
                    attemptId = "registration-invalid",
                    actionId = "action-3",
                    kind = CeremonyKind.REGISTRATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request() },
                    execute = { operation("makeCredential") { "tampered-response" } },
                    verify = { _, _ -> false },
                )

            assertEquals(CeremonyOutcome.REJECTED, attempt.outcome)
            assertEquals(false, attempt.verifiedResponse)
            assertEquals("completed", attempt.operations.single().terminalOutcome)
            assertEquals(OperationDecision.INCOMPLETE, attempt.operationAssessments.single().decision)
            assertEquals(false, attempt.operationAssessments.single().eligibleForDecision)
            if (attempt.operationAssessments.single().decision == OperationDecision.INCOMPLETE &&
                (
                    attempt.operationAssessments
                        .single()
                        .reason
                        ?.contains("suspend") == true ||
                        attempt.operationAssessments
                            .single()
                            .reason
                            ?.contains("discontinuity") == true
                )
            ) {
                assertEquals(null, attempt.operationAssessments.single().rawDurationNanos)
            } else {
                assertTrue(attempt.operationAssessments.single().rawDurationNanos != null)
            }
        }

    @Test
    fun `cancellation and timeout are returned as retained incomplete attempts`() =
        runBlocking {
            val ceremony = SimulatedCeremony(SimulatedUserVerification())
            val cancelled =
                ceremony.run(
                    runId = "synthetic-run",
                    attemptId = "cancelled",
                    actionId = "action-cancelled",
                    kind = CeremonyKind.AUTHENTICATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request() },
                    execute = {
                        operation<String>(
                            "getAssertion",
                        ) { throw CancellationException("synthetic cancellation") }
                    },
                    verify = { _, _ -> false },
                )
            val timedOut =
                ceremony.run(
                    runId = "synthetic-run",
                    attemptId = "timed-out",
                    actionId = "action-timeout",
                    kind = CeremonyKind.AUTHENTICATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request() },
                    execute = {
                        operation("getAssertion") {
                            withTimeout(1) { kotlinx.coroutines.awaitCancellation() }
                        }
                    },
                    verify = { _, _ -> false },
                )

            assertEquals(CeremonyOutcome.CANCELLED, cancelled.outcome)
            assertEquals("cancelled", cancelled.operations.single().terminalOutcome)
            assertEquals(OperationDecision.INCOMPLETE, cancelled.operationAssessments.single().decision)
            assertEquals(VerificationOutcome.NOT_INVOKED, cancelled.verification.outcome)
            assertEquals(CeremonyOutcome.TIMED_OUT, timedOut.outcome)
            assertEquals("timed_out", timedOut.operations.single().terminalOutcome)
            assertEquals(OperationDecision.INCOMPLETE, timedOut.operationAssessments.single().decision)
            assertEquals(VerificationOutcome.NOT_INVOKED, timedOut.verification.outcome)
        }

    @Test
    fun `concurrent operation and discovery probe spans retain unique action association`() =
        runBlocking {
            val attempt =
                SimulatedCeremony(SimulatedUserVerification()).run(
                    runId = "synthetic-run",
                    attemptId = "concurrent",
                    actionId = "one-rp-action",
                    kind = CeremonyKind.AUTHENTICATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request() },
                    execute = {
                        val responses =
                            listOf(
                                async { operation("GetAssertion") { "assertion" } },
                                async { operation("GetInfo-probe") { "probe" } },
                            ).awaitAll()
                        userVerification(VerificationOutcome.SUCCESS)
                        responses.joinToString()
                    },
                    verify = { _, response -> response == "assertion, probe" },
                )

            assertEquals(CeremonyOutcome.VERIFIED, attempt.outcome)
            assertEquals(setOf("GetAssertion", "GetInfo-probe"), attempt.operations.map { it.type }.toSet())
            assertEquals(
                2,
                attempt.operations
                    .map { it.operationId }
                    .toSet()
                    .size,
            )
            assertTrue(attempt.operations.all { it.actionId == "one-rp-action" })
            assertTrue(attempt.operations.all { (it.interval.elapsedNanos ?: -1L) >= 0L })
        }

    @Test
    fun `verification outcomes govern whether the operation workflow can continue`() =
        runBlocking {
            val cases =
                mapOf(
                    VerificationOutcome.DENIAL to CeremonyOutcome.FAILED,
                    VerificationOutcome.CANCELLED to CeremonyOutcome.CANCELLED,
                    VerificationOutcome.TIMEOUT to CeremonyOutcome.TIMED_OUT,
                    VerificationOutcome.ERROR to CeremonyOutcome.FAILED,
                )
            cases.forEach { (verificationOutcome, expectedOutcome) ->
                val attempt =
                    SimulatedCeremony(SimulatedUserVerification()).run(
                        runId = "synthetic-run",
                        attemptId = verificationOutcome.name,
                        actionId = "action-${verificationOutcome.name}",
                        kind = CeremonyKind.REGISTRATION,
                        attemptKind = AttemptKind.MEASURED,
                        issueRequest = { request() },
                        execute = {
                            requireSuccessfulUserVerification(verificationOutcome)
                            operation("MakeCredential") { "reachable only after success" }
                        },
                        verify = { _, _ -> false },
                    )

                assertEquals(expectedOutcome, attempt.outcome)
                assertEquals(verificationOutcome, attempt.verification.outcome)
                assertTrue((attempt.verification.interval?.elapsedNanos ?: -1L) >= 0L)
                assertTrue(attempt.operations.isEmpty())
                assertEquals(false, attempt.verifiedResponse)
            }
        }

    private fun request() =
        SimulatedRelyingParty.Request(
            rpId = "example.com",
            origin = "https://example.com",
            challenge = ByteArray(32) { it.toByte() },
            clientDataHash = ByteArray(32) { (it + 1).toByte() },
        )
}
