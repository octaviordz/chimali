# Implementation Plan: Replace Chimali's Local HDK Construction

**Branch**: `codex/055-migrate-hdk-draft` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/055-migrate-hdk-draft/spec.md`, including all four clarification decisions.

**Planning status (2026-10-18)**: **205/207 defined task rows checked; T084/T085 remain user-deferred.** T127 remediation is evidenced but has no task row. T128 ACCEPT and activation authorization remain limited to the exact reviewed 235-entry manifest and T130-T136; later changes remain outside that authorization. T203–T208 are complete. T203–T204 remains the latest full integrated local-CI/coverage checkpoint (198.88 seconds); T205–T208 focused style, compile and affected host regressions passed separately. The ASUS waiver, Mi A2 Android 10 verifier limitation, clean-install scope and partial SC-007 remain. No current-source independent security acceptance, activation/deployment, device-class equivalence or numeric performance compliance is claimed.

## Summary

Replace the retired HDK implementation with one local P-256 profile that adapts the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00`, explicitly substituting deterministic local inputs for the draft's per-issuance X25519 shared secret. Use RFC 5869 HKDF to derive a local secret bound to the full decoded credential identifier, and RFC 9180 deterministic P-256 key generation to obtain the software parent. The local credential index is zero; full identity changes the secret, so the old two-level index path and its 32-bit collision problem disappear. This is custom local composition, not the draft's issuance protocol or an interoperability claim.

The full BIP39 seed is a transient 64-byte input. A named HKDF extraction produces the 32-byte wallet root PRK. Parent generation and independent P-256, Ed25519, and ML-DSA credential material use separate expansion labels. The exact bytes are frozen in [the local derivation contract](contracts/local-key-derivation.md). Bouncy Castle 1.80 supports direct ML-DSA seed import; remove the custom deterministic random generator.

Acceptance starts with a clean installation. Implement current storage and current event reconstruction, without old-key support, database conversion, invalidation workflows, or re-registration work. Add one explicit current profile identifier to credential metadata; this is a checked identifier, not a registry of implementations. Remove obsolete aliases, key-existence fiction, salt/XMD APIs, remote KEM paths, and the public-key repair worker. Complete the required constitution amendment and current documentation changes as part of this feature. Keep OpenID4VCI as the existing research backlog item.

## Technical Context

**Language/Version**: Kotlin 2.3.21; Android/JVM target 17; Kotlin Multiplatform module layout.

**Primary Dependencies**: Existing Gradle 9.6.0, AGP 9.4.1, Bouncy Castle 1.80, Signum 3.20.0, Koin 4.2.1 with annotations/KSP, kotlinx.coroutines 1.11.0. No production dependency upgrade or new Gradle module is required. Scoped JVM coverage tooling will pin JaCoCo 0.8.15.

**Storage**: Existing encrypted Proto DataStore for recovery material; SQLDelight 2.3.2 and SQLite3MultipleCiphers driver 2.1.0-2.2.3-0 for credentials/events/snapshots. Persist encrypted mnemonic and credential metadata; never persist PRK, parent private scalar, credential seeds, factors, or child private keys.

**Testing**: `kotlin.test` for shared validation/deciders; JUnit 5 host tests for Android/JVM cryptography; independent reference fixtures; Android device tests for Keystore/DataStore/SQLiteMC and instrumented memory/lifecycle behavior. Move/update the mnemonic Compose tests from unconfigured `androidTest` into configured `androidDeviceTest`, execute them and record named UI results.

**Target Platform**: Android API 28+; compile SDK 37, application target SDK 35. Existing iOS targets remain placeholders.

**Project Type**: Android mobile application with shared KMP modules.

**Performance Goals**: On the user-designated reference device and workload, RP-to-verified-response Bluetooth authenticator latency remains below 1,000 ms, excluding only measured user-verification wait. All internal FIDO operation processing remains below 200 ms, excluding user-verification wait and Bluetooth response handoff; include operation-specific lookup, key derivation, signing, and response construction where applicable. Credential candidate listing performs no key derivation; an assertion derives only the selected credential. Keep cold-start and binary/memory budgets defined in the constitution.

**Constraints**: Clean-install scope; one supported current profile per algorithm; standard P-256 output and existing CTAP2 behavior; deterministic recovery with required credential metadata; explicit buffer ownership and terminal cleanup; Tier 1 traceability and actual test discovery; constitution amendment before replacing the old mandated construction.

