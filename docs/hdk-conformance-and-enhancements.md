# HDK Conformance Analysis and Enhancement Recommendations

**Date**: 2026-10-23  
**Spec reference**: [draft-dijkhuis-hdk-00](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html)  
**Scope**: Clean-install baseline; no OpenID4VCI implementation required; no backward compatibility.  
**Related**: [Local derivation contract](../specs/055-migrate-hdk-draft/contracts/local-key-derivation.md) · [Feature 055 plan](../specs/055-migrate-hdk-draft/plan.md) · [Security review](../specs/055-migrate-hdk-draft/security-review.md)

---

## 1. Purpose

This document analyzes Chimali's current implementation of its local HDK-derived key profile against `draft-dijkhuis-hdk-00`, identifies conformance gaps, and recommends enhancements. It covers:

- How each section of the draft maps to Chimali's implementation.
- Concrete findings and their severity.
- Actionable recommendations, each with a rationale and suggested file location.
- Technical debt present in the current codebase.
- Items that are explicitly out of scope (OpenID4VCI, backward compatibility).

> [!NOTE]
> Chimali does **not** implement the draft's full OpenID4VCI issuance protocol. Its local profile adapts only the scalar mapping and multiplicative child-key relation defined in §7 of the draft, deliberately substituting a deterministic RFC 5869 credential secret for the draft's per-issuance X25519 `shared_secret`. This is a conscious, documented design decision. The findings below evaluate that adaptation and identify improvement areas within the local scope.

---

## 2. Draft Structure vs. Chimali Coverage

| Draft section | Content | Chimali coverage |
|---|---|---|
| §1 Introduction | Problem statement, unlinkability | ✅ Acknowledged in contract and backlog |
| §2 Conventions | Notation, group operations | ✅ Applied in `P256Group`, `HkdfSha256` |
| §3 Overview | Key hierarchy, factor derivation | ✅ Scalar mapping adapted in `HdkP256` |
| §4 Wallet assurance | Hardware-backed parent, non-exportability | ⚠️ Software-only; acknowledged limit |
| §5 Issuance (OpenID4VCI) | X25519 KEM, issuer interaction | 🚫 Out of scope (backlog item) |
| §6 Presentation / assertion | Holder-binding, presentation proofs | 🟡 Partial — FIDO2 assertion used instead |
| §7 P-256 profile | Factor formula, child derivation | ✅ Core adapted; see §3 below |
| §8 Hierarchy | Multi-level tree, child-of-child | ⚠️ Mathematical relation correct, production API limited to depth 1 |
| §9 Capability metadata | `hdk` proof type, capability advertisement | 🚫 Not implemented (would require OpenID4VCI) |
| §10 Interoperability | Wire format requirements | ℹ️ Local profile; no interoperability claimed |
| §11 Security considerations | Compromise, privacy, oracle | ✅ Documented limitations in security-review.md |
| §12 Privacy considerations | Unlinkability, parent correlation | ✅ Local profile inherits parent correlation limit |

---

## 3. Conformance Findings

### 3.1 Factor Derivation Formula (§7) — Conformant with Intentional Substitution

**Draft formula:**
```
context = SEC1_UNCOMPRESSED(pk_parent) || I2OSP(index, 4)
factor = H(shared_secret || context)   # where H(m) = (OS2IP(SHA-256(m)) mod (n−1)) + 1
```

**Chimali adaptation ([HdkP256.kt](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkP256.kt)):**
```
preimage = local_secret || SEC1_UNCOMPRESSED(pk_parent) || I2OSP(index, 4)
digest   = SHA-256(preimage)
factor   = (OS2IP(digest) mod (n−1)) + 1
```

**Assessment**: Correct. The substitution of `local_secret` (a 32-byte HKDF-derived value) for the draft's X25519 `shared_secret` is the documented local-profile adaptation. The scalar mapping `(OS2IP(·) mod (n−1)) + 1` matches §7 exactly, guaranteeing a non-zero factor. The context encoding `SEC1_UNCOMPRESSED(pk_parent) || I2OSP(index, 4)` matches the draft's context construction.

**Finding**: ✅ No change needed.

---

### 3.2 Parent Key Generation (§7.1) — Conformant

