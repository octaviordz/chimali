# Research: Current Local HDK Profile

**Date**: 2026-10-14  
**Related plan**: [plan.md](plan.md)  
**Method**: Two read-only research agents examined the cryptographic choices and current implementation/build boundaries. The primary agent reconciled the findings against the clarified specification. No builds or tests were executed.

## 1. Draft child relation and local scope

**Decision**: Adapt the scalar mapping and child-key relation in Section 7 of the pinned replacement draft through one named local profile. The draft's `bk` is a per-issuance X25519 `shared_secret`; the local profile substitutes a deterministic RFC 5869 secret derived from the wallet PRK and full credential bytes. The local parent is deterministically provisioned using RFC 9180. FIDO2 uses child index zero. The low-level child primitive accepts unsigned indices over the full domain; no tree/path/fold API is needed.

**Rationale**: The required identity enters the local secret rather than being compressed to four bytes. Current operations can use a single leaf and one validated scalar multiplication. This substitution and parent policy form a custom local protocol; they are documented separately from the draft's issuance semantics and do not inherit its interoperability or security analysis.

**Alternatives considered**: Retaining the two-level `FID2` path or old salt evolution would preserve debt and shortened-identity collision behavior. Generating an issuer or X25519 exchange locally would add an excluded protocol. A registry of supported old implementations conflicts with the clean-install scope.

**Reference**: [draft-dijkhuis-hdk-00](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html), sections 3 and 7. See [the byte contract](contracts/local-key-derivation.md).

## 2. Root and parent derivation

**Decision**: Use the complete 64-byte BIP39 result as transient input to HKDF-Extract-SHA256 with the fixed public salt `chimali-local-key-derivation-v1`. The 32-byte result is the wallet root PRK and the owned-copy output of `MasterSeedProvider.getMasterSeed`. Expand it with the explicit parent label, then use RFC 9180 section 7.1.3 deterministic P-256 `DeriveKeyPair`.

**Rationale**: The provider's public seed length remains 32 bytes while its precise semantics become documented. Published labeled key generation validates non-zero parent candidates. It preserves a useful portion of existing standards code without retaining encapsulation/decapsulation capabilities.

**Alternatives considered**: The old modulo reduction can produce zero and is an application convention. Random parents would make reconstruction require additional private backup material. Expanding a truncated seed or preserving old labels adds no value under the authorized breaking change.

**Implementation finding**: Current `DhKem.deriveKeyPair` stops at counter 254. The RFC permits all 256 candidate counters, 0–255. Retain exact standard HPKE/KEM labels in the extracted key generator and fix the bound; remove old remote operations.

