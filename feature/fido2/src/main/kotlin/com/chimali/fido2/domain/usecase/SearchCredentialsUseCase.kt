package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Factory
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.PasskeyCredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * Searches FIDO2 credentials by user name or display name.
 */
@Factory
class SearchCredentialsUseCase(
    private val passkeyCredentialRepository: PasskeyCredentialRepository,
) {
    suspend operator fun invoke(query: String): Flow<List<PasskeyCredential>> {
        return passkeyCredentialRepository.searchCredentials(query)
    }
}