**Scale/Scope**: L. Main work in `:core:security`, `:core:domain`, and `:feature:fido2`; narrow ownership changes in `:core:database`, tracing metadata in `:core:common`, and session composition where required. No issuer networking, new user journeys, hardware-parent migration, or unrelated application refactor.

## Constitution Check

*Initial gate and post-design gate: the design is ready for task generation. Required implementation gates below are work in this plan, not completed verification or security approval.*

| Principle / standard | Initial assessment | Post-design disposition |
| --- | --- | --- |
| II — Master Seed Architecture | The old mandated algorithm conflicts with the requested replacement. | Authorized amendment is the first implementation milestone: [constitution-amendment.md](constitution-amendment.md). Do not retain obsolete behavior solely to satisfy old text. |
| I.5 and ES-V.5 — secret ownership | Current cached arrays alias callers, and mnemonic processing creates immutable secrets. | Planned mutable mnemonic processing, owned seed/key copies, atomic cache publication, session destruction, and `finally` cleanup resolve application ownership. Dependency copy limitations require the recorded audit in [security-review.md](security-review.md). |
| ES-V.5 — published constructions / review | The draft does not define this local secret policy. | Exact published primitives and byte composition are specified. A documented engineering security review is an implementation gate. Research does not count as formal security signoff. |
| ES-V.5 — randomness | The blanket rule for all salts conflicts with permitted fixed public KDF domain separation. | Related amendment distinguishes deterministic KDF constants from security-sensitive randomness; nonce/IV requirements remain enforced. |
| A-IV — event sourcing | Current registration events omit algorithm and derivation identity. | Add current algorithm/profile to command, registered event, state and projections; emit fields explicitly and preserve replay/snapshot agreement. Use additive serialized fields with invalid sentinel defaults; no old-event converter. |
| ES-V.3 — schema evolution | User explicitly selected fresh installations and excluded old database migration work. | Update fresh-schema definitions directly. Record this feature-specific clean-install override; no staged old-column deprecation or conversion scripts. General published-format policy is not redefined. |
| ES-IV — YAGNI / three uses | Old public APIs have no actual current callers; compatibility scaffolding would add debt. | Remove them. Keep only the minimal cryptographic interface used by generation, signing and warm-up. HKDF serves parent and three algorithm branches. No new module or profile registry. |
| A-III / ES-III — traceability | `@Req` is mandated but no implementation was found. | Add a source-retained common annotation, associate touched functions and tests with stable BRD/TRD IDs, and allocate a current local-HDK ID. |
| ES-I / ES-II — testing and CI | Older crypto/UI tests sit in unconfigured source directories; available coverage is vault-only. | Move current crypto and mnemonic Compose tests to configured sources and prove execution. Enforce affected Tier 1 statement/branch floors of 40%, each affected Tier 2 module statement floor of 20%, and repository floor of 20%; report Tier 2 branch coverage separately. |
| III — performance and memory | Cleanup and key reproduction must not force derivation of candidate lists. | Carry profile and registered public-key encoding in the summary; verify only the selected key using the already-derived result. Session caching is bounded and destructible. Measure existing budgets before integration. |

There are no unexplained planning violations. The constitution change, security review, ownership audit, independent vectors, and measured CI gates must be satisfied during implementation; none are claimed complete here.

## Project Structure

### Documentation (this feature)

```text
specs/055-migrate-hdk-draft/
├── spec.md
├── analysis.md
├── plan.md
├── research.md
├── data-model.md
├── constitution-amendment.md
├── security-review.md
├── quickstart.md
├── contracts/
│   ├── local-key-derivation.md
│   └── credential-lifecycle.md
└── checklists/requirements.md
```

The subsequent tasks workflow generated [tasks.md](tasks.md); completed work is marked in its checklist. The T054–T056 source/documentation audits are complete. The US1 acceptance checkpoint, device-only lifecycle evidence, module coverage floors, and Detekt/local-CI gate are complete. The T067 localhost browser-to-CTAP2 RP scenario and corrected matched ASUS performance evidence are recorded with their limitations. The independent formal implementation review is recorded as ACCEPT for the named local profile and pre-correction source; supplemental review of the assertion UV correction is recorded as closed under T075.

### Source Code (repository root)

