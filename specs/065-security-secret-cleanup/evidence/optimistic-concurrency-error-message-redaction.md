# Optimistic concurrency error-message redaction

**Finding:** SEC065-ERR-027  
**Date:** 2026-11-16

`OptimisticConcurrencyException` formatted the aggregate identifier and sequence into its default throwable message. Since diagnostics and crash reporters can format throwable messages, this could expose an event-sourced record identifier. The structured `aggregateId` and `sequenceNumber` properties remain available to conflict/retry logic, while the throwable message is now fixed. The arbitrary caller-supplied message parameter was removed so callers cannot reintroduce identifying values through this constructor.

## Verification

- Red-before: `OptimisticConcurrencyExceptionPrivacyTest` failed because the message contained the synthetic aggregate marker and sequence.
- Green: `:core:domain:testAndroidHostTest :core:domain:ktlintCheck --no-daemon` passed; the core domain Android host suite passed 94 tests across 17 reports, with zero failures/errors/skips; KtLint passed.
- Focused report SHA-256: `2379BB58AECA295CAD37FF154E8246323199D891BF45AD54FE6FB729E48D0FBE`.
- Production source SHA-256: `9CA5A3631E59A8C4A5986200F76BB2117089D38C0E0470E7F98C1772BB852FC0`.
- Test source SHA-256: `5252A071632D7CD987F1D45912283FB3A38C3E81D58E3BC086C43CD6BC035195`.

## Limits

The repository currently has no concrete constructor call site for this exception; the type and Feature 034 event-store contract define it as a concurrency failure passed to retry logic. The change prevents the exception's default formatting from revealing the identifier if a repository implementation throws it. The structured aggregate ID remains in memory and may be sensitive; callers must not log the property. This host check does not establish configured diagnostic sink behavior, provider/heap lifetime, or release-candidate acceptance.
