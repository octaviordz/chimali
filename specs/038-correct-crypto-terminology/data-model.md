# Data Model: Cryptographic Entities

This document defines the key cryptographic entities and their properties as used in the project's architecture, ensuring consistent terminology across documentation and implementation.

## Entities

### Master Seed
- **Description**: The 512-bit root of trust generated from a BIP-39 mnemonic.
- **Properties**:
  - `Size`: 512 bits (64 bytes).
  - `Generation`: BIP-39 PBKDF2(HMAC-SHA512, 2048 iterations).
  - `Storage`: Encrypted via AES-256-GCM using a device-unique key.

### HDK (Hierarchical Deterministic Key)
- **Description**: The derivation mechanism for all project keys, following IETF `draft-dijkhuis-cfrg-hdkeys-06`.
- **Properties**:
  - `Context`: Used to isolate different key types.
  - `Derivation Mode`: 
    - `Classical`: Blinding-based derivation (HDK-ECDH-P256).
    - `Post-Quantum`: `DeriveSalt`-based branch isolation.

### ML-DSA (Module-Lattice-Based Digital Signature Algorithm)
- **Description**: The primary post-quantum signature scheme (FIPS 204).
- **Properties**:
  - `Version`: ML-DSA-65.
  - `Purpose`: FIDO2 attestation and assertion signing.
  - `Isolation`: Derived from the HDK PQ branch.

### ML-KEM (Module-Lattice-Based Key-Encapsulation Mechanism)
- **Description**: The post-quantum key establishment scheme (FIPS 203).
- **Properties**:
  - `Version`: ML-KEM-768.
  - `Purpose`: Future remote key provisioning and secure channel establishment.

## Validation Rules
- All PQC keys MUST be derived using the HDK `DeriveSalt` method with a dedicated context string.
- All symmetric encryption MUST use AES-256 (GCM or SIV).
