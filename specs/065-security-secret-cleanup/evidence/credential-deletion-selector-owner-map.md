# Credential deletion selector owner map

**Finding:** SEC065-FIDO-022  
**Classification:** CLS-FIDO-002  
**Requirements:** FR-002, FR-004, NFR-SEC-041, TR-AUTH-031  
**Revision:** Dirty exploratory worktree; source review 2026-11-16

`DeleteCredentialUseCase` borrows a typed `CredentialId` and delegates it directly to `CredentialRepository.deleteCredential`. The identifier is an immutable Base64URL String; the use case makes no decoded byte copy and does not log or persist the selector. `DeleteAllCredentialsUseCase` similarly borrows an optional immutable `RpId` and delegates it or `null`; it creates no local owner.

The repository boundary is responsible for downstream deletion. `CredentialRepositoryImpl.deleteCredential` uses the encoded ID for aggregate deletion and DAO deletion, and erases returned aggregate metadata in `finally`. Bulk deletion reads caller-owned `PasskeyCredential` records and erases each record in a `finally` after the batch terminates. Database page/WAL and immutable identifier lifetimes remain separate unresolved persistence/runtime concerns.

The two use-case test classes exercise synthetic credential/RP IDs and success/failure or filtered/unfiltered delegation. Their five cases are included in the complete FIDO host suite, which passed 1,286 tests across 169 reports with zero failures, errors or skips. No product credential data is used by these tests. Synthetic immutable test Strings are GC-managed and cannot be deterministically zeroed.
