package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedProvider
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

/**
 * T087a — FIDO2.1 `hmac-secret` extension processor.
 *
 * Implements the CTAP2.1 `hmac-secret` extension (§12.4 of CTAP 2.1 specification):
 * > The `hmac-secret` extension allows RPs to derive secret material from the
 * > authenticator, bound to a specific credential. The authenticator XORs the
 * > requested salts with an HMAC of the credential's per-credential secret.
 *
 * ## Protocol Flow (assertion phase):
 *
 * 1. Client sends in `extensions`:
 *    `{"hmac-secret": {"keyAgreement": <COSE_Key>, "saltEnc": <bytes>, "saltAuth": <bytes>}}`
 * 2. Authenticator:
 *    a. Verifies `saltAuth` via HMAC-SHA-256(sharedSecret, saltEnc).
 *    b. Decrypts `saltEnc` → salt1 (32 bytes) [+ salt2 (32 bytes) if present].
 *    c. Computes: `output1 = HMAC-SHA-256(credSecret, salt1)`, `output2 = HMAC-SHA-256(credSecret, salt2)`.
 *    d. Returns: `output1 [|| output2]` XOR-encrypted with the shared AES-CBC key.
 * 3. Client decrypts the output and uses the 32-byte secrets for RP-defined purposes.
 *
 * ## Simplified server-less implementation
 *
 * Since Chimali serves as an authenticator (not a full CTAP2 PIN/UV token proxy),
 * and to avoid ECDH PIN-protocol key exchange implementation in this phase,
 * this processor derives `credSecret` deterministically from the master seed
 * via HMAC-SHA-256(masterSeed, "hmac-secret" || credentialId), which satisfies
 * the cryptographic binding to the credential.
 *
 * The salt encryption/decryption step (key agreement) is simplified to direct HMAC:
 * the `saltEnc` from a trusted client is taken as the raw salt input.
 *
 * @see [CTAP2.1 §12.4](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-20210615.html#sctn-hmac-secret-extension)
 */
@Single
class HmacSecretProcessor(
    private val masterSeedProvider: MasterSeedProvider,
) {
    companion object {
        const val EXTENSION_KEY = "hmac-secret"
        private const val SALT_SIZE = 32
        private val DOMAIN_SEPARATOR = "hmac-secret".toByteArray(Charsets.UTF_8)
    }

    /**
     * Processes the `hmac-secret` extension for a GetAssertion ceremony.
     *
     * @param credentialId  The credential ID string used to derive the per-credential secret.
     * @param extensionData The raw extension map value for key "hmac-secret".
     *                      Expected: a map with key "saltEnc" → ByteArray (32 or 64 bytes).
     * @return CBOR-encodable extension result map entry, or null if processing failed
     *         and the extension should be silently omitted.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun process(
        credentialId: String,
        extensionData: Any?,
    ): ByteArray? {
        if (extensionData == null) return null

        var sessionOutput: ByteArray? = null
        var resultTransferred = false
        return try {
            // Parse extension input
            val saltEnc: ByteArray =
                when (extensionData) {
                    is Map<*, *> -> {
                        when (val saltRaw = extensionData["saltEnc"]) {
                            is ByteArray -> saltRaw
                            else -> {
                                Logger.w("hmac-secret: saltEnc missing or wrong type")
                                return null
                            }
                        }
                    }
                    is ByteArray -> extensionData // direct salt (simplified mode)
                    else -> {
                        Logger.w { "hmac-secret: unexpected extension data type ${extensionData::class.simpleName}" }
                        return null
                    }
                }

            if ((saltEnc.size != SALT_SIZE) && (saltEnc.size != (SALT_SIZE * 2))) {
                Logger.w { "hmac-secret: invalid saltEnc length ${saltEnc.size} (expected 32 or 64)" }
                return null
            }

            val result =
                masterSeedProvider.withSessionOwner { _ ->
                    val rootPrk = masterSeedProvider.getMasterSeed() ?: return@withSessionOwner null
                    try {
                        val credentialBytes = credentialId.toByteArray(Charsets.UTF_8)
                        val credentialInput = DOMAIN_SEPARATOR + credentialBytes
                        credentialBytes.fill(0)
                        var credentialSecret: ByteArray? = null
                        try {
                            val secret = hmacSha256(rootPrk, credentialInput)
                            credentialSecret = secret
                            val output = ByteArray(saltEnc.size)
                            var completed = false
                            try {
                                for (offset in 0 until saltEnc.size step SALT_SIZE) {
                                    val saltCopy = saltEnc.copyOfRange(offset, offset + SALT_SIZE)
                                    var saltOutput: ByteArray? = null
                                    try {
                                        saltOutput = hmacSha256(secret, saltCopy)
                                        saltOutput.copyInto(output, offset)
                                    } finally {
                                        saltCopy.fill(0)
                                        saltOutput?.fill(0)
                                    }
                                }
                                Logger.d { "hmac-secret: computed output (${output.size} bytes)" }
                                completed = true
                                // Keep an outer owner reference in case withSessionOwner's
                                // cancellation check discards this completed callback result.
                                sessionOutput = output
                                output
                            } finally {
                                if (!completed) output.fill(0)
                            }
                        } finally {
                            credentialInput.fill(0)
                            credentialSecret?.fill(0)
                        }
                    } finally {
                        rootPrk.fill(0)
                    }
                }
            resultTransferred = true
            result
        } catch (_: java.security.GeneralSecurityException) {
            Logger.e { "hmac-secret processing failed" }
            null
        } finally {
            if (!resultTransferred) sessionOutput?.fill(0)
        }
    }

    /** Computes HMAC-SHA-256(key, data). */

    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Builds the CBOR extensions map for authData when [hmacSecretOutput] is present.
     *
     * The extension data bit (ED bit = 0x80) in the authData flags byte must also be set
     * by the caller when this method returns a non-null result.
     *
     * @return CBOR-encodable extensions map `{"hmac-secret": <output_bytes>}`, or null.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun buildAuthDataExtensions(hmacSecretOutput: ByteArray?): Map<String, Any>? {
        if (hmacSecretOutput == null) return null
        return mapOf(EXTENSION_KEY to hmacSecretOutput)
    }

    /**
     * Returns true if [extensions] map contains a processable `hmac-secret` entry.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isPresent(extensions: Map<String, Any>?): Boolean = extensions?.containsKey(EXTENSION_KEY) == true
}
