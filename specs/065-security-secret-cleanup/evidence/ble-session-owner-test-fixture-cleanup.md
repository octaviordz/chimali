# SEC065-FIDO-BLE-006 — BLE session-owner test fixture cleanup

**Date:** 2026-11-17  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`BleSessionOwnerTest` verifies synthetic host exclusivity, session-generation fencing, CTAP frame reassembly and receive timeout behavior. Its test-owned request frames and `FidoBleMessage` payloads are now tracked and erased in unconditional `@AfterTest` cleanup; the active synthetic host session is disconnected so the session owner can clear incomplete assembly state. A teardown regression retains a request array and confirms its contents are zeroed.

The focused class passes 18 cases. The complete FIDO Android host suite passes 1,297 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes.

The host addresses and payloads are fixed synthetic markers. This evidence does not establish production Bluetooth framework erasure, provider/native behavior, heap absence, or physical BLE behavior.
