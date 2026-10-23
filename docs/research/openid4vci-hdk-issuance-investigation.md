# OpenID4VCI and remote HDK issuance investigation

**Date**: 2026-10-23
**Disposition**: Research complete; production no-go; conditional feasibility proposal
**Origin**: [Backlog item](../backlog/openid4vci-hdk-issuance-investigation.md)
**Source baseline**: `d9b000a1fc26caaa0e954bd98cfa6a224aaa6418` plus the working tree inspected on this date. No runtime changes or device experiments were performed.

## 1. Decision and intended user value (question 1)

Chimali should not currently promise receipt of remotely issued HDK credentials. This would introduce a credential wallet product boundary, issuer trust, and recovery obligations beyond the current password vault and FIDO2 authenticator. The value to investigate is receiving several independently keyed copies of a credential without provisioning a hardware key for every copy. Product demand and a participating issuer must justify that additional complexity.

Proposed first scope: **Chimali Lab Issuer**, a future local test service, issuing two synthetic `mso_mdoc` mDL instances with doctype `org.iso.18013.5.1.mDL`. This is a named test scope, not an existing issuer, government partnership, or valid driving licence. No production credential type or issuer is approved. Use the draft's mdoc example to limit protocol variation; acquiring the applicable ISO format specification and an independent mdoc validator is a prerequisite.

Concrete test journey: a tester opens a lab offer, sees the issuer origin and a conspicuous synthetic-data label, selects the configuration, authorizes issuance, and consents to local storage. The wallet accepts a batch only after checking issuer authenticity, credential integrity and the expected holder key for every entry. The tester can inspect expiry/protection, lock and reopen the wallet, perform a local holder-key challenge, and delete the batch. A production journey also needs status/renewal, loss/reissue, accessible consent, and presentation to a real verifier; a local challenge does not establish presentation interoperability. Chimali would be a receiving wallet, not an issuer or identity-proofing authority.

## 2. Evidence and maturity (question 9)

The [Datatracker](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/) lists `draft-dijkhuis-hdk-00` as an active individual Internet-Draft, replacing the earlier `draft-dijkhuis-cfrg-hdkeys` series, with IESG state “I-D Exists”. This is not an adopted standard or evidence of security acceptance. The [pinned text](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html) describes its rewrite as awaiting earlier stakeholders' review (§1). OpenID4VCI [1.0 Final](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html) is a separate, final specification published September 2025.

