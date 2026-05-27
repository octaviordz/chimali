# Data Model: Authenticate Credential Use Case

**Branch**: `050-authenticate-credential-usecase` | **Date**: 2026-05-26

## Summary

No data model changes are required for this feature. This is a pure structural refactoring that extracts existing behavior into a use case class.

## Existing Entities (Unchanged)

The following entities are involved in the authentication flow but are **not modified**:

| Entity | Role | Location |
|--------|------|----------|
| `RpId` | Value object — relying party identifier (input) | `core/domain/valueobject/` |
| `CredentialId` | Value object — credential identifier (output) | `core/domain/valueobject/` |
| `DomainError` | Sealed class — error type hierarchy | `core/common/result/` |
| `Outcome<D, E>` | Result type — success or error wrapper | `core/common/result/` |

## Interfaces (Unchanged)

| Interface | Method | Signature |
|-----------|--------|-----------|
| `Fido2Repository` | `authenticateCredential` | `suspend fun authenticateCredential(rpId: RpId): Outcome<CredentialId, DomainError>` |
| `Fido2Service` | `authenticateWithCredential` | `suspend fun authenticateWithCredential(rpId: RpId): Outcome<CredentialId, DomainError>` |

No new entities, value objects, or schema changes are introduced.
