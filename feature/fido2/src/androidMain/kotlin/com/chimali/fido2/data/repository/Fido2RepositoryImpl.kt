package com.chimali.fido2.data.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.Fido2Repository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class Fido2RepositoryImpl(
    private val credentialRepository: CredentialRepository,
) : Fido2Repository {
    override suspend fun registerCredential(
        rpId: RpId,
        userName: String,
        userDisplayName: String,
    ): Outcome<CredentialId, DomainError> {
        // DEFERRED(040): FIDO2 registration — pending CTAP2 ceremony implementation
        return Outcome.Success(CredentialId.fromEncoded("mock-credential-id"))
    }

    override suspend fun authenticateCredential(rpId: RpId): Outcome<CredentialId, DomainError> {
        // DEFERRED(040): FIDO2 authentication — pending CTAP2 ceremony implementation
        return Outcome.Success(CredentialId.fromEncoded("mock-authentication-id"))
    }

    override fun getAllCredentials(): Flow<PasskeyCredential> = credentialRepository.getAllCredentials()

    override suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError> =
        credentialRepository.deleteCredential(credentialId)
}
