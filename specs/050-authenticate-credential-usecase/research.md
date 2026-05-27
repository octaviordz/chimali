# Research: Authenticate Credential Use Case

**Branch**: `050-authenticate-credential-usecase` | **Date**: 2026-05-26

## Research Summary

No NEEDS CLARIFICATION items were identified during planning. This feature is a pure structural refactoring with a well-established pattern to follow.

## Findings

### 1. Established Use Case Pattern

- **Decision**: Follow the exact `@Factory` + `operator fun invoke()` pattern used by all 12 existing use cases
- **Rationale**: Consistency is the entire purpose of this refactoring. The pattern is well-established across `DeleteCredentialUseCase`, `GetAllCredentialsUseCase`, `SearchCredentialsUseCase`, `GetAssertionUseCase`, `RegisterCredentialUseCase`, and 7 others.
- **Alternatives considered**: None — deviating from the established pattern would defeat the purpose

### 2. Repository Dependency Choice

- **Decision**: Inject `Fido2Repository` (not `CredentialRepository`)
- **Rationale**: The current `Fido2ServiceImpl.authenticateWithCredential` delegates to `fido2Repository.authenticateCredential(rpId)`. The `authenticateCredential` method exists on `Fido2Repository`, not on `CredentialRepository`. To preserve identical behavior, the use case must depend on the same repository.
- **Alternatives considered**: Injecting `CredentialRepository` — rejected because `authenticateCredential` is not a method on that interface. The existing delegation path is `Fido2Service → Fido2Repository → (deferred CTAP2 implementation)`.

### 3. DI Registration

- **Decision**: Use `@Factory` annotation only; no changes to `Fido2Module.kt`
- **Rationale**: `Fido2Module` uses `@ComponentScan("com.chimali.fido2")` which auto-discovers all `@Single`, `@Factory`, and `@KoinViewModel` annotated classes in the package tree. All existing use cases rely on this mechanism — no manual registration needed.
- **Alternatives considered**: Manual `@Single` factory method in `Fido2Module` — rejected as unnecessary and inconsistent with the existing pattern.

### 4. Test Strategy

- **Decision**: Rely on existing test suite for regression; no new test class required
- **Rationale**: No existing unit tests cover `Fido2ServiceImpl` directly. The refactoring preserves identical behavior, so existing integration tests (e.g., `Fido2StressTest`) that exercise the authenticate path will validate correctness. Adding a trivial delegation test for the new use case is optional but not required by the spec.
- **Alternatives considered**: Creating a new `AuthenticateCredentialUseCaseTest` — acceptable as a follow-up but out of scope for this refactoring spec (which explicitly states "no new features, error paths, or behavioral changes").
