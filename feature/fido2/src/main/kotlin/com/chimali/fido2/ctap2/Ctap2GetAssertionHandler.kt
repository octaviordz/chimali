package com.chimali.fido2.ctap2

import android.util.Log
import com.chimali.fido2.data.crypto.AuthenticatorDataBuilder
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Ctap2GetAssertionHandler"

// CTAP2 response keys (§6.2)
private const val KEY_CREDENTIAL  = 1
private const val KEY_AUTH_DATA   = 2
private const val KEY_SIGNATURE   = 3
private const val KEY_USER        = 4
private const val KEY_NUM_CREDS   = 5

/**
 * T087 — CTAP2 authenticatorGetAssertion command handler.
 *
 * Receives decoded CBOR request bytes from the HID transport, extracts
 * GetAssertion parameters, delegates to [GetAssertionUseCase], then
 * serialises the [AssertionObject] response back to CBOR.
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
@Singleton
class Ctap2GetAssertionHandler @Inject constructor(
    private val getAssertionUseCase: GetAssertionUseCase,
    private val cborCodec: CborCodec,
    private val authDataBuilder: AuthenticatorDataBuilder
) {

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
            Log.d(TAG, "GetAssertion: rpId=${options.rpId} allowCredentials=${options.allowCredentials?.size ?: "discoverable"}")

            val result = getAssertionUseCase(options)
            result.fold(
                onSuccess = { assertion ->
                    Log.d(TAG, "Assertion success: credId=${assertion.credentialId}")
                    val responseBytes = encodeResponse(assertion)
                    byteArrayOf(0x00.toByte()) + responseBytes  // CTAP2_OK + response
                },
                onFailure = { error ->
                    Log.e(TAG, "Assertion failed: ${error.message}", error)
                    val errorCode: Byte = when (error) {
                        is Fido2Exception.CredentialNotFound      -> 0x22.toByte() // CTAP2_ERR_NO_CREDENTIALS
                        is Fido2Exception.UserVerificationFailed  -> 0x29.toByte() // CTAP2_ERR_OPERATION_DENIED
                        is Fido2Exception.NoVerificationMethodAvailable -> 0x26.toByte()
                        else                                       -> 0x7F.toByte() // CTAP1_ERR_OTHER
                    }
                    byteArrayOf(errorCode)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "GetAssertion handler exception: ${e.message}", e)
            byteArrayOf(0x17.toByte()) // CTAP2_ERR_PROCESSING
        }
    }

    // ── Decoding ──────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun decodeOptions(params: Map<String, Any>): GetAssertionOptions {
        val rpId = params["1"] as? String
            ?: throw Fido2Exception.InvalidParameter("Missing rpId (key 0x01)")

        // clientDataHash: real CBOR delivers this as a raw ByteArray.
        // The old JSON-based codec delivered it as a base64 string — handle both.
        val clientDataHash: ByteArray = when (val raw = params["2"]) {
            is ByteArray -> raw
            is String    -> java.util.Base64.getDecoder().decode(raw)
            else         -> throw Fido2Exception.InvalidParameter("Missing clientDataHash (key 0x02)")
        }
        require(clientDataHash.size == 32) { "clientDataHash must be 32 bytes, got ${clientDataHash.size}" }

        // Optional allow-list: array of PublicKeyCredentialDescriptor maps.
        // credential ID is a byte string in CBOR → ByteArray, or legacy base64 String.
        val allowListRaw = params["3"] as? List<*>
        val allowCredentials = allowListRaw?.mapNotNull { descriptor ->
            (descriptor as? Map<*, *>)?.let { map ->
                val rawId = map["id"]
                val idBytes: ByteArray = when (rawId) {
                    is ByteArray -> rawId
                    is String    -> rawId.toByteArray()   // legacy / base64
                    else         -> return@mapNotNull null
                }
                com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor.create(id = idBytes)
            }
        }

        // options map (key 0x05): {"uv": bool, "up": bool}
        val optionsMap = params["5"] as? Map<*, *>
        val uvRaw = optionsMap?.get("uv") as? Boolean ?: false
        val userVerification = if (uvRaw) UserVerificationRequirement.REQUIRED
                               else       UserVerificationRequirement.PREFERRED

        return GetAssertionOptions(
            rpId             = rpId,
            clientDataHash   = clientDataHash,
            allowCredentials = allowCredentials,
            userVerification = userVerification
        )
    }

    // ── T088: Response encoding ───────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun encodeResponse(
        assertion: com.chimali.fido2.domain.model.AssertionObject
    ): ByteArray {
        val responseMap = mutableMapOf<String, Any>()

        // 0x01 — credential descriptor
        // Per CTAP2 §6.2, the credential id MUST be raw bytes (CBOR bstr), not base64.
        assertion.credential?.let { desc ->
            responseMap["1"] = mapOf(
                "type" to "public-key",
                "id"   to desc.id   // ByteArray — CborCodec encodes as CBOR bstr
            )
        }

        // 0x02 — authData: raw bytes, NOT base64 text
        responseMap["2"] = assertion.authData

        // 0x03 — DER-encoded ECDSA signature: raw bytes, NOT base64 text
        responseMap["3"] = assertion.signature

        // 0x04 — user entity (discoverable credential flow)
        assertion.user?.let { user ->
            responseMap["4"] = mapOf(
                "id"          to user.id,              // raw bytes
                "name"        to user.name,
                "displayName" to (user.displayName ?: user.name)
            )
        }

        // 0x05 — numberOfCredentials (omit if == 1)
        assertion.numberOfCredentials?.let { n ->
            if (n > 1) responseMap["5"] = n
        }

        return cborCodec.encodeToFido2Format(responseMap)
    }
}
