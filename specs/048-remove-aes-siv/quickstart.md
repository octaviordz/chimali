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

```powershell
rg -n "AES-256-SIV|AES256_SIV|AesSiv|SivEncryptionManager|EncryptedMetadataIndexService" .specify docs core feature app specs
```

```powershell
rg -n "fixed nonce|reused nonce|plaintext-derived nonce|deterministic GCM" .specify docs core feature app specs
```

```powershell
rg -n "key wrapping|key-wrapping|master key|master-key|associated data|AAD" .specify docs core feature app specs
```

```powershell
.\tools\local-ci.ps1
```

## Expected Final State

- The constitution no longer mandates AES-SIV.
- Exact-match lookup uses deterministic keyed lookup tokens.
- Partial text search remains on explicitly classified SQLCipher-only display fields.
- Recoverable metadata values use authenticated encryption with unique nonces.
- Key wrapping uses platform-backed AES-GCM/AEAD with unique nonces and associated data.
- AES-SIV production APIs and services are removed or deprecated as compatibility-only.
- Bouncy Castle remains only for non-SIV paths unless another feature removes it.
- Local CI passes.
