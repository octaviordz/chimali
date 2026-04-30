package com.chimali.fido2.data.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.Fido2Repository
import com.chimali.fido2.domain.repository.PasskeyCredentialRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
@Suppress("ForbiddenComment")
class Fido2RepositoryImpl(
    private val credentialRepository: PasskeyCredentialRepository,
) : Fido2Repository {
    override suspend fun registerCredential(
        rpId: String,
        userName: String,
        userDisplayName: String,
    ): Outcome<String, DomainError> {
        // TODO: Implement FIDO2 registration logic
        return Outcome.Success("mock-credential-id")
    }

    override suspend fun authenticateCredential(rpId: String): Outcome<String, DomainError> {
        // TODO: Implement FIDO2 authentication logic
        return Outcome.Success("mock-authentication-id")
    }

    override suspend fun getAllCredentials(): Flow<List<PasskeyCredential>> {
        return credentialRepository.getAllCredentials()
    }

    override suspend fun deleteCredential(credentialId: String): Outcome<Unit, DomainError> {
        return credentialRepository.deleteCredential(credentialId)
    }
}
