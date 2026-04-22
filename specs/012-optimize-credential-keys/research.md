# Research: Credential Key Optimization & Lazy Loading

## 1. Decoding Public Keys without HDK Derivation

**Decision**: Extract the public key decoding logic into a stateless `PublicKeyDecoder` utility that parses the stored `publicKey` string from the database.
**Rationale**: `Fido2CryptoService.getPublicKey` requires the Master Seed and recalculates the private key scalar to derive the public key. This violates performance constraints for list views. The DB already holds the public key representation. A stateless decoder can parse X.509 or uncompressed EC points instantly using BouncyCastle `KeyFactory`.
**Alternatives considered**: 
- *Caching `java.security.PublicKey` in memory*: Rejected because it doesn't solve the initial load time and wastes memory.
- *Changing the DB schema to store X.509 directly*: Unnecessary since the existing base64/hex string is already sufficient for reconstruction.

## 2. Pagination Strategy for SQLDelight + Compose

**Decision**: Use offset-based pagination implemented via Kotlin Coroutines `Flow` and a custom UI state, fetching fixed-size pages (e.g., 20 items at a time).
**Rationale**: The requirement is "load only what is/would be visible + 1 next page". A simple `LIMIT :limit OFFSET :offset` SQLDelight query mapped to a domain usecase is lightweight, easily tested via TDD, and avoids the heavy boilerplate of the `Paging3` library. Jetpack Compose's `LazyColumn` makes it trivial to trigger the next page load using a `LaunchedEffect` when the last visible item is reached.
**Alternatives considered**:
- *AndroidX Paging 3*: Rejected as it introduces complex multi-layer dependencies (PagingSource, Flow<PagingData>) that are overkill for a local SQLite database that doesn't rely on complex network sync strategies. 
- *Cursor-based pagination*: Faster for massive datasets, but offset-based is completely adequate for 10k items in SQLite and much simpler to implement with the existing sorting (e.g., sort by `lastUsedAt`).

## 3. Background Batch Repair for Corrupted Keys

**Decision**: Integrate `kmpworkmanager` to schedule a unique background job that performs the heavy HDK derivation to repair corrupted public key strings offline.
**Rationale**: Decoding may fail if the database contains malformed legacy entries. Re-deriving keys via HDK on the UI thread would block the main thread and cause freezing. Pushing this to `kmpworkmanager` ensures that the heavy operation is deferred and processed in batches without impacting the current user session, while natively supporting KMP.
**Alternatives considered**: 
- *Synchronous fallback during map*: Blocks the flow and causes the very UI freezing we are trying to eliminate.
- *Coroutines `launch` in Repository*: Fragile; if the app is killed before the derivation completes, the keys remain broken. `kmpworkmanager` ensures guaranteed execution.
