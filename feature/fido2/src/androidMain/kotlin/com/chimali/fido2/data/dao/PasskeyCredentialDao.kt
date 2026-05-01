package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.common.result.map
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredential as PasskeyCredentialEntity
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
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
    suspend fun insertCredential(credential: PasskeyCredential) {
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
    suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredentialEntity? =
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
     * Retrieves all credentials from the database (sync).
     */
    fun getAllCredentialsSync(): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries
            .selectAll()
            .executeAsList()

    /**
     * Updates an existing credential.
     */
    suspend fun updateCredential(credential: PasskeyCredential) {
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
    suspend fun updateLabel(
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
    suspend fun updatePublicKey(
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
    suspend fun updateSignCount(
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
    suspend fun updateLastUsedAt(credentialId: CredentialId) {
        database.passkeyCredentialQueries.updateLastUsedAt(
            lastUsedAt = timeProvider.now().toEpochMilliseconds(),
            id = credentialId.encoded,
        )
    }

    /**
     * Retrieves the sign count for a specific credential.
     */
    suspend fun getSignCount(credentialId: CredentialId): Long =
        database.passkeyCredentialQueries
            .getSignCount(id = credentialId.encoded)
            .executeAsOneOrNull() ?: 0L

    /**
     * Deletes a credential by its ID.
     */
    suspend fun deleteCredential(credentialId: CredentialId) {
        database.passkeyCredentialQueries.deleteById(credentialId.encoded)
    }

    /**
     * Deletes all credentials for a specific relying party.
     */
    suspend fun deleteCredentialsByRpId(rpId: RpId): Int {
        database.passkeyCredentialQueries.deleteByRpId(rpId.value)
        return getChangesCount()
    }

    /**
     * Deletes all credentials for a specific user.
     */
    suspend fun deleteCredentialsByUserId(userId: UserId): Int {
        database.passkeyCredentialQueries.deleteByUserId(userId.value)
        return getChangesCount()
    }

    /**
     * Searches credentials by user name or display name.
     */
    fun searchCredentials(
        query: String,
        rpId: RpId?,
    ): Flow<List<PasskeyCredentialEntity>> {
        val searchPattern = "%${query.trim()}%"
        return if (rpId != null) {
            database.passkeyCredentialQueries
                .searchByRpId(
                    query = searchPattern,
                    rpId = rpId.value,
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.passkeyCredentialQueries
                .searchAll(query = searchPattern)
                .asFlow()
                .map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves credentials that were created before a specific date.
     */
    fun getExpiredCredentials(cutoffDate: Instant): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectExpired(cutoffDate.toEpochMilliseconds())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves credentials that haven't been used in a specified number of days.
     */
    fun getUnusedCredentials(days: Int): Flow<List<PasskeyCredentialEntity>> {
        val cutoffDate = timeProvider.now().toEpochMilliseconds() - days.days.inWholeMilliseconds
        return database.passkeyCredentialQueries
            .selectUnused(cutoffDate)
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Retrieves credentials sorted by last used date (most recent first).
     */
    fun getCredentialsByLastUsed(limit: Int = 50): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectByLastUsed(limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves credentials sorted by creation date (newest first).
     */
    fun getCredentialsByCreationDate(limit: Int = 50): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectByCreationDate(limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Counts credentials for a specific relying party.
     */
    suspend fun countCredentialsByRpId(rpId: RpId): Long =
        database.passkeyCredentialQueries
            .countByRpId(rpId.value)
            .executeAsOne()

    /**
     * Counts credentials for a specific user.
     */
    suspend fun countCredentialsByUserId(userId: UserId): Long =
        database.passkeyCredentialQueries
            .countByUserId(userId.value)
            .executeAsOne()

    /**
     * Counts all credentials in the database.
     */
    suspend fun countAllCredentials(): Long =
        database.passkeyCredentialQueries
            .countAll()
            .executeAsOne()

    /**
     * Checks if a credential exists.
     */
    suspend fun credentialExists(credentialId: CredentialId): Boolean =
        database.passkeyCredentialQueries
            .existsById(credentialId.encoded)
            .executeAsOne()

    /**
     * Retrieves credentials with specific AAGUID.
     */
    fun getCredentialsByAaguid(aaguid: ByteArray): Flow<List<PasskeyCredentialEntity>> {
        val aaguidStr =
            java.util.Base64
                .getEncoder()
                .encodeToString(aaguid)
        return database.passkeyCredentialQueries
            .selectByAaguid(aaguidStr)
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Retrieves credentials with sign count above a threshold.
     */
    fun getCredentialsWithHighSignCount(threshold: Long): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .selectBySignCountAbove(threshold)
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates multiple credentials in a transaction.
     */
    suspend fun updateCredentials(credentials: List<PasskeyCredential>) {
        database.transaction {
            credentials.forEach { credential ->
                database.passkeyCredentialQueries.update(
                    // fallback
                    rpName = credential.rpId.value,
                    userName = credential.userName,
                    userDisplayName = credential.userDisplayName,
                    signCount = credential.signCount,
                    lastUsedAt = credential.lastUsedAt.toEpochMilliseconds(),
                    label = credential.label,
                    id = credential.id.encoded,
                )
            }
        }
    }

    /**
     * Deletes multiple credentials in a transaction.
     */
    suspend fun deleteCredentials(credentialIds: List<CredentialId>): Int {
        var deletedCount = 0
        database.transaction {
            credentialIds.forEach { credentialId ->
                database.passkeyCredentialQueries.deleteById(credentialId.encoded)
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Gets the number of changes from the last operation.
     */
    private suspend fun getChangesCount(): Int =
        database.passkeyCredentialQueries
            .changes()
            .executeAsOne()
            .toInt()
}
