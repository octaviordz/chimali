# Vault encrypted output cleanup before item transfer

**Finding:** SEC065-VAULT-011  
**Date:** 2026-11-16  
**Scope:** Ciphertext returned by `EncryptionManager.encrypt` before ownership transfers into a constructed `VaultItem`.

`VaultCryptoServiceImpl.encrypt` now keeps the encrypted array in a local owner until `VaultItem` construction succeeds. If a failure occurs between encryption and item construction, the `finally` block clears the untransferred array. Once the item is staged, its existing cleanup path owns it; successful dispatcher delivery transfers the item to the caller.

A synthetic failure injected immediately after the encryption manager returns the output reproduces the pre-fix leak: the focused test fails because the encrypted byte array remains nonzero. With the cleanup restored, the focused regression passes. The complete `VaultCryptoOwnershipTest` class and Vault KtLint check also passed before the red-before check; the focused green rerun passes one test with zero failures/errors.

- Focused test: `VaultCryptoOwnershipTest.encryptedOutputIsErasedWhenOutputConstructionFails`
- Green report: `feature/vault/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.vault.internal.crypto.VaultCryptoOwnershipTest.xml` SHA-256 `EF41551A1F684847044CBAE4107BE82A27100FAF775FABC43261EF0B60EF66B2`
- The red-before Gradle output reported one failed test at the encrypted-output zeroization assertion; the failed run report was replaced by the subsequent green run and is not retained as a separate artifact.

This verifies an application-owned ciphertext buffer on a synthetic host path. Cipher/provider internal copies, persistent database copies, device behavior and heap roots remain outside this result.
