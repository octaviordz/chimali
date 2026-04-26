package com.chimali.fido2.data.repository

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
    ): Result<String> {
        // TODO: Implement FIDO2 registration logic
        return Result.success("mock-credential-id")
    }

    override suspend fun authenticateCredential(rpId: String): Result<String> {
        // TODO: Implement FIDO2 authentication logic
        return Result.success("mock-authentication-id")
    }

    override suspend fun getAllCredentials(): Flow<List<PasskeyCredential>> {
        return credentialRepository.getAllCredentials()
    }

    override suspend fun deleteCredential(credentialId: String): Result<Unit> {
        return credentialRepository.deleteCredential(credentialId)
    }
}
