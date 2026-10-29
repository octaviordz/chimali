package com.chimali.fido2.performance

/** Accepts only a successful CTAP GetInfo response with the expected declared capabilities. */
internal fun isSuccessfulGetInfo(packets: List<ByteArray>): Boolean {
    val firstPacket = packets.firstOrNull() ?: return false
    val flattened = packets.flatMap { it.toList() }.toByteArray()
    val capabilityPayload = firstPacket.getOrNull(8)?.toInt()?.and(0xe0) == 0xa0
    val expectedCapabilities = listOf("FIDO_2_0", "FIDO_2_1", "credProtect", "usb")
    val capabilityContentPresent =
        expectedCapabilities.all { capability ->
            flattened.containsBytes(capability.encodeToByteArray())
        }
    return firstPacket.size == 62 &&
        firstPacket[4] == 0x90.toByte() &&
        firstPacket[7] == 0x00.toByte() &&
        packets.all { it.size == 62 } &&
        capabilityPayload &&
        capabilityContentPresent
}

private fun ByteArray.containsBytes(expected: ByteArray): Boolean =
    indices.any { start ->
        start + expected.size <= size && expected.indices.all { offset -> this[start + offset] == expected[offset] }
    }
