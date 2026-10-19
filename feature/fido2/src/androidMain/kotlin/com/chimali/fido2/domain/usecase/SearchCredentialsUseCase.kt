package com.chimali.fido2.domain.usecase

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

/** Searches by borrowing the caller's mutable query owner for the lifetime of the returned flow. */
@Factory
class SearchCredentialsUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    @Req("NFR-SEC-041", "TR-AUTH-031")
    operator fun invoke(query: SensitiveCredentialText): Flow<PasskeyCredential> =
        passkeyCredentialRepository.searchCredentials(query)
}
