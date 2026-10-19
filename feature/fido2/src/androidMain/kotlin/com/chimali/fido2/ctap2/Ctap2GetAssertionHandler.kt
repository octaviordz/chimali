package com.chimali.fido2.ctap2

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialProtectionPolicy
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PrfExtensionInput
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.util.performance.LatencyProfiler
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
@Req("NFR-PERF-030", "NFR-SEC-041")
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
        private const val CTAP2_ERR_INVALID_CBOR: Byte = 0x12
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
     * @return A list of 62-byte HID packets to send back to the host.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
        var decodedFields: Map<String, Any>? = null
        var ownedOptions: GetAssertionOptions? = null
        return try {
            val params = cborCodec.decodeSensitiveRequest(requestBytes)
            decodedFields = params
            val options = decodeOptions(params)
            ownedOptions = options
            Logger.d {
                "GetAssertion request admitted: " +
                    "allowCredentials=${options.allowCredentials?.size ?: "discoverable"}"
            }

            val candidates = getAssertionUseCase.findCandidateSummaries(options)
            try {
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
                        try {
                            kotlinx.coroutines.withTimeout(options.getSafeTimeout()) {
                                getAssertionUseCase.invokeHeadless(options, candidates)
                            }
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            Logger.e { "GetAssertion timed out after ${options.getSafeTimeout()}ms" }
                            return hidReportParser.encodeResponse(
                                com.chimali.fido2.bluetooth
                                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP2_ERR_USER_ACTION_TIMEOUT)),
                            )
                        }
                    } else {
                        val deferred = kotlinx.coroutines.CompletableDeferred<Outcome<AssertionObject, DomainError>>()
                        val userInteractionCompleted = kotlinx.coroutines.CompletableDeferred<Unit>()
                        LatencyProfiler.startUserInteraction("GetAssertion")
                        uiEventBus.dispatch(
                            Fido2UiEvent.AuthenticationRequested(options, deferred, userInteractionCompleted),
                        )

                        try {
                            LatencyProfiler.untilUserInteractionCompletes("GetAssertion", userInteractionCompleted) {
                                kotlinx.coroutines.withTimeout(options.getSafeTimeout()) {
                                    deferred.await()
                                }
                            }
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            Logger.e { "GetAssertion timed out after ${options.getSafeTimeout()}ms" }
                            deferred.cancel()
                            return hidReportParser.encodeResponse(
                                com.chimali.fido2.bluetooth.CtapHidMessage(
                                    cid,
                                    CTAP_CMD_CBOR,
                                    byteArrayOf(CTAP2_ERR_USER_ACTION_TIMEOUT),
                                ),
                            )
                        } catch (cancellation: kotlinx.coroutines.CancellationException) {
                            deferred.cancel(cancellation)
                            throw cancellation
                        }
                    }

                when (assertionResult) {
                    is Outcome.Success -> {
                        val assertion = assertionResult.data
                        Logger.d { "Assertion response ready" }
                        var responseCbor: ByteArray? = null
                        var responsePayload: ByteArray? = null
                        try {
                            responseCbor = encodeResponse(assertion, options)
                            responsePayload = byteArrayOf(CTAP2_OK) + responseCbor
                            hidReportParser.encodeResponse(
                                com.chimali.fido2.bluetooth
                                    .CtapHidMessage(cid, CTAP_CMD_CBOR, responsePayload),
                            )
                        } finally {
                            responseCbor?.fill(0)
                            responsePayload?.fill(0)
                            assertion.authData.fill(0)
                            assertion.signature.fill(0)
                        }
                    }

                    is Outcome.Error -> {
                        val error = assertionResult.error
                        // CredentialNotFound is an expected probe response before registration.
                        // All other errors are unexpected and warrant an error-level log.
                        if (error is DomainError.NotFound) {
                            Logger.d { "Assertion failed: credential not found" }
                        } else {
                            Logger.e { "Assertion failed during GetAssertion" }
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
            } finally {
                candidates.forEach(CredentialSummary::eraseSensitiveText)
            }
        } catch (_: Fido2Exception.InvalidFormatException) {
            Logger.e { "GetAssertion failed with a FIDO2 error" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP2_ERR_INVALID_CBOR)),
            )
        } catch (_: Fido2Exception.InvalidParameter) {
            Logger.e { "GetAssertion rejected an invalid parameter" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP2_ERR_INVALID_CBOR)),
            )
        } catch (e: Fido2Exception) {
            Logger.e { "GetAssertion failed with a FIDO2 error" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP2_ERR_PROCESSING)),
            )
        } catch (e: java.io.IOException) {
            Logger.e { "GetAssertion failed with an I/O error" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_OTHER)),
            )
        } catch (e: IllegalArgumentException) {
            Logger.e { "GetAssertion rejected an invalid argument" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_OTHER)),
            )
        } catch (e: IllegalStateException) {
            Logger.e { "GetAssertion encountered an invalid state" }
            hidReportParser.encodeResponse(
                com.chimali.fido2.bluetooth
                    .CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_OTHER)),
            )
        } finally {
            ownedOptions?.eraseSensitiveText()
            cborCodec.eraseDecodedRequestValues(decodedFields)
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
            (candidates.size == 1) &&
            CredentialProtectionPolicy.isSupported(candidates.single().credProtectPolicy) &&
            (candidates.single().credProtectPolicy != CredentialProtectionPolicy.USER_VERIFICATION_REQUIRED)

    // ── Decoding ──────────────────────────────────────────────────────────────

    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Suppress("ThrowsCount")
    private fun decodeOptions(params: Map<String, Any>): GetAssertionOptions {
        var rpIdOwner: SensitiveCredentialText? =
            when (val value = params[REQ_RP_ID]) {
                is SensitiveCredentialText -> value
                is String -> SensitiveCredentialText.copyOf(value)
                else -> null
            }
                ?: throw Fido2Exception.InvalidParameter("Missing rpId (key 0x01)")
        try {
            // clientDataHash: real CBOR delivers this as a raw ByteArray.
            // The old JSON-based codec delivered it as a base64 string — handle both.
            val clientDataHash: ByteArray =
                when (val raw = params[REQ_CLIENT_DATA_HASH]) {
                    is ByteArray -> {
                        raw
                    }

                    is SensitiveCredentialText -> {
                        java.util.Base64
                            .getDecoder()
                            .decode(raw.toBoundaryString())
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
            val allowCredentials =
                if (!params.containsKey(REQ_ALLOW_LIST)) {
                    null
                } else {
                    val rawList =
                        params[REQ_ALLOW_LIST] as? List<*>
                            ?: throw Fido2Exception.InvalidFormatException("Invalid GetAssertion allowList")
                    rawList.map { descriptor ->
                        val map =
                            descriptor as? Map<*, *>
                                ?: throw Fido2Exception.InvalidFormatException(
                                    "Invalid GetAssertion credential descriptor",
                                )
                        val credentialId =
                            try {
                                when (val rawId = map["id"]) {
                                    is ByteArray -> CredentialId.fromByteArray(rawId)
                                    is String -> CredentialId.fromEncoded(rawId)
                                    else -> throw Fido2Exception.InvalidFormatException(
                                        "Invalid GetAssertion credential ID",
                                    )
                                }
                            } catch (_: IllegalArgumentException) {
                                throw Fido2Exception.InvalidFormatException(
                                    "Invalid GetAssertion credential ID",
                                )
                            }
                        PublicKeyCredentialDescriptor.create(id = credentialId)
                    }
                }

            // options map (key 0x05): {"uv": bool, "up": bool}
            val optionsMap =
                if (!params.containsKey(REQ_OPTIONS)) {
                    null
                } else {
                    params[REQ_OPTIONS] as? Map<*, *>
                        ?: throw Fido2Exception.InvalidFormatException("Invalid GetAssertion options")
                }
            val isUvRequired =
                if (optionsMap?.containsKey("uv") == true) {
                    optionsMap["uv"] as? Boolean
                        ?: throw Fido2Exception.InvalidFormatException("Invalid GetAssertion UV option")
                } else {
                    false
                }
            val userVerification =
                if (isUvRequired) {
                    UserVerificationRequirement.REQUIRED
                } else {
                    UserVerificationRequirement.PREFERRED
                }

            // extensions map (key 0x04): e.g. {"hmac-secret": {...}}
            @Suppress("UNCHECKED_CAST")
            val extensions = params[REQ_EXTENSIONS] as? Map<String, Any>

            val options =
                GetAssertionOptions.fromSensitiveRpId(
                    rpId = requireNotNull(rpIdOwner),
                    clientDataHash = clientDataHash,
                    allowCredentials = allowCredentials,
                    userVerification = userVerification,
                    extensions = extensions,
                )
            rpIdOwner = null
            return options
        } finally {
            rpIdOwner?.erase()
        }
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
        var prfOutput: com.chimali.fido2.domain.model.PrfExtensionOutput? = null
        var hmacOutput: ByteArray? = null
        var extensionCbor: ByteArray? = null
        var extensionAuthData: ByteArray? = null
        var finalAuthData: ByteArray? = null

        try {
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
            hmacOutput =
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
                                    Logger.w {
                                        "GetAssertion hmac-secret input was invalid (${e::class.simpleName})"
                                    }
                                    return@run null
                                }
                            val derivedOutput = prfKeyDerivation.deriveAll(prfInput, selectedCredId)
                            if (derivedOutput == null) {
                                Logger.w { "GetAssertion hmac-secret: PRF derivation returned null — omitting (T049)" }
                                return@run null
                            }
                            prfOutput = derivedOutput
                            Logger.d {
                                "GetAssertion PRF: derived ${if (derivedOutput.output2 != null) 2 else 1} output(s)"
                            }
                            // Concatenate output1 [|| output2] for legacy authData embedding.
                            // The typed outputs stay owned here and are erased in the finalizer.
                            derivedOutput.output1 + (derivedOutput.output2 ?: byteArrayOf())
                        }
                        // Legacy flat ByteArray format (32 or 64 bytes)
                        else -> hmacSecretProcessor.process(selectedCredId, extData)
                    }
                }

            // Extend authData with extensions CBOR if hmac-secret output is present
            finalAuthData =
                if (hmacOutput != null) {
                    val extMap = hmacSecretProcessor.buildAuthDataExtensions(hmacOutput)
                    if (extMap != null) {
                        // Set the ED bit (0x80) in the flags byte (authData[32])
                        extensionAuthData = assertion.authData.clone()
                        val flags = requireNotNull(extensionAuthData)[AUTH_DATA_FLAGS_INDEX].toInt()
                        requireNotNull(extensionAuthData)[AUTH_DATA_FLAGS_INDEX] = (flags or FLAG_ED_BIT).toByte()
                        // Append CBOR-encoded extensions to authData
                        extensionCbor = cborCodec.encodeToFido2Format(extMap.mapKeys { it.key })
                        requireNotNull(extensionAuthData) + requireNotNull(extensionCbor)
                    } else {
                        assertion.authData
                    }
                } else {
                    assertion.authData
                }

            // 0x02 — authData (with optional extension data)
            responseMap[RESP_AUTH_DATA] = requireNotNull(finalAuthData)

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
        } finally {
            prfOutput?.clear()
            hmacOutput?.fill(0)
            extensionCbor?.fill(0)
            extensionAuthData?.fill(0)
            if (finalAuthData !== assertion.authData) finalAuthData?.fill(0)
        }
    }
}
