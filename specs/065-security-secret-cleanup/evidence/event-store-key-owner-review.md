# Event-Store Key Ownership Review

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`EventStoreKeyProviderImpl` derives a 32-byte key from an owned wallet-root PRK copy. It clears the root copy, encoded label and full HMAC-SHA512 result in `finally`, returning a separate truncated key array. The shared `EventStoreKeyProvider` API now states that the returned array transfers to the caller and must be erased after cryptographic use on success, failure and cancellation. `EventStoreRepositoryImpl` and `SnapshotRepositoryImpl` both clear acquired key arrays in `finally`.

`EventStoreKeyProviderImplTest` retains all returned synthetic key arrays and erases them in `@AfterEach`; its reference key is erased in a `finally` block. The 3-case provider suite and core-security KtLint pass.

**Limits:** This is source and host-test evidence. It does not establish that JCA `SecretKeySpec`/`Mac` internals erase copied key material, prove every injected provider implementation or caller follows the API rule, or establish post-operation heap behavior.
