# Detailed Changelog: Event Sourcing Security Audit & Gap Identification

**Date**: 2026-05-13  
**Status**: Security Audit Complete  
**Feature**: 034-refactor-event-sourcing

---

## Summary

This update documents a critical security audit of the newly integrated **Event Sourcing** infrastructure. While the functional implementation of event stores, snapshotting, and state rehydration is stable and verified, the audit revealed a significant gap in the encryption key management strategy.

## Key Findings

### 1. Placeholder Encryption Keys (The "Dummy Key" Issue)
- **Problem**: Identified 10 call sites across 4 core repository files (`EventStoreRepositoryImpl`, `SnapshotRepositoryImpl`, and their Fido2 counterparts) that use a static, zero-filled `ByteArray(32)` as the AES-256-GCM encryption key.
- **Impact**: Event and snapshot payloads are effectively stored in plaintext. This violates **Constitution §I (Security First)** and **FR-012**.
- **Risk**: High for `Fido2Database` (no file-level encryption); Medium for `VaultDatabase` (protected by SQLCipher but lacks defense-in-depth).

### 2. Infrastructure Analysis
- **Encryption Manager**: Confirmed that `AesEncryptionManager` correctly implements AES-256-GCM with random IVs and 128-bit tags. The mechanism is sound; only the key sourcing is missing.
- **Key Derivation Patterns**: Audited existing BIP39/BIP-85 derivation logic in `WalletMasterSeedProvider.kt`. Established a clear path for deterministic key derivation for the event store.

## Actions Taken

### 1. Comprehensive Gap Analysis
- Authored [analysis-encryption-key-management.md](../../specs/034-refactor-event-sourcing/analysis-encryption-key-management.md) documenting:
    - All affected files and call sites.
    - Security impact assessment per module.
    - Proposed `EventStoreKeyProvider` interface and derivation strategy (HMAC-SHA512).
    - Data truncation requirements for the upcoming key migration.

### 2. Linting Compliance & Documentation
- Restored `TODO` comments to all affected call sites to ensure visibility for the next implementation phase.
- Applied `@Suppress("ForbiddenComment")` to the relevant classes to satisfy static analysis while maintaining the security warnings.
- Authored a formal [handover-encryption-key-management.md](../../specs/034-refactor-event-sourcing/handover-encryption-key-management.md) to guide the resolution of the identified gap.

## Next Steps

- **T032 Remediation**: Implement the `EventStoreKeyProvider` and refactor repositories to use real derived keys.
- **Data Migration**: Execute table truncation to clear dummy-encrypted data before activating real encryption.
- **Hydration Benchmarking**: Complete performance verification (T033) once the security layer is hardened.

---
*This audit ensures that the transition from functional MVP to production-hardened security is explicitly tracked and architected.*
