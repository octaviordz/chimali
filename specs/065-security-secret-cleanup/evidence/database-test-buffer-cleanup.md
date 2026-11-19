# SQLiteMC host test buffer cleanup

**Finding/task:** T005 / T006; SEC065-DB-ownership-test
**Date:** 2026-11-16

`SQLiteMCDriverIntegrationTest` creates synthetic encryption keys and salt buffers for database open/reopen and wrong-key cases. The test now retains explicit references to the copied key arrays, closes successfully opened drivers in `finally`, and zeros copied and source key/salt arrays from `finally` on both success and failure paths. The cleanup assertions verify those test-owned arrays are zero after the test operation.

Verification on the Windows JVM host:

- `:core:database:testDebugUnitTest`: 12 tests across 2 reports, 0 failures, 0 errors, 0 skipped.
- `:core:database:ktlintCheck`: passed.
- Test report hashes: `EncryptedDriverFactoryTest` `24442879231B6089B0BA44EADC0CA8212C96B73CE749250B6972BF77FE0257B1`; `SQLiteMCDriverIntegrationTest` `4BF3941A1DDF877ECC34618AA6089AEC18544C88E2B3403609187C7C3A1B92B0`.

This verifies cleanup of arrays owned by the host test. It does not establish whether SQLiteMC or native/database layers retain key material after driver creation, nor does it provide on-device, database-page, WAL, or heap evidence. Those source ownership mappings and runtime boundaries remain open under T005/T006 and the database-related runtime tasks.
