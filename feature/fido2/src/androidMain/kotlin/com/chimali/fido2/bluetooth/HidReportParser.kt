package com.chimali.fido2.bluetooth

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.exception.Fido2Exception
import java.nio.ByteBuffer
import org.koin.core.annotation.Single

// ── CTAP2-over-HID packet structure (FIDO CTAP HID spec §8) ──────────────────
//
// The FIDO CTAP HID specification relies on a fixed packet size agreed upon
// in the HID Descriptor. Our HID Descriptor sets the report size to 62 bytes.
// Therefore, the CTAPHID packet size is exactly 62 bytes.
//
//
// Init packet (first in sequence):
//   [CID 4B] [CMD 1B (bit7=1)] [BCNTH 1B] [BCNTL 1B] [DATA up to 55B]
//
// Continuation packet:
//   [CID 4B] [SEQ 1B (bit7=0, 0x00-0x7F)] [DATA up to 57B]

/** Number of bytes in each CTAP HID report for the configured Bluetooth transport. */
internal const val HID_PACKET_SIZE = 62 // Must match FIDO_HID_REPORT_SIZE (62) exactly

/** Number of leading bytes reserved for the channel identifier in each report. */
internal const val CID_SIZE = 4

/** Byte index of the command field in an initial packet. */
internal const val INIT_CMD_OFFSET = 4

/** Byte index of the high byte of the initial packet's payload length. */
internal const val INIT_BCNTH_OFFSET = 5

/** Byte index of the low byte of the initial packet's payload length. */
internal const val INIT_BCNTL_OFFSET = 6

/** Byte index where initial-packet payload data begins. */
internal const val INIT_DATA_OFFSET = 7

/** Maximum payload bytes carried by one initial packet. */
internal const val INIT_DATA_SIZE = HID_PACKET_SIZE - INIT_DATA_OFFSET // 55

/** Byte index of the sequence number in a continuation packet. */
internal const val CONT_SEQ_OFFSET = 4

/** Byte index where continuation-packet payload data begins. */
internal const val CONT_DATA_OFFSET = 5

/** Maximum payload bytes carried by one continuation packet. */
internal const val CONT_DATA_SIZE = HID_PACKET_SIZE - CONT_DATA_OFFSET // 57

private const val CMD_FLAG = 0x80 // bit7 set → init packet
private const val CMD_MASK = 0x7F
private const val BYTE_MASK = 0xFF
private const val SHIFT_8 = 8

// Well-known CTAPHID commands

/** CTAPHID_MSG command code for legacy U2F messages. */
internal const val CTAPHID_MSG = 0x03

/** CTAPHID_CBOR command code for CTAP2 messages. */
internal const val CTAPHID_CBOR = 0x10

/** CTAPHID_INIT command code for channel allocation and initialization. */
internal const val CTAPHID_INIT = 0x06

/** CTAPHID_PING command code for echoing payloads. */
internal const val CTAPHID_PING = 0x01

/** CTAPHID_CANCEL command code for canceling an in-progress operation. */
internal const val CTAPHID_CANCEL = 0x11

/** CTAPHID_ERROR command code for transport-level errors. */
internal const val CTAPHID_ERROR = 0x3F

/** CTAPHID_KEEPALIVE command code for reporting operation progress. */
internal const val CTAPHID_KEEPALIVE = 0x3B

// CTAPHID_INIT nonce / response sizes

private const val INIT_NONCE_SIZE = 8

// response: nonce(8) + CID_assigned(4) + protocolVersion(1) + majorDV(1) + minorDV(1) + buildDV(1) + capabilities(1)
private const val INIT_RESPONSE_SIZE = 17
private const val INIT_PROTOCOL_VERSION: Byte = 0x02
private const val INIT_DEVICE_MAJOR_VERSION: Byte = 0x01
private const val INIT_DEVICE_MINOR_VERSION: Byte = 0x00
private const val INIT_DEVICE_BUILD_VERSION: Byte = 0x00

/** Maximum payload accepted across one initial packet and 128 continuation packets. */
internal const val MAX_HID_MESSAGE_SIZE = INIT_DATA_SIZE + (128 * CONT_DATA_SIZE)

private const val MAX_PENDING_HID_MESSAGES = 8
private const val PENDING_HID_MESSAGE_TIMEOUT_NANOS = 5_000_000_000L

