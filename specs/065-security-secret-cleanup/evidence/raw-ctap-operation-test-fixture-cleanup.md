# SEC065-FIDO-BLE-007 — Raw CTAP operation test fixture cleanup

**Date:** 2026-11-17  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`RawCtapOperationTest` covers command routing, seed-session wrapper selection, GetInfo errors and Classic HID framing. Its synthetic CTAP request/response buffers, HID channel ID, encoded reports and parser-returned arrays are now tracked and zeroed in unconditional `@AfterTest` cleanup. Concatenation clears intermediate CBOR chunks, and teardown clears the MockK answers and recorded invocations so stubs do not retain request or response references after the test.

The focused class passes 9 cases, including an explicit tracked-buffer erasure regression. The complete FIDO Android host suite passes 1,298 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes.

The channel ID and CTAP bytes are synthetic fixtures. This does not establish production transport/framework erasure, provider/native behavior, heap absence, or physical BLE behavior.
