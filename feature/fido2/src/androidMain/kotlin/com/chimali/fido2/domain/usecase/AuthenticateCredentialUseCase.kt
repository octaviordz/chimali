package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.Fido2Repository
import org.koin.core.annotation.Factory

/**
 * T110 — Authenticates a credential with the relying party ID (rpId).
 */
@Factory
class AuthenticateCredentialUseCase(
    private val fido2Repository: Fido2Repository,
) {
    suspend operator fun invoke(rpId: RpId): Outcome<CredentialId, DomainError> =
        fido2Repository.authenticateCredential(rpId)
}
