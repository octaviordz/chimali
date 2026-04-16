package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import com.chimali.fido2.data.database.PasskeyCredential as PasskeyCredentialEntity

/**
 * Data Access Object for PasskeyCredential entities using SQLDelight.
 * Provides database operations for credential metadata storage.
 */
@Singleton
class PasskeyCredentialDao
    @Inject
    constructor(
        private val database: Fido2Database,
    ) {
        /**
         * Inserts a new credential into the database.
         */
        suspend fun insertCredential(credential: PasskeyCredential) {
            database.passkeyCredentialQueries.insert(
                id = credential.id,
                createdAt = credential.createdAt.toEpochMilli(),
                lastUsedAt = credential.lastUsedAt.toEpochMilli(),
                aaguid = java.util.Base64.getEncoder().encodeToString(credential.aaguid),
                coseAlgorithm = credential.coseAlgorithm.toLong(),
                credentialId = java.util.Base64.getEncoder().encodeToString(credential.credentialId),
                credProtectPolicy = credential.credProtectPolicy.toLong(),
                label = credential.label,
                privateKeyAlias = credential.privateKeyAlias,
                publicKey = java.util.Base64.getEncoder().encodeToString(credential.publicKey.encoded),
                rpId = credential.rpId,
                rpName = credential.rpId, // fallback
                signCount = credential.signCount,
                userDisplayName = credential.userDisplayName,
                userId = credential.userId,
                userName = credential.userName,
            )
        }

        /**
         * Retrieves a credential by its ID.
         */
        suspend fun getCredentialById(credentialId: String): PasskeyCredentialEntity? {
            return database.passkeyCredentialQueries.selectById(credentialId)
                .executeAsOneOrNull()
        }

        /**
         * Retrieves all credentials for a specific relying party.
         */
        fun getCredentialsByRpId(rpId: String): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectByRpId(rpId)
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Retrieves all credentials for a specific user.
         */
        fun getCredentialsByUserId(userId: String): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectByUserId(userId)
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Retrieves all credentials from the database.
         */
        fun getAllCredentials(): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectAll()
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Updates an existing credential.
         */
        suspend fun updateCredential(credential: PasskeyCredential) {
            database.passkeyCredentialQueries.update(
                lastUsedAt = credential.lastUsedAt.toEpochMilli(),
                label = credential.label,
                rpName = credential.rpId, // fallback
                signCount = credential.signCount,
                userDisplayName = credential.userDisplayName,
                userName = credential.userName,
                id = credential.id,
            )
        }

        /**
         * Updates the custom label for a credential.
         */
        suspend fun updateLabel(
            credentialId: String,
            label: String?,
        ) {
            database.passkeyCredentialQueries.updateLabel(
                label = label,
                id = credentialId,
            )
        }

        /**
         * Updates the sign count for a credential.
         */
        suspend fun updateSignCount(
            credentialId: String,
            signCount: Long,
        ) {
            database.passkeyCredentialQueries.updateSignCount(
                signCount = signCount,
                id = credentialId,
            )
        }

        /**
         * Updates the last used timestamp for a credential.
         */
        suspend fun updateLastUsedAt(credentialId: String) {
            database.passkeyCredentialQueries.updateLastUsedAt(
                lastUsedAt = Instant.now().toEpochMilli(),
                id = credentialId,
            )
        }

        /**
         * Retrieves the sign count for a specific credential.
         */
        suspend fun getSignCount(credentialId: String): Long {
            return database.passkeyCredentialQueries.getSignCount(id = credentialId)
                .executeAsOneOrNull() ?: 0L
        }

        /**
         * Deletes a credential by its ID.
         */
        suspend fun deleteCredential(credentialId: String) {
            database.passkeyCredentialQueries.deleteById(credentialId)
        }

        /**
         * Deletes all credentials for a specific relying party.
         */
        suspend fun deleteCredentialsByRpId(rpId: String): Int {
            database.passkeyCredentialQueries.deleteByRpId(rpId)
            return getChangesCount()
        }

        /**
         * Deletes all credentials for a specific user.
         */
        suspend fun deleteCredentialsByUserId(userId: String): Int {
            database.passkeyCredentialQueries.deleteByUserId(userId)
            return getChangesCount()
        }

        /**
         * Searches credentials by user name or display name.
         */
        fun searchCredentials(
            query: String,
            rpId: String?,
        ): Flow<List<PasskeyCredentialEntity>> {
            val searchPattern = "%${query.trim()}%"
            return if (rpId != null) {
                database.passkeyCredentialQueries.searchByRpId(
                    query = searchPattern,
                    rpId = rpId,
                ).asFlow().map { query -> query.executeAsList() }
            } else {
                database.passkeyCredentialQueries.searchAll(query = searchPattern)
                    .asFlow().map { query -> query.executeAsList() }
            }
        }

        /**
         * Retrieves credentials that were created before a specific date.
         */
        fun getExpiredCredentials(cutoffDate: Instant): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectExpired(cutoffDate.toEpochMilli())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Retrieves credentials that haven't been used in a specified number of days.
         */
        fun getUnusedCredentials(days: Int): Flow<List<PasskeyCredentialEntity>> {
            val cutoffDate = Instant.now().minusSeconds(days.toLong() * 24 * 60 * 60)
            return database.passkeyCredentialQueries.selectUnused(cutoffDate.toEpochMilli())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Retrieves credentials sorted by last used date (most recent first).
         */
        fun getCredentialsByLastUsed(limit: Int = 50): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectByLastUsed(limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Retrieves credentials sorted by creation date (newest first).
         */
        fun getCredentialsByCreationDate(limit: Int = 50): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectByCreationDate(limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Counts credentials for a specific relying party.
         */
        suspend fun countCredentialsByRpId(rpId: String): Long {
            return database.passkeyCredentialQueries.countByRpId(rpId)
                .executeAsOne()
        }

        /**
         * Counts credentials for a specific user.
         */
        suspend fun countCredentialsByUserId(userId: String): Long {
            return database.passkeyCredentialQueries.countByUserId(userId)
                .executeAsOne()
        }

        /**
         * Counts all credentials in the database.
         */
        suspend fun countAllCredentials(): Long {
            return database.passkeyCredentialQueries.countAll()
                .executeAsOne()
        }

        /**
         * Checks if a credential exists.
         */
        suspend fun credentialExists(credentialId: String): Boolean {
            return database.passkeyCredentialQueries.existsById(credentialId)
                .executeAsOne()
        }

        /**
         * Retrieves credentials with specific AAGUID.
         */
        fun getCredentialsByAaguid(aaguid: ByteArray): Flow<List<PasskeyCredentialEntity>> {
            val aaguidStr = java.util.Base64.getEncoder().encodeToString(aaguid)
            return database.passkeyCredentialQueries.selectByAaguid(aaguidStr)
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Retrieves credentials with sign count above a threshold.
         */
        fun getCredentialsWithHighSignCount(threshold: Long): Flow<List<PasskeyCredentialEntity>> {
            return database.passkeyCredentialQueries.selectBySignCountAbove(threshold)
                .asFlow()
                .map { query -> query.executeAsList() }
        }

        /**
         * Updates multiple credentials in a transaction.
         */
        suspend fun updateCredentials(credentials: List<PasskeyCredential>) {
            database.transaction {
                credentials.forEach { credential ->
                    database.passkeyCredentialQueries.update(
                        rpName = credential.rpId, // fallback
                        userName = credential.userName,
                        userDisplayName = credential.userDisplayName,
                        signCount = credential.signCount,
                        lastUsedAt = credential.lastUsedAt.toEpochMilli(),
                        label = credential.label,
                        id = credential.id,
                    )
                }
            }
        }

        /**
         * Deletes multiple credentials in a transaction.
         */
        suspend fun deleteCredentials(credentialIds: List<String>): Int {
            var deletedCount = 0
            database.transaction {
                credentialIds.forEach { credentialId ->
                    database.passkeyCredentialQueries.deleteById(credentialId)
                    deletedCount++
                }
            }
            return deletedCount
        }

        /**
         * Gets the number of changes from the last operation.
         */
        private suspend fun getChangesCount(): Int {
            return database.passkeyCredentialQueries.changes()
                .executeAsOne().toInt()
        }

        /**
         * Retrieves credential statistics.
         */
        suspend fun getCredentialStatistics(): CredentialStatistics {
            val total = countAllCredentials()
            val byRp =
                database.passkeyCredentialQueries.getStatisticsByRpId()
                    .executeAsList()
                    .associate { it.rpId to it.count }
            val byUser =
                database.passkeyCredentialQueries.getStatisticsByUserId()
                    .executeAsList()
                    .associate { it.userId to it.count }

            return CredentialStatistics(
                totalCredentials = total.toInt(),
                credentialsByRp = byRp,
                credentialsByUser = byUser,
            )
        }

        /**
         * Data class for credential statistics.
         */
        data class CredentialStatistics(
            val totalCredentials: Int,
            val credentialsByRp: Map<String, Long>,
            val credentialsByUser: Map<String, Long>,
        )
    }
