# Quickstart: Remove AES-256-SIV

## Prerequisites

- Work on branch `048-remove-aes-siv`.
- Read [spec.md](spec.md), [plan.md](plan.md), and [research.md](research.md).
- Keep the AES-SIV removal scope separate from full Bouncy Castle removal.

## Implementation Order

1. Update `.specify/memory/constitution.md`.
   - Remove the AES-256-SIV mandatory rule.
   - Add deterministic keyed lookup tokens for exact-match searchable metadata.
   - Prohibit deterministic AES-GCM nonce misuse.
   - Replace AES-SIV key wrapping with platform-backed AES-GCM/AEAD using unique nonces and associated data.
   - Update SQL indexing guidance.

2. Update current documentation.
   - Align `docs/brd.md` and `docs/trd.md`.
   - Update active research notes that still recommend AES-SIV as mandatory.
   - Add a changelog entry for the policy change.

3. Implement replacement searchable metadata behavior.
   - Add the lookup-token abstraction.
   - Add encrypted metadata value handling.
   - Add deterministic/domain-separation/tamper tests first.
   - Add key-wrapping policy checks for unique nonces and associated data.

4. Migrate FIDO2 storage.
   - Add migration fields.
   - Backfill existing records.
   - Move exact-match queries to lookup tokens.
   - Preserve partial text search over explicitly classified SQLCipher-only display fields.

5. Remove AES-SIV-specific surface area.
   - Remove or deprecate the AES-SIV API and implementation.
   - Replace the encrypted metadata index service.
   - Update stale Bouncy Castle comments.

6. Verify.
   - Run focused security and migration tests.
   - Run FIDO2 regression tests.
   - Run `tools/local-ci.ps1`.

## Focused Validation Commands

### 1. Verify no AES-SIV production APIs remain in Kotlin sources

```powershell
rg -n "AesSivEncryptionManager|SivEncryptionManager|EncryptedMetadataIndexService" --type kotlin .specify docs core feature app
```

**Expected**: No results (all files removed in this feature).

### 2. Verify no deterministic GCM nonce misuse in Kotlin sources

```powershell
rg -n "fixed nonce|reused nonce|plaintext-derived nonce|deterministic GCM" --type kotlin .specify docs core feature app
```

**Expected**: No results.

### 3. Verify AES-SIV references in docs are marked historical

```powershell
rg -n "AES-256-SIV|AES_SIV|AesSiv" docs specs
```

**Expected**: Only in historical sections of `2026-04-03-fido2-cryptographic-and-transport-hardening.md`, `2026-05-20-proto-datastore-migration.md`, and `specs/048-remove-aes-siv/` documents.

### 4. Verify Bouncy Castle references are classified as non-SIV

```powershell
rg -n "BouncyCastle|bouncycastle|bouncy.castle" --type kotlin .specify docs core feature app
```

**Expected**: Any remaining BC references are in HDK/PQC contexts, not AES-SIV contexts.

### 5. Verify lookup-token service is registered in DI

```powershell
rg -n "MetadataLookupTokenService|EncryptedMetadataService|CredentialMetadataProtectionService" --type kotlin core feature
```

**Expected**: Services defined in `core/security/src/androidMain/kotlin/com/chimali/core/security/di/SecurityModule.kt` and consumed by `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/CredentialMetadataProtectionService.kt`.

### 6. Verify lookup-token columns are present in schema

```powershell
rg -n "rp_id_index|user_id_index|encrypted_metadata" feature/fido2/src/commonMain/sqldelight
```

**Expected**: Column definitions in `Fido2Database.sq`, migration in `Fido2Database/12.sqm`, and queries in `PasskeyCredential.sq`, `UserConsentRecord.sq`, `RelyingParty.sq`.

### 7. Run security and migration host tests

```powershell
.\gradlew :feature:fido2:androidHostTest --tests "com.chimali.fido2.data.database.SearchableMetadataMigrationTest" --no-daemon
```

```powershell
.\gradlew :feature:fido2:androidHostTest --tests "com.chimali.fido2.data.dao.SearchableMetadataDaoTest" --no-daemon
```

```powershell
.\gradlew :feature:fido2:androidHostTest --tests "com.chimali.fido2.data.repository.CredentialRepositorySearchableMetadataTest" --no-daemon
```

```powershell
.\gradlew :core:security:test --no-daemon
```

### 8. Run local CI gate

```powershell
.\tools\local-ci.ps1
```

**Expected**: All checks pass, exit code 0.

## Final Implementation State

### Services Implemented

| Service | File | Role |
|---------|------|------|
| `MetadataLookupTokenService` | `core/security/api/MetadataLookupTokenService.kt` | Lookup token contract |
| `HmacMetadataLookupTokenService` | `core/security/impl/HmacMetadataLookupTokenService.kt` | HMAC-SHA256 blind index |
| `EncryptedMetadataService` | `core/security/api/EncryptedMetadataService.kt` | Encrypted metadata contract |
| `AesGcmEncryptedMetadataService` | `core/security/impl/AesGcmEncryptedMetadataService.kt` | AES-256-GCM envelope |
| `CredentialMetadataProtectionService` | `feature/fido2/data/service/CredentialMetadataProtectionService.kt` | FIDO2 orchestration |
| `SearchableMetadataMigrationState` | `feature/fido2/data/database/SearchableMetadataMigrationState.kt` | Backfill migration engine |

### AES-SIV Surface Removed

| File | Action |
|------|--------|
| `core/security/api/SivEncryptionManager.kt` | **Deleted** |
| `core/security/impl/AesSivEncryptionManager.kt` | **Deleted** |
| `feature/fido2/data/service/EncryptedMetadataIndexService.kt` | **Deleted** |

### Database Schema Changes (Migration 12)

| Table | New Columns | New Indexes |
|-------|-------------|-------------|
| `passkey_credential` | `rp_id_index BLOB`, `user_id_index BLOB`, `encrypted_metadata BLOB` | `idx_passkey_credential_rp_id_index`, `idx_passkey_credential_user_id_index` |
| `relying_party` | `encrypted_metadata BLOB` | — |
| `user_consent_record` | `rp_id_index BLOB` | `idx_user_consent_record_rp_id_index` |

## Expected Final State

- The constitution no longer mandates AES-SIV.
- Exact-match lookup uses deterministic keyed HMAC lookup tokens.
- Partial text search remains on explicitly classified SQLCipher-only display fields (`user_name`, `user_display_name`, `name`).
- Recoverable metadata values use authenticated AES-256-GCM with unique nonces and associated data.
- Key wrapping uses platform-backed AES-GCM/AEAD with unique nonces and associated data.
- AES-SIV production APIs and services are removed.
- Bouncy Castle remains only for non-SIV paths (HDK, PQC).
- Local CI passes.
