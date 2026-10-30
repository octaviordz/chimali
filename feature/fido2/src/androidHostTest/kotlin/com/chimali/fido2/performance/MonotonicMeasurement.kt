package com.chimali.fido2.performance

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary

/** Windows host clock pair. `System.nanoTime` supplies elapsed results; QueryUnbiased validates continuity. */
object MonotonicMeasurement {
    const val CLOCK_DOMAIN = ClockDomains.SYSTEM_NANO_TIME

    class Reading internal constructor(
        val monotonicNanos: Long,
        val suspendAwareNanos: Long?,
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
        val suspendElapsed =
            if (start.suspendAwareNanos != null && end.suspendAwareNanos != null) {
                end.suspendAwareNanos - start.suspendAwareNanos
            } else {
                null
            }
        val divergence = suspendElapsed?.let { elapsed - it }
        val continuity =
            when {
                elapsed < 0L || suspendElapsed?.let { it < 0L } == true -> ClockContinuity.CLOCK_DISCONTINUITY
                divergence == null -> ClockContinuity.UNAVAILABLE
                divergence > ClockDomains.MAX_DIVERGENCE_NANOS -> ClockContinuity.SUSPEND_DETECTED
                divergence < -ClockDomains.MAX_DIVERGENCE_NANOS -> ClockContinuity.CLOCK_DISCONTINUITY
                else -> ClockContinuity.VERIFIED
            }
        return MonotonicInterval(
            start.monotonicNanos,
            end.monotonicNanos,
            CLOCK_DOMAIN,
            continuity,
            suspendElapsed,
            divergence,
        )
    }

    private fun capture(): Reading {
        val before = System.nanoTime()
        val suspendAware = WindowsSuspendClock.readNanosOrNull()
        val after = System.nanoTime()
        return Reading(before + (after - before) / 2L, suspendAware)
    }

    private object WindowsSuspendClock {
        private val api: QueryUnbiasedInterruptTime? by lazy {
            if (!System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
                null
            } else {
                Native.load(
                    "kernelbase",
                    QueryUnbiasedInterruptTime::class.java,
                    mapOf(
                        Library.OPTION_FUNCTION_MAPPER to
                            FunctionMapper { _, method ->
                                if (method.name == "queryUnbiasedInterruptTimePrecise") {
                                    "QueryUnbiasedInterruptTimePrecise"
                                } else {
                                    method.name
                                }
                            },
                    ),
                )
            }
        }

        fun readNanosOrNull(): Long? =
            readSuspendClockNanosOrNull {
                val clock = api ?: return@readSuspendClockNanosOrNull null
                val ticks = LongByReference()
                clock.queryUnbiasedInterruptTimePrecise(ticks)
                Math.multiplyExact(ticks.value, 100L)
            }

        private interface QueryUnbiasedInterruptTime : StdCallLibrary {
            fun queryUnbiasedInterruptTimePrecise(unbiasedTime: LongByReference)
        }
    }
}

/** Native clock loading and invocation failures retain incomplete timing evidence. */
internal fun readSuspendClockNanosOrNull(read: () -> Long?): Long? =
    try {
        read()
    } catch (_: LinkageError) {
        null
    } catch (_: RuntimeException) {
        null
    }
