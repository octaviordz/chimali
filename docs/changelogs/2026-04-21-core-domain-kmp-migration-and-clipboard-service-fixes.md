# Detailed Changes: April 21, 2026

## Domain Layer KMP Migration & Clipboard Service Stabilization

### core:domain KMP Migration
- **Kotlin Multiplatform Transition**: Successfully migrated the business logic layer (`core:domain`) to KMP, enabling sharing of use cases and entities across Android and iOS.
- **Serializable Models**: Annotated domain entities like `Credential` and `Passkey` with `@Serializable` and introduced explicit serialization for value objects (e.g., `CredentialId`, `EncryptedString`) to ensure robust cross-platform data exchange.
- **Clean Architecture Implementation**:
    - Defined pure Kotlin repository interfaces.
    - Implemented MVI-ready use cases (e.g., `GetCredentialsUseCase`, `SaveCredentialUseCase`) using Koin Annotations for dependency injection.
- **Cross-Platform Verification**: Validated the migration by executing `./gradlew :core:domain:allTests`, confirming successful execution of business logic tests on both Android and iOS targets.

### Clipboard Service Refactor
- **Concurrency Stabilization**: Refactored the `ClipboardManagerService` interface to use `suspend` functions. This change enables the use of Kotlin `Mutex` in platform implementations, ensuring thread-safe access to the system clipboard without blocking the main thread.
- **Bug Fixes**:
    - Resolved a critical compilation error in `AndroidClipboardManagerService` caused by calling the suspending `Mutex.withLock` from a synchronous context.
    - Fixed `ClipboardError.kt` to resolve property hiding issues (specifically `message` hiding `Throwable.message`) and corrected syntax errors in the sealed class hierarchy.
- **Caller Updates**:
    - Migrated `DevToolsViewModel` (FIDO2) to use `viewModelScope.launch` for secure mnemonic copying.
    - Updated `CopyCredentialToClipboardUseCase` in the domain layer to handle asynchronous clipboard operations and properly propagate `Result` types.

### Code Quality & Hygiene
- **Duplicate Removal**: Eliminated a redundant `ClipboardManagerService` definition in `core:domain`, consolidating on the standardized version in `core:common`.
- **DI Consistency**: Ensured consistent use of `@Single` and `@Factory` annotations across the newly migrated domain layer.
