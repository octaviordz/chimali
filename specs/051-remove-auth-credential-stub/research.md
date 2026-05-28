# Research: Remove AuthenticateCredentialUseCase Stub and Consolidate into GetAssertionUseCase

**Branch**: `051-remove-auth-credential-stub` | **Date**: 2026-05-28

## Research Summary

No `NEEDS CLARIFICATION` items were identified during planning. The codebase investigation confirmed that:
1. `AuthenticateCredentialUseCase` is indeed a stub delegation wrapping a mock repository method `Fido2Repository.authenticateCredential(rpId)` returning a hardcoded `mock-authentication-id`.
2. `GetAssertionUseCase` implements the complete FIDO2 CTAP2 assertion ceremony and is correctly invoked by `AuthenticationPromptViewModel`.
3. The stub `AuthenticateCredentialUseCase` is completely dead code and should be removed.
4. Swapping `Fido2Service` with `RegisterCredentialUseCase` in `RegistrationPromptViewModel` aligns the registration flow with the NowInKMP use-case-first VM architecture.

## Findings

### 1. Consolidating Authentication Logic

- **Decision**: Keep `GetAssertionUseCase` as the sole entry point for real FIDO2 assertion ceremonies, and delete `AuthenticateCredentialUseCase`.
- **Rationale**: Having two use cases for authentication (one of which is a mock stub returning hardcoded values) creates high architectural confusion and is a violation of the YAGNI principle.
- **Alternatives considered**: Rewriting `AuthenticateCredentialUseCase` to handle a simpler/fallback authentication flow — rejected because `GetAssertionUseCase` already comprehensively handles all authentications, and no distinct fallback flow is needed.

### 2. ViewModel UseCase Injection

- **Decision**: Inject `RegisterCredentialUseCase` directly into `RegistrationPromptViewModel` and completely remove `Fido2Service` from the ViewModel.
- **Rationale**: Aligns the registration architecture with the established `AuthenticationPromptViewModel` structure and the reference project `nowinkmp` pattern (ViewModel → UseCase).
- **Alternatives considered**: Retaining `Fido2Service` and delegating — rejected because it adds an unnecessary layer of indirection at the ViewModel boundary.

### 3. Cleanup of Stub Surfaces

- **Decision**: Delete the stub methods `Fido2Repository.authenticateCredential` and `Fido2Service.authenticateWithCredential` completely.
- **Rationale**: Since the stub use case is their only consumer, retaining these methods in the interfaces and implementations would only preserve dead code.
