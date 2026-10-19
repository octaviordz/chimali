package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
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
}
