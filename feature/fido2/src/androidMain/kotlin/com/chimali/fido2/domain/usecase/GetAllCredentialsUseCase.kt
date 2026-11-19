package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory

/**
 * Retrieves paged FIDO2 credentials across relying parties. A successful repository page is
 * forwarded without copying; each mutable [PasskeyCredential] transfers to this use case's caller,
 * which must erase its sensitive text after its final borrower completes.
 */
@Factory
class GetAllCredentialsUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    /** Returns a caller-owned page, or an error with no credential owner transferred. */
    suspend operator fun invoke(
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> = passkeyCredentialRepository.getPagedCredentials(limit, offset)
}
