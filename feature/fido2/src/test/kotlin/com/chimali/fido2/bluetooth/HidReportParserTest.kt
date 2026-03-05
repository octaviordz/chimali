package com.chimali.fido2.bluetooth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HidReportParser] — CTAP2-over-HID packet reassembly (T055).
 *
 * Covers:
 * - Single-packet message (payload ≤ 57 bytes)
 * - Multi-packet message reassembly (init + continuations)
 * - [encodeResponse] packet encoding
 * - CTAPHID_INIT response generation
 * - Error response generation
 * - Sequence mismatch error handling
 * - Short-report rejection
 */
class HidReportParserTest {

    private lateinit var parser: HidReportParser

    // Fixed test CID
    private val testCid = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    private val testCidKey = "01020304"

    @BeforeEach
    fun setUp() {
        parser = HidReportParser()
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    /** Builds a 64-byte init packet. */
    private fun initPacket(
        cid: ByteArray,
        cmd: Int,
        payload: ByteArray
    ): ByteArray {
        val packet = ByteArray(62)
        cid.copyInto(packet, 0)
        packet[4] = (cmd or 0x80).toByte()          // CMD with init flag
        packet[5] = ((payload.size shr 8) and 0xFF).toByte()
        packet[6] = (payload.size and 0xFF).toByte()
        payload.copyInto(packet, 7, 0, minOf(payload.size, 55))
        return packet
    }

    /** Builds a 64-byte continuation packet. */
    private fun contPacket(
        cid: ByteArray,
        seq: Int,
        payload: ByteArray,
        offset: Int
    ): ByteArray {
        val packet = ByteArray(62)
        cid.copyInto(packet, 0)
        packet[4] = (seq and 0x7F).toByte()
        payload.copyInto(packet, 5, offset, minOf(offset + 57, payload.size))
        return packet
    }

    // ── Single-packet message ─────────────────────────────────────────────────

    @Test
    fun `single packet message assembles immediately`() {
        val payload = ByteArray(20) { it.toByte() }
        val packet = initPacket(testCid, CTAPHID_CBOR, payload)

        val result = parser.processReport(packet)

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()
        assertNotNull(msg)
        assertEquals(CTAPHID_CBOR, msg!!.command)
        assertArrayEquals(testCid, msg.channelId)
        assertArrayEquals(payload, msg.payload)
    }

    @Test
    fun `single packet CTAPHID_PING assembles correctly`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val packet = initPacket(testCid, CTAPHID_PING, payload)

        val result = parser.processReport(packet)

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()
        assertNotNull(msg)
        assertEquals(CTAPHID_PING, msg!!.command)
    }

    @Test
    fun `zero length payload is accepted`() {
        val packet = initPacket(testCid, CTAPHID_PING, ByteArray(0))

        val result = parser.processReport(packet)

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()
        assertNotNull(msg)
        assertEquals(0, msg!!.payload.size)
    }

    // ── Multi-packet message ──────────────────────────────────────────────────

    @Test
    fun `multi packet message reassembles correctly`() {
        // 55 + 10 = 65 bytes total, needs 2 packets
        val fullPayload = ByteArray(65) { it.toByte() }

        val init = initPacket(testCid, CTAPHID_CBOR, fullPayload)
        val cont0 = contPacket(testCid, 0, fullPayload, 55)

        val r1 = parser.processReport(init)
        assertTrue(r1.isSuccess)
        assertNull(r1.getOrNull()) // still waiting for continuation

        val r2 = parser.processReport(cont0)
        assertTrue(r2.isSuccess)
        val msg = r2.getOrNull()
        assertNotNull(msg)
        assertArrayEquals(fullPayload, msg!!.payload)
    }

    @Test
    fun `two continuation packets reassemble correctly`() {
        // 55 + 57 + 1 = 113 bytes
        val fullPayload = ByteArray(113) { (it and 0xFF).toByte() }

        val r1 = parser.processReport(initPacket(testCid, CTAPHID_CBOR, fullPayload))
        assertNull(r1.getOrNull())

        val r2 = parser.processReport(contPacket(testCid, 0, fullPayload, 55))
        assertNull(r2.getOrNull())

        val r3 = parser.processReport(contPacket(testCid, 1, fullPayload, 112))
        val msg = r3.getOrNull()
        assertNotNull(msg)
        assertArrayEquals(fullPayload, msg!!.payload)
    }

    // ── Sequence mismatch ─────────────────────────────────────────────────────

