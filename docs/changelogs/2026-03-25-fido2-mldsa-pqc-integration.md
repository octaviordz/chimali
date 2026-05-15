# FIDO2 Post-Quantum Cryptography (ML-DSA) Integration
**Date:** 2026-03-25

## Overview
This update implements Post-Quantum Cryptography (PQC) for the Chimali FIDO2 authenticator, fulfilling NFR-SEC-010. It transitions the experimental post-quantum stack from **ML-KEM (Kyber)**—which is an encryption/encapsulation scheme—to **ML-DSA-65 (Dilithium)**, which is the NIST FIPS 204 standardized digital signature scheme required for FIDO2 operations (`MakeCredential` and `GetAssertion`). 

These changes also establish a cryptographically isolated key hierarchy following HDK (Hierarchical Deterministic Key) derivation strategies and implement algorithm negotiation for CTAP2.

## Key Changes

### 1. Algorithm Pivot to ML-DSA-65
- **`PostQuantumCrypto.kt` Rewrite**: Completely replaced the old ML-KEM encapsulation methods with ML-DSA-65 signature generation and verification logic.
- **Provider Migration**: Transitioned from the experimental `BouncyCastlePQCProvider` to the standard `BouncyCastleProvider` (`bcprov-jdk18on` v1.80), which now ships with production-ready ML-DSA support.
- **Deterministic Key Generation**: ML-DSA keypairs are generated deterministically by seeding a `SHA1PRNG` SecureRandom instance with a 64-byte derived child seed.

### 2. BIP-85 Cryptographic Isolation (HDK)
- **`MasterSeedProvider` Extension**: Added the `getPqChildSeed()` contract.
- **`WalletMasterSeedProvider` Derivation**: Implemented specific derivation for the post-quantum hierarchy. To ensure a compromise of PQ keys does not affect classical ECDSA keys (and vice versa), the PQ seed is derived using:
  1. A root BIP-32 key derived using `HMAC-SHA512("Bitcoin seed", masterSeed)`
  2. A fully-hardened CKD derivation path: `[83696968', 83286642', 2']`
  3. Final entropy extraction via `HMAC-SHA512("bip-entropy-from-k", derivedKey)` (producing the 64-byte seed).

### 3. CTAP2 Advertisement and Algorithm Negotiation
- **`getInfo` Update**: `Ctap2ResponseBuilder.kt` now advertises `COSE -257` (the working-draft COSE identifier for ML-DSA-65) in alongside `-7` (ES256 / P-256) in the authenticator's supported algorithms list.
- **Algorithm Negotiation**: Modified `Ctap2MakeCredentialHandler.kt` to inspect the Relying Party's `pubKeyCredParams` list and negotiate the highest priority supported algorithm.
- **Logging**: Added `Timber.i` statements to log exact details of the negotiation process, providing visibility into RP algorithm requests.
- **Domain Model**: Updated `PublicKeyCredentialParameters.kt` to explicitly allow "ML-DSA" and added a `createMlDsa65()` factory method.

### 4. Comprehensive Testing Suite
- **`PqcSigningTest.kt`**: Introduced an 8-test suite covering:
  - Determinism (Known Answer Tests - same seed produces identical keys).
  - Cryptographic branch isolation (different seeds produce different keys).
  - Sign/Verify Round-tripping.
  - Tamper detection (modified messages fail to verify).
  - Cross-key rejection (verifying with the wrong public key fails).
  - Minimum lattice key size bounds testing (verifying >1952 bytes size).
- **`PostQuantumCryptoTest.kt`**: Removed broken legacy Kyber tests and replaced them with ML-DSA specific structural smoke tests.
- **`WalletMasterSeedProviderTest.kt`**: Added the `getPqChildSeed` stub to the automated test doubles to satisfy the updated interface.

## Technical Notes & Future Scope
* **COSE Constant**: `-257` is the current working draft identifier for ML-DSA-65 and aligns with Chrome implementations. IANA standardization is pending and this constant may require a future minor update.
* **RP Round-Trip**: This update establishes full support on the Authenticator side. End-to-end post-quantum registration relies on the Relying Party explicitly requesting `alg: -257` in its `MakeCredential` JSON options.
