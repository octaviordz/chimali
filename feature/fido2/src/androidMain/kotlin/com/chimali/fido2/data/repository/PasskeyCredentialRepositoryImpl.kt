package com.chimali.fido2.data.repository

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrNull
import com.chimali.core.common.result.map
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.mapper.toDomainModel
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
    override suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError> {
        return try {
            passkeyCredentialDao.insertCredential(credential)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "PasskeyCredentialRepository: Failed to save credential id=${credential.id}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to save credential", e))
        }
    }

    override suspend fun getCredentialById(credentialId: String): PasskeyCredential? {
        val entity = passkeyCredentialDao.getCredentialById(credentialId) ?: return null
        return entity.toDomainModel(PublicKeyDecoder()).getOrNull()
    }

    override suspend fun getCredentialsByRpId(rpId: String): Flow<List<PasskeyCredential>> {
        val decoder = PublicKeyDecoder()
        return passkeyCredentialDao.getCredentialsByRpId(rpId).map { entities ->
            entities.mapNotNull { entity ->
                entity.toDomainModel(decoder).getOrNull()
            }
        }
    }

    override suspend fun getAllCredentials(): Flow<List<PasskeyCredential>> {
        val decoder = PublicKeyDecoder()
        return passkeyCredentialDao.getAllCredentials().map { entities ->
            entities.mapNotNull { entity ->
                entity.toDomainModel(decoder).getOrNull()
            }
        }
    }

    override suspend fun searchCredentials(query: String): Flow<List<PasskeyCredential>> {
        val decoder = PublicKeyDecoder()
        return passkeyCredentialDao.searchCredentials(query, null).map { entities ->
            entities.mapNotNull { entity ->
                entity.toDomainModel(decoder).getOrNull()
            }
        }
    }

    override suspend fun deleteCredential(credentialId: String): Outcome<Unit, DomainError> {
        return try {
            // Delete from KeyStore first
            cryptoService.deleteCredentialKey(CredentialId.fromString(credentialId))
            // Delete from DB
            passkeyCredentialDao.deleteCredential(credentialId)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "PasskeyCredentialRepository: Failed to delete credential id=$credentialId" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to delete credential", e))
        }
    }

    override suspend fun updateSignCount(
        credentialId: String,
        signCount: Long,
    ): Outcome<Unit, DomainError> {
        return try {
            passkeyCredentialDao.updateSignCount(credentialId, signCount)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "PasskeyCredentialRepository: Failed to update sign count for id=$credentialId" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to update sign count", e))
        }
    }

    override suspend fun updateLastUsedAt(credentialId: String): Outcome<Unit, DomainError> {
        return try {
            passkeyCredentialDao.updateLastUsedAt(credentialId)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "PasskeyCredentialRepository: Failed to update last used at for id=$credentialId" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to update last used at", e))
        }
    }

    override suspend fun validateCredentialCreation(
        rpId: String,
        userId: String,
    ): Outcome<Unit, DomainError> {
        // Basic validation for now — check if user already has a credential for this RP
        return if (passkeyCredentialDao.getCredentialsByRpIdAndUserId(rpId, userId).isEmpty()) {
            Outcome.Success(Unit)
        } else {
            Outcome.Error(
                DomainError.ValidationError(
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

    override suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError> {
        return try {
            relyingPartyDao.insertOrUpdateRelyingParty(rp)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "PasskeyCredentialRepository: Failed to save relying party rpId=${rp.id}" }
            Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to save relying party", e))
        }
    }
}
