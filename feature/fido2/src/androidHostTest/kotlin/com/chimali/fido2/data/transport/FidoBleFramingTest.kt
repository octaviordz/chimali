package com.chimali.fido2.data.transport

import com.chimali.fido2.data.transport.FidoBleFraming.CMD_ERROR
import com.chimali.fido2.data.transport.FidoBleFraming.CMD_KEEPALIVE
import com.chimali.fido2.data.transport.FidoBleFraming.CMD_MSG
import com.chimali.fido2.data.transport.FidoBleFraming.CMD_PING
import com.chimali.fido2.data.transport.FidoBleFraming.CONTROL_POINT_CHARACTERISTIC_UUID
import com.chimali.fido2.data.transport.FidoBleFraming.CONTROL_POINT_LENGTH_CHARACTERISTIC_UUID
import com.chimali.fido2.data.transport.FidoBleFraming.ERR_INVALID_LEN
import com.chimali.fido2.data.transport.FidoBleFraming.ERR_INVALID_SEQ
import com.chimali.fido2.data.transport.FidoBleFraming.ERR_REQ_TIMEOUT
import com.chimali.fido2.data.transport.FidoBleFraming.FIDO_SERVICE_UUID
import com.chimali.fido2.data.transport.FidoBleFraming.REVISION_BITFIELD_CHARACTERISTIC_UUID
import com.chimali.fido2.data.transport.FidoBleFraming.REVISION_FIDO2
import com.chimali.fido2.data.transport.FidoBleFraming.STATUS_CHARACTERISTIC_UUID
import com.chimali.fido2.data.transport.FidoBleFraming.STATUS_PROCESSING
import com.chimali.fido2.data.transport.FidoBleFraming.STATUS_UPNEEDED
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Independent byte-vector tests for FIDO BLE service/revision values, initialization and continuation
 * framing, 20-512 byte Control Point write limits, modulo-128 sequence wrapping, and ATT/BLE/CTAP error separation.
 *
 * @see FidoBleFraming
 * Traceability: TR-BLE-010, TR-BLE-020, FR-BLE-010, FR-BLE-020, FR-BLE-050
 */
