package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_MSG
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder

/** Handles the bounded ISO 7816 APDU compatibility path and routes CTAP2-over-MSG to the transport. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal class U2fApduCommandHandler(
    private val hidReportParser: HidReportParser,
    private val responseBuilder: Ctap2ResponseBuilder,
    private val enqueuePackets: (List<ByteArray>, Long) -> Unit,
    private val launchCborOperation: suspend (CtapHidMessage, Long) -> Unit,
) {
    private companion object {
        private const val ERR_INVALID_LEN: Byte = 0x03
        private const val INS_REGISTER = 0x01
        private const val INS_AUTHENTICATE = 0x02
        private const val INS_VERSION = 0x03
        private const val INS_CTAP2_OVER_MSG = 0x10
        private const val SW_SUCCESS_1 = 0x90.toByte()
        private const val SW_SUCCESS_2 = 0x00.toByte()
        private const val SW_CONDITIONS_NOT_SATISFIED_1 = 0x69.toByte()
        private const val SW_CONDITIONS_NOT_SATISFIED_2 = 0x85.toByte()
        private const val SW_WRONG_DATA_1 = 0x6A.toByte()
        private const val SW_WRONG_DATA_2 = 0x80.toByte()
        private const val SW_UNKNOWN_1 = 0x6F.toByte()
        private const val SW_UNKNOWN_2 = 0x00.toByte()
        private const val SW_INS_NOT_SUPPORTED_1 = 0x6D.toByte()
        private const val SW_INS_NOT_SUPPORTED_2 = 0x00.toByte()
        private const val APDU_MIN_SIZE = 4
        private const val APDU_LC_SHORT_OFFSET = 4
        private const val APDU_LC_EXTENDED_OFFSET = 5
        private const val APDU_LC_EXTENDED_MIN_SIZE = 7
        private const val APDU_DATA_OFFSET_SHORT = 5
        private const val BYTE_MASK = 0xFF
        private const val SHIFT_8 = 8
        private const val HEX_RADIX = 16
    }

    /**
     * Borrows the reassembled APDU payload for dispatch; the caller erases it after this returns.
     * The extracted CTAP2 payload transfers to [launchCborOperation], whose terminal job owner
     * erases it after handler completion or rejected admission.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun handle(
        message: CtapHidMessage,
        sessionGeneration: Long,
    ) {
        val cid = message.channelId
        val payload = message.payload
        Logger.d { "CTAPHID_MSG len=${payload.size} cid=${cid.toHex()}" }

        if (payload.size < APDU_MIN_SIZE) {
            enqueuePackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_LEN), sessionGeneration)
            return
        }

        val ins = payload[1].toInt() and BYTE_MASK
        if (ins == INS_CTAP2_OVER_MSG) {
            val cborData = extractApduData(payload)
            if (cborData == null || cborData.isEmpty()) {
                Logger.w { "CTAPHID_MSG INS=0x10 but APDU data is empty" }
                enqueuePackets(u2fErrorResponse(cid, SW_UNKNOWN_1.toInt(), SW_UNKNOWN_2.toInt()), sessionGeneration)
                return
            }
            Logger.d {
                "CTAPHID_MSG routing CTAP2 cmd=0x${(cborData[0].toInt() and BYTE_MASK).toString(HEX_RADIX)} as CBOR"
            }
            var transferred = false
            try {
                launchCborOperation(CtapHidMessage(cid, CTAPHID_CBOR, cborData), sessionGeneration)
                transferred = true
            } finally {
                if (!transferred) cborData.fill(0)
            }
            return
        }

        when (ins) {
            INS_VERSION -> {
                val u2fVersion = "U2F_V2".toByteArray(Charsets.US_ASCII)
                enqueuePackets(u2fSuccessResponse(cid, u2fVersion), sessionGeneration)
            }
            INS_REGISTER -> {
                Logger.i {
                    "CTAPHID_MSG U2F_REGISTER → returning SW_CONDITIONS_NOT_SATISFIED (0x6985) to escalate to CTAP2"
                }
                enqueuePackets(
                    u2fErrorResponse(cid, SW_CONDITIONS_NOT_SATISFIED_1.toInt(), SW_CONDITIONS_NOT_SATISFIED_2.toInt()),
                    sessionGeneration,
                )
            }
            INS_AUTHENTICATE -> {
                Logger.d { "CTAPHID_MSG U2F_AUTHENTICATE → SW_WRONG_DATA (triggers CTAP2 GetAssertion)" }
                enqueuePackets(
                    u2fErrorResponse(cid, SW_WRONG_DATA_1.toInt(), SW_WRONG_DATA_2.toInt()),
                    sessionGeneration,
                )
            }
            else -> {
                Logger.d { "CTAPHID_MSG U2F INS=0x${ins.toString(HEX_RADIX)} unknown — returning SW_INS_NOT_SUPPORTED" }
                enqueuePackets(
                    u2fErrorResponse(cid, SW_INS_NOT_SUPPORTED_1.toInt(), SW_INS_NOT_SUPPORTED_2.toInt()),
                    sessionGeneration,
                )
            }
        }
    }

    /** Extracts the declared data bytes from short or extended ISO 7816-4 APDU framing. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    private fun extractApduData(apdu: ByteArray): ByteArray? {
        if (apdu.size < APDU_MIN_SIZE) return null
        if (apdu.size == APDU_MIN_SIZE) return ByteArray(0)

        return if (apdu[APDU_LC_SHORT_OFFSET] != 0x00.toByte()) {
            val lc = apdu[APDU_LC_SHORT_OFFSET].toInt() and BYTE_MASK
            if (apdu.size >= APDU_DATA_OFFSET_SHORT + lc) {
                apdu.copyOfRange(APDU_DATA_OFFSET_SHORT, APDU_DATA_OFFSET_SHORT + lc)
            } else {
                null
            }
        } else {
            if (apdu.size < APDU_LC_EXTENDED_MIN_SIZE) return null
            val lc =
                ((apdu[APDU_LC_EXTENDED_OFFSET].toInt() and BYTE_MASK) shl SHIFT_8) or
                    (apdu[APDU_LC_EXTENDED_OFFSET + 1].toInt() and BYTE_MASK)
            val endOffset = APDU_LC_EXTENDED_MIN_SIZE + lc
            if (apdu.size >= endOffset) {
                apdu.copyOfRange(APDU_LC_EXTENDED_MIN_SIZE, endOffset)
            } else {
                Logger.e { "BluetoothHidTransport: APDU data extraction failed - buffer too small for lc=$lc" }
                null
            }
        }
    }

    @Req("NFR-SEC-041", "TR-AUTH-031")
    private fun u2fSuccessResponse(
        cid: ByteArray,
        data: ByteArray,
    ): List<ByteArray> =
        hidReportParser.encodeResponse(CtapHidMessage(cid, CTAPHID_MSG, data + byteArrayOf(SW_SUCCESS_1, SW_SUCCESS_2)))

    @Req("NFR-SEC-041", "TR-AUTH-031")
    private fun u2fErrorResponse(
        cid: ByteArray,
        sw1: Int,
        sw2: Int,
    ): List<ByteArray> =
        hidReportParser.encodeResponse(CtapHidMessage(cid, CTAPHID_MSG, byteArrayOf(sw1.toByte(), sw2.toByte())))

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
