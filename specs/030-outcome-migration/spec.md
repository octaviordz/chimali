# Feature Specification: Outcome Migration & Functional Exception Expansion

**Feature Branch**: `030-outcome-migration`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: User description: "Expand the use of DataResult and DomainError (renamed to Outcome) based on identified opportunities in fido2 and vault modules."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Type-Safe FIDO2 Operations (Priority: P1)

As a developer, I want all FIDO2 crypto and repository operations to return an `Outcome` instead of a standard `Result` or throwing exceptions, so that I am forced to handle all domain-level errors explicitly at the call site.

**Why this priority**: FIDO2 operations are critical security paths. Using `Outcome` ensures that error handling is exhaustive and that we don't leak platform-specific exceptions into the UI or domain logic.

**Independent Test**: Can be tested by mocking repository failures (e.g., SQLite exception) and verifying the calling code receives a categorized `Outcome.Error(DomainError.DatabaseError)` and logs the cause at the boundary.

**Acceptance Scenarios**:

1. **Given** a FIDO2 crypto service, **When** a `GeneralSecurityException` occurs during encryption, **Then** it returns `Outcome.Error(DomainError.CryptoError)` with the original exception as the cause.
2. **Given** a FIDO2 repository, **When** a `SQLException` occurs during a save operation, **Then** it returns `Outcome.Error(DomainError.DatabaseError)` and logs the error via Kermit.

---

### User Story 2 - Exception-Free ViewModels in Vault (Priority: P1)

As a developer, I want `VaultViewModel` to be free of `try-catch` blocks for business operations, using exhaustive `when` expressions on `Outcome` returned by `VaultService`.

**Why this priority**: Improves architectural consistency and makes the UI layer simpler and more robust. Eliminates "TooGenericExceptionCaught" suppressions.

**Independent Test**: Can be tested by triggering a network or storage failure in `VaultService` and verifying `VaultViewModel` updates the UI state correctly through the `Outcome.Error` branch without any explicit `try-catch`.

**Acceptance Scenarios**:

1. **Given** `VaultService.getItems` is called, **When** a storage error occurs, **Then** `VaultViewModel` handles the `Outcome.Error` and updates the `errorMessage` in the UI state.

---

### Edge Cases

- **CancellationException**: How does the system handle coroutine cancellation when using functional catching? (Assumed: It MUST be rethrown to preserve structured concurrency).
- **Unknown Exceptions**: How does the system handle undocumented runtime exceptions? (Assumed: Mapped to `DomainError.UnknownError` with the original cause preserved).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST rename `DataResult` to `Outcome` across the entire codebase to improve semantic clarity.
- **FR-002**: FIDO2 crypto services (e.g., `CredentialEncryptionService`, `Fido2CryptoService`) MUST return `Outcome<T, DomainError>`.
- **FR-003**: FIDO2 repository implementations MUST return `Outcome<T, DomainError>` for all operations that can fail.
- **FR-004**: `VaultService` MUST return `Outcome<T, DomainError>` for all data fetching and persistence operations.
- **FR-005**: ViewModels MUST NOT use `try-catch` blocks for business operation flow control; they MUST use exhaustive `when` expressions on `Outcome`.
- **FR-006**: System MUST log categorized errors via Kermit at the repository/service boundary before wrapping them in `Outcome.Error`.

### Key Entities *(include if feature involves data)*

- **Outcome<D, E : DomainError>**: A functional wrapper representing either a success with data `D` or a failure with `DomainError` `E`.
- **DomainError**: A sealed interface representing categorized errors (Crypto, Database, Network, etc.) with an optional `cause: Throwable`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero `@Suppress("TooGenericExceptionCaught")` annotations remain in the `feature:fido2` and `feature:vault` modules.
- **SC-002**: 100% of public methods in `VaultService` return `Outcome`.
- **SC-003**: 100% of repositories in the `fido2` module return `Outcome` instead of `Result`.
- **SC-004**: Static analysis (local-ci.ps1) passes with zero violations related to exception catching in the presentation layer.

## Assumptions

- **Existing Infrastructure**: The `DomainError` hierarchy and `runCatchingResult` utilities (to be renamed to `runCatchingOutcome`) are available in `core/common`.
- **Logging Policy**: Kermit is the designated logging framework for boundary logging.
- **Scope**: This migration focus specifically on `fido2` and `vault` modules as identified in the opportunities analysis.
