# Analysis: Replacement HDK Draft and Chimali Migration Impact

**Date**: 2026-10-14  
**Branch**: `codex/055-migrate-hdk-draft`  
**Related specification**: [spec.md](spec.md)  
**Method**: Read both published drafts, Datatracker replacement metadata, the current constitution, cryptographic implementations, production callers, persistence and reconstruction paths, and existing test sources. This is a static analysis; no cryptographic or application tests were executed.

## Decision and scope

The user selected a local key derivation and documentation migration and requested an OpenID4VCI investigation in `docs/backlog`. During clarification on 2026-10-14, the user authorized breaking changes, requested technical debt reduction, and explicitly scoped acceptance to a clean new installation. The target is one current local P-256 construction, removal of obsolete old-draft code, and coordinated documentation updates. Upgrade handling, old-record invalidation, re-registration workflows, and legacy data conversion are outside this feature.

Chimali implements a local FIDO2 authenticator. Searches of `core/` and `feature/` found no OpenID4VCI issuance implementation or X25519 exchange. Applying the new child-key formula locally requires an explicitly documented Chimali adaptation. Full remote issuance belongs to the separate investigation.

## Primary references and draft changes

- [Replacement draft, version 00](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html)
- [Replacement Datatracker record](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/)
- [Older draft, version 06](https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-06.html)
- [Older Datatracker record](https://datatracker.ietf.org/doc/draft-dijkhuis-cfrg-hdkeys/06/)

Datatracker records the replacement explicitly. The new document describes a substantial rewrite focused on P-256 credential issuance through OpenID4VCI. It establishes a per-request X25519 secret and derives child keys with:

```text
H(msg) = (OS2IP(SHA-256(msg)) mod (n - 1)) + 1
context = SEC1-uncompressed(parent_public_key) || I2OSP(index, 4)
factor = H(shared_secret || context)
child_public_key = parent_public_key * factor
child_private_key = parent_private_key * factor mod n
```

Here `n` is the P-256 group order. Section 3 requires canonical 65-byte parent encoding and public-key validation; section 7 uses unsigned 32-bit indices and non-zero factors. Sections 7.1 and 8 describe hierarchy and child operations. Sections 4–6 and 9 cover assurance, issuance, and capability metadata. Sections 10–12 discuss interoperability, security, and privacy. The old salt/context/handle abstraction is absent. These are draft-specific facts; local-profile recommendations below are deductions from Chimali's design.

## Comparison with the implemented construction

| Area | Observed Chimali behavior | Migration implication |
| --- | --- | --- |
| Context | `HDK-ECDH-P256-v1` plus a four-byte index. | Replace the retired context with the replacement construction's parent-key context. |
| Factor | Salt is mapped to a blind key, then `bk || 0x00 || ctx` is mapped through RFC 9380 hash-to-field with `ECDH Key Blind`. | Different scalar derivation produces different registered keys. Replacing it globally invalidates old passkeys. |
| Salt | Each step computes `SHA-256(salt || context)` and returns a new salt. | A local secret policy must be specified separately; old salt evolution cannot simply be relabeled. |
| Arithmetic | Multiplicative P-256 public/private key relation. | Reuse validated arithmetic through the single current contract. |
| Remote operations | P-256 KEM keys derived from salt and decapsulation of issuer handles through `DhKem`. | Remove unused retired capabilities; implementing a new exchange is deferred to the investigation. |
| Signing | Software derives a temporary child scalar and uses Bouncy Castle `SHA256withECDSA`. | Propagate profile selection through public-key generation, self-attestation, and signing. |
| Root | Stable P-256 parent derived from BIP39 material; private bytes are cached in software. | Encrypted recovery storage does not make the signing scalar non-exportable. |
| Other algorithms | Separate Ed25519 hash derivation and salt-based ML-DSA seed policy. | Keep these algorithms supported through independent policies; old key outputs need not constrain cleanup. |

## Source evidence and affected boundaries

Paths are repository-relative. Line numbers identify the reviewed revision and will move during implementation.

| Source | Evidence and required planning coverage |
| --- | --- |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/api/HdkManager.kt` | Exposes path/salt derivation, blinded ECDH, and remote KEM acceptance. Replace obsolete contracts and remove functions without a demonstrated current use. |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkEcdhP256.kt:37` | Application label, `createContext` at 60, `deriveSalt` at 72, `hdk` at 93, and `deriveHdk` at 155 define the old contract. Replace or remove these as required by the current construction; no parallel legacy implementation is required. |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/MultiplicativeBlinding.kt:26` and `HashToScalar.kt:86` in the same directory | Two field mappings and XMD expansion determine old key identity. Remove old-only mapping and blind-key stages after checking remaining uses; retain useful validated arithmetic without a compatibility facade. |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt:91` | Point decoding delegates to Bouncy Castle. Scalar decoding at 108 only converts bytes to an unsigned integer. Establish accepted encoding, finite-point, curve, length, and scalar-range checks at the new boundary. Comments do not establish validation. |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/DhKem.kt` | P-256 HPKE-style KEM helper. No production callers of the remote HDK methods were found outside their implementation. Confirm references before retirement or isolation. |
| `core/security/src/commonMain/kotlin/com/chimali/core/security/api/HdkTypes.kt` | `HdkResult` carries public key, child salt, and combined factor. Remove old-only result fields and define the current contract without preserving obsolete result behavior. |
| `core/security/src/androidMain/kotlin/com/chimali/core/security/di/SecurityModule.kt` | Component scan binds `HdkEcdhP256` to `HdkManager`. Wire the single current implementation and remove obsolete bindings. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt:97` | BIP39 yields 64 bytes. The HDK seed is its first 32 bytes, while the parent uses all 64. Document the updated recovery policy; retaining these old outputs is optional. |
| Same provider, lines 316 and 362 | Parent label is `chimali_device_key_v1`. PQ seed uses `HMAC-SHA512("chimali_pq_seed_v1", SHA-256(masterSeed32 || "PQ_ML-DSA_Branch"))`. Decouple PQ from the obsolete HDK salt API and document its independent policy. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt:101` | Generation and `sign` at 381 independently derive the credential key. Profile identity must also reach public-key retrieval and warm-up. |
| Same service, line 655 | Path is `[FID2 namespace index, first four bytes of SHA-256(credentialId.toByteArray())]`. Bind the full identifier in the new local profile. |
| `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/CredentialId.kt` | `toByteArray()` decodes Base64URL; `fromEncoded()` removes padding. This contradicts the service's UTF-8 comment. Define canonical identity once for updated derivation and fix stale comments. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCase.kt:262` | Generates a key, retrieves a public key again, and signs self-attestation at 372. Carry the selected profile before persistence so these calls agree. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCase.kt` and `core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/CredentialSummary.kt` | Assertion uses a summary without full hydration. Summary lacks profile identity. Add enough selection information without bulk derivation. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PasskeyCredential.kt` | Define the inputs needed to reproduce credentials under the current contract. A registry or detector for obsolete constructions is unnecessary in the clean-install scope. |
| `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq` and `PasskeyCredential.sq` | Define the current schema and credential persistence contract for a fresh installation. Old-database conversion and legacy defaults are not acceptance requirements. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt`, `data/mapper/EntityMappers.kt`, and `data/repository/CredentialRepositoryImpl.kt` | Storage, mapping, and summaries carry current fields; mappers decode the stored public key. Preserve new identity everywhere and avoid concealing mismatches by rewriting keys. |
| `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/` and `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/` | Ensure commands, events, state, snapshots, and replay preserve the inputs needed by newly created credentials. No old-event converter or invalidated-record tracking is required. |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt` | Documentation cites the old draft and retains a stale BIP-85 parameter description. Correct it to the selected independent PQ policy. |

## Compatibility and security findings

1. **Clean-install scope removes legacy routing.** Initialize the current wallet and credential model directly. Derivation identity remains a current reproducibility contract; it does not require old-profile detection, compatibility selection, or legacy defaults.
2. **Old-registration handling is excluded.** The constructions produce different keys, but testing old registrations or implementing invalidation and re-registration flows is not part of acceptance. Verify newly registered credentials against their own current public keys.
3. **Recovery policy can change across this migration.** Full BIP39 parent input and truncated HDK input differ today. The updated policy must be precisely specified and reproducible for newly registered credentials. Old parent and PQ seed outputs are not frozen acceptance conditions.
4. **Local secret derivation needs a precise policy.** Planning must define a stable domain-separated secret bound to the full credential identity, then apply the new child construction. Name it as a Chimali profile. Do not simulate an issuer or claim a protocol exchange occurred. Exact derivation and version identity belong in design artifacts and independent vectors.
5. **Old compression allows identifier collisions.** Two identifiers with the same first four hash bytes use the same path under a shared parent/seed. This finding follows from the source; no duplicate in a user's wallet was observed. Replace the shortened binding; keeping old paths is not required.
6. **Distinguish signing and storage keys.** The wrapping key protects recovery storage. The parent signing scalar is software material. A non-exportable parent requires additional recovery and compatibility decisions outside this local migration.
7. **Cleanup in affected operations needs completion.** `HdkEcdhP256.hdk` creates salt/blind-key intermediates; PQ derivation leaves working arrays to garbage collection. Signing erases the child bytes in `finally`, but parent-copy cleanup is after success, and warm-up cleanup is success-dependent. Define mutable-buffer ownership on failure/cancellation and accurately document Bouncy Castle and `BigInteger` limitations.
8. **Existing tests must follow the current contract.** Reviewed tests cover old algebra, deterministic paths, salt formulas, KEM round trips, and indices. Replace obsolete acceptance tests with independent vectors for the new construction and retain relevant primitive checks. Frozen old key outputs are not required, and the production derivation must not be its own oracle.
9. **Debt reduction is an explicit acceptance goal.** Remove old-only salt/result models, blind-key stages, KEM entry points, wrappers, bindings, and dead tests when their callers disappear. Keep a helper only for a demonstrated current use. Implement one current P-256 contract rather than building a registry of retired implementations; broader application refactoring remains outside scope.

## Documentation inventory

| Document | Migration action |
| --- | --- |
| `.specify/memory/constitution.md`, Principle II and Governance | Propose the single current local profile, independent PQ policy, authorized breaking change, accurate protection, and pinned draft status. Record amendment rationale and versioning. |
| `docs/brd.md`, FR-AUTH-030, FR-HID-024, NFR-SEC-020, NFR-SEC-040 | Align local migration, recovery metadata, actual protection, and future remote scope. |
| `docs/trd.md`, FR-AUTH-030 and TR-UI-080 | Replace generic old-draft/remote-KEM claims with precise current local behavior and clean-install scope; link future issuance research. |
| `docs/resources.md` | Reference the replacement as current; older references may remain explicitly historical. |
| `docs/research/key_derivation_comparison_dicekeys_seeding.md` | Correct the stale 31-bit description and update the Chimali comparison for its current local profile. External DiceKeys reassessment is outside scope. |
| `docs/research/credential_id_recovery_analysis.md` | Update or label historical source examples and explain required metadata; avoid claims of implemented mnemonic-only authentication. |
| Cryptographic KDoc and comments | Identify profile and correct section references, software protection, credential-byte encoding, and obsolete BIP-85 descriptions. |
| Historical `specs/` and `docs/changelogs/` | Preserve old decisions and references. Add a dated migration record and supersession links where historical text is presented as current guidance. |
| `docs/backlog/openid4vci-hdk-issuance-investigation.md` | Record the requested research independently of the local migration. |

The user explicitly authorizes the breaking migration. Planning must document that direction in the concrete constitution amendment and apply the required versioning, replacing the old-draft mandate as part of the implementation. This clarification does not itself change the constitution or require old passkey compatibility.

## Verification to define during planning

- Current-construction key results and unsigned index boundaries; old-key compatibility fixtures are not required.
- Independently established new scalar, public-key, private-key relationship, hierarchy, and signing results.
- Local-profile reproducibility, complete-identifier binding, and padded/unpadded identity equivalence.
- Clean-install wallet initialization, current-profile registration, assertion, restart, and invalid-input rejection.
- Persistence, replay, snapshot reconstruction, and recovery metadata round trips for newly created credentials only.
- Correct new Ed25519 and ML-DSA registrations and assertions after decoupling PQ policy; old-output stability is not required.
- Failure/cancellation cleanup with dependency limitations documented.
- Existing compilation and static checks and the reference-device latency target; candidate selection must remain free of derivation.
- Dependency review showing no active old-only implementation, compatibility wrappers, dead entry points, or obsolete contract tests remain.

`core/security/build.gradle.kts` configures Android host tests. Older crypto test sources remain under `src/test`, while newer ones use `src/androidHostTest`; confirm discovery during planning. Comments claiming an Android-specific migration test runs on iOS do not establish functional iOS support.

## Outcome of this specify invocation

Created the specification, this analysis, a requirements quality checklist, and the OpenID4VCI investigation. Production cryptography and current conformance documents await the planned implementation. No tests have been run or reported as passing.
