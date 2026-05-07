package com.chimali.fido2.bluetooth

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [HidReportParser] — CTAP2-over-HID packet reassembly (T055).
 *
 * Covers:
 * - Single-packet message (payload ≤ 57 bytes)
 * - Multi-packet message reassembly (init + continuations)
 * - [HidReportParser.encodeResponse] packet encoding
 * - CTAPHID_INIT response generation
 * - Error response generation
 * - Sequence mismatch error handling
 * - Short-report rejection
 */
class HidReportParserTest {
    private lateinit var parser: HidReportParser

    // Fixed test CID
    private val testCid = byteArrayOf(CID_0, CID_1, CID_2, CID_3)

    private companion object {
        private const val TEST_PAYLOAD_SIZE_20 = 20
        private const val OVERFLOW_SIZE_10 = 10
        private const val SEQ_0 = 0
        private const val SEQ_1 = 1
        private const val PACKET_INDEX_2 = 2
        private const val SHORT_REPORT_SIZE_10 = 10
        private const val ROUND_TRIP_DATA_SIZE_10 = 10
        private const val MULTI_PACKET_COUNT_3 = 3
        private const val EXTRA_CHUNKS_18 = 18
        private const val NONCE_SIZE_8 = 8
        private const val INIT_RESPONSE_PAYLOAD_SIZE = 17
        private const val OFFSET_8 = 8
        private const val OFFSET_12 = 12
        private const val PROTOCOL_VERSION_2 = 0x02.toByte()
        private const val INVALID_NONCE_SIZE_4 = 4
        private const val ERR_CODE_01 = 0x01.toByte()
        private const val KA_STATUS_01 = 0x01.toByte()

        private const val CID_0 = 0x01.toByte()
        private const val CID_1 = 0x02.toByte()
        private const val CID_2 = 0x03.toByte()
        private const val CID_3 = 0x04.toByte()
        private const val BIT_MASK_80 = 0x80
        private const val BYTE_MASK_FF = 0xFF
        private const val BIT_8 = 8
        private const val SEQ_MASK_7F = 0x7F

        private const val CID_INIT_A = 0xAA.toByte()
        private const val CID_INIT_B = 0xBB.toByte()
        private const val CID_INIT_C = 0xCC.toByte()
        private const val CID_INIT_D = 0xDD.toByte()
        private const val CID_DUMMY_1 = 1.toByte()
        private const val CID_DUMMY_2 = 2.toByte()
        private const val CID_DUMMY_3 = 3.toByte()
        private const val CID_DUMMY_4 = 4.toByte()
        private const val OFFSET_1 = 1
        private const val SIZE_1 = 1
        private const val BCNTH_ZERO = 0
    }

