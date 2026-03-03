package com.chimali.fido2.ctap2

import android.util.Log
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.service.Fido2Authenticator
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import kotlinx.coroutines.CompletableDeferred
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Ctap2MakeCredential"

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
private const val CTAP2_ERR_PIN_INVALID:           Byte = 0x31.toByte()

// COSE algorithm IDs
internal const val COSE_ES256 = -7    // ECDSA with SHA-256 / P-256
internal const val COSE_RS256 = -257  // RSASSA-PKCS1-v1_5 with SHA-256
internal const val COSE_EDDSA = -8    // EdDSA

// AuthData flags
private const val FLAG_UP: Int = 0x01  // User Present
private const val FLAG_UV: Int = 0x04  // User Verified
private const val FLAG_AT: Int = 0x40  // Attested Credential Data included

// Fixed AAGUID for Chimali authenticator (version 1)
private val CHIMALI_AAGUID = byteArrayOf(
    0x43, 0x48, 0x49, 0x4D, 0x41, 0x4C, 0x49, 0x00, // "CHIMALI\0"
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01  // ...version 1
)

/**
 * Handles CTAP2 `authenticatorMakeCredential` (0x01) commands arriving from
 * [BluetoothHidTransportImpl].
 *
 * Responsibilities:
 * - Decode CBOR-encoded MakeCredential request
 * - Validate parameters per CTAP2 spec §6.1
 * - Request user verification via [UserVerificationService]
 * - Delegate credential creation to [Fido2Authenticator]
 * - Encode the success / error CTAP2 response back as a [CtapHidMessage]
 */
@Singleton
class Ctap2MakeCredentialHandler @Inject constructor(
    private val userVerificationService: UserVerificationService,
    private val fido2Authenticator: Fido2Authenticator,
    private val cborCodec: CborCodec,
    private val hidReportParser: HidReportParser,
    private val uiEventBus: Fido2UiEventBus
) {

    private val secureRandom = SecureRandom()

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
            Log.e(TAG, "MakeCredential error: ${e.message}")
            errorPackets(cid, mapExceptionToStatus(e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in MakeCredential", e)
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
            throw Fido2Exception.InvalidFormatException("pubKeyCredParams contains no valid algorithms")
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
        // Build options
        val rp = PublicKeyCredentialRpEntity.create(req.rpId, req.rpName)
        val user = PublicKeyCredentialUserEntity.create(req.userId, req.userName, req.userDisplayName)
        val makeCredentialOptions = MakeCredentialOptions.create(
            rp = rp,
            user = user,
            challenge = req.clientDataHash,
            pubKeyCredParams = PublicKeyCredentialParameters.createES256P256()
        )

        // Dispatch to UI and wait
        val deferred = CompletableDeferred<Result<AttestationObject>>()
        uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(makeCredentialOptions, deferred))

        val attestationResult = deferred.await()
        
        if (attestationResult.isFailure) {
            val ex = attestationResult.exceptionOrNull()
            Log.e(TAG, "Registration failed or cancelled: ${ex?.message}")
            return when (ex) {
                is Fido2Exception.CredentialException -> errorPackets(cid, CTAP2_ERR_KEY_STORE_FULL)
                is Fido2Exception.UserVerificationException -> errorPackets(cid, CTAP2_ERR_OPERATION_DENIED)
                else -> errorPackets(cid, CTAP2_ERR_NOT_ALLOWED)
            }
        }

        val attestation = attestationResult.getOrThrow()
        val responseCbor = encodeAttestationResponse(attestation)
        val responsePayload = byteArrayOf(CTAP2_OK) + responseCbor
        val responseMsg = CtapHidMessage(cid, CTAPHID_CBOR.toInt() and 0x7F, responsePayload)
        return hidReportParser.encodeResponse(responseMsg)
    }

    // ── CBOR response encoding ────────────────────────────────────────────────

    /**
     * Encodes the AttestationObject into a CTAP2 MakeCredential response CBOR map.
     * Keys are integer per CTAP2 spec §6.1:
     *   0x01 = fmt, 0x02 = authData, 0x03 = attStmt
     */
    private fun encodeAttestationResponse(attestation: AttestationObject): ByteArray {
        val authDataBytes = buildAuthenticatorData(attestation.authData)
        val responseMap: Map<String, Any> = mapOf(
            "fmt" to attestation.fmt,
            "authData" to authDataBytes.toList(),
            "attStmt" to emptyList<Any>()
        )
        return cborCodec.encodeToFido2Format(responseMap)
    }

    /**
     * Serialises [AuthenticatorData] per CTAP2 / WebAuthn spec:
     *   rpIdHash(32) + flags(1) + signCount(4 BE) + AAGUID(16) + credIdLen(2 BE) + credId + cose key
     */
    private fun buildAuthenticatorData(authData: AuthenticatorData): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(authData.rpIdHash.toList())         // 32 bytes
        result.addAll(authData.flags.toList())            // 1 byte
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
                put("alg", COSE_ES256)
                stmt.attCert?.let { put("sig", it) }
                stmt.x5c?.let { put("x5c", it) }
            }
            else -> emptyMap()
        }
    }

    // ── Algorithm selection ───────────────────────────────────────────────────

    private fun selectAlgorithm(requested: List<Int>): Int? {
        val supported = listOf(COSE_ES256, COSE_RS256)
        return supported.firstOrNull { it in requested }
    }

    // ── Error helpers ─────────────────────────────────────────────────────────

    private fun errorPackets(cid: ByteArray, statusCode: Byte): List<ByteArray> {
        val payload = byteArrayOf(statusCode)
        val msg = CtapHidMessage(cid, CTAPHID_CBOR.toInt() and 0x7F, payload)
        return hidReportParser.encodeResponse(msg)
    }

    private fun mapExceptionToStatus(e: Fido2Exception): Byte = when (e) {
        is Fido2Exception.InvalidFormatException  -> CTAP2_ERR_INVALID_CBOR
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
)
