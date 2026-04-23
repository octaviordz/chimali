package com.chimali.fido2.data.crypto

import org.koin.core.annotation.Single

import co.touchlab.kermit.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
 * 1. Client sends in `extensions`: `{"hmac-secret": {"keyAgreement": <COSE_Key>, "saltEnc": <bytes>, "saltAuth": <bytes>}}`
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
        suspend fun process(
            credentialId: String,
            extensionData: Any?,
        ): ByteArray? {
            if (extensionData == null) return null

            return try {
                // Parse extension input
                val saltEnc: ByteArray =
                    when {
                        extensionData is Map<*, *> -> {
                            val saltRaw = extensionData["saltEnc"]
                            when (saltRaw) {
                                is ByteArray -> saltRaw
                                else -> {
                                    Logger.w("hmac-secret: saltEnc missing or wrong type")
                                    return null
                                }
                            }
                        }
                        extensionData is ByteArray -> extensionData // direct salt (simplified mode)
                        else -> {
                            Logger.w { "hmac-secret: unexpected extension data type ${extensionData::class.simpleName}" }
                            return null
                        }
                    }

                if (saltEnc.size != SALT_SIZE && saltEnc.size != SALT_SIZE * 2) {
                    Logger.w { "hmac-secret: invalid saltEnc length ${saltEnc.size} (expected 32 or 64)" }
                    return null
                }

                // Derive per-credential HMAC secret
                val credSecret =
                    deriveCredentialSecret(credentialId)
                        ?: return null

                // Compute output(s)
                val output = ByteArray(saltEnc.size)
                val salt1 = saltEnc.copyOfRange(0, SALT_SIZE)
                val hmacOutput1 = hmacSha256(credSecret, salt1)
                hmacOutput1.copyInto(output, 0)

                if (saltEnc.size == SALT_SIZE * 2) {
                    val salt2 = saltEnc.copyOfRange(SALT_SIZE, SALT_SIZE * 2)
                    val hmacOutput2 = hmacSha256(credSecret, salt2)
                    hmacOutput2.copyInto(output, SALT_SIZE)
                }

                // Zeroise sensitive material
                credSecret.fill(0)
                salt1.fill(0)
                if (saltEnc.size == SALT_SIZE * 2) saltEnc.copyOfRange(SALT_SIZE, SALT_SIZE * 2).fill(0)

                Logger.d { "hmac-secret: computed output (${output.size} bytes) for credentialId=$credentialId" }
                output
            } catch (e: Exception) {
                Logger.e(e) { "hmac-secret processing failed: ${e.message}" }
                null
            }
        }

        /**
         * Derives a 32-byte per-credential secret: HMAC-SHA-256(masterSeed, "hmac-secret" || credentialId).
         *
         * The credential secret is deterministic and unique per credential — different credentials
         * for the same RP produce different secrets, satisfying the FIDO2 isolation requirement.
         */
        private suspend fun deriveCredentialSecret(credentialId: String): ByteArray? {
            val masterSeed = masterSeedProvider.getMasterSeed()
            if (masterSeed == null) {
                Logger.w("hmac-secret: master seed unavailable, cannot derive credential secret")
                return null
            }
            return hmacSha256(masterSeed, DOMAIN_SEPARATOR + credentialId.toByteArray(Charsets.UTF_8))
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
        fun buildAuthDataExtensions(hmacSecretOutput: ByteArray?): Map<String, Any>? {
            if (hmacSecretOutput == null) return null
            return mapOf(EXTENSION_KEY to hmacSecretOutput)
        }

        /**
         * Returns true if [extensions] map contains a processable `hmac-secret` entry.
         */
        fun isPresent(extensions: Map<String, Any>?): Boolean = extensions?.containsKey(EXTENSION_KEY) == true
    }
