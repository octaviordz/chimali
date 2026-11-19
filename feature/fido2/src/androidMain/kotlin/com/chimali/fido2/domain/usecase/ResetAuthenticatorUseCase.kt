package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * Requests removal of current credential records and relying-party/consent projection metadata.
 * Historical event-store records and unrelated PIN/biometric settings are outside this operation.
 */
@Factory
class ResetAuthenticatorUseCase(
    private val credentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(): Outcome<Unit, DomainError> = credentialRepository.resetAuthenticator()
}
