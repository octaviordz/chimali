# Passkey aggregate error-cause discard

**Finding:** SEC065-ERR-019  
**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not a frozen release candidate.

`PasskeyAggregateServiceImpl` forwarded repository `Result.failure` exceptions from event replay, retained the last append failure across retries, returned raw snapshot-save failures, and returned repository-thrown exceptions from its outer command boundary. These exception objects can retain provider/database details and arbitrary throwable graphs.

A synthetic append regression failed before the correction: after three failed appends, the service returned the original marker-bearing exception and cause. A replay regression now asserts a fixed retrieval error with no cause; the snapshot-save regressions assert a fixed snapshot error; and the thrown append regression asserts a fixed command error. Cancellation exceptions continue to propagate, including cancellation represented as a failed replay result. The implementation clears the existing mutable command/event/state owners on the same paths.

The service now returns fixed cause-free failures for repository replay, snapshot-save, retry-exhaustion and unexpected command-execution errors. Cancellation carried as a failed snapshot lookup result also propagates without falling back to event replay. Final `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` passes **1,265 tests across 167 XML reports**, with zero failures, errors or skips; KtLint passes.

Source SHA-256 values: `PasskeyAggregateServiceImpl.kt` `eafd08b8a427eda0e8f336d969590570da9bb39f2d461d9d1ea9d511c05f3627`; `PasskeyAggregateServiceImplTest.kt` `fffba35aceb802810a6beb54b21d2813afa42078a4bf1ff6c82b99c33a7a5227`; `PasskeyAggregateServiceLifecycleTest.kt` `d57c86c38e249b2406205e0858bb9058e99795a377131e1c02569fd5f4582676`.

Full-suite report SHA-256 values: `PasskeyAggregateServiceImplTest.xml` `a4bbad580da6f86e04590e02e501796a931f32f1834a438eda09b4f3bbbab161`; `PasskeyAggregateServiceLifecycleTest.xml` `0c1954cd8bc5109e995c9968e0a4a966241e304f3d268451ff97e4fd3c873fc0`.

This is source and host-test evidence. It does not establish behavior in native/JDBC providers, configured diagnostic destinations, provider-internal retention, heap roots or a frozen candidate.
