# FIDO2 legacy repository owner map

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`  
**Scope:** `Fido2Service`, `Fido2ServiceImpl`, `Fido2Repository`, and `Fido2RepositoryImpl`.

## Registration request

`Fido2Service.registerNewCredential` accepts `RpId`, `userName`, and `userDisplayName`. `Fido2ServiceImpl` forwards these values to `Fido2Repository.registerCredential` without making copies or storing them. The DI-bound `Fido2RepositoryImpl` is reachable from the service and currently implements the deferred registration stub: it discards all three arguments and returns the fixed `mock-credential-id` value. The stub performs no persistence, logging, diagnostic formatting, or mutable conversion.

`RpId` is an immutable typed value; the two account-name parameters are immutable Kotlin `String`s. Their lifetime is managed by the runtime and cannot be deterministically erased by these owners. The reviewed path adds no app-owned retention beyond the synchronous/suspending call chain. This records the current deferred implementation and does not assert that the mock result is a valid registration or approve its future implementation.

## Credential listing and deletion

`getAllCredentials` forwards the repository `Flow<PasskeyCredential>` unchanged. This service/repository wrapper creates no replay cache or additional collection; the returned flow's producer and each collector remain separate owners and require caller/producer lifetime review. `deleteCredential` forwards immutable `CredentialId` and returns the repository outcome without copying credential payloads. Repository, DAO, event-store, provider, and database ownership are covered separately and are not closed by this wrapper map.

## Outcome and limits

Source review found no mutable secret owner or retained diagnostic/persistence value in these wrappers. No cleanup code change is indicated for the current stub. The immutable name strings, future real registration implementation, Flow producer/collector lifetimes, and provider/database copies remain explicit boundaries. No runtime, heap, or physical-device claim is made.
