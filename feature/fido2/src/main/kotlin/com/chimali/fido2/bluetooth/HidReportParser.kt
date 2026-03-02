package com.chimali.fido2.bluetooth

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HidReportParser"

// ── CTAP2-over-HID packet structure (FIDO CTAP HID spec §8) ──────────────────
//
// Init packet (first in sequence):
//   [CID 4B] [CMD 1B (bit7=1)] [BCNTH 1B] [BCNTL 1B] [DATA up to 57B]
//
// Continuation packet:
//   [CID 4B] [SEQ 1B (bit7=0, 0x00-0x7F)] [DATA up to 59B]
//
// Total packet size = 64 bytes (FIDO_HID_REPORT_SIZE)

private const val HID_PACKET_SIZE = 64
private const val CID_SIZE = 4
private const val INIT_CMD_OFFSET = 4
private const val INIT_BCNTH_OFFSET = 5
private const val INIT_BCNTL_OFFSET = 6
private const val INIT_DATA_OFFSET = 7
private const val INIT_DATA_SIZE = HID_PACKET_SIZE - INIT_DATA_OFFSET       // 57
private const val CONT_SEQ_OFFSET = 4
private const val CONT_DATA_OFFSET = 5
private const val CONT_DATA_SIZE = HID_PACKET_SIZE - CONT_DATA_OFFSET        // 59

private const val CMD_FLAG = 0x80  // bit7 set → init packet
private const val CMD_MASK = 0x7F

// Well-known CTAPHID commands
internal const val CTAPHID_MSG       = 0x03
internal const val CTAPHID_CBOR      = 0x10
internal const val CTAPHID_INIT      = 0x06
internal const val CTAPHID_PING      = 0x01
internal const val CTAPHID_CANCEL    = 0x11
internal const val CTAPHID_ERROR     = 0x3F
internal const val CTAPHID_KEEPALIVE = 0x3B

// CTAPHID_INIT nonce / response sizes
private const val INIT_NONCE_SIZE      = 8
private const val INIT_RESPONSE_SIZE   = 17  // nonce(8) + CID_assigned(4) + protocolVersion(1) + majorDV(1) + minorDV(1) + buildDV(1) + capabilities(1)

private const val CAPABILITY_CBOR  = 0x04
private const val CAPABILITY_NMSG  = 0x08

/** CID assigned to the broadcast channel (used for CTAPHID_INIT). */
val BROADCAST_CID: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

/**
 * Represents a fully assembled CTAP2 HID command — after multi-packet reassembly.
 */
data class CtapHidMessage(
    val channelId: ByteArray,
    val command: Int,       // masked CMD (CMD_MASK applied)
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CtapHidMessage) return false
        return channelId.contentEquals(other.channelId) &&
               command == other.command &&
               payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = channelId.contentHashCode()
        result = 31 * result + command
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Parses 64-byte raw HID reports and reassembles multi-packet CTAP2 HID messages.
 *
 * Per FIDO CTAP HID spec §8: each 64-byte report is either an *init* packet
 * (MSB of byte[4] == 1) or a *continuation* packet (MSB == 0). This parser
 * accumulates packets per channel ID until the full payload is received.
 *
 * Usage: feed each 64-byte report from [BluetoothHidDeviceWrapper.incomingReports]
 * to [processReport]. When a result is non-null the full message is ready.
 */
@Singleton
class HidReportParser @Inject constructor() {

    private data class InProgress(
        val channelId: ByteArray,
        val command: Int,
        val totalLength: Int,
        val buffer: MutableList<Byte>,
        var nextSeq: Int = 0
    )

    /** Map from CID (as hex string) → in-progress reassembly state. */
    private val pending = mutableMapOf<String, InProgress>()

    /**
     * Processes one raw 64-byte HID report.
     *
     * @return a [CtapHidMessage] when the full message is assembled, or null
     *         if more continuation packets are still expected.
     * @throws [Fido2Exception.ProtocolException] on framing errors.
     */
    fun processReport(report: ByteArray): Result<CtapHidMessage?> {
        if (report.size < HID_PACKET_SIZE) {
            return Result.failure(
                Fido2Exception.ProtocolException("HID report too short: ${report.size} < $HID_PACKET_SIZE")
            )
        }

        val cid = report.copyOfRange(0, CID_SIZE)
        val cidKey = cid.toHex()

        return if (report[INIT_CMD_OFFSET].toInt() and CMD_FLAG != 0) {
            processInitPacket(cid, cidKey, report)
        } else {
            processContinuationPacket(cid, cidKey, report)
        }
    }

    // ── Init packet ───────────────────────────────────────────────────────────

    private fun processInitPacket(
        cid: ByteArray,
        cidKey: String,
        report: ByteArray
    ): Result<CtapHidMessage?> {
        val cmd = report[INIT_CMD_OFFSET].toInt() and CMD_MASK
        val bcntH = report[INIT_BCNTH_OFFSET].toInt() and 0xFF
        val bcntL = report[INIT_BCNTL_OFFSET].toInt() and 0xFF
        val totalLength = (bcntH shl 8) or bcntL

        val dataInThisPacket = minOf(INIT_DATA_SIZE, totalLength)
        val data = report.copyOfRange(INIT_DATA_OFFSET, INIT_DATA_OFFSET + dataInThisPacket)

        // Abort any prior pending message on this channel
        if (pending.containsKey(cidKey)) {
            Log.w(TAG, "New init packet received while $cidKey was pending — aborting old")
            pending.remove(cidKey)
        }

        return if (totalLength <= INIT_DATA_SIZE) {
            // Single-packet message — complete immediately
            val message = CtapHidMessage(cid, cmd, data.copyOfRange(0, totalLength))
            Result.success(message)
        } else {
            // Multi-packet: start accumulating
            val buffer = mutableListOf<Byte>().also { it.addAll(data.toList()) }
            pending[cidKey] = InProgress(cid, cmd, totalLength, buffer, nextSeq = 0)
            Result.success(null)
        }
    }

