package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Factory
import org.koin.core.annotation.Single

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * T108 — Retrieves all FIDO2 credentials stored on the device across all relying parties.
 */
@Factory
class GetAllCredentialsUseCase
   (
        private val credentialRepository: CredentialRepository,
    ) {
        suspend operator fun invoke(): Flow<PasskeyCredential> {
            return credentialRepository.getAllCredentials()
        }
    }
