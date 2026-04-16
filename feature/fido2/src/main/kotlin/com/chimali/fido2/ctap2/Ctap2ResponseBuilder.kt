package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.AuthenticatorInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// CTAP2 status codes
private const val CTAP2_OK: Byte = 0x00
private const val CTAP2_ERR_INVALID_CBOR: Byte = 0x12.toByte()
private const val CTAP2_ERR_MISSING_PARAMETER: Byte = 0x14.toByte()
private const val CTAP2_ERR_NO_CREDENTIALS: Byte = 0x2E.toByte()
private const val CTAP2_ERR_NOT_ALLOWED: Byte = 0x36.toByte()
private const val CTAP2_ERR_UNSUPPORTED_ALGO: Byte = 0x26.toByte()
private const val CTAP2_ERR_OPERATION_DENIED: Byte = 0x27.toByte()
private const val CTAP2_ERR_KEY_STORE_FULL: Byte = 0x28.toByte()
private const val CTAP2_ERR_PIN_BLOCKED: Byte = 0x32.toByte()
private const val CTAP2_ERR_PIN_INVALID: Byte = 0x31.toByte()

private const val CMD_CBOR_BARE = CTAPHID_CBOR and 0x7F // 0x10

/**
 * Builds properly encoded CTAP2 response [CtapHidMessage]s and their corresponding
 * 64-byte HID report sequences.
 *
 * Each CTAP2 response consists of:
 *   - 1-byte CTAP2 status code
 *   - (Optional) JSON-encoded response body (using the project's CborCodec)
 */
