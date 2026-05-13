# Data Model: fix-event-sourcing

**Feature**: fix-event-sourcing
**Date**: 2026-05-13

## 1. Entities

### 1.1. EventStoreKeyProvider
An interface responsible for providing the deterministic encryption key for the event sourcing persistence layer.
- **Contract**: `suspend fun getEventStoreKey(): ByteArray`
- **Output**: 32-byte `ByteArray` to be used directly by `AesEncryptionManager`.

## 2. Validation & Constraints

- The key generated MUST be exactly 32 bytes (256 bits) to satisfy AES-256 constraints.
- `EventStoreRepositoryImpl`, `SnapshotRepositoryImpl`, `PasskeyEventStoreRepositoryImpl`, and `PasskeySnapshotRepositoryImpl` MUST NO LONGER instantiate `ByteArray(32) { 0 }` (dummy key).

## 3. Data Migration (Truncation)

- Existing `EventStore` and `Snapshot` tables in `VaultDatabase` and `Fido2Database` will be fully truncated (`DELETE FROM table;`) using migration scripts or a one-time startup check. 
- Reason: Legacy data encrypted with a dummy key cannot be securely migrated without retaining the dummy key, which violates the security policy.
