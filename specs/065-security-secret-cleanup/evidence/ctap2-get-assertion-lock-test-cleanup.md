# CTAP2 GetAssertion lock test fixture cleanup

**Date:** 2026-11-17  
**Requirements:** FR-002/003/004/010; T005/T006/T012.

`Ctap2GetAssertionLockTest` uses a synthetic CTAPHID channel ID, fixed request bytes, and a response byte array for the already-locked `CHANNEL_BUSY` path. The test now tracks all three buffer owners and any returned response arrays and erases them in `@AfterTest`. It also asserts that the handler leaves its borrowed channel-ID and request arrays unchanged when the ceremony lock is unavailable. A separate teardown regression verifies tracked buffers become zeroed.

The focused class passes 2 cases; the full FIDO host suite passes 1,292 tests across 171 XML reports with zero failures/errors/skips, and FIDO KtLint passes. This maps the test-only channel-ID owner under `CLS-FIDO-005`; it does not establish transport/provider or heap behavior.

Source SHA-256: `ac9b565c6a6a5cd6797685a8b60effebc5681288f703d1dbf5834f0a329bb6b9`  
Focused XML SHA-256: `e5aa06f37cb22a64d3df6c26bb181aae9811c123b724f80fb999940c96898daa`.
