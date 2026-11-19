# AES-GCM metadata adapter owned-buffer cleanup

**Date:** 2026-11-15  
**Finding:** SEC065-CRYPTO-006  
**Source:** `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataService.kt`

## Finding and correction

The adapter split a versioned encrypted envelope into a copied nonce and ciphertext slice for decryption, but retained those application-owned arrays after both successful `Cipher.doFinal` and authentication failure. Encryption also assembled the returned envelope using a nonce and temporary `Cipher.doFinal` ciphertext array without erasing either intermediate. Those arrays were redundant copies; caller-owned inputs and returned output must remain independent.

The adapter now clears its nonce and temporary encrypted array in an encryption `finally` after assembling the caller-owned envelope. Decryption clears its copied nonce and encrypted-data slice in `finally` on success and every thrown operation, including authentication failure. The API still borrows caller input arrays and returns a separate caller-owned plaintext or envelope. `@AfterEach` clears the synthetic test key and drops the service's key reference.

## Red/green verification

Three deterministic observer regressions were added for encryption, successful decryption and authentication failure. Before the cleanup change, all three failed because their retained references contained nonzero bytes. Afterward:

- All 15 `AesGcmEncryptedMetadataServiceTest` cases pass.
- The full `:core:security:testAndroidHostTest` run passes 72 tests across 13 reports, with zero failures, errors or skips.
- The full `:feature:fido2:testAndroidHostTest` integration run passes 1,247 tests across 166 reports, with zero failures, errors or skips.
- `:core:security:ktlintCheck` and `:feature:fido2:ktlintCheck` pass.
- `tools/security/validate-feature-065-evidence.ps1` validates the evidence index after the record is added.

## Limits

The host observer verifies only arrays owned by this adapter. It cannot inspect copies retained inside `Cipher`, the JCA provider, `SecretKeySpec`, native crypto or the managed heap. The returned plaintext/ciphertext arrays remain owned by their direct callers, whose terminal cleanup and heap roots are separate T005/T006/T015 review obligations. This is not proof of physical erasure or production Android provider behavior.
