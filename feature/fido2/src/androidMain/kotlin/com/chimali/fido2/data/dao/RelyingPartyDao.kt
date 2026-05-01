package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.common.result.map
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.RelyingParty as RelyingPartyEntity
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import org.koin.core.annotation.Single

/**
 * Data Access Object for RelyingParty entities using SQLDelight.
 * Provides database operations for relying party metadata storage.
 */
@Single
class RelyingPartyDao(
    private val database: Fido2Database,
    private val timeProvider: TimeProvider,
) {
    /**
     * Inserts or updates a relying party in the database (Upsert).
     */
    suspend fun insertOrUpdateRelyingParty(rp: RelyingParty) {
        database.relyingPartyQueries.insert(
            id = rp.id.value,
            createdAt = rp.createdAt.toEpochMilliseconds(),
            lastUsedAt = rp.lastUsedAt?.toEpochMilliseconds(),
            credentialCount = rp.credentialCount.toLong(),
            iconUrl = rp.iconUrl,
            isBlocked = if (rp.isBlocked) 1L else 0L,
            name = rp.name,
        )
    }

    /**
     * Retrieves a relying party by its ID.
     */
    suspend fun getRelyingPartyById(rpId: RpId): RelyingPartyEntity? =
        database.relyingPartyQueries
            .selectById(rpId.value)
            .executeAsOneOrNull()

    /**
     * Retrieves all relying parties from the database.
     */
    fun getAllRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectAll()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates an existing relying party.
     */
    suspend fun updateRelyingParty(rp: RelyingParty) {
        database.relyingPartyQueries.update(
            lastUsedAt = rp.lastUsedAt?.toEpochMilliseconds(),
            credentialCount = rp.credentialCount.toLong(),
            iconUrl = rp.iconUrl,
            isBlocked = if (rp.isBlocked) 1L else 0L,
            name = rp.name,
            id = rp.id.value,
        )
    }

    /**
     * Updates the credential count for a relying party.
     */
    suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ) {
        database.relyingPartyQueries.updateCredentialCount(
            id = rpId.value,
            credentialCount = count.toLong(),
            lastUsedAt = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Increments the credential count for a relying party.
     */
    suspend fun incrementCredentialCount(rpId: RpId) {
        database.relyingPartyQueries.incrementCredentialCount(
            id = rpId.value,
            lastUsedAt = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Decrements the credential count for a relying party.
     */
    suspend fun decrementCredentialCount(rpId: RpId) {
        database.relyingPartyQueries.decrementCredentialCount(
            id = rpId.value,
            lastUsedAt = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Updates the last used timestamp for a relying party.
     */
    suspend fun updateLastUsedAt(rpId: RpId) {
        database.relyingPartyQueries.updateLastUsedAt(
            id = rpId.value,
            lastUsedAt = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Blocks or unblocks a relying party.
     */
    suspend fun updateBlockedStatus(
        rpId: RpId,
        isBlocked: Boolean,
    ) {
        database.relyingPartyQueries.updateBlockedStatus(
            id = rpId.value,
            isBlocked = if (isBlocked) 1L else 0L,
        )
    }

    /**
     * Deletes a relying party by its ID.
     */
    suspend fun deleteRelyingParty(rpId: RpId) {
        database.relyingPartyQueries.deleteById(rpId.value)
    }

    /**
     * Searches relying parties by name.
     */
    fun searchRelyingParties(query: String): Flow<List<RelyingPartyEntity>> {
        val searchPattern = "%${query.trim()}%"
        return database.relyingPartyQueries
            .searchByName(searchPattern)
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Retrieves relying parties with credentials.
     */
    fun getRelyingPartiesWithCredentials(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectWithCredentials()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties without credentials.
     */
    fun getRelyingPartiesWithoutCredentials(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectWithoutCredentials()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves blocked relying parties.
     */
    fun getBlockedRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectBlocked()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves unblocked relying parties.
     */
    fun getUnblockedRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectUnblocked()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by credential count (most first).
     */
    fun getRelyingPartiesByCredentialCount(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectByCredentialCount(limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by last used date (most recent first).
     */
    fun getRelyingPartiesByLastUsed(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectByLastUsed(limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by creation date (newest first).
     */
    fun getRelyingPartiesByCreationDate(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectByCreationDate(limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties created within a date range.
     */
    fun getRelyingPartiesByDateRange(
        startDate: Instant,
        endDate: Instant,
    ): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectByDateRange(
                start = startDate.toEpochMilliseconds(),
                end = endDate.toEpochMilliseconds(),
            ).asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties that haven't been used in a specified number of days.
     */
    fun getUnusedRelyingParties(days: Int): Flow<List<RelyingPartyEntity>> {
        val cutoffDate = timeProvider.now().toEpochMilliseconds() - days.days.inWholeMilliseconds
        return database.relyingPartyQueries
            .selectUnused(cutoffDate)
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Counts relying parties in the database.
     */
    suspend fun countAllRelyingParties(): Long =
        database.relyingPartyQueries
            .countAll()
            .executeAsOne()

    /**
     * Counts relying parties with credentials.
     */
    suspend fun countRelyingPartiesWithCredentials(): Long =
        database.relyingPartyQueries
            .countWithCredentials()
            .executeAsOne()

    /**
     * Counts blocked relying parties.
     */
    suspend fun countBlockedRelyingParties(): Long =
        database.relyingPartyQueries
            .countBlocked()
            .executeAsOne()

    /**
     * Checks if a relying party exists.
     */
    suspend fun relyingPartyExists(rpId: RpId): Boolean =
        database.relyingPartyQueries
            .existsById(rpId.value)
            .executeAsOne()

    /**
     * Checks if a relying party is blocked.
     */
    suspend fun isRelyingPartyBlocked(rpId: RpId): Boolean =
        database.relyingPartyQueries
            .isBlocked(rpId.value)
            .executeAsOne() > 0L

    /**
     * Retrieves relying parties by domain.
     */
    fun getRelyingPartiesByDomain(domain: String): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectByDomain("%$domain%")
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties with specific credential count range.
     */
    fun getRelyingPartiesByCredentialCountRange(
        minCount: Int,
        maxCount: Int,
    ): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .selectByCredentialCountRange(
                min = minCount.toLong(),
                max = maxCount.toLong(),
            ).asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates multiple relying parties in a transaction.
     */
    suspend fun updateRelyingParties(rps: List<RelyingParty>) {
        database.transaction {
            rps.forEach { rp ->
                database.relyingPartyQueries.update(
                    id = rp.id.value,
                    name = rp.name,
                    iconUrl = rp.iconUrl,
                    credentialCount = rp.credentialCount.toLong(),
                    lastUsedAt = rp.lastUsedAt?.toEpochMilliseconds(),
                    isBlocked = if (rp.isBlocked) 1L else 0L,
                )
            }
        }
    }

    /**
     * Deletes multiple relying parties in a transaction.
     */
    suspend fun deleteRelyingParties(rpIds: List<RpId>): Int {
        var deletedCount = 0
        database.transaction {
            rpIds.forEach { rpId ->
                database.relyingPartyQueries.deleteById(rpId.value)
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Cleans up relying parties without credentials.
     */
    suspend fun cleanupEmptyRelyingParties(): Int {
        database.relyingPartyQueries.deleteEmptyRelyingParties()
        return getChangesCount()
    }

    /**
     * Gets the number of changes from the last operation.
     */
    private suspend fun getChangesCount(): Int =
        database.relyingPartyQueries
            .changes()
            .executeAsOne()
            .toInt()

    /**
     * Retrieves relying party statistics.
     */
    suspend fun getRelyingPartyStatistics(): RelyingPartyStatistics {
        val total = countAllRelyingParties()
        val withCredentials = countRelyingPartiesWithCredentials()
        val blocked = countBlockedRelyingParties()
        val topRps =
            database.relyingPartyQueries
                .getTopRelyingParties(10)
                .executeAsList()

        return RelyingPartyStatistics(
            totalRelyingParties = total.toInt(),
            relyingPartiesWithCredentials = withCredentials.toInt(),
            blockedRelyingParties = blocked.toInt(),
            topRelyingParties =
                topRps.map {
                    TopRelyingParty(
                        id = it.id,
                        name = it.name,
                        credentialCount = it.credentialCount,
                    )
                },
        )
    }

    /**
     * Data class for relying party statistics.
     */
    data class RelyingPartyStatistics(
        val totalRelyingParties: Int,
        val relyingPartiesWithCredentials: Int,
        val blockedRelyingParties: Int,
        val topRelyingParties: List<TopRelyingParty>,
    )

    /**
     * Data class for top relying party information.
     */
    data class TopRelyingParty(
        val id: String,
        val name: String,
        val credentialCount: Long,
    )
}
