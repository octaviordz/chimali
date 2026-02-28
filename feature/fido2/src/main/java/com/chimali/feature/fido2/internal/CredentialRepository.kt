package com.chimali.feature.fido2.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.chimali.core.database.ChimaliDatabase
import com.chimali.core.database.Fido2Credential
import com.chimali.core.fido2.CredentialStore
import com.chimali.core.fido2.StoredCredential
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepository @Inject constructor(
    private val database: ChimaliDatabase
) : CredentialStore {
    private val queries = database.vaultQueries

    fun getCredentialsByIdentityAsFlow(identityId: String): Flow<List<Fido2Credential>> {
        return queries.getFido2CredentialsByIdentity(identityId)
            .asFlow()
            .mapToList(kotlinx.coroutines.Dispatchers.IO)
    }

    fun getCredentialEntityById(credentialId: ByteArray): Fido2Credential? {
        return queries.getFido2CredentialById(credentialId).executeAsOneOrNull()
    }

    fun saveCredentialEntity(credential: Fido2Credential) {
        queries.insertFido2Credential(
            credential.credential_id,
            credential.identity_id,
            credential.public_key,
            credential.user_handle,
            credential.sign_count,
            credential.created_at,
            credential.metadata_json
        )
    }

    /**
     * Placeholder for master-seed-backed key derivation.
     *
     * CTAP2 should eventually call into HDK/HdkManager to derive per-credential keys.
     */
    fun generateAndStoreKey(identityId: String, relyingPartyId: String): ByteArray {
        // T016: Use Master Seed logic to derive a new key pair
        // For now, return a placeholder public key
        return "placeholder_public_key".toByteArray()
    }

    fun incrementSignCountEntity(credentialId: ByteArray, currentCount: Long) {
        queries.updateFido2SignCount(currentCount + 1, credentialId)
    }

    // CredentialStore implementation for the CTAP engine (transport-agnostic)

    override fun saveCredential(credential: StoredCredential) {
        queries.insertFido2Credential(
            credential.credentialId,
            credential.identityId,
            credential.publicKey,
            credential.userHandle,
            credential.signCount,
            credential.createdAt,
            credential.metadataJson
        )
    }

    override fun getCredentialById(credentialId: ByteArray): StoredCredential? {
        val entity = queries.getFido2CredentialById(credentialId).executeAsOneOrNull() ?: return null
        return StoredCredential(
            credentialId = entity.credential_id,
            identityId = entity.identity_id,
            publicKey = entity.public_key,
            userHandle = entity.user_handle,
            signCount = entity.sign_count,
            createdAt = entity.created_at,
            metadataJson = entity.metadata_json
        )
    }

    override fun getCredentialsByIdentity(identityId: String): List<StoredCredential> {
        return queries.getFido2CredentialsByIdentity(identityId)
            .executeAsList()
            .map { entity ->
                StoredCredential(
                    credentialId = entity.credential_id,
                    identityId = entity.identity_id,
                    publicKey = entity.public_key,
                    userHandle = entity.user_handle,
                    signCount = entity.sign_count,
                    createdAt = entity.created_at,
                    metadataJson = entity.metadata_json
                )
            }
    }

    override fun incrementSignCount(credentialId: ByteArray) {
        val entity = queries.getFido2CredentialById(credentialId).executeAsOneOrNull() ?: return
        queries.updateFido2SignCount(entity.sign_count + 1, credentialId)
    }
}

