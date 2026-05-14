# Quickstart: HDK Migration Implementation

This guide provides a brief overview of the HDK implementation for the PQ branch.

## Overview

The feature replaces the legacy BIP-32/BIP-85 derivation mechanism with the modern IETF `draft-dijkhuis-cfrg-hdkeys-06` `DeriveSalt` primitive for the Post-Quantum (ML-DSA) key branch.

## Implementation Steps (Summary)

1.  **Remove Legacy Code**: Delete `ckdHard`, `derivePqChildSeed` (the old version), and all BIP-32 related constants from `WalletMasterSeedProvider.kt`.
2.  **Inject HDK Manager**: Pass `HdkManager` into the `WalletMasterSeedProvider` via Koin.
3.  **Implement New Derivation**:
    ```kotlin
    // Inside WalletMasterSeedProvider.kt
    override fun getPqChildSeed(masterSeed: ByteArray): ByteArray {
        // 1. Derive 32-byte salt using HDK spec
        val pqContext = "PQ_ML-DSA_Branch".encodeToByteArray()
        val pqSalt = hdkManager.deriveSalt(masterSeed, pqContext)
        
        // 2. Expand to 64 bytes for ML-DSA
        val expansionKey = "chimali_pq_seed_v1".encodeToByteArray()
        return hmacSha512(expansionKey, pqSalt)
    }
    ```
4.  **Update Constitution**: Modify `.specify/memory/constitution.md` Section II to reflect the HDK usage for the PQ branch.
5.  **Write Tests**: Add KAT tests in `WalletMasterSeedProviderTest.kt` to ensure the new derivation is deterministic and independent of the ECDSA branch.

## Testing

Run the local CI pipeline after implementation:
```powershell
.\tools\local-ci.ps1
```
Ensure all tests pass and no Detekt/Ktlint violations are introduced.
