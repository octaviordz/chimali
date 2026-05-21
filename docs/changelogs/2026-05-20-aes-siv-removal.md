# AES-256-SIV Removal

**Date**: 2026-05-20
**Feature branch**: `048-remove-aes-siv`
**Spec**: [specs/048-remove-aes-siv/spec.md](../specs/048-remove-aes-siv/spec.md)

## Summary

Removed AES-256-SIV as a mandatory Chimali cryptographic primitive. Exact-match searchable metadata is now protected using deterministic keyed HMAC blind indexes. Encrypted metadata values are protected using AES-256-GCM envelopes with per-record nonces and associated data binding. This change resolves the constitutional conflict between the legacy AES-SIV mandate and the prohibition on AES-GCM nonce reuse.

## Documentation and Policy Updates

- **Constitution (Principle I)**: Removed mandatory AES-256-SIV requirement. Added requirement for deterministic keyed lookup tokens for exact-match searchable metadata. Added explicit prohibition on deterministic AES-GCM nonce misuse.
- **Constitution (Key Wrapping)**: Replaced AES-SIV key-wrapping rule with platform-backed AES-GCM/AEAD using unique nonces and associated data.
- **Constitution (SQL Guidance)**: Replaced "deterministic ciphertext (AES-SIV)" guidance with keyed lookup-token and SQLCipher display-field policy.
- **BRD (NFR-SEC-010)**: Updated to reflect AES-GCM/AEAD value encryption, keyed lookup tokens, SQLCipher display exceptions, and non-SIV Bouncy Castle scope.
- **TRD (NFR-SEC-010)**: Updated to reflect removal of AES-SIV and adoption of HMAC blind indexes for searchable metadata and AES-GCM/AEAD for key wrapping.
- **Research (`AES_SIV_vs_GCM_Evaluation.md`)**: Updated current guidance to recommend HMAC blind indexes + AES-GCM instead of AES-SIV.
- **Changelog (`2026-04-03-fido2-cryptographic-and-transport-hardening.md`)**: Marked AES-SIV section as historical reference.
- **Changelog (`2026-05-20-proto-datastore-migration.md`)**: Noted that `AesSivEncryptionManager` was subsequently removed.

## New Services

| Service | File | Description |
|---------|------|-------------|
| `MetadataLookupTokenService` | `core/security/api/MetadataLookupTokenService.kt` | Lookup token contract with domain separation constants |
| `HmacMetadataLookupTokenService` | `core/security/impl/HmacMetadataLookupTokenService.kt` | HMAC-SHA256 blind index with domain + input canonicalization |
| `EncryptedMetadataService` | `core/security/api/EncryptedMetadataService.kt` | Authenticated encrypted metadata envelope contract |
| `AesGcmEncryptedMetadataService` | `core/security/impl/AesGcmEncryptedMetadataService.kt` | AES-256-GCM with VERSION_1 header, 12-byte random nonce, GCM-128 tag, constant-time tamper detection |
| `CredentialMetadataProtectionService` | `feature/fido2/data/service/CredentialMetadataProtectionService.kt` | FIDO2 orchestration of token generation and metadata encryption |
| `SearchableMetadataMigrationState` | `feature/fido2/data/database/SearchableMetadataMigrationState.kt` | Idempotent, transactional backfill migration with retry and state tracking |

## Storage Migration

**SQLDelight migration 12** adds the following columns and indexes:

| Table | New Columns | New Indexes |
|-------|-------------|-------------|
| `passkey_credential` | `rp_id_index BLOB`, `user_id_index BLOB`, `encrypted_metadata BLOB` | `idx_passkey_credential_rp_id_index`, `idx_passkey_credential_user_id_index` |
| `relying_party` | `encrypted_metadata BLOB` | — |
| `user_consent_record` | `rp_id_index BLOB` | `idx_user_consent_record_rp_id_index` |

All exact-match queries on RP ID and user ID were updated to use the new token columns. Partial text management search is preserved via SQLCipher-only display fields (`user_name`, `user_display_name`, `name`).

