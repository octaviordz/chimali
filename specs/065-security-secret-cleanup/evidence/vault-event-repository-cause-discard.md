# Vault event repository cause-discard evidence

**Finding:** SEC065-ERR-017  
**Date:** 2026-11-16  
**Scope:** Vault event repository append and read storage, serialization and provider failures.

Event repository failures previously returned caught SQL and serialization exceptions directly in `Result`, retaining raw messages and throwable graphs. The added test injects a synthetic parser marker during row decryption and verifies the returned error keeps only the fixed serialization category/message with no cause. The test-first assertion failed before the production fix. SQL, serialization and unexpected provider exceptions now map to fixed cause-free errors; coroutine cancellation remains rethrown.

The eight-case focused `EventStoreRepositoryImplTest` class passes after correction. Full `core:data` verification and KtLint are recorded in the evidence index after refresh.

This is source/host evidence. It does not cover snapshot persistence, provider/native internals beyond caught Exception cases, configured diagnostic destinations, native/driver copies or heap roots. No frozen candidate or hosted integration result is claimed.

Full verification refresh: `:core:data:testDebugUnitTest :core:data:ktlintCheck` passes 48 tests across nine XML reports with zero failures, errors or skips; KtLint passes. The aggregate retry regression passes 14 cases and the event repository class passes eight. Current report hashes are recorded in the evidence index.
Full module suite refresh on 2026-11-16: 48 tests across nine XML reports pass with zero failures/errors/skips; KtLint passes. The current event repository XML hash is recorded in the evidence index.
