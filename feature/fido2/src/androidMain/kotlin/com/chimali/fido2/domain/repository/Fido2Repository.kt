package com.chimali.fido2.domain.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

interface Fido2Repository {
    suspend fun registerCredential(
        rpId: RpId,
        userName: String,
        userDisplayName: String,
    ): Outcome<CredentialId, DomainError>

    suspend fun authenticateCredential(rpId: RpId): Outcome<CredentialId, DomainError>

    fun getAllCredentials(): Flow<PasskeyCredential>

    suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError>
}
