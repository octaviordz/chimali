package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredential as PasskeyCredentialEntity
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Data Access Object for PasskeyCredential entities using SQLDelight.
 * Provides database operations for credential metadata storage.
 */
@Single
class PasskeyCredentialDao(
    private val database: Fido2Database,
    private val timeProvider: TimeProvider,
) {
    /**
     * Inserts a new credential into the database.
     */
    fun insertCredential(credential: PasskeyCredential) {
        database.passkeyCredentialQueries.insert(
            id = credential.id.encoded,
            createdAt = credential.createdAt.toEpochMilliseconds(),
            lastUsedAt = credential.lastUsedAt.toEpochMilliseconds(),
            aaguid =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(credential.aaguid),
            coseAlgorithm = credential.coseAlgorithm.toLong(),
            credentialId = credential.id.encoded,
            credProtectPolicy = credential.credProtectPolicy.toLong(),
            label = credential.label,
            privateKeyAlias = credential.privateKeyAlias,
            publicKey =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(credential.publicKey.encoded),
            rpId = credential.rpId.value,
            // fallback
            rpName = credential.rpId.value,
            signCount = credential.signCount,
            userDisplayName = credential.userDisplayName,
            userId = credential.userId.value,
            userName = credential.userName,
        )
    }

    /**
     * Retrieves a credential by its ID.
     */
    fun getCredentialById(credentialId: CredentialId): PasskeyCredentialEntity? =
        database.passkeyCredentialQueries
            .selectById(credentialId.encoded)
            .executeAsOneOrNull()

    /**
     * Retrieves all credentials for a specific relying party.
     */
    fun getCredentialsByRpId(rpId: RpId): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectByRpId(rpId.value)
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves a paginated list of credentials for a specific relying party.
     */
    fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries.getPagedCredentialsByRpId(rpId.value, limit, offset).executeAsList()

    /**
     * Retrieves a paginated list of all credentials.
     */
    fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries.getPagedCredentials(limit, offset).executeAsList()

    /**
     * Retrieves all credentials for a specific RP and user (sync).
     */
    fun getCredentialsByRpIdAndUserId(
        rpId: RpId,
        userId: UserId,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries
            .selectByRpIdAndUserId(rpId.value, userId.value)
            .executeAsList()

    /**
     * Retrieves all credentials for a specific user.
     */
    fun getCredentialsByUserId(userId: UserId): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectByUserId(userId.value)
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves all credentials from the database.
     */
    fun getAllCredentials(): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectAll()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates an existing credential.
     */
    fun updateCredential(credential: PasskeyCredential) {
        database.passkeyCredentialQueries.update(
            lastUsedAt = credential.lastUsedAt.toEpochMilliseconds(),
            label = credential.label,
            // fallback
            rpName = credential.rpId.value,
            signCount = credential.signCount,
            userDisplayName = credential.userDisplayName,
            userName = credential.userName,
            id = credential.id.encoded,
        )
    }

    /**
     * Updates the custom label for a credential.
     */
    fun updateLabel(
        credentialId: CredentialId,
        label: String?,
    ) {
        database.passkeyCredentialQueries.updateLabel(
            label = label,
            id = credentialId.encoded,
        )
    }

    /**
     * Updates the public key for a credential.
     */
    fun updatePublicKey(
        credentialId: CredentialId,
        publicKey: String,
    ) {
        database.passkeyCredentialQueries.updatePublicKey(
            publicKey = publicKey,
            id = credentialId.encoded,
        )
    }

    /**
     * Updates the sign count for a credential.
     */
    fun updateSignCount(
        credentialId: CredentialId,
        signCount: Long,
    ) {
        database.passkeyCredentialQueries.updateSignCount(
            signCount = signCount,
            id = credentialId.encoded,
        )
    }

    /**
     * Updates the last used timestamp for a credential.
     */
    fun updateLastUsedAt(credentialId: CredentialId) {
        database.passkeyCredentialQueries.updateLastUsedAt(
            lastUsedAt = timeProvider.now().toEpochMilliseconds(),
            id = credentialId.encoded,
        )
    }

    /**
     * Retrieves the sign count for a specific credential.
     */
    fun getSignCount(credentialId: CredentialId): Long =
        database.passkeyCredentialQueries
            .getSignCount(id = credentialId.encoded)
            .executeAsOneOrNull() ?: 0L

    /**
     * Deletes a credential by its ID.
     */
    fun deleteCredential(credentialId: CredentialId) {
        database.passkeyCredentialQueries.deleteById(credentialId.encoded)
    }
}
