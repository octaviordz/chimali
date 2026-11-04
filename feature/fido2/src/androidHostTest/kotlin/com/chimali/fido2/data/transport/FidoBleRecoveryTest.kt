package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Req("FR-BLE-050", "TR-BLE-020")
class FidoBleRecoveryTest {
    private var virtualClockNanos: Long = 1_000_000_000L // 1.0s base
    private lateinit var sessionOwner: BleSessionOwner

    companion object {
        private const val HOST_ADDRESS = "11:22:33:44:55:66"
        private const val CMD_MSG: Byte = 0x83.toByte()
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val TIMEOUT_MILLIS = 3_000L
    }

    @BeforeTest
    fun setUp() {
        virtualClockNanos = 1_000_000_000L
        sessionOwner =
            BleSessionOwner(
                monotonicNanos = { virtualClockNanos },
            )
        sessionOwner.connectHost(HOST_ADDRESS)
        sessionOwner.selectRevision(0x20)
        sessionOwner.setNotificationsEnabled(true)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `at 2999ms partial request remains valid and continuation completes message`() {
        // Declare 30 bytes message: 1 byte cmd + 2 bytes len (0x00, 0x1E) + 17 bytes data
        val dataPart1 = ByteArray(17) { (it + 1).toByte() }
        val initPacket = byteArrayOf(CMD_MSG, 0x00, 30.toByte()) + dataPart1

        // Feed initial packet at T0
        val r0 = sessionOwner.processIncomingWrite(initPacket)
        assertNull(r0, "Initial packet of multi-packet message must not complete immediately")

        // Advance 2999ms (1ms before 3000ms deadline)
        virtualClockNanos += 2_999L * NANOS_PER_MILLI

        // Feed continuation 0 (SEQ 0x00 + remaining 13 bytes data)
        val dataPart2 = ByteArray(13) { (it + 18).toByte() }
        val contPacket = byteArrayOf(0x00) + dataPart2
        val r1 = sessionOwner.processIncomingWrite(contPacket)

        assertNotNull(r1, "Continuation received at 2999ms must complete assembly successfully")
        assertEquals(CMD_MSG, r1.command)
        assertEquals(30, r1.data.size)
        assertContentEquals(dataPart1 + dataPart2, r1.data)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `at 3000ms plus 1ms partial request expires and continuation is discarded`() {
        // Initial packet for 30 bytes
        val initPacket = byteArrayOf(CMD_MSG, 0x00, 30.toByte()) + ByteArray(17) { 0x01 }
        val r0 = sessionOwner.processIncomingWrite(initPacket)
        assertNull(r0)

        // Advance 3001ms past deadline
        virtualClockNanos += (TIMEOUT_MILLIS + 1L) * NANOS_PER_MILLI

        // Feed continuation 0
        val contPacket = byteArrayOf(0x00) + ByteArray(13) { 0x02 }
        val r1 = sessionOwner.processIncomingWrite(contPacket)
        assertNull(r1, "Continuation received after 3s must be discarded due to timeout")

        // Subsequent valid message must assemble cleanly without residual corrupt state
        val freshPacket = byteArrayOf(CMD_MSG, 0x00, 0x02, 0xAA.toByte(), 0xBB.toByte())
        val freshResult = sessionOwner.processIncomingWrite(freshPacket)
        assertNotNull(freshResult, "Fresh packet after timeout must succeed cleanly")
        assertEquals(2, freshResult.data.size)
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), freshResult.data)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `valid continuation refreshes the 3-second receive deadline`() {
        // Declare 40 bytes: init 17 bytes, cont0 17 bytes, cont1 6 bytes
        val initPacket = byteArrayOf(CMD_MSG, 0x00, 40.toByte()) + ByteArray(17) { 0x10 }
        sessionOwner.processIncomingWrite(initPacket)

        // Advance 2000ms
        virtualClockNanos += 2_000L * NANOS_PER_MILLI

        // Feed SEQ 0 at T0+2000ms (refreshes deadline to T0+2000ms + 3000ms)
        val cont0 = byteArrayOf(0x00) + ByteArray(17) { 0x20 }
        val r0 = sessionOwner.processIncomingWrite(cont0)
        assertNull(r0)

        // Advance another 2000ms (total 4000ms from start, but only 2000ms since last fragment)
        virtualClockNanos += 2_000L * NANOS_PER_MILLI

        // Feed SEQ 1
        val cont1 = byteArrayOf(0x01) + ByteArray(6) { 0x30 }
        val r1 = sessionOwner.processIncomingWrite(cont1)

        assertNotNull(r1, "Refreshed deadline must allow message completion across >3s total duration")
        assertEquals(40, r1.data.size)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `out-of-order sequence number discards partial request and preserves clean next transaction`() {
        val initPacket = byteArrayOf(CMD_MSG, 0x00, 30.toByte()) + ByteArray(17) { 0x01 }
        sessionOwner.processIncomingWrite(initPacket)

        // Feed SEQ 1 instead of expected SEQ 0
        val outOfOrderCont = byteArrayOf(0x01) + ByteArray(13) { 0x02 }
        val r0 = sessionOwner.processIncomingWrite(outOfOrderCont)
        assertNull(r0, "Out-of-order continuation must be rejected")

        // Feed expected SEQ 0 now: must be rejected because assembly state was reset
        val nowLateCont = byteArrayOf(0x00) + ByteArray(13) { 0x02 }
        val r1 = sessionOwner.processIncomingWrite(nowLateCont)
        assertNull(r1, "Subsequent continuation must be rejected after reset")

        // New transaction completes cleanly
        val newInit = byteArrayOf(CMD_MSG, 0x00, 0x01, 0x77)
        val r2 = sessionOwner.processIncomingWrite(newInit)
        assertNotNull(r2)
        assertEquals(1, r2.data.size)
        assertEquals(0x77.toByte(), r2.data[0])
    }

    @Test
    @Req("FR-BLE-040", "FR-BLE-050", "TR-BLE-020")
    fun `disconnect and reconnect discards partial data and fences old session`() {
        val initPacket = byteArrayOf(CMD_MSG, 0x00, 30.toByte()) + ByteArray(17) { 0x05 }
        sessionOwner.processIncomingWrite(initPacket)

        // Host disconnects while assembly is incomplete
        sessionOwner.disconnectHost(HOST_ADDRESS)

        // Reconnect host
        sessionOwner.connectHost(HOST_ADDRESS)
        sessionOwner.selectRevision(0x20)
        sessionOwner.setNotificationsEnabled(true)

        // Old continuation from previous connection
        val oldCont = byteArrayOf(0x00) + ByteArray(13) { 0x06 }
        val r0 = sessionOwner.processIncomingWrite(oldCont)
        assertNull(r0, "Old continuation after reconnect must be rejected without active assembly")

        // Clean new message completes
        val newMsg = byteArrayOf(CMD_MSG, 0x00, 0x01, 0x42)
        val r1 = sessionOwner.processIncomingWrite(newMsg)
        assertNotNull(r1)
        assertEquals(0x42.toByte(), r1.data[0])
    }
}
