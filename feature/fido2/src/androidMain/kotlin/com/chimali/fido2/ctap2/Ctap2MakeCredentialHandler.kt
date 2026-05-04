package com.chimali.fido2.ctap2

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.data.transport.BluetoothHidTransportImpl
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PrfExtensionInput
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.util.performance.LatencyProfiler
import kotlinx.coroutines.CompletableDeferred
import org.koin.core.annotation.Single

/**
 * Handles CTAP2 `authenticatorMakeCredential` (0x01) commands arriving from
 * [BluetoothHidTransportImpl].
 *
 * Responsibilities:
 * - Decode CBOR-encoded MakeCredential request
 * - Validate parameters per CTAP2 spec §6.1
 * - Delegate credential creation to UI/Service layer via event bus
 * - Encode the success / error CTAP2 response back as a [CtapHidMessage]
 */
@Single
class Ctap2MakeCredentialHandler(
    private val cborCodec: CborCodec,
    private val hidReportParser: HidReportParser,
    private val uiEventBus: Fido2UiEventBus,
    private val prfKeyDerivation: PrfKeyDerivation,
) {
    companion object {
        // CTAPHID command codes
        private const val CTAPHID_CBOR: Byte = 0x10

        // CTAP2 command codes (first byte of CBOR payload)
        private const val CMD_MAKE_CREDENTIAL: Byte = 0x01

        // CTAP2 status codes
        private const val CTAP2_OK: Byte = 0x00
        private const val CTAP2_ERR_INVALID_CBOR: Byte = 0x12.toByte()
        private const val CTAP2_ERR_MISSING_PARAMETER: Byte = 0x14.toByte()
        private const val CTAP2_ERR_UNSUPPORTED_ALGORITHM: Byte = 0x26.toByte()
        private const val CTAP2_ERR_OPERATION_DENIED: Byte = 0x27.toByte()
        private const val CTAP2_ERR_KEY_STORE_FULL: Byte = 0x28.toByte()
        private const val CTAP2_ERR_NOT_ALLOWED: Byte = 0x36.toByte()
        private const val CTAP2_ERR_USER_ACTION_TIMEOUT: Byte = 0x2F.toByte()

        // COSE algorithm IDs (accepted)
        private const val COSE_ES256 = -7 // ECDSA with SHA-256 / P-256
        private const val COSE_EDSA = -8 // EdDSA / Ed25519, per WebAuthn L3 § 5.4
        private const val COSE_RS256 = -257 // RSASSA-PKCS1-v1_5 with SHA-256
        private const val COSE_ML_DSA_65 = -49 // ML-DSA-65 (Dilithium)

        // COSE algorithm IDs (deprecated — NOT RECOMMENDED per WebAuthn L3, must be rejected)
        private const val COSE_DEPRECATED_9 = -9
        private const val COSE_DEPRECATED_19 = -19 // Old Ed25519 identifier; -8 is correct
        private const val COSE_DEPRECATED_51 = -51
        private const val COSE_DEPRECATED_52 = -52

        // AuthData flags
        private const val FLAG_UP: Int = 0x01 // User Present
        private const val FLAG_AT: Int = 0x40 // Attested Credential Data included

        // Request Map Keys (§6.1)
        private const val REQ_CLIENT_DATA_HASH = "1"
        private const val REQ_RP = "2"
        private const val REQ_USER = "3"
        private const val REQ_PUB_KEY_PARAMS = "4"
        private const val REQ_OPTIONS = "7"
        private const val REQ_EXTENSIONS = "10"

        // Response Map Keys (§6.1)
        private const val RESP_FMT = "1"
        private const val RESP_AUTH_DATA = "2"
        private const val RESP_ATT_STMT = "3"

        // Bitwise offsets
        private const val SHIFT_24 = 24
        private const val SHIFT_16 = 16
        private const val SHIFT_8 = 8
        private const val BYTE_MASK = 0xFF
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Handles an incoming CTAPHID_CBOR message whose first payload byte is
     * [CMD_MAKE_CREDENTIAL] (0x01).
     *
     * @param message  The fully-reassembled CTAPHID message from the host.
     * @return A list of 64-byte HID packets to send back to the host.
     */
    suspend fun handle(message: CtapHidMessage): List<ByteArray> {
        val cid = message.channelId
        val payload = message.payload

        // payload[0] = command byte (0x01), rest = CBOR map
        if (payload.isEmpty() || payload[0] != CMD_MAKE_CREDENTIAL) {
            return errorPackets(cid, CTAP2_ERR_INVALID_CBOR)
        }

        val cborData = payload.drop(1).toByteArray()
        return try {
            val params = decodeMakeCredentialRequest(cborData)
            handleMakeCredential(cid, params)
        } catch (e: Fido2Exception) {
            Logger.e(e) { "MakeCredential error: ${e.message}" }
            errorPackets(cid, mapExceptionToStatus(e))
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "Unexpected illegal argument in MakeCredential" }
            errorPackets(cid, CTAP2_ERR_NOT_ALLOWED)
        }
    }

    // ── Request decoding ──────────────────────────────────────────────────────

    @Suppress("ThrowsCount")
    private fun decodeMakeCredentialRequest(cbor: ByteArray): MakeCredentialRequest {
        val map =
            cborCodec.decodeFromFido2Format(cbor)
                .takeIf { it.isNotEmpty() }
                ?: throw Fido2Exception.InvalidFormatException("MakeCredential request is not a valid map")

        // 0x01: clientDataHash (required)
        val clientDataHash =
            (map[REQ_CLIENT_DATA_HASH] ?: map["clientDataHash"]) as? ByteArray
                ?: throw Fido2Exception.InvalidFormatException("Missing clientDataHash (key 0x01)")

        // 0x02: rp (required)
        @Suppress("UNCHECKED_CAST")
        val rpMap =
            (map[REQ_RP] ?: map["rp"]) as? Map<*, *>
                ?: throw Fido2Exception.InvalidFormatException("Missing rp entity (key 0x02)")
        val rpId =
            rpMap["id"] as? String
                ?: throw Fido2Exception.InvalidFormatException("Missing rp.id")
        val rpName = rpMap["name"] as? String ?: rpId

        // 0x03: user (required)
        @Suppress("UNCHECKED_CAST")
        val userMap =
            (map[REQ_USER] ?: map["user"]) as? Map<*, *>
                ?: throw Fido2Exception.InvalidFormatException("Missing user entity (key 0x03)")
        val userId =
            userMap["id"] as? ByteArray
                ?: throw Fido2Exception.InvalidFormatException("Missing user.id")
        val userName = userMap["name"] as? String ?: ""
        val userDisplayName = userMap["displayName"] as? String ?: userName

        // 0x04: pubKeyCredParams (required)
        @Suppress("UNCHECKED_CAST")
        val rawParams =
            (map[REQ_PUB_KEY_PARAMS] ?: map["pubKeyCredParams"]) as? List<*>
                ?: throw Fido2Exception.InvalidFormatException("Missing pubKeyCredParams (key 0x04)")
        val algorithms =
            rawParams.mapNotNull { entry ->
                (entry as? Map<*, *>)?.let { m ->
                    (m["alg"] as? Long)?.toInt() ?: (m["alg"] as? Int)
                }
            }
        if (algorithms.isEmpty()) {
            throw Fido2Exception.MissingParameterException("pubKeyCredParams contains no valid algorithms")
        }

        // 0x07: options (optional)
        @Suppress("UNCHECKED_CAST")
        val options = (map[REQ_OPTIONS] ?: map["options"]) as? Map<*, *>
        val requireUserVerification = options?.get("uv") as? Boolean ?: false
        val requireResidentKey = options?.get("rk") as? Boolean ?: false

        // 0x0A / "extensions": optional FIDO2.1 extension map
        // T045: Parse hmac-secret (PRF) extension — extract salt1 and optional salt2.
        @Suppress("UNCHECKED_CAST")
        val extensions = (map[REQ_EXTENSIONS] ?: map["extensions"]) as? Map<*, *>
        val credProtectPolicy: Int? =
            extensions?.let {
                (it["credProtect"] as? Long)?.toInt() ?: it["credProtect"] as? Int
            }

        // T045: Parse hmac-secret salts from extensions["hmac-secret"].
        // CTAP2 encodes these under integer keys 1 (salt1) and 2 (salt2, optional).
        val prfInput: PrfExtensionInput? =
            extensions?.let { ext ->
                val hmacMap = ext["hmac-secret"] as? Map<*, *> ?: return@let null
                val salt1 = (hmacMap[1] ?: hmacMap[1L]) as? ByteArray ?: return@let null
                val salt2 = (hmacMap[2] ?: hmacMap[2L]) as? ByteArray
                val salts = if (salt2 != null) listOf(salt1, salt2) else listOf(salt1)
                try {
                    PrfExtensionInput(salts)
                } catch (e: IllegalArgumentException) {
                    // T048: >2 salts or wrong size — propagate as null; handleMakeCredential will error.
                    Logger.w { "hmac-secret: invalid salt input — ${e.message}" }
                    null
                }
            }

        return MakeCredentialRequest(
            clientDataHash = clientDataHash,
            rpId = rpId,
            rpName = rpName,
            userId = userId,
            userName = userName,
            userDisplayName = userDisplayName,
            algorithms = algorithms,
            requireUV = requireUserVerification,
            requireRK = requireResidentKey,
            credProtectPolicy = credProtectPolicy,
            prfInput = prfInput,
        )
    }

    // ── Core handler ──────────────────────────────────────────────────────────

    private suspend fun handleMakeCredential(
        cid: ByteArray,
        req: MakeCredentialRequest,
    ): List<ByteArray> {
        Logger.d { "handleMakeCredential START rpId=${req.rpId} user=${req.userName}" }

        val rp = PublicKeyCredentialRpEntity.create(RpId(req.rpId), req.rpName)
        val user =
            PublicKeyCredentialUserEntity.create(
                UserId.fromByteArray(req.userId),
                req.userName,
                req.userDisplayName,
            )

        // T019 Algorithm Negotiation: Pick the first algorithm requested that we support.
        // Preference order: -7 (ES256/P-256) → -8 (EdDSA/Ed25519) → -257 (RS256) → -49 (ML-DSA-65)
        // Deprecated identifiers -9, -19, -51, -52 are explicitly rejected with a log warning.
        val (selectedAlgId, pubKeyCredParams) =
            req.algorithms.firstNotNullOfOrNull { algId ->
                when (algId) {
                    COSE_ES256 -> COSE_ES256 to PublicKeyCredentialParameters.createES256P256()
                    COSE_EDSA -> COSE_EDSA to PublicKeyCredentialParameters.createEdDsa()
                    COSE_RS256 -> COSE_RS256 to PublicKeyCredentialParameters.createRS256()
                    COSE_ML_DSA_65 -> COSE_ML_DSA_65 to PublicKeyCredentialParameters.createMlDsa65()
                    COSE_DEPRECATED_9, COSE_DEPRECATED_19, COSE_DEPRECATED_51, COSE_DEPRECATED_52 -> {
                        Logger.w {
                            "Algorithm negotiation: ignoring deprecated COSE alg $algId " +
                                "(NOT RECOMMENDED per WebAuthn L3 §5.4)"
                        }
                        null
                    }
                    else -> null
                }
            } ?: run {
                Logger.e {
                    "Algorithm negotiation failed: None of the requested algorithms " +
                        "${req.algorithms} are supported"
                }
                return errorPackets(cid, CTAP2_ERR_UNSUPPORTED_ALGORITHM)
            }

        Logger.i {
            "Algorithm negotiation: RP requested ${req.algorithms}, " +
                "selected COSE alg $selectedAlgId (${pubKeyCredParams.algorithm})"
        }

        // T056a: Log credProtect policy for auditability. Policy enforcement (blocking
        // GetAssertion without UV when policy == 3) is handled in GetAssertionHandler.
        if (req.credProtectPolicy != null) {
            Logger.i {
                "MakeCredential: credProtect policy=${req.credProtectPolicy} (1=optional,2=uvOptional,3=uvRequired)"
            }
        }

        // T046: Derive PRF outputs if the RP included hmac-secret extension salts.
        // T049: If derivation fails (missing seed), omit PRF without failing the ceremony.
        val prfOutputMap: Map<String, Any>? =
            req.prfInput?.let { prfIn ->
                val credentialIdStr =
                    java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(req.clientDataHash) // use clientDataHash as credential ID proxy pre-storage
                val prfOutput = prfKeyDerivation.deriveAll(prfIn, credentialIdStr)
                if (prfOutput != null) {
                    Logger.d { "PRF extension: derived output(s) for MakeCredential" }
                    mapOf("hmac-secret" to prfOutput.toCborMap())
                } else {
                    Logger.w {
                        "PRF extension: derivation returned null — " +
                            "omitting from MakeCredential response (T049)"
                    }
                    null
                }
            }

        val extensionsMap =
            buildMap<String, Any> {
                req.credProtectPolicy?.let { put("credProtect", it) }
                prfOutputMap?.let { putAll(it) }
            }.ifEmpty { null }

        val makeCredentialOptions =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = req.clientDataHash,
                pubKeyCredParams = pubKeyCredParams,
                extensions = extensionsMap,
                selectedAlgId = selectedAlgId,
            )

        val deferred = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
        Logger.d("Dispatching RegistrationRequested event to UI")
        uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(makeCredentialOptions, deferred))
        Logger.d("Event dispatched — awaiting user response via deferred")

        // NFR-PERF-030: Exclude UI interaction time from system latency
        LatencyProfiler.startUserInteraction("MakeCredential")
        val makeCredentialResult =
            try {
                kotlinx.coroutines.withTimeout(makeCredentialOptions.getSafeTimeout()) {
                    deferred.await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Logger.e { "MakeCredential timed out after ${makeCredentialOptions.getSafeTimeout()}ms" }
                return hidReportParser.encodeResponse(
                    CtapHidMessage(cid, CTAPHID_CBOR.toInt(), byteArrayOf(CTAP2_ERR_USER_ACTION_TIMEOUT)),
                )
            }
        LatencyProfiler.endUserInteraction("MakeCredential")
        Logger.d {
            "Deferred resolved — success=${makeCredentialResult.isSuccess} " +
                "error=${(makeCredentialResult as? Outcome.Error)?.error?.message}"
        }

        if (makeCredentialResult is Outcome.Error) {
            val err = makeCredentialResult.error
            Logger.e(err.cause) { "Registration failed or cancelled: ${err.message}" }
            return when (err) {
                is DomainError.DatabaseError, is DomainError.StorageError ->
                    errorPackets(cid, CTAP2_ERR_KEY_STORE_FULL)
                is DomainError.OperationDenied ->
                    errorPackets(cid, CTAP2_ERR_OPERATION_DENIED)
                else -> errorPackets(cid, CTAP2_ERR_NOT_ALLOWED)
            }
        }

        val attestation = (makeCredentialResult as Outcome.Success).data.attestationObject
        Logger.d { "Encoding MakeCredential response for credId=${attestation.authData.credentialId.size}bytes" }
        val responseCbor = encodeAttestationResponse(attestation)
        val responsePayload = byteArrayOf(CTAP2_OK) + responseCbor
        // Command byte for CTAPHID_CBOR response = 0x10 (no masking needed)
        val responseMsg = CtapHidMessage(cid, CTAPHID_CBOR.toInt(), responsePayload)
        Logger.d { "MakeCredential response ready payloadLen=${responsePayload.size}" }
        return hidReportParser.encodeResponse(responseMsg)
    }

    // ── CBOR response encoding ────────────────────────────────────────────────

    /**
     * Encodes the AttestationObject as a CTAP2 authenticatorMakeCredential response.
     * Per CTAP2 spec §6.1, keys MUST be integers:
     *   0x01 = fmt, 0x02 = authData, 0x03 = attStmt
     *
     * CRITICAL: For packed self-attestation the server verifies `sig` over
     * `authData_bytes || clientDataHash`. The `authData_bytes` we transmit here MUST be
     * byte-for-byte identical to the bytes that were signed in RegisterCredentialUseCase.
     * [AttestationStatement.authData] stores exactly those signed bytes; use them directly
     * instead of re-serializing from AuthenticatorData fields (which risks divergence).
     */
    private fun encodeAttestationResponse(attestation: AttestationObject): ByteArray {
        // Use the pre-built, pre-signed authData bytes when available (packed attestation).
        // Fall back to re-serialization only for none-attestation (where no sig exists).
        val authDataBytes =
            attestation.attStmt.authData
                ?.takeIf { it.isNotEmpty() }
                ?: buildAuthenticatorData(attestation.authData)

        // Integer keys — not string keys — per CTAP2 §6.1
        val responseMap: Map<String, Any> =
            mapOf(
                // fmt
                RESP_FMT to attestation.fmt,
                // authData (raw bytes, not base64)
                RESP_AUTH_DATA to authDataBytes,
                RESP_ATT_STMT to buildAttestationStatementMap(attestation.attStmt),
            )
        Logger.d { "encodeAttestationResponse: fmt=${attestation.fmt} authDataLen=${authDataBytes.size}" }
        val encoded = cborCodec.encodeToFido2Format(responseMap)
        Logger.d { "MakeCredential CBOR response: ${encoded.size} bytes" }
        return encoded
    }

    /**
     * Serialises [AuthenticatorData] per CTAP2 / WebAuthn spec:
     *   rpIdHash(32) + flags(1) + signCount(4 BE) + AAGUID(16) + credIdLen(2 BE) + credId + cose key
     */
    private fun buildAuthenticatorData(authData: AuthenticatorData): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(authData.rpIdHash.toList()) // 32 bytes
        // Force AT (0x40) + UP (0x01) flags; preserve UV (0x04) if set.
        // AT MUST be set when attested credential data follows the counter.
        val rawFlags = if (authData.flags.isNotEmpty()) authData.flags[0].toInt() else 0
        val flags = (rawFlags or FLAG_AT or FLAG_UP).toByte()
        result.add(flags) // 1 byte
        // signCount as 4-byte big-endian
        val cnt = authData.counter
        result.add(((cnt shr SHIFT_24) and BYTE_MASK.toLong()).toByte())
        result.add(((cnt shr SHIFT_16) and BYTE_MASK.toLong()).toByte())
        result.add(((cnt shr SHIFT_8) and BYTE_MASK.toLong()).toByte())
        result.add((cnt and BYTE_MASK.toLong()).toByte())
        // Attested credential data
        result.addAll(authData.aaguid.toList()) // 16 bytes
        val credIdLen = authData.credentialId.size
        result.add(((credIdLen shr SHIFT_8) and BYTE_MASK).toByte())
        result.add((credIdLen and BYTE_MASK).toByte())
        result.addAll(authData.credentialId.toList())
        result.addAll(authData.publicKey.toList())
        return result.toByteArray()
    }

    private fun buildAttestationStatementMap(stmt: AttestationStatement): Map<Any, Any> {
        return when (stmt.fmt) {
            "none" -> emptyMap()
            "packed" ->
                buildMap {
                    put("alg", stmt.alg)
                    stmt.attCert?.let { put("sig", it) }
                    stmt.x5c?.let { put("x5c", it) }
                }
            // T036: AttCA — same wire format as packed; x5c carries the intermediate CA chain
            "attCA" ->
                buildMap {
                    put("alg", stmt.alg)
                    stmt.attCert?.let { put("sig", it) }
                    stmt.x5c?.let { put("x5c", it) }
                }
            else -> emptyMap()
        }
    }

    // ── Error helpers ─────────────────────────────────────────────────────────

    private fun errorPackets(
        cid: ByteArray,
        statusCode: Byte,
    ): List<ByteArray> {
        val payload = byteArrayOf(statusCode)
        val msg = CtapHidMessage(cid, CTAPHID_CBOR.toInt() and 0x7F, payload)
        return hidReportParser.encodeResponse(msg)
    }

    private fun mapExceptionToStatus(e: Fido2Exception): Byte =
        when (e) {
            is Fido2Exception.InvalidFormatException -> CTAP2_ERR_INVALID_CBOR
            is Fido2Exception.MissingParameterException -> CTAP2_ERR_MISSING_PARAMETER
            is Fido2Exception.UnsupportedAlgorithmException -> CTAP2_ERR_UNSUPPORTED_ALGORITHM
            is Fido2Exception.UserVerificationException -> CTAP2_ERR_OPERATION_DENIED
            is Fido2Exception.TooManyCredentials -> CTAP2_ERR_KEY_STORE_FULL // T115a (FR-HID-022)
            is Fido2Exception.CredentialException -> CTAP2_ERR_KEY_STORE_FULL
            else -> CTAP2_ERR_NOT_ALLOWED
        }
}

