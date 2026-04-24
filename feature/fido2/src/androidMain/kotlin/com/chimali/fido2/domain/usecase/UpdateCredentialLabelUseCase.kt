package com.chimali.fido2.domain.usecase

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
    ): Result<Unit> {
        return credentialRepository.updateLabel(credentialId, label)
    }
}
