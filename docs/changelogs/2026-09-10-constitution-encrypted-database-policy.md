# 2026-09-10 — Constitution Encrypted-Database Policy Update

## Summary

Updated `.specify/memory/constitution.md` from version **1.1.1** to **1.1.2**. The
constitution now defines the security properties required for encrypted SQLite storage
without prescribing a particular SQLite3MultipleCiphers cipher implementation.

## Changes

- Removed the remaining named legacy database-cipher and CBC terminology from the constitution.
- Kept **SQLite3MultipleCiphers** as the required encrypted database layer.
- Required an explicitly configured authenticated-encryption cipher providing both
  confidentiality and integrity.
- Prohibited silent fallback to plaintext or unauthenticated encryption.
- Kept 256-bit database keys derived with `PBKDF2-HMAC-SHA512` from the device master key.
- Required integration tests to verify that the approved encryption configuration is active.
- Kept AES-256-GCM protection for individual credential blobs before database insertion.

## Security Rationale

The policy is cipher-agnostic at the constitution level while remaining security-specific:
it mandates authenticated encryption, key strength, derivation, explicit configuration,
and fail-secure verification. Concrete cipher selection remains an implementation and
security-review decision, allowing the database library's supported modern options to
evolve without another constitutional amendment.

## Verification

- Confirmed no obsolete provider, CBC, or named-cipher references remain in the constitution.
- Confirmed no unresolved constitution template placeholders remain.
- Confirmed the constitution sync report and version line both identify version 1.1.2.
- Confirmed the documentation diff passes whitespace validation.