    @Test
    fun `out of order continuation returns failure`() {
        val fullPayload = ByteArray(65) { it.toByte() }

        parser.processReport(initPacket(testCid, CTAPHID_CBOR, fullPayload))
        // Send seq=1 instead of seq=0
        val contWrong = contPacket(testCid, 1, fullPayload, 55)
        val result = parser.processReport(contWrong)

        assertTrue(result.isFailure)
        assertInstanceOf(Exception::class.java, result.exceptionOrNull())
    }

    @Test
    fun `orphaned continuation returns failure`() {
        val packet = ByteArray(62)
        testCid.copyInto(packet, 0)
        packet[4] = 0x00 // continuation, seq=0, but no pending state

        val result = parser.processReport(packet)
        assertTrue(result.isFailure)
    }

    // ── Short report ──────────────────────────────────────────────────────────

    @Test
    fun `short report returns failure`() {
        val shortReport = ByteArray(10)
        val result = parser.processReport(shortReport)
        assertTrue(result.isFailure)
    }

    // ── encodeResponse round trip ─────────────────────────────────────────────

    @Test
    fun `encode and re-parse single packet round trip`() {
        val data = ByteArray(10) { it.toByte() }
        val msg = CtapHidMessage(testCid, CTAPHID_CBOR, data)
        val packets = parser.encodeResponse(msg)

        assertEquals(1, packets.size, "Single packet expected for small payload")
        assertEquals(62, packets[0].size)

        // Verify CID in first packet
        assertArrayEquals(testCid, packets[0].copyOfRange(0, 4))
        // Verify CMD with init flag
        assertEquals((CTAPHID_CBOR or 0x80).toByte(), packets[0][4])
        // Verify length
        assertEquals(0, packets[0][5].toInt())         // BCNTH
        assertEquals(data.size, packets[0][6].toInt()) // BCNTL
    }

    @Test
    fun `encode large payload produces multiple packets`() {
        val data = ByteArray(130) { it.toByte() }  // needs 3 packets
        val msg = CtapHidMessage(testCid, CTAPHID_CBOR, data)
        val packets = parser.encodeResponse(msg)

        assertEquals(3, packets.size) // init + 2 cont
        packets.forEach { assertEquals(62, it.size) }

        // Verify sequence numbers on continuations
        assertEquals(0x00.toByte(), packets[1][4]) // seq=0
        assertEquals(0x01.toByte(), packets[2][4]) // seq=1
    }

    // ── CTAPHID_INIT response ─────────────────────────────────────────────────

    @Test
    fun `buildInitResponse produces correctly structured response`() {
        val nonce = ByteArray(8) { (it + 1).toByte() }
        val newCid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())

        val response = parser.buildInitResponse(nonce, newCid)

        assertEquals(CTAPHID_INIT, response.command)
        assertEquals(17, response.payload.size)
        // First 8 bytes = nonce echo
        assertArrayEquals(nonce, response.payload.copyOfRange(0, 8))
        // Next 4 bytes = assigned CID
        assertArrayEquals(newCid, response.payload.copyOfRange(8, 12))
        // Byte 12 = CTAPHID protocol version = 0x02
        assertEquals(0x02.toByte(), response.payload[12])
    }

    @Test
    fun `buildInitResponse rejects wrong nonce size`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.buildInitResponse(ByteArray(4), byteArrayOf(1, 2, 3, 4))
        }
    }

    // ── Error/keepalive responses ─────────────────────────────────────────────

    @Test
    fun `buildErrorResponse has correct structure`() {
        val errorMsg = parser.buildErrorResponse(testCid, 0x01)
        assertEquals(CTAPHID_ERROR, errorMsg.command)
        assertEquals(1, errorMsg.payload.size)
        assertEquals(0x01.toByte(), errorMsg.payload[0])
    }

    @Test
    fun `buildKeepAliveResponse has correct structure`() {
        val kaMsg = parser.buildKeepAliveResponse(testCid, 0x01)
        assertEquals(CTAPHID_KEEPALIVE, kaMsg.command)
        assertEquals(1, kaMsg.payload.size)
        assertEquals(0x01.toByte(), kaMsg.payload[0])
    }

    // ── reset() ───────────────────────────────────────────────────────────────

    @Test
    fun `reset clears pending state so continuation becomes orphan`() {
        val fullPayload = ByteArray(65) { it.toByte() }
        parser.processReport(initPacket(testCid, CTAPHID_CBOR, fullPayload))
        parser.reset()

        // Continuation after reset should fail
        val cont = contPacket(testCid, 0, fullPayload, 55)
        val result = parser.processReport(cont)
        assertTrue(result.isFailure)
    }
}
