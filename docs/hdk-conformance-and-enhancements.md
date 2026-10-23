# HDK Conformance Assessment and Enhancement Recommendations

**Date**: 2026-10-23

**Status**: Consolidated engineering assessment and proposed follow-up work.

**Scope**: Chimali's current local credential profile; fresh installations; no backward compatibility or OpenID4VCI implementation.

This document consolidates the original analysis, the corrections response, the external agent's sustained response, and the subsequent review. It supersedes their conflicting recommendations. Recommendation identifiers E-1–E-5 and TD-1–TD-8 are retained for traceability.

Only this assessment has been consolidated. Source comments, cryptographic behavior, the derivation contract, and security acceptance have not been changed by this documentation work. Recommendations below are proposed work unless explicitly identified as already satisfied or closed.

## 1. Governing context and evidence

Read this assessment with the [implementation plan](../specs/055-migrate-hdk-draft/plan.md), [local derivation contract](../specs/055-migrate-hdk-draft/contracts/local-key-derivation.md), and [security review](../specs/055-migrate-hdk-draft/security-review.md). These remain the governing feature artifacts.

The assessment reviewed relevant implementation and existing test definitions, primary specifications, provider source, and direct scalar-bias arithmetic. Individual reviews had different evidence scopes: the external sustained response accepted the provider-erasure correction without independently checking BC 1.81 source; the subsequent review checked that source. No tests, device ceremonies, performance measurements, or independent security acceptance were produced by these document reviews. Existing fixtures and historical reports are evidence of their recorded scopes, not newly executed verification.

