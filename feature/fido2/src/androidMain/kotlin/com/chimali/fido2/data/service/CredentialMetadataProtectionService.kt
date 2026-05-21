package com.chimali.fido2.data.service

import com.chimali.core.security.api.EncryptedMetadataService
import com.chimali.core.security.api.MetadataLookupTokenService
import org.koin.core.annotation.Single

/**
 * Orchestrates searchable metadata protection for the FIDO2 feature.
 *
 * This service wraps [MetadataLookupTokenService] and [EncryptedMetadataService]
 * to provide FIDO2-specific token generation and metadata payload encryption.
 */
@Single
class CredentialMetadataProtectionService(
    private val lookupTokenService: MetadataLookupTokenService,
    private val encryptedMetadataService: EncryptedMetadataService,
) {
    /**
     * Generates a deterministic exact-match index token for an RP ID.
     */
    fun getRpIdIndex(rpId: String): ByteArray =
        lookupTokenService.generateToken(MetadataLookupTokenService.DOMAIN_RP_ID, rpId)

    /**
     * Generates a deterministic exact-match index token for a User ID.
     */
    fun getUserIdIndex(userId: String): ByteArray =
        lookupTokenService.generateToken(MetadataLookupTokenService.DOMAIN_USER_ID, userId)

    /**
     * Encrypts the credential metadata payload using AES-256-GCM.
     * The RP ID is used as associated data (AAD) to bind the metadata to its relying party.
     *
     * @param jsonPayload The serialized metadata to encrypt.
     * @param rpId The RP ID to use as associated data.
     * @return The encrypted metadata envelope.
     */
    fun encryptMetadata(
        jsonPayload: String,
        rpId: String,
    ): ByteArray =
        encryptedMetadataService.encrypt(
            plaintext = jsonPayload.toByteArray(Charsets.UTF_8),
            associatedData = rpId.toByteArray(Charsets.UTF_8),
        )

    /**
     * Decrypts the credential metadata payload.
     *
     * @param encryptedBlob The AES-GCM encrypted envelope.
     * @param rpId The RP ID used as associated data during encryption.
     * @return The decrypted metadata string.
     * @throws SecurityException if authentication fails or the data is tampered.
     */
    fun decryptMetadata(
        encryptedBlob: ByteArray,
        rpId: String,
    ): String {
        val decrypted =
            encryptedMetadataService.decrypt(
                ciphertext = encryptedBlob,
                associatedData = rpId.toByteArray(Charsets.UTF_8),
            )
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Encrypts arbitrary relying party metadata without AAD binding.
     *
     * @param jsonPayload The serialized metadata to encrypt.
     * @return The encrypted metadata envelope.
     */
    fun encryptRelyingPartyMetadata(jsonPayload: String): ByteArray =
        encryptedMetadataService.encrypt(
            plaintext = jsonPayload.toByteArray(Charsets.UTF_8),
            associatedData = null,
        )

    /**
     * Decrypts relying party metadata.
     *
     * @param encryptedBlob The AES-GCM encrypted envelope.
     * @return The decrypted metadata string.
     */
    fun decryptRelyingPartyMetadata(encryptedBlob: ByteArray): String {
        val decrypted =
            encryptedMetadataService.decrypt(
                ciphertext = encryptedBlob,
                associatedData = null,
            )
        return String(decrypted, Charsets.UTF_8)
    }
}