    @BeforeTest
    fun setUp() {
        parser = HidReportParser()
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    /** Builds a 64-byte init packet. */
    private fun initPacket(
        cid: ByteArray,
        cmd: Int,
        payload: ByteArray,
    ): ByteArray {
        val packet = ByteArray(HID_PACKET_SIZE)
        cid.copyInto(packet, 0)
        packet[INIT_CMD_OFFSET] = (cmd or BIT_MASK_80).toByte() // CMD with init flag
        packet[INIT_BCNTH_OFFSET] = ((payload.size shr BIT_8) and BYTE_MASK_FF).toByte()
        packet[INIT_BCNTL_OFFSET] = (payload.size and BYTE_MASK_FF).toByte()
        payload.copyInto(packet, INIT_DATA_OFFSET, 0, minOf(payload.size, INIT_DATA_SIZE))
        return packet
    }

    /** Builds a 64-byte continuation packet. */
    private fun contPacket(
        cid: ByteArray,
        seq: Int,
        payload: ByteArray,
        offset: Int,
    ): ByteArray {
        val packet = ByteArray(HID_PACKET_SIZE)
        cid.copyInto(packet, 0)
        packet[CONT_SEQ_OFFSET] = (seq and SEQ_MASK_7F).toByte()
        payload.copyInto(packet, CONT_DATA_OFFSET, offset, minOf(offset + CONT_DATA_SIZE, payload.size))
        return packet
    }

    // ── Single-packet message ─────────────────────────────────────────────────

    @Test
    fun `single packet message assembles immediately`() {
        val payload = ByteArray(TEST_PAYLOAD_SIZE_20) { it.toByte() }
        val packet = initPacket(testCid, CTAPHID_CBOR, payload)

        val result = parser.processReport(packet)

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()
        assertNotNull(msg)
        assertEquals(CTAPHID_CBOR, msg.command)
        assertContentEquals(testCid, msg.channelId)
        assertContentEquals(payload, msg.payload)
    }

    @Test
    fun `single packet CTAPHID_PING assembles correctly`() {
        val payload = byteArrayOf(CID_DUMMY_1, CID_DUMMY_2, CID_DUMMY_3)
        val packet = initPacket(testCid, CTAPHID_PING, payload)

        val result = parser.processReport(packet)

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()
        assertNotNull(msg)
        assertEquals(CTAPHID_PING, msg.command)
    }

    @Test
    fun `zero length payload is accepted`() {
        val packet = initPacket(testCid, CTAPHID_PING, ByteArray(0))

        val result = parser.processReport(packet)

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()
        assertNotNull(msg)
        assertEquals(0, msg.payload.size)
    }

    // ── Multi-packet message ──────────────────────────────────────────────────

    @Test
    fun `multi packet message reassembles correctly`() {
        // Init packet data size + some overflow
        val fullPayload = ByteArray(INIT_DATA_SIZE + OVERFLOW_SIZE_10) { it.toByte() }

        val init = initPacket(testCid, CTAPHID_CBOR, fullPayload)
        val cont0 = contPacket(testCid, SEQ_0, fullPayload, INIT_DATA_SIZE)

        val r1 = parser.processReport(init)
        assertTrue(r1.isSuccess)
        assertNull(r1.getOrNull()) // still waiting for continuation

        val r2 = parser.processReport(cont0)
        assertTrue(r2.isSuccess)
        val msg = r2.getOrNull()
        assertNotNull(msg)
        assertContentEquals(fullPayload, msg.payload)
    }

    @Test
    fun `two continuation packets reassemble correctly`() {
        // Init packet data size + full continuation + 1 byte
        val fullPayload = ByteArray(INIT_DATA_SIZE + CONT_DATA_SIZE + SIZE_1) { (it and BYTE_MASK_FF).toByte() }

        val r1 = parser.processReport(initPacket(testCid, CTAPHID_CBOR, fullPayload))
        assertNull(r1.getOrNull())

        val r2 = parser.processReport(contPacket(testCid, SEQ_0, fullPayload, INIT_DATA_SIZE))
        assertNull(r2.getOrNull())

        val r3 = parser.processReport(contPacket(testCid, SEQ_1, fullPayload, INIT_DATA_SIZE + CONT_DATA_SIZE))
        val msg = r3.getOrNull()
        assertNotNull(msg)
        assertContentEquals(fullPayload, msg.payload)
    }

    // ── Sequence mismatch ─────────────────────────────────────────────────────

    @Test
    fun `out of order continuation returns failure`() {
        val fullPayload = ByteArray(INIT_DATA_SIZE + OVERFLOW_SIZE_10) { it.toByte() }

        parser.processReport(initPacket(testCid, CTAPHID_CBOR, fullPayload))
        // Send seq=1 instead of seq=0
        val contWrong = contPacket(testCid, SEQ_1, fullPayload, INIT_DATA_SIZE)
        val result = parser.processReport(contWrong)

        assertTrue(result.isFailure)
        assertIs<Exception>(result.exceptionOrNull())
    }

    @Test
    fun `orphaned continuation returns failure`() {
        val packet = ByteArray(HID_PACKET_SIZE)
        testCid.copyInto(packet, 0)
        packet[CONT_SEQ_OFFSET] = SEQ_0.toByte() // continuation, seq=0, but no pending state

        val result = parser.processReport(packet)
        assertTrue(result.isFailure)
    }

    // ── Short report ──────────────────────────────────────────────────────────

    @Test
    fun `short report returns failure`() {
        val shortReport = ByteArray(SHORT_REPORT_SIZE_10)
        val result = parser.processReport(shortReport)
        assertTrue(result.isFailure)
    }

    // ── encodeResponse round trip ─────────────────────────────────────────────

    @Test
    fun `encode and re-parse single packet round trip`() {
        val data = ByteArray(ROUND_TRIP_DATA_SIZE_10) { it.toByte() }
        val msg = CtapHidMessage(testCid, CTAPHID_CBOR, data)
        val packets = parser.encodeResponse(msg)

        assertEquals(1, packets.size, "Single packet expected for small payload")
        assertEquals(HID_PACKET_SIZE, packets[0].size)

        // Verify CID in first packet
        assertContentEquals(testCid, packets[0].copyOfRange(0, CID_SIZE))
        // Verify CMD with init flag
        assertEquals((CTAPHID_CBOR or BIT_MASK_80).toByte(), packets[0][INIT_CMD_OFFSET])
        // Verify length
        assertEquals(BCNTH_ZERO, packets[0][INIT_BCNTH_OFFSET].toInt()) // BCNTH
        assertEquals(data.size, packets[0][INIT_BCNTL_OFFSET].toInt()) // BCNTL
    }

    @Test
    fun `encode large payload produces multiple packets`() {
        val data = ByteArray(INIT_DATA_SIZE + CONT_DATA_SIZE + EXTRA_CHUNKS_18) { it.toByte() } // needs 3 packets
        val msg = CtapHidMessage(testCid, CTAPHID_CBOR, data)
        val packets = parser.encodeResponse(msg)

        assertEquals(MULTI_PACKET_COUNT_3, packets.size) // init + 2 cont
        packets.forEach { assertEquals(HID_PACKET_SIZE, it.size) }

        // Verify sequence numbers on continuations
        assertEquals(SEQ_0.toByte(), packets[1][CONT_SEQ_OFFSET]) // seq=0
        assertEquals(SEQ_1.toByte(), packets[PACKET_INDEX_2][CONT_SEQ_OFFSET]) // seq=1
    }

    // ── CTAPHID_INIT response ─────────────────────────────────────────────────

    @Test
    fun `buildInitResponse produces correctly structured response`() {
        val nonce = ByteArray(NONCE_SIZE_8) { (it + OFFSET_1).toByte() }
        val newCid = byteArrayOf(CID_INIT_A, CID_INIT_B, CID_INIT_C, CID_INIT_D)

        val response = parser.buildInitResponse(nonce, newCid)

        assertEquals(CTAPHID_INIT, response.command)
        assertEquals(INIT_RESPONSE_PAYLOAD_SIZE, response.payload.size)
        // First 8 bytes = nonce echo
        assertContentEquals(nonce, response.payload.copyOfRange(0, OFFSET_8))
        // Next 4 bytes = assigned CID
        assertContentEquals(newCid, response.payload.copyOfRange(OFFSET_8, OFFSET_12))
        // Byte 12 = CTAPHID protocol version = 0x02
        assertEquals(PROTOCOL_VERSION_2, response.payload[OFFSET_12])
    }

    @Test
    fun `buildInitResponse rejects wrong nonce size`() {
        assertFailsWith<IllegalArgumentException> {
            parser.buildInitResponse(
                ByteArray(INVALID_NONCE_SIZE_4),
                byteArrayOf(CID_DUMMY_1, CID_DUMMY_2, CID_DUMMY_3, CID_DUMMY_4),
            )
        }
    }

    // ── Error/keepalive responses ─────────────────────────────────────────────

    @Test
    fun `buildErrorResponse has correct structure`() {
        val errorMsg = parser.buildErrorResponse(testCid, ERR_CODE_01)
        assertEquals(CTAPHID_ERROR, errorMsg.command)
        assertEquals(SIZE_1, errorMsg.payload.size)
        assertEquals(ERR_CODE_01, errorMsg.payload[0])
    }

    @Test
    fun `buildKeepAliveResponse has correct structure`() {
        val kaMsg = parser.buildKeepAliveResponse(testCid, KA_STATUS_01)
        assertEquals(CTAPHID_KEEPALIVE, kaMsg.command)
        assertEquals(SIZE_1, kaMsg.payload.size)
        assertEquals(KA_STATUS_01, kaMsg.payload[0])
    }

    // ── reset() ───────────────────────────────────────────────────────────────

    @Test
    fun `reset clears pending state so continuation becomes orphan`() {
        val fullPayload = ByteArray(INIT_DATA_SIZE + OVERFLOW_SIZE_10) { it.toByte() }
        parser.processReport(initPacket(testCid, CTAPHID_CBOR, fullPayload))
        parser.reset()

        // Continuation after reset should fail
        val cont = contPacket(testCid, SEQ_0, fullPayload, INIT_DATA_SIZE)
        val result = parser.processReport(cont)
        assertTrue(result.isFailure)
    }
}
