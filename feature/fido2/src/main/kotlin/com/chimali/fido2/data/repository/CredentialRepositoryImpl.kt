package com.chimali.fido2.data.repository

import com.chimali.fido2.domain.model.*
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.service.CredentialStorageService
import com.chimali.fido2.domain.exception.Fido2Exception
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CredentialRepository using SQLDelight for data persistence
 * and Android KeyStore for secure credential storage.
 */
@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val passkeyCredentialDao: PasskeyCredentialDao,
    private val relyingPartyDao: RelyingPartyDao,
    private val userConsentRecordDao: UserConsentRecordDao,
    private val credentialStorageService: CredentialStorageService
) : CredentialRepository {
    
    override suspend fun saveCredential(credential: PasskeyCredential): Result<Unit> {
        return try {
            // Store private key securely in KeyStore
            val keyStorageResult = credentialStorageService.storePrivateKey(
                credential.privateKeyAlias,
                credential.publicKey
            )
            if (keyStorageResult.isFailure) {
                return Result.failure(keyStorageResult.exceptionOrNull() ?: Fido2Exception.CredentialStorageFailed())
            }
            
            // Save credential metadata to database
            passkeyCredentialDao.insertCredential(credential)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialStorageFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun getCredentialById(credentialId: String): PasskeyCredential? {
        return try {
            val credentialEntity = passkeyCredentialDao.getCredentialById(credentialId)
            credentialEntity?.let { entity ->
                // Retrieve public key from KeyStore
                val publicKey = credentialStorageService.getPublicKey(entity.privateKeyAlias)
                if (publicKey != null) {
                    entity.toDomainModel(publicKey)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getCredentialsByRpId(rpId: String): Flow<List<PasskeyCredential>> {
        return try {
            passkeyCredentialDao.getCredentialsByRpId(rpId)
                .map { entities ->
                    entities.mapNotNull { entity ->
                        val publicKey = credentialStorageService.getPublicKey(entity.privateKeyAlias)
                        publicKey?.let { entity.toDomainModel(it) }
                    }
                }
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
    
    override suspend fun getCredentialsByUserId(userId: String): Flow<List<PasskeyCredential>> {
        return try {
            passkeyCredentialDao.getCredentialsByUserId(userId)
                .map { entities ->
                    entities.mapNotNull { entity ->
                        val publicKey = credentialStorageService.getPublicKey(entity.privateKeyAlias)
                        publicKey?.let { entity.toDomainModel(it) }
                    }
                }
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
    
    override suspend fun getAllCredentials(): Flow<List<PasskeyCredential>> {
        return try {
            passkeyCredentialDao.getAllCredentials()
                .map { entities ->
                    entities.mapNotNull { entity ->
                        val publicKey = credentialStorageService.getPublicKey(entity.privateKeyAlias)
                        publicKey?.let { entity.toDomainModel(it) }
                    }
                }
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
    
    override suspend fun updateCredential(credentialId: String, updateFn: (PasskeyCredential) -> PasskeyCredential): Result<Unit> {
        return try {
            val currentCredential = getCredentialById(credentialId)
                ?: return Result.failure(Fido2Exception.CredentialNotFound())
            
            val updatedCredential = updateFn(currentCredential)
            
            // Update credential metadata
            passkeyCredentialDao.updateCredential(updatedCredential)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialUpdateFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun deleteCredential(credentialId: String): Result<Unit> {
        return try {
            val credential = getCredentialById(credentialId)
                ?: return Result.failure(Fido2Exception.CredentialNotFound())
            
            // Remove private key from KeyStore
            credentialStorageService.deletePrivateKey(credential.privateKeyAlias)
            
            // Delete credential from database
            passkeyCredentialDao.deleteCredential(credentialId)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialDeletionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun deleteCredentialsByRpId(rpId: String): Result<Int> {
        return try {
            val credentials = getCredentialsByRpId(rpId).first()
            var deletedCount = 0
            
            credentials.forEach { credential ->
                val deleteResult = deleteCredential(credential.id)
                if (deleteResult.isSuccess) {
                    deletedCount++
                }
            }
            
            Result.success(deletedCount)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialDeletionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun validateCredentialCreation(rpId: String, userId: String): Result<Unit> {
        return try {
            // Check if user already has credentials for this RP
            val existingCredentials = getCredentialsByRpId(rpId).first()
            val userCredentials = existingCredentials.filter { it.userId.equals(userId, ignoreCase = true) }
            
            // Allow multiple credentials per user per RP (common in FIDO2)
            // But enforce reasonable limits
            if (userCredentials.size >= 10) {
                return Result.failure(Fido2Exception.TooManyCredentials())
            }
            
            // Validate RP exists or can be created
            val rp = getRelyingParty(rpId)
            if (rp == null) {
                // RP doesn't exist, we'll create it when credential is saved
                Result.success(Unit)
            } else {
                // Check if RP is not blocked
                if (rp.isBlocked) {
                    return Result.failure(Fido2Exception.RelyingPartyBlocked())
                }
                Result.success(Unit)
            }
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialCreationNotAllowed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun getRelyingParty(rpId: String): RelyingParty? {
        return try {
            relyingPartyDao.getRelyingPartyById(rpId)?.toDomainModel()
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun updateRelyingParty(rpId: String, updateFn: (RelyingParty?) -> RelyingParty): Result<Unit> {
        return try {
            val currentRp = getRelyingParty(rpId)
            val updatedRp = updateFn(currentRp)
            
            // Update or insert RP
            if (currentRp == null) {
                relyingPartyDao.insertRelyingParty(updatedRp)
            } else {
                relyingPartyDao.updateRelyingParty(updatedRp)
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.RelyingPartyUpdateFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun getAllRelyingParties(): Flow<List<RelyingParty>> {
        return try {
            relyingPartyDao.getAllRelyingParties()
                .map { entities ->
                    entities.map { it.toDomainModel() }
                }
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
    
    override suspend fun getRelyingPartyStatistics(rpId: String): RelyingPartyStatistics? {
        return try {
            val rp = getRelyingParty(rpId) ?: return null
            val credentials = getCredentialsByRpId(rpId).first()
            
            RelyingPartyStatistics(
                rpId = rp.id,
                name = rp.name,
                credentialCount = credentials.size,
                userCount = credentials.map { it.userId.lowercase() }.distinct().size,
                lastUsedAt = credentials.maxOfOrNull { it.lastUsedAt },
                createdAt = rp.createdAt,
                isBlocked = rp.isBlocked
            )
            
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun saveUserConsent(consent: UserConsentRecord): Result<Unit> {
        return try {
            userConsentRecordDao.insertConsent(consent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ConsentStorageFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun getRecentUserConsent(rpId: String?, limit: Int): Flow<UserConsentRecord> {
        return try {
            userConsentRecordDao.getRecentConsent(rpId, limit)
                .map { it.toDomainModel() }
        } catch (e: Exception) {
            flowOf()
        }
    }
    
    override suspend fun getUserConsentByCredential(credentialId: String, limit: Int): Flow<UserConsentRecord> {
        return try {
            userConsentRecordDao.getConsentByCredential(credentialId, limit)
                .map { it.toDomainModel() }
        } catch (e: Exception) {
            flowOf()
        }
    }
    
    override suspend fun getUserConsentByOperation(
        operationType: ConsentOperationType,
        rpId: String?,
        limit: Int
    ): Flow<UserConsentRecord> {
        return try {
            userConsentRecordDao.getConsentByOperation(operationType, rpId, limit)
                .map { it.toDomainModel() }
        } catch (e: Exception) {
            flowOf()
        }
    }
    
    override suspend fun deleteUserConsent(consentId: String): Result<Unit> {
        return try {
            userConsentRecordDao.deleteConsent(consentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ConsentDeletionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun deleteOldConsent(before: Instant): Result<Int> {
        return try {
            val deletedCount = userConsentRecordDao.deleteConsentBefore(before)
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ConsentDeletionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun searchCredentials(query: String, rpId: String?): Flow<List<PasskeyCredential>> {
        return try {
            passkeyCredentialDao.searchCredentials(query, rpId)
                .map { entities ->
                    entities.mapNotNull { entity ->
                        val publicKey = credentialStorageService.getPublicKey(entity.privateKeyAlias)
                        publicKey?.let { entity.toDomainModel(it) }
                    }
                }
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
    
    override suspend fun getExpiredCredentials(maxAgeDays: Int): Flow<List<PasskeyCredential>> {
        return try {
            val cutoffDate = Instant.now().minusSeconds(maxAgeDays.toLong() * 24 * 60 * 60)
            passkeyCredentialDao.getExpiredCredentials(cutoffDate)
                .map { entities ->
                    entities.mapNotNull { entity ->
                        val publicKey = credentialStorageService.getPublicKey(entity.privateKeyAlias)
                        publicKey?.let { entity.toDomainModel(it) }
                    }
                }
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
    
    override suspend fun cleanupExpiredCredentials(maxAgeDays: Int): Result<Int> {
        return try {
            val expiredCredentials = getExpiredCredentials(maxAgeDays).first()
            var deletedCount = 0
            
            expiredCredentials.forEach { credential ->
                val deleteResult = deleteCredential(credential.id)
                if (deleteResult.isSuccess) {
                    deletedCount++
                }
            }
            
            Result.success(deletedCount)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialCleanupFailed(e.message ?: "Unknown error", e))
        }
    }
    
    override suspend fun getRepositoryStatistics(): RepositoryStatistics {
        return try {
            val allCredentials = getAllCredentials().first()
            val allRps = getAllRelyingParties().first()
            val allConsent = getRecentUserConsent(null, Int.MAX_VALUE).first()
            
            val credentialsByRp = allCredentials.groupBy { it.rpId }
            val credentialsByUser = allCredentials.groupBy { it.userId }
            
            RepositoryStatistics(
                totalCredentials = allCredentials.size,
                totalRelyingParties = allRps.size,
                totalConsentRecords = allConsent.size,
                credentialsByRp = credentialsByRp.mapValues { it.value.size },
                credentialsByUser = credentialsByUser.mapValues { it.value.size },
                averageCredentialsPerRp = if (allRps.isNotEmpty()) allCredentials.size.toDouble() / allRps.size else 0.0,
                mostUsedRp = credentialsByRp.maxByOrNull { it.value.size }?.key,
                oldestCredential = allCredentials.minOfOrNull { it.createdAt },
                newestCredential = allCredentials.maxOfOrNull { it.createdAt },
                oldestRp = allRps.minOfOrNull { it.createdAt },
                newestRp = allRps.maxOfOrNull { it.createdAt }
            )
            
        } catch (e: Exception) {
            RepositoryStatistics(
                totalCredentials = 0,
                totalRelyingParties = 0,
                totalConsentRecords = 0,
                credentialsByRp = emptyMap(),
                credentialsByUser = emptyMap(),
                averageCredentialsPerRp = 0.0,
                mostUsedRp = null,
                oldestCredential = null,
                newestCredential = null,
                oldestRp = null,
                newestRp = null
            )
        }
    }
}
