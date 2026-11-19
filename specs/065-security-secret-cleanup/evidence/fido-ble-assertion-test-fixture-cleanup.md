# SEC065-FIDO-BLE-002 — BLE assertion test fixture cleanup

**Date:** 2026-11-17  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`FidoBleAssertionTest` uses synthetic relying-party and user identity, credential IDs, client-data hashes, signatures, authenticator response data, and BLE notification frames. The test class now tracks its mutable fixture arrays and clears them in unconditional `@AfterTest` teardown. Response/fragment fixture builders clear intermediate chunks after assembling their returned buffers; the fake response sender clears its retained notification copies; and teardown resets the `BleSessionOwner` and releases the synthetic device reference. The new teardown regression confirms both a fixture buffer and a sender-retained notification copy are zeroed and the sender no longer retains entries.

Focused `FidoBleAssertionTest` passes 10 cases, including the new teardown regression. The complete FIDO Android host suite passes 1,293 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes.

This is host-test fixture evidence for synthetic protocol data under CLS-FIDO-001/002. It does not establish production BLE/framework buffer erasure, provider/native behavior, heap absence, or physical BLE behavior.
