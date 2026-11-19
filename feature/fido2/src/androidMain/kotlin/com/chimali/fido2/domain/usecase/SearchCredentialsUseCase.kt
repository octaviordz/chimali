package com.chimali.fido2.domain.usecase

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

/**
 * Returns a cold flow that borrows [query] for its full collection lifetime. The caller must keep
 * the query valid until collection completes, then erase it. Each accepted credential emission
 * transfers to the collector, which erases it after its final borrower completes; rejected
 * emissions remain the producer's cleanup responsibility.
 */
@Factory
class SearchCredentialsUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    @Req("NFR-SEC-041", "TR-AUTH-031")
    operator fun invoke(query: SensitiveCredentialText): Flow<PasskeyCredential> =
        passkeyCredentialRepository.searchCredentials(query)
}
