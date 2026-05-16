# Data Model: SQL Convention Alignment

**Feature**: 043-sql-convention-alignment
**Date**: 2026-05-16

## Table Rename Mapping

### VaultDatabase (`core:database`)

| Current Name (PascalCase) | New Name (snake_case) | Notes |
|---------------------------|----------------------|-------|
| `Identity` | `identity` | PK: `id` |
| `IdentityBackup` | `identity_backup` | FK → `identity(id)` |
| `VaultEntry` | `vault_entry` | FK → `identity(id)` |
| `Label` | `label` | PK: `id` |
| `VaultEntryLabel` | `vault_entry_label` | Composite PK, FKs → `vault_entry`, `label` |
| `EventStore` | `event_store` | Composite PK |
| `SnapshotStore` | `snapshot_store` | Composite PK |

### Fido2Database (`feature:fido2`)

| Current Name (PascalCase) | New Name (snake_case) | Notes |
|---------------------------|----------------------|-------|
| `PasskeyCredential` | `passkey_credential` | PK: `id`, UNIQUE(rp_id, user_id) |
| `RelyingParty` | `relying_party` | PK: `id` |
| `UserConsentRecord` | `user_consent_record` | FK → `relying_party(id)` |
| `BluetoothHidSession` | `bluetooth_hid_session` | PK: `session_id` |
| `PairedDevice` | `paired_device` | PK: `mac_address` |
| `EventStore` | `event_store` | Composite PK |
| `SnapshotStore` | `snapshot_store` | Composite PK |

## Column Rename Mapping

### passkey_credential (was PasskeyCredential)

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `createdAt` | `created_at` | INTEGER NOT NULL | 2 (audit) |
| `lastUsedAt` | `last_used_at` | INTEGER | 3 (audit) |
| `aaguid` | `aaguid` | TEXT NOT NULL | 4 (alpha) |
| `coseAlgorithm` | `cose_algorithm` | INTEGER NOT NULL DEFAULT -7 | 5 (alpha) |
| `credentialId` | `credential_id` | TEXT NOT NULL | 6 (alpha) |
| `credProtectPolicy` | `cred_protect_policy` | INTEGER NOT NULL DEFAULT 1 | 7 (alpha) |
| `label` | `label` | TEXT | 8 (alpha) |
| `privateKeyAlias` | `private_key_alias` | TEXT NOT NULL | 9 (alpha) |
| `publicKey` | `public_key` | TEXT NOT NULL | 10 (alpha) |
| `rpId` | `rp_id` | TEXT NOT NULL | 11 (alpha) |
| `rpName` | `rp_name` | TEXT NOT NULL | 12 (alpha) |
| `signCount` | `sign_count` | INTEGER NOT NULL DEFAULT 0 | 13 (alpha) |
| `userDisplayName` | `user_display_name` | TEXT NOT NULL | 14 (alpha) |
| `userId` | `user_id` | TEXT NOT NULL | 15 (alpha) |
| `userName` | `user_name` | TEXT NOT NULL | 16 (alpha) |

### relying_party (was RelyingParty)

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `createdAt` | `created_at` | INTEGER NOT NULL | 2 (audit) |
| `lastUsedAt` | `last_used_at` | INTEGER | 3 (audit) |
| `credentialCount` | `credential_count` | INTEGER NOT NULL DEFAULT 0 | 4 (alpha) |
| `iconUrl` | `icon_url` | TEXT | 5 (alpha) |
| `isBlocked` | `is_blocked` | INTEGER NOT NULL DEFAULT 0 | 6 (alpha) |
| `name` | `name` | TEXT NOT NULL | 7 (alpha) |

### user_consent_record (was UserConsentRecord)

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `timestamp` | `timestamp` | INTEGER NOT NULL | 2 (audit) |
| `isBiometricUsed` | `biometric_used` | INTEGER NOT NULL DEFAULT 0 | 3 (alpha) |
| `credentialId` | `credential_id` | TEXT | 4 (alpha) |
| `deviceId` | `device_id` | TEXT | 5 (alpha) |
| `ipAddress` | `ip_address` | TEXT | 6 (alpha) |
| `operationType` | `operation_type` | TEXT NOT NULL | 7 (alpha) |
| `isPinUsed` | `pin_used` | INTEGER NOT NULL DEFAULT 0 | 8 (alpha) |
| `rpId` | `rp_id` | TEXT NOT NULL | 9 (alpha) |
| `userAgent` | `user_agent` | TEXT | 10 (alpha) |

