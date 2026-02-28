package com.chimali.core.bluetooth.impl

import java.nio.ByteBuffer

object FidoBleFraming {
    const val CMD_PING = 0x81.toByte()
    const val CMD_KEEPALIVE = 0x82.toByte()
    const val CMD_MSG = 0x83.toByte()
    const val CMD_CANCEL = 0xBE.toByte()
    const val CMD_ERROR = 0xBF.toByte()

    /**
     * Represents a partially or fully assembled FIDO BLE message.
     */
    data class Message(
        val cmd: Byte,
        val data: ByteArray
    )

    /**
     * Accumulates fragments for a specific device connection.
     */
    class Accumulator {
        private var cmd: Byte = 0
        private var totalLen: Int = -1
        private var buffer = ByteBuffer.allocate(0)
        private var nextSeq: Int = 0

        fun reset() {
            totalLen = -1
            nextSeq = 0
        }

        fun addFragment(fragment: ByteArray): Message? {
            if (fragment.isEmpty()) return null

            val firstByte = fragment[0].toInt() and 0xFF
            
            if (firstByte and 0x80 != 0) {
                // Initialization Fragment
                if (fragment.size < 3) return null
                
                cmd = fragment[0]
                totalLen = ((fragment[1].toInt() and 0xFF) shl 8) or (fragment[2].toInt() and 0xFF)
                
                buffer = ByteBuffer.allocate(totalLen)
                val dataLen = (fragment.size - 3).coerceAtMost(totalLen)
                buffer.put(fragment, 3, dataLen)
                nextSeq = 0
            } else {
                // Continuation Fragment
                if (totalLen == -1) return null // Haven't seen init fragment
                
                val seq = firstByte
                if (seq != nextSeq) {
                    reset()
                    return null // Sequence error
                }
                
                val remaining = totalLen - buffer.position()
                val dataLen = (fragment.size - 1).coerceAtMost(remaining)
                buffer.put(fragment, 1, dataLen)
                nextSeq++
            }

            return if (buffer.position() == totalLen) {
                val fullData = buffer.array()
                val result = Message(cmd, fullData)
                reset()
                result
            } else {
                null
            }
        }
    }

    /**
     * Fragments a message into one or more packets based on MTU.
     */
    fun fragment(message: Message, mtu: Int): List<ByteArray> {
        val payload = message.data
        val fragments = mutableListOf<ByteArray>()
        
        // FIDO BLE Spec: The maximum length of a FIDO BLE packet is (MTU - 3) bytes.
        val maxPacketSize = mtu - 3
        
        // Note: In FIDO BLE, the first fragment has 3 bytes of header (CMD, LEN_H, LEN_L)
        // Subsequent fragments have 1 byte of header (SEQ)
        
        // firstFragPayloadSize = maxPacketSize - 3 header bytes
        val firstFragPayloadSize = (maxPacketSize - 3).coerceAtMost(payload.size)
        val firstFrag = ByteArray(3 + firstFragPayloadSize)
        firstFrag[0] = message.cmd
        firstFrag[1] = ((payload.size shr 8) and 0xFF).toByte()
        firstFrag[2] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, firstFrag, 3, firstFragPayloadSize)
        fragments.add(firstFrag)

        var offset = firstFragPayloadSize
        var seq = 0
        while (offset < payload.size) {
            // nextFragPayloadSize = maxPacketSize - 1 header byte
            val nextFragPayloadSize = (maxPacketSize - 1).coerceAtMost(payload.size - offset)
            val nextFrag = ByteArray(1 + nextFragPayloadSize)
            nextFrag[0] = (seq and 0x7F).toByte()
            System.arraycopy(payload, offset, nextFrag, 1, nextFragPayloadSize)
            fragments.add(nextFrag)
            offset += nextFragPayloadSize
            seq++
        }

        return fragments
    }
}
