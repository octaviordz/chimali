package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import java.util.UUID

/**
 * Encapsulates standard FIDO Bluetooth Low Energy (BLE) framing, packet fragmentation,
 * sequence validation, and characteristic UUIDs conforming to FIDO CTAP 2.2 §11.4.
 */
@Req("TR-BLE-010", "TR-BLE-020", "FR-BLE-010", "FR-BLE-020", "FR-BLE-050")
internal object FidoBleFraming {
    /** Standard FIDO BLE Service UUID (16-bit 0xFFFD). */
    val FIDO_SERVICE_UUID: UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")

    /** FIDO Control Point characteristic UUID (Write/Write Request). */
    val CONTROL_POINT_CHARACTERISTIC_UUID: UUID = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")

    /** FIDO Status characteristic UUID (Notify). */
    val STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")

    /** FIDO Control Point Length characteristic UUID (Read). */
    val CONTROL_POINT_LENGTH_CHARACTERISTIC_UUID: UUID = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")

    /** FIDO Service Revision Bitfield characteristic UUID (Read/Write). */
    val REVISION_BITFIELD_CHARACTERISTIC_UUID: UUID = UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")

    /** Client Characteristic Configuration Descriptor UUID (0x2902). */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    /** Standard FIDO2 protocol revision bit (Byte 0, bit 5: 0x20). */
    const val REVISION_FIDO2: Byte = 0x20.toByte()

    // ── Command codes ──────────────────────────────────────────────────────────
    const val CMD_PING: Byte = 0x81.toByte()
    const val CMD_KEEPALIVE: Byte = 0x82.toByte()
    const val CMD_MSG: Byte = 0x83.toByte()
    const val CMD_CANCEL: Byte = 0xBE.toByte()
    const val CMD_ERROR: Byte = 0xBF.toByte()

    // ── Keepalive statuses ─────────────────────────────────────────────────────
    const val STATUS_PROCESSING: Byte = 0x01.toByte()
    const val STATUS_UPNEEDED: Byte = 0x02.toByte()

    // ── Framing error codes ────────────────────────────────────────────────────
    const val ERR_INVALID_CMD: Byte = 0x01.toByte()
    const val ERR_INVALID_PAR: Byte = 0x02.toByte()
    const val ERR_INVALID_LEN: Byte = 0x03.toByte()
    const val ERR_INVALID_SEQ: Byte = 0x04.toByte()
    const val ERR_REQ_TIMEOUT: Byte = 0x05.toByte()
    const val ERR_BUSY: Byte = 0x06.toByte()
    const val ERR_OTHER: Byte = 0x7F.toByte()

    private const val MIN_ATT_MTU = 23
    private const val ATT_HEADER_OVERHEAD = 3
    private const val MIN_WRITE_CAPACITY = 20
    private const val MAX_WRITE_CAPACITY = 512
    private const val INIT_HEADER_SIZE = 3
    private const val CONT_HEADER_SIZE = 1
    private const val INIT_COMMAND_FLAG_MASK = 0x80
    private const val BYTE_MASK = 0xFF
    private const val BYTE_SHIFT = 8
    private const val SEQUENCE_MASK = 0x7F
    private const val MAX_WIRE_LENGTH = 65535

    /**
     * Calculates the supported maximum Control Point single-write size in the inclusive range 20–512 bytes.
     */
    @Req("FR-BLE-020", "TR-BLE-020")
    fun calculateWriteCapacity(
        attMtu: Int,
        maxPlatformCapacity: Int = MAX_WRITE_CAPACITY,
    ): Int {
        require(attMtu >= MIN_ATT_MTU) { "ATT MTU must be at least $MIN_ATT_MTU, but was $attMtu" }
        val capacity = minOf(MAX_WRITE_CAPACITY, minOf(attMtu - ATT_HEADER_OVERHEAD, maxPlatformCapacity))
        require(capacity >= MIN_WRITE_CAPACITY) { "Calculated capacity $capacity below minimum $MIN_WRITE_CAPACITY" }
        return capacity
    }

