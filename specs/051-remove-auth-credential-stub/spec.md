# Feature Specification: Remove AuthenticateCredentialUseCase Stub and Consolidate into GetAssertionUseCase

**Feature Branch**: `051-remove-auth-credential-stub`

**Created**: 2026-05-28

**Status**: Draft

**Input**: User description: "I did not know about GetAssertionUseCase, must rectify, validate that AuthenticateCredentialUseCase is not doing anything useful, move all useful logic to GetAssertionUseCase then remove AuthenticateCredentialUseCase. The secondary goal still mostly applies, but is also updated. Goal refactor source code related to GetAssertionUseCase to be similar to `now in android (KMP version)` source code at D:\octav\source\repos\nowinkmp, they connect the application ui with the use case at the ViewModel level, the use case must be the main interactor for the application level logic."

## Context

Branch `050-authenticate-credential-usecase` created `AuthenticateCredentialUseCase` as a structural refactoring. However, investigation reveals it has **no real authentication logic**: the underlying `Fido2RepositoryImpl.authenticateCredential()` is a placeholder stub returning `Outcome.Success(CredentialId.fromEncoded("mock-authentication-id"))`, explicitly deferred in a DEFERRED(040) comment awaiting a real CTAP2 implementation.

The **real and complete** FIDO2 authentication use case is `GetAssertionUseCase`, which implements the full CTAP2 GetAssertion ceremony: credential discovery, user verification, cryptographic signing, sign-count management, and authenticator data assembly. `AuthenticationPromptViewModel` already correctly invokes `GetAssertionUseCase` directly.

`AuthenticateCredentialUseCase` is therefore dead code wrapping a mock stub — it provides zero useful logic not already handled by `GetAssertionUseCase`.

In parallel, `RegistrationPromptViewModel` still injects `Fido2Service` and calls `Fido2Service.makeCredential()`, adding one indirection level over `RegisterCredentialUseCase`. Per the NowInKMP architectural pattern, ViewModels should inject use cases directly as the primary application-logic interactors.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validate and Remove AuthenticateCredentialUseCase (Priority: P1)

As a developer maintaining the codebase, I want `AuthenticateCredentialUseCase` removed after confirming it carries no logic not already provided by `GetAssertionUseCase`, so that the codebase has no dead code and no misleading "authentication" abstraction that bypasses the real FIDO2 ceremony.

**Why this priority**: This is the root correction of the mistake made in branch 050. Keeping a use case that wraps a mock stub (a) creates confusion about which use case represents real authentication, (b) pollutes the `domain/usecase` directory with non-functional code, and (c) leaves `Fido2Service.authenticateWithCredential` and `Fido2Repository.authenticateCredential` as legacy stub surfaces that imply a path to real authentication that does not exist.

**Independent Test**: Can be fully tested by confirming that (a) all code paths that previously referenced `AuthenticateCredentialUseCase` either compile without it or are themselves removed, and (b) the project passes all existing tests with no regressions.

**Acceptance Scenarios**:

1. **Given** the codebase after this refactoring, **When** searching for `AuthenticateCredentialUseCase`, **Then** no source files in `src/` reference it — it has been fully removed.
2. **Given** the stub method `Fido2Repository.authenticateCredential()` and `Fido2RepositoryImpl.authenticateCredential()`, **When** this refactoring is complete, **Then** these are also removed (they have no caller outside the deleted use case).
3. **Given** `Fido2Service.authenticateWithCredential()` and its implementation in `Fido2ServiceImpl`, **When** this refactoring is complete, **Then** these are also removed or replaced as part of cleaning up the stub surface.
4. **Given** `AuthenticateCredentialUseCaseTest`, **When** this refactoring is complete, **Then** the test file is deleted (it tests a deleted class).

---

### User Story 2 - GetAssertionUseCase Is the Sole Authentication Interactor (Priority: P2)

As a developer, I want to confirm that `GetAssertionUseCase` already is — and remains — the complete, sole use case responsible for FIDO2 authentication, so that the architecture is clear and unambiguous about where authentication logic lives.

**Why this priority**: Validates the correctness of the remaining authentication architecture after removing the stub. `AuthenticationPromptViewModel` already delegates to `GetAssertionUseCase` — this story confirms no other path needs to be changed for authentication.

**Independent Test**: Verified by confirming that `AuthenticationPromptViewModel` retains `GetAssertionUseCase` as its primary authentication interactor, and the full GetAssertion ceremony (credential lookup → user verification → sign → sign count update) is traceable through that single use case.

**Acceptance Scenarios**:

1. **Given** `AuthenticationPromptViewModel`, **When** an authentication ceremony is performed, **Then** it is executed entirely through `GetAssertionUseCase` — no other use case or service handles authentication.
2. **Given** `GetAssertionUseCase`, **When** reviewing its implementation, **Then** it contains the complete FIDO2 GetAssertion ceremony logic (credential discovery, user verification, signing, sign count, auth data), with no dependencies on the removed stub.
3. **Given** the `domain/usecase` directory, **When** reviewing it after the refactoring, **Then** `AuthenticateCredentialUseCase.kt` is absent and `GetAssertionUseCase.kt` remains unchanged.

---

### User Story 3 - RegistrationPromptViewModel Injects RegisterCredentialUseCase Directly (Priority: P3)

As a developer, I want `RegistrationPromptViewModel` to inject and invoke `RegisterCredentialUseCase` directly instead of calling through `Fido2Service`, so that both authentication and registration ViewModels consistently follow the NowInKMP pattern of use cases being the primary application-logic interactors at the ViewModel boundary.

