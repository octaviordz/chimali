package com.chimali.core.domain.validation

import com.chimali.core.domain.exception.DomainException
import com.chimali.core.domain.model.Credential
import org.koin.core.annotation.Factory

interface Validator<T> {
    fun validate(item: T): Result<T>
}

@Factory
class CredentialValidator : Validator<Credential> {
    override fun validate(item: Credential): Result<Credential> {
        return when {
            item.title.isBlank() -> Result.failure(
                DomainException.ValidationError("title", "Title cannot be empty")
            )
            item.username.isBlank() -> Result.failure(
                DomainException.ValidationError("username", "Username cannot be empty")
            )
            else -> Result.success(item)
        }
    }
}
