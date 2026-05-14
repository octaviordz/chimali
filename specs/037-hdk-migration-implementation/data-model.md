# Data Model: HDK Migration Implementation

This feature refactors existing cryptographic key derivation logic. It does not introduce new persistent database entities or modify existing data structures.

## Core Cryptographic Data Flow

1.  **Master Seed (Input)**:
    *   **Type**: `ByteArray`
    *   **Size**: 32 to 64 bytes (typically 64 bytes from BIP-39 `PBKDF2-SHA512`).
    *   **Role**: The root entropy from which all keys are derived.
    *   **Persistence**: Stored encrypted in `EncryptedSharedPreferences`. Unchanged by this feature.

2.  **PQ Context String (Internal Parameter)**:
    *   **Type**: `String` -> UTF-8 encoded `ByteArray`
    *   **Value**: `"PQ_ML-DSA_Branch"`
    *   **Role**: Domain separation parameter for `DeriveSalt` to ensure the PQ branch is cryptographically isolated from standard HDK ECDSA keys.

3.  **PQ Salt (Intermediate Output)**:
    *   **Type**: `ByteArray`
    *   **Size**: 32 bytes (defined by `Ns` parameter of HDK-ECDH-P256).
    *   **Generation**: `HdkManager.deriveSalt(masterSeed, "PQ_ML-DSA_Branch".encodeToByteArray())`
    *   **Role**: Intermediate deterministic entropy specifically for the PQ branch.

4.  **PQ Child Seed (Output)**:
    *   **Type**: `ByteArray`
    *   **Size**: 64 bytes
    *   **Generation**: `HMAC-SHA512(key = "chimali_pq_seed_v1", message = pqSalt)`
    *   **Role**: Final deterministic seed passed to the ML-DSA implementation to generate the post-quantum signature key pair.
    *   **Persistence**: Only exists in volatile memory during derivation and is zeroed out after use.

## Deprecated Entities (To be Removed)

The following BIP-32/BIP-85 concepts are completely removed from the data flow:
*   **Chain Codes**: The 32-byte extension used alongside private keys in BIP-32.
*   **BIP-32 Derivation Paths**: Specifically the `m/83696968'/83286642'/2'` hierarchy.
*   **BIP-32 Hardened Indexes**: Offsets starting at `0x80000000`.
