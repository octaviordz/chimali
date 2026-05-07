package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

/**
 * Searches FIDO2 credentials by user name or display name.
 */
@Factory
class SearchCredentialsUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(query: String): Flow<PasskeyCredential> =
        passkeyCredentialRepository.searchCredentials(query)
}
