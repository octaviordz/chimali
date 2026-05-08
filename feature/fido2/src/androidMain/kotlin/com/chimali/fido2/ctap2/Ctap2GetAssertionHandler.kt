package com.chimali.fido2.ctap2

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PrfExtensionInput
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
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
    private val cborCodec: CborCodec,
    private val hmacSecretProcessor: HmacSecretProcessor,
    private val prfKeyDerivation: PrfKeyDerivation,
    private val hidReportParser: HidReportParser,
    private val uiEventBus: Fido2UiEventBus,
    private val getAssertionUseCase: GetAssertionUseCase,
    private val ceremonyLock: CeremonyLock,
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
        private const val CTAP2_ERR_USER_ACTION_TIMEOUT: Byte = 0x2F.toByte()
        private const val CTAP1_ERR_CHANNEL_BUSY: Byte = 0x06 // Returned when a ceremony is already in progress
        private const val CTAP1_ERR_OTHER: Byte = 0x7F.toByte()

        // Commands
        private const val CTAP_CMD_CBOR: Int = 0x10

        // Authenticator Data Constants
        private const val AUTH_DATA_FLAGS_INDEX = 32
        private const val FLAG_ED_BIT = 0x80
        private const val CLIENT_DATA_HASH_SIZE = 32
    }

    /**
     * T087 — Handles a raw CTAP2 GetAssertion CBOR payload.
     *
     * @param cid Channel ID for the response.
     * @param requestBytes CBOR-encoded request body (without the command byte).
     * @return A list of 64-byte HID packets to send back to the host.
     */
    suspend fun handle(
        cid: ByteArray,
        requestBytes: ByteArray,
    ): List<ByteArray> {
        // Fix A: Reject concurrent requests immediately across ALL ceremonies (Assertion or Registration).
        if (!ceremonyLock.tryLock()) {
            Logger.w { "GetAssertion: Authenticator is busy with another ceremony — returning CHANNEL_BUSY" }
            return hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_CHANNEL_BUSY)),
            )
        }
        return try {
            val params = cborCodec.decodeFromFido2Format(requestBytes)
            val options = decodeOptions(params)
            Logger.d {
                "GetAssertion: rpId=${options.rpId} " +
                    "allowCredentials=${options.allowCredentials?.size ?: "discoverable"}"
            }

            val candidates = getAssertionUseCase.findCandidateSummaries(options)
            if (candidates.isEmpty()) {
                Logger.d { "GetAssertion: no matching credentials found — returning NO_CREDENTIALS" }
                return hidReportParser.encodeResponse(
                    com.chimali.fido2.bluetooth.CtapHidMessage(
                        cid,
                        CTAP_CMD_CBOR,
                        byteArrayOf(CTAP2_ERR_NO_CREDENTIALS),
                    ),
                )
            }

            val assertionResult =
                if (shouldGoHeadless(options, candidates)) {
                    Logger.d { "GetAssertion: executing headless fast-path (candidates=${candidates.size})" }
                    getAssertionUseCase(options)
                } else {
                    val deferred = kotlinx.coroutines.CompletableDeferred<Outcome<AssertionObject, DomainError>>()
                    uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(options, deferred))

                    try {
                        kotlinx.coroutines.withTimeout(options.getSafeTimeout()) {
                            deferred.await()
                        }
                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                        Logger.e { "GetAssertion timed out after ${options.getSafeTimeout()}ms" }
                        return hidReportParser.encodeResponse(
                            com.chimali.fido2.bluetooth.CtapHidMessage(
                                cid,
                                CTAP_CMD_CBOR,
                                byteArrayOf(CTAP2_ERR_USER_ACTION_TIMEOUT),
                            ),
                        )
                    }
                }

            when (assertionResult) {
                is Outcome.Success -> {
                    val assertion = assertionResult.data
                    Logger.d { "Assertion success: credId=${assertion.credentialId}" }
                    val responseCbor = encodeResponse(assertion, options)
                    val responsePayload = byteArrayOf(CTAP2_OK) + responseCbor
                    hidReportParser.encodeResponse(
                        com.chimali.fido2.bluetooth
                            .CtapHidMessage(cid, CTAP_CMD_CBOR, responsePayload),
                    )
                }

                is Outcome.Error -> {
                    val error = assertionResult.error
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
                    hidReportParser.encodeResponse(
                        com.chimali.fido2.bluetooth
                            .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(errorCode)),
                    )
                }
            }
        } catch (e: Fido2Exception) {
            Logger.e(e) { "GetAssertion Fido2Exception: ${e.message}" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP2_ERR_PROCESSING)),
            )
        } catch (e: java.io.IOException) {
            Logger.e(e) { "Unexpected IO error during GetAssertion: ${e.message}" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_OTHER)),
            )
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "Invalid argument during GetAssertion: ${e.message}" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_OTHER)),
            )
        } catch (e: IllegalStateException) {
            Logger.e(e) { "Invalid state during GetAssertion: ${e.message}" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_OTHER)),
            )
        } finally {
            ceremonyLock.unlock()
        }
    }

    /**
     * T052: Determines if the ceremony can proceed without user interaction.
     * Fast-path criteria:
     * 1. uv is PREFERRED (or DISCOURAGED) — host doesn't REQUIRE biometric/PIN.
     * 2. Exactly one local credential matches the RP ID and allowList.
     */
    private fun shouldGoHeadless(
        options: GetAssertionOptions,
        candidates: List<CredentialSummary>,
    ): Boolean =
        (options.userVerification != UserVerificationRequirement.REQUIRED) &&
            (candidates.size == 1)

    // ── Decoding ──────────────────────────────────────────────────────────────

    private fun decodeOptions(params: Map<String, Any>): GetAssertionOptions {
        val rpId =
            (params[REQ_RP_ID] as? String)
                ?: throw Fido2Exception.InvalidParameter("Missing rpId (key 0x01)")

        // clientDataHash: real CBOR delivers this as a raw ByteArray.
        // The old JSON-based codec delivered it as a base64 string — handle both.
        val clientDataHash: ByteArray =
            when (val raw = params[REQ_CLIENT_DATA_HASH]) {
                is ByteArray -> {
                    raw
                }

                is String -> {
                    java.util.Base64
                        .getDecoder()
                        .decode(raw)
                }

                else -> {
                    throw Fido2Exception.InvalidParameter("Missing clientDataHash (key 0x02)")
                }
            }
        require(
            clientDataHash.size == CLIENT_DATA_HASH_SIZE,
        ) { "clientDataHash must be $CLIENT_DATA_HASH_SIZE bytes, got ${clientDataHash.size}" }

        // Optional allow-list: array of PublicKeyCredentialDescriptor maps.
        // Credential ID arrives as a CBOR byte-string (ByteArray) in real CTAP2.
        // Legacy / test paths may send it as a Base64url-encoded String; in that
        // case use CredentialId.fromEncoded() — NOT .toByteArray(), which would
        // produce the UTF-8 bytes of the encoded string instead of the raw ID bytes.
        val allowListRaw = params[REQ_ALLOW_LIST] as? List<*>
        val allowCredentials =
            allowListRaw?.mapNotNull { descriptor ->
                (descriptor as? Map<*, *>)?.let { map ->
                    val credentialId: CredentialId? =
                        when (val rawId = map["id"]) {
                            // Standard CTAP2: credential ID is a raw byte-string.
                            is ByteArray -> CredentialId.fromByteArray(rawId)

                            // Legacy / test: credential ID is a Base64url string — decode
                            // via fromEncoded() which is consistent with how the DB stores it.
                            is String -> CredentialId.fromEncoded(rawId)

                            else -> return@mapNotNull null
                        }
                    credentialId?.let { PublicKeyCredentialDescriptor.create(id = it) }
                }
            }

        // options map (key 0x05): {"uv": bool, "up": bool}
        val optionsMap = params[REQ_OPTIONS] as? Map<*, *>
        val isUvRequired = (optionsMap?.get("uv") as? Boolean) ?: false
        val userVerification =
            if (isUvRequired) {
                UserVerificationRequirement.REQUIRED
            } else {
                UserVerificationRequirement.PREFERRED
            }

        // extensions map (key 0x04): e.g. {"hmac-secret": {...}}
        @Suppress("UNCHECKED_CAST")
        val extensions = params[REQ_EXTENSIONS] as? Map<String, Any>

        return GetAssertionOptions(
            rpId = RpId(rpId),
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
                    "id" to desc.id.toByteArray(),
                )
        }

        // T047: Process hmac-secret / PRF extension.
        // Attempt typed PrfExtensionInput parsing (integer keys 1 and 2) first;
        // fall back to HmacSecretProcessor for legacy flat-ByteArray format.
        // T048: >2 salts → PrfExtensionInput constructor throws → null → omit extension.
        // T049: Missing seed → deriveAll returns null → omit extension gracefully.
        val hmacOutput: ByteArray? =
            run {
                val extData = options.extensions?.get(HmacSecretProcessor.EXTENSION_KEY) ?: return@run null
                val selectedCredId = assertion.credentialId

                when (extData) {
                    // Typed CBOR map format: {1 → salt1, 2 → salt2?}
                    is Map<*, *> -> {
                        val salt1 = ((extData[1] ?: extData[1L]) as? ByteArray) ?: return@run null
                        val salt2 = (extData[2] ?: extData[2L]) as? ByteArray
                        val salts = listOfNotNull(salt1, salt2)
                        val prfInput =
                            try {
                                PrfExtensionInput(salts)
                            } catch (e: IllegalArgumentException) {
                                Logger.w { "GetAssertion hmac-secret: invalid PrfExtensionInput — ${e.message}" }
                                return@run null
                            }
                        val prfOutput = prfKeyDerivation.deriveAll(prfInput, selectedCredId)
                        if (prfOutput == null) {
                            Logger.w { "GetAssertion hmac-secret: PRF derivation returned null — omitting (T049)" }
                            return@run null
                        }
                        Logger.d { "GetAssertion PRF: derived ${if (prfOutput.output2 != null) 2 else 1} output(s)" }
                        // Concatenate output1 [|| output2] for legacy authData embedding
                        prfOutput.output1 + (prfOutput.output2 ?: byteArrayOf())
                    }
                    // Legacy flat ByteArray format (32 or 64 bytes)
                    else -> hmacSecretProcessor.process(selectedCredId, extData)
                }
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
                    "id" to user.id.value,
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
