@file:Suppress("ForbiddenImport")

package com.chimali.fido2.util.performance

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// This test observes Android's direct logging API because that is the sink under audit.
class BaselineMeasurementsPrivacyTest {
    /** @see NFR-SEC-030, FR-008, FR-009, and NFR-PERF-030 */
    @Test
    fun `direct Android timing output uses the fixed operation label`() {
        val records = mutableListOf<String>()
        mockkStatic(Log::class)
        every { Log.println(any<Int>(), any<String>(), any<String>()) } answers {
            records += thirdArg<String>()
            0
        }

        try {
            val operation = LatencyOperation.MAKE_CREDENTIAL
            val packet = byteArrayOf(1, 2, 3)
            BaselineMeasurements.start(operation)
            BaselineMeasurements.processingComplete(operation)
            BaselineMeasurements.trackResponse(operation, listOf(packet))
            BaselineMeasurements.reportAccepted(packet, accepted = true)

            assertEquals(1, records.size)
            assertTrue(records.single().contains("operation=MakeCredential"))
            val stringParametersAccepted =
                BaselineMeasurements::class.java.methods
                    .filter {
                        it.name in
                            setOf(
                                "start",
                                "startUserInteraction",
                                "endUserInteraction",
                                "processingComplete",
                                "trackResponse",
                                "traceContext",
                            )
                    }.any { method ->
                        method.parameterTypes.any { it == String::class.java }
                    }
            assertFalse(
                stringParametersAccepted,
                "The timing emitter must not expose caller-supplied string parameters",
            )
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
