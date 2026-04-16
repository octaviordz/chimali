package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.repository.CredentialRepository
import javax.inject.Inject

/**
 * Update the user-defined label or note for a specific FIDO2 credential.
 */
class UpdateCredentialLabelUseCase
    @Inject
    constructor(
        private val credentialRepository: CredentialRepository,
    ) {
        suspend operator fun invoke(
            credentialId: String,
            label: String?,
        ): Result<Unit> {
            return credentialRepository.updateLabel(credentialId, label)
        }
    }
