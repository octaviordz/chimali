package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.testing.CapturedDiagnostics
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** @see NFR-SEC-041 and TR-AUTH-031 */
@OptIn(ExperimentalCoroutinesApi::class)
class HidReportSenderTest {
    /** @see NFR-SEC-041, FR-003, FR-008 and FR-009 */
    @Test
    fun `report completion returns the original correlation owner after sending a copy`() =
        runTest {
            val original = ByteArray(62) { 0x4A }
            var correlated: ByteArray? = null
            var accepted: Boolean? = null
            val sender =
                HidReportSender(
                    scope = this,
                    dispatcher = { StandardTestDispatcher(testScheduler) },
                    sessionGeneration = { 1L },
                    sendReport = { packet, _ ->
                        assertContentEquals(original, packet)
                        assertFalse(packet === original, "The sender must use its independent queued copy")
                        true
                    },
                    reportAccepted = { owner, result ->
                        correlated = owner
                        accepted = result
                    },
                    reportPaceDelayMillis = 0,
                )
            sender.start()
            runCurrent()

            assertTrue(sender.enqueueAndAwait(listOf(original), expectedGeneration = 1L))

            assertSame(original, correlated, "Correlation removes the exact response owner from tracking")
            assertTrue(accepted == true)
            assertTrue(original.any { it != 0.toByte() }, "The caller retains the response until its own release")
            sender.stop()
            runCurrent()
            original.fill(0)
        }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `replacement settles predecessor waiter erases its payload and delivers successor`() =
        runTest {
            val senderDispatcher = StandardTestDispatcher(testScheduler)
            var generation = 1L
            val predecessorEnteredDriver = CompletableDeferred<ByteArray>()
            val delivered = mutableListOf<ByteArray>()
            val sender =
                HidReportSender(
                    scope = this,
                    dispatcher = { senderDispatcher },
                    sessionGeneration = { generation },
                    sendReport = { packet, session ->
                        if (session == 1L) {
                            predecessorEnteredDriver.complete(packet)
                            awaitCancellation()
                        }
                        delivered += packet.copyOf()
                        true
                    },
                    reportAccepted = { _, _ -> },
                    reportPaceDelayMillis = 0,
                )

            sender.start()
            runCurrent()
            val predecessorSource = ByteArray(62) { 0x31 }
            val predecessorExpected = predecessorSource.copyOf()
            val predecessorWaiter = async { sender.enqueueAndAwait(listOf(predecessorSource), 1L) }
            runCurrent()
            val predecessorDriverBuffer = predecessorEnteredDriver.await()
            assertContentEquals(predecessorExpected, predecessorDriverBuffer)

            generation = 2L
            sender.start()
            val successor = ByteArray(62) { 0x62 }
            val successorWaiter = async { sender.enqueueAndAwait(listOf(successor), 2L) }
            runCurrent()

            assertFalse(predecessorWaiter.await(), "Replacement must settle the displaced handoff waiter")
            assertTrue(predecessorDriverBuffer.all { it == 0.toByte() }, "Cancelled driver buffer must be erased")
            assertTrue(successorWaiter.await(), "The successor response must reach the current host")
            assertContentEquals(successor, delivered.single())
            sender.stop()
            runCurrent()
        }

    @Test
    @Req("FR-008", "FR-009", "FR-HID-010", "FR-VAL-020")
    fun `driver exception diagnostic omits platform exception details`() =
        runTest {
            val marker = "synthetic-private-hid-driver-detail"
            val sender =
                HidReportSender(
                    scope = this,
                    dispatcher = { StandardTestDispatcher(testScheduler) },
                    sessionGeneration = { 1L },
                    sendReport = { _, _ -> throw IllegalStateException(marker) },
                    reportAccepted = { _, _ -> },
                    reportPaceDelayMillis = 0,
                )
            sender.start()
            runCurrent()

            CapturedDiagnostics().use { diagnostics ->
                assertFalse(sender.enqueueAndAwait(listOf(byteArrayOf(1, 2, 3)), 1L))
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(marker)
            }
            sender.stop()
            runCurrent()
        }
}