```text
core/common/src/commonMain/kotlin/com/chimali/core/common/annotation/Req.kt       [ADD]
core/security/src/commonMain/kotlin/com/chimali/core/security/api/
  HdkManager.kt, HdkTypes.kt, MasterSeedProvider.kt, MasterSeedGenerator.kt        [REPLACE/UPDATE]
core/security/src/androidMain/kotlin/com/chimali/core/security/
  hdkeys/HdkP256.kt, P256ParentKeyDerivation.kt, HkdfSha256.kt                    [ADD]
  hdkeys/P256Group.kt                                                           [REDUCE/VALIDATE]
  hdkeys/HdkEcdhP256.kt, HashToScalar.kt, MultiplicativeBlinding.kt, DhKem.kt       [RETIRE]
  impl/Bip39MasterSeedGenerator.kt, EventStoreKeyProviderImpl.kt                  [UPDATE]
  di/SecurityModule.kt                                                         [UPDATE]
core/domain/src/commonMain/kotlin/com/chimali/core/domain/
  model/CredentialSummary.kt, valueobject/CredentialId.kt                         [UPDATE]
  model/CredentialKeyDescriptor.kt                                              [ADD]
  eventsourcing/passkey/{PasskeyCommand,PasskeyEvent,PasskeyState,PasskeyDecider}.kt [UPDATE]
core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt  [OWNERSHIP UPDATE]
feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
  data/crypto/{WalletMasterSeedProvider,Fido2CryptoService,PostQuantumCrypto}.kt   [UPDATE]
  data/crypto/HmacSecretProcessor.kt                                            [OWNERSHIP UPDATE]
  data/worker/CorruptedKeyRepairWorker.kt                                        [DELETE]
  domain/model/PasskeyCredential.kt                                             [UPDATE]
  domain/usecase/{RegisterCredentialUseCase,GetAssertionUseCase}.kt              [UPDATE]
  data/{dao/PasskeyCredentialDao,mapper/EntityMappers,repository/CredentialRepositoryImpl}.kt
  Fido2Initializer.kt, session composition, mnemonic DevTools state/UI adapters  [UPDATE]
feature/fido2/src/commonMain/
  sqldelight/com/chimali/fido2/data/database/{Fido2Database,PasskeyCredential}.sq  [UPDATE FRESH SCHEMA]
  kotlin/com/chimali/fido2/data/eventsourcing/                                   [UPDATE]
core/{security,domain}/src/{androidHostTest,commonTest}/                          [CURRENT TESTS]
feature/fido2/src/{androidHostTest,androidDeviceTest}/                            [CURRENT TESTS]
  androidDeviceTest/.../presentation/ui/DevelopmentToolsScreenTest.kt             [MOVE/UPDATE COMPOSE]
tools/hdk-coverage.init.gradle                                                  [ADD]
.specify/memory/constitution.md; docs/{brd,trd,resources}.md; related research    [UPDATE]
```

**Structure Decision**: Use existing module boundaries. The byte-only HDK API can live in `commonMain`, while Bouncy Castle/JCA and ICU implementation stay in `androidMain`. Do not create unsupported iOS implementations. `@Req` is shared infrastructure in `:core:common`; add the minimal `:core:security` common dependency needed to consume it, without creating a new module.

## Phase 0 — Research Decisions

The two research reviews resolved the local policy, parent algorithm, ML-DSA seed-import API, canonical identity, current source-set placement, active event bindings, and ownership blast radius. Decisions, rejected alternatives, and primary references are in [research.md](research.md). The exact profile is in [contracts/local-key-derivation.md](contracts/local-key-derivation.md).

## Phase 1 — Design and Implementation Sequence

### A. Governance and verification foundation

1. Review the concrete local profile and [security assessment](security-review.md), recording reviewer/date/disposition for the implementation security gate. If it is classified as a custom protocol under ES-V.5, complete formal review before activation. The user's migration, breaking-change and clean-install directions are already authorized.
2. Apply the proposed constitution amendment as **2.0.0 → 3.0.0**, with actual amendment date and sync impact report. Update the old-draft requirement references in BRD/TRD and add the specific local-HDK requirement used by code annotations.
3. Establish configured test locations and freeze independently derived fixtures before implementing the replacement. Do not relabel old expected values. Add only the minimal traceability metadata required by the constitution.

### B. Current cryptographic construction

