# Vault legacy fixtures and payload cleanup test map

- **Scope:** SEC065-VAULT-002 and T005 source-inventory completion
- **Classifications:** CLS-VAULT-001/002/003
- **Requirements:** FR-002/003/004/010; NFR-SEC-041; T005/T006/T012
- **Data:** Test-only synthetic fixtures; no user-provided or production vault records.

## Legacy-format JSON fixtures

`vault-legacy-v1/password.json`, `card.json` and `note.json` contain plaintext JSON fields with clearly synthetic test values, including test passwords, card-shaped numbers, labels and note content. They are loaded only by legacy codec/crypto/detail ownership tests to characterize historical serialization and decode behavior. These files are Relevant because they are literal sensitive-category fixtures in source control; they are not product storage or a heap-retention positive control.

## Payload cleanup tests

`PasswordCryptoTest`, `CreditCardCryptoTest` and `SecureNoteCryptoTest` create synthetic mutable payload arrays, call each payload's `clearMemory`, and assert the original arrays are zero. They exercise test/model cleanup only; they do not establish crypto-provider, UI, database, or heap erasure.

## Verification and limits

The current full Vault unit suite passed 98 tests across 23 XML reports, with zero failures/errors/skips; KtLint passed. The fixture tests and resources are mapped as distinct owners in `source-inventory.json`. The suite is from the dirty exploratory worktree, not a frozen product candidate. Immutable source literals, JVM class/resource copies, runtime UI, provider and heap behavior remain outside the claim.
