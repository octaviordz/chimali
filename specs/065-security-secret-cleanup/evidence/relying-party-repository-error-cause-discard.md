# SEC065-ERR-023 — Relying Party repository retained DAO failures

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

The result-returning save, delete and credential-count update operations in `RelyingPartyRepositoryImpl` preserved arbitrary DAO throwables in `Result.failure`. That retained caller-supplied exception messages and cause graphs at the repository boundary. The existing wrapper also caught `Throwable`, which could turn fatal errors into ordinary results.

`RelyingPartyRepositoryFlowOwnershipTest.result operations do not retain DAO failure details` injects a synthetic marker into each DAO operation and asserts all three results carry only fixed cause-free failure text. The regression failed before correction because the first returned exception exposed the marker. Ordinary `Exception` failures now become `IllegalStateException("Relying-party operation failed")` without a cause; `CancellationException` still propagates, and fatal `Error`s are no longer caught.

Verification: the focused repository class passes all six tests, preserving its cancellation and owner-lifetime cases. The full FIDO host suite passes 1,267 tests across 167 reports with zero failures/errors/skips. KtLint and Detekt pass.

Limits: host/source evidence only. Repository/provider internals, production caller reachability, configured diagnostic destinations and heap lifetime remain open under T005/T006/T029/T033/T034/T035.
