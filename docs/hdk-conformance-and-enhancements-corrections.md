# Response to the HDK Conformance and Enhancement Recommendations

> Historical review input. The [consolidated HDK assessment](hdk-conformance-and-enhancements.md) supersedes this document's recommendations and incorporates subsequent review corrections. This response is retained for provenance; proposed changes below are not evidence of completed implementation.

**Date**: 2026-10-23  
**Responds to**: [HDK conformance analysis and enhancement recommendations](hdk-conformance-and-enhancements.md)  
**Scope**: Read-only engineering assessment of the current local profile and proposed enhancements. This response does not implement or authorize cryptographic changes, extend independent security acceptance, or claim deployment readiness.

## Overall assessment

The original analysis identifies useful documentation improvements and a reasonable opportunity to standardize metadata derivation. Its recommendations should not be implemented unchanged: several technical claims are inaccurate, some proposed work is already covered, and two recommendations change deliberate contract decisions.

The current [implementation plan](../specs/055-migrate-hdk-draft/plan.md), [derivation contract](../specs/055-migrate-hdk-draft/contracts/local-key-derivation.md), and [security review](../specs/055-migrate-hdk-draft/security-review.md) remain the governing context. Chimali adapts the pinned draft's scalar mapping and child-key relation through a custom local composition. It does not implement the draft's issuance protocol or inherit its security analysis.

This assessment examined relevant source and existing test definitions and checked primary specifications and provider source. No tests were executed during the assessment. Existing test definitions and historical evidence are not new verification results.

## Corrections to the analysis

### 1. Scalar bias: distinguish per-value deviation from statistical distance

Original references: §3.9 and E-3.

The proposed estimate of approximately `2^-256` advantage per output bit is incorrect. Assume SHA-256's output is uniform and define:

```text
N = 2^256
m = n - 1
r = N - m
b = (digest mod m) + 1
```

Because `m < N < 2m`, output values `1..r` each have two digest preimages; the remaining values each have one. The total variation distance from the uniform distribution on `1..m` is:

```text
Delta = r * (m - r) / (N * m)
      approximately 2.328306435454494e-10
      approximately 2^-32
```

The absolute probability difference for any single scalar is on the order of `2^-256`; this is different from the aggregate statistical distance. Neither quantity should be described as an unqualified per-output-bit security advantage.

