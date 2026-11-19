# Passkey event replay batch transfer

Date: 2026-11-18
Finding: SEC065-CORE-047  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyEventStoreRepositoryImpl.kt`

## Source correction and regression

Both event replay methods stage decoded events in a local mutable list. They now erase an event when list admission fails, create the immutable result list before marking ownership transferred, and erase every staged `PasskeyEvent.Registered` if a later row fails, cancellation occurs, admission fails, or result-list allocation fails. Per-row plaintext and the event-store key remain under their enclosing `finally` cleanup.

Two deterministic host regressions inject an exception at the batch-transfer boundary after a synthetic or database-decoded event is staged: one covers `getEvents`, the other `getEventsFrom` (the snapshot-tail replay path). Both verify failure is returned, the SQLDelight query-row payload passed to decrypt is zeroed, and the event credential-ID bytes and RP/user character owners are zeroed. A fresh query confirms persisted ciphertext remains intact; test teardown clears fixture row arrays, event owners, and synthetic key material on every exit.

## Verification

- Focused `PasskeyEventStorageOwnershipTest`: 21 tests passed, zero failures/errors/skips.
- Full `:feature:fido2:testAndroidHostTest`: 1,367 tests across 175 XML reports, zero failures/errors/skips.
- `:feature:fido2:ktlintCheck`: passed.

## Limits

These are injected-failure/source and host-test results, not real allocator-exhaustion tests. The aggregate fold source and host tests clear transferred event owners, and application-visible SQLDelight row arrays are erased; driver-internal/native copies, other external repository callers, Android runtime behavior, and heap roots remain unresolved under the broader audit.
