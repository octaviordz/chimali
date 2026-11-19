# SEC065-ERR-025 — Transport preference results retained raw I/O failures

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`Fido2SettingsRepositoryImpl` placed raw `IOException` instances in `ReadError` and `WriteError` values. Although current UI and process-selection callers use only the result type, the throwable stayed reachable to any result consumer and could carry provider or path details.

Read and write marker assertions failed before correction because the result retained the original exception. The repository API now models read/write failures as cause-free singleton result objects; `observeTransportPreference`, `getTransportPreference`, and `saveTransportPreference` no longer carry the source throwable. Existing fixed diagnostic labels and prior-setting preservation remain covered. UI and process consumers still map failures to fixed status values.

Verification: `TransportPreferenceRepositoryTest` passes 11 cases, `TransportSettingsViewModelTest` passes 9, and `ProcessTransportSelectionRecoveryTest` passes 4. The complete FIDO host suite also passes 1,268 tests across 167 reports with zero failures/errors/skips; KtLint and Detekt pass. The red-before report was overwritten by the passing run.

Limits: focused Android host/source evidence only. Other settings storage behavior, configured Android diagnostic destinations, full candidate owner reconciliation and heap/provider state remain open under T005/T006/T029/T033/T034/T035/T015.
