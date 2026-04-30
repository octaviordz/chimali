package com.chimali.fido2.domain.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import kotlinx.coroutines.flow.Flow

interface PasskeyCredentialRepository {
    suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError>

    suspend fun getCredentialById(credentialId: String): PasskeyCredential?

    suspend fun getCredentialsByRpId(rpId: String): Flow<List<PasskeyCredential>>

    suspend fun getAllCredentials(): Flow<List<PasskeyCredential>>

    suspend fun searchCredentials(query: String): Flow<List<PasskeyCredential>>

    suspend fun deleteCredential(credentialId: String): Outcome<Unit, DomainError>

    suspend fun updateSignCount(
        credentialId: String,
        signCount: Long,
    ): Outcome<Unit, DomainError>

    suspend fun updateLastUsedAt(credentialId: String): Outcome<Unit, DomainError>

    suspend fun validateCredentialCreation(
        rpId: String,
        userId: String,
    ): Outcome<Unit, DomainError>

    suspend fun getCredentialStatistics(): CredentialStatistics

    suspend fun getRelyingParty(rpId: String): RelyingParty?

    suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError>
}

/**
 * Data class representing credential statistics.
 */
data class CredentialStatistics(
    val totalCredentials: Int,
    val credentialsByRp: Map<String, Int>,
    val expiredCredentials: Int,
    val recentlyUsedCredentials: Int,
    val credentialsRequiringUserVerification: Int,
    val averageAgeDays: Double,
)
