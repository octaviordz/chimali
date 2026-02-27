package com.chimali.feature.fido2.internal

import com.chimali.core.database.ChimaliDatabase
import com.chimali.core.database.Fido2Credential
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepository @Inject constructor(
    private val database: ChimaliDatabase
) {
    private val queries = database.vaultQueries

    fun getCredentialsByIdentity(identityId: String): Flow<List<Fido2Credential>> {
        return queries.getFido2CredentialsByIdentity(identityId).asFlow().mapToList()
    }

    fun getCredentialById(credentialId: ByteArray): Fido2Credential? {
        return queries.getFido2CredentialById(credentialId).executeAsOneOrNull()
    }

    fun saveCredential(credential: Fido2Credential) {
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

    fun generateAndStoreKey(identityId: String, relyingPartyId: String): ByteArray {
        // T016: Use Master Seed logic to derive a new key pair
        // For now, return a placeholder public key
        return "placeholder_public_key".toByteArray()
    }

    fun incrementSignCount(credentialId: ByteArray, currentCount: Long) {
        queries.updateFido2SignCount(currentCount + 1, credentialId)
    }
}
