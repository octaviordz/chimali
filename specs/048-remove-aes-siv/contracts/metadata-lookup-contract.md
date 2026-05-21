# Contract: Searchable Metadata Lookup

## Purpose

Defines the observable behavior required for searchable metadata after AES-SIV removal.

## Exact-Match Lookup

Given a metadata field that requires exact-match lookup:

1. The caller provides a canonical metadata value.
2. The system derives a deterministic keyed lookup token using the field's domain and version.
3. The storage query matches records by lookup token.
4. The system returns the same record set that the legacy exact-match query returned for supported records.

## Encrypted Metadata Hydration

Given a stored record with encrypted metadata:

1. The system decrypts the metadata using authenticated encryption.
2. The associated data binds the metadata to the record and schema context.
3. If authentication fails, the record is treated as corrupted and is not accepted as valid.
4. Decrypted sensitive values are exposed only to the domain layer paths that need them.

## Migration Compatibility

During migration:

1. Legacy records remain readable.
2. Migrated records are queried through lookup tokens.
3. Re-running migration does not duplicate records or change domain identity.
4. Interrupted migration can resume from the last safe point.

## Partial Text Search

Partial text search is not provided by exact-match lookup tokens.

Allowed outcomes:

- continue existing partial search over fields explicitly classified as SQLCipher-only display fields.

Disallowed outcomes:

- claim HMAC lookup tokens support substring search,
- derive deterministic GCM ciphertext for substring search,
- narrow or drop existing partial search without a new approved scope change,
- silently store sensitive partial-search metadata as raw plaintext without a documented exception.

## Verification Requirements

- Same input value and domain returns the same token.
- Same input value with different domains returns different tokens.
- Different values produce different lookup results except for cryptographic collision events.
- Existing seeded records remain discoverable after migration.
- Existing partial text search flows continue over classified SQLCipher-only display fields.
- Tampered encrypted metadata is rejected.