Chimali adapts the P-256 scalar mapping and multiplicative child-key relation in [draft-dijkhuis-hdk-00 §7](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html#section-7). It substitutes a deterministic per-credential HKDF secret for the draft's per-issuance X25519 shared secret and uses a deterministic software parent. This is custom local composition; it does not implement the draft's issuance protocol, inherit its security analysis, or claim issuance interoperability.

## 2. Draft coverage and local policy

| Draft section | Actual subject | Chimali assessment |
| --- | --- | --- |
| §1 | Introduction | Related motivation; different local application model. |
| §2 | Conventions and Encoding | Relevant encodings applied at the primitive boundary. |
| §3 | Cryptographic Dependencies | P-256, canonical encodings, hashing, and group operations are relevant. |
| §4 | Parent-Key Assurance | Current signing parent is software-derived; no hardware non-exportability or issuer-assurance claim. |
| §5 | Credential Request | X25519 and OpenID4VCI request flow are excluded. |
| §6 | Credential Response | Issuer exchange and credential batch response are excluded; this is not a FIDO2 assertion section. |
| §7 | Child-Key Derivation | Scalar mapping and multiplicative relation are adapted locally. |
| §7.1 | Hierarchical Derivation | Mathematical composition is tested; production FIDO2 uses one leaf derivation. |
| §8 | Using a Child Key | Local software signing is used. |
| §8.1 | ECDH | Parent-preserving ECDH equivalence is not implemented. |
| §8.2 | ECDSA | Alternative raw-parent signing transformation is not implemented. |
| §9 | OpenID4VCI Metadata | No hdk proof-type advertisement; no inference of HDK support from P-256 support. |
| §10 | Interoperability Considerations | No interoperability with the draft's issuance construction is claimed. |
| §11 | Security Considerations | Compromise and bias distinctions are discussed below. |
| §12 | Privacy Considerations | No blanket unlinkability claim; metadata and application behavior can correlate credentials. |

Deterministic parent generation using [RFC 9180 §7.1.3](https://www.rfc-editor.org/rfc/rfc9180.html#section-7.1.3) is Chimali's local policy. It is not required by the HDK draft. Parent-generation references must point to RFC 9180; child-relation references must point to draft §7.

## 3. Current construction and assessment

### 3.1 Root and independent credential branches

The current contract uses the full 64-byte BIP39 seed as input to HKDF-SHA256:

```text
PRK = HKDF-Extract(ASCII("chimali-local-key-derivation-v1"), BIP39_seed)
credentialInfo(label, C) = ASCII(label) || 0x00 || I2OSP(length(C), 2) || C
credential_material = HKDF-Expand(PRK, credentialInfo(label, C), 32)
```

`C` is the complete decoded credential ID, 16–1023 bytes. P-256 local secrets, Ed25519 seeds, and ML-DSA-65 xi seeds use separate labels. Unknown algorithm/profile pairs fail explicitly. The P-256 parent input uses its own expansion label.

Full-ID binding addresses the local recovery and identity requirements and avoids the former 32-bit identity-prefix collision problem. It is not evidence that this application is stronger than the draft's different issuance design. Credential-ID uniqueness remains dependent on generation and collision handling; a random identifier does not make collision mathematically impossible.

### 3.2 Deterministic P-256 parent

[P256ParentKeyDerivation](../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256ParentKeyDerivation.kt) implements the RFC 9180 DHKEM(P-256, HKDF-SHA256) deterministic sampler, with KEM identifier 0x0010, standard HPKE labels, and candidate counters 0–255 inclusive. It accepts only scalars in 1..n-1 and fails on exhaustion.

The parent is derived in software. Encrypted recovery storage and Android Keystore protection of persisted secrets do not make the signing scalar a non-exportable hardware key. [BRD NFR-SEC-041/NFR-SEC-020](brd.md) and [TRD TR-AUTH-031](trd.md) already state this distinction.

### 3.3 Child factor, public point, and private scalar

[HdkP256](../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkP256.kt) applies:

```text
n = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
ctx = SEC1_UNCOMPRESSED(pk_parent) || I2OSP(index, 4)
digest = SHA-256(local_secret || ctx)
b = (OS2IP(digest) mod (n - 1)) + 1
pk_child = ScalarMult(pk_parent, b)
sk_child = sk_parent * b mod n
```

These equations match the draft's scalar mapping and child relation with the documented local-secret substitution. Because the parent scalar and factor are nonzero modulo prime n, the child scalar is nonzero. HKDF derives the local secret upstream; the child factor uses SHA-256 directly.

An all-zero 32-byte local secret remains a valid input to this mapping. The all-zero X25519 shared-secret rejection rule belongs to the excluded exchange. Do not invent an all-zero HKDF-output rejection rule.

### 3.4 Point validation and subgroup membership

[P256Group](../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt) requires canonical 65-byte uncompressed SEC1 input, canonical field coordinates, a valid finite point, and the specified curve.

[BC 1.80 ECPoint validation](https://github.com/bcgit/bc-java/blob/r1rv80/core/src/main/java/org/bouncycastle/math/ec/ECPoint.java) includes an order-validation path. For cofactor-one curves, its order check succeeds without a separate multiplication: on P-256, a valid finite on-curve point belongs to the prime-order group.

The original medium-severity subgroup finding is withdrawn. E-1 is a documentation clarification; this assessment identifies no missing subgroup multiplication.

### 3.5 Scalar bias: exact quantities and limits of interpretation

Assume a uniform SHA-256 output and define:

```text
N = 2^256
m = n - 1
r = N - m
b = (digest mod m) + 1
```

Since m < N < 2m, each scalar output in 1..r has two digest preimages, and each remaining output has one. Each digest still maps to exactly one scalar.

```text
P[b = x] = 2/N, for 1 <= x <= r
P[b = x] = 1/N, for r < x <= m
Uniform reference probability = 1/m

Excess for an output in 1..r = 2/N - 1/m
                             = (m-r)/(N*m)
                             approximately 2^-256

Total variation distance = r*(m-r)/(N*m)
                         approximately 2.328306435454494e-10
                         approximately 2^-32
```

The per-value deviation and aggregate statistical distance are different quantities. Neither is an unqualified per-output-bit security advantage. This calculation alone does not establish a practical key-recovery attack, a security-strength estimate, or a universal distinguishing sample count.

The draft intentionally accepts the bias, and the local contract freezes the mapping. Preserve it under the existing profile identifier. E-3 may use a short qualitative source comment referencing this explanation; do not introduce rejection sampling or another mapping as a documentation cleanup.

### 3.6 Index and hierarchy boundaries

[HdkManager](../core/security/src/commonMain/kotlin/com/chimali/core/security/api/HdkManager.kt) deliberately accepts the full UInt index range. The FIDO2 application supplies LOCAL_CREDENTIAL_INDEX = 0u. Existing HdkP256 tests exercise nonzero indices and repeated composition.

Retain this separation between the primitive and application policy. TD-8 is a KDoc clarification only. Removing the index parameter or rejecting nonzero indices would require a separate contract change; clean-install scope alone is not a reason to change the API. No additional production hierarchy API is needed.

### 3.7 Signing, encodings, and BIP39 parameters

[Fido2CryptoService](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt) signs the derived P-256 child scalar with SHA256withECDSA. It does not use the alternative NONEwithECDSA parent-signing transformation in draft §8.2. The draft discusses possible intellectual-property considerations; this assessment describes the construction used and makes no legal-clearance conclusion.

BC lightweight Ed25519 public-key generation returns raw 32-byte encoding. The JCA decoding adapter prepends ED25519_X509_PREFIX and constructs an X509EncodedKeySpec. TD-5 concerns comments at those boundaries, not a change to encoding.

[BIP39](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki#from-mnemonic-to-seed) specifies exactly 2048 PBKDF2-HMAC-SHA512 iterations and a 64-byte result. This is not a configurable minimum. Increasing it changes the seed and all dependent keys. Password-storage iteration recommendations do not replace this derivation contract. The current 24-word mnemonic carries 256 bits of initial entropy when correctly generated; a disclosed mnemonic is not protected by that entropy claim.

## 4. Security residuals and lifecycle boundaries

### 4.1 Compromise assumptions

- Wallet PRK compromise permits reconstruction of the deterministic parent and all supported credential branches when the required credential metadata is available.
- Parent-scalar-only compromise does not reveal the HKDF-derived local secrets and does not automatically reconstruct the credential keys, including the independent Ed25519 and ML-DSA branches.
- A child private scalar and its factor reveal the parent scalar through sk_parent = sk_child * inverse(b) mod n. Erasure is an ownership measure; it cannot undo disclosure of both values.
- Software derivation and encrypted storage do not establish hardware non-exportability, issuer assurance, or a global unlinkability guarantee.

### 4.2 JVM and provider erasure limits

Java BigInteger arithmetic creates immutable temporaries that application code cannot erase. HdkP256 already documents this. Application-owned byte arrays are cleared at their ownership boundaries; that is not proof of physical erasure of runtime or provider copies. TD-1 should add useful security-review references without repeating a warning at every arithmetic expression.

[PostQuantumCrypto](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt) imports ML-DSA xi through BC 1.80 and clears owned input/export buffers. Provider-managed key state remains outside that cleanup guarantee. The [BC 1.81 ML-DSA private-key source](https://github.com/bcgit/bc-java/blob/r1rv81/prov/src/main/java/org/bouncycastle/jcajce/provider/asymmetric/mldsa/BCMLDSAPrivateKey.java) supplies no effective destruction override. Do not promise that BC 1.81 or any unspecified later version resolves the limitation.

In Java 17, [PrivateKey extends Destroyable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/PrivateKey.html); destroy() is not declared by Key. An inherited API is not evidence of internal zeroing. Revisit TD-2 only for a specific verified destruction implementation, assessing remaining copies and actual lifecycle behavior. No provider upgrade is proposed solely on an expected capability.

### 4.3 Memory-pressure implementation

WalletSessionMemoryManager.kt does not exist. [WalletSessionMemoryPressure](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletSessionMemoryPressure.kt) clears the session for the three specified severe Android trim levels and then requests a GC hint. It defines no KDF. [MemoryUtils](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/MemoryUtils.kt) likewise defines no independent KDF.

[ChimaliApplication](../app/src/main/kotlin/com/chimali/ChimaliApplication.kt) wires memory callbacks; [WalletMasterSeedProvider](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt) owns generation fencing, session owners, and PRK/parent caches. E-5 is closed as an investigation. A GC hint does not guarantee physical erasure.

## 5. Metadata derivation: TD-3 and TD-4

### 5.1 Current construction and consumers

WalletMasterSeedProvider.provisionSearchableMetadataKeys currently computes:

```text
label = ASCII("chimali_searchable_metadata_v1")
metadataKey = first32(HMAC-SHA512(key = label, data = PRK))
```

The same 32-byte result is provisioned into [HmacMetadataLookupTokenService](../core/security/src/androidMain/kotlin/com/chimali/core/security/impl/HmacMetadataLookupTokenService.kt) for lookup tokens and [AesGcmEncryptedMetadataService](../core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataService.kt) for metadata encryption.

Use explicit key/data notation. The earlier description of MAC mode as data-before-key is withdrawn. A public-label-keyed HMAC over secret input must be assessed using its actual input assumptions; the usual secret-key HMAC PRF argument alone does not justify this construction.

The local contract already permits independent KDF policies for non-signature consumers. SHA-512 here is not an established violation of a universal HKDF-only rule. Other consumers, including database encryption, retain independent policies. Changing metadata alone would not standardize every wallet secret consumer.

TD-4 is useful independently: document the exact current label, key/data roles, truncation, consumer purposes, ownership, and recovery/install scope in the derivation contract. This contract update remains proposed work.

### 5.2 Proposed standardization decision

HKDF-Expand from PRK is a reasonable direction for TD-3, primarily for explicit policy and auditability. It is not an established urgent vulnerability fix. The original one-key candidate was:

```text
HKDF-Expand(PRK, ASCII("chimali_searchable_metadata_v1"), 32)
```

This candidate is not a frozen design. Before implementation, decide whether lookup and encryption retain one shared key or receive distinct keys with separate purpose labels. Separate purposes deserve explicit consideration; this assessment does not demonstrate an exploit in the current shared-key use.

Freeze exact labels, input/output lengths, purpose separation, ownership, and install scope before changing derivation. A changed algorithm produces different keys even if its label is unchanged.

### 5.3 Persistence consequences

Replacing the derivation changes both encryption keys and lookup tokens. Previously stored ciphertext can fail authentication and existing indexes can stop matching, even though credential signing derivation remains unchanged.

The selected clean-install scope permits a fresh-state implementation. It does not make old persisted state readable or constitute authorization to erase user data. State the fresh-install boundary explicitly; do not add migration, compatibility fallback, or reset workflows as incidental work.

### 5.4 Required implementation evidence

Design decisions must precede implementation; execution evidence must be produced during validation before the change is considered complete:

1. Freeze the metadata derivation contract and independently calculate expected outputs for the exact construction.
2. Exercise provisioning through the production wallet provider using concrete metadata service instances.
3. Verify encryption/decryption and lookup consistency across wallet reconstruction and application restart using fresh-install state.
4. Verify failure, cancellation, owner-release, and session-clear cleanup, including owned derived buffers and service key references within documented provider limits.
5. Preserve root, parent, and credential-branch fixtures and verify affected registration/assertion behavior.
6. Update focused regression evidence and security-review scope for the changed derivation, following applicable project gates.

The existing WalletMasterSeedProviderTest uses metadata-service interface mocks. Production provisioning uses concrete-service safe casts, so those mocks bypass actual provisioning. Existing wallet-root tests remain useful regression evidence for their assertions, but cannot alone verify this metadata change. Add coverage using concrete services; it may extend the existing test class or use a separate integration test.

## 6. Consolidated recommendation decisions

| ID | Priority/status | Proposed action and boundary |
| --- | --- | --- |
| E-1 | Low; documentation only | Explain P-256 cofactor-one membership. The earlier medium-severity finding is withdrawn. |
| E-2 | Mostly already satisfied | Check existing BRD/TRD software-assurance statements; add wording only if it improves clarity. |
| E-3 | Low; corrected explanation | Use the bias analysis in §3.5. Preserve the mapping and avoid per-bit or sample-count claims. |
| E-4 | Low; recommended | Link HdkP256 to draft §7 and the local contract, and parent generation to RFC 9180 §7.1.3. Ensure documentation links resolve. |
| E-5 | Closed | Actual memory helpers contain no KDF; replace the nonexistent target reference. |
| TD-1 | Informational; selective documentation | Cross-reference security-review residuals where useful; existing BigInteger limitations remain. |
| TD-2 | Conditional future work | Require verified effective provider destruction and documented copy limits; no assumed version guarantee. |
| TD-3 | Medium design follow-up | Specify and separately review metadata KDF standardization, purpose separation, persistence consequences, and verification. |
| TD-4 | Low; independent documentation | Document the current metadata construction before or independently of TD-3. |
| TD-5 | Low; documentation | Clarify Ed25519 raw output versus JCA X.509 decoding. Preserve bytes. |
| TD-6 | Low; documentation | Explain that 2048 is the exact BIP39 count. Preserve parameters. |
| TD-7 | Low; optional cleanup | Share the public ECNamedCurveSpec across P-256 signing/decoding; keep mutable signature engines operation-local. |
| TD-8 | Retain API; documentation only | Explain that FIDO2 uses zero and the primitive intentionally accepts all UInt indices. |

TD-7 removes repeated construction of public curve parameters; it is a plausible allocation reduction, not a measured latency result. Verify affected compilation and existing signing/decoding behavior if implemented. Additional performance claims require measurement.

## 7. Existing invariants and excluded work

Source review and existing test definitions support the following intended invariants; this consolidation does not newly execute or certify them:

- Canonical SEC1 context framing and nonzero child-factor/scalar relation.
- RFC 9180 parent labels, suite ID, and inclusive candidate range.
- Complete decoded credential-ID binding and independent credential expansion labels.
- Explicit algorithm/profile validation and registered-public-key mismatch rejection before signing.
- Operation-owned secret cleanup and session-owner/generation fencing within documented runtime limits.
- Frozen independent fixtures for HKDF, parent generation, and child derivation.

OpenID4VCI request/response flows, ephemeral X25519 exchange, hdk capability metadata, hardware-backed parent migration, issuer batch workflows, and issuance interoperability remain excluded. Mathematical nonzero-index support is retained at the primitive boundary; no production batch API is proposed. Future issuance research remains in the [existing backlog item](backlog/openid4vci-hdk-issuance-investigation.md).

Recovery requires the mnemonic and required credential metadata. No recommendation here introduces old-key support, database conversion, key repair, or re-registration workflows.

## 8. Implementation order and completion criteria

1. Use this consolidated assessment as the corrected recommendation set. Earlier contradictory severity, formula, attribution, comparative-security, and legal-clearance statements are superseded.
2. Apply useful source documentation: accurate specification/contract references, cofactor rationale, encoding boundaries, BIP39 parameters, selective erasure-limit references, and the index-policy distinction.
3. Document the current metadata derivation under TD-4 without waiting for a redesign.
4. Optionally implement TD-7 as an isolated cleanup and verify affected compilation and signing/decoding behavior.
5. Design and implement TD-3 separately, with frozen inputs and labels, explicit fresh-install scope, concrete-service verification, and updated review evidence.
6. Preserve the scalar mapping, BIP39 parameters, valid all-zero local-secret handling, and low-level index API unless a separately specified change deliberately revises the relevant contract.

Only this document's consolidation is complete. Proposed source comments, contract amendments, optimizations, and KDF changes are not marked implemented by their acceptance in this assessment.

The plan's T084/T085 performance deferrals, partial SC-007, ASUS waiver, Android 10 verifier limitation, clean-install scope, and exact-source security-review boundaries remain. This consolidation supplies no new independent current-source security acceptance, activation/deployment authorization, or numeric performance-compliance result.

## 9. Review provenance

The [corrections response](hdk-conformance-and-enhancements-corrections.md) and [external sustained response](hdk-conformance-enhancements-sustained-response.md) are retained as historical review inputs. Their conflicting or superseded wording must not be treated as a second implementation plan. This consolidated document incorporates the later review fixes: exact bias probabilities and mapping direction, correct parent-generation attribution, scoped verification claims, correct Destroyable inheritance, and explicit separation of proposed edits from completed work.
