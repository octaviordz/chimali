# Data Model: Proto DataStore Migration

**Feature**: Proto DataStore Migration
**Date**: 2026-05-19

## Entities

### User Preferences

The shared schema for all user preferences and settings across the KMP project.

**Fields**:
- `wallet_seed_mnemonic` (string, optional): BIP39 mnemonic (24 words) for wallet seed derivation
- `fido2_max_credential_count` (int32, optional): Maximum number of FIDO2 credentials allowed
- `migration_completed` (bool): Flag indicating whether migration from EncryptedSharedPreferences is complete
- `migration_version` (int32): Version of the migration schema for future evolution

**Validation Rules**:
- `wallet_seed_mnemonic`: Must be exactly 24 words when present, each word must be a valid BIP39 word
- `fido2_max_credential_count`: Must be >= 1 when present, default is 1000
- `migration_completed`: Must be true before legacy storage can be removed
- `migration_version`: Must be >= 1, increments on schema changes

**State Transitions**:
1. Initial state: All fields empty (fresh installation)
2. Migrating state: `migration_completed = false`, data being transferred from legacy storage
3. Completed state: `migration_completed = true`, all data in Proto DataStore
4. Schema evolution: `migration_version` increments, optional fields added

### Wallet Seed

Represents the BIP39 mnemonic used for master seed derivation.

**Fields**:
- `mnemonic` (string): 24-word BIP39 mnemonic, space-separated
- `word_count` (int32): Always 24 for this implementation

**Validation Rules**:
- Must be exactly 24 words
- Each word must be a valid BIP39 English wordlist entry
- Mnemonic must be encrypted at rest (Constitution §I)

**Relationships**:
- Used to derive master seed via PBKDF2-SHA512
- Master seed used for HDK-ECDH-P256 key derivation (Constitution §II)

### FIDO2 Settings

Configuration settings for the FIDO2 authenticator.

**Fields**:
- `max_credential_count` (int32): Maximum number of credentials the authenticator can store
- `default_limit` (int32): Default limit when not customized (1000)

**Validation Rules**:
- `max_credential_count` must be >= 1
- `max_credential_count` must be <= device capacity
- Default is 1000 if not set

**Relationships**:
- Used by Fido2SettingsRepository to enforce storage limits
- Affects CTAP2_ERR_KEY_STORE_FULL error behavior

### Migration Data

Temporary entity representing data being transferred from legacy storage.

**Fields**:
- `source_type` (enum): LEGACY_ENCRYPTED_SHARED_PREFS
- `source_file` (string): Path to legacy storage file
- `target_type` (enum): PROTO_DATASTORE
- `status` (enum): PENDING, IN_PROGRESS, COMPLETED, FAILED
- `error_message` (string, optional): Error details if migration failed

**Validation Rules**:
- `status` must transition: PENDING → IN_PROGRESS → COMPLETED or FAILED
- `error_message` required when status is FAILED
- Source file must exist before migration starts

**State Transitions**:
1. PENDING: Migration queued, not started
2. IN_PROGRESS: Data being read from legacy storage and written to Proto DataStore
3. COMPLETED: Migration successful, legacy data can be removed
4. FAILED: Migration failed, error logged, retry possible

## Protocol Buffer Schema

```protobuf
syntax = "proto3";

package com.chimali.core.common;

option java_package = "com.chimali.core.common";
option java_multiple_files_files = true;

message UserPreferences {
  // Wallet seed data (encrypted)
  string wallet_seed_mnemonic = 1;
  
  // FIDO2 settings
  int32 fido2_max_credential_count = 2;
  
  // Migration metadata
  bool migration_completed = 3;
  int32 migration_version = 4;
  
  // Reserved for future preferences
  reserved 5 to 10;
}
```

## Encryption Strategy

### Sensitive Data Encryption

Per Constitution §I (Security First):
- `wallet_seed_mnemonic` MUST be encrypted with AES-256-GCM
- Encryption key stored in Android KeyStore
- Encryption wrapper applied at DataStore level (transparent to application code)

### Non-Sensitive Data

- `fido2_max_credential_count`: Not encrypted (configuration data)
- `migration_completed`: Not encrypted (metadata)
- `migration_version`: Not encrypted (metadata)

## Data Migration Flow

```
1. App Launch
   ↓
2. Check migration_completed flag
   ↓
3. If false:
   a. Read from EncryptedSharedPreferences (chimali_wallet_seed.xml)
   b. Read from EncryptedSharedPreferences (fido2_settings.xml)
   c. Write to Proto DataStore with encryption wrapper
   d. Verify data integrity
   e. Set migration_completed = true
   ↓
4. If true:
   a. Read from Proto DataStore only
   b. Legacy storage files can be removed (future cleanup)
```

## Schema Evolution

### Version 1 (Initial)
- Fields: wallet_seed_mnemonic, fido2_max_credential_count, migration_completed, migration_version

### Future Versions
- Add optional fields with new field numbers (never reuse deleted numbers)
- Increment migration_version on schema changes
- Provide migration logic for each version increment
- Maintain backward compatibility via optional fields
