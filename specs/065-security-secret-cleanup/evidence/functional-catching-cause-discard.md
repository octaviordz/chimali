# Generic failure cause discard

**Date:** 2026-11-15
**Scope:** `functionalCatching` common implementation and two synthetic common host regression cases.

The first test-first run failed because `functionalCatching` retained the injected throwable as `DomainError.UnknownError.cause`. The helper now discards non-cancellation throwables and returns only its caller-selected fixed error message. The cancellation case verifies the same `CancellationException` instance is rethrown.

Focused verification: `:core:common:testAndroidHostTest --tests '*FunctionalCatchingPrivacyTest'` passed 2 focused cases with no failures, errors or skips; the full `core:common` host suite passes 45 cases across 9 reports, with zero failures/errors/skips, and KtLint passes. This does not test configured logging destinations or change the caller-specific `runCatchingOutcome` cause policy.

JUnit XML SHA-256: `3e9d8e590453199341e7c414bfcb46c40097a3c509e10c0a4858b0ab9e5b2ea4` (`core/common/build/test-results/testAndroidHostTest/TEST-com.chimali.core.common.result.FunctionalCatchingPrivacyTest.xml`).
