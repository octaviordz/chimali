package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.transport.BluetoothHidTransportImpl
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.util.performance.LatencyProfiler
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// CTAPHID command codes
private const val CTAPHID_CBOR: Byte = 0x10

// CTAP2 command codes (first byte of CBOR payload)
private const val CMD_MAKE_CREDENTIAL: Byte = 0x01

// CTAP2 status codes
private const val CTAP2_OK:                        Byte = 0x00
private const val CTAP2_ERR_INVALID_CBOR:          Byte = 0x12.toByte()
private const val CTAP2_ERR_MISSING_PARAMETER:     Byte = 0x14.toByte()
private const val CTAP2_ERR_UNSUPPORTED_ALGORITHM: Byte = 0x26.toByte()
private const val CTAP2_ERR_OPERATION_DENIED:      Byte = 0x27.toByte()
private const val CTAP2_ERR_KEY_STORE_FULL:        Byte = 0x28.toByte()
private const val CTAP2_ERR_NOT_ALLOWED:           Byte = 0x36.toByte()

// COSE algorithm IDs
internal const val COSE_ES256 = -7    // ECDSA with SHA-256 / P-256
/** COSE algorithm identifier for Ed25519. */
internal const val COSE_ED25519 = -19 // Ed25519
/** ML-DSA-65 (Dilithium, NIST FIPS 204 Level 3). Working-draft COSE ID -49; IANA pending. */
internal const val COSE_ML_DSA_65 = -49 // ML-DSA-65 (Dilithium)

