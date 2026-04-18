package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Single

import com.chimali.fido2.domain.repository.CredentialRepository

/**
 * T110 — Deletes all FIDO2 credentials (or all credentials for a specific RP).
 */
class DeleteAllCredentialsUseCase
   (
        private val credentialRepository: CredentialRepository,
    ) {
        /**
         * @param rpId Optional RP ID. If provided, only credentials for that RP are deleted.
         */
        suspend operator fun invoke(rpId: String? = null): Result<Unit> {
            return credentialRepository.deleteAllCredentials(rpId)
        }
    }
