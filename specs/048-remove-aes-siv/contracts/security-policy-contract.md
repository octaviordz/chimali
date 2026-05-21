# Contract: Security Policy After AES-SIV Removal

## Purpose

Defines the governance contract that plans, reviews, and implementations must satisfy once AES-256-SIV is removed as a mandatory primitive.

## Required Policy Outcomes

1. General recoverable sensitive values are protected with authenticated encryption.
2. Exact-match searchable sensitive metadata uses deterministic keyed lookup tokens.
3. Lookup tokens are not decryptable metadata values.
4. AES-256-GCM is never made deterministic through fixed, reused, predictable, or plaintext-derived nonces.
5. Cryptographic purposes are separated for lookup tokens, value encryption, database encryption, and signing material.
6. Key wrapping and master-key boundary protection use platform-backed AES-GCM/AEAD with unique nonces and associated data.
7. SQLCipher remains a database file-level protection layer, not a blanket substitute for field-level value encryption where required.
8. Bouncy Castle provider removal is not implied by AES-SIV removal.

## Review Checklist

- The constitution contains no MUST-level AES-SIV requirement.
- The SQL/database guidance no longer says searchable data requires AES-SIV deterministic ciphertext.
- Any SQLCipher-only display exception names the field class and lookup limitation.
- Any new exact-match searchable metadata field defines a domain separator and canonicalization rule.
- Any encrypted metadata value defines associated data and tamper handling.
- Any key-wrapping path defines nonce uniqueness, associated data, and platform-backed AEAD usage.
- Any remaining Bouncy Castle reference is classified as non-SIV, historical, or separate migration debt.

## Rejection Conditions

A plan or implementation must be rejected if it:

- uses AES-GCM with nonce reuse to obtain deterministic ciphertext,
- keeps AES-SIV as the required key-wrapping primitive,
- stores sensitive exact-match metadata as raw plaintext without a documented exception,
- removes AES-SIV code before the constitution is amended,
- claims Bouncy Castle has been removed while non-SIV provider paths remain, or
- rewrites or prunes event-sourced history as part of metadata migration.
