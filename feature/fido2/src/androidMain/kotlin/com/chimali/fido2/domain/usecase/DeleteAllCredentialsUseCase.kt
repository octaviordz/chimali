package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * T110 — Deletes all FIDO2 credentials, or only those for a specific relying party.
 */
@Factory
class DeleteAllCredentialsUseCase(
    private val credentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(rpId: String? = null): Result<Unit> {
        return credentialRepository.deleteAllCredentials(rpId)
    }
}
