# Research: Remove AES-256-SIV

## Decision 1: Amend the Constitution Before Code Removal

**Decision**: Update the constitution first so AES-256-SIV is no longer mandatory. Replace it with a requirement for deterministic keyed lookup tokens for exact-match searchable metadata, authenticated encrypted values for recoverable metadata, and platform-backed AES-GCM/AEAD for key wrapping.

**Rationale**: The current constitution explicitly requires AES-256-SIV for searchable encrypted metadata and key wrapping. Removing code first would violate governance. The feature is primarily a policy migration, so the policy must lead implementation.

**Alternatives considered**:

- Remove AES-SIV code and update policy later: rejected because constitution supremacy blocks it.
- Keep AES-SIV as optional only: rejected for this feature because the main goal is removal of AES-SIV-specific surface area.
- Replace the policy with "AES-GCM everywhere": rejected because AES-GCM is not deterministic and must not be forced into deterministic operation for lookup.

## Decision 2: Use Keyed Lookup Tokens for Exact-Match Search

**Decision**: Use deterministic keyed lookup tokens for exact-match searchable metadata, with explicit domain separation per metadata type and purpose.

**Rationale**: Exact-match lookup needs deterministic equality, not decryptable deterministic ciphertext. A keyed token preserves lookup behavior while avoiding a custom AES-SIV implementation. Equality and frequency leakage remain, but AES-SIV deterministic ciphertext has the same equality/frequency leakage.

**Alternatives considered**:

- AES-GCM with fixed or plaintext-derived nonce: rejected as unsafe and explicitly prohibited by this feature.
- AES-GCM-SIV: rejected because it still requires non-platform library support and is not needed for equality-only indexes.
- Plain SQLCipher-protected columns for all lookup fields: rejected for sensitive exact-match metadata unless explicitly documented as a display-only exception.

## Decision 3: Encrypt Recoverable Metadata Separately

**Decision**: Store recoverable sensitive metadata as authenticated encrypted values using AES-256-GCM with a unique nonce per encryption.

**Rationale**: Lookup tokens are not reversible and cannot hydrate domain models. Recoverable metadata still needs authenticated encryption. AES-GCM is already the project-approved and platform-aligned value encryption mode.

**Alternatives considered**:

- Store metadata only as lookup tokens: rejected because domain models still need canonical values for authenticator and UI flows.
- Store all metadata only in SQLCipher tables: rejected for data classified by the constitution as requiring individual field/value encryption.
- Keep SIV for recoverable metadata: rejected because it keeps the primitive this feature removes.

## Decision 4: Preserve Partial Text Search Through SQLCipher Display Fields

**Decision**: Preserve existing partial text search over explicitly classified SQLCipher-only display fields. Exact-match lookup tokens remain equality-only and must not be represented as substring-search support.

**Rationale**: Deterministic keyed tokens support equality, not substring search. Building privacy-preserving substring search would be a separate, complex searchable-encryption feature. The current UI uses partial matching on display names, so preserving the UX requires a documented SQLCipher-only display-field classification rather than silent narrowing.

**Alternatives considered**:

- Tokenize all substrings or n-grams: rejected as scope expansion with substantial leakage and complexity.
- Decrypt and scan all records for every search: rejected as the default because it does not fit the 10,000-record performance target.
- Drop partial search silently: rejected because it would be a user-visible regression.

## Decision 5: Replace AES-SIV Key Wrapping With Platform-Backed AEAD

**Decision**: Replace the former AES-SIV key-wrapping rule with platform-backed AES-GCM/AEAD key wrapping using unique nonces and associated data.

**Rationale**: The Android platform and current DataStore direction already align with AES-GCM wrappers backed by Android Keystore. Keeping AES-SIV only for key wrapping would preserve the primitive this feature is trying to remove and would continue to require software-only handling for a policy path that does not appear to be actively used by `SivEncryptionManager`.

**Alternatives considered**:

- Keep AES-SIV only for key wrapping: rejected because it leaves an AES-SIV policy exception and active maintenance burden.
- Use AES-KW/RFC 3394: rejected for this feature because it is not the current platform-backed app pattern and would introduce another migration target.
- Remove key-wrapping guidance entirely: rejected because the constitution needs an explicit replacement for master-key boundary protection.

## Decision 6: Keep Full Bouncy Castle Removal Out of Scope

**Decision**: This feature removes AES-SIV-specific code and policy only. Bouncy Castle remains for HDK P-256 math, FIDO2 signing/key reconstruction, Ed25519, ML-DSA-65, and ASN.1 parsing until a separate provider migration exists.

**Rationale**: The prior analysis found AES-SIV is only one Bouncy Castle consumer. Claiming this feature removes Bouncy Castle would be technically false and would mix unrelated cryptographic migrations.

**Alternatives considered**:

- Remove Bouncy Castle entirely in the same feature: rejected as too broad and risky.
- Leave stale comments saying Bouncy Castle is retained only for AES-SIV: rejected because it misleads future planning.

## Decision 7: Stage Database Compatibility

**Decision**: Add replacement lookup/encrypted metadata fields and migrate data before removing or deprecating old plaintext lookup columns. Any physical column drops must be staged according to SQLDelight migration policy.

**Rationale**: Existing credentials must remain usable, event history must remain immutable, and SQL schema changes must be backward-compatible. The migration must support interruption and re-run safely.

**Alternatives considered**:

- Big-bang schema replacement: rejected due to data-loss and rollback risk.
- Re-register credentials: rejected because it violates the feature assumption that users should not re-register solely due to AES-SIV removal.
