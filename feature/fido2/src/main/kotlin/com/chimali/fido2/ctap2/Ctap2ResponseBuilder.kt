package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.service.AuthenticatorInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// CTAP2 status codes
private const val CTAP2_OK:                   Byte = 0x00
private const val CTAP2_ERR_INVALID_CBOR:     Byte = 0x12.toByte()
private const val CTAP2_ERR_MISSING_PARAMETER:Byte = 0x14.toByte()
private const val CTAP2_ERR_NO_CREDENTIALS:   Byte = 0x2E.toByte()
private const val CTAP2_ERR_NOT_ALLOWED:      Byte = 0x36.toByte()
private const val CTAP2_ERR_UNSUPPORTED_ALGO: Byte = 0x26.toByte()
private const val CTAP2_ERR_OPERATION_DENIED: Byte = 0x27.toByte()
private const val CTAP2_ERR_KEY_STORE_FULL:   Byte = 0x28.toByte()
private const val CTAP2_ERR_PIN_BLOCKED:      Byte = 0x32.toByte()
private const val CTAP2_ERR_PIN_INVALID:      Byte = 0x31.toByte()

private const val CMD_CBOR_BARE = CTAPHID_CBOR and 0x7F   // 0x10

/**
 * Builds properly encoded CTAP2 response [CtapHidMessage]s and their corresponding
 * 64-byte HID report sequences.
 *
 * Each CTAP2 response consists of:
 *   - 1-byte CTAP2 status code
 *   - (Optional) JSON-encoded response body (using the project's CborCodec)
 */
