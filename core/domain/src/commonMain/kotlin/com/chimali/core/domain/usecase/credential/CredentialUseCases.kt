package com.chimali.core.domain.usecase.credential

import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.domain.model.Credential
import com.chimali.core.domain.repository.CredentialRepository
import com.chimali.core.domain.service.CryptoService
import com.chimali.core.domain.usecase.BaseUseCase
import com.chimali.core.domain.usecase.BaseUseCaseNoParams
import com.chimali.core.domain.validation.CredentialValidator
import com.chimali.core.domain.valueobject.CredentialId
import org.koin.core.annotation.Factory

@Factory
class GetCredentialsUseCase(
    private val repository: CredentialRepository,
) : BaseUseCaseNoParams<List<Credential>>() {
    override suspend fun invoke(): Result<List<Credential>> {
        return repository.getAllCredentials()
    }
}

@Factory
class SearchCredentialsUseCase(
    private val repository: CredentialRepository,
) : BaseUseCase<String, List<Credential>>() {
    override suspend fun invoke(parameters: String): Result<List<Credential>> {
        return if (parameters.isBlank()) {
            repository.getAllCredentials()
        } else {
            repository.searchCredentials(parameters)
        }
    }
}

@Factory
class SaveCredentialUseCase(
    private val repository: CredentialRepository,
    private val validator: CredentialValidator,
) : BaseUseCase<Credential, CredentialId>() {
    override suspend fun invoke(parameters: Credential): Result<CredentialId> {
        return validator.validate(parameters)
            .mapCatching { repository.saveCredential(parameters).getOrThrow() }
    }
}

@Factory
class CopyCredentialToClipboardUseCase(
    private val repository: CredentialRepository,
    private val clipboardService: ClipboardManagerService,
    private val cryptoService: CryptoService,
) : BaseUseCase<CredentialId, Unit>() {
    override suspend fun invoke(parameters: CredentialId): Result<Unit> {
        return repository.getCredentialById(parameters)
            .mapCatching { credential ->
                val decryptedPassword = cryptoService.decrypt(credential.password)
                clipboardService.copySensitiveData(
                    label = credential.title,
                    text = decryptedPassword,
                ).getOrThrow()
                repository.updateLastUsed(parameters).getOrThrow()
            }
    }
}
