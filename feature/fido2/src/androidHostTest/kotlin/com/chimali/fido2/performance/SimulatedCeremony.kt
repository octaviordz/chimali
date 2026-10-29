package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Runs one host-side RP action while retaining its raw request-to-verification interval and nested app spans. */
@Req("TR-PERF-010")
class SimulatedCeremony(
    private val verificationDouble: SimulatedUserVerification,
) {
    class ActionTrace internal constructor(
        val actionId: String,
        private val verificationDouble: SimulatedUserVerification,
    ) {
        private val recordedOperations = mutableListOf<OperationRecord>()
        private var verificationRecord: VerificationRecord? = null
        private var handoffInterval: MonotonicInterval? = null

        val operations: List<OperationRecord> get() = synchronized(recordedOperations) { recordedOperations.toList() }
        val verification: VerificationRecord get() =
            verificationRecord
                ?: VerificationRecord(VerificationOutcome.NOT_INVOKED, null)
        val bluetoothHandoff: MonotonicInterval? get() = handoffInterval

        @Suppress("TooGenericExceptionCaught", "ThrowsCount")
        suspend fun <T> operation(
            type: String,
            work: suspend () -> T,
        ): T {
            val id = UUID.randomUUID().toString()
            val start = MonotonicMeasurement.begin()
            var outcome = "completed"
            try {
                return work()
            } catch (timeout: TimeoutCancellationException) {
                outcome = "timed_out"
                throw timeout
            } catch (cancelled: CancellationException) {
                outcome = "cancelled"
                throw cancelled
            } catch (failure: Exception) {
                outcome = "failed:${failure::class.simpleName ?: "unknown"}"
                throw failure
            } finally {
                val end = MonotonicMeasurement.end()
                val record =
                    OperationRecord(
                        operationId = id,
                        actionId = actionId,
                        type = type,
                        interval = MonotonicMeasurement.interval(start, end),
                        terminalOutcome = outcome,
                    )
                synchronized(recordedOperations) { recordedOperations += record }
            }
        }

        fun userVerification(outcome: VerificationOutcome): VerificationRecord {
            check(verificationRecord == null) { "only one user-verification invocation is supported per attempt" }
            return verificationDouble.provide(outcome).also { verificationRecord = it }
        }

        /** Records the test-double outcome and permits the production operation only on success. */
        @Suppress("ThrowsCount")
        suspend fun requireSuccessfulUserVerification(outcome: VerificationOutcome): VerificationRecord {
            val record = userVerification(outcome)
            when (record.outcome) {
                VerificationOutcome.SUCCESS -> Unit
                VerificationOutcome.DENIAL -> throw SecurityException("simulated user verification denied")
                VerificationOutcome.CANCELLED -> throw CancellationException("simulated user verification cancelled")
                VerificationOutcome.TIMEOUT -> withTimeout(0) { Unit }
                VerificationOutcome.ERROR -> error("simulated user verification failed")
                VerificationOutcome.NOT_INVOKED -> error("NOT_INVOKED must use notInvoked()")
            }
            return record
        }

        fun notInvoked(): VerificationRecord {
            check(verificationRecord == null) { "verification state has already been recorded" }
            return verificationDouble.notInvoked().also { verificationRecord = it }
        }

        fun recordBluetoothHandoff(interval: MonotonicInterval) {
            check(handoffInterval == null) { "only one response handoff interval is supported per attempt" }
            handoffInterval = interval
        }
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    suspend fun <Response> run(
        runId: String,
        attemptId: String = UUID.randomUUID().toString(),
        actionId: String = UUID.randomUUID().toString(),
        kind: CeremonyKind,
        attemptKind: AttemptKind,
        issueRequest: () -> SimulatedRelyingParty.Request,
        execute: suspend ActionTrace.() -> Response,
        verify: (SimulatedRelyingParty.Request, Response) -> Boolean,
    ): CeremonyAttempt {
        require(runId.isNotBlank() && attemptId.isNotBlank() && actionId.isNotBlank())
        val trace = ActionTrace(actionId, verificationDouble)
        val start = MonotonicMeasurement.begin()
        var outcome = CeremonyOutcome.FAILED
        var reason: String? = null
        try {
            val request = issueRequest()
            val response = trace.execute()
            outcome = if (verify(request, response)) CeremonyOutcome.VERIFIED else CeremonyOutcome.REJECTED
            if (outcome == CeremonyOutcome.REJECTED) reason = "independent relying-party verification rejected response"
        } catch (timeout: TimeoutCancellationException) {
            outcome = CeremonyOutcome.TIMED_OUT
            reason = "ceremony timed out"
        } catch (cancelled: CancellationException) {
            outcome = CeremonyOutcome.CANCELLED
            reason = "ceremony cancelled"
        } catch (failure: Exception) {
            outcome = CeremonyOutcome.FAILED
            reason = "ceremony failed: ${failure::class.simpleName ?: "unknown"}"
        }
        val end = MonotonicMeasurement.end()
        val verificationRecord = trace.verification
        val operations = trace.operations
        val exclusions =
            buildList {
                verificationRecord.interval?.let { add(ExclusionRecord(ExclusionRecord.Kind.USER_VERIFICATION, it)) }
                trace.bluetoothHandoff?.let {
                    add(
                        ExclusionRecord(ExclusionRecord.Kind.BLUETOOTH_RESPONSE_HANDOFF, it),
                    )
                }
            }
        val attempt =
            CeremonyAttempt(
                runId = runId,
                attemptId = attemptId,
                actionId = actionId,
                kind = kind,
                attemptKind = attemptKind,
                outcome = outcome,
                ceremonyInterval = MonotonicMeasurement.interval(start, end),
                verifiedResponse = outcome == CeremonyOutcome.VERIFIED,
                verification = verificationRecord,
                operations = operations,
                bluetoothHandoff = trace.bluetoothHandoff,
                operationAssessments =
                    operations.map {
                        TimingAssessment.assess(
                            it,
                            exclusions,
                            eligibleForDecision =
                                attemptKind == AttemptKind.MEASURED && outcome == CeremonyOutcome.VERIFIED,
                            responseVerified = outcome == CeremonyOutcome.VERIFIED,
                        )
                    },
                failureReason = reason,
            )
        return attempt
    }
}
