package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Relying_party as RelyingPartyEntity
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.toOwnedUtf8
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun insertOrUpdateRelyingParty(rp: RelyingParty) = writeRelyingParty(rp, insert = true)

    /**
     * Retrieves a relying party by its ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getRelyingPartyById(rpId: RpId): RelyingPartyEntity? = getRelyingPartyByText(rpId.value)

    /** Borrows mutable RP text for one SQL lookup; the returned row transfers to its mapper/caller. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getRelyingPartyByText(rpId: CharSequence): RelyingPartyEntity? =
        withRpIdBytes(rpId) { bytes ->
            database.relyingPartyQueries.select_by_id(id = bytes).executeAsOneOrNull()
        }

    /**
     * Retrieves all relying parties from the database.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAllRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_all()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates an existing relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateRelyingParty(rp: RelyingParty) = writeRelyingParty(rp, insert = false)

    private fun writeRelyingParty(
        rp: RelyingParty,
        insert: Boolean,
    ) {
        val idBytes = rp.idText.toOwnedUtf8()
        var nameBytes: ByteArray? = null
        try {
            val ownedName = rp.nameText.toOwnedUtf8()
            nameBytes = ownedName
            val encrypted = metadataProtectionService.encryptRelyingPartyMetadata(rp.idText)
            if (insert) {
                database.transaction {
                    val existingCreation =
                        database.relyingPartyQueries
                            .select_created_at(idBytes)
                            .executeAsOneOrNull()
                    val incomingLastUsed = rp.lastUsedAt?.toEpochMilliseconds()
                    val activityTimeIsValid =
                        existingCreation == null || incomingLastUsed == null || incomingLastUsed >= existingCreation
                    require(activityTimeIsValid) {
                        "Last-used time cannot precede the stored relying-party creation time"
                    }
                    database.relyingPartyQueries.insert(
                        id = idBytes,
                        created_at = rp.createdAt.toEpochMilliseconds(),
                        last_used_at = incomingLastUsed,
                        updated_at =
                            maxOf(
                                timeProvider.now(),
                                rp.createdAt,
                                rp.lastUsedAt ?: rp.createdAt,
                            ).toEpochMilliseconds(),
                        credential_count = rp.credentialCount.toLong(),
                        icon_url = rp.iconUrl,
                        is_blocked = if (rp.isBlocked) 1L else 0L,
                        name = ownedName,
                        encrypted_metadata = encrypted,
                    )
                }
            } else {
                database.relyingPartyQueries.update(
                    id = idBytes,
                    last_used_at = rp.lastUsedAt?.toEpochMilliseconds(),
                    credential_count = rp.credentialCount.toLong(),
                    icon_url = rp.iconUrl,
                    is_blocked = if (rp.isBlocked) 1L else 0L,
                    name = ownedName,
                    encrypted_metadata = encrypted,
                )
            }
        } finally {
            idBytes.fill(0)
            nameBytes?.fill(0)
        }
    }

    private inline fun <T> withRpIdBytes(
        rpId: CharSequence,
        block: (ByteArray) -> T,
    ): T {
        val bytes = rpId.toOwnedUtf8()
        return try {
            block(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Updates the credential count for a relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ) {
        withRpIdBytes(rpId.value) { rpIdBytes ->
            database.relyingPartyQueries.update_credential_count(
                id = rpIdBytes,
                credential_count = count.toLong(),
                last_used_at = timeProvider.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * Increments the credential count for a relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun incrementCredentialCount(rpId: RpId) {
        withRpIdBytes(rpId.value) { rpIdBytes ->
            database.relyingPartyQueries.increment_credential_count(
                id = rpIdBytes,
                last_used_at = timeProvider.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * Decrements the credential count for a relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun decrementCredentialCount(rpId: RpId) {
        withRpIdBytes(rpId.value) { rpIdBytes ->
            database.relyingPartyQueries.decrement_credential_count(
                id = rpIdBytes,
                last_used_at = timeProvider.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * Updates the last used timestamp for a relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateLastUsedAt(rpId: RpId) {
        withRpIdBytes(rpId.value) { rpIdBytes ->
            database.relyingPartyQueries.update_last_used_at(
                id = rpIdBytes,
                last_used_at = timeProvider.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * Blocks or unblocks a relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateBlockedStatus(
        rpId: RpId,
        isBlocked: Boolean,
    ) {
        withRpIdBytes(rpId.value) { rpIdBytes ->
            database.relyingPartyQueries.update_blocked_status(
                id = rpIdBytes,
                is_blocked = if (isBlocked) 1L else 0L,
            )
        }
    }

    /**
     * Deletes a relying party by its ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun deleteRelyingParty(rpId: RpId) {
        withRpIdBytes(rpId.value) { rpIdBytes ->
            database.relyingPartyQueries.delete_by_id(id = rpIdBytes)
        }
    }

    /**
     * Searches relying parties by name.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRelyingPartiesWithCredentials(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_with_credentials()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties without credentials.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRelyingPartiesWithoutCredentials(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_without_credentials()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves blocked relying parties.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getBlockedRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_blocked()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves unblocked relying parties.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getUnblockedRelyingParties(): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_unblocked()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by credential count (most first).
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRelyingPartiesByCredentialCount(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_credential_count(limit = limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by last used date (most recent first).
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRelyingPartiesByLastUsed(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_last_used(limit = limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties sorted by creation date (newest first).
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRelyingPartiesByCreationDate(limit: Int = 50): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_creation_date(limit = limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties created within a date range.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun countAllRelyingParties(): Long =
        database.relyingPartyQueries
            .count_all()
            .executeAsOne()

    /**
     * Counts relying parties with credentials.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun countRelyingPartiesWithCredentials(): Long =
        database.relyingPartyQueries
            .count_with_credentials()
            .executeAsOne()

    /**
     * Counts blocked relying parties.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun countBlockedRelyingParties(): Long =
        database.relyingPartyQueries
            .count_blocked()
            .executeAsOne()

    /**
     * Checks if a relying party exists.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun relyingPartyExists(rpId: RpId): Boolean =
        withRpIdBytes(rpId.value) { rpIdBytes ->

            database.relyingPartyQueries
                .exists_by_id(id = rpIdBytes)
                .executeAsOne()
        }

    /**
     * Checks if a relying party is blocked.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun isRelyingPartyBlocked(rpId: RpId): Boolean =
        withRpIdBytes(rpId.value) { rpIdBytes ->

            database.relyingPartyQueries
                .is_blocked(id = rpIdBytes)
                .executeAsOne() > 0L
        }

    /**
     * Retrieves relying parties by domain.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRelyingPartiesByDomain(domain: String): Flow<List<RelyingPartyEntity>> =
        database.relyingPartyQueries
            .select_by_domain(domain = "%$domain%")
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves relying parties with specific credential count range.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateRelyingParties(rps: List<RelyingParty>) {
        database.transaction { rps.forEach { writeRelyingParty(it, insert = false) } }
    }

    /**
     * Deletes multiple relying parties in a transaction.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun deleteRelyingParties(rpIds: List<RpId>): Int {
        var deletedCount = 0
        database.transaction {
            rpIds.forEach { rpId ->
                withRpIdBytes(rpId.value) { bytes -> database.relyingPartyQueries.delete_by_id(id = bytes) }
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Cleans up relying parties without credentials.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
        val id: ByteArray,
        val name: ByteArray,
        val credentialCount: Long,
    ) {
        /** Erases the metadata transferred by the statistics query; callers release the complete result batch. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun eraseSensitiveText() {
            id.fill(0)
            name.fill(0)
        }
    }
}
