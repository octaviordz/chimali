# Domain Layer KMP Migration Specification

**Document ID**: 008-DOMAIN-KMP-MIGRATION  
**Version**: 1.0  
**Date**: 2026-04-20  
**Status**: Draft  

## Executive Summary

This document outlines the comprehensive migration and enhancement plan for the `core:domain` module to fully leverage Kotlin Multiplatform (KMP) capabilities. The module already has KMP infrastructure but requires substantial domain logic implementation to serve as the business logic foundation for the Chimali application.

## Current State Analysis

### Existing Infrastructure
- **KMP Configuration**: Complete with Android + iOS targets
- **Build System**: Properly configured with Koin Annotations
- **Source Sets**: Correctly structured (commonMain, androidMain, iosMain)
- **Dependencies**: Core KMP libraries integrated

### Current Implementation
- `DomainModel` interface (base marker interface)
- `UseCase` interface (base marker interface)
- **Missing**: Concrete domain models, business logic, repositories

## Migration Strategy

### Phase 1: Foundation Enhancement (High Priority)

#### 1.1 Generic UseCase Pattern
```kotlin
// commonMain/kotlin/com/chimali/core/domain/usecase/BaseUseCase.kt
abstract class BaseUseCase<in P, out R> : UseCase {
    abstract suspend operator fun invoke(parameters: P): Result<R>
}

abstract class BaseUseCaseNoParams<out R> : UseCase {
    abstract suspend operator fun invoke(): Result<R>
}

abstract class BaseUseCaseIn<in P> : UseCase {
    abstract suspend operator fun invoke(parameters: P): Result<Unit>
}
```

#### 1.2 Core Domain Models
```kotlin
// commonMain/kotlin/com/chimali/core/domain/model/Credential.kt
@Serializable
data class Credential(
    val id: CredentialId,
    val title: String,
    val username: String,
    val password: EncryptedString,
    val url: String?,
    val notes: String?,
    val category: CredentialCategory,
    val tags: List<String>,
    val createdAt: Instant,
    val lastModified: Instant,
    val lastUsed: Instant?,
    val isFavorite: Boolean
) : DomainModel

// commonMain/kotlin/com/chimali/core/domain/model/Passkey.kt
@Serializable
data class Passkey(
    val id: PasskeyId,
    val relyingParty: String,
    val username: String,
    val credentialId: ByteArray,
    val publicKey: ByteArray,
    val signatureCounter: Long,
    val createdAt: Instant,
    val lastUsed: Instant?
) : DomainModel
```

#### 1.3 Value Objects
```kotlin
// commonMain/kotlin/com/chimali/core/domain/valueobject/
@JvmInline
value class CredentialId(val value: String)

@JvmInline
value class PasskeyId(val value: String)

@JvmInline
value class EncryptedString(val value: String)

enum class CredentialCategory {
    SOCIAL, WORK, FINANCIAL, PERSONAL, DEVELOPMENT, SHOPPING, OTHER
}

@Serializable
data class CredentialTag(val name: String)
```

### Phase 2: Repository Layer (High Priority)

#### 2.1 Repository Interfaces
```kotlin
// commonMain/kotlin/com/chimali/core/domain/repository/CredentialRepository.kt
interface CredentialRepository {
    suspend fun getAllCredentials(): Result<List<Credential>>
    suspend fun getCredentialById(id: CredentialId): Result<Credential>
    suspend fun searchCredentials(query: String): Result<List<Credential>>
    suspend fun saveCredential(credential: Credential): Result<CredentialId>
    suspend fun updateCredential(credential: Credential): Result<Unit>
    suspend fun deleteCredential(id: CredentialId): Result<Unit>
    suspend fun getCredentialsByCategory(category: CredentialCategory): Result<List<Credential>>
    suspend fun getFavoriteCredentials(): Result<List<Credential>>
    suspend fun updateLastUsed(id: CredentialId): Result<Unit>
}

// commonMain/kotlin/com/chimali/core/domain/repository/PasskeyRepository.kt
interface PasskeyRepository {
    suspend fun getAllPasskeys(): Result<List<Passkey>>
    suspend fun getPasskeyById(id: PasskeyId): Result<Passkey>
    suspend fun getPasskeysByRelyingParty(rp: String): Result<List<Passkey>>
    suspend fun createPasskey(passkey: Passkey): Result<PasskeyId>
    suspend fun updatePasskey(passkey: Passkey): Result<Unit>
    suspend fun deletePasskey(id: PasskeyId): Result<Unit>
    suspend fun updateSignatureCounter(id: PasskeyId, counter: Long): Result<Unit>
}
```

