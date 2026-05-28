# Data Model: Remove AuthenticateCredentialUseCase Stub and Consolidate into GetAssertionUseCase

**Branch**: `051-remove-auth-credential-stub` | **Date**: 2026-05-28

## Summary

No data model or schema changes are required. This feature consists of dead code deletion and presentation-to-use-case rewiring.

## Interfaces Modified

The following interface contracts are modified to remove dead methods:

### `Fido2Repository`

```diff
-    suspend fun authenticateCredential(rpId: RpId): Outcome<CredentialId, DomainError>
```

### `Fido2Service`

```diff
-    suspend fun authenticateWithCredential(rpId: RpId): Outcome<CredentialId, DomainError>
```
