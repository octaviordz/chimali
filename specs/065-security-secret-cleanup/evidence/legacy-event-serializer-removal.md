# Legacy Event Serializer Removal

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

A current source-reference search found `VaultEventSerializer` was used only by test code; production event/snapshot persistence already routes through `VaultMutableJsonCodec`. The legacy object and duplicate `EventStoreSerializerTest` were removed. The event repository cleanup regression now creates decrypted bytes through the active mutable codec. Frozen event/snapshot compatibility tests were moved to byte-for-byte comparison against the checked-in synthetic v1 fixtures, and their mutable title, fixture, output and decoded owners are erased. The mutable codec suite retains a malformed-event rejection case.

The complete `:core:data:testDebugUnitTest` suite passes 43 tests across 9 reports with zero failures/errors/skips. `:core:data:ktlintCheck` passes. Inventory removes the deleted test file and refreshes current source hashes and owner usages.

**Limits:** JVM host tests use in-memory SQLite and synthetic fixtures. They do not establish production encryption-provider behavior, native driver copies, device/runtime behavior or post-terminal heap roots.
