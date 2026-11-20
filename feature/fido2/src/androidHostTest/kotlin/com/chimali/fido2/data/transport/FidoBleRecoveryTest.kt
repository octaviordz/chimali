package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@Req("FR-BLE-050", "TR-BLE-020")
class FidoBleRecoveryTest {
    private val ownedBuffers = mutableListOf<ByteArray>()
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

    private fun own(bytes: ByteArray): ByteArray = bytes.also(ownedBuffers::add)

    @Suppress("TooGenericExceptionCaught")
    private fun concatBytes(vararg parts: ByteArray): ByteArray {
        val result = ByteArray(parts.sumOf(ByteArray::size))
        try {
            var offset = 0
            parts.forEach { part ->
                part.copyInto(result, destinationOffset = offset)
                offset += part.size
            }
            return own(result)
        } catch (failure: Throwable) {
            result.fill(0)
            throw failure
        } finally {
            parts.forEach { it.fill(0) }
        }
    }

    @AfterTest
    fun tearDown() {
        if (::sessionOwner.isInitialized) sessionOwner.disconnectHost(HOST_ADDRESS)
        ownedBuffers.forEach { it.fill(0) }
        ownedBuffers.clear()
    }

    @Test
    fun `teardown erases tracked request buffers`() {
        val request = own(byteArrayOf(CMD_MSG, 0x01))

        tearDown()

        assertContentEquals(ByteArray(request.size), request)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `at 2999ms partial request remains valid and continuation completes message`() {
        // Declare 30 bytes message: 1 byte cmd + 2 bytes len (0x00, 0x1E) + 17 bytes data
        val dataPart1 = own(ByteArray(17) { (it + 1).toByte() })
        val initPacket = concatBytes(byteArrayOf(CMD_MSG, 0x00, 30.toByte()), dataPart1.copyOf())

        // Feed initial packet at T0
        val r0 = sessionOwner.processIncomingWrite(initPacket)
        assertIs<BleIncomingWriteResult.InProgress>(
            r0,
            "Initial packet of multi-packet message must not complete immediately",
        )

        // Advance 2999ms (1ms before 3000ms deadline)
        virtualClockNanos += 2_999L * NANOS_PER_MILLI

        // Feed continuation 0 (SEQ 0x00 + remaining 13 bytes data)
        val dataPart2 = own(ByteArray(13) { (it + 18).toByte() })
        val contPacket = concatBytes(byteArrayOf(0x00), dataPart2.copyOf())
        val r1 = sessionOwner.processIncomingWrite(contPacket)

        val complete = (r1 as BleIncomingWriteResult.Complete).message
        own(complete.data)
        assertEquals(CMD_MSG, complete.command)
        assertEquals(30, complete.data.size)
        assertContentEquals(own(dataPart1 + dataPart2), complete.data)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `at 3000ms partial request expires and continuation is discarded`() {
        // Initial packet for 30 bytes
        val initPacket = concatBytes(byteArrayOf(CMD_MSG, 0x00, 30.toByte()), ByteArray(17) { 0x01 })
        val r0 = sessionOwner.processIncomingWrite(initPacket)
        assertIs<BleIncomingWriteResult.InProgress>(r0)

        // Advance exactly 3000ms to the receive deadline
        virtualClockNanos += TIMEOUT_MILLIS * NANOS_PER_MILLI

        // Feed continuation 0
        val contPacket = concatBytes(byteArrayOf(0x00), ByteArray(13) { 0x02 })
        val r1 = sessionOwner.processIncomingWrite(contPacket)
        assertEquals(FidoBleFraming.ERR_REQ_TIMEOUT, (r1 as BleIncomingWriteResult.Error).errorCode)

        // Subsequent valid message must assemble cleanly without residual corrupt state
        val freshPacket = own(byteArrayOf(CMD_MSG, 0x00, 0x02, 0xAA.toByte(), 0xBB.toByte()))
        val freshResult = sessionOwner.processIncomingWrite(freshPacket)
        val freshMessage = (freshResult as BleIncomingWriteResult.Complete).message
        own(freshMessage.data)
        assertEquals(2, freshMessage.data.size)
        own(freshMessage.data)
        assertContentEquals(own(byteArrayOf(0xAA.toByte(), 0xBB.toByte())), freshMessage.data)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `scheduled timeout expires only the matching incomplete assembly`() {
        val first = sessionOwner.processIncomingWrite(concatBytes(byteArrayOf(CMD_MSG, 0x00, 40), ByteArray(17)))
        val firstAssembly = assertIs<BleIncomingWriteResult.InProgress>(first)

        val nextFragment = sessionOwner.processIncomingWrite(concatBytes(byteArrayOf(0x00), ByteArray(11)))
        val refreshedAssembly = assertIs<BleIncomingWriteResult.InProgress>(nextFragment)
        assertNull(sessionOwner.expireIncompleteRequest(firstAssembly.sessionGeneration, firstAssembly.timeoutToken))

        val timeout =
            sessionOwner.expireIncompleteRequest(
                refreshedAssembly.sessionGeneration,
                refreshedAssembly.timeoutToken,
            )
        assertEquals(FidoBleFraming.ERR_REQ_TIMEOUT, timeout?.errorCode)

        val cleanRequest = sessionOwner.processIncomingWrite(own(byteArrayOf(CMD_MSG, 0x00, 0x01, 0x55)))
        val cleanData = (cleanRequest as BleIncomingWriteResult.Complete).message.data
        own(cleanData)
        assertContentEquals(own(byteArrayOf(0x55)), cleanData)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `valid continuation refreshes the 3-second receive deadline`() {
        // Declare 40 bytes: init 17 bytes, cont0 17 bytes, cont1 6 bytes
        val initPacket = concatBytes(byteArrayOf(CMD_MSG, 0x00, 40.toByte()), ByteArray(17) { 0x10 })
        sessionOwner.processIncomingWrite(initPacket)

        // Advance 2000ms
        virtualClockNanos += 2_000L * NANOS_PER_MILLI

        // Feed SEQ 0 at T0+2000ms (refreshes deadline to T0+2000ms + 3000ms)
        val cont0 = concatBytes(byteArrayOf(0x00), ByteArray(17) { 0x20 })
        val r0 = sessionOwner.processIncomingWrite(cont0)
        assertIs<BleIncomingWriteResult.InProgress>(r0)

        // Advance another 2000ms (total 4000ms from start, but only 2000ms since last fragment)
        virtualClockNanos += 2_000L * NANOS_PER_MILLI

        // Feed SEQ 1
        val cont1 = concatBytes(byteArrayOf(0x01), ByteArray(6) { 0x30 })
        val r1 = sessionOwner.processIncomingWrite(cont1)

        val complete = (r1 as BleIncomingWriteResult.Complete).message
        own(complete.data)
        assertEquals(40, complete.data.size)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `out-of-order sequence number discards partial request and preserves clean next transaction`() {
        val initPacket = concatBytes(byteArrayOf(CMD_MSG, 0x00, 30.toByte()), ByteArray(17) { 0x01 })
        sessionOwner.processIncomingWrite(initPacket)

        // Feed SEQ 1 instead of expected SEQ 0
        val outOfOrderCont = concatBytes(byteArrayOf(0x01), ByteArray(13) { 0x02 })
        val r0 = sessionOwner.processIncomingWrite(outOfOrderCont)
        assertEquals(FidoBleFraming.ERR_INVALID_SEQ, (r0 as BleIncomingWriteResult.Error).errorCode)

        // Feed expected SEQ 0 now: must be rejected because assembly state was reset
        val nowLateCont = concatBytes(byteArrayOf(0x00), ByteArray(13) { 0x02 })
        val r1 = sessionOwner.processIncomingWrite(nowLateCont)
        assertEquals(FidoBleFraming.ERR_INVALID_SEQ, (r1 as BleIncomingWriteResult.Error).errorCode)

        // New transaction completes cleanly
        val newInit = own(byteArrayOf(CMD_MSG, 0x00, 0x01, 0x77))
        val r2 = sessionOwner.processIncomingWrite(newInit)
        val newMessage = (r2 as BleIncomingWriteResult.Complete).message
        own(newMessage.data)
        assertEquals(1, newMessage.data.size)
        assertEquals(0x77.toByte(), newMessage.data[0])
    }

    @Test
    @Req("FR-BLE-040", "FR-BLE-050", "TR-BLE-020")
    fun `disconnect and reconnect discards partial data and fences old session`() {
        val initPacket = concatBytes(byteArrayOf(CMD_MSG, 0x00, 30.toByte()), ByteArray(17) { 0x05 })
        sessionOwner.processIncomingWrite(initPacket)

        // Host disconnects while assembly is incomplete
        sessionOwner.disconnectHost(HOST_ADDRESS)

        // Reconnect host
        sessionOwner.connectHost(HOST_ADDRESS)
        sessionOwner.selectRevision(0x20)
        sessionOwner.setNotificationsEnabled(true)

        // Old continuation from previous connection
        val oldCont = concatBytes(byteArrayOf(0x00), ByteArray(13) { 0x06 })
        val r0 = sessionOwner.processIncomingWrite(oldCont)
        assertEquals(FidoBleFraming.ERR_INVALID_SEQ, (r0 as BleIncomingWriteResult.Error).errorCode)

        // Clean new message completes
        val newMsg = own(byteArrayOf(CMD_MSG, 0x00, 0x01, 0x42))
        val r1 = sessionOwner.processIncomingWrite(newMsg)
        val message = (r1 as BleIncomingWriteResult.Complete).message
        own(message.data)
        assertEquals(0x42.toByte(), message.data[0])
    }
}
