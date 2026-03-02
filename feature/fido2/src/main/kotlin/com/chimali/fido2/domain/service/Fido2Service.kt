package com.chimali.fido2.domain.service

import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

interface Fido2Service {
    suspend fun registerNewCredential(rpId: String, userName: String, userDisplayName: String): Result<String>
    suspend fun authenticateWithCredential(rpId: String): Result<String>
    suspend fun getAllCredentials(): Flow<List<PasskeyCredential>>
    suspend fun deleteCredential(credentialId: String): Result<Unit>
    suspend fun isSupported(): Boolean
}