**Why this priority**: Symmetry goal. `AuthenticationPromptViewModel` already injects `GetAssertionUseCase` directly. `RegistrationPromptViewModel` is the only ViewModel that still routes through the service layer, creating an architectural inconsistency. This is a pure structural refactoring with no behavioral change.

**Independent Test**: Verified by inspecting `RegistrationPromptViewModel`'s constructor — it must list `RegisterCredentialUseCase` as a dependency and must NOT list `Fido2Service`.

**Acceptance Scenarios**:

1. **Given** `RegistrationPromptViewModel`, **When** inspecting its constructor dependencies, **Then** `RegisterCredentialUseCase` is present as a direct dependency and `Fido2Service` is NOT present.
2. **Given** a valid `MakeCredentialOptions`, **When** the registration ceremony executes, **Then** the call is delegated directly to `RegisterCredentialUseCase` and produces an outcome identical to the previous `Fido2Service.makeCredential()` path.
3. **Given** `RegisterCredentialUseCase` returns an error, **When** the ViewModel processes it, **Then** the registration state transitions to `Error` with no behavioral change visible to the user or calling layer.

---

### Edge Cases

- What happens to `Fido2Service.authenticateWithCredential()` and `Fido2Repository.authenticateCredential()`? Since their only callers are the stub use case and its mock implementation, they become unreachable. Both the interface method and implementation must be removed to avoid dead code in the service and repository layers.
- What happens if any integration test or transport-layer code calls `Fido2Service.authenticateWithCredential()` directly? A compile-time error will appear, making the dependency visible and forcing it to be updated to use `GetAssertionUseCase` directly.
- What happens to `Fido2ServiceImpl` after `authenticateWithCredential` is removed? The class is updated to remove that method and its `AuthenticateCredentialUseCase` constructor parameter. If no other callers remain for `Fido2Service`, the interface itself may eventually be evaluated for removal — but that is out of scope for this refactoring.
- What happens to the `RegistrationPromptViewModel` tests? There are no separate unit tests for `RegistrationPromptViewModel` in the test sources, so no test updates are required for the registration change.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `AuthenticateCredentialUseCase` MUST be deleted from the `domain/usecase` directory.
- **FR-002**: `AuthenticateCredentialUseCaseTest` MUST be deleted.
- **FR-003**: `Fido2Repository.authenticateCredential()` MUST be removed from the `Fido2Repository` interface.
- **FR-004**: `Fido2RepositoryImpl.authenticateCredential()` MUST be removed from the implementation.
- **FR-005**: `Fido2Service.authenticateWithCredential()` MUST be removed from the `Fido2Service` interface.
- **FR-006**: `Fido2ServiceImpl` MUST be updated to remove the `authenticateWithCredential` method and the `AuthenticateCredentialUseCase` constructor parameter.
- **FR-007**: `GetAssertionUseCase` MUST NOT be modified — it is already the correct and complete authentication interactor.
- **FR-008**: `AuthenticationPromptViewModel` MUST NOT be modified — it already correctly injects and invokes `GetAssertionUseCase` directly.
- **FR-009**: `RegistrationPromptViewModel` MUST be updated to inject `RegisterCredentialUseCase` directly and remove the `Fido2Service` dependency.
- **FR-010**: The observable behavior of the registration flow — state transitions, error propagation, cancellation, success — MUST remain identical after removing the `Fido2Service` indirection.
- **FR-011**: No new business logic, error paths, or domain behavior MUST be introduced by this refactoring.
- **FR-012**: The project MUST compile and all existing tests MUST pass after all changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: No source file in `src/` contains a reference to `AuthenticateCredentialUseCase` — verified by search.
- **SC-002**: `Fido2Repository` interface no longer declares `authenticateCredential()` — verified by code inspection.
- **SC-003**: `Fido2Service` interface no longer declares `authenticateWithCredential()` — verified by code inspection.
- **SC-004**: `RegistrationPromptViewModel` constructor contains `RegisterCredentialUseCase` and does NOT contain `Fido2Service` — verified by code inspection.
- **SC-005**: `AuthenticationPromptViewModel` constructor still contains `GetAssertionUseCase` — verified by code inspection (unchanged).
- **SC-006**: The project compiles successfully after all deletions and modifications with no unresolved references.
- **SC-007**: All existing tests pass after the refactoring, confirming zero behavioral regressions.

## Assumptions

- `GetAssertionUseCase` is already fully complete and correct — it requires no modification as part of this refactoring.
- `AuthenticationPromptViewModel` already follows the NowInKMP pattern (ViewModel → UseCase directly) — it requires no modification.
- The `Fido2Repository` stub methods (`registerCredential`, `authenticateCredential`) were created as early placeholders before real CTAP2 ceremonies were implemented. Removing `authenticateCredential` is safe as it is only referenced by the deleted use case.
- `Fido2Service.registerNewCredential()` (the simple registration path, distinct from `makeCredential`) is out of scope — it may also be a stub but is not part of this refactoring.
- `Fido2ServiceImpl` will retain its other methods (`makeCredential`, `registerNewCredential`, `getAllCredentials`, `deleteCredential`, `isSupported`) — only `authenticateWithCredential` and its use case constructor parameter are removed.
- No integration tests or transport-layer code calls `Fido2Service.authenticateWithCredential()` directly — compile errors at that point would surface the dependency, which is expected and acceptable.
