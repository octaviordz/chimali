# Vault aggregate retrieval and thrown-error cause discard

**Finding:** SEC065-ERR-020  
**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not a frozen release candidate.

`VaultAggregateServiceImpl.getState` returned raw event-store failures in both full-history and snapshot-suffix replay. Repository calls that threw could also escape raw from state retrieval and command execution, including append and snapshot-save operations. These throwable objects can retain provider/database messages and cause graphs.

Synthetic read and snapshot-replay marker regressions failed before correction. Two additional marker/cause regressions for thrown append and snapshot-save calls also failed before correction. The aggregate now maps event-retrieval failures to a fixed cause-free result and maps unexpected command-execution exceptions to a fixed cause-free result. Existing snapshot fallback for non-cancellation snapshot lookup failures remains intact; cancellation is rethrown, including cancellation encoded as a failed result. State, event, snapshot, command and generated event cleanup remains paired with the same failure paths.

Full `:core:data:testDebugUnitTest :core:data:ktlintCheck` passes **50 tests across 9 XML reports**, with zero failures, errors or skips; KtLint passes.

Source SHA-256 values: `VaultAggregateServiceImpl.kt` `18d0a23726eb0792b1a951dc473db9411a1d16b55063605ecb359b28712b60ed`; `VaultAggregateServiceImplTest.kt` `2fc82001e1432eb63be7d1b7536b3f08fc4aa2c54ee67e17a7b8010c94fe7358`; `VaultAggregateSnapshotReplayOwnershipTest.kt` `8ef99f781bbc303bd04dc5830c57f1727f7f0e12188b63b6c41db04328a60fab`.

Full-suite report SHA-256 values: `VaultAggregateServiceImplTest.xml` `7aac61a0f37ad0903b637730eceb4f2634e665520da23ddfa3be51b440081a31`; `VaultAggregateSnapshotReplayOwnershipTest.xml` `4b5b33a76bc23104eb81a81e732fdadb9b05e9ec5055c6302914140cd82fcc61`.

This is source and host-test evidence. It does not establish provider/native behavior, configured diagnostic destinations, provider-internal retention, heap roots or frozen-candidate acceptance.
