package com.chimali.fido2.performance

import android.os.SystemClock

/** Android clock pair. The primary `System.nanoTime` delta is the only reported elapsed duration. */
object MonotonicMeasurement {
    const val CLOCK_DOMAIN = ClockDomains.ANDROID_NANO_TIME

    class Reading internal constructor(
        val monotonicNanos: Long,
        val elapsedRealtimeNanos: Long?,
    )

    fun nowNanos(): Long = System.nanoTime()

    fun begin(): Reading = capture()

    fun end(): Reading = capture()

    fun interval(
        startNanos: Long,
        endNanos: Long,
    ): MonotonicInterval = MonotonicInterval(startNanos, endNanos, CLOCK_DOMAIN)

    fun interval(
        start: Reading,
        end: Reading,
    ): MonotonicInterval {
        val elapsed = end.monotonicNanos - start.monotonicNanos
        val elapsedRealtime =
            if (start.elapsedRealtimeNanos != null && end.elapsedRealtimeNanos != null) {
                end.elapsedRealtimeNanos - start.elapsedRealtimeNanos
            } else {
                null
            }
        val difference = elapsedRealtime?.let { it - elapsed }
        val continuity =
            when {
                elapsed < 0L || elapsedRealtime?.let { it < 0L } == true -> ClockContinuity.CLOCK_DISCONTINUITY
                difference == null -> ClockContinuity.UNAVAILABLE
                difference > ClockDomains.MAX_DIVERGENCE_NANOS -> ClockContinuity.SUSPEND_DETECTED
                difference < -ClockDomains.MAX_DIVERGENCE_NANOS -> ClockContinuity.CLOCK_DISCONTINUITY
                else -> ClockContinuity.VERIFIED
            }
        return MonotonicInterval(
            startNanos = start.monotonicNanos,
            endNanos = end.monotonicNanos,
            clockDomain = CLOCK_DOMAIN,
            continuity = continuity,
            suspendAwareElapsedNanos = elapsedRealtime,
            clockDivergenceNanos = difference,
        )
    }

    private fun capture(): Reading {
        val before = System.nanoTime()
        val elapsedRealtime = readElapsedRealtimeOrNull()
        val after = System.nanoTime()
        return Reading(before + (after - before) / 2L, elapsedRealtime)
    }

    internal fun readElapsedRealtimeOrNull(read: () -> Long = SystemClock::elapsedRealtimeNanos): Long? =
        try {
            read()
        } catch (_: LinkageError) {
            null
        } catch (_: RuntimeException) {
            null
        }
}