**Draft requirement**: Parent key pair derived from a deterministic source using RFC 9180 DHKEM(P-256, HKDF-SHA256) `DeriveKeyPair` (§7.1.3).

**Chimali implementation ([P256ParentKeyDerivation.kt](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256ParentKeyDerivation.kt)):**
- Labeled extraction: `HKDF-Extract(empty_salt, HPKE-v1 || KEM-0x0010 || "dkp_prk" || parent_ikm)`
- Candidate loop: counters 0–255 inclusive, accepts first scalar in `(0, n)`.
- KEM suite ID: `KEM || I2OSP(0x0010, 2)` — matches DHKEM(P-256) exactly.

**Assessment**: Conformant. The inclusive 0–255 candidate range (previously a bug with range 0–254) is now correctly implemented. Suite ID, version label, and labeled-expand construction all match RFC 9180 §7.1.3.

**Finding**: ✅ No change needed.

---

### 3.3 Child Key Derivation Relation (§7) — Conformant

**Draft:**
```
pk_child = pk_parent * factor
sk_child = sk_parent * factor mod n
```

**Chimali ([HdkP256.kt](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkP256.kt#L64-L65)):**
```kotlin
val childPoint = P256Group.scalarMult(parentPoint, factor)
childPrivate = P256Group.serializeScalar(parentScalar.multiply(factor).mod(P256Group.ORDER))
```

**Assessment**: Conformant. Both public and private child derivations match the draft's multiplicative relation.

**Finding**: ✅ No change needed.

---

### 3.4 Point Encoding and Validation (§3) — Conformant with One Open Finding

**Draft requirement (§3)**: Parent public key must be canonical 65-byte uncompressed SEC1 encoding `0x04 || x || y`; public keys must be validated as non-infinite, on-curve, and in the correct subgroup.

**Chimali ([P256Group.kt](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt)):**
- `deserializeElement` rejects wrong length, wrong prefix byte, out-of-field coordinates, and off-curve points via Bouncy Castle `decodePoint`.
- `requirePoint` checks `!isInfinity && isValid && point.curve == curve`.

**Open finding (MEDIUM)**: The `isValid` check on a Bouncy Castle `ECPoint` tests curve membership but does **not** perform a cofactor multiplication check for the full group order (P-256 has cofactor 1, so this is effectively a no-op risk here — but it is not explicitly documented). For P-256 (prime-order curve, cofactor = 1), subgroup membership is guaranteed by on-curve validation, but the rationale is absent from the code.

**Recommendation E-1**: Add an inline comment to `requirePoint` stating that P-256 has cofactor 1, so on-curve membership implies full-order subgroup membership. This removes ambiguity for future reviewers without any code change.

---

### 3.5 Wallet Assurance (§4) — Documented Limitation

**Draft requirement (§4)**: The parent private key should be held in a non-exportable hardware-backed secure area (e.g., Android Keystore). The draft's security model relies on the issuer trusting the holder's hardware attestation for this property.

**Chimali**: Parent key is software-derived, cached in JVM heap (`WalletMasterSeedProvider`), and non-exportable only by policy, not by hardware enforcement. This is explicitly documented in the security review and contract.

**Finding**: ⚠️ Known architectural limitation. Not a bug. The `security-review.md` correctly states:

> "Encrypted recovery storage does not make the signing scalar non-exportable."

**Recommendation E-2**: Ensure the TRD and BRD carry an explicit statement distinguishing "software-wallet assurance" from "hardware-backed assurance" as defined in §4 of the draft. The backlog item `openid4vci-hdk-issuance-investigation.md` already flags this as a prerequisite for any future issuance integration. No code change is required; documentation update only.

---

### 3.6 Credential Identifier Binding — Conformant and Improved

**Draft (§7)**: The index `i` is a 32-bit unsigned value supplied by the issuer as part of the issuance context.

**Chimali local adaptation**: The full decoded credential ID (16–1023 bytes) is fed into the HKDF `credentialInfo` function to derive the per-credential secret, replacing the draft's issuer-supplied index. The local FIDO2 application fixes `i = 0` in the child derivation. This means each credential effectively has a unique `local_secret`, making per-credential unlinkability a property of the HKDF step rather than the index.

**Assessment**: This is an improvement over the draft's intent for local use cases, since credential IDs are cryptographically random (FIDO2 MakeCredential generates them with secure random) and of sufficient length.

**Finding**: ✅ Design is stronger than required for local use. No change needed.

---

### 3.7 Hierarchy Depth (§8) — Intentional Limitation

**Draft (§8)**: The draft supports multi-level key hierarchies through recursive application of the child derivation formula.

**Chimali**: The `HdkManager` interface exposes `deriveChild` and `deriveChildPrivateKey`, which can be composed. The test `repeated child composition preserves the scalar and point relation` in `HdkP256Test` verifies the mathematical property. However, the production FIDO2 application only uses depth 1 (index 0).

**Finding**: ✅ Mathematical correctness is verified. Depth-1 restriction is intentional and appropriate for the current local FIDO2 use case. No production API for deeper hierarchies is needed.

---

### 3.8 Capability Metadata (§9) — Out of Scope

**Draft (§9)**: Defines how wallets advertise HDK capability in OpenID4VCI credential offer metadata using the `hdk` proof type identifier.

**Chimali**: No OpenID4VCI capability is advertised. This is correctly excluded in the backlog item.

**Finding**: 🚫 Out of scope. No action required for current release.

---

### 3.9 Security Considerations (§11) — Partially Addressed

The draft's §11 raises several security concerns. Chimali's status against each:

| Draft §11 item | Chimali status |
|---|---|
| Parent compromise reconstructs all child keys | ✅ Documented in security-review.md |
| Factor + child-private key reveals parent (inversion) | ✅ Factors are operation-scoped; `eraseSecrets()` called in `finally` |
| Batch key derivation without parent private key | ✅ `deriveChild(public-only)` path exists in `HdkManager` |
| Index space exhaustion / collision | ✅ Mitigated by HKDF-bound `local_secret`; credential IDs are unique |
| Scalar bias from modulo mapping | ✅ Documented in contract: "Its specified small scalar bias is retained" |
| Oracle attack: distinguishing valid from rejected scalars | ✅ No rejected scalars possible — the `(mod n-1)+1` mapping always yields `[1, n-1]` |

**Open finding (LOW)**: The `(mod n-1)+1` scalar mapping introduces a slight bias toward lower values (the range `[1, 1]` has probability proportional to 1/(n-1) extra vs. uniform on `[1, n-1]`). The draft explicitly retains this bias. Chimali's contract documentation acknowledges it but does not quantify it. For a 256-bit group this bias is cryptographically negligible (~2⁻²⁵⁶). No code change is needed, but a single comment in the code or contract could quantify this.

**Recommendation E-3**: Add one sentence to the `HdkP256.deriveChild` KDoc or the contract document quantifying the scalar bias as negligible (`~1/(n-1) ≈ 2⁻²⁵⁶` advantage per output bit).

---

## 4. Technical Debt Findings

### 4.1 `BigInteger` Non-Erasability — Known Residual

**Location**: [`HdkP256.kt`](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkP256.kt#L62-L65), [`P256ParentKeyDerivation.kt`](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256ParentKeyDerivation.kt#L76-L78), [`P256Group.kt`](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt#L96)

**Issue**: Java `BigInteger` is immutable. Intermediate scalar values computed during factor derivation, child-private computation, and scalar serialization exist as JVM heap objects that cannot be zeroed.

**Assessment**: This is a known, documented, and accepted residual limit. The security review explicitly states:

> "Java BigInteger values used for scalar arithmetic are immutable and not erasable."

Application-owned byte arrays are erased. The JVM limitation is not resolvable without replacing the arithmetic engine.

**Recommendation TD-1**: No code change is possible without replacing Bouncy Castle's EC arithmetic. Document this as a permanent residual in a comment near each `BigInteger` usage site for clarity. The existing security-review documentation already covers this; add a cross-reference `@see security-review.md` to the relevant KDoc blocks.

---

### 4.2 ML-DSA Key Object Non-Erasability — Known Residual

**Location**: [`PostQuantumCrypto.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt#L61-L73)

**Issue**: BC 1.80's `MLDSAPrivateKey` object contains private byte-array fields that cannot be erased by application code. The application zeroes its owned `xi` copy and `privateKeyEncoding` copy, but the provider's internal state is not erased.

**Assessment**: Correctly handled at application-ownership boundary. Documented in security review.

**Recommendation TD-2**: When BC ≥ 1.81 adds a `destroy()` method to `MLDSAPrivateKey`, adopt it. Track BC release notes for this. No change needed now.

---

### 4.3 `WalletMasterSeedProvider.provisionSearchableMetadataKeys` Uses HMAC-SHA512 — Inconsistency

**Location**: [`WalletMasterSeedProvider.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt#L340-L354)

**Issue**: Searchable metadata key derivation uses `HMAC-SHA512(ASCII("chimali_searchable_metadata_v1"), PRK)` rather than an HKDF expand from the same PRK used for credential derivation. This creates a second, distinct KDF in the codebase (not HKDF expand) that is not documented in the [local derivation contract](../specs/055-migrate-hdk-draft/contracts/local-key-derivation.md).

**Assessment**: Not a security bug — the construction is domain-separated. However, it is inconsistent with the documented policy:
1. It uses HMAC in "MAC" mode (data-before-key) rather than `HKDF-Expand` semantics.
2. It uses SHA-512 while the rest of the local profile uses SHA-256.
3. It is not covered by the derivation contract document.

**Recommendation TD-3 (MEDIUM)**: Replace the ad-hoc `HMAC-SHA512(label, PRK)` with `HKDF-Expand(PRK, ASCII("chimali_searchable_metadata_v1"), 32)` using the existing `HkdfSha256.expand`. Update the contract document to include the metadata key expansion. This aligns the entire wallet secret surface under one documented KDF policy.

```kotlin
// Proposed replacement in provisionSearchableMetadataKeys:
val label = SEARCHABLE_METADATA_EXPANSION_KEY.toByteArray(StandardCharsets.US_ASCII)
val metadataKey = HkdfSha256.expand(prk, label, METADATA_KEY_SIZE_BYTES)
```

> [!IMPORTANT]
> This is a breaking change for encrypted metadata stored on disk. Since backward compatibility is explicitly excluded (clean-install scope), this change is safe to make in the same install scope as feature 055.

---

### 4.4 Missing Contract Documentation for Metadata Key Derivation

**Location**: [`contracts/local-key-derivation.md`](file:///d:/octav/source/repos/Chimali/specs/055-migrate-hdk-draft/contracts/local-key-derivation.md)

**Issue**: The contract document covers P-256, Ed25519, and ML-DSA-65 credential branches but makes no mention of the searchable-metadata key derivation used by `HmacMetadataLookupTokenService` and `AesGcmEncryptedMetadataService`.

**Recommendation TD-4**: Add a "Non-signature consumers" section to the contract documenting the metadata key expansion: label, input, output length, and the consuming services.

---

### 4.5 Ed25519 `generatePublicKey().encoded` Returns JCA X.509 Encoding

**Location**: [`Fido2CryptoService.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt#L212)

```kotlin
return Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
```

**Issue**: `Ed25519PublicKeyParameters.encoded` returns the raw 32-byte public key (Bouncy Castle lightweight API). This is correct for CTAP2 COSE encoding. However, in `decodePublicKey` for Ed25519, the code prefixes the 32-byte key with `ED25519_X509_PREFIX` to reconstruct a JCA `X509EncodedKeySpec`. The encoding assumptions are implicit and distributed across two methods.

**Recommendation TD-5 (LOW)**: Add a comment at both call sites clarifying which encoding is used at each boundary. The encoding contract is currently enforced by the `ED25519_X509_PREFIX` constant but is not commented.

---

### 4.6 `PBKDF2_ITERATIONS = 2048` — Below Current BIP39 Recommendation

**Location**: [`Bip39MasterSeedGenerator.kt`](file:///d:/octav/source/repos/Chimali/core/security/src/androidMain/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGenerator.kt#L294)

**Issue**: The BIP39 specification mandates 2048 PBKDF2-HMAC-SHA512 iterations. This matches the constant. However, modern BIP39 wallets often use higher iteration counts (e.g., 210,000 for PBKDF2-SHA256 per OWASP 2023). The BIP39 constant of 2048 is the minimum mandated by the standard and is deliberately low for compatibility.

**Assessment**: Technically correct per BIP39 spec. OWASP guidance applies to password storage, not BIP39 seed derivation (different threat model — attacker needs the mnemonic to brute-force, not just the hash). The 2048-iteration count is the BIP39 standard value. No change is required, but the rationale should be documented.

**Recommendation TD-6 (LOW)**: Add a comment to the `PBKDF2_ITERATIONS` constant explaining that 2048 is the BIP39 specification value and that the security property derives from mnemonic entropy (256 bits), not iteration count. This answers future auditor questions.

---

### 4.7 P256 `ECNamedCurveSpec` Constructed in `signP256Scalar` and `decodeP256Point` — Minor Inefficiency

**Location**: [`Fido2CryptoService.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt#L330-L341)

**Issue**: `ECNamedCurveSpec` is constructed on every P-256 signing call and in `decodeP256Point`. It wraps `P256Group.G.curve`, `P256Group.G`, `P256Group.ORDER`, and cofactor into a JCA curve spec. This object is immutable and could be a cached companion object constant.

**Recommendation TD-7 (LOW)**: Extract the `ECNamedCurveSpec` construction into a `companion object` constant in `Fido2CryptoService`. This is a minor performance improvement (avoids 2–3 object allocations per signing call) with no security implications.

```kotlin
private companion object {
    val EC_CURVE_SPEC = org.bouncycastle.jce.spec.ECNamedCurveSpec(
        "secp256r1",
        P256Group.G.curve,
        P256Group.G,
        P256Group.ORDER,
        P256Group.G.curve.cofactor,
    )
    // ... existing constants
}
```

---

### 4.8 `HdkManager.deriveChild` Public Interface Exposes `index: UInt` — Review

**Location**: [`HdkManager.kt`](file:///d:/octav/source/repos/Chimali/core/security/src/commonMain/kotlin/com/chimali/core/security/api/HdkManager.kt)

**Issue**: The `HdkManager` interface exposes `index: UInt` as a parameter, which allows callers to pass any 32-bit index. In the current production FIDO2 application, only index 0 (`LOCAL_CREDENTIAL_INDEX = 0u` in `Fido2CryptoService`) is ever used. If the application inadvertently called this with a non-zero index, it would silently produce a different child key.

**Recommendation TD-8 (LOW)**: Consider removing the `index` parameter from `HdkManager.deriveChild` (the public API) and leaving it only on the internal implementation. The index is part of the draft's issuance protocol (to batch credentials from the same parent at an issuer), not of Chimali's local FIDO2 profile. Alternatively, document in the interface KDoc that index 0 is the mandated local value and that other indices are reserved.

> [!WARNING]
> Removing the parameter is an API break, but since there are no external callers and this is within the clean-install scope, it is safe to do.

---

## 5. Enhancements Aligned with the Draft

### 5.1 Explicit Conformance Statement in Code (Not Just Contract)

**Issue**: The conformance statement "Chimali's local P-256 profile adapts the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00`..." exists in the contract document but is not referenced from the primary implementation classes.

**Recommendation E-4**: Add a `@see` reference to `HdkP256.kt` pointing to both the contract document and the draft section. This makes auditor traversal from code to specification trivial.

```kotlin
/**
 * Implements Chimali's local P-256 child relation from the byte-only HDK contract.
 *
 * Adapts the scalar mapping and child-key relation in §7 of draft-dijkhuis-hdk-00,
 * substituting a deterministic RFC 5869 local secret for the draft's X25519 shared_secret.
 * This is a local custom protocol; it does not implement the draft's OpenID4VCI issuance flow.
 *
 * @see <a href="https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html#section-7">
 *     draft-dijkhuis-hdk-00 §7 P-256 Profile</a>
 * @see contracts/local-key-derivation.md
 */
```

---

### 5.2 Validate `local_secret` Is Not All-Zero

**Issue**: The contract states that the `(mod n-1)+1` mapping always produces a non-zero factor regardless of the input. However, it does not address what happens if `local_secret` itself is all-zero (which would happen if HKDF-Expand produces a zero output — theoretically possible but computationally negligible).

The draft does not define rejection for an all-zero `shared_secret` in the P-256 path, and the contract explicitly states:

> "Do not invent a rejection rule for an all-zero HKDF output."

**Assessment**: The contract correctly handles this. The `(mod n-1)+1` mapping means even an all-zero input produces a non-zero factor. No validation is needed.

**Finding**: ✅ No change needed. Document this reasoning in `HdkP256.deriveChild` KDoc if not already present.

---

### 5.3 Consider HKDF-SHA256 for `WalletSessionMemoryManager` (if it exists)

**Location**: [`WalletSessionMemoryManager.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletSessionMemoryManager.kt) (referenced but not read)

The memory manager may have independent KDF usage. Review it against the unified derivation contract.

**Recommendation E-5**: Audit `WalletSessionMemoryManager.kt` to confirm it uses no independent key derivation not covered by the contract.

---

## 6. Summary of Recommendations

| ID | Severity | Category | Description | File(s) |
|---|---|---|---|---|
| E-1 | LOW | Documentation | Add comment explaining P-256 cofactor=1 implies subgroup membership | `P256Group.kt` |
| E-2 | LOW | Documentation | Add explicit "software wallet assurance" label to BRD/TRD | `docs/brd.md`, `docs/trd.md` |
| E-3 | LOW | Documentation | Quantify scalar bias in KDoc or contract | `HdkP256.kt` or contract |
| E-4 | LOW | Documentation | Add `@see` draft reference to `HdkP256.kt` | `HdkP256.kt` |
| E-5 | LOW | Review | Audit `WalletSessionMemoryManager.kt` against unified contract | `WalletSessionMemoryManager.kt` |
| TD-1 | INFO | Residual | Add `@see security-review.md` cross-reference near `BigInteger` usages | `HdkP256.kt`, `P256Group.kt`, `P256ParentKeyDerivation.kt` |
| TD-2 | INFO | Future | Track BC `destroy()` for ML-DSA keys | `PostQuantumCrypto.kt` |
| **TD-3** | **MEDIUM** | **Technical debt** | **Replace ad-hoc `HMAC-SHA512` metadata key derivation with `HKDF-Expand`; update contract** | **`WalletMasterSeedProvider.kt`, contract** |
| TD-4 | LOW | Documentation | Document metadata key expansion in derivation contract | `contracts/local-key-derivation.md` |
| TD-5 | LOW | Documentation | Comment Ed25519 encoding boundary in `Fido2CryptoService` | `Fido2CryptoService.kt` |
| TD-6 | LOW | Documentation | Comment `PBKDF2_ITERATIONS = 2048` as the BIP39 spec value | `Bip39MasterSeedGenerator.kt` |
| TD-7 | LOW | Performance | Cache `ECNamedCurveSpec` as companion constant | `Fido2CryptoService.kt` |
| TD-8 | LOW | API | Remove or restrict `index` parameter on `HdkManager.deriveChild` | `HdkManager.kt`, `HdkP256.kt` |

---

## 7. Non-Findings (Explicitly Verified Correct)

The following areas were reviewed and found conformant:

- **Factor non-zero guarantee**: `(OS2IP(digest) mod (n−1)) + 1` always in `[1, n−1]`. ✅
- **Context encoding**: `SEC1_UNCOMPRESSED(pk_parent) || I2OSP(index, 4)`. ✅
- **Parent sampler range**: Counters 0–255 inclusive. ✅
- **Suite ID encoding**: `KEM || I2OSP(0x0010, 2)`. ✅
- **Labeled HKDF extraction**: `HPKE-v1 || suiteId || label || ikm`. ✅
- **Child public key derived from parent (not G)**: `pk_child = pk_parent * factor`. ✅
- **Child private scalar**: `sk_child = sk_parent * factor mod n`. ✅
- **Root PRK derivation**: `HKDF-Extract(ASCII("chimali-local-key-derivation-v1"), BIP39_seed)`. ✅
- **Credential info framing**: `ASCII(label) || 0x00 || I2OSP(len, 2) || credentialId`. ✅
- **All credential branches isolated**: P-256, Ed25519, ML-DSA-65 use distinct HKDF labels. ✅
- **Session ownership and cache erasure**: PRK and parent scalar erased on session clear or last-owner exit. ✅
- **Independent test vectors**: Frozen in `HdkP256Test`, `HkdfSha256Test`, `P256ParentKeyDerivationTest`. ✅
- **Algorithm/profile pair validation**: `else -> throw IllegalArgumentException` on unknown pairs. ✅
- **Public-key mismatch rejection**: `requireRegisteredPublicKeyMatches` rejects before signing. ✅

---

## 8. Out-of-Scope Items (Confirmed)

The following items from `draft-dijkhuis-hdk-00` are explicitly not implemented and are not considered gaps:

1. **§5 OpenID4VCI issuance protocol** — X25519 KEM, credential offer, authorization, and proof binding. Tracked in [`docs/backlog/openid4vci-hdk-issuance-investigation.md`](file:///d:/octav/source/repos/Chimali/docs/backlog/openid4vci-hdk-issuance-investigation.md).
2. **§9 Capability metadata** — `hdk` proof type advertisement in OpenID4VCI metadata.
3. **§4 Hardware-backed parent** — Android Keystore non-exportable key storage. Acknowledged limitation.
4. **§6 Batch issuance indexing** — Multiple credentials from one issuer request using index > 0. Only index 0 is used locally.
5. **§10 Interoperability** — No wire-format interoperability with other HDK implementations is claimed.

---

## 9. Supplementary Notes from Full Draft Review

The following items were confirmed from reading the complete draft text:

### 9.1 All-Zero X25519 Output Rejection (§5–§6)

The draft requires:
> If `shared_secret` is all zero, the Issuer/Wallet MUST reject the request/response.

This applies **only to the X25519 exchange** in the issuance protocol (§5–§6), not to HKDF outputs. Chimali's local profile uses no X25519 exchange. The contract explicitly states:

> "The X25519 all-zero-output rule belongs to the excluded exchange. Do not invent a rejection rule for an all-zero HKDF output."

**Finding**: ✅ Correctly handled. No action needed.

### 9.2 Draft Uses No HKDF in Child Derivation (§7)

The draft's `H(msg) = (OS2IP(SHA-256(msg)) mod (n-1)) + 1` uses SHA-256 directly, not HKDF. Chimali correctly applies this same function with `local_secret` substituted for `shared_secret`. The HKDF usage in Chimali is in the root/parent derivation layer (from BIP39 seed), not in the child derivation formula itself. These are separate concerns and correctly separated in the implementation.

**Finding**: ✅ No confusion between HKDF and SHA-256 layers.

### 9.3 Draft Interoperability Explicitly Not Inherited (§10)

The draft §10 states:
> Implementations MUST NOT infer HDK support merely from P-256 support.

Chimali's metadata and capability advertisement make no HDK capability claim. The capability metadata extension (`proof_types_supported: {"hdk": {}}`) is not implemented.

**Finding**: ✅ No interoperability claim is made. No action needed.

### 9.4 ECDSA Blinding Alternative (§8.2) — Not Applicable

The draft §8.2 describes an alternative ECDSA signing method that keeps `sk_parent` in the secure area by computing `NONEwithECDSA(sk_parent, I2OSP(z * b⁻¹ mod n, 32))` with `s * b mod n`. This is for hardware-backed wallets where the parent key cannot be exported.

Chimali derives `sk_child` directly in software and signs with it using `SHA256withECDSA`. Since the parent is software-derived and operation-local, this alternative construction is not needed.

> [!NOTE]
> The draft §8.2 carries an intellectual-property note: this ECDSA construction may be covered by patent claims in some jurisdictions. Since Chimali does not implement this construction, there is no exposure. The standard `sk_child` derivation + direct signing path used by Chimali is not the subject of that note.

**Finding**: ✅ Chimali's signing path avoids the IP-noted construction. No action needed.

---

## 10. Priority Order for Implementation

Given the findings above, the recommended implementation order (highest value / lowest risk first):

1. **TD-3** — Replace metadata key HMAC-SHA512 with HKDF-Expand + update contract. *(Medium priority: consistency + auditability)*
2. **TD-4** — Add metadata key to contract document. *(Should accompany TD-3)*
3. **E-4** — Add draft `@see` reference to `HdkP256.kt`. *(Low effort, high audit value)*
4. **E-1, E-3** — Add cofactor and scalar-bias comments. *(Documentation only)*
5. **E-2** — Add software-assurance label to BRD/TRD. *(Documentation only)*
6. **TD-7** — Cache `ECNamedCurveSpec`. *(Low effort performance gain)*
7. **TD-8** — Restrict or remove `index` parameter on `HdkManager`. *(API cleanup, safe in clean-install scope)*
8. **TD-5, TD-6, TD-1** — Inline documentation improvements. *(Lowest priority)*
