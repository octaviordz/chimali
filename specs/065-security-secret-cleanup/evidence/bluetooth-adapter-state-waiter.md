# SEC065-PLATFORM-002 — Bluetooth adapter wait-state diagnostic scope

**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree; included in the current full FIDO host suite.

`BluetoothAdapterStateWaiter` reads only `BluetoothAdapter.state`, returns the final platform state to its HID wrapper, and retries at most ten times with a fixed 500 ms delay while the adapter is `STATE_TURNING_ON`. Its wait diagnostic includes only the fixed delay and a bounded attempt number. When a state read throws `SecurityException`, it returns `BluetoothAdapter.ERROR` and emits a fixed permission-denied message without forwarding the throwable.

The existing `BluetoothHidCallbackCoordinatorTest.adapter state waiter omits platform exception details` injects a synthetic exception marker, captures the configured Kermit writer, checks the existing fallback state and verifies the marker and throwable are absent. The complete FIDO host suite passed 1,279 tests across 169 reports with zero failures/errors/skips; KtLint passed.

This source review covers the fixed error projection and bounded operational metadata only. It does not establish configured production destination behavior, platform adapter-state privacy, or runtime Bluetooth behavior. Ownership and usage IDs are recorded in the source inventory and `CLS-ERR-001`.
