package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Req("TR-PERF-010")
class SimulatedUserVerificationTest {
    @Test
    fun `returns each configured outcome immediately and records intrinsic intervals`() {
        val double = SimulatedUserVerification()
        val outcomes =
            listOf(
                VerificationOutcome.SUCCESS,
                VerificationOutcome.DENIAL,
                VerificationOutcome.CANCELLED,
                VerificationOutcome.TIMEOUT,
                VerificationOutcome.ERROR,
            )

        val records = outcomes.map(double::provide)

        assertEquals(outcomes, records.map { it.outcome })
        assertEquals(outcomes.size, double.invocationCount)
        assertEquals(records, double.invocations)
        assertTrue(records.all { (it.interval?.elapsedNanos ?: -1L) >= 0L })
    }

    @Test
    fun `not invoked has no execution interval or duration`() {
        val record = SimulatedUserVerification().notInvoked()

        assertEquals(VerificationOutcome.NOT_INVOKED, record.outcome)
        assertEquals(null, record.interval)
    }
}
