package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import org.koin.core.annotation.Single

/**
 * Result of an unframed CTAP command execution.
 *
 * @property responseBytes Full raw response array: status code (first byte, 0x00 on success)
 *                         followed by optional CBOR-encoded payload.
 * @property presenceRequired Whether user presence was required for this ceremony.
 */
@Req("FR-BLE-080", "TR-BLE-030")
data class RawCtapResult(
    val responseBytes: ByteArray,
    val presenceRequired: Boolean = false,
) {
    /** 1-byte CTAP status code (e.g. 0x00 for CTAP2_OK). */
    val status: Byte get() = responseBytes.firstOrNull() ?: 0x7F.toByte()

    /** True if status is CTAP2_OK (0x00). */
    val isSuccess: Boolean get() = status == 0x00.toByte()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawCtapResult) return false
        if (!responseBytes.contentEquals(other.responseBytes)) return false
        return presenceRequired == other.presenceRequired
    }

    override fun hashCode(): Int {
        var result = responseBytes.contentHashCode()
        result = 31 * result + presenceRequired.hashCode()
        return result
    }
}

/**
 * Raw unframed CTAP operation boundary.
 *
 * Decouples CTAP2 protocol execution (authenticatorMakeCredential, authenticatorGetAssertion,
 * authenticatorGetInfo) from transport framing (HID vs BLE).
 */
@Req("FR-BLE-080", "TR-BLE-030")
interface RawCtapOperation {
    /**
     * Executes an unframed CTAP command.
     *
     * @param request Full CTAP request starting with the command byte (e.g. 0x01, 0x02, 0x04).
     * @param onKeepalive Optional callback to report keepalive status (0x01 = processing, 0x02 = upneeded).
     * @return Owned mutable [RawCtapResult].
     */
    suspend fun execute(
        request: ByteArray,
        onKeepalive: ((Byte) -> Unit)? = null,
    ): RawCtapResult

    /** Convenience alias for [execute]. */
    suspend fun handle(
        request: ByteArray,
        onKeepalive: ((Byte) -> Unit)? = null,
    ): RawCtapResult = execute(request, onKeepalive)
}

/**
 * Default implementation of [RawCtapOperation] routing to core handlers while preserving
 * master seed derivation session ownership, timeouts, and user verification semantics.
 */
@Single(binds = [RawCtapOperation::class])
@Req("FR-BLE-080", "TR-BLE-030")
class RawCtapOperationImpl(
    private val makeCredentialHandler: Ctap2MakeCredentialHandler,
    private val getAssertionHandler: Ctap2GetAssertionHandler,
    private val authenticatorInfoProvider: AuthenticatorInfoProvider,
    private val responseBuilder: Ctap2ResponseBuilder,
    private val masterSeedProvider: MasterSeedProvider,
) : RawCtapOperation {
    companion object {
        const val CMD_MAKE_CREDENTIAL: Byte = 0x01
        const val CMD_GET_ASSERTION: Byte = 0x02
        const val CMD_GET_INFO: Byte = 0x04

        const val CTAP2_OK: Byte = 0x00
        const val CTAP2_ERR_INVALID_CMD: Byte = 0x01
        const val CTAP2_ERR_UNSUPPORTED_CMD: Byte = 0x01
        const val CTAP2_ERR_OTHER: Byte = 0x7F.toByte()
        private const val HEX_RADIX = 16
        private const val BYTE_MASK = 0xFF
    }

    override suspend fun execute(
        request: ByteArray,
        onKeepalive: ((Byte) -> Unit)?,
    ): RawCtapResult {
        if (request.isEmpty()) {
            return RawCtapResult(byteArrayOf(CTAP2_ERR_INVALID_CMD))
        }

        val cmd = request[0]
        return when (cmd) {
            CMD_MAKE_CREDENTIAL -> {
                masterSeedProvider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { _ ->
                    makeCredentialHandler.handleRaw(request, onKeepalive)
                }
            }

            CMD_GET_ASSERTION -> {
                val requestBody = request.copyOfRange(1, request.size)
                try {
                    masterSeedProvider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { _ ->
                        getAssertionHandler.handleRaw(requestBody, onKeepalive)
                    }
                } finally {
                    requestBody.fill(0)
                }
            }

            CMD_GET_INFO -> {
                handleGetInfo()
            }

            else -> {
                Logger.w { "Unsupported CTAP2 command: 0x${(cmd.toInt() and BYTE_MASK).toString(HEX_RADIX)}" }
                RawCtapResult(byteArrayOf(CTAP2_ERR_UNSUPPORTED_CMD))
            }
        }
    }

    private suspend fun handleGetInfo(): RawCtapResult =
        try {
            val info = authenticatorInfoProvider.getAuthenticatorInfo()
            val payload = responseBuilder.buildGetInfoRawPayload(info)
            RawCtapResult(payload)
        } catch (failure: Fido2Exception) {
            Logger.e(failure) { "RawCtapOperation: GetInfo failed: ${failure.message}" }
            RawCtapResult(byteArrayOf(0x30.toByte()))
        }
}
