# Data Model: Current Local Credential Keys

**Date**: 2026-10-14  
**Scope**: Clean installation and newly created credentials. Exact derivation bytes are in [the local contract](contracts/local-key-derivation.md).

## Wallet recovery material

| Field / value | Representation | Owner / persistence |
| --- | --- | --- |
| English mnemonic | Owned `CharArray` or bounded audited mutable equivalent | Temporary generator/provider/UI owner; erase on replacement and operation/session end. |
| Encrypted mnemonic | Existing Base64 ciphertext in Proto DataStore `encryptedWalletSeed` | Persistent ciphertext; never raw mnemonic text. |
| Full BIP39 result `S` | 64-byte owned array | Initialization operation only; erase after extraction/provisioning. |
| Root `PRK` | 32-byte owned array | Provider session cache; public getter returns a separate owned copy. |
| Parent key pair | 32-byte private scalar and canonical 65-byte public point | Provider session cache or reconstructed operation; consumers receive copies and erase private bytes. |

No new persistent private-key table or root plaintext field exists. Provider initialization uses Proto DataStore before opening seed-dependent SQL databases, avoiding recursive database bootstrap. Read the encrypted mnemonic directly; wallet initialization must not depend on the shared historical `migrationCompleted` flag. Stop setting wallet-specific legacy migration state while preserving unrelated preference workflows that own those fields.

### Provider state and ownership transitions

1. **Uninitialized**: no secret cache. A request starts serialized initialization.
2. **Initializing**: operation owns mnemonic, BIP39 seed, PRK and candidate parent. Failure/cancellation erases all unpublished material.
3. **Ready for current wallet session**: publish PRK and parent atomically after all provisioning succeeds. Copy getters run under the same cache synchronization used by clearing; no cached array is returned directly.
4. **Last owner ending / replacement / required memory pressure**: cancel affected session-authorized operations, erase all provider-owned secret caches and release handles; return to uninitialized. Operation copies remain their owners' responsibility and are erased in their own `finally` blocks. Replacement and required memory callbacks fence the generation, reject new signing admission and discard stale in-flight results under the lifecycle contract's provider-interruption limits.

