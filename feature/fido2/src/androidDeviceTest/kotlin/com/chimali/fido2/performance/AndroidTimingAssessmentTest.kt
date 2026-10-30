package com.chimali.fido2.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device evidence checks for TR-PERF-010 Android monotonic timing and continuity. */
@RunWith(AndroidJUnit4::class)
@Suppress("MagicNumber")
class AndroidTimingAssessmentTest {
    @Test
    fun realPrimaryAndElapsedRealtimeClocksProduceRetainedDeltas() {
        val start = MonotonicMeasurement.begin()
        Thread.yield()
        val end = MonotonicMeasurement.end()
        val interval = MonotonicMeasurement.interval(start, end)

        assertTrue(requireNotNull(interval.elapsedNanos) >= 0L)
        assertNotNull(interval.suspendAwareElapsedNanos)
        assertTrue(interval.clockDivergenceNanos != null)
        assertEquals(MonotonicMeasurement.CLOCK_DOMAIN, interval.clockDomain)
    }

    @Test
    fun divergenceBeyondFiveMillisecondsIsIncompleteInEitherDirection() {
        val suspend =
            MonotonicMeasurement.interval(
                MonotonicMeasurement.Reading(1_000L, 1_000L),
                MonotonicMeasurement.Reading(10_000_000L, 16_000_001L),
            )
        val discontinuity =
            MonotonicMeasurement.interval(
                MonotonicMeasurement.Reading(1_000L, 1_000L),
                MonotonicMeasurement.Reading(16_000_001L, 10_000_000L),
            )

        assertEquals(ClockContinuity.SUSPEND_DETECTED, suspend.continuity)
        assertEquals(ClockContinuity.CLOCK_DISCONTINUITY, discontinuity.continuity)
    }

    @Test
    fun boundaryDivergenceIsValidAndUnavailableSourceIsIncomplete() {
        val withinBoundary =
            MonotonicMeasurement.interval(
                MonotonicMeasurement.Reading(1_000L, 1_000L),
                MonotonicMeasurement.Reading(10_000_000L, 15_000_000L),
            )
        val unavailable =
            MonotonicMeasurement.interval(
                MonotonicMeasurement.Reading(1_000L, null),
                MonotonicMeasurement.Reading(10_000_000L, null),
            )

        assertEquals(ClockContinuity.VERIFIED, withinBoundary.continuity)
        assertEquals(5_000_000L, withinBoundary.clockDivergenceNanos)
        assertEquals(ClockContinuity.UNAVAILABLE, unavailable.continuity)
        assertNull(MonotonicMeasurement.readElapsedRealtimeOrNull { error("clock unavailable") })
    }

    @Test
    fun overlappingExclusionsAreSubtractedOnlyOnce() {
        val operation =
            OperationRecord(
                operationId = "android-operation",
                actionId = "android-action",
                type = "MakeCredential",
                interval = MonotonicInterval(0L, 100L, MonotonicMeasurement.CLOCK_DOMAIN, ClockContinuity.VERIFIED),
                terminalOutcome = "completed",
            )
        val exclusions =
            listOf(
                ExclusionRecord(
                    ExclusionRecord.Kind.USER_VERIFICATION,
                    MonotonicInterval(10L, 40L, MonotonicMeasurement.CLOCK_DOMAIN, ClockContinuity.VERIFIED),
                ),
                ExclusionRecord(
                    ExclusionRecord.Kind.BLUETOOTH_RESPONSE_HANDOFF,
                    MonotonicInterval(30L, 60L, MonotonicMeasurement.CLOCK_DOMAIN, ClockContinuity.VERIFIED),
                ),
            )

        val result = TimingAssessment.assess(operation, exclusions)

        assertEquals(50L, result.excludedDurationNanos)
        assertEquals(50L, result.assessedDurationNanos)
    }
}