This calculation does not establish a practical key-recovery attack or a replacement security-strength estimate. It corrects the proposed explanatory text. The [draft explicitly accepts the bias](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html#section-7), and the local contract freezes the mapping. Preserve the algorithm; do not substitute rejection sampling or another mapping under its existing identifier.

**Disposition**: Revise E-3 before adoption. A short qualitative comment is preferable to an inaccurate numerical assurance claim.

### 2. Point validation: documentation clarification, not an open medium-severity defect

Original references: §3.4 and E-1.

The claim that Bouncy Castle point validation checks only curve membership is inaccurate. BC 1.80's `ECPoint` validation includes an order-validation path. Its `satisfiesOrder` implementation returns true for cofactor-one curves because valid points on those curves already satisfy the subgroup condition. See [BC 1.80 ECPoint source](https://github.com/bcgit/bc-java/blob/r1rv80/core/src/main/java/org/bouncycastle/math/ec/ECPoint.java).

Chimali also rejects infinity and requires the P-256 curve in [P256Group](../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt). P-256 has prime order and cofactor one, so a finite on-curve point belongs to the required subgroup.

**Disposition**: Accept E-1 as a documentation improvement. Remove the medium-severity security-finding characterization; no additional subgroup multiplication is indicated by this finding.

### 3. Correct the draft section map and parent-generation attribution

Original references: §2, §3.2, §3.7, §3.8, and §9.3.

The pinned [draft-dijkhuis-hdk-00](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html) has the following structure:

| Section | Actual subject |
| --- | --- |
| §3 | Cryptographic Dependencies |
| §4 | Parent-Key Assurance |
| §5 | Credential Request |
| §6 | Credential Response |
| §7 | Child-Key Derivation |
| §7.1 | Hierarchical Derivation |
| §8 | Using a Child Key |
| §8.1 | ECDH |
| §8.2 | ECDSA |
| §9 | OpenID4VCI Metadata |
| §10 | Interoperability Considerations |

In particular, §6 is not a FIDO2 presentation/assertion section, and §8 is not the hierarchical derivation section. The prohibition on inferring HDK support merely from P-256 support appears in §9.

RFC 9180 deterministic parent generation is Chimali's local policy, not a parent-generation requirement imposed by this HDK draft. Describe [P256ParentKeyDerivation](../core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256ParentKeyDerivation.kt) against [RFC 9180 §7.1.3](https://www.rfc-editor.org/rfc/rfc9180.html#section-7.1.3), and the child relation against the HDK draft §7.

**Disposition**: Correct the section map before adding E-4 references to implementation documentation.

### 4. BIP39's 2048 iterations are an exact derivation parameter

Original references: §4.6 and TD-6.

[BIP39](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki#from-mnemonic-to-seed) specifies PBKDF2-HMAC-SHA512 with 2048 iterations and a 64-byte result. It does not define 2048 as a configurable minimum. Increasing it changes the seed and all dependent keys, breaking deterministic recovery under the current profile.

The heading suggesting the implementation is below a current BIP39 recommendation should be corrected. Password-storage iteration recommendations are not a replacement for the BIP39 derivation parameters. Chimali's current 24-word generated mnemonic carries 256 bits of initial entropy; that statement is conditional on correct entropy generation and does not imply that a disclosed mnemonic remains protected by the stretching step.

**Disposition**: Accept a short standards comment. Keep the value unchanged.

### 5. Do not promise provider erasure based on a version number

Original references: §4.2 and TD-2.

The assertion that BC version 1.81 or later adds usable ML-DSA key destruction is not established. The [BC 1.81 ML-DSA private-key implementation](https://github.com/bcgit/bc-java/blob/r1rv81/prov/src/main/java/org/bouncycastle/jcajce/provider/asymmetric/mldsa/BCMLDSAPrivateKey.java) does not implement the suggested effective destruction method. An inherited `destroy()` API alone is not evidence that provider-owned key arrays are erased.

**Disposition**: Retain the documented provider-copy limitation. Revisit only when a specific provider release supplies a verified destruction implementation, with its actual behavior and remaining copy limitations assessed. No dependency upgrade is justified by this recommendation alone.

### 6. Correct the Ed25519 heading

Original references: §4.5 and TD-5.

The section heading says `generatePublicKey().encoded` returns JCA X.509 encoding, while its body correctly identifies the Bouncy Castle lightweight result as the raw 32-byte public key. [Fido2CryptoService](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt) adds the X.509 prefix at the JCA decoding boundary. The local contract already describes native generation output and stored JCA encodings.

**Disposition**: Correct the heading and add concise boundary comments if useful. No encoding change is needed.

### 7. Remove unsupported comparative and assurance claims

Original references: §3.6, §3.9, and §9.4.

- Full credential-ID binding addresses Chimali's local deterministic recovery requirements. Calling it stronger than the draft's intended issuance design is not supported by the comparison: the constructions have different inputs, participants, and assurance models.
- Parent-scalar compromise alone does not automatically reveal independently protected per-credential secrets. Distinguish compromise of the wallet PRK, which derives the parent and credential branches, from compromise of the parent scalar alone.
- The implementation avoids the alternative parent-signing transformation described in draft §8.2. That observation does not establish the document's blanket legal conclusion of no patent exposure. Record which construction is used without making a legal clearance claim.
- Erasing factors is an ownership measure. It does not eliminate the algebraic consequence that possession of a child private scalar and its factor allows recovery of the parent scalar.

**Disposition**: Replace categorical assurance statements with scoped descriptions of the actual construction and compromise assumptions.

## Evaluation of the enhancement recommendations

| ID | Decision | Rationale and next action |
| --- | --- | --- |
| E-1 | Accept with correction | Explain P-256's cofactor-one subgroup property; classify as documentation only. |
| E-2 | Mostly already satisfied | BRD NFR-SEC-041/NFR-SEC-020 and TRD TR-AUTH-031 already distinguish software signing material from hardware-protected persisted secrets. Add terminology only if it improves clarity. |
| E-3 | Revise | Correct the bias explanation above; preserve the frozen mapping. |
| E-4 | Accept | Add accurate draft and contract references. Ensure repository-document links resolve rather than assuming a bare relative KDoc path is meaningful. |
| E-5 | Close with corrected target | `WalletSessionMemoryManager.kt` does not exist. The actual memory-pressure handler has no KDF, as described below. |
| TD-1 | Accept selectively | Cross-reference the security review where useful. HdkP256 already explicitly documents immutable BigInteger temporaries; avoid repetitive comments at every arithmetic expression. |
| TD-2 | Defer conditionally | Require verified provider destruction support; do not assume a version supplies it. |
| TD-3 | Separate design decision | HKDF standardization is reasonable, but changes encryption and lookup keys. Specify the complete metadata policy before implementation. |
| TD-4 | Accept independently | Document the actual current metadata construction now; update it alongside any later algorithm change. |
| TD-5 | Accept with heading correction | Clarify raw Ed25519 versus JCA X.509 boundaries without changing bytes. |
| TD-6 | Accept with correction | State that 2048 is the exact BIP39 parameter, not a minimum. |
| TD-7 | Optional small cleanup | Share the public curve specification across signing and decoding. Keep mutable signature engines operation-local; do not claim measured latency gains. |
| TD-8 | Retain current API | The low-level UInt parameter is intentional in the plan and contract. Document that FIDO2 passes zero. Removing it requires a separate contract change. |

### E-5: actual memory-management implementation

[WalletSessionMemoryPressure](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletSessionMemoryPressure.kt) handles the three specified severe Android trim levels. It invokes the supplied session-clearing callback before requesting a GC hint. It performs no key derivation. [ChimaliApplication](../app/src/main/kotlin/com/chimali/ChimaliApplication.kt) connects the callback to application lifecycle handling.

The adjacent [MemoryUtils](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/MemoryUtils.kt) provides buffer/conversion utilities and likewise defines no independent KDF. Session ownership, generation fencing, and cached material belong to [WalletMasterSeedProvider](../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt). A GC hint must not be presented as a physical-erasure guarantee.

### TD-8: preserve the boundary between primitive and application policy

The derivation contract explicitly permits the full UInt range in the low-level child operation, while the FIDO2 policy supplies index zero. Existing tests exercise nonzero indices and repeated child composition. This is a deliberate separation of responsibilities, not an accidental recovery ambiguity.

The clean-install scope addresses persistence compatibility; it is not by itself a reason to change this API. Prefer a KDoc clarification identifying the FIDO2 index policy. Do not make the primitive reject nonzero indices under the current contract.

## TD-3 and TD-4: metadata derivation needs a complete design

### Current behavior

`WalletMasterSeedProvider.provisionSearchableMetadataKeys` currently computes:

```text
label = ASCII("chimali_searchable_metadata_v1")
metadataKey = first32(HMAC-SHA512(key = label, data = PRK))
```

The 32-byte result is provisioned into both:

- [HmacMetadataLookupTokenService](../core/security/src/androidMain/kotlin/com/chimali/core/security/impl/HmacMetadataLookupTokenService.kt), for keyed metadata lookup tokens.
- [AesGcmEncryptedMetadataService](../core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataService.kt), for metadata encryption/decryption.

Use explicit key/data notation when documenting the current HMAC. The original phrase describing MAC mode as data-before-key is misleading. The public-label-keyed construction should not be justified simply by invoking the usual secret-key HMAC PRF argument; a full security conclusion requires its actual high-entropy-input assumptions to be assessed.

The local derivation contract already states that existing non-signature consumers retain independent KDF policies. Consequently, use of SHA-512 here is not an established violation of a universal HKDF-only policy. The exact metadata construction is nevertheless worth documenting. Other consumers, including database encryption, retain their own policies, so changing metadata alone would not standardize the entire wallet secret surface.

### Proposed direction

HKDF-Expand from the wallet PRK is a reasonable standardization option. Before selecting its exact bytes, decide whether metadata encryption and lookup should keep sharing one key or receive distinct purpose-specific keys. Separate labels for these two purposes deserve consideration; this is a design recommendation, not a finding of an observed exploit in the current shared-key use.

Freeze the chosen labels, input/output lengths, purpose separation, consumer ownership, and recovery/install scope in the contract. Reusing a label with a changed algorithm still changes the derived key; the label alone does not preserve compatibility.

### Persistence and verification impact

The replacement changes both encryption keys and lookup tokens. Existing stored ciphertext can fail authentication and existing indexes can stop matching, even though credential signing derivation is unchanged. Under the selected clean-install scope, implementation should target fresh state and state this limitation explicitly; it should not add an unrequested migration or fallback.

Required evidence for a future implementation should include:

1. Independently calculated expected outputs for the exact metadata derivation policy.
2. Provisioning through the production wallet provider into real metadata service instances.
3. Stable metadata encryption/decryption and lookup across wallet reconstruction and application restart under fresh-install state.
4. Failure, cancellation, owner-release, and session-clear handling, including cleanup of application-owned derived buffers and service key references within their documented limits.
5. Unchanged root, parent, and credential-branch fixtures and normal registration/assertion behavior.
6. Updated contract, focused regression evidence, and security-review scope for the changed derivation.

The existing `WalletMasterSeedProviderTest` uses interface mocks for metadata services. Production provisioning uses concrete-service safe casts, so those mocks do not exercise actual metadata key provisioning. Existing wallet-root tests alone cannot establish correctness of the proposed change.

## Recommended implementation sequence

1. Correct the original analysis: section references, scalar-bias explanation, subgroup characterization, BIP39 heading, Ed25519 heading, provider-erasure assumption, and unsupported assurance claims.
2. Apply useful documentation clarifications and source-to-contract references. Document the current metadata derivation without waiting for a redesign.
3. Optionally share the public P-256 curve specification as a small isolated cleanup. Verify affected compilation and existing signing/decoding behavior; do not claim a measured performance improvement without measurement.
4. Handle metadata KDF standardization as a separate reviewed change with frozen bytes, explicit install scope, and the evidence described above.
5. Preserve the current scalar mapping, BIP39 parameters, valid all-zero local-secret behavior, and low-level index API.

The original proposal correctly avoids inventing an all-zero HKDF-output rejection rule. The X25519 all-zero exchange rule belongs to the excluded issuance flow; it should not be transferred to the local HKDF input/output policy.

The feature plan's existing deferred performance work, platform limitations, and exact-source review boundaries remain in effect. Neither this response nor documentation cleanup constitutes fresh independent security acceptance or activation authorization.
