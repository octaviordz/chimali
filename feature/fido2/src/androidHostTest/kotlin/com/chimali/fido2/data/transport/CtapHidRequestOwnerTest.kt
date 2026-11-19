package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.testing.CapturedDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** @see NFR-SEC-041 and TR-AUTH-031 */
@OptIn(ExperimentalCoroutinesApi::class)
class CtapHidRequestOwnerTest {
    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `total active requests are bounded and rejected owner bytes are erased`() =
        runTest {
            val admittedPayloads = mutableListOf<ByteArray>()
            val admittedCids = mutableListOf<ByteArray>()
            val busyCids = mutableListOf<ByteArray>()
            val owner =
                CtapHidRequestOwner(
                    scope = backgroundScope,
                    dispatcher = { StandardTestDispatcher(testScheduler) },
                    monotonicNanos = { testScheduler.currentTime * 1_000_000L },
                    isSessionCurrent = { it == 1L },
                    handleRequest = { message, _, _ ->
                        admittedPayloads += message.payload
                        admittedCids += message.channelId
                        awaitCancellation()
                    },
                    reportChannelBusy = { cid, _ -> busyCids += cid.copyOf() },
                    onPayloadForTest = {},
                )

            repeat(CtapHidRequestOwner.MAX_ACTIVE_REQUESTS) { index ->
                owner.dispatch(CtapHidMessage(cid(index), 0x10, byteArrayOf(index.toByte())), 1L)
            }
            runCurrent()

            val rejectedPayload = byteArrayOf(0x7F)
            val rejectedCid = cid(100)
            owner.dispatch(CtapHidMessage(rejectedCid, 0x10, rejectedPayload), 1L)
            assertTrue(rejectedPayload.all { it == 0.toByte() })
            assertTrue(rejectedCid.all { it == 0.toByte() })
            assertEquals(1, busyCids.size)
            assertEquals(CtapHidRequestOwner.MAX_ACTIVE_REQUESTS, admittedPayloads.size)

            owner.cancelAll()
            runCurrent()
            assertTrue(admittedPayloads.all { payload -> payload.all { it == 0.toByte() } })
            assertTrue(admittedCids.all { channel -> channel.all { it == 0.toByte() } })
        }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `ended generation releases its CID without blocking successor request ownership`() =
        runTest {
            var currentGeneration = 1L
            val admitted = mutableListOf<ByteArray>()
            val busy = mutableListOf<ByteArray>()
            val owner =
                CtapHidRequestOwner(
                    scope = backgroundScope,
                    dispatcher = { StandardTestDispatcher(testScheduler) },
                    monotonicNanos = { testScheduler.currentTime * 1_000_000L },
                    isSessionCurrent = { it == currentGeneration },
                    handleRequest = { message, _, _ ->
                        admitted += message.payload
                        awaitCancellation()
                    },
                    reportChannelBusy = { cid, _ -> busy += cid.copyOf() },
                )
            val oldPayload = byteArrayOf(0x31)
            owner.dispatch(CtapHidMessage(cid(7), 0x10, oldPayload), 1L)
            runCurrent()

            currentGeneration = 2L
            owner.cancelGeneration(1L)
            val successorPayload = byteArrayOf(0x62)
            owner.dispatch(CtapHidMessage(cid(7), 0x10, successorPayload), 2L)
            runCurrent()

            assertEquals(2, admitted.size)
            assertTrue(oldPayload.all { it == 0.toByte() }, "Ended owner payload is erased")
            assertTrue(successorPayload.any { it != 0.toByte() }, "Successor retains its own active payload")
            assertTrue(busy.isEmpty(), "An old CID does not make the successor channel busy")

            owner.cancelGeneration(2L)
            runCurrent()
            assertTrue(successorPayload.all { it == 0.toByte() })
        }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `stale request admission erases input without emitting a successor busy response`() =
        runTest {
            val busy = mutableListOf<ByteArray>()
            val owner =
                CtapHidRequestOwner(
                    scope = backgroundScope,
                    dispatcher = { StandardTestDispatcher(testScheduler) },
                    monotonicNanos = { 0L },
                    isSessionCurrent = { it == 2L },
                    handleRequest = { _, _, _ -> error("Stale request must never run") },
                    reportChannelBusy = { cid, _ -> busy += cid.copyOf() },
                )
            val stalePayload = byteArrayOf(0x4A)
            val staleCid = cid(8)
            owner.dispatch(CtapHidMessage(staleCid, 0x10, stalePayload), 1L)

            assertTrue(stalePayload.all { it == 0.toByte() })
            assertTrue(staleCid.all { it == 0.toByte() })
            assertTrue(busy.isEmpty())
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `unexpected handler failure omits throwable details and still erases the request`() =
        runTest {
            val marker = "synthetic-ctaphid-handler-failure-marker"
            val payload = byteArrayOf(0x31, 0x32)
            val channelId = cid(9)
            val owner =
                CtapHidRequestOwner(
                    scope = backgroundScope,
                    dispatcher = { StandardTestDispatcher(testScheduler) },
                    monotonicNanos = { 0L },
                    isSessionCurrent = { it == 1L },
                    handleRequest = { _, _, _ -> throw IllegalStateException(marker) },
                    reportChannelBusy = { _, _ -> error("Unexpected handler failure must not be reported as busy") },
                )

            CapturedDiagnostics().use { logs ->
                owner.dispatch(CtapHidMessage(channelId, 0x10, payload), 1L)
                runCurrent()
                assertTrue(logs.size > 0)
                logs.assertNoSensitiveValues(marker)
            }
            assertTrue(payload.all { it == 0.toByte() }, "Request bytes must be erased when the handler fails")
            assertTrue(
                channelId.all { it == 0.toByte() },
                "Request channel owner must be erased when the handler fails",
            )
            owner.cancelAll()
        }

    private fun cid(value: Int) = byteArrayOf(0, 0, (value shr 8).toByte(), value.toByte())
}
