# Latency profiler user-window diagnostic test owner

**Date:** 2026-11-17  
**Finding:** SEC065-LOG-035  
**Requirements:** FR-008/009; NFR-SEC-041; TR-AUTH-031; T005/T006/T029/T035.

`LatencyProfilerUserWindowTest` captures operation diagnostic output and checks that a synthetic identifier marker is excluded. It also checks user-interaction window timing, processing failure and cancellation. Test teardown now restores prior Kermit writers/severity and clears captured message records in `finally`, including when an assertion fails.

## Verification

The focused class passes 4/4 cases; full FIDO host suite passes 1,290 tests across 171 reports with zero failures/errors/skips; KtLint passes. Current source SHA-256: `da0f237e7bdcae0107f40b9554ab7dbc629e7684a236b8e893f92b1a688d1a72`; XML SHA-256: `363036a8cfeae79bfcfb682eccb1388e1d6d1169e6ee4741d9965c9d817b7df9`; report index SHA-256: `016d3a5d275935d9173f184fbf4e875920155081ecf3dee1b9aa0928f18a8905`.

This verifies only the host test's captured logger and test-local owner lifetime. Configured Android destinations, file rotation/fallback behavior and full diagnostic path remain separate.
