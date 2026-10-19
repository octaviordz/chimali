package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.bluetooth.CTAPHID_CANCEL
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_INIT
import com.chimali.fido2.bluetooth.CTAPHID_MSG
import com.chimali.fido2.bluetooth.CTAPHID_PING
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider

/** Dispatches non-CBOR CTAPHID commands and transfers CBOR messages to their bounded request owner. */
internal class CtapHidCommandDispatcher(
    private val hidReportParser: HidReportParser,
    private val responseBuilder: Ctap2ResponseBuilder,
    private val getAssertionHandler: Ctap2GetAssertionHandler,
    private val authenticatorInfoProvider: AuthenticatorInfoProvider,
    private val cborRequestOwner: CtapHidRequestOwner,
    private val u2fApduCommandHandler: U2fApduCommandHandler,
    private val isCurrentSession: (Long) -> Boolean,
    private val isChannelRegistered: (ByteArray, Long) -> Boolean,
    private val allocateCid: (Long) -> ByteArray?,
    private val sendPackets: (List<ByteArray>, Long) -> Unit,
) {
    /** Dispatches one parsed message and erases its payload unless a CBOR owner accepts it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun dispatch(
        message: CtapHidMessage,
        sessionGeneration: Long,
    ) {
        if (!isCurrentSession(sessionGeneration)) {
            message.payload.fill(0)
            return
        }
        if (message.command != CTAPHID_INIT &&
            !isChannelRegistered(message.channelId, sessionGeneration)
        ) {
            try {
                sendPackets(
                    responseBuilder.hidErrorResponse(message.channelId, ERR_INVALID_CHANNEL),
                    sessionGeneration,
                )
            } finally {
                message.payload.fill(0)
            }
            return
        }
        if (message.command == CTAPHID_CBOR) {
            var transferred = false
            try {
                cborRequestOwner.dispatch(message, sessionGeneration)
                transferred = true
            } finally {
                if (!transferred) message.payload.fill(0)
            }
            return
        }

        try {
            when (message.command) {
                CTAPHID_INIT -> handleInit(message, sessionGeneration)
                CTAPHID_MSG -> u2fApduCommandHandler.handle(message, sessionGeneration)
                CTAPHID_PING ->
                    sendPackets(
                        hidReportParser.encodeResponse(
                            CtapHidMessage(message.channelId, CTAPHID_PING, message.payload),
                        ),
                        sessionGeneration,
                    )
                CTAPHID_CANCEL -> cborRequestOwner.cancel(message.channelId, sessionGeneration)
                else -> {
                    Logger.w { "Unknown CTAPHID command 0x${message.command.toString(HEX_RADIX)}" }
                    sendPackets(responseBuilder.hidErrorResponse(message.channelId, ERR_INVALID_CMD), sessionGeneration)
                }
            }
        } finally {
            message.payload.fill(0)
        }
    }

    private fun handleInit(
        message: CtapHidMessage,
        sessionGeneration: Long,
    ) {
        val nonce = message.payload.takeIf { it.size >= NONCE_SIZE }?.copyOfRange(0, NONCE_SIZE)
        if (nonce == null) {
            sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_INVALID_LEN), sessionGeneration)
            return
        }
        try {
            val cid = allocateCid(sessionGeneration)
            if (cid == null) {
                sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_CHANNEL_BUSY), sessionGeneration)
                return
            }
            Logger.i { "CTAPHID_INIT: assigned CID=${cid.toHex()}" }
            val response = hidReportParser.buildInitResponse(nonce, cid)
            sendPackets(hidReportParser.encodeResponse(response), sessionGeneration)
        } finally {
            nonce.fill(0)
        }
    }

    /** Builds the owned assertion response by stripping and erasing the borrowed CTAP command byte. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun handleGetAssertion(message: CtapHidMessage): List<ByteArray> {
        val requestBytes = message.payload.copyOfRange(1, message.payload.size)
        return try {
            getAssertionHandler.handle(message.channelId, requestBytes)
        } finally {
            requestBytes.fill(0)
        }
    }

    /** Produces the current authenticator capability response with the established CTAP error mapping. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun handleGetInfo(cid: ByteArray): List<ByteArray> =
        try {
            val info = authenticatorInfoProvider.getAuthenticatorInfo()
            val packets = responseBuilder.getInfoResponse(cid, info)
            if (packets.isNotEmpty()) {
                val hex = packets.first().joinToString("") { packetByte -> "%02x".format(packetByte) }
                Logger.d { "GetInfo response packet[0] hex: $hex" }
            }
            packets
        } catch (failure: Fido2Exception) {
            Logger.e(failure) { "GetInfo failed: ${failure.message}" }
            responseBuilder.errorResponse(cid, GET_INFO_FAILURE_STATUS)
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /** Existing CTAP2 status emitted when GetInfo raises a FIDO2 exception. */
        const val GET_INFO_FAILURE_STATUS: Byte = 0x30
        const val ERR_INVALID_CMD: Byte = 0x01
        const val ERR_INVALID_LEN: Byte = 0x03
        const val ERR_CHANNEL_BUSY: Byte = 0x06
        const val ERR_INVALID_CHANNEL: Byte = 0x0B
        const val NONCE_SIZE = 8
        const val HEX_RADIX = 16
        val BROADCAST_CID = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
    }
}
