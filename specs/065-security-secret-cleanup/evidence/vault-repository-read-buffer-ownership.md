# Vault repository read buffer ownership

Date: 2026-11-15  
Scope: `VaultRepositoryImpl.getItems` and `VaultItem` mutable output arrays.

## Finding and remediation

The repository previously decoded row titles with `decodeToString()`, assigned SQLDelight row payload/CRDT arrays directly into `VaultItem`, and returned `entries.map` output directly from `withContext`. That left an immutable plaintext title String, aliased query-row buffers, no cleanup for a partially mapped list, and no cleanup if cancellation prevented the mapped list from reaching the caller.

The current implementation decodes UTF-8 into a mutable `CharBuffer`, copies its characters into a caller-owned `CharArray`, and zeroes the decoder buffer. On save it encodes the mutable title through a mutable `CharBuffer`/`ByteBuffer` path, copies the bytes for database binding, then zeros the encoder buffer and bind array. Payload and CRDT arrays are copied into each model. Row-owned title/payload/CRDT arrays are cleared after mapping. Partial models clear on errors, and a complete list remains staged until dispatcher return is delivered; cancellation clears every staged model. `VaultItem.clearMemory()` remains the caller's terminal cleanup operation.

`VaultRepositoryImplIntegrationTest.clearingReturnedItemOwnersDoesNotMutatePersistedVaultData` verifies a caller can erase a returned item and a later repository read still returns the persisted title, payload and CRDT values. The create/update/read integration path also round-trips a multibyte title (`Updated 🔐`). The full `:feature:vault:testDebugUnitTest` suite passes 91 tests across 20 reports with zero failures/errors/skips; KtLint passes.

## Limits

This verifies application-owned arrays with the in-memory JDBC SQLite integration setup. SQLDelight/JDBC/Android/native driver copies, provider internals, failed-row buffer observation, heap roots, and Android runtime behavior remain unverified under T006/T015.
