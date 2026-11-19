# SEC065-VAULT-006 — Vault editor copies cleared at terminal events

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

Source tracing found that `VaultNavGraph` created an independent `copyForEditing()` payload and retained it in Compose state. The save-success effect popped the editor and dropped the state references without calling `clearMemory()`, leaving the copied password/card/note and nested custom-field arrays populated until collection. Cancel already cleared explicitly; graph disposal also cleared, but ordinary editor-route disposal did not.

Added `clearEditingPayloadCopies`, which idempotently clears all supplied payload owners and nested custom fields. Save success invokes it before releasing edit state; each editor route invokes it when disposed, covering direct back/leave paths. Failed saves keep the active edit copy for retry. A synthetic unit regression retains every mutable field array across password, card, note and nested custom fields, invokes cleanup twice, and verifies all arrays are zero.

The test was introduced before the helper and initially failed to compile because the cleanup owner did not exist. The first complete navigation run had one system-back timeout; the same three system-back cases passed in isolation. After removing redundant Compose state mutation from route-disposal callbacks, the complete API 35 navigation rerun passed all 15 cases. The complete Vault unit suite passes 94 tests across 22 reports with zero failures/errors/skips; KtLint and Detekt pass. The one-case owner regression report and 15-case navigation report are linked below.

Limits: the API 35 emulator verifies app-owned arrays and navigation outcomes, not IME/Compose renderer snapshots, provider/heap copies, process-death heap recovery or frozen-candidate acceptance. Broader T005/T006/T015/T037 gates remain open.