    // ── Continuation packet ───────────────────────────────────────────────────

    private fun processContinuationPacket(
        cid: ByteArray,
        cidKey: String,
        report: ByteArray
    ): Result<CtapHidMessage?> {
        val state = pending[cidKey]
            ?: return Result.failure(
                Fido2Exception.ProtocolException("Continuation packet for unknown CID $cidKey")
            )

        val seq = report[CONT_SEQ_OFFSET].toInt() and 0x7F
        if (seq != state.nextSeq) {
            pending.remove(cidKey)
            return Result.failure(
                Fido2Exception.ProtocolException("Sequence mismatch on CID $cidKey: expected ${state.nextSeq}, got $seq")
            )
        }

        val remaining = state.totalLength - state.buffer.size
        val dataInPacket = minOf(CONT_DATA_SIZE, remaining)
        val data = report.copyOfRange(CONT_DATA_OFFSET, CONT_DATA_OFFSET + dataInPacket)
        state.buffer.addAll(data.toList())
        state.nextSeq++

        return if (state.buffer.size >= state.totalLength) {
            // Message is complete
            pending.remove(cidKey)
            val payload = state.buffer.take(state.totalLength).toByteArray()
            Result.success(CtapHidMessage(cid, state.command, payload))
        } else {
            Result.success(null) // still waiting for more
        }
    }

    // ── Response packing ──────────────────────────────────────────────────────

    /**
     * Encodes a [CtapHidMessage] into one or more 64-byte HID reports that can
     * be sent back to the host via [BluetoothHidDeviceWrapper.sendReport].
     */
    fun encodeResponse(message: CtapHidMessage): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        val payload = message.payload
        val totalLength = payload.size

        // Init packet
        val initPacket = ByteArray(HID_PACKET_SIZE)
        message.channelId.copyInto(initPacket, 0)
        initPacket[INIT_CMD_OFFSET] = (message.command or CMD_FLAG).toByte()
        initPacket[INIT_BCNTH_OFFSET] = ((totalLength shr 8) and 0xFF).toByte()
        initPacket[INIT_BCNTL_OFFSET] = (totalLength and 0xFF).toByte()
        val firstChunk = minOf(INIT_DATA_SIZE, totalLength)
        payload.copyInto(initPacket, INIT_DATA_OFFSET, 0, firstChunk)
        packets.add(initPacket)

        // Continuation packets
        var offset = firstChunk
        var seq = 0
        while (offset < totalLength) {
            val contPacket = ByteArray(HID_PACKET_SIZE)
            message.channelId.copyInto(contPacket, 0)
            contPacket[CONT_SEQ_OFFSET] = (seq and 0x7F).toByte()
            val chunk = minOf(CONT_DATA_SIZE, totalLength - offset)
            payload.copyInto(contPacket, CONT_DATA_OFFSET, offset, offset + chunk)
            packets.add(contPacket)
            offset += chunk
            seq++
        }

        return packets
    }

    /**
     * Builds a CTAPHID_INIT response that assigns a new channel to the caller.
     *
     * @param nonce  The 8-byte nonce from the host's CTAPHID_INIT request.
     * @param newCid 4-byte channel ID assigned for this session.
     */
    fun buildInitResponse(nonce: ByteArray, newCid: ByteArray): CtapHidMessage {
        require(nonce.size == INIT_NONCE_SIZE) { "Nonce must be $INIT_NONCE_SIZE bytes" }
        require(newCid.size == CID_SIZE) { "CID must be $CID_SIZE bytes" }

        val payload = ByteBuffer.allocate(INIT_RESPONSE_SIZE).apply {
            put(nonce)
            put(newCid)
            put(0x02.toByte())  // CTAPHID protocol version
            put(0x01.toByte())  // Major device version
            put(0x00.toByte())  // Minor device version
            put(0x00.toByte())  // Build number
            put((CAPABILITY_CBOR).toByte())  // Capabilities
        }.array()

        return CtapHidMessage(BROADCAST_CID, CTAPHID_INIT, payload)
    }

    /**
     * Builds a CTAPHID_ERROR response for the given channel.
     */
    fun buildErrorResponse(cid: ByteArray, errorCode: Byte): CtapHidMessage {
        return CtapHidMessage(cid, CTAPHID_ERROR, byteArrayOf(errorCode))
    }

    /**
     * Builds a CTAPHID_KEEPALIVE response.
     * @param status  0x01 = processing, 0x02 = upneeded (waiting for user)
     */
    fun buildKeepAliveResponse(cid: ByteArray, status: Byte): CtapHidMessage {
        return CtapHidMessage(cid, CTAPHID_KEEPALIVE, byteArrayOf(status))
    }

    /** Clears all pending reassembly state (e.g. on disconnect). */
    fun reset() {
        pending.clear()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