private const val CAPABILITY_CBOR = 0x04 // Authenticator supports CTAPHID_CBOR (CTAP2)
private const val CAPABILITY_NMSG = 0x08 // Authenticator does NOT support CTAPHID_MSG (U2F)
//
// ⚠️  BOTH bits must be set for a CTAP2-only authenticator.
//
// Without CAPABILITY_NMSG, Windows assumes MSG is supported and sends
// U2F_REGISTER (INS=0x01) as a legacy capability probe.  Any U2F APDU
// error code returned — including 0x6985 SW_CONDITIONS_NOT_SATISFIED —
// causes Windows to enter an infinite polling loop: it shows "Touch your
// security key" and re-sends U2F_REGISTER every ~300 ms until it times out
// and kills the process.  It NEVER escalates to CTAPHID_CBOR MakeCredential.
//
// With CAPABILITY_NMSG set, Windows reads from the INIT response that
// CTAPHID_MSG is not supported, skips the U2F probe entirely, and goes
// directly to CTAPHID_CBOR (0x10) for authenticatorMakeCredential.
//
// The prior concern "may trip device-state validation errors" was based on
// early testing before CTAPHID_MSG handling existed and is no longer
// applicable.  wiokey-android sets NMSG; we do the same.

/** CID assigned to the broadcast channel (used for CTAPHID_INIT). */
val BROADCAST_CID: ByteArray =
    byteArrayOf(
        BYTE_MASK.toByte(),
        BYTE_MASK.toByte(),
        BYTE_MASK.toByte(),
        BYTE_MASK.toByte(),
    )

/**
 * Represents a fully assembled CTAP2 HID command — after multi-packet reassembly.
 */