    /**
     * Calculates notification capacity bounded by ATT MTU minus 3 and the platform supported capacity.
     */
    @Req("FR-BLE-020", "TR-BLE-020")
    fun calculateNotifyCapacity(
        attMtu: Int,
        maxPlatformCapacity: Int = MAX_WRITE_CAPACITY,
    ): Int {
        require(attMtu >= MIN_ATT_MTU) { "ATT MTU must be at least $MIN_ATT_MTU, but was $attMtu" }
        return minOf(attMtu - ATT_HEADER_OVERHEAD, maxPlatformCapacity)
    }

    /**
     * Returns the next sequence number wrapping modulo 128 (0x00..0x7F).
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun nextSequence(currentSeq: Int): Int = (currentSeq + 1) and SEQUENCE_MASK

    /**
     * Encodes a FIDO BLE initialization frame.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun encodeInitializationFrame(
        command: Byte,
        totalLength: Int,
        data: ByteArray,
        offset: Int,
        chunkSize: Int,
    ): ByteArray {
        val frame = ByteArray(INIT_HEADER_SIZE + chunkSize)
        frame[0] = command
        frame[1] = ((totalLength shr BYTE_SHIFT) and BYTE_MASK).toByte()
        frame[2] = (totalLength and BYTE_MASK).toByte()
        System.arraycopy(data, offset, frame, INIT_HEADER_SIZE, chunkSize)
        return frame
    }

    /**
     * Encodes a FIDO BLE continuation frame.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun encodeContinuationFrame(
        sequence: Int,
        data: ByteArray,
        offset: Int,
        chunkSize: Int,
    ): ByteArray {
        val frame = ByteArray(CONT_HEADER_SIZE + chunkSize)
        frame[0] = (sequence and SEQUENCE_MASK).toByte()
        System.arraycopy(data, offset, frame, CONT_HEADER_SIZE, chunkSize)
        return frame
    }

    /**
     * Encodes an error response frame (Command 0xBF, 0x00, ErrorCode).
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun encodeErrorFrame(errorCode: Byte): ByteArray = byteArrayOf(CMD_ERROR, 0x00.toByte(), errorCode)

    /**
     * Encodes a periodic keepalive frame (Command 0x82, 0x00, 0x01, Status).
     */
    @Req("FR-BLE-060", "TR-BLE-040")
    fun encodeKeepaliveFrame(status: Byte): ByteArray = byteArrayOf(CMD_KEEPALIVE, 0x00.toByte(), 0x01.toByte(), status)

    /**
     * Fragments a response payload into a list of initialization and continuation frames.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun fragmentResponse(
        command: Byte,
        payload: ByteArray,
        notifyCapacity: Int,
    ): List<ByteArray> {
        val totalLength = payload.size
        val frames = mutableListOf<ByteArray>()

        val firstChunkSize = minOf(totalLength, notifyCapacity - INIT_HEADER_SIZE)
        frames.add(
            encodeInitializationFrame(
                command = command,
                totalLength = totalLength,
                data = payload,
                offset = 0,
                chunkSize = firstChunkSize,
            ),
        )

        var sentBytes = firstChunkSize
        var seq = 0

        while (sentBytes < totalLength) {
            val chunkSize = minOf(totalLength - sentBytes, notifyCapacity - CONT_HEADER_SIZE)
            frames.add(
                encodeContinuationFrame(
                    sequence = seq,
                    data = payload,
                    offset = sentBytes,
                    chunkSize = chunkSize,
                ),
            )
            sentBytes += chunkSize
            seq = nextSequence(seq)
        }

        return frames
    }

    /**
     * Convenience alias for [fragmentResponse].
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun frameResponse(
        command: Byte,
        payload: ByteArray,
        maxFragmentSize: Int,
    ): List<ByteArray> = fragmentResponse(command, payload, maxFragmentSize)

    /**
     * Result of feeding a frame into [RequestAssembler].
     */
    sealed interface FeedResult {
        data class Complete(
            val command: Byte,
            val payload: ByteArray,
        ) : FeedResult

