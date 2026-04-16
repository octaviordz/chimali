package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.repository.CredentialRepository
import javax.inject.Inject

/**
 * T109 — Deletes a specific FIDO2 credential by its identifier.
 */
class DeleteCredentialUseCase
    @Inject
    constructor(
        private val credentialRepository: CredentialRepository,
    ) {
        suspend operator fun invoke(credentialId: String): Result<Unit> {
            return credentialRepository.deleteCredential(credentialId)
        }
    }
