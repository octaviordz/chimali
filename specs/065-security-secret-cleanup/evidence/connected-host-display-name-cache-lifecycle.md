# Connected host display-name cache lifecycle

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`  
**Scope:** `Fido2HomeViewModel.connectedDeviceDisplayName`, `Fido2HomeScreen`, and the host regression in `Fido2HomeViewModelTest`.

## Finding and correction

The dashboard derives a host display name from a paired-device alias or `BluetoothDevice.name` and exposes it through a `StateFlow<String?>` for the connected-state Compose title. Its original `SharingStarted.WhileSubscribed(5_000)` configuration kept the last replayed string indefinitely after collection stopped. A test using the synthetic alias `Synthetic alias marker` confirmed the defect: after cancelling the collector and advancing the five-second stop timeout, the state still contained the alias instead of returning to `null`.

The flow now sets `replayExpirationMillis = 0` while preserving the existing five-second stop timeout. The regression confirms the alias is available while collected and the StateFlow resets to `null` after the final collector stops and the timeout elapses. The connected state still provides the value while an active dashboard collector needs to render it; the UI text boundary and immutable platform/framework copies are separate.

## Verification

- Focused `Fido2HomeViewModelTest` passes, including the new cache-expiration regression.
- Full `:feature:fido2:testAndroidHostTest` passes **1,275 tests across 168 XML reports**, with zero failures, errors, or skips.
- `:feature:fido2:ktlintCheck` and `:feature:fido2:detekt` pass.
- Full suite report for `Fido2HomeViewModelTest.xml`: SHA-256 `a2ee4a104841959ba7ba929e43b6d2b480c7ff583c7ae6e4497833d6feade09f`.

## Limitations

This is host evidence for the StateFlow sharing contract. It does not verify Compose text/semantics release, activity lifecycle delivery, or heap roots. `Fido2HomeScreen` currently uses `collectAsState`; if that collector remains active while the app is backgrounded, the value remains available to the active collector. No app-background clearance claim is made. The strings are immutable and cannot be explicitly zeroed; the correction bounds the app-owned replay cache after actual collector termination.