data class CtapHidMessage(
    /** Channel identifier carried by the assembled CTAP HID message. */
    @Req("FR-HID-010", "FR-VAL-020")
    val channelId: ByteArray,
    /** Command code with the initial-packet flag bit removed. */
    @Req("FR-HID-010", "FR-VAL-020")
    val command: Int,
    /** Reassembled message payload bytes. */
    @Req("FR-HID-010", "FR-VAL-020")
    val payload: ByteArray,
) {
    /** Compares channel, command, and payload by value; byte arrays use content equality. */
    @Req("FR-HID-010", "FR-VAL-020")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CtapHidMessage) return false
        return channelId.contentEquals(other.channelId) &&
            command == other.command &&
            payload.contentEquals(other.payload)
    }

    /** Returns a content-derived collection hash consistent with [equals]. */
    @Req("FR-HID-010", "FR-VAL-020")
    override fun hashCode(): Int {
        var result = channelId.contentHashCode()
        result = 31 * result + command
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Parses 62-byte raw HID reports and reassembles multi-packet CTAP2 HID messages.
 *
 * Per FIDO CTAP HID spec §8, each report is either an *init* packet
 * (MSB of byte[4] == 1) or a *continuation* packet (MSB == 0). This parser
 * accumulates packets per channel ID until the full payload is received.
 *
 * Android-specific: report size is 62 (not the spec's 64) due to the L2CAP
 * MTU cap. See [FIDO_HID_REPORT_SIZE] and the wiokey-android reference.
 *
 * Usage: feed each report from [BluetoothHidDeviceWrapper.incomingReports]
 * to [processReport]. When a result is non-null the full message is ready.
 */
@Single
@Req("FR-HID-010", "FR-VAL-020")
class HidReportParser {
    private data class InProgress(
        val channelId: ByteArray,
        val command: Int,
        val buffer: ByteArray,
        var bytesWritten: Int,
        val createdAtNanos: Long,
        var nextSeq: Int = 0,
    )

    /** Map from CID (as hex string) → in-progress reassembly state. */
    private val pending = mutableMapOf<String, InProgress>()

    /** Monotonic nanosecond clock used for expiration and deterministic host tests. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal var monotonicNanos: () -> Long = System::nanoTime

    /** Optional host-test observer receiving the parser-owned reassembly buffer after allocation. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal var onReassemblyBufferCreatedForTest: ((ByteArray) -> Unit)? = null

    /**
     * Processes one raw HID report (62 bytes on BT HID — see [HID_PACKET_SIZE]).
     *
     * Borrows the platform-owned report. Any completed message owns a separate payload; its
     * terminal dispatcher must erase that payload after the operation finishes.
     *
     * @return a [CtapHidMessage] when the full message is assembled, or null
     *         if more continuation packets are still expected.
     * @throws [Fido2Exception.ProtocolException] on framing errors.
     */
    @Synchronized
    @Req("FR-HID-010", "FR-VAL-020")
    fun processReport(report: ByteArray): Result<CtapHidMessage?> {
        expirePending(monotonicNanos())
        if (report.size != HID_PACKET_SIZE) {
            return Result.failure(
                Fido2Exception.ProtocolException(
                    "Invalid HID report size: ${report.size} != $HID_PACKET_SIZE",
                ),
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

    @Req("FR-HID-010", "FR-VAL-020")
    private fun processInitPacket(
        cid: ByteArray,
        cidKey: String,
        report: ByteArray,
    ): Result<CtapHidMessage?> {
        val cmd = report[INIT_CMD_OFFSET].toInt() and CMD_MASK
        val bcntH = report[INIT_BCNTH_OFFSET].toInt() and BYTE_MASK
        val bcntL = report[INIT_BCNTL_OFFSET].toInt() and BYTE_MASK
        val totalLength = (bcntH shl SHIFT_8) or bcntL

        // A new init packet replaces any incomplete request from the same channel, even when
        // the new length itself is rejected.
        pending.remove(cidKey)?.erase()

        if (totalLength > MAX_HID_MESSAGE_SIZE) {
            return Result.failure(
                Fido2Exception.ProtocolException("HID message exceeds $MAX_HID_MESSAGE_SIZE bytes"),
            )
        }

        val dataInThisPacket = minOf(INIT_DATA_SIZE, totalLength)

        if (totalLength > INIT_DATA_SIZE && pending.size >= MAX_PENDING_HID_MESSAGES) {
            return Result.failure(
                Fido2Exception.ProtocolException("Too many incomplete HID messages"),
            )
        }

        return if (totalLength <= INIT_DATA_SIZE) {
            // This returned copy becomes the message payload owner. The caller must erase it
            // after dispatch; the platform report remains borrowed and is never modified here.
            val payload = report.copyOfRange(INIT_DATA_OFFSET, INIT_DATA_OFFSET + totalLength)
            Result.success(CtapHidMessage(cid, cmd, payload))
        } else {
            val buffer = ByteArray(totalLength)
            onReassemblyBufferCreatedForTest?.invoke(buffer)
            report.copyInto(buffer, 0, INIT_DATA_OFFSET, INIT_DATA_OFFSET + dataInThisPacket)
            pending[cidKey] =
                InProgress(
                    channelId = cid,
                    command = cmd,
                    buffer = buffer,
                    bytesWritten = dataInThisPacket,
                    createdAtNanos = monotonicNanos(),
                    nextSeq = 0,
                )
            Result.success(null)
        }
    }

    // ── Continuation packet ───────────────────────────────────────────────────

    @Req("FR-HID-010", "FR-VAL-020")
    private fun processContinuationPacket(
        cid: ByteArray,
        cidKey: String,
        report: ByteArray,
    ): Result<CtapHidMessage?> {
        val state =
            pending[cidKey]
                ?: return Result.failure(
                    Fido2Exception.ProtocolException("Continuation packet for unknown CID $cidKey"),
                )

        val seq = report[CONT_SEQ_OFFSET].toInt() and CMD_MASK
        if (seq != state.nextSeq) {
            pending.remove(cidKey)
            state.erase()
            return Result.failure(
                Fido2Exception.ProtocolException(
                    "Sequence mismatch on CID $cidKey: expected ${state.nextSeq}, got $seq",
                ),
            )
        }

        val remaining = state.buffer.size - state.bytesWritten
        val dataInPacket = minOf(CONT_DATA_SIZE, remaining)
        report.copyInto(state.buffer, state.bytesWritten, CONT_DATA_OFFSET, CONT_DATA_OFFSET + dataInPacket)
        state.bytesWritten += dataInPacket
        state.nextSeq++

        return if (state.bytesWritten == state.buffer.size) {
            pending.remove(cidKey)
            val payload = state.buffer.copyOf()
            state.erase()
            Result.success(CtapHidMessage(cid, state.command, payload))
        } else {
            Result.success(null)
        }
    }

    @Req("FR-HID-010", "FR-VAL-020")
    private fun InProgress.erase() {
        buffer.fill(0)
        channelId.fill(0)
        bytesWritten = 0
    }

    /**
     * Erases every partial request whose monotonic lifetime has elapsed. The bounded receive
     * loop calls this during idle polling and parsing calls it before accepting another packet.
     */
    @Synchronized
    @Req("FR-HID-010", "FR-VAL-020")
    fun expirePending(nowNanos: Long = monotonicNanos()) {
        val expiredKeys =
            pending
                .filterValues { nowNanos - it.createdAtNanos >= PENDING_HID_MESSAGE_TIMEOUT_NANOS }
                .keys
                .toList()
        expiredKeys.forEach { key -> pending.remove(key)?.erase() }
    }

    // ── Response packing ──────────────────────────────────────────────────────

    /**
     * Encodes a [CtapHidMessage] into one or more 62-byte BT HID reports (see [HID_PACKET_SIZE])
     * that can be sent back to the host via [BluetoothHidDeviceWrapper.sendReport].
     */
    @Req("FR-HID-010", "FR-VAL-020")
    fun encodeResponse(message: CtapHidMessage): List<ByteArray> {
        require(message.channelId.size == CID_SIZE) { "CID must be $CID_SIZE bytes" }
        require(message.payload.size <= MAX_HID_MESSAGE_SIZE) {
            "HID message exceeds $MAX_HID_MESSAGE_SIZE bytes"
        }
        val packets = mutableListOf<ByteArray>()
        val payload = message.payload
        val totalLength = payload.size

        // Init packet
        val initPacket = ByteArray(HID_PACKET_SIZE)
        message.channelId.copyInto(initPacket, 0)
        initPacket[INIT_CMD_OFFSET] = (message.command or CMD_FLAG).toByte()
        initPacket[INIT_BCNTH_OFFSET] = ((totalLength shr SHIFT_8) and BYTE_MASK).toByte()
        initPacket[INIT_BCNTL_OFFSET] = (totalLength and BYTE_MASK).toByte()
        val firstChunk = minOf(INIT_DATA_SIZE, totalLength)
        payload.copyInto(initPacket, INIT_DATA_OFFSET, 0, firstChunk)
        packets.add(initPacket)

        // Continuation packets
        var offset = firstChunk
        var seq = 0
        while (offset < totalLength) {
            val contPacket = ByteArray(HID_PACKET_SIZE)
            message.channelId.copyInto(contPacket, 0)
            contPacket[CONT_SEQ_OFFSET] = (seq and CMD_MASK).toByte()
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
     * @return A message containing the nonce, assigned channel, protocol version and capabilities.
     * @throws IllegalArgumentException if either input has an invalid length.
     */
    @Req("FR-HID-010", "FR-VAL-020")
    fun buildInitResponse(
        nonce: ByteArray,
        newCid: ByteArray,
    ): CtapHidMessage {
        require(nonce.size == INIT_NONCE_SIZE) { "Nonce must be $INIT_NONCE_SIZE bytes" }
        require(newCid.size == CID_SIZE) { "CID must be $CID_SIZE bytes" }

        val payload =
            ByteBuffer
                .allocate(INIT_RESPONSE_SIZE)
                .apply {
                    put(nonce)
                    put(newCid)
                    put(INIT_PROTOCOL_VERSION)
                    put(INIT_DEVICE_MAJOR_VERSION)
                    put(INIT_DEVICE_MINOR_VERSION)
                    put(INIT_DEVICE_BUILD_VERSION)
                    put(
                        (CAPABILITY_CBOR or CAPABILITY_NMSG).toByte(),
                    ) // 0x0C: CBOR supported, MSG not — prevents U2F polling loop
                }.array()

        return CtapHidMessage(BROADCAST_CID, CTAPHID_INIT, payload)
    }

    /**
     * Builds a CTAPHID_ERROR response using the borrowed channel identifier and one status byte.
     * The returned message owns its one-byte payload; the caller retains ownership of [cid].
     */
    @Req("FR-HID-010", "FR-VAL-020")
    fun buildErrorResponse(
        cid: ByteArray,
        errorCode: Byte,
    ): CtapHidMessage = CtapHidMessage(cid, CTAPHID_ERROR, byteArrayOf(errorCode))

    /**
     * Builds a CTAPHID_KEEPALIVE response with a one-byte progress status.
     * The returned message owns its payload; [cid] remains borrowed.
     * @param status 0x01 means processing; 0x02 means user action is needed.
     */
    @Req("FR-HID-010", "FR-VAL-020")
    fun buildKeepAliveResponse(
        cid: ByteArray,
        status: Byte,
    ): CtapHidMessage = CtapHidMessage(cid, CTAPHID_KEEPALIVE, byteArrayOf(status))

    /** Erases all pending request bytes and channel copies when a transport session ends. */
    @Synchronized
    @Req("FR-HID-010", "FR-VAL-020")
    fun reset() {
        pending.values.forEach { it.erase() }
        pending.clear()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
