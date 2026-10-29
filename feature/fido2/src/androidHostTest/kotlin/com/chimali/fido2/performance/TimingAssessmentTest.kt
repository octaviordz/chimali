package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Req("TR-PERF-010")
class TimingAssessmentTest {
    private fun operation(
        id: String = "op-1",
        interval: MonotonicInterval = MonotonicInterval(1_000L, 200_001_000L, "test-jvm"),
    ) = OperationRecord(id, "action-1", "makeCredential", interval, "completed")

    @Test
    fun `subtracts union of user verification and handoff overlaps only once`() {
        val result =
            TimingAssessment.assess(
                operation(),
                listOf(
                    ExclusionRecord(
                        ExclusionRecord.Kind.USER_VERIFICATION,
                        MonotonicInterval(100_000_000L, 150_000_000L, "test-jvm"),
                    ),
                    ExclusionRecord(
                        ExclusionRecord.Kind.BLUETOOTH_RESPONSE_HANDOFF,
                        MonotonicInterval(125_000_000L, 175_000_000L, "test-jvm"),
                    ),
                ),
            )

        assertEquals(200_000_000L, result.rawDurationNanos)
        assertEquals(75_000_000L, result.excludedDurationNanos)
        assertEquals(125_000_000L, result.assessedDurationNanos)
        assertEquals(OperationDecision.PASS, result.decision)
        assertEquals(2, result.exclusions.size)
        assertEquals(
            100_000_000L,
            result.exclusions
                .first()
                .interval.startNanos,
        )
    }

    @Test
    fun `operation exactly at budget fails`() {
        val result =
            TimingAssessment.assess(
                operation(interval = MonotonicInterval(10L, 200_000_010L, "test-jvm")),
                emptyList(),
            )
        assertEquals(200_000_000L, result.assessedDurationNanos)
        assertEquals(OperationDecision.FAIL, result.decision)
    }

    @Test
    fun `invalid operation and incompatible exclusion are incomplete`() {
        val negative =
            TimingAssessment.assess(
                operation(interval = MonotonicInterval(20L, 10L, "test-jvm")),
                emptyList(),
            )
        val incompatible =
            TimingAssessment.assess(
                operation(),
                listOf(ExclusionRecord(ExclusionRecord.Kind.USER_VERIFICATION, MonotonicInterval(10L, 20L, "other"))),
            )

        assertEquals(OperationDecision.INCOMPLETE, negative.decision)
        assertEquals(OperationDecision.INCOMPLETE, incompatible.decision)
        assertNull(negative.assessedDurationNanos)
        assertFalse(negative.reason.isNullOrBlank())
    }

    @Test
    fun `real monotonic measurement reports ordered interval and checks continuity`() {
        val start = MonotonicMeasurement.begin()
        Thread.sleep(25L)
        val end = MonotonicMeasurement.end()
        val interval = MonotonicMeasurement.interval(start, end)
        assertEquals(MonotonicMeasurement.CLOCK_DOMAIN, interval.clockDomain)
        assertTrue((interval.elapsedNanos ?: -1L) >= 0L)
        if (System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
            assertTrue(interval.suspendAwareElapsedNanos != null)
            assertTrue(
                interval.continuity == ClockContinuity.VERIFIED ||
                    interval.continuity == ClockContinuity.SUSPEND_DETECTED ||
                    interval.continuity == ClockContinuity.CLOCK_DISCONTINUITY,
            )
            if (interval.continuity == ClockContinuity.VERIFIED) {
                assertTrue(kotlin.math.abs(requireNotNull(interval.clockDivergenceNanos)) <= 5_000_000L)
            } else {
                assertTrue(kotlin.math.abs(requireNotNull(interval.clockDivergenceNanos)) > 5_000_000L)
            }
        } else {
            assertEquals(ClockContinuity.UNAVAILABLE, interval.continuity)
        }
    }

    @Test
    fun `suspend or clock discontinuity evidence makes real-clock operation incomplete`() {
        val interval =
            MonotonicInterval(
                startNanos = 1_000L,
                endNanos = 20_000_000L,
                clockDomain = MonotonicMeasurement.CLOCK_DOMAIN,
                continuity = ClockContinuity.SUSPEND_DETECTED,
                suspendAwareElapsedNanos = 5_000L,
                clockDivergenceNanos = 18_994_000L,
            )

        val result = TimingAssessment.assess(operation(interval = interval), emptyList())

        assertEquals(OperationDecision.INCOMPLETE, result.decision)
        assertNull(result.assessedDurationNanos)
        assertEquals("host suspend detected during interval", result.reason)
    }

