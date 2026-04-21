package com.chimali.core.domain.exception

sealed class DomainException : Exception() {
    object CredentialNotFound : DomainException()
    object PasskeyNotFound : DomainException()
    object EncryptionFailed : DomainException()
    object DecryptionFailed : DomainException()
    object InvalidCredentials : DomainException()
    data class ValidationError(val field: String, override val message: String) : DomainException()
    data class RepositoryError(override val message: String, override val cause: Throwable? = null) : DomainException()
}
