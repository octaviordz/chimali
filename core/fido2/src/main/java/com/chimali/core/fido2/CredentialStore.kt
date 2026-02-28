package com.chimali.core.fido2

/**
 * Transport-agnostic storage interface for CTAP2 credentials.
 *
 * This lives in the core FIDO2 module so that the CTAP engine can
 * remain independent of any particular database or feature module.
 */
data class StoredCredential(
    val credentialId: ByteArray,
    val identityId: String,
    val publicKey: ByteArray,
    val userHandle: ByteArray,
    val signCount: Long,
    val createdAt: String,
    val metadataJson: String
)

interface CredentialStore {
    fun saveCredential(credential: StoredCredential)

    fun getCredentialById(credentialId: ByteArray): StoredCredential?

    fun getCredentialsByIdentity(identityId: String): List<StoredCredential>

    fun incrementSignCount(credentialId: ByteArray)
}

