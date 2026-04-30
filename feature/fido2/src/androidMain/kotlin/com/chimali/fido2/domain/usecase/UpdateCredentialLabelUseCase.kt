package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * Updates the custom label for a specific FIDO2 credential.
 */
@Factory
class UpdateCredentialLabelUseCase(
    private val credentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(
        credentialId: String,
        label: String?,
    ): Outcome<Unit, DomainError> {
        return credentialRepository.updateLabel(credentialId, label)
    }
}
