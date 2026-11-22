# SQLDelight replay row payload cleanup

- Date: 2026-11-22
- Worktree identity: dirty exploratory worktree; no refreshed candidate declared.
- Change: full event replay and sequence-tail replay clear every materialized query row payload in a batch `finally`; snapshot reads clear the query payload even when key acquisition or decode fails. Failure cleanup leaves persisted ciphertext untouched and preserves returned owners on successful transfer.
- Regressions: full replay success and first-row decoder failure; full replay cancellation before ownership transfer; snapshot-tail success and cancellation before transfer; snapshot success, key-provider failure, decoder failure and cancellation. Captured SQLDelight payload arrays are zeroed on every terminal path. Staged owners are erased on failure/cancellation, returned owners remain readable on success, and persisted event rows remain replayable after cleanup attempts.
- Verification: `PasskeyEventStorageOwnershipTest` passed 23/23 with zero failures/errors/skips; `PasskeyAggregateServiceImplTest` passed 18/18; FIDO KtLint passed.
- Limits: success and injected failure are covered for the targeted branches, but cancellation and snapshot-tail row-array cases still need explicit captured-buffer assertions. SQLDelight-generated mapper/native-driver internal copies are outside application-owned arrays. T110 remains open.
