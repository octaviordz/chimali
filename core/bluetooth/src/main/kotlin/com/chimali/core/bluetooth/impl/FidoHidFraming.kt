package com.chimali.core.bluetooth.impl

import com.chimali.core.bluetooth.util.BluetoothHidConstants
import java.nio.ByteBuffer

/**
 * FIDO HID framing logic for Bluetooth Classic HID transport.
 *
 * Implements the CTAP over HID protocol framing as specified by the FIDO Alliance:
 * https://fidoalliance.org/specs/fido-v2.0-ps-20190130/fido-client-to-authenticator-protocol-v2.0-ps-20190130.html#usb
 *
 * Each HID packet is exactly [HID_REPORT_SIZE] bytes. Messages larger than one
 * packet are split into an Initialization packet followed by one or more Continuation packets.
 *
 * Packet Format:
 * - Initialization:  CID(4) | CMD(1, MSB=1) | BCNTH(1) | BCNTL(1) | DATA(57)
 * - Continuation:    CID(4) | SEQ(1, MSB=0) | DATA(59)
 *
 * Ported from WIOsense/rauth-android (Framing.java), adapted to Kotlin idioms.
 */
object FidoHidFraming {

    // --- HID Report Sizes ---
    const val HID_PACKET_SIZE = BluetoothHidConstants.HID_REPORT_SIZE // 62
    private const val CID_LENGTH = 4
    private const val INIT_HEADER_SIZE = 7  // CID(4) + CMD(1) + BCNTH(1) + BCNTL(1)
    private const val CONT_HEADER_SIZE = 5  // CID(4) + SEQ(1)
    const val INIT_DATA_SIZE = HID_PACKET_SIZE - INIT_HEADER_SIZE // 55
    const val CONT_DATA_SIZE = HID_PACKET_SIZE - CONT_HEADER_SIZE // 57

    // --- Broadcast Channel ID (used for INIT command before CID assignment) ---
    val BROADCAST_CID = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

    // --- HID Command Codes (MSB set = Init packet command) ---
    const val CMD_PING: Byte   = 0x81.toByte()  // Echo data back
    const val CMD_MSG: Byte    = 0x83.toByte()  // U2F message (CTAP1)
    const val CMD_LOCK: Byte   = 0x84.toByte()  // Lock channel
    const val CMD_INIT: Byte   = 0x86.toByte()  // Channel initialization
    const val CMD_WINK: Byte   = 0x88.toByte()  // Device wink
    const val CMD_CBOR: Byte   = 0x90.toByte()  // CTAP2 CBOR message
    const val CMD_CANCEL: Byte = 0xBE.toByte()  // Cancel any outstanding request
    const val CMD_ERROR: Byte  = 0xBF.toByte()  // Error response
    const val CMD_KEEPALIVE: Byte = 0xBB.toByte() // Processing, please wait

    // Keep-alive status codes
    const val KEEPALIVE_PROCESSING: Byte = 0x01  // Still processing
    const val KEEPALIVE_UP_NEEDED: Byte  = 0x02  // User presence needed

    // Error codes
    const val ERR_INVALID_CMD: Byte  = 0x01  // The command in the request is invalid
    const val ERR_INVALID_LEN: Byte  = 0x03  // The length field (BCNT) is invalid
    const val ERR_INVALID_SEQ: Byte  = 0x04  // The sequence number is invalid
    const val ERR_MSG_TIMEOUT: Byte  = 0x05  // The message has timed out
    const val ERR_CHANNEL_BUSY: Byte = 0x06  // The device is busy

    // --- Data Classes ---

