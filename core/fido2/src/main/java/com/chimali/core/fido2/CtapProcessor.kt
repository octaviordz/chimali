package com.chimali.core.fido2

data class CtapResponse(
    val status: Byte,
    val data: ByteArray
)

class CtapProcessor {
    
    fun processPacket(packet: ByteArray): CtapResponse {
        if (packet.isEmpty()) {
            return CtapResponse(0, packet)
        }

        return when (packet[0].toInt()) {
            0x02 -> { // authenticatorGetAssertion
                CtapResponse(0x01, ByteArray(0)) // kCtap2ErrUserActionRequired
            }
            0x01 -> { // authenticatorMakeCredential
                CtapResponse(0x01, ByteArray(0)) // kCtap2ErrUserActionRequired
            }
            else -> {
                CtapResponse(0, packet)
            }
        }
    }
}
