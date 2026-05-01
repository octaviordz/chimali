package com.chimali.fido2.data.repository

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrNull
import com.chimali.core.common.result.map
import com.chimali.core.common.result.onFailure
import com.chimali.core.common.result.onSuccess
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Implementation of CredentialRepository using SQLDelight for data persistence
 * and Android KeyStore for secure credential storage.
 */
@Single
class CredentialRepositoryImpl(
    private val passkeyCredentialDao: PasskeyCredentialDao,
    private val relyingPartyDao: RelyingPartyDao,
    private val userConsentRecordDao: UserConsentRecordDao,
    private val cryptoService: com.chimali.fido2.data.crypto.Fido2CryptoService,
    private val publicKeyDecoder: com.chimali.fido2.data.crypto.PublicKeyDecoder,
    private val corruptedKeyRepairWorker: com.chimali.fido2.data.worker.CorruptedKeyRepairWorker,
    private val timeProvider: TimeProvider,
    @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher,
) : CredentialRepository {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    companion object {
        private const val MAX_USER_CREDENTIALS_PER_RP = 10
        private const val RECENT_USAGE_CUTOFF_DAYS = 90L
        private const val EXPIRY_DAYS_THRESHOLD = 730L
        private const val UNKNOWN_ERROR = "Unknown error"
    }

    // ── Credential CRUD ──────────────────────────────────────────────────────

    override suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError> {
        return try {
            // Re-registering the same user for the same RP replaces the existing credential.
            val existingEntities =
                passkeyCredentialDao.getCredentialsByRpIdAndUserId(credential.rpId, credential.userId)
            for (old in existingEntities) {
                cryptoService.deleteCredentialKey(CredentialId.fromEncoded(old.id))
                passkeyCredentialDao.deleteCredential(CredentialId.fromEncoded(old.id))
            }

            // Key is already stored in Android KeyStore via Fido2CryptoService in the use case.
            // We only need to check it exists and save metadata.
            if (!cryptoService.keyExists(credential.id)) {
                return Outcome.Error(DomainError.StorageError("Key not found for alias: ${credential.id.encoded}"))
            }

            passkeyCredentialDao.insertCredential(credential)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to save credential id=${credential.id}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    override suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredential? {
        return try {
            val entity = passkeyCredentialDao.getCredentialById(credentialId) ?: return null
            entity.toDomainModel(publicKeyDecoder).getOrNull()
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get credential by ID: ${credentialId.encoded}" }
            null
        }
    }

    /** Interface returns Flow<PasskeyCredential> (individual items emitted from a list). */
    override suspend fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential> =
        flow {
            try {
                passkeyCredentialDao.getCredentialsByRpId(rpId).first().forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Failed to get credentials by RP ID: ${rpId.value}" }
            }
        }

    override suspend fun getCredentialsByUserId(userId: UserId): Flow<PasskeyCredential> =
        flow {
            try {
                passkeyCredentialDao.getCredentialsByUserId(userId).first().forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Failed to get credentials by User ID: ${userId.value}" }
            }
        }

    override suspend fun getAllCredentials(): Flow<PasskeyCredential> =
        flow {
            try {
                passkeyCredentialDao.getAllCredentials().first().forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder).onSuccess {
                        emit(it)
                    }
                }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Failed to get all credentials" }
            }
        }

    override suspend fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> =
        try {
            val entities = passkeyCredentialDao.getPagedCredentials(limit, offset)
            val validCredentials = mutableListOf<PasskeyCredential>()
            val corruptedIds = mutableListOf<CredentialId>()

            for (entity in entities) {
                entity
                    .toDomainModel(publicKeyDecoder)
                    .onSuccess { validCredentials.add(it) }
                    .onFailure { corruptedIds.add(CredentialId.fromEncoded(entity.id)) }
            }

            if (corruptedIds.isNotEmpty()) {
                scope.launch {
                    corruptedKeyRepairWorker.doWork(corruptedIds)
                }
            }

            Outcome.Success(validCredentials)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get paged credentials" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> =
        try {
            val entities = passkeyCredentialDao.getPagedCredentialsByRpId(rpId, limit, offset)
            val validCredentials = mutableListOf<PasskeyCredential>()
            val corruptedIds = mutableListOf<CredentialId>()

            for (entity in entities) {
                entity
                    .toDomainModel(publicKeyDecoder)
                    .onSuccess { validCredentials.add(it) }
                    .onFailure { corruptedIds.add(CredentialId.fromEncoded(entity.id)) }
            }

            if (corruptedIds.isNotEmpty()) {
                scope.launch {
                    corruptedKeyRepairWorker.doWork(corruptedIds)
                }
            }

            Outcome.Success(validCredentials)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get paged credentials for RP: ${rpId.value}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError> =
        try {
            cryptoService.deleteCredentialKey(credentialId)
            passkeyCredentialDao.deleteCredential(credentialId)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to delete credential id=${credentialId.encoded}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun credentialExists(
        rpId: RpId,
        userId: UserId,
    ): Boolean =
        try {
            passkeyCredentialDao.getCredentialsByRpIdAndUserId(rpId, userId).isNotEmpty()
        } catch (e: android.database.SQLException) {
            Logger.e(e) {
                "CredentialRepository: Error checking if credential exists " +
                    "for RP ${rpId.value} and User ${userId.value}"
            }
            false
        }

    // ── Sign count & usage ────────────────────────────────────────────────────

    override suspend fun updateSignCount(
        credentialId: CredentialId,
        newSignCount: Long,
    ): Outcome<Unit, DomainError> =
        try {
            passkeyCredentialDao.updateSignCount(credentialId, newSignCount)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to update sign count for id=${credentialId.encoded}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to update sign count", e))
        }

    override suspend fun updateLastUsedAt(credentialId: CredentialId): Outcome<Unit, DomainError> =
        try {
            passkeyCredentialDao.updateLastUsedAt(credentialId)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to update last used at for id=${credentialId.encoded}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to update last used", e))
        }

    override suspend fun getSignCount(credentialId: CredentialId): Outcome<Long, DomainError> =
        try {
            val count = passkeyCredentialDao.getSignCount(credentialId)
            Outcome.Success(count)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get sign count for ${credentialId.encoded}" }
            Outcome.Success(0L)
        }

    // ── Batch retrieval ───────────────────────────────────────────────────────

    override suspend fun getCredentialsForRp(rpId: RpId): Outcome<List<PasskeyCredential>, DomainError> =
        try {
            val entities = passkeyCredentialDao.getCredentialsByRpId(rpId).first()

            // Performance: derive public keys lazily, only for credentials that will
            // actually be used. Previously this called cryptoService.getPublicKey() for
            // every entity in the list — a full HDK derivation per credential — even
            // though GetAssertionUseCase only needs one (the MRU or the allow-listed one).
            //
            // The new approach derives the public key on-demand, per entity. The call
            // is still made here so that the returned list contains complete domain
            // objects, but we skip entities whose derivation fails (e.g., stale records
            // whose credential ID no longer maps to a valid seed path) rather than
            // failing the entire query.
            val credentials =
                entities.mapNotNull { entity ->
                    entity.toDomainModel(publicKeyDecoder).getOrNull()
                }
            Outcome.Success(credentials)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get credentials for RP: ${rpId.value}" }
            Outcome.Success(emptyList())
        }

    override suspend fun getCredentialSummariesForRp(rpId: RpId): Outcome<List<CredentialSummary>, DomainError> =
        try {
            // Pure DB read — no HDK derivation at all. This is the fast path used
            // by GetAssertionUseCase to list candidates for selection without incurring
            // key-derivation cost for every stored credential.
            val entities = passkeyCredentialDao.getCredentialsByRpId(rpId).first()
            val summaries =
                entities.map { entity ->
                    CredentialSummary(
                        id = entity.id,
                        rpId = RpId(entity.rpId),
                        credentialId = CredentialId.fromEncoded(entity.credentialId),
                        lastUsedAt =
                            kotlinx.datetime.Instant.fromEpochMilliseconds(
                                entity.lastUsedAt
                                    ?: entity.createdAt,
                            ),
                        coseAlgorithm = entity.coseAlgorithm.toInt(),
                        credProtectPolicy = entity.credProtectPolicy.toInt(),
                    )
                }
            Outcome.Success(summaries)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get credential summaries for RP: ${rpId.value}" }
            Outcome.Success(emptyList())
        }

    override suspend fun getCredentialsByIds(
        credentialIds: Set<CredentialId>,
        rpId: RpId?,
    ): Outcome<List<PasskeyCredential>, DomainError> =
        try {
            val credentials = credentialIds.mapNotNull { id -> getCredentialById(id) }
            val filtered = if (rpId != null) credentials.filter { it.rpId == rpId } else credentials
            Outcome.Success(filtered)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get credentials by IDs" }
            Outcome.Success(emptyList())
        }

    override suspend fun getCredentialCountByRpId(rpId: RpId): Int =
        try {
            passkeyCredentialDao.getCredentialsByRpId(rpId).first().size
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get credential count for RP: ${rpId.value}" }
            0
        }

    // ── Querying / searching ──────────────────────────────────────────────────

    override suspend fun searchCredentials(query: String): Flow<PasskeyCredential> =
        flow {
            try {
                val lq = query.lowercase()
                passkeyCredentialDao
                    .getAllCredentials()
                    .first()
                    .filter { it.userName.lowercase().contains(lq) || it.userDisplayName.lowercase().contains(lq) }
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Search failed for query: $query" }
            }
        }

    override suspend fun getRecentlyUnusedCredentials(days: Long): Flow<PasskeyCredential> =
        flow {
            try {
                val cutoff = timeProvider.now() - days.days
                passkeyCredentialDao
                    .getAllCredentials()
                    .first()
                    .filter { entity ->
                        val lastUsed =
                            entity.lastUsedAt?.let { Instant.fromEpochMilliseconds(it) }
                                ?: Instant.fromEpochMilliseconds(entity.createdAt)
                        lastUsed < cutoff
                    }.forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Failed to get recently unused credentials" }
            }
        }

    override suspend fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential> =
        flow {
            try {
                passkeyCredentialDao
                    .getAllCredentials()
                    .first()
                    .filter { false } // Not implemented in current schema
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Failed to get credentials requiring UV" }
            }
        }

    override suspend fun getExpiredCredentials(maxAgeDays: Long): Flow<PasskeyCredential> =
        flow {
            try {
                val cutoff = timeProvider.now() - maxAgeDays.days
                passkeyCredentialDao
                    .getAllCredentials()
                    .first()
                    .filter { Instant.fromEpochMilliseconds(it.createdAt) < cutoff }
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "CredentialRepository: Failed to get expired credentials" }
            }
        }

    override suspend fun cleanupExpiredCredentials(maxAgeDays: Long): Outcome<Int, DomainError> =
        try {
            var count = 0
            getExpiredCredentials(maxAgeDays).collect { credential ->
                val r = deleteCredential(credential.id)
                if (r is Outcome.Success) count++
            }
            Outcome.Success(count)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to cleanup expired credentials" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Cleanup failed", e))
        }

    // ── Validation / checks ───────────────────────────────────────────────────

    override suspend fun validateCredentialCreation(
        rpId: RpId,
        userId: UserId,
    ): Outcome<Unit, DomainError> {
        return try {
            val existingCredentials =
                getCredentialsByRpId(rpId).let { flow ->
                    val list = mutableListOf<PasskeyCredential>()
                    flow.collect { list.add(it) }
                    list
                }
            val userCreds = existingCredentials.filter { it.userId == userId }
            if (userCreds.size >= MAX_USER_CREDENTIALS_PER_RP) {
                return Outcome.Error(
                    DomainError.OperationDenied(
                        "Too many credentials ($MAX_USER_CREDENTIALS_PER_RP max) for this user and RP",
                    ),
                )
            }
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: validateCredentialCreation failed for rpId=${rpId.value}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    // ── Relying Party ─────────────────────────────────────────────────────────

    override suspend fun getRelyingParty(rpId: RpId): RelyingParty? =
        try {
            relyingPartyDao.getRelyingPartyById(rpId)?.toDomainModel()
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to get RP: ${rpId.value}" }
            null
        }

    override suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError> =
        try {
            relyingPartyDao.insertOrUpdateRelyingParty(rp)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to save relying party rpId=${rp.id.value}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun updateRelyingParty(
        rpId: RpId,
        update: (RelyingParty) -> RelyingParty,
    ): Outcome<Unit, DomainError> {
        return try {
            val currentRp =
                getRelyingParty(rpId)
                    ?: return Outcome.Error(DomainError.NotFound("RP not found: ${rpId.value}"))
            val updatedRp = update(currentRp)
            relyingPartyDao.updateRelyingParty(updatedRp)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to update relying party rpId=${rpId.value}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    // ── User Consent ──────────────────────────────────────────────────────────

    override suspend fun saveUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError> =
        try {
            userConsentRecordDao.insertConsent(consent)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to save user consent" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun getRecentUserConsent(
        rpId: RpId?,
        limit: Int,
    ): Flow<UserConsentRecord> =
        try {
            userConsentRecordDao
                .getRecentConsent(rpId, limit)
                .map { list ->
                    list.map { it.toDomainModel() }.firstOrNull()
                        ?: throw NoSuchElementException("Empty consent record list")
                }
        } catch (e: NoSuchElementException) {
            Logger.e(e) { "CredentialRepository: Failed to get recent user consent" }
            flowOf()
        }

    override suspend fun isUserConsentRequired(
        rpId: RpId,
        operationType: String,
    ): Boolean {
        // By default, user consent is always required for FIDO2 operations
        return true
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    override suspend fun getCredentialStatistics(): CredentialStatistics =
        try {
            val allCredentials = mutableListOf<PasskeyCredential>()
            getAllCredentials().collect { allCredentials.add(it) }

            val byRp = allCredentials.groupBy { it.rpId }.mapValues { it.value.size }
            val now = timeProvider.now()
            val cutoff = now - RECENT_USAGE_CUTOFF_DAYS.days
            val expired = allCredentials.count { (now - it.createdAt).inWholeDays > EXPIRY_DAYS_THRESHOLD }
            val recentlyUsed = allCredentials.count { it.lastUsedAt > cutoff }
            val needsUV = 0 // Not implemented in current schema
            val avgAge =
                if (allCredentials.isNotEmpty()) {
                    allCredentials.map { (now - it.createdAt).inWholeDays }.average()
                } else {
                    0.0
                }

            CredentialStatistics(
                totalCredentials = allCredentials.size,
                credentialsByRp = byRp,
                expiredCredentials = expired,
                recentlyUsedCredentials = recentlyUsed,
                credentialsRequiringUserVerification = needsUV,
                averageAgeDays = avgAge,
            )
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to calculate credential statistics" }
            CredentialStatistics(0, emptyMap(), 0, 0, 0, 0.0)
        }

    // ── Bulk deletion / reset ─────────────────────────────────────────────────

    override suspend fun deleteAllCredentials(rpId: RpId?): Outcome<Unit, DomainError> =
        try {
            val target: Flow<PasskeyCredential> =
                if (rpId != null) {
                    getCredentialsByRpId(rpId)
                } else {
                    getAllCredentials()
                }

            target.collect { credential -> deleteCredential(credential.id) }
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to delete all credentials for RP: ${rpId?.value}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun resetAuthenticator(): Outcome<Unit, DomainError> =
        try {
            getAllCredentials().collect { credential -> deleteCredential(credential.id) }
            // TODO: relyingPartyDao.deleteAll() / userConsentRecordDao.deleteAll() once DAOs support it
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to reset authenticator" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: UNKNOWN_ERROR, e))
        }

    override suspend fun updateLabel(
        credentialId: CredentialId,
        label: String?,
    ): Outcome<Unit, DomainError> =
        try {
            passkeyCredentialDao.updateLabel(credentialId, label)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "CredentialRepository: Failed to update label for id=${credentialId.encoded}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to update label", e))
        }
}
