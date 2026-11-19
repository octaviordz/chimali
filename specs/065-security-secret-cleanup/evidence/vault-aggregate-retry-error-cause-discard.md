# Vault aggregate retry error cause-discard evidence

**Finding:** SEC065-ERR-016  
**Date:** 2026-11-16  
**Scope:** Vault aggregate append retry exhaustion on the Windows JVM host.

The aggregate service previously returned the last append failure after exhausting its retries, retaining the raw database exception, its message and its object graph. A test-first synthetic-marker regression failed before the correction. Retry exhaustion now returns the fixed message `Aggregate command execution failed after retries` without attaching the underlying exception. The generated command and event cleanup assertions remain in place.

`VaultAggregateServiceImplTest`: 14 tests passed, 0 failures, errors, or skips. `:core:data:ktlintCheck` passed.

This is focused source/host evidence. It does not cover earlier `getState` failures, every exception translation in the Vault repositories, configured diagnostic destinations, provider/native copies or heap roots. No frozen candidate or hosted integration CI result is claimed.
