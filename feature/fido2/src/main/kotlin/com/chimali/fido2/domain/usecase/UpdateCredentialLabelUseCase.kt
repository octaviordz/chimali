package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Single

import com.chimali.fido2.domain.repository.CredentialRepository

/**
 * Update the user-defined label or note for a specific FIDO2 credential.
 */
class UpdateCredentialLabelUseCase
   (
        private val credentialRepository: CredentialRepository,
    ) {
        suspend operator fun invoke(
            credentialId: String,
            label: String?,
        ): Result<Unit> {
            return credentialRepository.updateLabel(credentialId, label)
        }
    }
