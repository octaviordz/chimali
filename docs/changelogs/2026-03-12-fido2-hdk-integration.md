# Changelog: FIDO2 HDK Keys & Master Seed Integration

**Date:** 2026-03-12  
**Task ID:** T145a  
**Requirement Focus:** FR-AUTH-030 (Master Seed Backup), NFR-SEC-040 (Master Key Management)

## Overview
This update implements a critical pivot in the FIDO2 cryptographic architecture. To satisfy the project's requirement for a **Master Seed** backup mechanism (allowing all credentials to be restored from a single recovery point), we have transitioned away from non-exportable hardware-backed Android KeyStore keys for FIDO2 credentials in favor of software-derived keys using the **HDK-ECDH-P256** standard (IETF `draft-dijkhuis-cfrg-hdkeys-06`).

## Changes

### Core & Infrastructure
- **Dependency Integration**: Added `:core:security` as a dependency to the `feature:fido2` module to enable access to `HdkManager`.
- **Master Seed Plumbing**: 
    - Created the `MasterSeedProvider` interface to decouple cryptographic derivation from specific storage implementations (SQLCipher/Vault).
    - Implemented `EphemeralMasterSeedProvider` as an in-memory stub to unblock current development until the full BIP39 onboarding flow is ready (T145c).

### Cryptography (feature:fido2)
- **Fido2CryptoService (Major Rewrite)**:
    - Replaced the `KeyPairGenerator` based on "AndroidKeyStore" with deterministic derivation via `HdkManager.deriveHdk()`.
    - Implemented P-256 key material extraction (65-byte uncompressed public keys).
    - Transitioned `sign()` to use **BouncyCastle** with the blinded private scalar derived in-memory on-demand.
    - **Security Enhancement**: Private key scalars are never persisted to disk and are zeroed out immediately after signing operations.
    - **Path Derivation**: Implemented deterministic HDK path mapping using the SHA-256 hash of the `credentialId`.
- **Exception Handling**: Added `Fido2Exception.SigningFailed` to specifically track HDK-related signing errors.

### Use Cases (feature:fido2)
- **GetAssertionUseCase**: Refactored to eliminate all direct `java.security.KeyStore` logic. The use case now delegates signing to `Fido2CryptoService`, abstracting the underlying key management strategy.

### Quality Assurance & Verification
- **New Unit Tests**: Implemented `Fido2CryptoServiceTest` covering key stability, derivation paths, and signature formation (DER encoding).
- **Test Fixes**: 
    - Migrated `GetAssertionUseCaseTest` to use `Fido2CryptoService` mocks, resolving compilation errors and removing obsolete KeyStore stubbing.
    - Fixed a pre-existing parameter naming bug in `PasskeyCredentialDaoTest` (`credentialId` -> `id`) uncovered during the full module recompile.
- **Verification**: All unit tests in `:feature:fido2` pass (`./gradlew :feature:fido2:testDebugUnitTest`).

## Security Assessment
- **Benefit**: Credentials are now fully restorable from the Master Seed, preventing data loss on device reset or app uninstallation.
- **Mitigation**: While keys are no longer "Hardware Backed" in the traditional SE/TEE sense, the master seed itself will be protected by SQLCipher (AES-256-GCM) with keys deriveable only when the user unlocks their vault via Biometrics/PIN. This maintains a high security bar while enabling the required backup functionality.

## Next Steps
- **T145c**: Implementation of `WalletMasterSeedProvider` backed by BIP39 mnemonics.
- **T145d**: Shamir's Secret Sharing (SSS) integration for partitioned master seed backup.
- **T146**: Persistence mechanism for the device root key pair.