At most one PRK and one parent pair are cached. No PQ cache or unbounded key map exists. The exact operation-owner, foreground/HID overlap, cancellation, memory-level and lazy reinitialization policy is normative in [the lifecycle contract](contracts/credential-lifecycle.md#session-ownership-and-termination). This is not a new login or wallet-lock feature.

`getMasterSeed()` retains its 32-byte output shape but now documents an owned copy of the root PRK. `getDeviceKeyPair()` returns an owned key-pair copy. Remove `getPqChildSeed()`. Mnemonic getters return an owned character buffer rather than secret `List<String>`. Import consumes and erases its provided buffer under the existing interaction.

## CredentialKeyDescriptor

| Field | Validation / meaning |
| --- | --- |
| `credentialId` | Full decoded bytes, length 16–1023; canonical external encoding. |
| `coseAlgorithm` | One of the application's supported algorithm cases. |
| `derivationProfile` | Exactly the one current profile identifier assigned to that algorithm. |

No path, child salt, variable leaf index, parent registry or legacy version selector is stored. The local P-256 leaf index is the contract constant zero. Distinct full IDs alter HKDF context; collisions in the removed four-byte mapping do not collapse the identity input.

The descriptor is public metadata. It must not retain private scalar, seed, PRK or blinding factor. Identity comparisons and public-key matching use the same canonical encoding and appropriate constant-time comparison.

## Current credential record

Retain existing credential ID, RP/user association, created/last-used timestamps, stored public key, COSE algorithm, sign count, protection policy and display metadata. Add `derivationProfile` to the domain model and `derivation_profile TEXT NOT NULL` to the fresh SQL table and insert/query mappings. Supply it explicitly on every registration insert; no old-format default is required.

Remove `privateKeyAlias` / `private_key_alias`, `Fido2KeyPair.alias` and `credentialAlias`: they describe no stored asymmetric key and duplicate the explicit descriptor. Keep ciphertext and lookup-token encodings unchanged. The T136 ownership correction also changes the directly related relying-party ID/name and consent RP columns to BLOBs so generated rows expose mutable bytes rather than retained plaintext Strings.

The public-key field retains the current stored Base64 encoding of X.509/JCA public-key bytes. Conversion from generation's raw/native encoding happens once at a named adapter. A public-key mismatch is an error; no public-key update/repair operation remains.

These edits update creation of the current schema. User authorization explicitly excludes old databases and staged upgrade conversion; no `.sqm` migration or reset handler is introduced for this feature. No FIDO2 SQLDelight migration-history files remain; the user explicitly excluded upgrade compatibility and requested their removal.

## Candidate summary

`CredentialSummary` retains current ID, RP, credential bytes, last-used timestamp, algorithm and protection policy. Add the current profile identifier and stored public-key encoding. Listing/mapping performs no cryptographic derivation. Only the selected summary is decoded/checked at signing, using the public key already calculated for that operation.

## Registration event, aggregate state and snapshot

Update `PasskeyCommand.Register`, `PasskeyEvent.Registered`, `PasskeyState` and the decider with:

- `coseAlgorithm`;
- `derivationProfile`.

Commands require explicit values. Additive serialized event/state fields use invalid sentinels (`0` and empty text) as their default shape, and registered-state validation rejects invalid current values. They are not a default usable profile or a legacy conversion mechanism. Newly emitted registration events always supply the current values, and serializer settings must preserve the identity fields explicitly.

Replay copies algorithm/profile into materialized state. Snapshot and replay reconstruct the same descriptor and public key. Keep the existing registered/authenticated/deleted states, counters, sequence numbers and 20-event snapshot cadence. No old-event interpreter, invalidation event or extra aggregate is introduced.

Secrets never appear in these payloads. Current passkey event/snapshot repositories use their already bound FIDO2 store and own/wipe encryption keys and mutable plaintext serialization buffers; do not create a second storage path.

Registration uses explicit event-first and projection-second SQL transactions on the existing FIDO2 database. The projection transaction atomically includes all related prior-record deletions and the new insertion. Failure/cancellation rolls that entire projection phase back, preserving prior projection records; an already committed event remains append-only and reconstructs its recorded descriptor/public key. Success requires both commits. The lifecycle contract defines failure outcomes; no general transaction coordinator is added.

## Cryptographic operation outputs

- **GeneratedCredentialPublicKey**: descriptor and public-key bytes only. Replace obsolete alias-based result fields.
- **HdkChild**: canonical child public key and operation-owned 32-byte factor for P-256 signing; no child salt. The caller must erase the factor after use. Prefer a closeable/scoped result so cleanup survives failure/cancellation.
- **Parent key operation copy**: own private bytes and release provider/JCA handles after the call; caches hold separate storage.
- **Credential seed**: 32-byte local secret, Ed25519 seed or ML-DSA `xi`, owned by the current operation and never cached/persisted per credential.

## Mutable mnemonic presentation

Existing DevTools state replaces `mnemonicWords: List<String>` with a mutable owner suitable for the current reveal/import lifetime. Erase replaced/hidden/disposed contents, including on cancellation and ViewModel disposal. Rendering, clipboard and QR boundaries create only their minimum permitted platform text and release it immediately after the binding/call; the owner remains mutable. Do not introduce secret saved-state or immutable draft/baseline caches.

Public BIP39 dictionary strings are not private mnemonic storage. Convert selected indices into mutable output without joining private words into a `String`; wipe entropy/checksum/index work arrays. Normalize private character input through ICU's mutable destination API and encode through mutable UTF-8 buffers. Keep normalization and checksum validation separate from display adaptation.

## RP, consent and candidate ownership (T136)

The common `RelyingParty`, `UserConsentRecord` and `CredentialSummary` models retain RP associations in owned `SensitiveText` buffers; relying-party names have an independent owner too. Borrowed views are valid only while the containing model remains live. Copies own distinct buffers. Validation failure erases transferred model owners, and diagnostic formatting is redacted. Current registration/assertion lookup and policy calls borrow mutable request text through narrow repository/service entry points; they do not construct `RpId`/`UserId` Strings for those operations. The global value-object APIs remain available to their existing callers.

Fresh `relying_party.id`, `relying_party.name` and `user_consent_record.rp_id` columns are BLOBs. Their UTF-8 byte values, relationships and lookup-token inputs are unchanged. Mapping consumes row bytes, transfers independent text owners to its result, and erases partially decoded text and row buffers on failure. Batch consumers erase skipped/untransferred rows and results. DAO bind arrays and lookup tokens remain operation-owned. Callers erase returned models at their terminal boundary; registration erases its RP/consent/user-encoding work on success, failure and cancellation.

The attestation sidecar `ClientData` also owns mutable origin text. `MakeCredentialResult` transfers its credential and origin owners together and provides terminal erasure. A registration UI success state retains a separate credential copy. CTAP response encoding and rejected/stale result handling erase the transport result without clearing the still-authorized UI copy. Candidate lookup transfers independently owned summaries; the assertion handler/use case erases its batch after selection/signing or terminal failure. Candidate listing still performs no key derivation.

Compatibility model constructors/getters and generic JSON serializers preserve existing public shapes. They are not production mutable-persistence adapters or an erasure exception. Production RP/consent persistence uses mutable UTF-8 BLOB bindings; passkey events/snapshots retain their existing mutable JSON route and unchanged wire fields. Audited rendering still requires minimum platform text. Runtime, SQL driver, native SQLite, generic serializer and platform-internal copies are not claimed physically erasable.