### bluetooth_hid_session (was BluetoothHidSession)

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `sessionId` | `session_id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `startTime` | `start_time` | INTEGER NOT NULL | 2 (audit-like) |
| `lastActivityTime` | `last_activity_time` | INTEGER NOT NULL | 3 (audit-like) |
| `hostDeviceAddress` | `host_device_address` | TEXT NOT NULL | 4 (alpha) |
| `isActive` | `is_active` | INTEGER NOT NULL DEFAULT 1 | 5 (alpha) |
| `protocolVersion` | `protocol_version` | TEXT NOT NULL | 6 (alpha) |

### paired_device (was PairedDevice)

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `macAddress` | `mac_address` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `createdAt` | `created_at` | INTEGER NOT NULL | 2 (audit) |
| `lastUsedAt` | `last_used_at` | INTEGER NOT NULL | 3 (audit) |
| `alias` | `alias` | TEXT | 4 (alpha) |
| `deviceClass` | `device_class` | INTEGER | 5 (alpha) |
| `name` | `name` | TEXT | 6 (alpha) |

### identity (was Identity) — VaultDatabase

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `alias` | `alias` | TEXT NOT NULL | 2 (alpha) |

### identity_backup (was IdentityBackup) — VaultDatabase

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `backup_method` | `backup_method` | TEXT NOT NULL | 2 (alpha) |
| `configuration_json` | `configuration_json` | TEXT NOT NULL | 3 (alpha) |
| `identity_id` | `identity_id` | TEXT NOT NULL | 4 (alpha, FK) |
| `last_backed_up_at` | `last_backed_up_at` | TEXT NOT NULL | 5 (alpha) |

### vault_entry (was VaultEntry) — VaultDatabase

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `date_created` | `date_created` | TEXT NOT NULL | 2 (audit) |
| `date_modified` | `date_modified` | TEXT NOT NULL | 3 (audit) |
| `crdt_state` | `crdt_state` | BLOB NOT NULL | 4 (alpha) |
| `doc_id` | `doc_id` | TEXT NOT NULL | 5 (alpha) |
| `encrypted_payload` | `encrypted_payload` | BLOB NOT NULL | 6 (alpha) |
| `identity_id` | `identity_id` | TEXT NOT NULL | 7 (alpha, FK) |
| `last_backed_up_at` | `last_backed_up_at` | TEXT | 8 (alpha) |
| `title` | `title` | TEXT NOT NULL | 9 (alpha) |
| `type` | `type` | TEXT NOT NULL | 10 (alpha) |

### label (was Label) — VaultDatabase

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `id` | `id` | TEXT NOT NULL PRIMARY KEY | 1 (PK) |
| `color_hex` | `color_hex` | TEXT NOT NULL | 2 (alpha) |
| `name` | `name` | TEXT NOT NULL | 3 (alpha) |

### vault_entry_label (was VaultEntryLabel) — VaultDatabase

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `entry_id` | `entry_id` | TEXT NOT NULL | 1 (composite PK) |
| `label_id` | `label_id` | TEXT NOT NULL | 2 (composite PK) |

### event_store (was EventStore) — both databases

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `aggregate_id` | `aggregate_id` | TEXT NOT NULL | 1 (composite PK) |
| `sequence_number` | `sequence_number` | INTEGER NOT NULL | 2 (composite PK) |
| `timestamp` | `timestamp` | TEXT NOT NULL | 3 (audit) |
| `payload` | `payload` | BLOB NOT NULL | 4 (alpha) |

### snapshot_store (was SnapshotStore) — both databases

| Current Column | New Column | Type | Order |
|----------------|-----------|------|-------|
| `aggregate_id` | `aggregate_id` | TEXT NOT NULL | 1 (composite PK) |
| `sequence_number` | `sequence_number` | INTEGER NOT NULL | 2 (composite PK) |
| `timestamp` | `timestamp` | TEXT NOT NULL | 3 (audit) |
| `payload` | `payload` | BLOB NOT NULL | 4 (alpha) |

## View Rename Mapping (Fido2Database)

| Current Name | New Name |
|-------------|----------|
| `CredentialSummary` | `credential_summary` |
| `RelyingPartyStats` | `relying_party_stats` |

## Index Rename Mapping (Fido2Database)

| Current Name | New Name |
|-------------|----------|
| `idx_passkey_credential_rpId` | `idx_passkey_credential_rp_id` |
| `idx_passkey_credential_userId` | `idx_passkey_credential_user_id` |
| `idx_user_consent_rpId` | `idx_user_consent_record_rp_id` |
| `idx_user_consent_timestamp` | `idx_user_consent_record_timestamp` |
| `idx_bluetooth_session_active` | `idx_bluetooth_hid_session_is_active` |

## Kotlin Impact Summary

### Generated Type Name Changes (import alias strategy)

Files using `import ... as PasskeyCredentialEntity` pattern can absorb the type rename via updated alias. The generated class name changes from `PasskeyCredential` to `Passkey_credential` (SQLDelight naming convention for snake_case tables).

### Property Accessor Changes (every call site)

All `.createdAt` → `.created_at`, `.rpId` → `.rp_id`, etc. changes propagate to:
- `EntityMappers.kt` — ~30 accessor references
- `PasskeyCredentialDao.kt` — ~20 named parameter references
- `RelyingPartyDao.kt` — ~15 named parameter references
- `PairedDeviceRepositoryImpl.kt` — ~10 references
- `CredentialRepositoryImpl.kt` — ~10 references
- `Fido2RepositoryImpl.kt` — ~5 references
- Test files — ~40+ references across integration, unit, and schema tests

### Query Name Changes

SQLDelight query accessors are derived from the query name in `.sq` files (e.g., `database.passkeyCredentialQueries`). Since query names are `camelCase` per constitution (and already are), no query name changes are needed. However, the queries property accessor changes from `database.passkeyCredentialQueries` to `database.passkey_credentialQueries` (reflecting the table name change).
