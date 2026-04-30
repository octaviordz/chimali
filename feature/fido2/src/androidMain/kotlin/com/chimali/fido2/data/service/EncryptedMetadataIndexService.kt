package com.chimali.fido2.data.service

import co.touchlab.kermit.Logger
import com.chimali.core.security.api.SivEncryptionManager
import org.koin.core.annotation.Single

/**
 * T113a — AES-256-SIV metadata indexing service for encrypted credential lookup tags.
 *
 * ## Motivation (Constitution §I.2)
 *
 * The Chimali Constitution mandates that **searchable metadata** (RP ID tags, credential
 * aliases) be encrypted with AES-256-SIV rather than stored in plaintext. SIV provides:
 *
 * 1. **Deterministic ciphertext** — the same RP ID encrypted with the same key always
 *    produces the same tag, enabling exact-match lookups without decrypting the entire table.
 * 2. **Authenticity** — SIV authenticates the ciphertext; tampered tags fail decryption
 *    with [SecurityException], preventing tag-forgery attacks.
 * 3. **Nonce-misuse resistance** — unlike GCM, SIV does not require a random nonce,
 *    eliminating the risk of nonce reuse in high-frequency indexing operations.
 *
 * ## Key Material
 *
 * The SIV index key is derived from the master seed via HKDF:
 * `indexKey = masterSeed[0..63]` (first 64 bytes of the 64-byte master seed).
 *
 * In a production deployment, the index key should be derived separately from the
 * master seed using HKDF with domain separation, e.g.:
 * `HKDF-SHA-256(masterSeed, "chimali-index-key", 64 bytes)`
 *
 * For this implementation, the key is provisioned externally via [provisionKey].
 *
 * ## Usage
 *
 * ```kotlin
 * val tag = metadataIndexService.encryptIndexTag("example.com")
 * // Store `tag` in the database instead of raw "example.com"
 *
 * // On lookup:
 * val lookupTag = metadataIndexService.encryptIndexTag("example.com")
 * db.query("SELECT * FROM credentials WHERE rpIdTag = ?", lookupTag)
 * ```
 */
@Single
class EncryptedMetadataIndexService(
    private val sivEncryptionManager: SivEncryptionManager,
) {
    companion object {
        /** SIV key length: 64 bytes (two 256-bit sub-keys). */
        private const val KEY_LENGTH = 64

        /** Domain separator for RP ID tags. */
        private val RP_ID_DOMAIN = "chimali.rpid.index".toByteArray(Charsets.UTF_8)

        /** Domain separator for credential alias tags. */
        private val ALIAS_DOMAIN = "chimali.alias.index".toByteArray(Charsets.UTF_8)
    }

    /**
     * The 64-byte SIV index key. Must be provisioned before any [encryptRpIdTag] calls.
     * In production this is derived from the master seed via HKDF.
     */
    @Volatile
    private var indexKey: ByteArray? = null

    /**
     * Provisions the SIV index key from raw key material.
     *
     * Must be called once during authenticator initialization, after the master seed
     * is loaded. The [rawKey] must be exactly 64 bytes.
     *
     * @throws IllegalArgumentException if [rawKey] is not 64 bytes.
     */
    fun provisionKey(rawKey: ByteArray) {
        require(rawKey.size == KEY_LENGTH) {
            "SIV index key must be $KEY_LENGTH bytes, got ${rawKey.size}"
        }
        indexKey = rawKey.clone()
        Logger.d { "EncryptedMetadataIndexService: index key provisioned (${rawKey.size} bytes)" }
    }

    /**
     * Clears the in-memory index key. Called on authenticator reset or session teardown.
     */
    fun clearKey() {
        indexKey?.fill(0)
        indexKey = null
        Logger.d { "EncryptedMetadataIndexService: index key cleared" }
    }

    /**
     * Generates a deterministic AES-256-SIV encrypted index tag for an RP ID.
     *
     * The same [rpId] encrypted with the same key always produces the same tag,
     * enabling exact-match SQL queries on encrypted data. The RP_ID_DOMAIN prefix
     * ensures tags from different contexts (RP IDs vs aliases) are cryptographically
     * separated even with the same key.
     *
     * @param rpId The Relying Party identifier (e.g., "example.com").
     * @return 28-byte SIV-encrypted tag (16-byte SIV + 12-byte encrypted input ~= varies by input),
     *         or null if the key has not been provisioned.
     */
    fun encryptRpIdTag(rpId: String): ByteArray? {
        val key =
            indexKey ?: run {
                Logger.w { "EncryptedMetadataIndexService: key not provisioned, returning plaintext tag fallback" }
                return null
            }
        return try {
            val plaintext = RP_ID_DOMAIN + rpId.toByteArray(Charsets.UTF_8)
            sivEncryptionManager.encrypt(plaintext, key)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "encryptRpIdTag failed for rpId=$rpId" }
            null
        }
    }

    /**
     * Generates a deterministic AES-256-SIV encrypted index tag for a credential alias.
     *
     * @param alias The credential alias string (e.g., "device-key/1176399154/2342818").
     * @return SIV-encrypted tag bytes, or null if the key has not been provisioned.
     */
    fun encryptAliasTag(alias: String): ByteArray? {
        val key =
            indexKey ?: run {
                Logger.w { "EncryptedMetadataIndexService: key not provisioned, returning null" }
                return null
            }
        return try {
            val plaintext = ALIAS_DOMAIN + alias.toByteArray(Charsets.UTF_8)
            sivEncryptionManager.encrypt(plaintext, key)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "encryptAliasTag failed for alias=$alias" }
            null
        }
    }

    /**
     * Decrypts a previously encrypted RP ID tag to verify its contents.
     *
     * Primarily used for debugging and verification; the primary usage pattern
     * is to generate the tag for a known RP ID and compare bytes in SQL.
     *
     * @param encryptedTag The SIV-encrypted tag bytes, as returned by [encryptRpIdTag].
     * @return The original RP ID string, or null if decryption fails.
     */
    fun decryptRpIdTag(encryptedTag: ByteArray): String? {
        val key = indexKey ?: return null
        return try {
            val plaintext = sivEncryptionManager.decrypt(encryptedTag, key)
            // Strip the domain separator prefix
            val rpIdBytes = plaintext.drop(RP_ID_DOMAIN.size).toByteArray()
            String(rpIdBytes, Charsets.UTF_8)
        } catch (e: SecurityException) {
            Logger.w(e) { "decryptRpIdTag: authentication failed — tag may be tampered" }
            null
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "decryptRpIdTag failed" }
            null
        }
    }

    /**
     * Returns true if the index key is currently provisioned.
     */
    fun isKeyProvisioned(): Boolean = indexKey != null
}
