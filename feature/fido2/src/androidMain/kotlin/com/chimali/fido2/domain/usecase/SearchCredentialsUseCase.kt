package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import org.koin.core.annotation.Factory
import kotlinx.coroutines.flow.Flow

/**
 * Searches FIDO2 credentials by user name or display name.
 */
@Factory
class SearchCredentialsUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(query: String): Flow<PasskeyCredential> {
        return passkeyCredentialRepository.searchCredentials(query)
    }
}