// AuthData flags
private const val FLAG_UP: Int = 0x01  // User Present
private const val FLAG_AT: Int = 0x40  // Attested Credential Data included

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
@Singleton
class Ctap2MakeCredentialHandler @Inject constructor(
    private val cborCodec: CborCodec,
    private val hidReportParser: HidReportParser,
    private val uiEventBus: Fido2UiEventBus
) {

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Handles an incoming CTAPHID_CBOR message whose first payload byte is
     * [CMD_MAKE_CREDENTIAL] (0x01).
     *
     * @param message  The fully-reassembled CTAPHID message from the host.
     * @return         A list of 64-byte HID packets to send back to the host.
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
            Timber.e(e, "MakeCredential error: %s", e.message)
            errorPackets(cid, mapExceptionToStatus(e))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error in MakeCredential")
            errorPackets(cid, CTAP2_ERR_NOT_ALLOWED)
        }
    }

    // ── Request decoding ──────────────────────────────────────────────────────

    private fun decodeMakeCredentialRequest(cbor: ByteArray): MakeCredentialRequest {
        val map = cborCodec.decodeFromFido2Format(cbor)
            .takeIf { it.isNotEmpty() }
            ?: throw Fido2Exception.InvalidFormatException("MakeCredential request is not a valid map")

        // 0x01: clientDataHash (required)
        val clientDataHash = (map["1"] ?: map["clientDataHash"]) as? ByteArray
            ?: throw Fido2Exception.InvalidFormatException("Missing clientDataHash (key 0x01)")

        // 0x02: rp (required)
        @Suppress("UNCHECKED_CAST")
        val rpMap = (map["2"] ?: map["rp"]) as? Map<*, *>
            ?: throw Fido2Exception.InvalidFormatException("Missing rp entity (key 0x02)")
        val rpId = rpMap["id"] as? String
            ?: throw Fido2Exception.InvalidFormatException("Missing rp.id")
        val rpName = rpMap["name"] as? String ?: rpId

        // 0x03: user (required)
        @Suppress("UNCHECKED_CAST")
        val userMap = (map["3"] ?: map["user"]) as? Map<*, *>
            ?: throw Fido2Exception.InvalidFormatException("Missing user entity (key 0x03)")
        val userId = userMap["id"] as? ByteArray
            ?: throw Fido2Exception.InvalidFormatException("Missing user.id")
        val userName = userMap["name"] as? String ?: ""
        val userDisplayName = userMap["displayName"] as? String ?: userName

        // 0x04: pubKeyCredParams (required)
        @Suppress("UNCHECKED_CAST")
        val rawParams = (map["4"] ?: map["pubKeyCredParams"]) as? List<*>
            ?: throw Fido2Exception.InvalidFormatException("Missing pubKeyCredParams (key 0x04)")
        val algorithms = rawParams.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let { m ->
                (m["alg"] as? Long)?.toInt() ?: (m["alg"] as? Int)
            }
        }
        if (algorithms.isEmpty()) {
            throw Fido2Exception.MissingParameterException("pubKeyCredParams contains no valid algorithms")
        }

        // 0x07: options (optional)
        @Suppress("UNCHECKED_CAST")
        val options = (map["7"] ?: map["options"]) as? Map<*, *>
        val requireUserVerification = options?.get("uv") as? Boolean ?: false
        val requireResidentKey = options?.get("rk") as? Boolean ?: false

        return MakeCredentialRequest(
            clientDataHash = clientDataHash,
            rpId = rpId,
            rpName = rpName,
            userId = userId,
            userName = userName,
            userDisplayName = userDisplayName,
            algorithms = algorithms,
            requireUV = requireUserVerification,
            requireRK = requireResidentKey
        )
    }

    // ── Core handler ──────────────────────────────────────────────────────────

    private suspend fun handleMakeCredential(
        cid: ByteArray,
        req: MakeCredentialRequest
    ): List<ByteArray> {
        Timber.d("handleMakeCredential START rpId=%s user=%s", req.rpId, req.userName)

        val rp   = PublicKeyCredentialRpEntity.create(req.rpId, req.rpName)
        val user = PublicKeyCredentialUserEntity.create(req.userId, req.userName, req.userDisplayName)

        // T017a Algorithm Negotiation: Pick the first algorithm requested that we support.
        val (selectedAlgId, pubKeyCredParams) = req.algorithms.firstNotNullOfOrNull { algId ->
            when (algId) {
                COSE_ES256 -> COSE_ES256 to PublicKeyCredentialParameters.createES256P256()
                COSE_ED25519 -> COSE_ED25519 to PublicKeyCredentialParameters.createEd25519()
                COSE_ML_DSA_65 -> COSE_ML_DSA_65 to PublicKeyCredentialParameters.createMlDsa65()
                else -> null
            }
        } ?: run {
            Timber.e("Algorithm negotiation failed: None of the requested algorithms %s are supported", req.algorithms)
            return errorPackets(cid, CTAP2_ERR_UNSUPPORTED_ALGORITHM)
        }

        Timber.i("Algorithm negotiation: RP requested %s, selected COSE alg %d (%s)",
            req.algorithms, selectedAlgId, pubKeyCredParams.algorithm)

        val makeCredentialOptions = MakeCredentialOptions.create(
            rp = rp, user = user,
            challenge = req.clientDataHash,
            pubKeyCredParams = pubKeyCredParams,
            selectedAlgId = selectedAlgId
        )

        val deferred = CompletableDeferred<Result<MakeCredentialResult>>()
        Timber.d("Dispatching RegistrationRequested event to UI")
        uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(makeCredentialOptions, deferred))
        Timber.d("Event dispatched — awaiting user response via deferred")

        // NFR-PERF-030: Exclude UI interaction time from system latency
        LatencyProfiler.startUserInteraction("MakeCredential")
        val makeCredentialResult = deferred.await()
        LatencyProfiler.endUserInteraction("MakeCredential")
        Timber.d("Deferred resolved — success=%b error=%s", makeCredentialResult.isSuccess, makeCredentialResult.exceptionOrNull()?.message)

        if (makeCredentialResult.isFailure) {
            val ex = makeCredentialResult.exceptionOrNull()
            Timber.e(ex, "Registration failed or cancelled: %s", ex?.message)
            return when (ex) {
                is Fido2Exception.CredentialException ->
                    errorPackets(cid, CTAP2_ERR_KEY_STORE_FULL)
                is Fido2Exception.UserVerificationException ->
                    errorPackets(cid, CTAP2_ERR_OPERATION_DENIED)
                else -> errorPackets(cid, CTAP2_ERR_NOT_ALLOWED)
            }
        }

        val attestation = makeCredentialResult.getOrThrow().attestationObject
        Timber.d("Encoding MakeCredential response for credId=%dbytes", attestation.authData.credentialId.size)
        val responseCbor   = encodeAttestationResponse(attestation)
        val responsePayload = byteArrayOf(CTAP2_OK) + responseCbor
        // Command byte for CTAPHID_CBOR response = 0x10 (no masking needed)
        val responseMsg = CtapHidMessage(cid, CTAPHID_CBOR.toInt(), responsePayload)
        Timber.d("MakeCredential response ready payloadLen=%d", responsePayload.size)
        return hidReportParser.encodeResponse(responseMsg)
    }

    // ── CBOR response encoding ────────────────────────────────────────────────

    /**
     * Encodes the AttestationObject as a CTAP2 authenticatorMakeCredential response.
     * Per CTAP2 spec §6.1, keys MUST be integers:
     *   0x01 = fmt, 0x02 = authData, 0x03 = attStmt
     */
    private fun encodeAttestationResponse(attestation: AttestationObject): ByteArray {
        val authDataBytes = buildAuthenticatorData(attestation.authData)
        // Integer keys — not string keys — per CTAP2 §6.1
        val responseMap: Map<String, Any> = mapOf(
            "1" to attestation.fmt,         // fmt
            "2" to authDataBytes,           // authData (raw bytes, not base64)
            "3" to buildAttestationStatementMap(attestation.attStmt)
        )
        Timber.d("encodeAttestationResponse: fmt=%s authDataLen=%d", attestation.fmt, authDataBytes.size)
        Timber.d("authData hex: %s", authDataBytes.joinToString("") { "%02x".format(it) })
        val encoded = cborCodec.encodeToFido2Format(responseMap)
        Timber.d("CBOR response hex: %s", encoded.joinToString("") { "%02x".format(it) })
        return encoded
    }

    /**
     * Serialises [AuthenticatorData] per CTAP2 / WebAuthn spec:
     *   rpIdHash(32) + flags(1) + signCount(4 BE) + AAGUID(16) + credIdLen(2 BE) + credId + cose key
     */
    private fun buildAuthenticatorData(authData: AuthenticatorData): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(authData.rpIdHash.toList())         // 32 bytes
        // Force AT (0x40) + UP (0x01) flags; preserve UV (0x04) if set.
        // AT MUST be set when attested credential data follows the counter.
        val rawFlags = if (authData.flags.isNotEmpty()) authData.flags[0].toInt() else 0
        val flags = (rawFlags or FLAG_AT or FLAG_UP).toByte()
        result.add(flags)                                 // 1 byte
        // signCount as 4-byte big-endian
        val cnt = authData.counter
        result.add(((cnt shr 24) and 0xFF).toByte())
        result.add(((cnt shr 16) and 0xFF).toByte())
        result.add(((cnt shr  8) and 0xFF).toByte())
        result.add(( cnt         and 0xFF).toByte())
        // Attested credential data
        result.addAll(authData.aaguid.toList())           // 16 bytes
        val credIdLen = authData.credentialId.size
        result.add(((credIdLen shr 8) and 0xFF).toByte())
        result.add(( credIdLen        and 0xFF).toByte())
        result.addAll(authData.credentialId.toList())
        result.addAll(authData.publicKey.toList())
        return result.toByteArray()
    }

    private fun buildAttestationStatementMap(stmt: AttestationStatement): Map<Any, Any> {
        return when (stmt.fmt) {
            "none" -> emptyMap()
            "packed" -> buildMap {
                put("alg", stmt.alg)
                stmt.attCert?.let { put("sig", it) }
                stmt.x5c?.let { put("x5c", it) }
            }
            else -> emptyMap()
        }
    }

    // ── Error helpers ─────────────────────────────────────────────────────────

    private fun errorPackets(cid: ByteArray, statusCode: Byte): List<ByteArray> {
        val payload = byteArrayOf(statusCode)
        val msg = CtapHidMessage(cid, CTAPHID_CBOR.toInt() and 0x7F, payload)
        return hidReportParser.encodeResponse(msg)
    }

    private fun mapExceptionToStatus(e: Fido2Exception): Byte = when (e) {
        is Fido2Exception.InvalidFormatException  -> CTAP2_ERR_INVALID_CBOR
        is Fido2Exception.MissingParameterException -> CTAP2_ERR_MISSING_PARAMETER
        is Fido2Exception.UnsupportedAlgorithmException -> CTAP2_ERR_UNSUPPORTED_ALGORITHM
        is Fido2Exception.UserVerificationException -> CTAP2_ERR_OPERATION_DENIED
        is Fido2Exception.CredentialException     -> CTAP2_ERR_KEY_STORE_FULL
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
    val requireRK: Boolean
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
        return result
    }
}
