# Changelog: FIDO2 HDK Keys & Master Seed Integration

**Date:** 2026-03-12  
**Task IDs:** T145a, T145b, T145c  
**Requirement Focus:** FR-AUTH-030 (Master Seed Backup), FR-HID-015 (Secure Key Storage), NFR-SEC-040 (Master Key Management), SC-006 (Credential Storage Survives Restart)

## Overview

This changelog covers the complete T145 arc — a critical architectural pivot in the FIDO2 cryptographic layer. The series transitions credential key management from non-exportable hardware-bound Android KeyStore keys to a fully deterministic, BIP39-backed Hierarchical Deterministic Key (HDK) derivation scheme, enabling credential backup and recovery from a single mnemonic.

---

## T145a — HDK Integration into Fido2CryptoService

**Summary:** Rewrote the FIDO2 crypto layer to use HDK-ECDH-P256 (IETF `draft-dijkhuis-cfrg-hdkeys-06`) for deterministic key derivation. Introduced a temporary `EphemeralMasterSeedProvider` stub.

### Core & Infrastructure
- **Dependency Integration**: Added `:core:security` as a dependency to `feature:fido2` to enable access to `HdkManager`.
- **`MasterSeedProvider` Interface**: Created to decouple cryptographic derivation from storage (SQLCipher/Vault).
- **`EphemeralMasterSeedProvider`**: Implemented an in-memory stub to unblock development until the full BIP39 flow was ready (T145c).

### Cryptography
- **`Fido2CryptoService` (Major Rewrite)**:
  - Replaced `KeyPairGenerator` (AndroidKeyStore) with deterministic `HdkManager.deriveHdk()`.
  - Implemented P-256 key material extraction (65-byte uncompressed public keys).
  - Transitioned `sign()` to use BouncyCastle with a blinded private scalar derived in-memory.
  - Private key scalars are **never persisted** and are zeroed immediately after signing.
  - Deterministic HDK path mapping via SHA-256 hash of `credentialId`.

### Tests
- `Fido2CryptoServiceTest`: Key stability, derivation paths, DER-encoded signatures.
- Migrated `GetAssertionUseCaseTest` to `Fido2CryptoService` mocks.

---

## T145b — Use Case Migration to Derived Keys

**Summary:** Migrated `RegisterCredentialUseCase` and `AuthenticateAssertionUseCase` to fully use the HDK derivation path, removing all residual direct KeyStore references.

### Changes
- **`RegisterCredentialUseCase`**: Refactored to obtain key material exclusively from `Fido2CryptoService`, which internally uses HDK derivation.
- **`AuthenticateAssertionUseCase` / `GetAssertionUseCase`**: Eliminated all `java.security.KeyStore` logic; signing fully delegated to `Fido2CryptoService`.
- Added `Fido2Exception.SigningFailed` for HDK-specific signing error tracking.

---

## T145c — Persistent BIP39 Master Seed Provider

**Summary:** Replaced the ephemeral seed stub with a persistent, BIP39-backed `WalletMasterSeedProvider`. The mnemonic is generated once, encrypted on-device, and the deterministic seed is re-derived on each launch. All tests pass.

### `core:security`

- **`bip39_english.txt`** *(NEW)*: Official 2048-word BIP39 English wordlist (source: trezor/python-mnemonic) added to module assets.
- **`Bip39MasterSeedGenerator`** *(REWRITTEN)*:
  - Now accepts `@ApplicationContext` to load the wordlist from assets.
  - Full BIP39-compliant `entropyToMnemonic`: entropy → SHA-256 checksum → 11-bit group → wordlist index.
  - PBKDF2-HMAC-SHA512 with 2048 iterations for seed derivation (BIP39 spec).
- **`build.gradle.kts`**: Added MockK, JUnit Jupiter, and coroutines-test for unit testing.
- **`Bip39MasterSeedGeneratorTest`** *(NEW)*: 10 tests — word count validation, entropy determinism, seed length, passphrase sensitivity.

### `feature:fido2`

- **`WalletMasterSeedProvider`** *(NEW)*:
  - Generates a 24-word BIP39 mnemonic on first launch.
  - Persists securely via `EncryptedSharedPreferences` (AES-256-GCM for values, satisfying Constitution §I).
  - Re-derives the 64-byte seed deterministically on subsequent launches (`MasterSeedGenerator.deriveSeed`).
  - In-process seed and device key pair are cached (`@Singleton`, `@Synchronized`).
- **`EphemeralMasterSeedProvider`** *(DELETED)*: T145a stub removed.
- **`Fido2Module`**: `@Binds` updated from `EphemeralMasterSeedProvider` → `WalletMasterSeedProvider`.
- **`build.gradle.kts`**: Added `androidx.security:security-crypto:1.0.0` (stable).
- **`WalletMasterSeedProviderTest`** *(NEW)*: 6 tests using a `TestableWalletMasterSeedProvider` test double verifying initialization, in-process caching, mnemonic reuse, and device key pair stability.

### Test Results

```
:core:security:testDebugUnitTest    — 10 tests PASSED ✅
:feature:fido2:testDebugUnitTest    —  6 tests PASSED ✅
BUILD SUCCESSFUL
```

---

## Security Assessment

| Aspect | Detail |
|--------|--------|
| **Key exportability** | HDK-derived keys are software keys; the master seed is the single secret. Recovery = mnemonic. |
| **Mnemonic storage** | AES-256-GCM (EncryptedSharedPreferences value encryption). |
| **Memory safety** | Private scalars exist in memory only for the duration of the signing operation. |
| **Quantum readiness** | Architecture supports HHD (Hybrid HD), enabling PQ key derivation from the same BIP39 root (see BRD NFR-SEC-040). |

> ⚠️ **Migration Notice**: Credentials registered under T145a's ephemeral seed are orphaned (the ephemeral seed no longer exists). Users must re-register any previously created passkey credentials.

---

## Next Steps

- **T146**: Secure backup verification — mnemonic export/recovery path.
- **T147**: Audit logging for security events.
- **T145d** *(future)*: Shamir's Secret Sharing (SSS) partitioned backup.
