package com.chimali.fido2.data.dao

import com.chimali.fido2.data.database.RelyingPartyEntity
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.data.database.Fido2Database
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Access Object for RelyingParty entities using SQLDelight.
 * Provides database operations for relying party metadata storage.
 */
@Singleton
class RelyingPartyDao @Inject constructor(
    private val database: Fido2Database
) {
    
    /**
     * Inserts a new relying party into the database.
     */
    suspend fun insertRelyingParty(rp: RelyingParty) {
        database.relyingPartyQueries.insert(
            id = rp.id,
            name = rp.name,
            iconUrl = rp.iconUrl,
            credentialCount = rp.credentialCount.toLong(),
            createdAt = rp.createdAt.toEpochMilli(),
            lastUsedAt = rp.lastUsedAt?.toEpochMilli(),
            isBlocked = rp.isBlocked
        )
    }
    
    /**
     * Retrieves a relying party by its ID.
     */
    suspend fun getRelyingPartyById(rpId: String): RelyingPartyEntity? {
        return database.relyingPartyQueries.selectById(rpId)
            .executeAsOneOrNull()
    }
    
    /**
     * Retrieves all relying parties from the database.
     */
    fun getAllRelyingParties(): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectAll()
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Updates an existing relying party.
     */
    suspend fun updateRelyingParty(rp: RelyingParty) {
        database.relyingPartyQueries.update(
            id = rp.id,
            name = rp.name,
            iconUrl = rp.iconUrl,
            credentialCount = rp.credentialCount.toLong(),
            lastUsedAt = rp.lastUsedAt?.toEpochMilli(),
            isBlocked = rp.isBlocked
        )
    }
    
    /**
     * Updates the credential count for a relying party.
     */
    suspend fun updateCredentialCount(rpId: String, count: Int) {
        database.relyingPartyQueries.updateCredentialCount(
            rpId = rpId,
            credentialCount = count.toLong(),
            lastUsedAt = Instant.now().toEpochMilli()
        )
    }
    
    /**
     * Increments the credential count for a relying party.
     */
    suspend fun incrementCredentialCount(rpId: String) {
        database.relyingPartyQueries.incrementCredentialCount(
            rpId = rpId,
            lastUsedAt = Instant.now().toEpochMilli()
        )
    }
    
    /**
     * Decrements the credential count for a relying party.
     */
    suspend fun decrementCredentialCount(rpId: String) {
        database.relyingPartyQueries.decrementCredentialCount(
            rpId = rpId,
            lastUsedAt = Instant.now().toEpochMilli()
        )
    }
    
    /**
     * Updates the last used timestamp for a relying party.
     */
    suspend fun updateLastUsedAt(rpId: String) {
        database.relyingPartyQueries.updateLastUsedAt(
            rpId = rpId,
            lastUsedAt = Instant.now().toEpochMilli()
        )
    }
    
    /**
     * Blocks or unblocks a relying party.
     */
    suspend fun updateBlockedStatus(rpId: String, isBlocked: Boolean) {
        database.relyingPartyQueries.updateBlockedStatus(
            rpId = rpId,
            isBlocked = isBlocked
        )
    }
    
    /**
     * Deletes a relying party by its ID.
     */
    suspend fun deleteRelyingParty(rpId: String) {
        database.relyingPartyQueries.deleteById(rpId)
    }
    
    /**
     * Searches relying parties by name.
     */
    fun searchRelyingParties(query: String): Flow<List<RelyingPartyEntity>> {
        val searchPattern = "%${query.trim()}%"
        return database.relyingPartyQueries.searchByName(searchPattern)
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties with credentials.
     */
    fun getRelyingPartiesWithCredentials(): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectWithCredentials()
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties without credentials.
     */
    fun getRelyingPartiesWithoutCredentials(): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectWithoutCredentials()
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves blocked relying parties.
     */
    fun getBlockedRelyingParties(): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectBlocked()
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves unblocked relying parties.
     */
    fun getUnblockedRelyingParties(): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectUnblocked()
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties sorted by credential count (most first).
     */
    fun getRelyingPartiesByCredentialCount(limit: Int = 50): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectByCredentialCount(limit)
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties sorted by last used date (most recent first).
     */
    fun getRelyingPartiesByLastUsed(limit: Int = 50): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectByLastUsed(limit)
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties sorted by creation date (newest first).
     */
    fun getRelyingPartiesByCreationDate(limit: Int = 50): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectByCreationDate(limit)
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties created within a date range.
     */
    fun getRelyingPartiesByDateRange(
        startDate: Instant,
        endDate: Instant
    ): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectByDateRange(
            startDate = startDate.toEpochMilli(),
            endDate = endDate.toEpochMilli()
        ).asFlow().map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties that haven't been used in a specified number of days.
     */
    fun getUnusedRelyingParties(days: Int): Flow<List<RelyingPartyEntity>> {
        val cutoffDate = Instant.now().minusSeconds(days.toLong() * 24 * 60 * 60)
        return database.relyingPartyQueries.selectUnused(cutoffDate.toEpochMilli())
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Counts relying parties in the database.
     */
    suspend fun countAllRelyingParties(): Long {
        return database.relyingPartyQueries.countAll()
            .executeAsOne()
    }
    
    /**
     * Counts relying parties with credentials.
     */
    suspend fun countRelyingPartiesWithCredentials(): Long {
        return database.relyingPartyQueries.countWithCredentials()
            .executeAsOne()
    }
    
    /**
     * Counts blocked relying parties.
     */
    suspend fun countBlockedRelyingParties(): Long {
        return database.relyingPartyQueries.countBlocked()
            .executeAsOne()
    }
    
    /**
     * Checks if a relying party exists.
     */
    suspend fun relyingPartyExists(rpId: String): Boolean {
        return database.relyingPartyQueries.existsById(rpId)
            .executeAsOne()
    }
    
    /**
     * Checks if a relying party is blocked.
     */
    suspend fun isRelyingPartyBlocked(rpId: String): Boolean {
        return database.relyingPartyQueries.isBlocked(rpId)
            .executeAsOne()
    }
    
    /**
     * Retrieves relying parties by domain.
     */
    fun getRelyingPartiesByDomain(domain: String): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectByDomain("%$domain%")
            .asFlow()
            .map { query -> query.executeAsList() }
    }
    
    /**
     * Retrieves relying parties with specific credential count range.
     */
    fun getRelyingPartiesByCredentialCountRange(
        minCount: Int,
        maxCount: Int
    ): Flow<List<RelyingPartyEntity>> {
        return database.relyingPartyQueries.selectByCredentialCountRange(
            minCount = minCount.toLong(),
            maxCount = maxCount.toLong()
        ).asFlow().map { query -> query.executeAsList() }
    }
    
    /**
     * Updates multiple relying parties in a transaction.
     */
    suspend fun updateRelyingParties(rps: List<RelyingParty>) {
        database.transaction {
            rps.forEach { rp ->
                updateRelyingParty(rp)
            }
        }
    }
    
    /**
     * Deletes multiple relying parties in a transaction.
     */
    suspend fun deleteRelyingParties(rpIds: List<String>): Int {
        var deletedCount = 0
        database.transaction {
            rpIds.forEach { rpId ->
                deleteRelyingParty(rpId)
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
    private suspend fun getChangesCount(): Int {
        return database.relyingPartyQueries.changes()
            .executeAsOne()
    }
    
    /**
     * Retrieves relying party statistics.
     */
    suspend fun getRelyingPartyStatistics(): RelyingPartyStatistics {
        val total = countAllRelyingParties()
        val withCredentials = countRelyingPartiesWithCredentials()
        val blocked = countBlockedRelyingParties()
        val topRps = database.relyingPartyQueries.getTopRelyingParties(10)
            .executeAsList()
        
        return RelyingPartyStatistics(
            totalRelyingParties = total.toInt(),
            relyingPartiesWithCredentials = withCredentials.toInt(),
            blockedRelyingParties = blocked.toInt(),
            topRelyingParties = topRps.map { 
                TopRelyingParty(
                    id = it.id,
                    name = it.name,
                    credentialCount = it.credential_count
                )
            }
        )
    }
    
    /**
     * Data class for relying party statistics.
     */
    data class RelyingPartyStatistics(
        val totalRelyingParties: Int,
        val relyingPartiesWithCredentials: Int,
        val blockedRelyingParties: Int,
        val topRelyingParties: List<TopRelyingParty>
    )
    
    /**
     * Data class for top relying party information.
     */
    data class TopRelyingParty(
        val id: String,
        val name: String,
        val credentialCount: Long
    )
}