@Singleton
class Ctap2ResponseBuilder @Inject constructor(
    private val cborCodec: CborCodec,
    private val hidReportParser: HidReportParser
) {

    // ── MakeCredential response ───────────────────────────────────────────────

    /**
     * Encodes a successful `authenticatorMakeCredential` response.
     */
    fun makeCredentialResponse(
        cid: ByteArray,
        attestation: AttestationObject
    ): List<ByteArray> {
        val authDataBytes = serializeAuthData(attestation.authData)
        // CTAP2 §6.1 integer keys: 1=fmt, 2=authData, 3=attStmt
        val responseMap: Map<String, Any> = mapOf(
            "1" to attestation.fmt,
            "2" to authDataBytes,          // raw ByteArray, not List
            "3" to emptyMap<String, Any>() // attStmt ("none" format)
        )
        return successCborPackets(cid, responseMap)
    }

    // ── GetInfo response ──────────────────────────────────────────────────────

    /**
     * Encodes a `authenticatorGetInfo` (0x04) response per CTAP2 §6.4.
     */
    fun getInfoResponse(cid: ByteArray, info: AuthenticatorInfo): List<ByteArray> {
        // CTAP2 §6.4 authenticatorGetInfo response — keys MUST be integers:
        //   1 = versions, 2 = extensions, 3 = aaguid, 4 = options,
        //   5 = maxMsgSize, 6 = pinUvAuthProtocols, 7 = maxCredentialCountInList,
        //   8 = maxCredentialIdLength, 9 = transports
        //
        // Windows (CtapGetPluginAuthenticatorList) requires:
        //   - "U2F_V2" in versions (even for CTAP2-only authenticators)
        //   - key 9 (transports) listing the transport, e.g. ["bluetooth"]
        //     Without this, Windows 0x80090011 "Object not found" error occurs.
        val responseMap: Map<String, Any> = mapOf(
            "1" to listOf("FIDO_2_0"), // versions — CTAP2 only; no U2F_V2 to prevent Windows from trying U2F_REGISTER
            "3" to info.aaguid,                   // aaguid: raw ByteArray (16 bytes)
            "4" to mapOf(                          // options
                "rk" to info.supportsResidentKeys,
                "up" to true,
                "uv" to true,                      // device has internal UV (biometric) — required for discoverable credentials
                "plat" to false                    // not platform-bound
            ),
            "5" to 1200L,                          // maxMsgSize
            "8" to 255L,                           // maxCredentialIdLength
            "9" to listOf("usb"),                  // transports — Windows treats HID as USB-like
            "10" to listOf(                        // algorithms
                mapOf("alg" to COSE_ES256.toLong(), "type" to "public-key")
            )
        )
        Timber.d("getInfoResponse: versions=[FIDO_2_0] aaguid=%dbytes transports=[usb]", info.aaguid.size)
        return successCborPackets(cid, responseMap)
    }

    // ── Error response ────────────────────────────────────────────────────────

    /**
     * Encodes a CTAP2 error response (status byte only, no body).
     */
    fun errorResponse(cid: ByteArray, statusCode: Byte): List<ByteArray> {
        val payload = byteArrayOf(statusCode)
        val msg = CtapHidMessage(cid, CMD_CBOR_BARE, payload)
        return hidReportParser.encodeResponse(msg)
    }

    /**
     * Encodes a CTAPHID-level error using the HID ERROR command.
     */
    fun hidErrorResponse(cid: ByteArray, errorCode: Byte): List<ByteArray> {
        val msg = hidReportParser.buildErrorResponse(cid, errorCode)
        return hidReportParser.encodeResponse(msg)
    }

    // ── KeepAlive ─────────────────────────────────────────────────────────────

    /**
     * Sends a CTAPHID_KEEPALIVE packet to prevent host timeout.
     * @param status 0x01 = processing, 0x02 = upneeded (waiting for user presence)
     */
    fun keepAliveResponse(cid: ByteArray, status: Byte = 0x01): List<ByteArray> {
        val msg = hidReportParser.buildKeepAliveResponse(cid, status)
        return hidReportParser.encodeResponse(msg)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun successCborPackets(
        cid: ByteArray,
        responseMap: Map<String, Any>
    ): List<ByteArray> {
        val encoded = cborCodec.encodeToFido2Format(responseMap)
        val payload = byteArrayOf(CTAP2_OK) + encoded
        val msg = CtapHidMessage(cid, CMD_CBOR_BARE, payload)
        return hidReportParser.encodeResponse(msg)
    }

    /**
     * Serialises [AuthenticatorData] to bytes per WebAuthn §6.1:
     *   rpIdHash(32) | flags(1) | signCount(4 BE) | attestedCredData
     */
    private fun serializeAuthData(authData: AuthenticatorData): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(authData.rpIdHash.toList())    // 32 bytes
        out.addAll(authData.flags.toList())        // 1 byte
        val cnt = authData.counter
        out.add(((cnt shr 24) and 0xFF).toByte())
        out.add(((cnt shr 16) and 0xFF).toByte())
        out.add(((cnt shr  8) and 0xFF).toByte())
        out.add(( cnt         and 0xFF).toByte())
        // Attested credential data (bit6 of flags)
        if (authData.flags.isNotEmpty() && (authData.flags[0].toInt() and 0x40) != 0) {
            out.addAll(authData.aaguid.toList())
            val idLen = authData.credentialId.size
            out.add(((idLen shr 8) and 0xFF).toByte())
            out.add(( idLen        and 0xFF).toByte())
            out.addAll(authData.credentialId.toList())
            out.addAll(authData.publicKey.toList())
        }
        return out.toByteArray()
    }

    /** Human-readable description of a CTAP2 status code (for logging only). */
    fun statusDescription(code: Byte): String = when (code) {
        CTAP2_OK                    -> "CTAP2_OK"
        CTAP2_ERR_INVALID_CBOR      -> "CTAP2_ERR_INVALID_CBOR (0x12)"
        CTAP2_ERR_MISSING_PARAMETER -> "CTAP2_ERR_MISSING_PARAMETER (0x14)"
        CTAP2_ERR_NO_CREDENTIALS    -> "CTAP2_ERR_NO_CREDENTIALS (0x2E)"
        CTAP2_ERR_NOT_ALLOWED       -> "CTAP2_ERR_NOT_ALLOWED (0x36)"
        CTAP2_ERR_UNSUPPORTED_ALGO  -> "CTAP2_ERR_UNSUPPORTED_ALGORITHM (0x26)"
        CTAP2_ERR_OPERATION_DENIED  -> "CTAP2_ERR_OPERATION_DENIED (0x27)"
        CTAP2_ERR_KEY_STORE_FULL    -> "CTAP2_ERR_KEY_STORE_FULL (0x28)"
        CTAP2_ERR_PIN_BLOCKED       -> "CTAP2_ERR_PIN_BLOCKED (0x32)"
        CTAP2_ERR_PIN_INVALID       -> "CTAP2_ERR_PIN_INVALID (0x31)"
        else -> "UNKNOWN (0x${code.toInt().and(0xFF).toString(16).uppercase()})"
    }
}