        data class InProgress(
            val received: Int,
            val total: Int,
        ) : FeedResult

        data class Error(
            val errorCode: Byte,
        ) : FeedResult
    }

    /**
     * State machine for reassembling fragmented FIDO BLE request packets.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    class RequestAssembler(
        val maxMsgSize: Int,
    ) {
        init {
            require(maxMsgSize in 1..MAX_WIRE_LENGTH) {
                "maxMsgSize must be positive and <= $MAX_WIRE_LENGTH, but was $maxMsgSize"
            }
        }

        private var currentCommand: Byte = 0
        private var declaredLength = 0
        private var receivedBytes = 0
        private var expectedSequence = 0
        private var buffer: ByteArray? = null

        val isAssembling: Boolean
            get() = buffer != null

        /**
         * Feeds a single packet into the assembler.
         */
        @Req("FR-BLE-050", "TR-BLE-020")
        fun feed(frame: ByteArray): FeedResult {
            if (frame.isEmpty()) {
                reset()
                return FeedResult.Error(ERR_INVALID_PAR)
            }

            val firstByte = frame[0].toInt() and BYTE_MASK
            val isInitFrame = (firstByte and INIT_COMMAND_FLAG_MASK) != 0

            return if (isInitFrame) {
                handleInitialization(frame, firstByte.toByte())
            } else {
                handleContinuation(frame, firstByte)
            }
        }

        private fun handleInitialization(
            frame: ByteArray,
            cmd: Byte,
        ): FeedResult {
            if (frame.size < INIT_HEADER_SIZE) {
                reset()
                return FeedResult.Error(ERR_INVALID_LEN)
            }

            val len =
                ((frame[1].toInt() and BYTE_MASK) shl BYTE_SHIFT) or
                    (frame[2].toInt() and BYTE_MASK)
            if (len > maxMsgSize || len > MAX_WIRE_LENGTH) {
                reset()
                return FeedResult.Error(ERR_INVALID_LEN)
            }

            // Zero existing buffer before reassignment
            reset()

            currentCommand = cmd
            declaredLength = len
            val dataInFrame = frame.size - INIT_HEADER_SIZE

            if (dataInFrame > len) {
                reset()
                return FeedResult.Error(ERR_INVALID_LEN)
            }

            val newBuffer = ByteArray(len)
            System.arraycopy(frame, INIT_HEADER_SIZE, newBuffer, 0, dataInFrame)
            buffer = newBuffer
            receivedBytes = dataInFrame
            expectedSequence = 0

            return if (receivedBytes == declaredLength) {
                val completePayload = newBuffer
                buffer = null
                FeedResult.Complete(currentCommand, completePayload)
            } else {
                FeedResult.InProgress(receivedBytes, declaredLength)
            }
        }

        private fun handleContinuation(
            frame: ByteArray,
            seq: Int,
        ): FeedResult {
            val buf = buffer ?: return FeedResult.Error(ERR_INVALID_SEQ)

            if (seq != expectedSequence) {
                reset()
                return FeedResult.Error(ERR_INVALID_SEQ)
            }

            val dataInFrame = frame.size - CONT_HEADER_SIZE
            if (receivedBytes + dataInFrame > declaredLength) {
                reset()
                return FeedResult.Error(ERR_INVALID_LEN)
            }

            System.arraycopy(frame, CONT_HEADER_SIZE, buf, receivedBytes, dataInFrame)
            receivedBytes += dataInFrame
            expectedSequence = nextSequence(expectedSequence)

            return if (receivedBytes == declaredLength) {
                val completePayload = buf
                buffer = null
                FeedResult.Complete(currentCommand, completePayload)
            } else {
                FeedResult.InProgress(receivedBytes, declaredLength)
            }
        }

        /**
         * Clears and securely zeroes active reassembly buffers.
         */
        @Req("NFR-SEC-030", "FR-BLE-050")
        fun reset() {
            buffer?.fill(0)
            buffer = null
            receivedBytes = 0
            declaredLength = 0
            expectedSequence = 0
            currentCommand = 0
        }
    }
}
