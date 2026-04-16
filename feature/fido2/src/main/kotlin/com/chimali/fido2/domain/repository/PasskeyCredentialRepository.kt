package com.chimali.fido2.domain.repository

import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

interface PasskeyCredentialRepository {
    suspend fun saveCredential(credential: PasskeyCredential): Result<Unit>

    suspend fun getCredentialById(credentialId: String): PasskeyCredential?

    suspend fun getCredentialsByRpId(rpId: String): Flow<List<PasskeyCredential>>

    suspend fun getAllCredentials(): Flow<List<PasskeyCredential>>

    suspend fun deleteCredential(credentialId: String): Result<Unit>

    suspend fun updateSignCount(
        credentialId: String,
        signCount: Long,
    ): Result<Unit>
}
