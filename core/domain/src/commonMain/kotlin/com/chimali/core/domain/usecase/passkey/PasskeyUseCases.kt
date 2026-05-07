package com.chimali.core.domain.usecase.passkey

import com.chimali.core.domain.model.Passkey
import com.chimali.core.domain.repository.PasskeyRepository
import com.chimali.core.domain.service.CreatePasskeyRequest
import com.chimali.core.domain.service.Fido2Service
import com.chimali.core.domain.usecase.BaseUseCase
import com.chimali.core.domain.usecase.BaseUseCaseNoParams
import com.chimali.core.domain.valueobject.PasskeyId
import org.koin.core.annotation.Factory

@Factory
class GetPasskeysUseCase(
    private val repository: PasskeyRepository,
) : BaseUseCaseNoParams<List<Passkey>>() {
    override suspend fun invoke(): Result<List<Passkey>> = repository.getAllPasskeys()
}

@Factory
class CreatePasskeyUseCase(
    private val repository: PasskeyRepository,
    private val fido2Service: Fido2Service,
) : BaseUseCase<CreatePasskeyRequest, PasskeyId>() {
    override suspend fun invoke(parameters: CreatePasskeyRequest): Result<PasskeyId> =
        fido2Service
            .makeCredential(parameters)
            .mapCatching { passkey -> repository.createPasskey(passkey).getOrThrow() }
}
