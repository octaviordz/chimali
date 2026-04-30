package com.chimali.fido2.domain.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

interface Fido2Repository {
    suspend fun registerCredential(
        rpId: String,
        userName: String,
        userDisplayName: String,
    ): Outcome<String, DomainError>

    suspend fun authenticateCredential(rpId: String): Outcome<String, DomainError>

    suspend fun getAllCredentials(): Flow<List<PasskeyCredential>>

    suspend fun deleteCredential(credentialId: String): Outcome<Unit, DomainError>
}
