package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * T013: Bounded FIFO notification ownership unit tests.
 *
 * Verifies:
 * - One in-flight notification at a time with asynchronous GATT completion.
 * - Strict FIFO packet ordering.
 * - Keepalive coalescing (no duplicate keepalives queueing up).
 * - Suppression of keepalives once response fragments start.
 * - Bounded queue capacity (rejects rather than silently dropping).
 * - Independent 5-second completion deadline triggering session stall abort.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Req("FR-BLE-050", "TR-BLE-020")
class BleNotificationWriterTest {
    private val sentPackets = mutableListOf<ByteArray>()
    private var sessionStalledCalled = false

    private fun createWriter(
        scope: TestScope,
        maxMsgSize: Int = 1200,
    ): BleNotificationWriter {
        sentPackets.clear()
        sessionStalledCalled = false
        return BleNotificationWriter(
            scope = scope,
            maxMsgSize = maxMsgSize,
            sendGattNotification = { _, packet ->
                sentPackets.add(packet.copyOf())
                true
            },
            onSessionStalled = { _ ->
                sessionStalledCalled = true
            },
        )
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `enqueuing sends first packet immediately and waits for completion before sending second`() =
        runTest {
            val writer = createWriter(this)
            val packet1 = byteArrayOf(0x01, 0x02)
            val packet2 = byteArrayOf(0x03, 0x04)

            assertTrue(writer.enqueue(packet1, isKeepalive = false, sessionGeneration = 1L))
            assertTrue(writer.enqueue(packet2, isKeepalive = false, sessionGeneration = 1L))
            runCurrent()

            // Only packet1 should be in flight
            assertEquals(1, sentPackets.size)
            assertContentEquals(packet1, sentPackets[0])

            // Acknowledge completion of packet1
            writer.onNotificationSent(success = true)
            runCurrent()

            // Now packet2 is sent
            assertEquals(2, sentPackets.size)
            assertContentEquals(packet2, sentPackets[1])
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `terminal notification callback reports GATT acknowledgement`() =
        runTest {
            val writer = createWriter(this)
            val deliveryResults = mutableListOf<Boolean>()

            assertTrue(
                writer.enqueue(
                    byteArrayOf(0x83.toByte(), 0x00, 0x01, 0x00),
                    isKeepalive = false,
                    sessionGeneration = 1L,
                    onDeliveryResult = deliveryResults::add,
                ),
            )
            runCurrent()

            assertTrue(deliveryResults.isEmpty())
            writer.onNotificationSent(success = true)

            assertEquals(listOf(true), deliveryResults)
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `terminal notification callback reports failure when queued response is cleared`() =
        runTest {
            val writer = createWriter(this)
            val deliveryResults = mutableListOf<Boolean>()

            assertTrue(writer.enqueue(byteArrayOf(0x01), isKeepalive = false, sessionGeneration = 1L))
            assertTrue(
                writer.enqueue(
                    byteArrayOf(0x83.toByte(), 0x00, 0x01, 0x00),
                    isKeepalive = false,
                    sessionGeneration = 1L,
                    onDeliveryResult = deliveryResults::add,
                ),
            )
            runCurrent()

            writer.clear()

            assertEquals(listOf(false), deliveryResults)
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-040")
    fun `keepalive notifications are coalesced when already pending`() =
        runTest {
            val writer = createWriter(this)
            val regularPacket = byteArrayOf(0x01)
            val keepalive1 = byteArrayOf(0x82.toByte(), 0x01)
            val keepalive2 = byteArrayOf(0x82.toByte(), 0x02)

            assertTrue(writer.enqueue(regularPacket, isKeepalive = false, sessionGeneration = 1L))
            runCurrent()
            assertEquals(1, sentPackets.size)

            // Queue two keepalives while regularPacket is in flight
            assertTrue(writer.enqueue(keepalive1, isKeepalive = true, sessionGeneration = 1L))
            // Should coalesce and replace keepalive1.
            assertTrue(writer.enqueue(keepalive2, isKeepalive = true, sessionGeneration = 1L))

            // Complete regularPacket
            writer.onNotificationSent(success = true)
            runCurrent()

            // Only one keepalive should be sent (the latest coalesced keepalive2)
            assertEquals(2, sentPackets.size)
            assertContentEquals(keepalive2, sentPackets[1])
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-040")
    fun `keepalives are suppressed once terminal response transmission begins`() =
        runTest {
            val writer = createWriter(this)
            val responseFragment1 = byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01)
            val keepalive = byteArrayOf(0x82.toByte(), 0x01)

            writer.markResponseTransmissionStarted(sessionGeneration = 1L)

            // Enqueueing keepalive during response delivery must be rejected / discarded
            assertFalse(writer.enqueue(keepalive, isKeepalive = true, sessionGeneration = 1L))

            // Enqueueing response fragment must succeed
            assertTrue(writer.enqueue(responseFragment1, isKeepalive = false, sessionGeneration = 1L))
            runCurrent()

            assertEquals(1, sentPackets.size)
            assertContentEquals(responseFragment1, sentPackets[0])
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `queue capacity overflow rejects packet and aborts session`() =
        runTest {
            val maxMsg = 50 // smaller for test
            val writer = createWriter(this, maxMsgSize = maxMsg)
            val capacityLimit = 2 * maxMsg + 1024 // 1124 bytes

            val largePayload = ByteArray(capacityLimit + 10) { 0x01 }
            assertFalse(
                writer.enqueue(largePayload, isKeepalive = false, sessionGeneration = 1L),
                "Excess payload must be rejected",
            )
            assertTrue(sessionStalledCalled, "Overflow must abort rather than leave a dropped response ambiguous")
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `queue capacity includes the notification currently owned by the sender`() =
        runTest {
            val maxMsg = 50
            val writer = createWriter(this, maxMsgSize = maxMsg)
            val capacityLimit = 2 * maxMsg + 1024
            val inFlightPacket = ByteArray(capacityLimit - 100) { 0x01 }
            val excessPacket = ByteArray(101) { 0x02 }

            assertTrue(writer.enqueue(inFlightPacket, isKeepalive = false, sessionGeneration = 1L))
            runCurrent()
            assertEquals(1, sentPackets.size)
            assertFalse(
                writer.enqueue(excessPacket, isKeepalive = false, sessionGeneration = 1L),
                "The in-flight bytes and queued bytes together must stay within capacity",
            )
            assertTrue(sessionStalledCalled)
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `five-second notification timeout triggers session stall abort`() =
        runTest {
            val writer = createWriter(this)
            val packet = byteArrayOf(0x01, 0x02)

            assertTrue(writer.enqueue(packet, isKeepalive = false, sessionGeneration = 1L))
            runCurrent()
            assertEquals(1, sentPackets.size)
            assertFalse(sessionStalledCalled)

            // Advance 4.9 seconds (no stall yet)
            advanceTimeBy(4900)
            runCurrent()
            assertFalse(sessionStalledCalled)

            // Advance past 5.0 seconds
            advanceTimeBy(200)
            runCurrent()
            assertTrue(sessionStalledCalled, "5-second stall must trigger onSessionStalled")
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `clear drains pending queue and cancels in-flight timeout`() =
        runTest {
            val writer = createWriter(this)
            writer.enqueue(byteArrayOf(0x01), isKeepalive = false, sessionGeneration = 1L)
            writer.enqueue(byteArrayOf(0x02), isKeepalive = false, sessionGeneration = 1L)
            runCurrent()

            writer.clear()
            advanceTimeBy(6000)
            runCurrent()

            assertFalse(sessionStalledCalled, "Cleared writer must not trigger stall")
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `completion from cleared send cannot stall a successor session`() =
        runTest {
            val sendMayFinish = kotlinx.coroutines.CompletableDeferred<Boolean>()
            val writer =
                BleNotificationWriter(
                    scope = this,
                    sendGattNotification = { _, packet ->
                        if (packet[0] == 0x01.toByte()) sendMayFinish.await() else true
                    },
                    onSessionStalled = { sessionStalledCalled = true },
                )

            assertTrue(writer.enqueue(byteArrayOf(0x01), isKeepalive = false, sessionGeneration = 1L))
            runCurrent()
            writer.clear()
            assertTrue(writer.enqueue(byteArrayOf(0x02), isKeepalive = false, sessionGeneration = 2L))
            sendMayFinish.complete(false)
            runCurrent()

            assertFalse(sessionStalledCalled, "A delayed send from the old session must not stall its successor")
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `in-flight packet is erased only after notification sender releases its borrowed buffer`() =
        runTest {
            val original = byteArrayOf(0x83.toByte(), 0x01, 0x02)
            val sendStarted = CompletableDeferred<ByteArray>()
            var sendCancelled = false
            val writer =
                BleNotificationWriter(
                    scope = this,
                    sendGattNotification = { _, borrowed ->
                        sendStarted.complete(borrowed)
                        try {
                            kotlinx.coroutines.awaitCancellation()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            sendCancelled = true
                            throw cancelled
                        }
                    },
                    onSessionStalled = { sessionStalledCalled = true },
                )

            assertTrue(writer.enqueue(original, isKeepalive = false, sessionGeneration = 1L))
            runCurrent()
            val borrowed = sendStarted.await()

            writer.clear()
            runCurrent()
            assertTrue(sendCancelled, "Clearing the writer must cancel an in-flight send operation")
            assertContentEquals(ByteArray(original.size), borrowed, "The sender-owned buffer must be erased on release")
            assertFalse(sessionStalledCalled)
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `old session generation cannot enqueue work after reconnect`() =
        runTest {
            var currentGeneration = 2L
            var sends = 0
            val writer =
                BleNotificationWriter(
                    scope = this,
                    sendGattNotification = { _, _ ->
                        sends++
                        true
                    },
                    isSessionCurrent = { it == currentGeneration },
                    onSessionStalled = { sessionStalledCalled = true },
                )

            assertFalse(writer.enqueue(byteArrayOf(0x01), isKeepalive = false, sessionGeneration = 1L))
            assertTrue(writer.enqueue(byteArrayOf(0x02), isKeepalive = false, sessionGeneration = currentGeneration))
            runCurrent()

            assertEquals(1, sends)
            assertFalse(sessionStalledCalled)
        }
}
