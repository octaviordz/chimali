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
            0x04 -> { // authenticatorGetInfo
                // Robust CBOR response for Windows:
                // { 
                //   0x01 (versions): ["FIDO_2_0"], 
                //   0x03 (aaguid): [16 bytes], 
                //   0x04 (options): {"up": true, "uv": true, "rk": true} 
                // }
                val responseData = byteArrayOf(
                    0xA3.toByte(), // Map of 3 items
                    0x01.toByte(), // Key 1: versions
                    0x81.toByte(), // Array of 1
                    0x68.toByte(), 0x46.toByte(), 0x49.toByte(), 0x44.toByte(), 0x4F.toByte(), 0x5F.toByte(), 0x32.toByte(), 0x5F.toByte(), 0x30.toByte(), // "FIDO_2_0"
                    0x03.toByte(), // Key 3: aaguid
                    0x50.toByte(), // Byte string length 16
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
                    0x04.toByte(), // Key 4: options
                    0xA3.toByte(), // Map of 3 items (up, uv, rk)
                    0x62.toByte(), 0x75.toByte(), 0x70.toByte(), // "up"
                    0xF5.toByte(), // True
                    0x62.toByte(), 0x75.toByte(), 0x76.toByte(), // "uv"
                    0xF5.toByte(), // True
                    0x62.toByte(), 0x72.toByte(), 0x6B.toByte(), // "rk" (Resident Key / Discoverable Credential)
                    0xF5.toByte()  // True
                )
                CtapResponse(0x00, responseData)
            }
            0x02 -> { // authenticatorGetAssertion
                CtapResponse(0x01, ByteArray(0)) // kCtap2ErrUserActionRequired
            }
            0x01 -> { // authenticatorMakeCredential
                CtapResponse(0x01, ByteArray(0)) // kCtap2ErrUserActionRequired
            }
            else -> {
                CtapResponse(0x00, packet)
            }
        }
    }
}
