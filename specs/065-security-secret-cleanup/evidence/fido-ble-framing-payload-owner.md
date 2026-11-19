# SEC065-FIDO-BLE-001 — BLE framing payload ownership

**Date:** 2026-11-16  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`FidoBleFraming.encode*` and `fragmentResponse` borrow source payloads and allocate new frame arrays for their callers. `RequestAssembler` borrows each incoming frame and copies bytes into its own mutable partial buffer. `reset()` zeroes and releases that buffer. Invalid input and sequence errors call `reset()`. `BleSessionOwner` also resets partial assembly before a new initialization request, for empty input, on timeout, after expiry, and on framing errors. On successful completion the assembler clears its internal reference and transfers the owned payload to `FeedResult.Complete`; the caller then wraps/transfers it to the BLE message pipeline and owns terminal cleanup.

Three regressions were added to `FidoBleFramingTest`: explicit reset zeroes the partial payload without mutating the borrowed frame; out-of-order continuation zeroes the partial payload; successful completion transfers payload that remains readable after assembler reset. These tests inspect the private buffer only to retain an owner reference for zeroing assertions.

The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passed 1,282 tests across 169 XML reports, with zero failures, errors or skips; KtLint passed.

This establishes the host framing-owner contract only. Downstream BLE notification/transport queue completion and Android Bluetooth framework copies remain separate owner obligations; the test does not establish provider/native heap erasure or physical BLE behavior.