**References**: [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html), [RFC 9180 §7.1.3](https://www.rfc-editor.org/rfc/rfc9180.html#section-7.1.3), [BIP39](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).

## 3. Independent Ed25519 and ML-DSA seeds

**Decision**: Expand the same PRK with separate algorithm-specific credential labels to obtain 32-byte Ed25519 and ML-DSA-65 seeds. Import ML-DSA seed material directly with the pinned BC 1.80 seed specification and obtain its corresponding public key. Remove `getPqChildSeed`, the old PQ cache, and `DeterministicSecureRandom`.

**Rationale**: Current Bouncy Castle already exposes the deterministic seed path, so no library upgrade or custom entropy expander is necessary. Independent labels separate purposes; they do not protect against compromise of the shared root.

**Alternatives considered**: Keeping the old salt-based PQ branch couples the new implementation to retired APIs. Retaining the custom SHA-256 counter generator would keep misleading DRBG claims and unnecessary secret storage. Reusing P-256 blinding for another curve would be incorrect.

**References**: [RFC 8032](https://www.rfc-editor.org/rfc/rfc8032.html), [FIPS 204](https://csrc.nist.gov/pubs/fips/204/final), BC 1.80 [seed specification](https://raw.githubusercontent.com/bcgit/bc-java/r1rv80/prov/src/main/java/org/bouncycastle/jcajce/spec/MLDSAPrivateKeySpec.java) and [private-key public-key accessor](https://raw.githubusercontent.com/bcgit/bc-java/r1rv80/prov/src/main/java/org/bouncycastle/jcajce/interfaces/MLDSAPrivateKey.java).

## 4. Identity and current persistence

**Decision**: Use decoded credential bytes everywhere, validate length 16–1023, and canonicalize external Base64URL encodings. Persist one explicit current profile identifier with algorithm and credential bytes. Add algorithm/profile to active registered events and state; carry profile and registered public-key encoding in selection summaries.

**Rationale**: Current SQL already stores algorithm, but command/event/state omit it. Durable profile identity distinguishes the local policy from the signing algorithm without requiring alternate implementations. Selection remains a metadata read; only the selected key is derived and checked.

**Alternatives considered**: A compile-time-only profile would leave serialized credentials without part of their reconstruction identity. A parent registry, persisted index, or profile factory is unnecessary. Old-record conversion and staged alias migration are explicitly excluded by the user.

**Finding**: The repository's active FIDO2 event and snapshot stores are bound to FIDO2-qualified storage. Update that real path; do not add another parallel passkey store.

## 5. Fail-closed registered-key handling

**Decision**: Remove `CorruptedKeyRepairWorker`, its repository scheduling and dependency, DAO `updatePublicKey`, and the SQL update query. Decode the current generation result for registration; compare the selected derived public key with stored encoding before signing.

**Rationale**: The worker overwrites a local public key without changing the relying party's registration. It contradicts FR-004 and adds asynchronous ownership/race complexity. Public generation results need no private-key alias, so remove the alias property/column and obsolete key-existence/no-op deletion facade.

**Alternative considered**: Keeping a repair fallback would conceal malformed current records and retain the illusion of separately stored Keystore credential keys.

## 6. Secret ownership and mnemonic processing

**Decision**: Provider caches own their arrays and never lend them through getters. Return copies under the same synchronization used to publish/clear caches. Consumers erase copies in `finally`. Publish caches only after all provisioning succeeds. The normative owner/generation table in [the lifecycle contract](contracts/credential-lifecycle.md#session-ownership-and-termination) defines overlapping foreground/HID operations, last-owner release, disconnect, replacement fencing, all three mandatory memory levels and lazy same-wallet reinitialization.

The BIP39 generation/derivation API must use mutable character storage. Use public dictionary entries to construct owned character buffers, mutable UTF-8 encoding, and Android ICU NFKD normalization into a bounded mutable `Appendable`. The existing debug reveal/import UI must own and erase its mnemonic buffers; any required immutable display/clipboard representation is an audited UI boundary, never crypto input storage.

**Rationale**: The current getters return cached arrays, so caller erasure corrupts shared state. Current crypto code creates mnemonic `String` and `List<String>` values, which cannot be passed off as unavoidable provider internals under I.5. The platform normalization API has a mutable destination form.

**Alternatives considered**: Shared-array return, success-only cleanup, and treating immutable private mnemonic text as a UI exception are incompatible with the constitution. Unlimited caching and fictional erasure of JCA/BC internals are also rejected.

**References**: [Android ICU Normalizer2](https://developer.android.com/reference/android/icu/text/Normalizer2), mutable `normalize(CharSequence, Appendable)` overload. Source ownership inventory is in [analysis.md](analysis.md) and [security-review.md](security-review.md).

## 7. Constitutional treatment and security review

**Decision**: Prepare the concrete 3.0.0 amendment before production replacement. It changes Principle II and related fixed-KDF-salt wording, and records the clean-install scope. Preserve the mandatory mutable application-owned secret policy. Record the exact local composition and provider limitations in an engineering security review before changing production derivation; formal review is required before activation if the composition is classified as a custom protocol under ES-V.5.

**Rationale**: The user authorized the breaking change and constitution work. Published primitives and independent fixtures are useful evidence but do not themselves establish approval, assurance or proof for the composition. Fixed public HKDF salts need a precise rule distinct from fresh nonces and IVs.

**Alternatives considered**: Leaving Principle II untouched, silently treating application-specific HKDF policy as an issuance standard, or marking the research as formal security signoff would be inaccurate.

**Output**: [constitution-amendment.md](constitution-amendment.md) and [security-review.md](security-review.md). This is a concrete implementation gate, not an unresolved product question.

## 8. Build, discovery and coverage

**Decision**: Keep current dependency versions. Use configured `commonTest` and `androidHostTest`, with `androidDeviceTest` for platform/Compose behavior; move/update the existing mnemonic UI tests from unconfigured `androidTest`. Host execution uses `testAndroidHostTest`. Pin JaCoCo 0.8.15 and discover real compilation outputs; enforce affected Tier 1 statement/branch ≥40%, each affected Tier 2 statement ≥20%, and repository aggregate ≥20%, reporting Tier 2 branches. Merge host/device data where needed without duplicate class denominators. Exclude classes discovered from generated SQLDelight Kotlin sources from coverage denominators; exercise generated queries through SQL integration tests so module statement floors measure maintained source.

**Findings**: Gradle 9.6.0 / AGP 9.4.1 / Kotlin 2.3.21 / JVM 17 / SDK 37 / min 28 / target 35 are recorded in current configuration. Root applies Detekt/Ktlint. Older crypto tests under `core/security/src/test` are not configured host evidence. `tools/vault-memory-coverage.init.gradle` measures only vault sources; it cannot demonstrate HDK coverage. The connected-device task must be discovered from this module's task list before execution; the quickstart gives that procedure.

**Rationale**: Make verification executable and truthful. Separate algorithm vectors from implementation self-agreement, and keep full-module/repository floors distinct from a scoped crypto report. JaCoCo instrumentation is test tooling, not a production dependency.

**References**: Repository Gradle files and `tools/local-ci.ps1`; [Gradle JaCoCo documentation](https://docs.gradle.org/current/userguide/jacoco_plugin.html) and [JaCoCo changes](https://www.jacoco.org/jacoco/trunk/doc/changes.html). The consulted Gradle web manual describes a newer release; use it for plugin concepts, not proof of the repository's exact task model.

## Resolved Research Inventory

| Design question | Resolution |
| --- | --- |
| Local secret source and identity | Named RFC 5869 profile with full decoded identifier and length framing. |
| Parent construction | Exact RFC 9180 P-256 deterministic key generation. |
| Leaf index / stored paths | Zero for local FIDO2; no path/index persistence. |
| Other algorithms | Independent 32-byte seeds; direct BC ML-DSA seed import. |
| Current credential reconstruction | Explicit algorithm/profile through SQL, events, snapshots and summaries. |
| Old data handling | Clean installation only; no legacy compatibility work. |
| Secret representation | Owned mutable buffers, copied getters, mutable mnemonic processing and audited provider boundaries. |
| Review requirement | Concrete pre-implementation engineering assessment and conditional formal-review activation gate. |
| Validation mechanics | Configured host sources, independent fixtures, device task discovery, actual coverage setup. |

All technical choices needed for the design artifacts are resolved. Implementation gates and measurements remain to be executed during the feature work.
