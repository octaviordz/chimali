package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * T108 — Retrieves all FIDO2 credentials stored on the device across all relying parties.
 */
class GetAllCredentialsUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository
) {
    suspend operator fun invoke(): Flow<PasskeyCredential> {
        return credentialRepository.getAllCredentials()
    }
}
