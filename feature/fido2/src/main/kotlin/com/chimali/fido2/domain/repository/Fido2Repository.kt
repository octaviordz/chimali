package com.chimali.fido2.domain.repository

import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

interface Fido2Repository {
    suspend fun registerCredential(
        rpId: String,
        userName: String,
        userDisplayName: String,
    ): Result<String>

    suspend fun authenticateCredential(rpId: String): Result<String>

    suspend fun getAllCredentials(): Flow<List<PasskeyCredential>>

    suspend fun deleteCredential(credentialId: String): Result<Unit>
}