### Phase 3: Business Logic Use Cases (High Priority)

#### 3.1 Credential Management Use Cases
```kotlin
// commonMain/kotlin/com/chimali/core/domain/usecase/credential/
import org.koin.core.annotation.Factory

@Factory
class GetCredentialsUseCase(
    private val repository: CredentialRepository
) : BaseUseCaseNoParams<List<Credential>>() {
    override suspend fun invoke(): Result<List<Credential>> {
        return repository.getAllCredentials()
    }
}

class SearchCredentialsUseCase(
    private val repository: CredentialRepository
) : BaseUseCase<String, List<Credential>>() {
    override suspend fun invoke(parameters: String): Result<List<Credential>> {
        return if (parameters.isBlank()) {
            repository.getAllCredentials()
        } else {
            repository.searchCredentials(parameters)
        }
    }
}

class SaveCredentialUseCase(
    private val repository: CredentialRepository,
    private val validator: CredentialValidator
) : BaseUseCase<Credential, CredentialId>() {
    override suspend fun invoke(parameters: Credential): Result<CredentialId> {
        return validator.validate(parameters)
            .map { repository.saveCredential(parameters) }
    }
}

class CopyCredentialToClipboardUseCase(
    private val repository: CredentialRepository,
    private val clipboardService: ClipboardManagerService,
    private val cryptoService: CryptoService
) : BaseUseCase<CredentialId, Unit>() {
    override suspend fun invoke(parameters: CredentialId): Result<Unit> {
        return repository.getCredentialById(parameters)
            .map { credential ->
                val decryptedPassword = cryptoService.decrypt(credential.password)
                clipboardService.copySensitiveData(
                    label = credential.title,
                    text = decryptedPassword
                )
                repository.updateLastUsed(parameters)
            }
    }
}
```

#### 3.2 Passkey Management Use Cases
```kotlin
// commonMain/kotlin/com/chimali/core/domain/usecase/passkey/
class GetPasskeysUseCase(
    private val repository: PasskeyRepository
) : BaseUseCaseNoParams<List<Passkey>>() {
    override suspend fun invoke(): Result<List<Passkey>> {
        return repository.getAllPasskeys()
    }
}

class CreatePasskeyUseCase(
    private val repository: PasskeyRepository,
    private val fido2Service: Fido2Service
) : BaseUseCase<CreatePasskeyRequest, PasskeyId>() {
    override suspend fun invoke(parameters: CreatePasskeyRequest): Result<PasskeyId> {
        return fido2Service.makeCredential(parameters)
            .map { passkey -> repository.createPasskey(passkey) }
    }
}
```

### Phase 4: Cross-Cutting Concerns (Medium Priority)

#### 4.1 Domain Exceptions
```kotlin
// commonMain/kotlin/com/chimali/core/domain/exception/
sealed class DomainException : Exception() {
    object CredentialNotFound : DomainException()
    object PasskeyNotFound : DomainException()
    object EncryptionFailed : DomainException()
    object DecryptionFailed : DomainException()
    object InvalidCredentials : DomainException()
    data class ValidationError(val field: String, val message: String) : DomainException()
    data class RepositoryError(override val message: String, override val cause: Throwable? = null) : DomainException()
}
```

#### 4.2 Validation Framework
```kotlin
// commonMain/kotlin/com/chimali/core/domain/validation/
interface Validator<T> {
    fun validate(item: T): Result<T>
}

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
```

#### 4.3 Time Abstraction
// commonMain/kotlin/com/chimali/core/domain/time/
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.annotation.Single

