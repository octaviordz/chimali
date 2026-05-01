package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * T109 — Deletes a specific FIDO2 credential by its identifier.
 */
@Factory
class DeleteCredentialUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(credentialId: CredentialId): Outcome<Unit, DomainError> {
        return passkeyCredentialRepository.deleteCredential(credentialId)
    }
}
