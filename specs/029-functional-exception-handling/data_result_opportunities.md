# Outcome & DomainError - Opportunities for Improvement

Based on an analysis of the current Chimali codebase and the functional exception handling implementation plan, several key areas have been identified where migrating to `Outcome<D, E>` and `DomainError` patterns will improve maintainability, type safety, and architectural compliance.

## 1. `feature/fido2` Module (Data & Domain Layers)

The `fido2` module heavily uses Kotlin's standard `Result<T>` (which wraps a raw `Throwable`) and a custom `Fido2Exception` hierarchy.

### Current Pattern Example (`CredentialEncryptionService.kt`):
```kotlin
suspend fun encrypt(data: ByteArray): Result<EncryptedData> {
    return try {
        // ... crypto logic
    } catch (e: GeneralSecurityException) {
        Result.failure(Fido2Exception.EncryptionFailed(e.message, e))
    }
}
```

### Improvement Opportunity:
Migrate these services and repositories to return `Outcome<T, DomainError>`, replacing `Fido2Exception` with the sealed `DomainError` hierarchy (e.g., `DomainError.CryptoError`, `DomainError.DatabaseError`). This eliminates leaking raw throwables to the UI layer and removes the need for exception downcasting.

### Key Files to Migrate:
- `CredentialStorageService.kt`
- `CredentialEncryptionService.kt`
- `Fido2CryptoService.kt`
- `CredentialRepositoryImpl.kt` (and other Repository implementations like `PasskeyCredentialRepositoryImpl.kt`)
- `Ctap2MakeCredentialHandler.kt` (and other CTAP2 handlers)

## 2. `feature/vault` Module (Service & Presentation Layers)

The `VaultViewModel` was previously refactored to catch specific exceptions (like `IOException`) instead of a generic `Exception`. However, following the `Outcome.kt` architectural contract, the ViewModel should **not** contain `try-catch` blocks for business operations at all.

### Current Pattern Example (`VaultViewModel.kt`):
```kotlin
try {
    val items = vaultService.getItems()
    _state.update { it.copy(items = items) }
} catch (e: IOException) {
    _state.update { it.copy(errorMessage = e.message) }
}
```

### Improvement Opportunity:
Refactor `VaultService` to return `Outcome<List<Item>, DomainError.NetworkError>`. Then, update `VaultViewModel` to use an exhaustive `when` expression, which completely removes flow-control via exceptions in the presentation layer.

### Target Pattern Example:
```kotlin
when (val result = vaultService.getItems()) {
    is Outcome.Success -> _state.update { it.copy(items = result.data) }
    is Outcome.Error -> _state.update { it.copy(errorMessage = result.error.message) }
}
```

### Key Files to Migrate:
- `VaultService.kt` (and its implementation)
- `VaultViewModel.kt`

## 3. General Best Practices and Benefits

1. **Exhaustive Error Handling**: By using `Outcome<D, E : DomainError>`, the compiler forces the UI layer to handle all possible error branches.
2. **Crashlytics Integrity**: `DomainError` retains the `cause` throwable, ensuring we don't lose stack traces when logging errors at the data/repository boundary.
3. **Detekt Compliance**: Prevents the need to suppress `TooGenericExceptionCaught`, as `try-catch` blocks are pushed down to the boundary where specific exceptions are caught.
4. **Performance**: Avoids the performance penalty of instantiating and throwing exceptions for standard business logic flow.
