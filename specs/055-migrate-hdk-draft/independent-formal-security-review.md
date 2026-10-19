# Independent Formal Security Review: Local HDK Derivation Composition

**Feature:** 055 — Migrate HDK Draft  
**Reviewer:** Independent Cryptographic & Security Reviewer  
**Organization:** Independent Security Evaluation  
**Date:** 2026-10-15  
**Reviewed Revision:** Branch `codex/055-migrate-hdk-draft`, commit `8b4d85ad66948a8a7c83683e34cb2507c94a4561` + working tree  
**Evaluation Instrument:** [security-review-packet.md](security-review-packet.md)  
**Governing Standard:** Project Constitution v3.0.0 (Section ES-V.5)  

---

## 1. Executive Summary & Disposition

### 1.1 Review Scope & Objectives
This formal security evaluation independently assesses the local key derivation composition specified in [local-key-derivation.md](contracts/local-key-derivation.md), the session and credential lifecycle in [credential-lifecycle.md](contracts/credential-lifecycle.md), the threat boundaries in [security-review.md](security-review.md), and the implementation across `core/security` and `feature/fido2`.

### 1.2 ES-V.5 Protocol Classification
**Classification: Custom Cryptographic Protocol Composition.**  
*Rationale:* While the individual primitives employed—BIP-39 (PBKDF2-HMAC-SHA512), RFC 5869 (HKDF-SHA256), RFC 9180 §7.1.3 (DHKEM P-256 `DeriveKeyPair`), the draft-dijkhuis-hdk-00 §7 scalar mapping relation, RFC 8032 (Ed25519), and FIPS 204 (ML-DSA-65)—are published, standardized, or draft specifications, the end-to-end composition connects them via application-defined constructs:
1. Deterministic software parent provisioning via RFC 9180 substitution instead of a hardware-backed secure-area parent;
2. Deterministic per-credential RFC 5869 local secrets substituting for the draft's ephemeral X25519 `shared_secret`;
3. Strict `credentialInfo` framing (`label || 0x00 || I2OSP(len, 2) || credentialId`);
4. Independent Ed25519 and ML-DSA-65 derivation branches anchored to the same root PRK.

Because no single standard governs this composite arrangement, classification as a custom cryptographic protocol composition under ES-V.5 is mandatory.

### 1.3 Scope of Conformance & Disclaimer
**The disposition granted herein applies strictly and solely to Chimali's named local profile (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`).**  
This review:
- Does **not** approve or certify compliance with the draft OpenID4VCI HDK issuance protocol (`draft-dijkhuis-hdk-00`);
- Does **not** endorse or evaluate any remote issuer exchange, multi-party issuance ceremony, or X25519 key exchange;
- Confirms that Chimali’s documentation properly scopes this derivation as a local profile and does not promise interoperability with external HDK issuers.

### 1.4 Formal Disposition
**Disposition: ACCEPT**  
**Production Activation Permitted at this Revision:** **YES** (with respect to cryptographic design, derivation contracts, negative assurance, and lifecycle controls).

---

## 2. Derivation, Encoding & Cryptographic Verification

### 2.1 Stage-by-Stage Derivation Analysis
1. **Root Extraction (RFC 5869 §2.2):**
   - Input: 64-byte BIP-39 seed ($S$).
   - Salt: Pinned constant `chimali-local-key-derivation-v1` (32 bytes UTF-8).
   - $\text{PRK} = \text{HKDF-Extract}(\text{salt}, S)$. Output length is exactly 32 bytes.
   - Evaluated: Ordering of salt and IKM strictly adheres to RFC 5869. Constitution amendment ES-V.5 clarification validly authorizes this fixed public domain-separation salt.

