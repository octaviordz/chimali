# Aggregate service ownership contract

**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`  
**Status:** Source contract synchronized; runtime evidence is linked separately.

`AggregateService` is implemented by the Vault and Passkey aggregate services. Both reconstruct mutable state from snapshot/event owners, erase partial or superseded owners on non-transfer exits, and return successful state for the caller to erase. Both implementations may also erase mutable sensitive fields on a command before the suspend call returns or throws.

The common interface now documents that successful state results transfer to the caller, failed or cancelled operations transfer no partial state, and implementations can consume mutable command content. Callers must relinquish that content during execution and treat it as unavailable afterward. Source review confirmed the rule against `VaultAggregateServiceImpl` and `PasskeyAggregateServiceImpl`; current call sites construct commands in the storage/repository layer and retain the returned state only across their owning operation.

Existing owner regressions are indexed separately: `VaultAggregateServiceImplTest` and `VaultAggregateSnapshotReplayOwnershipTest` cover Vault command/state cleanup, while `PasskeyAggregateServiceImplTest` and `PasskeyAggregateServiceLifecycleTest` cover Passkey registration/state lifecycle. This KDoc synchronization did not rerun those tests.

This does not complete the full T005/T006 inventory or outcome matrix. Concrete caller cleanup across every terminal path, repository/provider copies, generated persistence, runtime behavior, and heap roots remain open.