    /** A fully reassembled FIDO HID message. */
    data class HidMessage(
        val channelId: ByteArray,
        val cmd: Byte,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HidMessage) return false
            return channelId.contentEquals(other.channelId) &&
                cmd == other.cmd &&
                data.contentEquals(other.data)
        }
        override fun hashCode(): Int {
            var result = channelId.contentHashCode()
            result = 31 * result + cmd
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /** Accumulates HID packets for a single logical channel until a complete message is formed. */
    class Accumulator {
        private var channelId: ByteArray? = null
        private var cmd: Byte = 0
        private var totalLen: Int = -1
        private var buffer = ByteBuffer.allocate(0)
        private var nextSeq: Int = 0

        /** Reset accumulator state (call on cancel or sequence error). */
        fun reset() {
            channelId = null
            totalLen = -1
            nextSeq = 0
            buffer = ByteBuffer.allocate(0)
        }

        /**
         * Add a raw 64-byte HID packet. Returns a complete [HidMessage] once all
         * continuation packets have been received, or null if more packets are needed.
         */
        fun addPacket(packet: ByteArray): HidMessage? {
            if (packet.size != HID_PACKET_SIZE) return null

            val cid = packet.copyOfRange(0, CID_LENGTH)
            val controlByte = packet[CID_LENGTH].toInt() and 0xFF

            return if (controlByte and 0x80 != 0) {
                // Initialization packet
                channelId = cid
                cmd = packet[CID_LENGTH]
                val bcntH = packet[CID_LENGTH + 1].toInt() and 0xFF
                val bcntL = packet[CID_LENGTH + 2].toInt() and 0xFF
                totalLen = (bcntH shl 8) or bcntL

                buffer = ByteBuffer.allocate(totalLen)
                val dataLen = INIT_DATA_SIZE.coerceAtMost(totalLen)
                buffer.put(packet, INIT_HEADER_SIZE, dataLen)
                nextSeq = 0

                if (buffer.position() >= totalLen) completeMessage() else null
            } else {
                // Continuation packet — validate state
                if (totalLen == -1 || channelId == null || !cid.contentEquals(channelId!!)) {
                    reset()
                    return null
                }
                val seq = controlByte
                if (seq != nextSeq) {
                    reset()
                    return null
                }
                val remaining = totalLen - buffer.position()
                val dataLen = CONT_DATA_SIZE.coerceAtMost(remaining)
                buffer.put(packet, CONT_HEADER_SIZE, dataLen)
                nextSeq++

                if (buffer.position() >= totalLen) completeMessage() else null
            }
        }

        private fun completeMessage(): HidMessage {
            val result = HidMessage(channelId!!, cmd, buffer.array().copyOf(totalLen))
            reset()
            return result
        }
    }

    /**
     * Encodes a [HidMessage] into one or more 64-byte HID packets ready to send.
     */
    fun encode(message: HidMessage): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        val payload = message.data

        // --- Initialization packet ---
        val initPacket = ByteArray(HID_PACKET_SIZE)
        System.arraycopy(message.channelId, 0, initPacket, 0, CID_LENGTH)
        initPacket[CID_LENGTH] = message.cmd
        initPacket[CID_LENGTH + 1] = ((payload.size shr 8) and 0xFF).toByte()
        initPacket[CID_LENGTH + 2] = (payload.size and 0xFF).toByte()
        val firstDataLen = INIT_DATA_SIZE.coerceAtMost(payload.size)
        System.arraycopy(payload, 0, initPacket, INIT_HEADER_SIZE, firstDataLen)
        packets.add(initPacket)

        // --- Continuation packets ---
        var offset = firstDataLen
        var seq = 0
        while (offset < payload.size) {
            val contPacket = ByteArray(HID_PACKET_SIZE)
            System.arraycopy(message.channelId, 0, contPacket, 0, CID_LENGTH)
            contPacket[CID_LENGTH] = (seq and 0x7F).toByte()
            val dataLen = CONT_DATA_SIZE.coerceAtMost(payload.size - offset)
            System.arraycopy(payload, offset, contPacket, CONT_HEADER_SIZE, dataLen)
            packets.add(contPacket)
            offset += dataLen
            seq++
        }

        return packets
    }

    /**
     * Build a FIDO INIT response to assign a new channel ID.
     *
     * @param requestPacket The raw INIT command packet received on BROADCAST_CID.
     * @param newCid        The new Channel ID to assign.
     */
    fun buildInitResponse(requestPacket: ByteArray, newCid: ByteArray): ByteArray {
        require(requestPacket.size == HID_PACKET_SIZE)
        require(newCid.size == CID_LENGTH)

        // INIT response payload: NONCE(8) | CID(4) | PROTOCOL_VER(1) | DEV_VER_MAJ(1) | DEV_VER_MIN(1) | DEV_VER_BLD(1) | CAPS(1)
        val nonce = requestPacket.copyOfRange(INIT_HEADER_SIZE, INIT_HEADER_SIZE + 8)
        val responseData = ByteArray(17).also {
            System.arraycopy(nonce, 0, it, 0, 8)
            System.arraycopy(newCid, 0, it, 8, 4)
            it[12] = 0x02  // CTAPHID protocol version
            it[13] = 0x01  // Device major version
            it[14] = 0x00  // Device minor version
            it[15] = 0x00  // Device build version
            it[16] = 0x04  // Capabilities: CBOR (bit 2 set = 0x04)
        }
        return encode(HidMessage(BROADCAST_CID, CMD_INIT, responseData)).first()
    }

    /**
     * Build an error response packet.
     */
    fun buildErrorResponse(channelId: ByteArray, errorCode: Byte): ByteArray =
        encode(HidMessage(channelId, CMD_ERROR, byteArrayOf(errorCode))).first()
}