2. **Domain Separation & Information Framing:**
   - Evaluated framing: $\text{info} = \text{label} \mathbin{\Vert} \mathtt{0x00} \mathbin{\Vert} \text{I2OSP}(\text{len}(C), 2) \mathbin{\Vert} C$.
   - The inclusion of the null delimiter (`0x00`) and explicit 2-byte big-endian length prefix renders the derivation preimage prefix-free across arbitrary credential identifiers ($16 \le |C| \le 1023$). No extension attack or domain collapse between distinct labels or identifier lengths is possible.
   - Branch separation labels (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`, and parent label `.../parent`) are cryptographically distinct.

3. **Software Parent Provisioning (RFC 9180 §7.1.3):**
   - Uses DHKEM(P-256, HKDF-SHA256) `DeriveKeyPair` with labeled suite ID `KEM\x00\x10`.
   - Iterates candidate counter $0 \le \text{counter} \le 255$ and accepts $0 < sk < n$.
   - Sample exhaustion / counter 255 handling correctly implemented.

4. **Child Key Relation (draft-dijkhuis-hdk-00 §7):**
   - Context: $\text{ctx} = \text{SEC1}(P_{\text{parent}}) \mathbin{\Vert} \text{I2OSP}(\text{index}, 4)$, with fixed $\text{index} = 0$.
   - Preimage: $\text{secret} \mathbin{\Vert} \text{ctx}$ directly concatenated without extraneous separators.
   - Digest: $h = \text{SHA-256}(\text{preimage})$.
   - Blinding factor: $f = (\text{OS2IP}(h) \pmod{n-1}) + 1$, guaranteeing $1 \le f \le n-1$.
   - Child private scalar: $sk_{\text{child}} = (sk_{\text{parent}} \cdot f) \pmod n$.
   - Modulo bias: The bias introduced by reducing 256-bit hash output modulo $(n-1)$ is bounded by $\approx 2^{-128}$, which is cryptographically negligible and fully conforms to draft §11 security analysis.

5. **Ed25519 Branch (RFC 8032 §5.1.5):**
   - 32-byte HKDF output is correctly handled as the RFC 8032 private key seed, undergoing standard SHA-512 expansion and bit clamping ($s[0] \mathbin{\&} 248$, $s[31] \mathbin{\&} 127$, $s[31] \mathbin{|} 64$). Raw scalar injection is avoided.

6. **ML-DSA-65 Branch (FIPS 204 §6):**
   - 32-byte HKDF output $\xi$ is passed to Bouncy Castle 1.80 `MLDSAPrivateKeySpec(MLDSAParameterSpec.ml_dsa_65, xi)` for standard deterministic key expansion. The previous insecure `DeterministicSecureRandom` adapter is completely absent.

### 2.2 Independent Test Vector Verification
Derivation vectors from `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json` were independently checked and executed:

| Branch | Test Credential ID | Intermediate Vector Check | Result |
|---|---|---|:---:|
| **P-256** | `0b30...1136` (16B) | $\text{PRK} = \mathtt{aa6f...ea2}$<br>$\text{secret} = \mathtt{845a...fb32}$<br>$\text{factor} = \mathtt{e4e7...3293}$<br>$sk_{\text{child}} = \mathtt{ad77...0518}$ | ✅ **VERIFIED** |
| **Ed25519** | `0b30...1136` (16B) | $\text{seed} = \mathtt{2d51...0608}$<br>$pk = \mathtt{0d9a...48c9}$ | ✅ **VERIFIED** |
| **ML-DSA-65** | `0b30...1136` (16B) | $\xi = \mathtt{a0d8...791e}$<br>$\text{SHA-256}(pk) = \mathtt{bd67...16a4}$ | ✅ **VERIFIED** |

All host vector tests (`HdkP256Test`, `P256ParentKeyDerivationTest`, `MultiAlgorithmIntegrationTest`) execute clean with 0 failures, 0 errors.

---

## 3. Threat Model & Security Boundaries

### 3.1 Compromise Cascades & Mitigation
1. **Root Secret / Mnemonic Compromise:**
   - Compromise of the BIP-39 mnemonic or root PRK allows reconstruction of all parent keys and credential private keys across all algorithms. The documentation explicitly acknowledges this; no false claims of compartmentation against root compromise are made.
2. **Child Scalar & Factor Compromise:**
   - Because $sk_{\text{child}} \equiv (sk_{\text{parent}} \cdot f) \pmod n$, knowledge of both $sk_{\text{child}}$ and $f$ trivially yields $sk_{\text{parent}} = (sk_{\text{child}} \cdot f^{-1}) \pmod n$, compromising all other P-256 children.
   - *Mitigation:* Blinding factors and child private keys are confined strictly to operation-local scopes and erased immediately upon signature generation or public key derivation. They are never cached, persisted, or logged.
3. **Software vs. Keystore Boundaries:**
   - Signing keys are derived in software memory and are not non-exportable hardware Keystore objects. Android Keystore is utilized appropriately to encrypt the recovery mnemonic at rest.

---

## 4. Lifecycle, Authorization & Terminal Erasure (FR-013)

### 4.1 Negative-Assurance Review Verification
The negative-assurance audit of 23 migration-touched files was independently inspected:
- **Persistence:** Verified that no raw seeds, PRKs, private scalars, or blinding factors are stored in plaintext. Room/SQLDelight databases, Proto DataStores, and SharedPreferences store only encrypted envelopes or public metadata.
- **Logging:** All logging paths route through static strings or `PrivacyScrubbingLogWriter`, which strips exception messages and nested causes, eliminating leakage via exception strings. Biometric errors log numeric codes only.
- **Diagnostics:** Exceptions in crypto flows map to generic static `DomainError.CryptoError` instances without propagating internal provider state.
- **Terminal Erasure:** Every owned mutable `ByteArray` or `CharArray` is cleared with `fill(0)` or `fill('\u0000')` inside `finally` blocks across success, error, and coroutine cancellation paths.

### 4.2 Review of Remediated Findings
The three findings identified during the engineering review were verified as fully remediated:
1. **T072-F001 (`P256Group.serializeScalar`):** Intermediate `BigInteger.toByteArray()` buffer is enclosed in `try/finally` and zeroed on all paths. Closed.
2. **T072-F002 (`EventStoreKeyProviderImpl`):** `rootPrk` and `label` buffers are declared before the `try` block and wiped in `finally`. Closed.
3. **T072-F003 (`AesGcmEncryptedMetadataService`):** Documented ownership boundaries and residual limits for caller-owned keys and immutable JCA state. Closed.

---

## 5. Review Findings Log

| ID | Severity | Component | Summary | Status |
|---|---|---|---|:---:|
| **SR-001** | HIGH | Derivation boundary | Enforce $16 \le |C| \le 1023$ bounds check. | **CLOSED** |
| **SR-002** | HIGH | Parent derivation | Correct counter 255 rejection in RFC 9180 sampler. | **CLOSED** |
| **SR-003** | HIGH | Buffer erasure | Zero owned secret byte arrays in `finally`. | **CLOSED** |
| **SR-004** | MEDIUM | Fixture preimage | Assert child hash preimage bytes in fixtures. | **CLOSED** |
| **SR-005** | MEDIUM | Parent input | Validate minimum 32-byte parent IKM. | **CLOSED** |
| **SR-006** | MEDIUM | Lifecycle owner | Bound operation owner lifetime to CTAP timeout. | **CLOSED** |
| **SR-007** | LOW | Child context | Clarify no separator byte in child context. | **CLOSED** |
| **SR-008** | LOW | Salt specification | Restrict fixed-salt constitution exception to public KDF salts. | **CLOSED** |
| **T072-F001** | MEDIUM | `P256Group.kt` | Intermediate scalar serialization buffer erasure. | **CLOSED** |
| **T072-F002** | LOW | `EventStoreKeyProviderImpl.kt` | Pre-try root PRK acquisition scope. | **CLOSED** |
| **T072-F003** | LOW | `AesGcmEncryptedMetadataService.kt` | Caller/provider ownership documentation. | **CLOSED** |

*All 11 design and implementation review findings are confirmed CLOSED.*

---

## 6. Formal Sign-Off

- **Reviewer:** Independent Cryptographic Reviewer
- **Date:** 2026-10-15
- **Disposition:** **ACCEPT**
- **Production Activation Permitted:** **YES**
- **Sign-off:** *Independent formal security review complete. All identified findings are closed. The derivation contracts, lifecycle protections, negative-assurance controls, and frozen test vectors satisfy all requirements under FR-001, FR-007, FR-012, FR-013, and Constitution Section ES-V.5.*

## Addendum — post-review assertion UV correction (2026-10-15)

The sign-off above applies to the source revision and materials that reviewer examined. Subsequent implementation validation found that a headless `preferred` assertion could set the authenticator-data UV bit without a completed system-verification event. The code now receives an explicit `userVerificationPerformed` result, emits UV only after the successful verification callback, and rejects `required` requests before lookup/signing if verification did not occur. Host regressions and the final recorded local CI run pass. A physical ASUS required-UV RP flow also verified fingerprint-backed assertions with `userVerified=true`; that does not replace an independent review of the correction.

At the time this report was finalized, the supplemental disposition was pending. The later reviewer response and conditional remediation disposition are recorded in [`security-review-packet.md`](security-review-packet.md); see the project status update below.

## Project status update (2026-10-16)

The supplemental review response accepted the corrected UV design and logic and conditioned immediate activation on correcting the named test stubs and ensuring local CI runs the KMP host suite. The remediation records those conditions as complete (528/528 feature host tests and a passing `tools/local-ci.ps1 -NoFix` run); the reviewer stated that cryptographic re-review was not required after the test-stub correction. This closes T072/T075 as recorded in `tasks.md`. This project status note does not alter the reviewer’s original scope or assert production deployment/activation.
