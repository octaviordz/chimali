# SEC065-FIDO-BLE-004 — BLE lifecycle test fixture cleanup

**Date:** 2026-11-17  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`FidoBleLifecycleTest` exercises advertising, request timeout, peer replacement, stale callbacks and cancellation using fixed synthetic Bluetooth host addresses and synthetic BLE/CTAP payloads. Its mutable packet and mock CTAP response arrays are now tracked and zeroed in unconditional `@AfterTest` teardown. The fake response sender zeroes retained notification copies before releasing its list; teardown also disconnects any active synthetic host. A regression retains a packet and notification copy across cleanup and verifies both arrays are zeroed and the sender releases its copy.

The focused class passes 17 cases. The complete FIDO Android host suite passes 1,295 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes.

This is synthetic host-test evidence only. It does not establish production Bluetooth framework erasure, provider/native behavior, heap absence, or physical BLE behavior.