1. Implement RFC 5869 HKDF with explicit mutable-buffer ownership and the fixed profile byte contract.
2. Extract only RFC 9180 P-256 `DeriveKeyPair` into the deterministic parent helper. Its candidate loop is counters 0–255 inclusive; the old helper's 0–254 range is incorrect. Keep its standard labels, not the old remote protocol.
3. Implement exact draft factor/context and validated P-256 operations in `HdkP256`. The low-level operation accepts `UInt`; the FIDO2 local profile supplies zero. Keep no path/fold/salt hierarchy or unused ECDH entry point.
4. Route Ed25519 and ML-DSA to separate HKDF seeds. Import the 32-byte ML-DSA seed directly using the pinned BC provider; remove `DeterministicSecureRandom`. Preserve existing wire/public-key format expectations, not old derived key bytes.
5. Reject unsupported algorithm/profile pairs explicitly. Remove `else` branches that silently use ES256.

### C. Wallet and ownership boundaries

1. Change mnemonic generation, derivation and getters to owned mutable character storage; validate English BIP39 checksum/word count and use mutable normalization/UTF-8 paths. Keep public dictionary strings; never construct private mnemonic `String`/`List<String>` in crypto code. Adapt the existing debug reveal/import UI owners and audit its unavoidable display/clipboard boundary.
2. Persist only the encrypted mnemonic through the current Proto DataStore path. Remove this provider's old EncryptedSharedPreferences migration/fallback and its reliance on shared legacy migration flags. Keep fields and workflows owned by unrelated preference migration code.
3. Cache one PRK and one parent pair in a bounded wallet session. Publish them together only after all initialization/provisioning succeeds. Return owned copies; erase provider caches on replacement/session end/severe memory pressure and erase every operation copy in `finally`.
4. Remove `getPqChildSeed` and the old PQ cache. Derive each algorithm's seed from the root PRK at the current credential boundary.
5. Update `EventStoreKeyProviderImpl`, `EncryptedDriverFactory`, `HmacSecretProcessor`, warm-up/availability callers and initializer cleanup to the copy contract. Remove the database KDF's immutable hex-password temporary using owned ASCII bytes while retaining the documented PBKDF2 algorithm.
6. Apply the exact owner/generation policy in [the lifecycle contract](contracts/credential-lifecycle.md#session-ownership-and-termination): foreground loss ends foreground owners, an already authorized bounded HID ceremony has its own owner, disconnect ends transport work, and last-owner release erases caches. Import/replacement and all three required memory levels fence the generation and cancel all its work. Reinitialize lazily from the same encrypted wallet only for a new authorized request; no idle-connection retention or new login journey.

### D. Credential creation, persistence and reconstruction

1. Use the single explicit descriptor `(credentialId, coseAlgorithm, derivationProfile)` through generation, self-attestation, assertions and warm-up. Decode the registration public-key result instead of regenerating it just to construct a Java public key.
2. Fresh schema adds `derivation_profile TEXT NOT NULL` and removes the obsolete `private_key_alias`; all current insertion/mapping paths supply the profile explicitly. No old-database migration, reset handler or legacy default is part of this feature.
3. Remove `Fido2KeyPair.alias`, `credentialAlias`, `keyExists`, and no-op `deleteCredentialKey` usage once callers use current descriptors and metadata deletion. Keep public data only in generation results.
4. Add algorithm/profile metadata to current registration commands, events, states and snapshots. Default new serialized event/state fields to invalid sentinels to meet additive-schema shape rules without guessing current key identity; successful registration always supplies them. Emit defaults explicitly where necessary and reject malformed current records normally.
5. Use explicit event-first and projection-second SQL transactions. Atomically enclose all related projection deletions and insertion; rollback the whole projection phase on failure/cancellation so previous projections remain intact. Retain an already committed append-only event/key, return a typed persistence error or propagate cancellation, and report success only after both commits. Verify transaction ordering, rollback and reconstruction without a general transaction coordinator.
6. Carry profile and registered public-key encoding in the metadata summary. Derive only the selected assertion key, compare its already-produced public key against the stored key, and fail on mismatch without overwriting storage.
7. Delete repair-worker scheduling/bindings, DAO `updatePublicKey`, and SQL `update_public_key`. Current public-key corruption is an error, not an invitation to replace a relying party's registration locally.

### E. Debt removal and documentation

Inventory retired callers/tests before changing the API. Delete the retired implementation and replace configured old-API tests during US1, after current callers are replaced and before its DI/compile/test checkpoint; no old implementation remains compiling against removed methods. Keep standards-required RFC 9180 key-generation machinery under its new demonstrated purpose. US2 audits remaining debt and synchronizes DI comments, resources, BRD/TRD, recovery analysis, the existing comparison and a dated changelog. Preserve historical specifications/changelogs as records and link the separate OpenID4VCI backlog item; the user's clean-install-only scope removes FIDO2 SQLDelight `.sqm` schema history.

### F. Required verification and completion

Use [quickstart.md](quickstart.md) and the contracts for independent vectors, configured shared/host/device and Compose tests, atomic projection rollback, owner/generation cancellation, static checks and measured size/latency/memory. Enforce whole affected Tier 1 modules at 40% statement/branch, each affected Tier 2 module (`core:database`, `core:common`, `app`, and any additionally changed Tier 2 owner) at 20% statement coverage, and repository aggregate at 20%. Report Tier 2 branches and merge host/device execution data without duplicate class denominators where needed. A scoped HDK report cannot establish these floors. Report pre-existing failures with evidence.

## Requirements and Acceptance Mapping

| Spec requirements | Design coverage / acceptance evidence |
| --- | --- |
| FR-001, FR-005, FR-007, FR-008 | Exact byte contract, full-identifier binding, bounded parent generation, independently derived vectors and malformed-input cases. |
| FR-002, FR-003, FR-004, FR-009, FR-016, FR-017 | Clean initialization, explicit descriptor, persistence/replay and partial-failure behavior, valid registration/assertion after restart, selected-key equality and metadata-only listing. |
| FR-006, FR-010, FR-011, FR-018 | Required recovery metadata, separate signature seeds, removal of retired remote APIs, existing OpenID4VCI research item. |
| FR-012, FR-013 | Truthful software-parent assurance, mutable mnemonic/root/operation owners, cancellation/session cleanup and provider audit. |
| FR-014, FR-015, FR-019 | Constitution 3.1.0 (local-profile amendment introduced in 3.0.0), current documentation, dead-path dependency review and no compatibility scaffolding. |

SC-001 through SC-004 concern current functional and safe-failure behavior; SC-005/SC-010 require truthful documentation/constitution; SC-006 checks full-identity collision fixtures; SC-007 separately measures RP-to-verified-response latency (<1,000 ms excluding measured user-verification wait), all internal FIDO operation processing (<200 ms each, excluding user-verification wait and Bluetooth response handoff), and zero candidate derivation; SC-008 keeps the backlog item; SC-009 checks removed debt.

## Complexity Tracking

| Constraint requiring explicit treatment | Why required | Simpler alternative rejected because |
| --- | --- | --- |
| Current Principle II and deterministic-salt wording | User requires a different current construction and local recovery policy. | A citation-only edit would leave false obligations and incompatible formulas. Concrete amendment is included. |
| Fresh schema removes an alias and adds current identity without old conversion | The user explicitly authorizes clean installation only. | Supporting staged old-format migration would restore the excluded user story and add debt. Record the scoped authorization, not a global schema-policy exemption. |
| A current profile field and thin byte-only interface | Profile identity must survive replay; implementation serves generation/signing/warm-up. | A multi-profile registry is unnecessary; implicit algorithm-only identity would omit part of the recovery contract. |
| Mutable mnemonic boundary adapter | I.5 forbids immutable application-owned cryptographic plaintext. | Keeping the current secret `List<String>` cannot satisfy the adopted memory requirement. The adapter has an explicit constitutional mandate. |

## Execution Notes

The planning, task-generation, and remediation workflows produced the implementation plan; implementation and evidence are tracked in [tasks.md](tasks.md) and [implementation-evidence.md](implementation-evidence.md). The current FIDO2 schema assumes a clean installation and has no SQLDelight migration-history files. The local RP registration/assertion/restart scenario and corrected matched ASUS evidence remain recorded. T069/T083 documentation closure explicitly preserves the user-deferred T084 and T085 performance work; SC-007 is not fully verified. The independent formal implementation review and supplemental assertion UV disposition are recorded and closed under T075 within their reviewed scopes. Neither disposition is a deployment claim or an interoperability certification. Optional commit hooks were inspected and left unrun for this invocation.
