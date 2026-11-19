# Vault snapshot repository cause-discard evidence

**Finding:** SEC065-ERR-018  
**Date:** 2026-11-16  
**Scope:** Vault snapshot save/load failures on the Windows JVM host.

Snapshot repository save/load paths previously returned SQL causes or allowed serialization and provider exceptions to escape while retaining raw details. Added synthetic-marker regressions for encryption failure during save and decode failure during load; both failed before the correction. Snapshot operations now preserve coroutine cancellation, return fixed cause-free errors for SQL, serialization and other `Exception` failures, and erase acquired keys and plaintext buffers in `finally`.

The six-case `SnapshotRepositoryImplTest` passes, including both new cases. Updated buffer-owner tests verify snapshot save/load failures are returned as sanitized results and confirm acquired buffers are erased. Full `core:data:testDebugUnitTest` passes 48 tests across nine reports with zero failures/errors/skips; `:core:data:ktlintCheck` passes.

This remains source/host evidence. It does not establish JDBC/native buffer behavior, configured diagnostic destination privacy, a frozen candidate or heap-root absence.
