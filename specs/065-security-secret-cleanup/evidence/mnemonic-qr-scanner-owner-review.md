# SEC065-KEY-QR-001 — Development mnemonic QR scanner ownership

**Date:** 2026-11-16  
**Revision:** Exploratory dirty worktree; no frozen-candidate claim.

## Source path and owners

`MnemonicQrScanner` owns the active CameraX preview, `ImageAnalysis` callback, single-thread executor and ML Kit scanner. It closes each `ImageProxy` after asynchronous scan completion, shuts down the executor and closes the ML Kit scanner when the composable is disposed, and unbinds the camera provider on AndroidView release. If `imageProxy.image` is null, it closes the proxy immediately.

ML Kit returns `Barcode.rawValue` as an immutable `String`. The scanner counts its words and, for a 24-word value, copies the trimmed characters into a new `CharArray`, then transfers that array through `DevelopmentToolsScreen` to `DevToolsIntent.RecoverFromSeed`. `DevToolsViewModel` consumes and erases the transferred array in its import `finally` and completion cleanup, including queued jobs that never start. Invalid QR diagnostics contain only the word count, not phrase content.

Frame processing now uses `withMnemonicFrameRelease`: a synchronous exception while obtaining the image, creating the ML Kit input, starting the scan, or registering listeners closes the frame immediately. The asynchronous task completion callback releases it exactly once. Three host regressions cover synchronous setup failure, asynchronous/double completion, and failure after completion registration; all pass, and FIDO KtLint passes.

## Disposition

The app-owned transfer after `Barcode.rawValue` is mutable and reaches the existing import owner. The library-provided immutable phrase `String`, image/frame buffers, ML Kit internal copies and Android camera/provider copies remain outside app-owned erasure control. Scanner/API runtime lifetime and negative or malformed scan behavior remain unverified.

This source is a development-tool seed input path. The QR scanner does not gain a platform UI text exception from BND-UI-003, which covers Compose rendering and manual recovery input. Its camera permission and optional hardware feature are restricted to debug packaging; release manifest merge verification is documented separately in `release-camera-permission-scope.md`. No claim is made that the immutable provider value or frame is erased.

## Required follow-up

Assess whether the development QR import path is required in the product at all. If retained, investigate an API path that avoids or bounds immutable phrase copies, add scanner-owner tests for success/failure/disposal, and capture provider/runtime behavior. The owned frame release wrapper is implemented and host-tested; provider/API runtime behavior and broader scanner tests remain open under T005/T006/T012/T015/T027/T037.


## Full-suite follow-up

The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passed 1,279 tests across 169 XML reports with zero failures, errors or skips. This includes the three close-once frame lifecycle regressions. It is host evidence only; ML Kit/CameraX runtime and immutable provider copies remain unverified.
