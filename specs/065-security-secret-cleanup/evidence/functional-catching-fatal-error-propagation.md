# SEC065-ERR-026 — Shared error wrappers converted fatal Errors into ordinary outcomes

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`runCatchingOutcome` and `functionalCatching` discarded throwable details, but both caught every non-cancellation `Throwable`. This also converted fatal JVM `Error` instances such as `AssertionError`, `LinkageError` and `OutOfMemoryError` into ordinary domain failures, masking process-level faults.

Two new regressions were run before the source correction. Both failed: `functionalCatching` returned `Outcome.Error` for a synthetic `AssertionError`, and `runCatchingOutcome` invoked its mapper instead of propagating that same error. The helpers now catch ordinary `Exception` values after explicitly rethrowing coroutine cancellation; fatal `Error` values propagate unchanged. The mapper callback retains its existing `Throwable` signature for source compatibility, but receives only ordinary exceptions from this helper. Existing fixed-message cause-discard behavior remains.

Verification: `FunctionalCatchingPrivacyTest` passes all four cases; the complete Core Common Android host suite passes 47 tests across 9 reports; Core Common KtLint and Detekt pass. Downstream FIDO host tests pass 1,268 cases across 167 reports with zero failures/errors/skips, including seven `PublicKeyDecoderTest` cases; FIDO KtLint and Detekt pass.

Limits: host/source evidence only. This does not establish native/provider behavior, configured diagnostic destinations, heap lifetime or frozen-candidate acceptance.
