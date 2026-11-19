# Vault crypto contract and never-started cancellation owner

**Scope:** `VaultCryptoService` ownership contract and `VaultCancellationCleanupTest`; synthetic SecureNote text only.

The public service contract states that encryption consumes the independently owned submission even on failure or cancellation, and successful decryption transfers an independently owned payload to its receiver for terminal cleanup. The implementation's outer `finally` spans dispatcher entry and erases rejected/untransferred owners. The test explicitly cancels before dispatcher execution and confirms the synthetic content array is zeroed, covering the never-started encryption borrower case.

The `VaultCancellationCleanupTest` XML report has one test and zero failures/errors/skips (SHA-256 `5b24e646a12429f1c3f7551a766007ae850533ecc66aa2663cc70f9b91012691`). The eight-case `VaultCryptoOwnershipTest` also passes (SHA-256 `e0364f06b494b63e6c5c1029b90e036308e144c5c98cde7a8eedf11f940768bc`). These reports are from the current worktree's Vault unit suite run on 2026-11-16.

This source/host evidence covers the service boundary and never-started submission only. Successful decryption ownership belongs to callers, and provider/native copies, concurrent callers, UI delivery, heap roots and frozen-candidate behavior remain separate open obligations under T005/T006/T015/T037.
