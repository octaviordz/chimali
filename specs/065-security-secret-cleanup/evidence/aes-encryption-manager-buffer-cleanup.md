# AES Encryption Manager Buffer Cleanup

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

Three retained-array regressions failed before cleanup. They observed that encryption retained its IV and temporary `Cipher.doFinal` bytes, while decryption retained its IV and encrypted-data slice after successful and wrong-key authentication paths. A bad-key-length case also checks IV cleanup during initialization failure.

`AesEncryptionManager` now clears the encryption IV and temporary encrypted bytes in `finally`, and clears copied decryption IV/data slices in `finally`. The resulting envelope/plaintext remains an independent caller-owned output. The common `EncryptionManager` API now documents borrowed inputs, caller-owned outputs and caller erasure. The test observer is internal and unset in production.

All 4 `AesEncryptionManagerTest` cases pass; the full `:core:security:testAndroidHostTest` suite passes 73 tests across 13 reports with zero failures/errors/skips, and `:core:security:ktlintCheck` passes.

**Limits:** JCA `Cipher`, `SecretKeySpec` and `GCMParameterSpec` may retain internal copies outside application array references. The tests do not inspect provider memory or post-terminal heap roots. All downstream callers still require source-owner reconciliation.
