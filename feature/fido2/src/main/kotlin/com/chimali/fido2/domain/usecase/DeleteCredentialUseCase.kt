package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Single

import com.chimali.fido2.domain.repository.CredentialRepository

/**
 * T109 — Deletes a specific FIDO2 credential by its identifier.
 */
class DeleteCredentialUseCase
   (
        private val credentialRepository: CredentialRepository,
    ) {
        suspend operator fun invoke(credentialId: String): Result<Unit> {
            return credentialRepository.deleteCredential(credentialId)
        }
    }
