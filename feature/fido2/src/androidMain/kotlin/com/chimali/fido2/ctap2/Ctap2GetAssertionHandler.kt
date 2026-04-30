package com.chimali.fido2.ctap2

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import org.koin.core.annotation.Single

/**
 * T087 — CTAP2 authenticatorGetAssertion command handler.
 *
 * Receives decoded CBOR request bytes from the HID transport, extracts
 * GetAssertion parameters, delegates to [GetAssertionUseCase], then
 * serializes the [AssertionObject] response back to CBOR.
 *
 * ### CTAP2 Request parameters decoded (§6.2):
 * | Key | Type | Field             |
 * |-----|------|-------------------|
 * | 0x01 | text | rpId             |
 * | 0x02 | bytes | clientDataHash |
 * | 0x03 | array | allowList      |
 * | 0x04 | map  | extensions      |
 * | 0x05 | map  | options         |
 * | 0x06 | map  | pinUvAuthParam  |
 */
@Single
class Ctap2GetAssertionHandler(
    private val getAssertionUseCase: GetAssertionUseCase,
    private val cborCodec: CborCodec,
    private val hmacSecretProcessor: HmacSecretProcessor,
) {
    companion object {
        // CTAP2 Request Keys (§6.2)
        private const val REQ_RP_ID = "1"
        private const val REQ_CLIENT_DATA_HASH = "2"
        private const val REQ_ALLOW_LIST = "3"
        private const val REQ_EXTENSIONS = "4"
        private const val REQ_OPTIONS = "5"

        // CTAP2 Response Keys (§6.2)
        private const val RESP_CREDENTIAL = "1"
        private const val RESP_AUTH_DATA = "2"
        private const val RESP_SIGNATURE = "3"
        private const val RESP_USER = "4"
        private const val RESP_NUM_CREDS = "5"

        // Status and Error Codes
        private const val CTAP2_OK: Byte = 0x00
        private const val CTAP2_ERR_PROCESSING: Byte = 0x17
        private const val CTAP2_ERR_OPERATION_DENIED: Byte = 0x29
        private const val CTAP2_ERR_NO_CREDENTIALS: Byte = 0x2E
        private const val CTAP1_ERR_OTHER: Byte = 0x7F.toByte()

        // Authenticator Data Constants
        private const val AUTH_DATA_FLAGS_INDEX = 32
        private const val FLAG_ED_BIT = 0x80
        private const val CLIENT_DATA_HASH_SIZE = 32
    }

    /**
     * T087 — Handles a raw CTAP2 GetAssertion CBOR payload.
     *
     * @param requestBytes CBOR-encoded request body (without the command byte).
     * @return CBOR-encoded response body including the 0x00 status byte prefix.
     */
    suspend fun handle(requestBytes: ByteArray): ByteArray {
        return try {
            val params = cborCodec.decodeFromFido2Format(requestBytes)
            val options = decodeOptions(params)
            Logger.d {
                "GetAssertion: rpId=${options.rpId} " +
                    "allowCredentials=${options.allowCredentials?.size ?: "discoverable"}"
            }

            when (val result = getAssertionUseCase(options)) {
                is Outcome.Success -> {
                    val assertion = result.data
                    Logger.d { "Assertion success: credId=${assertion.credentialId}" }
                    val responseBytes = encodeResponse(assertion, options)
                    byteArrayOf(CTAP2_OK) + responseBytes
                }
                is Outcome.Error -> {
                    val error = result.error
                    // CredentialNotFound is an expected probe response before registration.
                    // All other errors are unexpected and warrant an error-level log.
                    if (error is DomainError.NotFound) {
                        Logger.d { "Assertion failed (expected): ${error.message}" }
                    } else {
                        Logger.e { "Assertion failed: ${error.message}" }
                    }
                    val errorCode: Byte =
                        when (error) {
                            is DomainError.NotFound -> CTAP2_ERR_NO_CREDENTIALS
                            is DomainError.OperationDenied -> CTAP2_ERR_OPERATION_DENIED
                            else -> CTAP1_ERR_OTHER
                        }
                    byteArrayOf(errorCode)
                }
            }
        } catch (e: Fido2Exception) {
            Logger.e(e) { "GetAssertion Fido2Exception: ${e.message}" }
            byteArrayOf(CTAP2_ERR_PROCESSING)
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "GetAssertion handler invalid argument: ${e.message}" }
            byteArrayOf(CTAP2_ERR_PROCESSING)
        }
    }

    // ── Decoding ──────────────────────────────────────────────────────────────

    private fun decodeOptions(params: Map<String, Any>): GetAssertionOptions {
        val rpId =
            params[REQ_RP_ID] as? String
                ?: throw Fido2Exception.InvalidParameter("Missing rpId (key 0x01)")

        // clientDataHash: real CBOR delivers this as a raw ByteArray.
        // The old JSON-based codec delivered it as a base64 string — handle both.
        val clientDataHash: ByteArray =
            when (val raw = params[REQ_CLIENT_DATA_HASH]) {
                is ByteArray -> raw
                is String -> java.util.Base64.getDecoder().decode(raw)
                else -> throw Fido2Exception.InvalidParameter("Missing clientDataHash (key 0x02)")
            }
        require(
            clientDataHash.size == CLIENT_DATA_HASH_SIZE,
        ) { "clientDataHash must be $CLIENT_DATA_HASH_SIZE bytes, got ${clientDataHash.size}" }

        // Optional allow-list: array of PublicKeyCredentialDescriptor maps.
        // credential ID is a byte string in CBOR → ByteArray, or legacy base64 String.
        val allowListRaw = params[REQ_ALLOW_LIST] as? List<*>
        val allowCredentials =
            allowListRaw?.mapNotNull { descriptor ->
                (descriptor as? Map<*, *>)?.let { map ->
                    val idBytes: ByteArray =
                        when (val rawId = map["id"]) {
                            is ByteArray -> rawId
                            is String -> rawId.toByteArray() // legacy / base64
                            else -> return@mapNotNull null
                        }
                    PublicKeyCredentialDescriptor.create(id = idBytes)
                }
            }

        // options map (key 0x05): {"uv": bool, "up": bool}
        val optionsMap = params[REQ_OPTIONS] as? Map<*, *>
        val uvRaw = optionsMap?.get("uv") as? Boolean ?: false
        val userVerification =
            if (uvRaw) {
                UserVerificationRequirement.REQUIRED
            } else {
                UserVerificationRequirement.PREFERRED
            }

        // extensions map (key 0x04): e.g. {"hmac-secret": {...}}
        @Suppress("UNCHECKED_CAST")
        val extensions = params[REQ_EXTENSIONS] as? Map<String, Any>

        return GetAssertionOptions(
            rpId = rpId,
            clientDataHash = clientDataHash,
            allowCredentials = allowCredentials,
            userVerification = userVerification,
            extensions = extensions,
        )
    }

    // ── T087a: hmac-secret extension + T088: Response encoding ─────────────────

    /**
     * Encodes the GetAssertion response.
     *
     * If the request contained an `hmac-secret` extension, the extension output
     * is appended to authData (with the ED flag set) before signing is complete.
     * Note: The assertion's authData already carries the signature over the base
     * authData; hmac-secret output is added as a separate extensions map in authData.
     * Per CTAP2.1 §12.4, the ED bit and extensions CBOR are appended to authData
     * after signing, and included in the response map as key 0x02.
     */
    private suspend fun encodeResponse(
        assertion: AssertionObject,
        options: GetAssertionOptions,
    ): ByteArray {
        val responseMap = mutableMapOf<String, Any>()

        // 0x01 — credential descriptor
        assertion.credential?.let { desc ->
            responseMap[RESP_CREDENTIAL] =
                mapOf(
                    "type" to "public-key",
                    "id" to desc.id,
                )
        }

        // T087a: Process hmac-secret extension and build authData with extensions
        val hmacOutput =
            if (hmacSecretProcessor.isPresent(options.extensions)) {
                val extensionData = options.extensions?.get(HmacSecretProcessor.EXTENSION_KEY)
                val selectedCredId = assertion.credentialId
                hmacSecretProcessor.process(selectedCredId, extensionData)
            } else {
                null
            }

        // Extend authData with extensions CBOR if hmac-secret output is present
        val finalAuthData =
            if (hmacOutput != null) {
                val extMap = hmacSecretProcessor.buildAuthDataExtensions(hmacOutput)
                if (extMap != null) {
                    // Set the ED bit (0x80) in the flags byte (authData[32])
                    val extAuthData = assertion.authData.clone()
                    val flags = extAuthData[AUTH_DATA_FLAGS_INDEX].toInt()
                    extAuthData[AUTH_DATA_FLAGS_INDEX] = (flags or FLAG_ED_BIT).toByte()
                    // Append CBOR-encoded extensions to authData
                    val extCbor = cborCodec.encodeToFido2Format(extMap.mapKeys { it.key })
                    extAuthData + extCbor
                } else {
                    assertion.authData
                }
            } else {
                assertion.authData
            }

        // 0x02 — authData (with optional extension data)
        responseMap[RESP_AUTH_DATA] = finalAuthData

        // 0x03 — DER-encoded ECDSA signature: raw bytes
        responseMap[RESP_SIGNATURE] = assertion.signature

        // 0x04 — user entity (discoverable credential flow)
        assertion.user?.let { user ->
            responseMap[RESP_USER] =
                mapOf(
                    "id" to user.id,
                    "name" to user.name,
                    "displayName" to user.displayName.ifEmpty { user.name },
                )
        }

        // 0x05 — numberOfCredentials (omit if == 1)
        assertion.numberOfCredentials?.let { n ->
            if (n > 1) responseMap[RESP_NUM_CREDS] = n
        }

        return cborCodec.encodeToFido2Format(responseMap)
    }
}