`SearchableMetadataMigrationState.migrate()` is invoked asynchronously by `Fido2Initializer` after BouncyCastle/KeyStore warmup completes, ensuring the crypto infrastructure is fully provisioned before any migration runs. The migration is idempotent and safe to re-run after interruption.

## AES-SIV Surface Removed

| File | Action |
|------|--------|
| `core/security/src/commonMain/kotlin/com/chimali/core/security/api/SivEncryptionManager.kt` | **Deleted** |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesSivEncryptionManager.kt` | **Deleted** |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/EncryptedMetadataIndexService.kt` | **Deleted** |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/di/SecurityModule.kt` | Removed AES-SIV DI references |

## Bouncy Castle Scope

Bouncy Castle (`org.bouncycastle:bcprov-jdk15on`) remains in `core/security` and `feature/fido2` for:

- **HDK (Hybrid Key Derivation)**: EC key operations for FIDO2 hybrid transport.
- **Post-Quantum Cryptography (PQC)**: ML-KEM and related algorithms.

Bouncy Castle **is not** removed by this feature. A separate provider-migration feature would be required to evaluate full BC removal.

## Test Coverage Added

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `HmacMetadataLookupTokenServiceTest` | Determinism, domain separation, versioning, non-reversibility | `core/security` |
| `AesGcmEncryptedMetadataServiceTest` | Tamper detection, AAD binding, version header | `core/security` |
| `SearchableMetadataMigrationTest` | Successful migration, failure/rollback, idempotency, retry, partial migration, concurrent guard | `feature/fido2` |
| `SearchableMetadataDaoTest` | Token-based exact-match DAO queries, null token handling | `feature/fido2` |
| `CredentialRepositorySearchableMetadataTest` | Repository lookup, partial search, tampered metadata fallback, null metadata fallback | `feature/fido2` |
| `SearchableMetadataPerformanceTest` | 10,000-record lookup within 200ms budget | `feature/fido2` |
| `SecurityModuleTest` | DI binding verification — no `SivEncryptionManager` binding | `core/security` |
| `RegistrationAuthenticationDataIntegrationTest` | Full registration/authentication with migrated credential schema | `feature/fido2` |

## Runtime Migration Fix

The initial implementation triggered `SearchableMetadataMigrationState.migrate()` synchronously from the Koin `@Single` provider for `Fido2Database`. This caused an `IllegalStateException: Index key not provisioned` because the HMAC/AES-GCM keys derived from the master seed had not yet been provisioned at DI initialization time.

**Fix**: Migration was moved to `Fido2Initializer`, which runs asynchronously after the BouncyCastle/KeyStore warmup. `WalletMasterSeedProvider` now provisions symmetric keys into `HmacMetadataLookupTokenService` and `AesGcmEncryptedMetadataService` immediately after master seed initialization via a new `provisionSearchableMetadataKeys()` method.

## Test Fixture Updates

Stricter domain model validations (`RpId`, `CredentialId`) exposed sloppy test fixtures:

| Test Class | Fix |
|------------|-----|
| `CredentialRepositoryImplTest` | Added `decryptMetadata` stub to relaxed mock — was returning empty string causing `RpId("")` blank validation failure |
| `CredentialRepositorySearchableMetadataTest` | Replaced invalid Base64 strings (`"aaguid"`, `"tampered-cred"`) with valid UrlSafe Base64 16-byte encodings for `aaguid` and `credential_id` columns |
| `RegistrationAuthenticationDataIntegrationTest` | Replaced `"placeholder"` rpId mock return with `"https://data-integration.example.com"` to satisfy domain/origin validation |

## Verification

- Final AES-SIV grep across all Kotlin sources: **No results** (production API and implementation removed).
- Deterministic GCM nonce misuse grep: **No results**.
- Local CI (`tools/local-ci.ps1`): **All checks passing** — ktlint, detekt, lint, compilation, and unit tests.