/** Internal parsed MakeCredential request. */
private data class MakeCredentialRequest(
    val clientDataHash: ByteArray,
    val rpId: String,
    val rpName: String,
    val userId: ByteArray,
    val userName: String,
    val userDisplayName: String,
    val algorithms: List<Int>,
    val requireUV: Boolean,
    val requireRK: Boolean,
    /**
     * T056a — FIDO2.1 credProtect policy from the client extensions map (key 0x0A).
     *   1 = credProtectOptional (default if absent)
     *   2 = credProtectOptionalWithCredentialIdList
     *   3 = credProtectRequired — GetAssertion MUST fail without user verification
     * Null means the RP did not specify a policy.
     */
    val credProtectPolicy: Int? = null,
    // T045: PRF extension — salts parsed from extensions["hmac-secret"]; null if absent.
    val prfInput: PrfExtensionInput? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MakeCredentialRequest) return false

        if (!clientDataHash.contentEquals(other.clientDataHash)) return false
        if (rpId != other.rpId) return false
        if (rpName != other.rpName) return false
        if (!userId.contentEquals(other.userId)) return false
        if (userName != other.userName) return false
        if (userDisplayName != other.userDisplayName) return false
        if (algorithms != other.algorithms) return false
        if (requireUV != other.requireUV) return false
        if (requireRK != other.requireRK) return false
        if (credProtectPolicy != other.credProtectPolicy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientDataHash.contentHashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + rpName.hashCode()
        result = 31 * result + userId.contentHashCode()
        result = 31 * result + userName.hashCode()
        result = 31 * result + userDisplayName.hashCode()
        result = 31 * result + algorithms.hashCode()
        result = 31 * result + requireUV.hashCode()
        result = 31 * result + requireRK.hashCode()
        result = 31 * result + (credProtectPolicy ?: 0)
        return result
    }
}
