package com.chimali.fido2.data.repository

import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.repository.CredentialStatistics
import com.chimali.fido2.domain.repository.PasskeyCredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class PasskeyCredentialRepositoryImpl(
    private val passkeyCredentialDao: PasskeyCredentialDao,
    private val relyingPartyDao: RelyingPartyDao,
    private val cryptoService: Fido2CryptoService,
) : PasskeyCredentialRepository {
    override suspend fun saveCredential(credential: PasskeyCredential): Result<Unit> {
        return try {
            passkeyCredentialDao.insertCredential(credential)
            Result.success(Unit)
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }

    override suspend fun getCredentialById(credentialId: String): PasskeyCredential? {
        val entity = passkeyCredentialDao.getCredentialById(credentialId) ?: return null
        return entity.toDomainModel(com.chimali.fido2.data.crypto.PublicKeyDecoder()).getOrNull()
    }

    override suspend fun getCredentialsByRpId(rpId: String): Flow<List<PasskeyCredential>> {
        val decoder = com.chimali.fido2.data.crypto.PublicKeyDecoder()
        return passkeyCredentialDao.getCredentialsByRpId(rpId).map { entities ->
            entities.mapNotNull { entity ->
                entity.toDomainModel(decoder).getOrNull()
            }
        }
    }

    override suspend fun getAllCredentials(): Flow<List<PasskeyCredential>> {
        val decoder = com.chimali.fido2.data.crypto.PublicKeyDecoder()
        return passkeyCredentialDao.getAllCredentials().map { entities ->
            entities.mapNotNull { entity ->
                entity.toDomainModel(decoder).getOrNull()
            }
        }
    }

    override suspend fun searchCredentials(query: String): Flow<List<PasskeyCredential>> {
        val decoder = com.chimali.fido2.data.crypto.PublicKeyDecoder()
        return passkeyCredentialDao.searchCredentials(query, null).map { entities ->
            entities.mapNotNull { entity ->
                entity.toDomainModel(decoder).getOrNull()
            }
        }
    }

    override suspend fun deleteCredential(credentialId: String): Result<Unit> {
        return try {
            // Delete from KeyStore first
            cryptoService.deleteCredentialKey(CredentialId.fromString(credentialId))
            // Delete from DB
            passkeyCredentialDao.deleteCredential(credentialId)
            Result.success(Unit)
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }

    override suspend fun updateSignCount(
        credentialId: String,
        signCount: Long,
    ): Result<Unit> {
        return try {
            passkeyCredentialDao.updateSignCount(credentialId, signCount)
            Result.success(Unit)
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }

    override suspend fun updateLastUsedAt(credentialId: String): Result<Unit> {
        return try {
            passkeyCredentialDao.updateLastUsedAt(credentialId)
            Result.success(Unit)
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }

    override suspend fun validateCredentialCreation(
        rpId: String,
        userId: String,
    ): Result<Unit> {
        // Basic validation for now — check if user already has a credential for this RP
        return if (passkeyCredentialDao.getCredentialsByRpIdAndUserId(rpId, userId).isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(
                Fido2Exception.CredentialCreationNotAllowed(
                    "Credential already exists for this user and RP",
                ),
            )
        }
    }

    override suspend fun getCredentialStatistics(): CredentialStatistics {
        val all = passkeyCredentialDao.getAllCredentialsSync()
        return CredentialStatistics(
            totalCredentials = all.size,
            credentialsByRp = all.groupBy { it.rpId }.mapValues { it.value.size },
            // Placeholder
            expiredCredentials = 0,
            recentlyUsedCredentials = all.count { it.lastUsedAt?.let { lastUsed -> lastUsed > 0 } ?: false },
            credentialsRequiringUserVerification = all.count { it.credProtectPolicy > 1 },
            // Placeholder
            averageAgeDays = 0.0,
        )
    }

    override suspend fun getRelyingParty(rpId: String): RelyingParty? {
        return relyingPartyDao.getRelyingPartyById(rpId)?.toDomainModel()
    }

    override suspend fun saveRelyingParty(rp: RelyingParty): Result<Unit> {
        return try {
            relyingPartyDao.insertOrUpdateRelyingParty(rp)
            Result.success(Unit)
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }
}
