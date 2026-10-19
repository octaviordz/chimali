package com.chimali.fido2.util.performance

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** @see NFR-PERF-030 */
class LatencyProfilerUserWindowTest {
    /**
     * @see NFR-PERF-030
     * @see FR-VAL-030
     */
    @Test
    @Req("NFR-PERF-030", "FR-VAL-030")
    fun `window ends at confirmation and includes subsequent processing latency`() =
        runBlocking {
            val startNs = System.nanoTime()
            val interactionCompleted = CompletableDeferred<Unit>()
            var closeCount = 0
            var closeNs = 0L

            val response =
                withUserInteractionWindow(
                    interactionCompleted,
                    closeWindow = {
                        closeCount += 1
                        closeNs = System.nanoTime()
                    },
                ) {
                    delay(60)
                    interactionCompleted.complete(Unit)
                    delay(45)
                    "response"
                }

            val totalMs = (System.nanoTime() - startNs) / 1_000_000
            val userMs = (closeNs - startNs) / 1_000_000
            val measuredSystemMs = totalMs - userMs
            assertEquals("response", response)
            assertEquals(1, closeCount)
            assertTrue(userMs >= 35, "verification wait should be inside the excluded window: $userMs ms")
            assertTrue(measuredSystemMs >= 25, "post-confirmation work must remain measured: $measuredSystemMs ms")
        }

    /**
     * @see NFR-PERF-030
     * @see FR-VAL-030
     */
    @Test
    @Req("NFR-PERF-030", "FR-VAL-030")
    fun `window closes exactly once when response processing fails`() =
        runBlocking {
            val signal = CompletableDeferred<Unit>()
            var closeCount = 0
            assertFailsWith<IllegalStateException> {
                withUserInteractionWindow(signal, { closeCount += 1 }) {
                    error("synthetic processing failure")
                }
            }
            assertTrue(signal.isCompleted)
            assertEquals(1, closeCount)
        }

    /**
     * @see NFR-PERF-030
     * @see FR-VAL-030
     */
    @Test
    @Req("NFR-PERF-030", "FR-VAL-030")
    fun `window closes exactly once when response processing is cancelled`() =
        runBlocking {
            val signal = CompletableDeferred<Unit>()
            var closeCount = 0
            val operation =
                launch {
                    withUserInteractionWindow(signal, { closeCount += 1 }) { awaitCancellation() }
                }
            delay(10)
            operation.cancelAndJoin()
            assertTrue(signal.isCompleted)
            assertEquals(1, closeCount)
        }
}
