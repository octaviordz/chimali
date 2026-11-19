# SEC065-FIDO-BLE-003 — BLE cancellation test fixture cleanup

**Date:** 2026-11-17  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`FidoBleCancellationTest` exercises cancellation, notification queue limits and malformed synthetic BLE/CTAP packets. Its test owner now tracks the mutable packet and queue payload arrays, zeroes them during unconditional `@AfterTest` teardown, clears the fake sender's retained notification copies, and disconnects its synthetic host session. A teardown regression retains references to a tracked packet and a sender copy, invokes cleanup, then confirms both are zeroed and the fake sender has released its notification list. The fixed Bluetooth host address is a synthetic marker, not a real paired-device identity.

The focused class passes 7 cases. The complete FIDO Android host suite passes 1,294 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes.

This verifies synthetic host-test owner cleanup only. It does not establish production transport/framework erasure, provider/native behavior, heap absence, or physical BLE behavior.
