package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * T111 — Resets the authenticator completely.
 * This wipes all credentials, master PIN, biometric state, and restores factory defaults.
 */
@Factory
class ResetAuthenticatorUseCase(
    private val credentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(): Outcome<Unit, DomainError> = credentialRepository.resetAuthenticator()
}
