# SEC065-ERR-024 — Passkey storage adapter retained raw operation failures

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`captureStorageResult`, shared by Passkey event and snapshot repositories, returned known persistence, encryption, serialization, and conversion exceptions directly in `Result.failure`. This retained arbitrary exception messages and causes at the storage boundary even though aggregate callers later sanitize failures.

The existing `PasskeyEventStorageOwnershipTest.storage result sanitizes malformed UTF-8 failures and preserves cancellation` regression now injects a synthetic exception marker and requires fixed failure text with no cause or marker. It failed before correction because the raw exception was returned. The adapter now maps ordinary `Exception`s to `IllegalStateException("Passkey storage operation failed")`, rethrows cancellation, and allows fatal `Error`s to propagate.

Verification: focused Passkey event-storage and replay suites pass 18 and 4 tests respectively. The complete FIDO host suite passes 1,267 tests across 167 reports, with zero failures/errors/skips. KtLint and Detekt pass.

Limits: host/source evidence only. SQLite/SQLDelight provider internals, direct caller reachability, configured diagnostic destinations and heap lifetime remain open under T005/T006/T029/T033/T034/T035.