    @Test
    fun `native suspend clock failures are retained as incomplete measurement evidence`() {
        val loadFailure = readSuspendClockNanosOrNull { throw UnsatisfiedLinkError("native clock unavailable") }
        val invocationFailure = readSuspendClockNanosOrNull { throw IllegalStateException("native query failed") }
        val interval =
            MonotonicMeasurement.interval(
                MonotonicMeasurement.Reading(1_000L, loadFailure),
                MonotonicMeasurement.Reading(20_000L, invocationFailure),
            )
        val result =
            TimingAssessment.assess(
                operation(interval = interval),
                emptyList(),
            )
        val report =
            MeasurementReport.create(
                SimulatedWorkload(
                    version = "clock-failure-test",
                    warmupCount = 0,
                    repetitionCount = 1,
                    summary = "clock failure handling",
                    eligibleAfterWarmup = true,
                    operationTypes = setOf("makeCredential"),
                    exercisedOperationTypes = setOf("makeCredential"),
                    host = "host-test",
                    runtime = "JVM",
                    sourceRevision = "synthetic-test-revision",
                    realComponents = setOf("operation calculator"),
                    simulatedComponents = setOf("RP", "transport", "verification"),
                ),
                emptyList(),
                listOf(result),
            )

        assertEquals(ClockContinuity.UNAVAILABLE, interval.continuity)
        assertEquals(OperationDecision.INCOMPLETE, result.decision)
        assertEquals("suspend detection is unavailable on this host", result.reason)
        assertEquals(ProjectDisposition.INCOMPLETE, report.disposition)
        assertTrue(report.reasons.any { it.contains("insufficient eligible measured") })
    }

    @Test
    fun `negative monotonic origin is valid when elapsed delta is nonnegative`() {
        val interval = MonotonicInterval(-20L, 30L, "jvm:System.nanoTime")
        assertEquals(50L, interval.elapsedNanos)
    }

    @Test
    fun `unordered and overflowed intervals are incomplete`() {
        val reversed =
            TimingAssessment.assess(
                operation(interval = MonotonicInterval(10L, 9L, "test-jvm")),
                emptyList(),
            )
        val overflowed =
            TimingAssessment.assess(
                operation(interval = MonotonicInterval(Long.MIN_VALUE, Long.MAX_VALUE, "test-jvm")),
                emptyList(),
            )

        assertEquals(OperationDecision.INCOMPLETE, reversed.decision)
        assertEquals(OperationDecision.INCOMPLETE, overflowed.decision)
        assertNull(overflowed.assessedDurationNanos)
    }

    @Test
    fun `exclusions outside operation do not reduce its duration`() {
        val result =
            TimingAssessment.assess(
                operation(interval = MonotonicInterval(100L, 200L, "test-jvm")),
                listOf(ExclusionRecord(ExclusionRecord.Kind.USER_VERIFICATION, MonotonicInterval(0L, 50L, "test-jvm"))),
            )

        assertEquals(100L, result.rawDurationNanos)
        assertEquals(0L, result.excludedDurationNanos)
        assertEquals(100L, result.assessedDurationNanos)
    }

    @Test
    fun `failed operation retains duration as incomplete instead of passing`() {
        val result = TimingAssessment.assess(operation().copy(terminalOutcome = "failed:synthetic"), emptyList())

        assertEquals(OperationDecision.INCOMPLETE, result.decision)
        assertEquals(200_000_000L, result.rawDurationNanos)
        assertEquals(200_000_000L, result.assessedDurationNanos)
        assertTrue(result.reason?.contains("failed:synthetic") == true)
    }

    @Test
    fun `unverified response cannot produce an operation pass`() {
        val result = TimingAssessment.assess(operation(), emptyList(), responseVerified = false)

        assertEquals(OperationDecision.INCOMPLETE, result.decision)
        assertFalse(result.eligibleForDecision)
        assertEquals(200_000_000L, result.rawDurationNanos)
        assertEquals(200_000_000L, result.assessedDurationNanos)
    }
}