class FidoBleFramingTest {
    @Test
    fun `standard UUIDs match FIDO CTAP 2 2 section 11 4 specification`() {
        assertEquals(UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB"), FIDO_SERVICE_UUID)
        assertEquals(UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB"), CONTROL_POINT_CHARACTERISTIC_UUID)
        assertEquals(UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB"), STATUS_CHARACTERISTIC_UUID)
        assertEquals(UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB"), CONTROL_POINT_LENGTH_CHARACTERISTIC_UUID)
        assertEquals(UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB"), REVISION_BITFIELD_CHARACTERISTIC_UUID)
    }

    @Test
    fun `revision bitfield value advertises only FIDO2`() {
        assertEquals(0x20.toByte(), REVISION_FIDO2)
    }

    @Test
    fun `control point length calculation enforces 20 to 512 byte bounds`() {
        // Default ATT MTU = 23 -> MTU - 3 = 20
        assertEquals(20, FidoBleFraming.calculateWriteCapacity(23, maxPlatformCapacity = 512))
        // MTU 64 -> 61
        assertEquals(61, FidoBleFraming.calculateWriteCapacity(64, maxPlatformCapacity = 512))
        // MTU 517 -> 514, clamped to 512
        assertEquals(512, FidoBleFraming.calculateWriteCapacity(517, maxPlatformCapacity = 512))
        // MTU < 23 is invalid
        assertFailsWith<IllegalArgumentException> {
            FidoBleFraming.calculateWriteCapacity(22, maxPlatformCapacity = 512)
        }
    }

    @Test
    fun `notification capacity calculation respects MTU minus 3 and platform capacity`() {
        assertEquals(20, FidoBleFraming.calculateNotifyCapacity(23, maxPlatformCapacity = 512))
        assertEquals(100, FidoBleFraming.calculateNotifyCapacity(103, maxPlatformCapacity = 512))
        assertEquals(244, FidoBleFraming.calculateNotifyCapacity(512, maxPlatformCapacity = 244))
    }

    @Test
    fun `encodeInitializationFrame creates correct header and payload`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val frame =
            FidoBleFraming.encodeInitializationFrame(
                command = CMD_PING,
                totalLength = payload.size,
                data = payload,
                offset = 0,
                chunkSize = payload.size,
            )

        assertEquals(3 + payload.size, frame.size)
        assertEquals(CMD_PING, frame[0])
        assertEquals(0x00.toByte(), frame[1])
        assertEquals(0x04.toByte(), frame[2])
        assertContentEquals(payload, frame.copyOfRange(3, frame.size))
    }

    @Test
    fun `encodeContinuationFrame creates correct sequence header and payload`() {
        val payload = byteArrayOf(0x0A, 0x0B, 0x0C)
        val frame =
            FidoBleFraming.encodeContinuationFrame(
                sequence = 5,
                data = payload,
                offset = 0,
                chunkSize = payload.size,
            )

        assertEquals(1 + payload.size, frame.size)
        assertEquals(0x05.toByte(), frame[0])
        assertContentEquals(payload, frame.copyOfRange(1, frame.size))
    }

    @Test
    fun `sequence wraps modulo 128 from 0x7F to 0x00`() {
        assertEquals(0, FidoBleFraming.nextSequence(0x7F))
        assertEquals(1, FidoBleFraming.nextSequence(0))
        assertEquals(127, FidoBleFraming.nextSequence(126))
    }

    @Test
    fun `fragmentResponse correctly splits large payload into init and continuation frames`() {
        val largePayload = ByteArray(100) { it.toByte() }
        val notifyCapacity = 30 // 3-byte init header -> 27 data bytes; 1-byte cont header -> 29 data bytes

        val frames =
            FidoBleFraming.fragmentResponse(
                command = CMD_MSG,
                payload = largePayload,
                notifyCapacity = notifyCapacity,
            )

        assertTrue(frames.size > 1)
        // Init frame
        assertEquals(CMD_MSG, frames[0][0])
        assertEquals(0, frames[0][1].toInt() and 0xFF)
        assertEquals(100, frames[0][2].toInt() and 0xFF)
        assertEquals(30, frames[0].size) // 3 + 27 = 30

        // Continuation frame 0
        assertEquals(0x00.toByte(), frames[1][0])
        assertEquals(30, frames[1].size) // 1 + 29 = 30

        // Reassemble fragments to verify lossless roundtrip
        val reassembled = ByteArray(100)
        var offset = 0
        System.arraycopy(frames[0], 3, reassembled, offset, frames[0].size - 3)
        offset += frames[0].size - 3

        for (i in 1 until frames.size) {
            val contDataLen = frames[i].size - 1
            System.arraycopy(frames[i], 1, reassembled, offset, contDataLen)
            offset += contDataLen
        }

        assertEquals(100, offset)
        assertContentEquals(largePayload, reassembled)
    }

    @Test
    fun `request reassembly accepts valid multi-packet request and rejects out of order sequences`() {
        val maxMsgSize = 1024
        val assembler = FidoBleFraming.RequestAssembler(maxMsgSize = maxMsgSize)

        // Init frame declaring 10 bytes, carrying 4 bytes
        val initFrame = byteArrayOf(CMD_MSG, 0x00, 0x0A, 1, 2, 3, 4)
        val initResult = assembler.feed(initFrame)
        assertTrue(initResult is FidoBleFraming.FeedResult.InProgress)

        // Continuation frame sequence 1 (skipping sequence 0) -> should fail with ERR_INVALID_SEQ
        val badContFrame = byteArrayOf(0x01, 5, 6, 7)
        val errResult = assembler.feed(badContFrame)
        assertTrue(errResult is FidoBleFraming.FeedResult.Error)
        assertEquals(ERR_INVALID_SEQ, errResult.errorCode)
    }

    @Test
    fun `request reassembly rejects declared length exceeding maxMsgSize before allocation`() {
        val assembler = FidoBleFraming.RequestAssembler(maxMsgSize = 512)
        // Declaring 1000 bytes > 512
        val oversizeInit = byteArrayOf(CMD_MSG, 0x03, 0xE8.toByte(), 1, 2, 3)
        val result = assembler.feed(oversizeInit)
        assertTrue(result is FidoBleFraming.FeedResult.Error)
        assertEquals(ERR_INVALID_LEN, result.errorCode)
    }

    @Test
    fun `encodeErrorFrame produces exactly 3 bytes with command 0xBF`() {
        val errorFrame = FidoBleFraming.encodeErrorFrame(ERR_REQ_TIMEOUT)
        assertEquals(3, errorFrame.size)
        assertEquals(CMD_ERROR, errorFrame[0])
        assertEquals(0x00.toByte(), errorFrame[1])
        assertEquals(ERR_REQ_TIMEOUT, errorFrame[2])
    }

    @Test
    fun `encodeKeepaliveFrame produces valid 4-byte frame`() {
        val processingFrame = FidoBleFraming.encodeKeepaliveFrame(STATUS_PROCESSING)
        assertEquals(4, processingFrame.size)
        assertEquals(CMD_KEEPALIVE, processingFrame[0])
        assertEquals(0x00.toByte(), processingFrame[1])
        assertEquals(0x01.toByte(), processingFrame[2])
        assertEquals(STATUS_PROCESSING, processingFrame[3])

        val upNeededFrame = FidoBleFraming.encodeKeepaliveFrame(STATUS_UPNEEDED)
        assertEquals(STATUS_UPNEEDED, upNeededFrame[3])
    }
}
