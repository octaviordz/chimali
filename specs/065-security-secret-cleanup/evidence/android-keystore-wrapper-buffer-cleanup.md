# Android Keystore Wrapper Buffer Cleanup

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`EncryptionWrapper.encrypt` previously erased its consumed plaintext only after successful cipher initialization and encryption. Cipher creation and Android Keystore key lookup occur before that cleanup; a setup failure could leave plaintext populated. The wrapper now clears plaintext in an outer `finally` covering those setup steps and the operation. The encryption primitive clears its owned IV and intermediate `Cipher.doFinal` array in `finally`. Decryption clears its copied IV and encrypted-data slices after success and authentication failure while leaving the borrowed caller envelope unchanged and returning independent plaintext.

Four Android-host regressions cover successful encryption/decryption, invalid-key initialization, cipher-construction and key-lookup failures, and authentication failure. The full `:core:common:testAndroidHostTest :core:common:ktlintCheck` run passes 43 tests across 8 reports with zero failures/errors/skips. `:core:common:assembleAndroidTest` also succeeds and compiles/packages the configured Android device-test source set. The real Android Keystore round-trip test is present under `androidDeviceTest`; it was compiled but not executed on a device.

The test fixtures in the configured Android device-test source now clear plaintext, expected plaintext, envelope and decrypted arrays in `finally`. A duplicate test under the unconfigured `src/androidTest` path was identified and given a file-specific assessed-no-relevant-use rationale; the actual configured integration test remains under `src/androidDeviceTest`.

The source inventory maps the production adapter, host regression and configured device test separately. `CLS-KEY-001` now lists the adapter; `SEC065-CRYPTO-008` records the implementation finding.

**Limits:** The Android Keystore/JCA provider may retain copies of key, IV, plaintext or ciphertext internally. Host tests and APK assembly do not establish provider/native memory erasure, device behavior, or heap-root removal. Those remain open under T005/T006/T015.
