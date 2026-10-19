@file:Suppress("ForbiddenImport")

package com.chimali.fido2.data.transport

import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_PING
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import io.mockk.coEvery
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** Production-boundary checks for host-session replacement and delayed old-owner cleanup. */
class BluetoothHidTransportSessionGenerationTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `delayed predecessor teardown preserves successor parser channel and control traffic`() =
        runTest {
            val lifecycle = BluetoothHidTransportLifecycleTest()
            val senderScheduler = TestCoroutineScheduler()
            with(lifecycle) {
                withFixture(
                    phase = "candidates",
                    sendQueueDispatcher = StandardTestDispatcher(senderScheduler),
                    useRealWrapper = true,
                ) { fixture ->
                    val predecessorCleanupStarted = CompletableDeferred<Unit>()
                    val releasePredecessorCleanup = CompletableDeferred<Unit>()
                    val predecessorHandlerStarted = CompletableDeferred<Unit>()
                    val successorHandlerStarted = CompletableDeferred<Unit>()
                    var predecessorOwnedPayload: ByteArray? = null
                    var successorOwnedPayload: ByteArray? = null
                    fixture.transport.onCborRequestPayloadForTest = { payload ->
                        when (payload.getOrNull(1)) {
                            PREDECESSOR_MARKER -> predecessorOwnedPayload = payload
                            SUCCESSOR_MARKER -> successorOwnedPayload = payload
                        }
                    }
                    coEvery { fixture.registrationHandler.handle(any()) } coAnswers {
                        val marker = firstArg<CtapHidMessage>().payload.getOrNull(1)
                        if (marker == PREDECESSOR_MARKER) {
                            predecessorHandlerStarted.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                withContext(NonCancellable) {
                                    predecessorCleanupStarted.complete(Unit)
                                    releasePredecessorCleanup.await()
                                }
                            }
                        } else {
                            successorHandlerStarted.complete(Unit)
                            awaitCancellation()
                        }
                        emptyList()
                    }

                    try {
                        fixture.connectHost()
                        val predecessorGeneration = fixture.transportSessionGeneration()
                        fixture.sendHostReport(lifecycle.initRequestReport(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
                        runCurrent()
                        senderScheduler.runCurrent()
                        runCurrent()
                        val predecessorCid = lifecycle.allocatedCidFromInitResponse(fixture.sent.last())

                        fixture.sendHostReport(
                            lifecycle.cborRequestReport(predecessorCid, byteArrayOf(0x01, PREDECESSOR_MARKER)),
                        )
                        advanceTimeBy(BOOTSTRAP_WAIT_MILLIS)
                        runCurrent()
                        assertTrue(predecessorHandlerStarted.isCompleted)
                        val retainedPredecessorPayload = requireNotNull(predecessorOwnedPayload)

                        fixture.disconnectHost()
                        fixture.connectHost()
                        runCurrent()
                        assertTrue(
                            predecessorCleanupStarted.isCompleted,
                            "Old-generation teardown is deliberately held",
                        )
                        assertTrue(
                            retainedPredecessorPayload.any { it != 0.toByte() },
                            "Borrowed handler input remains live until its delayed teardown completes",
                        )
                        val successorGeneration = fixture.transportSessionGeneration()
                        assertTrue(successorGeneration > predecessorGeneration)

                        fixture.sendHostReport(lifecycle.initRequestReport(byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)))
                        runCurrent()
                        senderScheduler.advanceUntilIdle()
                        runCurrent()
                        val successorCid = lifecycle.allocatedCidFromInitResponse(fixture.sent.last())
                        assertTrue(fixture.transport.isChannelRegisteredForTest(successorCid, successorGeneration))
                        assertFalse(fixture.transport.isChannelRegisteredForTest(predecessorCid, successorGeneration))

                        val beforeStaleControl = fixture.sent.size
                        fixture.sendHostReport(
                            lifecycle.requestReport(predecessorCid, CTAPHID_PING, byteArrayOf(0x41)),
                        )
                        runCurrent()
                        verify(exactly = 1) {
                            fixture.responses.hidErrorResponse(
                                match { it.contentEquals(predecessorCid) },
                                INVALID_CHANNEL,
                            )
                        }
                        assertEquals(
                            beforeStaleControl,
                            fixture.sent.size,
                            "Stale PING is rejected without echoing its payload",
                        )

                        fixture.sendHostReport(lifecycle.requestReport(successorCid, CTAPHID_PING, byteArrayOf(0x42)))
                        runCurrent()
                        senderScheduler.advanceUntilIdle()
                        runCurrent()
                        val pingResponse =
                            requireNotNull(HidReportParser().processReport(fixture.sent.last()).getOrNull())
                        assertEquals(CTAPHID_PING, pingResponse.command)
                        assertContentEquals(byteArrayOf(0x42), pingResponse.payload)
                        pingResponse.payload.fill(0)

                        val successorCborPayload = byteArrayOf(0x01, SUCCESSOR_MARKER) + ByteArray(70) { 0x55 }
                        val successorReports =
                            HidReportParser().encodeResponse(
                                CtapHidMessage(successorCid, CTAPHID_CBOR, successorCborPayload),
                            )
                        assertTrue(
                            successorReports.size > 1,
                            "The successor request exercises continuation-fragment parsing",
                        )
                        successorReports.forEach(fixture::sendHostReport)
                        advanceTimeBy(BOOTSTRAP_WAIT_MILLIS)
                        runCurrent()
                        assertTrue(
                            successorHandlerStarted.isCompleted,
                            "Successor fragments reach the live request handler",
                        )
                        val retainedSuccessorPayload = requireNotNull(successorOwnedPayload)
                        assertTrue(retainedSuccessorPayload.any { it != 0.toByte() })

                        releasePredecessorCleanup.complete(Unit)
                        runCurrent()
                        assertTrue(retainedPredecessorPayload.all { it == 0.toByte() })
                        assertTrue(
                            retainedSuccessorPayload.any { it != 0.toByte() },
                            "Completing old teardown does not erase the successor's borrowed payload",
                        )

                        fixture.disconnectHost()
                        runCurrent()
                        assertTrue(retainedSuccessorPayload.all { it == 0.toByte() })
                    } finally {
                        releasePredecessorCleanup.complete(Unit)
                        fixture.disconnectHost()
                        runCurrent()
                    }
                }
            }
        }

    private companion object {
        const val PREDECESSOR_MARKER: Byte = 0x11
        const val SUCCESSOR_MARKER: Byte = 0x22
        const val BOOTSTRAP_WAIT_MILLIS = 201L
        const val INVALID_CHANNEL: Byte = 0x0B
    }
}
