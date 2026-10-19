package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
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
     * Borrows [salt] for this call and does not erase or retain it. A non-null output is an
     * independently owned secret buffer; the caller must erase it after use. A null result
     * means the master seed is unavailable. Cancellation and other failures propagate; internal
     * HMAC working buffers are erased by [HmacSecretProcessor].
     *
     * @param salt Exactly 32-byte salt borrowed from the RP.
     * @param credentialId The credential ID used for per-credential derivation.
     * @return Caller-owned 32-byte HMAC-SHA-256 output, or `null` if unavailable.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
     * Borrows the input salts and leaves them under the caller's ownership. Returned output
     * arrays are owned by the caller and must be erased after response construction. If the
     * first derivation is unavailable, returns `null` so the optional extension can be omitted;
     * an unavailable second derivation omits only output2. If construction fails or cancellation
     * occurs before ownership transfers to the result, every output already created is erased.
     * Cancellation and other unexpected failures propagate.
     *
     * @param input        Validated PRF extension input with 1–2 salts.
     * @param credentialId The credential's string ID.
     * @return Caller-owned [PrfExtensionOutput] with one or two 32-byte outputs, or `null` when
     * the first derivation is unavailable.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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

        var output2: ByteArray? = null
        var transferred = false
        try {
            output2 =
                input.salt2?.let { salt2 ->
                    val out = derive(salt2, credentialId)
                    if (out == null) {
                        Logger.w(
                            TAG,
                        ) { "PRF: salt2 derivation failed for credentialId=$credentialId; omitting output2" }
                    }
                    out
                }

            val result = PrfExtensionOutput(output1 = output1, output2 = output2)
            transferred = true
            return result
        } finally {
            if (!transferred) {
                output1.fill(0)
                output2?.fill(0)
            }
        }
    }
}
