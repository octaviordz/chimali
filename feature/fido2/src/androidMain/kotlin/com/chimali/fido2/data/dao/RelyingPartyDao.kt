package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.common.result.map
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Relying_party as RelyingPartyEntity
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
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
    private val metadataProtectionService: CredentialMetadataProtectionService,
) {
    /**
     * Inserts or updates a relying party in the database (Upsert).
     */
    suspend fun insertOrUpdateRelyingParty(rp: RelyingParty) {
        val metadataJson = """{"id":"${rp.id.value}"}"""
        val encryptedMetadata = metadataProtectionService.encryptRelyingPartyMetadata(metadataJson)

        database.relyingPartyQueries.insert(
            id = rp.id.value,
            created_at = rp.createdAt.toEpochMilliseconds(),
            last_used_at = rp.lastUsedAt?.toEpochMilliseconds(),
            credential_count = rp.credentialCount.toLong(),
            icon_url = rp.iconUrl,
            is_blocked = if (rp.isBlocked) 1L else 0L,
            name = rp.name,
            encrypted_metadata = encryptedMetadata,
        )
    }

    /**
     * Retrieves a relying party by its ID.
     */
    suspend fun getRelyingPartyById(rpId: RpId): RelyingPartyEntity? =
        database.relyingPartyQueries
            .select_by_id(id = rpId.value)
            .executeAsOneOrNull()

    /**
     * Retrieves all relying parties from the database.
     */
    fun getAllRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_all()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates an existing relying party.
     */
    suspend fun updateRelyingParty(rp: RelyingParty) {
        val metadataJson = """{"id":"${rp.id.value}"}"""
        val encryptedMetadata = metadataProtectionService.encryptRelyingPartyMetadata(metadataJson)

        database.relyingPartyQueries.update(
            last_used_at = rp.lastUsedAt?.toEpochMilliseconds(),
            credential_count = rp.credentialCount.toLong(),
            icon_url = rp.iconUrl,
            is_blocked = if (rp.isBlocked) 1L else 0L,
            name = rp.name,
            encrypted_metadata = encryptedMetadata,
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
        database.relyingPartyQueries.update_credential_count(
            id = rpId.value,
            credential_count = count.toLong(),
            last_used_at = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Increments the credential count for a relying party.
     */
    suspend fun incrementCredentialCount(rpId: RpId) {
        database.relyingPartyQueries.increment_credential_count(
            id = rpId.value,
            last_used_at = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Decrements the credential count for a relying party.
     */
    suspend fun decrementCredentialCount(rpId: RpId) {
        database.relyingPartyQueries.decrement_credential_count(
            id = rpId.value,
            last_used_at = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Updates the last used timestamp for a relying party.
     */
    suspend fun updateLastUsedAt(rpId: RpId) {
        database.relyingPartyQueries.update_last_used_at(
            id = rpId.value,
            last_used_at = timeProvider.now().toEpochMilliseconds(),
        )
    }

    /**
     * Blocks or unblocks a relying party.
     */
    suspend fun updateBlockedStatus(
        rpId: RpId,
        isBlocked: Boolean,
    ) {
        database.relyingPartyQueries.update_blocked_status(
            id = rpId.value,
            is_blocked = if (isBlocked) 1L else 0L,
        )
    }

    /**
     * Deletes a relying party by its ID.
     */
    suspend fun deleteRelyingParty(rpId: RpId) {
        database.relyingPartyQueries.delete_by_id(id = rpId.value)
    }

    /**
     * Searches relying parties by name.
     */
    fun searchRelyingParties(query: String): Flow<List<RelyingPartyEntity>> {
        val searchPattern = "%${query.trim()}%"
        return database.relyingPartyQueries
            .search_by_name(query = searchPattern)
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Retrieves relying parties with credentials.
     */
    fun getRelyingPartiesWithCredentials(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_with_credentials()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties without credentials.
     */
    fun getRelyingPartiesWithoutCredentials(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_without_credentials()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves blocked relying parties.
     */
    fun getBlockedRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_blocked()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves unblocked relying parties.
     */
    fun getUnblockedRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_unblocked()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by credential count (most first).
     */
    fun getRelyingPartiesByCredentialCount(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_credential_count(limit = limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by last used date (most recent first).
     */
    fun getRelyingPartiesByLastUsed(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_last_used(limit = limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by creation date (newest first).
     */
    fun getRelyingPartiesByCreationDate(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_creation_date(limit = limit.toLong())
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
            .select_by_date_range(
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
            .select_unused(cutoff_date = cutoffDate)
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Counts relying parties in the database.
     */
    suspend fun countAllRelyingParties(): Long =
        database.relyingPartyQueries
            .count_all()
            .executeAsOne()

    /**
     * Counts relying parties with credentials.
     */
    suspend fun countRelyingPartiesWithCredentials(): Long =
        database.relyingPartyQueries
            .count_with_credentials()
            .executeAsOne()

    /**
     * Counts blocked relying parties.
     */
    suspend fun countBlockedRelyingParties(): Long =
        database.relyingPartyQueries
            .count_blocked()
            .executeAsOne()

    /**
     * Checks if a relying party exists.
     */
    suspend fun relyingPartyExists(rpId: RpId): Boolean =
        database.relyingPartyQueries
            .exists_by_id(id = rpId.value)
            .executeAsOne()

    /**
     * Checks if a relying party is blocked.
     */
    suspend fun isRelyingPartyBlocked(rpId: RpId): Boolean =
        database.relyingPartyQueries
            .is_blocked(id = rpId.value)
            .executeAsOne() > 0L

    /**
     * Retrieves relying parties by domain.
     */
    fun getRelyingPartiesByDomain(domain: String): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_domain(domain = "%$domain%")
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
            .select_by_credential_count_range(
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
                val metadataJson = """{"id":"${rp.id.value}"}"""
                val encryptedMetadata = metadataProtectionService.encryptRelyingPartyMetadata(metadataJson)

                database.relyingPartyQueries.update(
                    id = rp.id.value,
                    name = rp.name,
                    icon_url = rp.iconUrl,
                    credential_count = rp.credentialCount.toLong(),
                    last_used_at = rp.lastUsedAt?.toEpochMilliseconds(),
                    is_blocked = if (rp.isBlocked) 1L else 0L,
                    encrypted_metadata = encryptedMetadata,
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
                database.relyingPartyQueries.delete_by_id(id = rpId.value)
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Cleans up relying parties without credentials.
     */
    suspend fun cleanupEmptyRelyingParties(): Int {
        database.relyingPartyQueries.delete_empty_relying_parties()
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

    companion object {
        private const val DEFAULT_TOP_RPS_LIMIT = 10L
    }

    /**
     * Retrieves relying party statistics.
     */
    suspend fun getRelyingPartyStatistics(): RelyingPartyStatistics {
        val total = countAllRelyingParties()
        val withCredentials = countRelyingPartiesWithCredentials()
        val blocked = countBlockedRelyingParties()
        val topRps =
            database.relyingPartyQueries
                .get_top_relying_parties(limit = DEFAULT_TOP_RPS_LIMIT)
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
                        credentialCount = it.credential_count,
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