The [author repository](https://github.com/sander/hierarchical-deterministic-keys) includes an illustrative [Java 25 prototype](https://github.com/sander/hierarchical-deterministic-keys/blob/main/prototype.java). Inspection found X25519 exchange, two child public keys and ECDH/ECDSA possession checks. These are in-process records, not an HTTP issuer issuing signed credentials. Its seeded random generator is not a portable frozen vector contract. It is a candidate independent arithmetic cross-check, not an interoperability certification; it was inspected, not executed here. Pin a commit and runtime before using it as an oracle.

Searches for `draft-dijkhuis-hdk issuer implementation` and `hdk pk_wallet OpenID4VCI issuer`, and inspection of those authoritative sources, did not establish a compatible external issuer or frozen remote issuance vector set. This is a bounded search result, not proof none exists. Generic OpenID4VCI servers and retired ARKG/KEM implementations cannot fill that evidence gap by name alone.

## 3. Requirements and source gap map (questions 2 and 8)

The following are proposed Chimali requirements, not implemented behavior. Protocol references identify where a future implementation must check the full normative obligations.

| Area / reference | Observed Chimali capability | Required work / disposition |
| --- | --- | --- |
| Discovery and trust: OpenID4VCI §§4, 12 | No issuance integration found in `core`/`feature` Kotlin sources | Validate issuer identifier and metadata correspondence, authorized servers, configuration and endpoints. Use HTTPS and an explicit lab trust anchor. Treat offers, metadata, display URLs and redirects as untrusted; bound downloads and reject unintended network destinations. |
| Authorization: §§5–7, 13 | FIDO2 authorization does not implement OAuth issuance | First experiment: authorization code with external browser, PKCE S256, state, exact redirect and issuer/server binding. Separate issuer identity onboarding from OAuth consent. Handle cancellation, code replay, token expiry and nonce retrieval. Pre-authorized grants are outside the first scope. |
| Request identity: §8.2 | Local passkey identifier only | Track the authorized dataset/configuration with its token and transaction; respect mutually exclusive request identifiers and server-returned credential identifiers. Never substitute a similarly named configuration. |
| HDK negotiation: draft §9 | No remote proof metadata | Require explicit `proof_types_supported.hdk` for the selected configuration. P-256, ES256, a `jwt` proof, or an unknown proof type never implies support. |
| Binding: OpenID4VCI §8.2; HDK §§4–6 | No remote holder-assurance workflow | Resolve D1 below before calling a lab flow conformant. Bind the accepted parent, holder, issuer, authorization and response to one issuance transaction. |
| Responses: OpenID4VCI §§8–11 | No credential-format validation or deferred transaction store | Strict parsing and limits, trusted issuer signature/chain, signed content and holder-key checks before atomic acceptance. Initially reject deferred issuance and required unsupported encryption rather than silently downgrading. Add them only with a reviewed transaction/key-retention design. |
| Errors: §§6.3, 8.3.1, 9.3 | Existing FIDO errors have another meaning | Map invalid/expired authorization, invalid proof/nonce, denial, unsupported configuration, invalid response and persistence failure to safe user outcomes. Limit retries; ambiguous network failure requires reconciliation or a fresh transaction. Never replay an old exchange pair automatically. |
| Local arithmetic | `HdkP256`, `P256Group`, host fixtures | Candidate reuse after remote input tests; no network/credential parsing inside these primitives. Do not generalize into a profile registry now. |
| Persistence and UI | Password `Credential`, passkey repository and local descriptors | A distinct issued-credential aggregate, consent, encrypted payload, verification result and lifecycle are needed. Do not reinterpret a passkey row as an mdoc. |

**D1 — proof-binding dependency, owner: protocol lead + participating issuer + draft maintainer.** OpenID4VCI §8.2 requires proofs to incorporate issuer audience and, when applicable, `c_nonce`. The HDK proof describes `parent` and `pk_wallet` without explaining that incorporation. Engineering inference: prior parent assurance and transport TLS alone do not establish how this extension satisfies that requirement. Obtain a reviewed binding profile or an updated draft, including proof of parent possession and binding of the ephemeral key to the authorization. An ad hoc signed wrapper would be a lab-specific extension, not demonstrated `-00` interoperability. Do not quietly add JWT fields and claim resolution.

Also reconcile the HDK mdoc metadata example with OpenID4VCI Appendix A.2: the latter uses numeric COSE signing algorithm identifiers and `cose_key` binding examples. The lab must document the agreed metadata and conversion into the actual signed mdoc device key; copying an illustrative JSON example is insufficient.

Source anchors inspected:

- [HdkManager](../../core/security/src/commonMain/kotlin/com/chimali/core/security/api/HdkManager.kt) deliberately exposes local byte operations, with no remote exchange.
- [HdkP256](../../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkP256.kt) and [P256Group](../../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt) provide factor/child arithmetic and validated canonical P-256 points.
- [WalletMasterSeedProvider](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt) derives/caches a software parent and returns private-key copies. [Fido2CryptoService](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt) derives a local per-identifier secret and uses index zero.
- [CredentialKeyDescriptor](../../core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/CredentialKeyDescriptor.kt) accepts current local algorithm/profile pairs only. [Credential](../../core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/Credential.kt) is a password-vault record.
- [Independent local fixtures](../../tools/hdk-reference/README.md) verify the local construction; they do not cover issuer authorization or received credentials. [BRD NFR-SEC-041](../brd.md) expressly identifies the software-parent limitation.

Reuse current point validation, child arithmetic, mutable ownership conventions and encrypted-storage infrastructure only where a concrete caller needs them. The local HKDF credential secret and parent-generation policy are not remote exchange inputs. Preserve local profile bytes and passkey behavior. Do not restore retired draft-06 handles, KEM APIs, hierarchy abstractions or ECDH APIs with no implemented consumer.

## 4. Parent assurance and Android protection (questions 3 and 4)

**Decision:** a software parent is acceptable only for a lab issuer that explicitly accepts software protection. Do not reuse the live local passkey parent; create disposable test material. Production acceptance requires an issuer-approved assurance procedure and evidence for the selected secure area. Encrypting a mnemonic or importing an already exposed scalar cannot substantiate “private key never exported”. A future hardware-parent option must generate it in the selected protected area and keep its policy distinct from mnemonic-derived local keys.

Proposed onboarding evidence: issuer challenge, proof of possession, holder association, authenticated issuer decision, accepted key fingerprint, key protection level, user-authentication policy, attestation validation where applicable, and assurance expiry. A self-declared policy or attestation without holder association is insufficient. No such issuer procedure exists in this feature.

Android [Keystore documentation](https://developer.android.com/privacy-and-security/keystore) makes hardware support conditional on the operation and authorization combination; inspect actual key security and authentication enforcement. [KeyProperties](https://developer.android.com/reference/android/security/keystore/KeyProperties) provides `DIGEST_NONE` from API 23 and `PURPOSE_AGREE_KEY` from API 31. Thus the app's API 28 baseline does not establish protected ECDH availability. Neither the API constants nor a host JVM signature establish device support.

Proposed reproducible device experiment (D2, owner: Android security engineer):

| Operation | Experiment and independent expected result | Release gate |
| --- | --- | --- |
| Child signing through protected parent | Generate P-256 signing key with raw-digest authorization and per-operation authentication. Apply the draft §8.2 representative/signature transformation; independently verify the resulting ordinary signature with the derived child key. Verify different message/child rejection. | Show exact 32-byte no-rehash semantics, DER/raw conversion correctness and issuer acceptance of this operation policy. Ordinary SHA256 signing support alone fails this gate. |
| Child agreement through protected parent | On API 31+, generate agreement-authorized parent; validate and scale a test peer public point, then compare protected agreement output to an independent software child result. | Exact 32-byte output agreement plus rejection of malformed/infinite/off-curve inputs; do not claim API 28–30 support. |
| Authorization and isolation | Attempt before authentication, after cancellation/expiry, after device lock, after key invalidation and with unsupported purpose/digest. Repeat independently on TEE and StrongBox where available. | Unauthorized operations fail; no silent software fallback; actual security level and policy are recorded. |

Record build/patch/API, device model, provider, generation parameters, security level, authentication mode, test source revision, public fixtures, expected/actual outputs, exceptions and timings. Never log production key material. An emulator can check plumbing but cannot establish hardware protection. No device in this investigation has passed these gates.

**D3 — intellectual-property dependency, owner: project owner with qualified IP counsel.** The draft flags possible claims for its ECDSA transformation (§8.2). The Datatracker IPR link returned HTTP 403 during this investigation; no clearance or absence-of-claims conclusion is possible. Before selecting this production path, review the exact implementation and jurisdictions. Internal child derivation requires a secure-area capability that has not been established either. This document makes no legal determination.

## 5. Exchange acceptance and secret ownership (questions 5 and 6)

Draft §§3, 5–7 define fresh per-request X25519 pairs, all-zero exchange rejection, canonical P-256 bytes and positional batch indexing. Proposed wallet acceptance policy:

1. Allocate one bounded transaction owner after consent. Keep issuer/configuration/authorization, accepted parent and fresh wallet exchange key together. Generate a new exchange pair for each new credential request; never derive it from a mnemonic or credential identifier.
2. Parse bounded unpadded base64url values; require 32-byte exchange public values. Apply RFC 7748 decoding semantics, including its treatment of noncanonical field inputs, rather than applying P-256 rules to X25519. Reject an all-zero shared result regardless of provider behavior. Normalize P-256 JWK coordinates to 32-byte unsigned values and validate the resulting 65-byte uncompressed SEC1 point.
3. Require the expected exchange response. Preserve credential order, with unsigned four-byte big-endian indices. Set a lab cap of two credentials (production cap separately reviewed); test index boundaries at the arithmetic layer without allocating enormous batches.
4. Independently derive each expected child public key, validate issuer signature/trust, credential type, validity and the signed holder-binding key. Compare normalized key material, not JSON text or an unsigned response hint. A wrong/reordered/duplicate binding, partial batch, or untrusted issuer rejects the whole batch.
5. Commit accepted credentials and required recovery metadata together. Erase request private key, exchange working copies and operation factors on success, error, cancellation and timeout. A crash before durable acceptance leaves no usable credential claim. The initial lab cancels interrupted requests instead of resuming them.

The [X25519 decoding rules](https://www.rfc-editor.org/rfc/rfc7748.html#section-5) must be covered independently; “canonical public-key encoding” must not accidentally become an incompatible X25519 decoder.

Proposed data classification and owners:

| Data | Owner and retention | Backup / disclosure |
| --- | --- | --- |
| Parent private key | Secure area for its lifetime, or disposable lab secret owner; erase lab copies | Hardware profile: device-bound, no exported backup. Never put private parent bytes in events. |
| Exchange private keys, access/refresh tokens, PKCE verifier, transient factors/scalars | Issuance/operation owner, volatile mutable buffers, terminal cleanup; no refresh-token persistence in first lab | Excluded from backups, analytics, crash reports and event payloads. Issuer owns and destroys its own ephemeral private key. |
| Issuance shared secret | Wallet vault secret owner; encrypted at rest only if retained for future child operations | Proposed batch-level encrypted recovery record; erase working copies immediately and stored record when no live credentials need it. Issuer should erase its copy when operationally unnecessary, but wallet cannot enforce that. |
| Alternative per-child factors | Possible later minimization option, not simultaneous duplicate storage | Review separately: retains only needed child links but still needs encrypted recovery and a parent. Do not claim factor export solves parent security. |
| Credential bytes/claims and disclosures | Issued-credential store; sensitive personal data, encrypted | Explicit opt-in encrypted backup with integrity and versioning; delete locally on user request. |
| Issuer/configuration, parent reference/fingerprint, index, expected key, format/profile version, validity/status and verification/assurance provenance | Credential/batch aggregate; non-secret but correlation-sensitive; encrypted | Back up alongside credential and required secret; omit bearer tokens and raw network transcripts. Revalidate trust/status after restore. |
| Audit events | Separate minimal lifecycle facts, opaque IDs, outcome and time | Never append raw credentials, shared secrets or subject identifiers to immutable history. Persistent audit minimization must be reviewed against repository event-sourcing rules. |

Engineering security consequence: knowledge of a child private scalar and its factor permits parent recovery by modular inversion. Preventing factor disclosure alone cannot compensate for exported child private keys; restrict both. This follows from the current multiplicative relation and is another reason not to present software-derived child keys as hardware-protected.

Privacy proposal: isolate parents by issuer where feasible and avoid exposing parent identifiers to verifiers. The issuer still sees its parent/batch associations; colluding issuers can correlate a reused parent. Subject claims, document identifiers/signatures, status checks, IP addresses, offer URLs, timing and application behavior can correlate even when child keys differ. Shared-secret or factor disclosure reveals key relationships. No anonymity or issuer unlinkability claim is appropriate.

## 6. Lifecycle, recovery and portability (question 7)

Proposed states: `offered → consented → authorized → requesting → validating → stored`; any pre-storage terminal error becomes `failed/cancelled`. Stored records can become `expired`, `status-invalid`, `deleted`, or `key-unavailable`. Unknown/offline status must be shown as unknown under the chosen issuer policy, not treated as valid. Renewal/reissue creates a new batch and fresh exchange; it does not overwrite key provenance. These are application states, not new wire fields.

For software experiments, restoration needs the exact parent recovery material, batch shared secret (or chosen per-child factor), parent public key identity, index, derivation version, issued credential and validation metadata. A mnemonic alone cannot recreate an issuer's ephemeral contribution or lost signed credential. Test missing/mismatched metadata as an explicit failure.

For a generated non-exportable hardware parent, restoring credential bytes and batch metadata on a new phone does not restore signing capability. Proposed production policy is issuer reauthentication and reissue to a newly accepted parent; old-device loss/revocation policy must be agreed. No automatic device replacement or cloud escrow is promised. Recovery UI must distinguish local passkey recovery from this issuer-dependent process. Deletion clears local payload and protected batch secret references; it does not erase issuer records, previous backups, or already shared presentations. Backups require their own retention/deletion policy.

## 7. Reproducible interoperability proposal (question 9)

This is a proposed experiment, not a performed proof of concept. D4 is owned by the interoperability engineer and external issuer operator. Require a named external issuer, pinned build, advertised HDK metadata, accepted parent policy and test account before describing any run as cross-implementation issuance. The proposed Lab Issuer is useful for failure injection but is not independent evidence when built alongside the wallet.

1. Freeze synthetic raw parent/exchange input bytes, revision, algorithms and expected transcript. Pin the author's Java prototype and a second implementation using independently maintained crypto primitives. Record runtime/provider versions and SHA-256 of fixtures and output. Do not copy expected values from Chimali or rely on seeded provider-specific randomness.
2. Establish primitive correctness first with [RFC 7748 §6.1 vectors](https://www.rfc-editor.org/rfc/rfc7748.html#section-6.1). Independently freeze shared result, context, factor and child SEC1 keys for indices `0`, `1`, `0x80000000`, `0xffffffff`. Compare issuer public derivation with wallet private derivation and ordinary signature verification. The existing local fixtures are regression guards only.
3. For signed test mdocs, use an independently implemented format validator with the applicable ISO profile and a frozen lab trust anchor. Expected results are two valid credentials with their signed device public keys equal to the independently frozen child keys. Verify holder possession after process restart. Do not compare randomized signature bytes for equality.
4. Run one documented HTTP authorization/issuance scenario with the independent issuer after D1 resolution, saving redacted metadata, message hashes, timestamps and public expected/actual keys. Run protected operations separately on the device matrix. A successful arithmetic example is not an OAuth or credential validation pass.
5. Require all failure cases below to fail closed, with no accepted partial record and cleanup evidence. Preserve test results, failing inputs and exact rerun instructions in the future feature evidence packet.

Required failures: missing `hdk` capability; issuer/authorization-server/redirect substitution; unsupported algorithm/protection; rejected parent association; stale proof/nonce and replay; wrong credential identifier; malformed/missing exchange value and all-zero result; invalid P-256 coordinates/curve/infinity; swapped responses between concurrent requests; reordered/duplicated or excessive batch; wrong signed holder key; invalid issuer signature/trust/expiry/type; storage failure; cancellation/timeouts; missing backup secret; device-bound parent unavailable; unauthenticated or invalidated secure-area operation. Replay/audience cases cannot be marked passed while D1 is unresolved.

## 8. Follow-up scope, effort and closure

**Conditional feature outline: “Remote HDK issuance feasibility with synthetic credentials.”** It requires separate implementation authorization; no numbered Spec Kit feature or production requirement is created here. Acceptance consists of the bounded journey, frozen independent expected values, actual external issuer transcript if available, explicit D1 disposition, device authorization evidence, restore/loss checks and a renewed go/no-go decision. If only the lab or software path works, report exactly that restricted outcome.

Rough engineering estimate, one experienced Android/protocol engineer, excluding external waits:

| Work package | Person-weeks |
| --- | ---: |
| Binding/assurance profile, test issuer agreement and frozen fixtures | 1–2 |
| Protected signing/agreement and authentication device experiment | 1–2 |
| Offer/discovery/OAuth and bounded exchange integration | 2–3 |
| Credential validation, encrypted lifecycle, minimal UI and recovery experiment | 2–3 |
| Independent issuance/failure runs and evidence review | 1–2 |
| **Feasibility total** | **7–12** |

If feasibility passes, production wallet lifecycle/presentation, issuer onboarding, backup/reissue, accessibility, hardening and independent security review are a separate roughly **12–24 additional person-weeks**, with low confidence until scope is agreed. These are planning ranges, not commitments; standards changes, external issuer work, IP review and unavailable devices have unbounded waiting time. Compare this effort with ordinary independently generated holder keys before selecting HDK for production.

| Dependency | Accountable role | Evidence needed to reopen production decision |
| --- | --- | --- |
| D1: audience/freshness/ephemeral binding and metadata profile | Protocol lead, issuer operator, draft maintainer | Reviewed profile or revised draft plus negative replay/substitution results |
| D2: protected child operations | Android security engineer / secure-area vendor | Named device matrix, actual policy/security levels and independently verified operations |
| D3: ECDSA IP path | Project owner / qualified IP counsel | Applicable implementation decision; no clearance inferred from repository licensing |
| D4: compatible issuer and independent expectations | Interoperability engineer / external issuer operator | Pinned independent issuer build, vector provenance and reproducible signed issuance |
| D5: product, format and lifecycle agreement | Product owner / credential issuer | Real credential demand, format specification/validator, trust/status and replacement policy |
| D6: production assurance | Security reviewer | Threat model, ownership/backup audit and independent implementation review for the selected design |

Closure mapping: question 1 is addressed in §1; 2 in §3/D1; 3–4 in §4/D2/D3; 5–6 in §5; 7 in §6; 8 in §3; 9 in §§2 and 7/D4. All research deliverables have an answer or an explicit dependency. Research closure is neither implementation completion nor production approval.