@Single
class TimeProvider {
    fun now(): Instant = Clock.System.now()
    fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

### Phase 5: Testing Infrastructure (Medium Priority)

#### 5.1 Test Doubles
```kotlin
// commonTest/kotlin/com/chimali/core/domain/repository/
class FakeCredentialRepository : CredentialRepository {
    private val credentials = mutableMapOf<CredentialId, Credential>()
    
    override suspend fun getAllCredentials(): Result<List<Credential>> =
        Result.success(credentials.values.toList())
    
    override suspend fun getCredentialById(id: CredentialId): Result<Credential> =
        credentials[id]?.let { Result.success(it) }
            ?: Result.failure(DomainException.CredentialNotFound)
    
    override suspend fun saveCredential(credential: Credential): Result<CredentialId> {
        val id = CredentialId(UUID().toString())
        val newCredential = credential.copy(id = id)
        credentials[id] = newCredential
        return Result.success(id)
    }
    
    // Additional implementations...
}
```

#### 5.2 Test Utilities
```kotlin
// commonTest/kotlin/com/chimali/core/domain/util/
object TestDataFactory {
    fun createCredential(
        id: CredentialId = CredentialId("test-id"),
        title: String = "Test Credential",
        username: String = "testuser",
        password: String = "password123"
    ) = Credential(
        id = id,
        title = title,
        username = username,
        password = EncryptedString(password),
        url = null,
        notes = null,
        category = CredentialCategory.PERSONAL,
        tags = emptyList(),
        createdAt = TimeProvider().now(),
        lastModified = TimeProvider().now(),
        lastUsed = null,
        isFavorite = false
    )
}
```

### Phase 6: Dependency Injection (Low Priority)

#### 6.1 Domain Module
```kotlin
// commonMain/kotlin/com/chimali/core/domain/di/DomainModule.kt
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.chimali.core.domain")
class DomainModule
// Note: Dependencies are auto-wired via @Factory and @Single annotations on the actual classes.
```

## Implementation Timeline

### Sprint 1 (Week 1-2)
- [ ] Generic UseCase patterns
- [ ] Core domain models (Credential, Passkey)
- [ ] Value objects and enums
- [ ] Repository interfaces

### Sprint 2 (Week 3-4)
- [ ] Credential management use cases
- [ ] Passkey management use cases
- [ ] Domain exceptions
- [ ] Validation framework

### Sprint 3 (Week 5-6)
- [ ] Time abstraction
- [ ] Test infrastructure
- [ ] Dependency injection module
- [ ] Integration tests

## Success Criteria

1. **All domain logic is platform-agnostic** and resides in `commonMain`
2. **Clean Architecture principles** are followed with clear separation of concerns
3. **Comprehensive test coverage** for all use cases and domain models
4. **Proper error handling** with domain-specific exceptions
5. **Dependency injection** works seamlessly across platforms
6. **Documentation** is complete for all public APIs

## Dependencies

### Required Modules
- `core:common` (already KMP)
- `core:security` (already KMP)
*(Note: `core:domain` strictly does NOT depend on data or database layers to preserve Clean Architecture)*

### External Dependencies
- Kotlinx Coroutines
- Kotlinx Serialization
- Koin Annotations
- Kermit (logging)

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Domain logic complexity | High | Start with simple use cases, iterate incrementally |
| Platform-specific requirements | Medium | Use expect/actual pattern for platform abstractions |
| Testing coverage gaps | Medium | Implement test doubles and comprehensive unit tests |
| Performance issues | Low | Profile and optimize critical paths |

## Conclusion

This migration plan establishes a robust, platform-agnostic domain layer that will serve as the foundation for Chimali's business logic across Android and iOS platforms. The phased approach ensures manageable implementation while maintaining architectural integrity.

The enhanced `core:domain` module will provide:
- Clean, testable business logic
- Platform-agnostic domain models
- Comprehensive error handling
- Scalable use case patterns
- Solid foundation for future features

**Next Steps**: Begin Phase 1 implementation with generic UseCase patterns and core domain models.
