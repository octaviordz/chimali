# Data Model: Remove AES-256-SIV

## CryptographicPolicy

Represents the project-governance rules that replace mandatory AES-SIV.

**Fields**

- `payloadEncryptionRule`: AES-256-GCM for recoverable encrypted values with unique nonce per encryption.
- `keyWrappingRule`: platform-backed AES-GCM/AEAD for key wrapping with unique nonce and associated data.
- `searchableMetadataRule`: deterministic keyed lookup tokens for exact-match search.
- `nonceMisuseRule`: fixed, reused, predictable, or plaintext-derived GCM nonces are prohibited.
- `keySeparationRule`: lookup, payload encryption, database encryption, and signing use separate purposes.
- `sivStatus`: AES-SIV is not mandatory and should not remain in active production paths.
- `bouncyCastleScope`: non-SIV Bouncy Castle uses are tracked separately and remain out of scope.

**Validation Rules**

- Must not contain a MUST-level AES-SIV requirement.
- Must contain a concrete replacement for exact-match searchable metadata.
- Must contain a concrete replacement for key wrapping and master-key boundary protection.
- Must preserve memory zeroing, domain separation, and no cryptographic invention rules.

## SearchableMetadataField

Represents a metadata field that participates in lookup, filtering, or display.

**Fields**

- `fieldName`: stable logical name, such as relying party id, user id, label, alias, or category.
- `classification`: `sensitive`, `sqlcipher_display_only`, or `public_protocol_value`.
- `lookupMode`: `exact_match`, `partial_text`, `display_only`, or `none`.
- `canonicalizationRule`: normalization applied before deriving lookup tokens.
- `storageDecision`: `lookup_token`, `encrypted_value`, `sqlcipher_display_exception`, or `deprecated_plain_column`.
- `migrationRequired`: whether existing rows need backfill.

**Validation Rules**

- Sensitive exact-match fields must have a lookup-token storage decision.
- Recoverable sensitive values must have an encrypted-value storage decision.
- Partial text fields must not be represented as exact-match-only lookup tokens.
- SQLCipher display exceptions must be documented and reviewed.

## KeyWrappingEnvelope

Represents wrapped key material or master-key boundary values after AES-SIV removal.

**Fields**

- `ciphertext`: platform-backed AEAD ciphertext.
- `nonce`: unique nonce or IV for the wrapping operation.
- `associatedData`: stable context binding the wrapped material to purpose, version, and storage location.
- `keyPurpose`: key or boundary purpose, distinct from lookup-token and metadata-value encryption purposes.
- `version`: wrapping format version.

**Validation Rules**

- AES-SIV must not be required or used for new key-wrapping paths in this feature.
- Nonce uniqueness is mandatory for a given wrapping key.
- Associated data must include enough context to prevent cross-purpose envelope substitution.
- Unwrapped key material must be kept in memory only as long as required and zeroed where mutable buffers are available.

## MetadataLookupToken

Represents a keyed deterministic value used only for equality lookup.

**Fields**

- `tokenBytes`: fixed-size byte sequence.
- `domain`: purpose-specific domain separator.
- `sourceField`: metadata field this token represents.
- `version`: token derivation version.
- `createdAt`: migration or write timestamp when available.

**Relationships**

- Belongs to one `SearchableMetadataField`.
- May be attached to one FIDO2 credential, relying party, consent record, vault entry, or label.

**Validation Rules**

- Same canonical value, key, domain, and version must produce the same token.
- Different domains must produce different tokens for the same canonical value.
- Tokens must not be reversible to the original metadata.
- Token comparisons that affect security decisions must use constant-time comparison where practical.

## EncryptedMetadataValue

Represents recoverable metadata protected independently from the lookup token.

**Fields**

- `ciphertext`: authenticated encrypted value.
- `nonce`: unique nonce or IV for the encryption operation.
- `associatedData`: stable context binding the value to record id, schema version, and field purpose.
- `version`: encryption format version.

**Relationships**

- May contain one or more canonical metadata values for a record.
- Has zero or more corresponding `MetadataLookupToken` entries.

**Validation Rules**

- Nonce must be unique for the same encryption key.
- Tampering with ciphertext, nonce, or associated data must fail decryption.
- Decrypted sensitive bytes must be zeroed after use where mutable buffers are available.

## SearchableMetadataRecord

Represents a persisted app record that uses searchable metadata.

**Fields**

- `recordId`: stable domain identifier.
- `recordType`: `passkey_credential`, `relying_party`, `user_consent_record`, `vault_entry`, `label`, or `paired_device`.
- `lookupTokens`: one or more `MetadataLookupToken` values.
- `encryptedMetadata`: optional `EncryptedMetadataValue`.
- `displayFields`: optional explicitly classified SQLCipher-protected fields used for UI display or partial text search.
- `migrationStatus`: `legacy`, `dual_written`, `migrated`, or `deprecated_plain_columns_pending_drop`.

**Validation Rules**

- Exact-match query paths must use `lookupTokens` after migration.
- Legacy records must be backfilled before AES-SIV-specific cleanup is considered complete.
- Display fields may preserve partial text search only when explicitly classified as SQLCipher display fields.
- Display fields must not be used as a hidden bypass for sensitive exact-match lookup unless documented as an exception.

## MigrationState

Tracks migration from legacy plaintext/SIV expectations to lookup-token storage.

**Fields**

- `featureVersion`: migration version.
- `startedAt`: first migration attempt timestamp.
- `completedAt`: completion timestamp, if complete.
- `recordsScanned`: number of records inspected.
- `recordsMigrated`: number of records successfully migrated.
- `lastRecordId`: progress marker for resumable migration.
- `failureReason`: last recoverable failure, if any.

**State Transitions**

```text
not_started -> running -> completed
not_started -> running -> failed_retryable -> running
running -> failed_retryable -> completed
running -> failed_terminal
```

**Validation Rules**

- Re-running migration must be idempotent.
- A failed retryable migration must not make already-migrated records inaccessible.
- Completion requires exact-match lookup verification over migrated records.

## DeprecatedAesSivSurface

Represents production and documentation artifacts that must be removed, replaced, or explicitly deprecated.

**Fields**

- `artifactPath`: source, test, config, or documentation location.
- `artifactType`: `api`, `implementation`, `service`, `dependency_comment`, `test`, `documentation`, or `spec_history`.
- `currentUse`: `active`, `unused`, `legacy_test`, `historical_doc`, or `non_siv_bouncy_castle`.
- `targetDisposition`: `remove`, `replace`, `deprecate`, `document_out_of_scope`, or `leave_historical`.

**Validation Rules**

- Active AES-SIV production APIs must not remain after replacement behavior is complete.
- Historical changelogs may remain, but current docs must not present AES-SIV as mandatory.
- Non-SIV Bouncy Castle usage must not be counted as AES-SIV removal debt.
