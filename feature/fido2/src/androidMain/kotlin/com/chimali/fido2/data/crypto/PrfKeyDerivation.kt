package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import com.chimali.fido2.domain.model.PrfExtensionInput
import com.chimali.fido2.domain.model.PrfExtensionOutput
import org.koin.core.annotation.Single

/**
 * T043 — PRF key derivation service.
 *
 * Wraps [HmacSecretProcessor]'s low-level HMAC-SHA-256 machinery to expose a
 * typed, domain-level API for the WebAuthn PRF / CTAP2 `hmac-secret` extension.
 *
 * ## Derivation contract
 * For each salt:
 * ```
 * output = HMAC-SHA-256(credSecret, salt)
 * where credSecret = HMAC-SHA-256(masterSeed, "hmac-secret" || credentialId)
 * ```
 * Both `credSecret` and intermediate values are zeroised after use inside
 * [HmacSecretProcessor].
 *
 * @see HmacSecretProcessor for the low-level implementation.
 * @see PrfExtensionInput  for salt validation rules (1–2 salts, 32 bytes each).
 * @see PrfExtensionOutput for the typed output model.
 */
@Single
class PrfKeyDerivation(
    private val hmacSecretProcessor: HmacSecretProcessor,
) {
    companion object {
        private const val TAG = "PrfKeyDerivation"
    }

    /**
     * Derives a single 32-byte PRF output for the given [salt] and [credentialId].
     *
     * @param salt         Exactly 32-byte salt from the RP.
     * @param credentialId The credential's string ID used to derive the per-credential secret.
     * @return 32-byte HMAC-SHA-256 output, or `null` if the master seed is unavailable.
     */
    suspend fun derive(
        salt: ByteArray,
        credentialId: String,
    ): ByteArray? {
        require(salt.size == PrfExtensionInput.SALT_SIZE_BYTES) {
            "PRF derive: salt must be exactly ${PrfExtensionInput.SALT_SIZE_BYTES} bytes; got ${salt.size}"
        }
        return hmacSecretProcessor
            .process(
                credentialId = credentialId,
                extensionData = salt,
            )?.also { output ->
                Logger.d(TAG) { "PRF derive: ${output.size} bytes for credentialId=$credentialId" }
            }
    }

    /**
     * Derives PRF outputs for all salts in [input] and returns a typed [PrfExtensionOutput].
     *
     * If derivation for salt1 fails (e.g., master seed unavailable), returns `null`
     * and the PRF extension is omitted from the response (T049).
     *
     * @param input        Validated PRF extension input with 1–2 salts.
     * @param credentialId The credential's string ID.
     * @return [PrfExtensionOutput] with 1 or 2 32-byte outputs, or `null` on failure.
     */
    suspend fun deriveAll(
        input: PrfExtensionInput,
        credentialId: String,
    ): PrfExtensionOutput? {
        val output1 = derive(input.salt1, credentialId)
        if (output1 == null) {
            // T049: Missing hmac-secret capability — omit PRF without failing the ceremony.
            Logger.w(TAG) { "PRF: salt1 derivation failed for credentialId=$credentialId; omitting PRF from response" }
            return null
        }

        val output2 =
            input.salt2?.let { salt2 ->
                val out = derive(salt2, credentialId)
                if (out == null) {
                    Logger.w(TAG) { "PRF: salt2 derivation failed for credentialId=$credentialId; omitting output2" }
                }
                out
            }

        return PrfExtensionOutput(output1 = output1, output2 = output2)
    }
}
