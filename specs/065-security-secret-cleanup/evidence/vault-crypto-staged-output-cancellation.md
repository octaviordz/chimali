# Vault crypto staged output cancellation

Date: 2026-11-15  
Scope: `VaultCryptoServiceImpl.encrypt` dispatcher return delivery.

## Finding and remediation

Encryption previously returned a newly built `VaultItem` from `withContext` without retaining an erasure handle until the caller actually received it. If cancellation prevented delivery across that dispatcher boundary, the output title, ciphertext payload, and CRDT byte array became unreachable without explicit cleanup. Decryption already tracked a staged result and erased it when return delivery failed.

Encryption now stages the produced `VaultItem` before returning from the dispatcher block, relinquishes the staged reference only after `withContext` returns successfully, and clears the item in the outer `finally` if transfer did not complete. The original submitted payload continues to be consumed in the same `finally`.

`VaultCryptoOwnershipTest.cancellationDuringEncryptionReturnDeliveryErasesStagedVaultItemForEveryType` exercises password, card, and note payloads. It pauses the dispatcher return, cancels the caller, and asserts the undelivered item's title, ciphertext, and CRDT arrays are zeroed. The eight-case `VaultCryptoOwnershipTest` class passes; `:feature:vault:ktlintCheck` passes.

## Limits

This covers application-owned arrays and coroutine transfer semantics. Encryption-provider copies, database/native copies, full heap-root evidence, and frozen candidate review remain open under T005/T006/T015/T037.
