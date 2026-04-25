package com.chimali.fido2.data.repository

import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.model.CredentialSummary
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.model.UserConsentRecord
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import org.koin.core.annotation.Single
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
) : CredentialRepository {
    companion object {
        private const val MAX_USER_CREDENTIALS_PER_RP = 10
        private const val RECENT_USAGE_CUTOFF_DAYS = 90L
        private const val EXPIRY_DAYS_THRESHOLD = 730L
    }

    // ── Credential CRUD ──────────────────────────────────────────────────────

    override suspend fun saveCredential(credential: PasskeyCredential): Result<Unit> {
        return try {
            // Re-registering the same user for the same RP replaces the existing credential.
            val existingEntities =
                passkeyCredentialDao.getCredentialsByRpId(credential.rpId)
                    .first()
                    .filter { it.userId == credential.userId }
            for (old in existingEntities) {
                cryptoService.deleteCredentialKey(CredentialId.fromString(old.id))
                passkeyCredentialDao.deleteCredential(old.id)
            }

            // Key is already stored in Android KeyStore via Fido2CryptoService in the use case.
            // We only need to check it exists and save metadata.
            if (!cryptoService.keyExists(CredentialId.fromString(credential.id))) {
                return Result.failure(Fido2Exception.KeyNotFound("Key not found for alias: ${credential.id}"))
            }

            passkeyCredentialDao.insertCredential(credential)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialStorageFailed(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun getCredentialById(credentialId: String): PasskeyCredential? {
        return try {
            val entity = passkeyCredentialDao.getCredentialById(credentialId) ?: return null
            entity.toDomainModel(publicKeyDecoder).getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /** Interface returns Flow<PasskeyCredential> (individual items emitted from a list). */
    override suspend fun getCredentialsByRpId(rpId: String): Flow<PasskeyCredential> {
        return flow {
            try {
                passkeyCredentialDao.getCredentialsByRpId(rpId).first().forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getCredentialsByUserId(userId: String): Flow<PasskeyCredential> {
        return flow {
            try {
                passkeyCredentialDao.getCredentialsByUserId(userId).first().forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getAllCredentials(): Flow<PasskeyCredential> {
        return flow {
            try {
                passkeyCredentialDao.getAllCredentials().first().forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder).onSuccess {
                        emit(it)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): Result<List<PasskeyCredential>> {
        return try {
            val entities = passkeyCredentialDao.getPagedCredentials(limit, offset)
            val validCredentials = mutableListOf<PasskeyCredential>()
            val corruptedIds = mutableListOf<String>()

            for (entity in entities) {
                entity.toDomainModel(publicKeyDecoder)
                    .onSuccess { validCredentials.add(it) }
                    .onFailure { corruptedIds.add(entity.id) }
            }

            if (corruptedIds.isNotEmpty()) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    corruptedKeyRepairWorker.doWork(corruptedIds)
                }
            }

            Result.success(validCredentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPagedCredentialsByRpId(
        rpId: String,
        limit: Long,
        offset: Long,
    ): Result<List<PasskeyCredential>> {
        return try {
            val entities = passkeyCredentialDao.getPagedCredentialsByRpId(rpId, limit, offset)
            val validCredentials = mutableListOf<PasskeyCredential>()
            val corruptedIds = mutableListOf<String>()

            for (entity in entities) {
                entity.toDomainModel(publicKeyDecoder)
                    .onSuccess { validCredentials.add(it) }
                    .onFailure { corruptedIds.add(entity.id) }
            }

            if (corruptedIds.isNotEmpty()) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    corruptedKeyRepairWorker.doWork(corruptedIds)
                }
            }

            Result.success(validCredentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCredential(credentialId: String): Result<Unit> {
        return try {
            cryptoService.deleteCredentialKey(CredentialId.fromString(credentialId))
            passkeyCredentialDao.deleteCredential(credentialId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialDeletionFailed(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun credentialExists(
        rpId: String,
        userId: String,
    ): Boolean {
        return try {
            val entities = passkeyCredentialDao.getCredentialsByRpId(rpId).first()
            entities.any { it.userId == userId }
        } catch (e: Exception) {
            false
        }
    }

    // ── Sign count & usage ────────────────────────────────────────────────────

    override suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Result<Unit> {
        return try {
            passkeyCredentialDao.updateSignCount(credentialId, newSignCount)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialUpdateFailed(e.message ?: "Failed to update sign count", e))
        }
    }

    override suspend fun updateLastUsedAt(credentialId: String): Result<Unit> {
        return try {
            passkeyCredentialDao.updateLastUsedAt(credentialId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialUpdateFailed(e.message ?: "Failed to update last used", e))
        }
    }

    override suspend fun getSignCount(credentialId: String): Result<Long> {
        return try {
            val count = passkeyCredentialDao.getSignCount(credentialId)
            Result.success(count)
        } catch (e: Exception) {
            Result.success(0L)
        }
    }

    // ── Batch retrieval ───────────────────────────────────────────────────────

    override suspend fun getCredentialsForRp(rpId: String): Result<List<PasskeyCredential>> {
        return try {
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
            Result.success(credentials)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getCredentialSummariesForRp(rpId: String): Result<List<CredentialSummary>> {
        return try {
            // Pure DB read — no HDK derivation at all. This is the fast path used
            // by GetAssertionUseCase to list candidates for selection without incurring
            // key-derivation cost for every stored credential.
            val entities = passkeyCredentialDao.getCredentialsByRpId(rpId).first()
            val summaries =
                entities.map { entity ->
                    CredentialSummary(
                        id = entity.id,
                        rpId = entity.rpId,
                        credentialId = java.util.Base64.getDecoder().decode(entity.credentialId),
                        lastUsedAt =
                            entity.lastUsedAt
                                ?.let { Instant.ofEpochMilli(it) }
                                ?: Instant.ofEpochMilli(entity.createdAt),
                        coseAlgorithm = entity.coseAlgorithm.toInt(),
                        credProtectPolicy = entity.credProtectPolicy.toInt(),
                    )
                }
            Result.success(summaries)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getCredentialsByIds(
        credentialIds: Set<String>,
        rpId: String?,
    ): Result<List<PasskeyCredential>> {
        return try {
            val credentials = credentialIds.mapNotNull { id -> getCredentialById(id) }
            val filtered = if (rpId != null) credentials.filter { it.rpId == rpId } else credentials
            Result.success(filtered)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getCredentialCountByRpId(rpId: String): Int {
        return try {
            passkeyCredentialDao.getCredentialsByRpId(rpId).first().size
        } catch (e: Exception) {
            0
        }
    }

    // ── Querying / searching ──────────────────────────────────────────────────

    override suspend fun searchCredentials(query: String): Flow<PasskeyCredential> {
        return flow {
            try {
                val lq = query.lowercase()
                passkeyCredentialDao.getAllCredentials().first()
                    .filter { it.userName.lowercase().contains(lq) || it.userDisplayName.lowercase().contains(lq) }
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getRecentlyUnusedCredentials(days: Long): Flow<PasskeyCredential> {
        return flow {
            try {
                val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
                passkeyCredentialDao.getAllCredentials().first()
                    .filter {
                            entity ->
                        entity.lastUsedAt == null || Instant.ofEpochMilli(entity.lastUsedAt).isBefore(cutoff)
                    }
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential> {
        return flow {
            try {
                passkeyCredentialDao.getAllCredentials().first()
                    .filter { false } // Not implemented in current schema
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getExpiredCredentials(maxAgeDays: Long): Flow<PasskeyCredential> {
        return flow {
            try {
                val cutoff = Instant.now().minus(maxAgeDays, ChronoUnit.DAYS)
                passkeyCredentialDao.getAllCredentials().first()
                    .filter { Instant.ofEpochMilli(it.createdAt).isBefore(cutoff) }
                    .forEach { entity ->
                        entity.toDomainModel(publicKeyDecoder).onSuccess { emit(it) }
                    }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun cleanupExpiredCredentials(maxAgeDays: Long): Result<Int> {
        return try {
            var count = 0
            getExpiredCredentials(maxAgeDays).collect { credential ->
                val r = deleteCredential(credential.id)
                if (r.isSuccess) count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialDeletionFailed(e.message ?: "Cleanup failed", e))
        }
    }

    // ── Validation / checks ───────────────────────────────────────────────────

    override suspend fun validateCredentialCreation(
        rpId: String,
        userId: String,
    ): Result<Unit> {
        return try {
            val existingCredentials =
                getCredentialsByRpId(rpId).let { flow ->
                    val list = mutableListOf<PasskeyCredential>()
                    flow.collect { list.add(it) }
                    list
                }
            val userCreds = existingCredentials.filter { it.userId.equals(userId, ignoreCase = true) }
            if (userCreds.size >= MAX_USER_CREDENTIALS_PER_RP) {
                return Result.failure(Fido2Exception.TooManyCredentials(MAX_USER_CREDENTIALS_PER_RP))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialCreationNotAllowed(e.message ?: "Unknown error", e))
        }
    }

    // ── Relying Party ─────────────────────────────────────────────────────────

    override suspend fun getRelyingParty(rpId: String): RelyingParty? {
        return try {
            relyingPartyDao.getRelyingPartyById(rpId)?.toDomainModel()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveRelyingParty(rp: RelyingParty): Result<Unit> {
        return try {
            relyingPartyDao.insertOrUpdateRelyingParty(rp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.RelyingPartyUpdateFailed(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun updateRelyingParty(
        rpId: String,
        update: (RelyingParty) -> RelyingParty,
    ): Result<Unit> {
        return try {
            val currentRp =
                getRelyingParty(rpId) ?: return Result.failure(
                    Fido2Exception.RelyingPartyUpdateFailed("RP not found: $rpId"),
                )
            val updatedRp = update(currentRp)
            relyingPartyDao.updateRelyingParty(updatedRp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.RelyingPartyUpdateFailed(e.message ?: "Unknown error", e))
        }
    }

    // ── User Consent ──────────────────────────────────────────────────────────

    override suspend fun saveUserConsent(consent: UserConsentRecord): Result<Unit> {
        return try {
            userConsentRecordDao.insertConsent(consent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ConsentStorageFailed(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun getRecentUserConsent(
        rpId: String?,
        limit: Int,
    ): Flow<UserConsentRecord> {
        return try {
            userConsentRecordDao.getRecentConsent(rpId, limit)
                .map { list -> list.map { it.toDomainModel() }.firstOrNull() ?: throw Exception("Empty") }
        } catch (e: Exception) {
            flowOf()
        }
    }

    override suspend fun isUserConsentRequired(
        rpId: String,
        operationType: String,
    ): Boolean {
        // By default, user consent is always required for FIDO2 operations
        return true
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    override suspend fun getCredentialStatistics(): CredentialStatistics {
        return try {
            val allCredentials = mutableListOf<PasskeyCredential>()
            getAllCredentials().collect { allCredentials.add(it) }

            val byRp = allCredentials.groupBy { it.rpId }.mapValues { it.value.size }
            val now = Instant.now()
            val cutoff = now.minus(RECENT_USAGE_CUTOFF_DAYS, ChronoUnit.DAYS)
            val expired = allCredentials.count { ChronoUnit.DAYS.between(it.createdAt, now) > EXPIRY_DAYS_THRESHOLD }
            val recentlyUsed = allCredentials.count { it.lastUsedAt?.isAfter(cutoff) == true }
            val needsUV = 0 // Not implemented in current schema
            val avgAge =
                if (allCredentials.isNotEmpty()) {
                    allCredentials.map { ChronoUnit.DAYS.between(it.createdAt, now) }.average()
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
        } catch (e: Exception) {
            CredentialStatistics(0, emptyMap(), 0, 0, 0, 0.0)
        }
    }

    // ── Bulk deletion / reset ─────────────────────────────────────────────────

    override suspend fun deleteAllCredentials(rpId: String?): Result<Unit> {
        return try {
            val target: Flow<PasskeyCredential> =
                if (rpId != null) {
                    getCredentialsByRpId(rpId)
                } else {
                    getAllCredentials()
                }

            target.collect { credential -> deleteCredential(credential.id) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetAuthenticator(): Result<Unit> {
        return try {
            getAllCredentials().collect { credential -> deleteCredential(credential.id) }
            // TODO: relyingPartyDao.deleteAll() / userConsentRecordDao.deleteAll() once DAOs support it
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLabel(
        credentialId: String,
        label: String?,
    ): Result<Unit> {
        return try {
            passkeyCredentialDao.updateLabel(credentialId, label)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialUpdateFailed(e.message ?: "Failed to update label", e))
        }
    }
}
