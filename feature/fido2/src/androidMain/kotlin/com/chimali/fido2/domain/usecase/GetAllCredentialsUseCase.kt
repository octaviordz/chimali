package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * T108 — Retrieves all FIDO2 credentials stored on the device across all relying parties.
 */
@Factory
class GetAllCredentialsUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(
        limit: Long,
        offset: Long,
    ): Result<List<PasskeyCredential>> {
        return passkeyCredentialRepository.getPagedCredentials(limit, offset)
    }
}