@Singleton
class Ctap2ResponseBuilder
    @Inject
    constructor(
        private val cborCodec: CborCodec,
        private val hidReportParser: HidReportParser,
    ) {
        companion object {
            // Response keys (§6.1, §6.4)
            private const val KEY_FMT = "1"
            private const val KEY_AUTH_DATA = "2"
            private const val KEY_ATT_STMT = "3"
            private const val KEY_VERSIONS = "1"
            private const val KEY_EXTENSIONS = "2"
            private const val KEY_AAGUID = "3"
            private const val KEY_OPTIONS = "4"
            private const val KEY_MAX_MSG_SIZE = "5"
            private const val KEY_MAX_CREDS = "7"
            private const val KEY_MAX_CRED_ID_LEN = "8"
            private const val KEY_TRANSPORTS = "9"
            private const val KEY_ALGORITHMS = "10"

            // Bitwise flags
            private const val FLAG_AT_MASK = 0x40

            // Shifts and masks
            private const val SHIFT_24 = 24
            private const val SHIFT_16 = 16
            private const val SHIFT_8 = 8
            private const val BYTE_MASK = 0xFF

            // Response defaults
            private const val MAX_MSG_SIZE = 1200L
            private const val MAX_CRED_ID_LEN = 255L
        }

        // ── MakeCredential response ───────────────────────────────────────────────

        /**
         * Encodes a successful `authenticatorMakeCredential` response.
         */
        fun makeCredentialResponse(
            cid: ByteArray,
            attestation: AttestationObject,
        ): List<ByteArray> {
            val authDataBytes = serializeAuthData(attestation.authData)
            // CTAP2 §6.1 integer keys: 1=fmt, 2=authData, 3=attStmt
            val responseMap: Map<String, Any> =
                mapOf(
                    KEY_FMT to attestation.fmt,
                    KEY_AUTH_DATA to authDataBytes, // raw ByteArray, not List
                    KEY_ATT_STMT to serializeAttStmt(attestation.attStmt),
                )
            return successCborPackets(cid, responseMap)
        }

        // ── GetInfo response ──────────────────────────────────────────────────────

        /**
         * Encodes a `authenticatorGetInfo` (0x04) response per CTAP2 §6.4.
         */
        fun getInfoResponse(
            cid: ByteArray,
            info: AuthenticatorInfo,
        ): List<ByteArray> {
            // CTAP2 §6.4 authenticatorGetInfo response — keys MUST be integers:
            //   1 = versions, 2 = extensions, 3 = aaguid, 4 = options,
            //   5 = maxMsgSize, 6 = pinUvAuthProtocols, 7 = maxCredentialCountInList,
            //   8 = maxCredentialIdLength, 9 = transports
            //
            // Windows (CtapGetPluginAuthenticatorList) requires:
            //   - "U2F_V2" in versions (even for CTAP2-only authenticators)
            //   - key 9 (transports) listing the transport, e.g. ["bluetooth"]
            //     Without this, Windows 0x80090011 "Object not found" error occurs.
            //
            // T056b (FIDO2.1): extensions key (0x02) and updated options announce
            //   credProtect, minPinLength, and hmac-secret support per FR-HID-020.
            //   clientPin=true is advertised so clients know PIN is available.
            //   Out-of-scope: enterprise attestation, largeBlobKey.
            val responseMap: Map<String, Any> =
                mapOf(
                    KEY_VERSIONS to listOf("FIDO_2_0", "FIDO_2_1"), // versions — include FIDO_2_1 for CTAP2.1 clients
                    KEY_EXTENSIONS to listOf("credProtect", "hmac-secret", "minPinLength"), // extensions (FIDO2.1)
                    KEY_AAGUID to info.aaguid, // aaguid: raw ByteArray (16 bytes)
                    KEY_OPTIONS to
                        mapOf( // options
                            "rk" to info.supportsResidentKeys,
                            "up" to true,
                            "uv" to true, // device has internal UV (biometric)
                            "clientPin" to true, // PIN capability advertised (FIDO2.1)
                            "credProtect" to true, // credProtect extension supported (FIDO2.1)
                            "plat" to false, // not platform-bound
                        ),
                    KEY_MAX_MSG_SIZE to MAX_MSG_SIZE, // maxMsgSize
                    KEY_MAX_CRED_ID_LEN to MAX_CRED_ID_LEN, // maxCredentialIdLength
                    KEY_TRANSPORTS to listOf("usb"), // transports — Windows treats HID as USB-like
                    KEY_ALGORITHMS to
                        listOf( // algorithms
                            mapOf("alg" to PasskeyCredential.COSE_ES256.toLong(), "type" to "public-key"),
                            mapOf("alg" to PasskeyCredential.COSE_ED25519.toLong(), "type" to "public-key"),
                            mapOf("alg" to PasskeyCredential.COSE_ML_DSA_65.toLong(), "type" to "public-key"),
                        ),
                )
            Timber.d(
                "getInfoResponse: versions=[FIDO_2_0,FIDO_2_1] extensions=[credProtect,hmac-secret,minPinLength] aaguid=%dbytes",
                info.aaguid.size,
            )
            return successCborPackets(cid, responseMap)
        }

        // ── Error response ────────────────────────────────────────────────────────

        /**
         * Encodes a CTAP2 error response (status byte only, no body).
         */
        fun errorResponse(
            cid: ByteArray,
            statusCode: Byte,
        ): List<ByteArray> {
            val payload = byteArrayOf(statusCode)
            val msg = CtapHidMessage(cid, CMD_CBOR_BARE, payload)
            return hidReportParser.encodeResponse(msg)
        }

        /**
         * Encodes a CTAPHID-level error using the HID ERROR command.
         */
        fun hidErrorResponse(
            cid: ByteArray,
            errorCode: Byte,
        ): List<ByteArray> {
            val msg = hidReportParser.buildErrorResponse(cid, errorCode)
            return hidReportParser.encodeResponse(msg)
        }

        // ── KeepAlive ─────────────────────────────────────────────────────────────

        /**
         * Sends a CTAPHID_KEEPALIVE packet to prevent host timeout.
         * @param status 0x01 = processing, 0x02 = upneeded (waiting for user presence)
         */
        fun keepAliveResponse(
            cid: ByteArray,
            status: Byte = 0x01,
        ): List<ByteArray> {
            val msg = hidReportParser.buildKeepAliveResponse(cid, status)
            return hidReportParser.encodeResponse(msg)
        }

        // ── Internal helpers ──────────────────────────────────────────────────────

        private fun successCborPackets(
            cid: ByteArray,
            responseMap: Map<String, Any>,
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
            out.addAll(authData.rpIdHash.toList()) // 32 bytes
            out.addAll(authData.flags.toList()) // 1 byte
            val cnt = authData.counter
            out.add(((cnt shr SHIFT_24) and BYTE_MASK.toLong()).toByte())
            out.add(((cnt shr SHIFT_16) and BYTE_MASK.toLong()).toByte())
            out.add(((cnt shr SHIFT_8) and BYTE_MASK.toLong()).toByte())
            out.add((cnt and BYTE_MASK.toLong()).toByte())
            // Attested credential data (bit6 of flags)
            if (authData.flags.isNotEmpty() && (authData.flags[0].toInt() and FLAG_AT_MASK) != 0) {
                out.addAll(authData.aaguid.toList())
                val idLen = authData.credentialId.size
                out.add(((idLen shr SHIFT_8) and BYTE_MASK).toByte())
                out.add((idLen and BYTE_MASK).toByte())
                out.addAll(authData.credentialId.toList())
                out.addAll(authData.publicKey.toList())
            }
            return out.toByteArray()
        }

        /**
         * Serialises [AttestationStatement] to a map.
         */
        private fun serializeAttStmt(attStmt: AttestationStatement): Map<String, Any> {
            if (attStmt.fmt == "none") return emptyMap()

            val map = mutableMapOf<String, Any>()
            if (attStmt.fmt == "packed") {
                map["alg"] = attStmt.alg
                // In packed format, the signature is stored in attCert in our domain model
                attStmt.attCert?.let { map["sig"] = it }
                attStmt.x5c?.let { map["x5c"] = it }
            } else {
                // Future formats (fido-u2f, android-key) can be added here
                attStmt.attCert?.let { map["sig"] = it }
            }
            return map
        }

        /** Human-readable description of a CTAP2 status code (for logging only). */
        fun statusDescription(code: Byte): String =
            when (code) {
                CTAP2_OK -> "CTAP2_OK"
                CTAP2_ERR_INVALID_CBOR -> "CTAP2_ERR_INVALID_CBOR (0x12)"
                CTAP2_ERR_MISSING_PARAMETER -> "CTAP2_ERR_MISSING_PARAMETER (0x14)"
                CTAP2_ERR_NO_CREDENTIALS -> "CTAP2_ERR_NO_CREDENTIALS (0x2E)"
                CTAP2_ERR_NOT_ALLOWED -> "CTAP2_ERR_NOT_ALLOWED (0x36)"
                CTAP2_ERR_UNSUPPORTED_ALGO -> "CTAP2_ERR_UNSUPPORTED_ALGORITHM (0x26)"
                CTAP2_ERR_OPERATION_DENIED -> "CTAP2_ERR_OPERATION_DENIED (0x27)"
                CTAP2_ERR_KEY_STORE_FULL -> "CTAP2_ERR_KEY_STORE_FULL (0x28)"
                CTAP2_ERR_PIN_BLOCKED -> "CTAP2_ERR_PIN_BLOCKED (0x32)"
                CTAP2_ERR_PIN_INVALID -> "CTAP2_ERR_PIN_INVALID (0x31)"
                else -> "UNKNOWN (0x${code.toInt().and(0xFF).toString(16).uppercase()})"
            }
    }
