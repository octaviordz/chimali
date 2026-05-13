# Handover: Encryption Key Management for Event Store

**Date**: 2026-05-13  
**Status**: Audit Complete | Phase: Security Audit & GAP Analysis  
**Analyst**: Antigravity  
**Reference Analysis**: [analysis-encryption-key-management.md](analysis-encryption-key-management.md)

---

## 1. Executive Summary

During the finalization of **Phase 5: Snapshot-Accelerated Hydration**, a security audit was performed on the persistence layer. A critical gap was identified: all event store and snapshot repositories are currently using a **zero-filled 32-byte dummy key** for AES-256-GCM encryption.

While the cryptographic implementation (`AesEncryptionManager`) is sound, the lack of real key material means the data is effectively stored in plaintext to any attacker with database access. This is especially critical for the `Fido2Database`, which lacks the file-level SQLCipher protection found in the `VaultDatabase`.

---

## 2. Current Status & Findings

### ✅ What is Working
- **Functional Pipeline**: Event append, state hydration, and snapshot-accelerated recovery are fully implemented and verified with tests.
- **Crypto Mechanism**: `AesEncryptionManager` correctly applies AES/GCM with random IVs and 128-bit tags.
- **Serialization**: Kotlin Serialization (JSON) is correctly applied before encryption.

### ❌ Critical Gap (The Blocker)
- **Dummy Key Usage**: 10 call sites across 4 files use a constant zero-key (`dummyKey`).
- **T032 Blocker**: Task `T032` (Verify AES-256-GCM encryption) cannot be completed until real keys are derived.
- **Module Isolation**: No common provider exists to bridge `core:data` and `feature:fido2` with a shared (or isolated) secure key source.

---

## 3. Implementation Roadmap (Next Steps)

The next developer should prioritize the following:

1.  **Define `EventStoreKeyProvider`**:
    - Create a common interface in `core:domain` or `core:security`.
    - Implement the provider in `core:security` (androidMain).
    - Use **HMAC-SHA512** to derive the ES key from the existing BIP39 Master Seed (matches `WalletMasterSeedProvider` pattern).

2.  **Refactor Repositories**:
    - Inject `EventStoreKeyProvider` into all 4 repository implementations.
    - Replace `dummyKey` usage with calls to `keyProvider.getEventStoreKey()`.

3.  **Data Truncation**:
    - Since changing the key breaks existing dummy-encrypted data, perform a one-time truncation of `EventStore` and `Snapshot` tables in both databases.

4.  **Verification**:
    - Complete task **T032** by verifying that encrypted blobs in SQLite are no longer decryptable with a zero-key.

---

## 4. Pending Tasks

| Task ID | Description | Priority |
|---------|-------------|----------|
| **T032** | Verify AES-256-GCM encryption is correctly applied | **HIGH** |
| **T033** | Benchmark hydration performance (1,000+ events) | Medium |
| **N/A** | Implement `EventStoreKeyProvider` (Pre-req for T032) | **CRITICAL** |

---

## 5. Knowledge Base Reference

- **Analysis Document**: [analysis-encryption-key-management.md](analysis-encryption-key-management.md)
- **Master Seed Logic**: See `WalletMasterSeedProvider.kt` for the reference key derivation pattern.
- **DI Configuration**: Check `SecurityModule.kt` and `Fido2Module.kt` for wiring.

---

*Handover complete. The infrastructure is functionally stable but cryptographically "cold" until the key provider is integrated.*
