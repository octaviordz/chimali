# Cryptographic Terminology Audit

**Scope**: `constitution.md`, `brd.md`, `trd.md`
**Date**: 2026-05-14
**Objective**: Identify statements where a cryptographic algorithm, mechanism, or primitive is described as something it factually is not.

---

## Severity Legend

| Level | Meaning |
|-------|---------|
| 🔴 **Critical** | The statement is factually wrong and could lead to incorrect implementation decisions |
| 🟡 **Moderate** | The statement is imprecise or misleading but the intent is recoverable from context |
| 🔵 **Minor** | Loose language that a domain expert would read correctly but could confuse a new contributor |

---

## Findings

### Finding 1 — ML-DSA called a "cryptographic method" (implies encryption)

| | |
|---|---|
| **File** | `brd.md` line 84 |
| **Severity** | 🔴 Critical |
| **Quote** | *"If the device supports Quantum-Resistant (Post-Quantum Cryptography) algorithms (e.g., ML-DSA-65), the application must utilize these as the **primary cryptographic method**."* |
| **Problem** | ML-DSA (FIPS 204) is a **digital signature algorithm**, not a general-purpose "cryptographic method." It cannot encrypt data, perform key exchange, or replace AES-256 for payload encryption. Describing it as the "primary cryptographic method" implies it would replace the symmetric encryption strategy (AES-256-GCM/SIV), which is neither possible nor desirable. |
| **Fix** | Replace with: *"The application SHOULD support Post-Quantum digital signature schemes (e.g., ML-DSA-65) for FIDO2 attestation and assertion signing, as defined in NFR-SEC-040."* |

---

### Finding 2 — SLIP-10 described as supporting PQC key derivation

| | |
|---|---|
| **File** | `brd.md` line 87 |
| **Severity** | 🔴 Critical |
| **Quote** | *"The architecture accommodates **Hybrid Hierarchical Deterministic Derivation (HHD)** to allow deterministic derivation of both classical (e.g., ES256, ECDSA/Ed25519) and Post-Quantum (e.g., ML-DSA-65, Falcon-512) signature schemes from the single BIP39 root seed using standard paths (BIP-44 / SLIP-10 adaptations)."* |
| **Problem** | Multiple factual issues: **(a)** SLIP-10 is defined exclusively for `secp256k1`, `ed25519`, and `nist256p1` curves. It has no PQC support and cannot derive ML-DSA or Falcon keys. **(b)** "HHD" is not a recognized cryptographic standard — it appears to be a project-invented term. Constitution Principle X.5 prohibits cryptographic invention without a formal security review. **(c)** The constitution (Principle II) now mandates HDK per `draft-dijkhuis-cfrg-hdkeys-06` with a `DeriveSalt`-based PQ branch, making the BIP-44/SLIP-10 reference stale and contradictory. **(d)** Falcon-512 is not referenced anywhere else in the project; the actual PQ scheme in use is ML-DSA. |
| **Fix** | Align with constitution Principle II: *"Credential keys are derived using Hierarchical Deterministic Key derivation following IETF `draft-dijkhuis-cfrg-hdkeys-06` (HDK-ECDH-P256). The Post-Quantum (ML-DSA) key branch is cryptographically isolated via HDK `DeriveSalt` with a dedicated context string."* Remove "HHD", SLIP-10, BIP-44, and Falcon-512 references. |

---

### Finding 3 — SLIP-10 referenced for PQC child key derivation in the TRD

| | |
|---|---|
| **File** | `trd.md` line 58 |
| **Severity** | 🔴 Critical |
| **Quote** | *"**Child Key Derivation**: SLIP-10 for unified derivation of EC and EdDSA/PQC keys."* |
| **Problem** | Same as Finding 2. SLIP-10 is defined for three specific elliptic curves. It cannot derive PQC (lattice-based) keys. The claim of "unified derivation" across EC, EdDSA, and PQC via SLIP-10 is factually incorrect. The project uses HDK `DeriveSalt` (constitution Principle II) for PQ branch isolation, not SLIP-10. |
| **Fix** | Replace with: *"**Child Key Derivation**: HDK per IETF `draft-dijkhuis-cfrg-hdkeys-06`. Classical (P-256) keys use HDK blinding; PQ (ML-DSA) keys use `DeriveSalt`-based branch isolation."* |

---

### Finding 4 — ML-KEM-768 listed under "Encryption Standards"

| | |
|---|---|
| **File** | `trd.md` line 64 |
| **Severity** | 🔴 Critical |
| **Quote** | Section header: *"5.1 Encryption Standards (NFR-SEC-010)"* containing: *"**Post-Quantum**: ML-KEM-768 for key encapsulation (via BouncyCastle 1.80+)."* |
| **Problem** | While the bullet itself correctly says "key encapsulation," placing ML-KEM-768 under a section titled **"Encryption Standards"** misclassifies it. ML-KEM (FIPS 203) is a Key Encapsulation Mechanism — it produces a shared secret that could feed into a symmetric cipher, but it is not itself an encryption algorithm. Listing it alongside AES-256-GCM and AES-256-SIV (which *are* encryption algorithms) implies ML-KEM serves the same function. Additionally, the project is local-first with no key exchange protocol; there is no architectural use case for ML-KEM in the current design. |
| **Fix** | Either **(a)** move ML-KEM to its own subsection (e.g., "5.3 Key Encapsulation") with an explicit use-case justification, or **(b)** remove it if no concrete key exchange flow exists. If retained, add: *"ML-KEM-768 is designated for future remote key provisioning flows (see TR-UI-080) and is not used for data encryption."* |

---

### Finding 5 — "HSM" used to describe Android Hardware Keystore

| | |
|---|---|
| **File** | `brd.md` line 31 |
| **Severity** | 🟡 Moderate |
| **Quote** | *"Implement industry-standard (AES-256) and Post-Quantum Cryptography (PQC) encryption, leveraging Android's **Hardware Security Module (HSM)**."* |
| **Problem** | Android does not expose a generic "HSM." The correct term is **Android Keystore** backed by a **Trusted Execution Environment (TEE)** or, on supported devices, a **StrongBox Secure Element (SE)**. "HSM" is an industry term for dedicated, tamper-resistant hardware appliances (e.g., Thales Luna, AWS CloudHSM) — a different security model from the TEE/SE architecture Android uses. This could mislead a developer into assuming FIPS 140-2/3 Level 3 guarantees that the Android Keystore does not provide. |
| **Fix** | Replace with: *"…leveraging the **Android Keystore** (TEE-backed, StrongBox-preferred where available)."* This matches the constitution's technical constraints and `brd.md` line 85 which correctly uses "Android KeyStore." |

---

### Finding 6 — PBKDF2-HMAC-SHA512 described as "Seed Derivation"

| | |
|---|---|
| **File** | `trd.md` line 57 |
| **Severity** | 🟡 Moderate |
| **Quote** | *"**Seed Derivation**: PBKDF2-HMAC-SHA512."* |
| **Problem** | In BIP-39, the mnemonic-to-seed function uses PBKDF2 with HMAC-SHA512 as the PRF, 2048 iterations, and the passphrase "mnemonic" + optional password as the salt. This is technically correct but the label "Seed Derivation" is imprecise — PBKDF2 is a **password-based key derivation function** designed to slow brute-force attacks on low-entropy inputs. In BIP-39's context it's used as a **mnemonic stretching function**, not as a general KDF. The distinction matters because the constitution (Principle II) separately states the master seed is stored encrypted via AES-256-GCM, and HKDF-SHA256 is used for child key material. A reader could confuse which KDF is used where. |
| **Fix** | Clarify: *"**Mnemonic-to-Seed Stretching**: BIP-39 PBKDF2(HMAC-SHA512, 2048 iterations) converts the mnemonic phrase into the 512-bit master seed."* |

---

### Finding 7 — BouncyCastle framed as enabling ML-KEM and ML-DSA equally

| | |
|---|---|
| **File** | `trd.md` line 21 |
| **Severity** | 🟡 Moderate |
| **Quote** | *"**Security Leadership**: Integration of BouncyCastle 1.80 for ML-KEM and ML-DSA support."* |
| **Problem** | This groups ML-KEM and ML-DSA together as if they serve the same purpose. ML-KEM is a Key Encapsulation Mechanism (key exchange); ML-DSA is a Digital Signature Algorithm (authentication/signing). They are different primitive categories solving different problems. The statement also implies both are actively used, but only ML-DSA has an architectural role (FIDO2 attestation signing via the PQ branch). ML-KEM has no current use case in the local-first architecture. |
| **Fix** | Separate the primitives: *"Integration of BouncyCastle 1.80+ for **ML-DSA** (Post-Quantum digital signatures for FIDO2 attestation). ML-KEM support is available for future remote key provisioning flows."* |

---

### Finding 8 — "ES256" listed as a signature scheme name

| | |
|---|---|
| **File** | `brd.md` line 87 |
| **Severity** | 🔵 Minor |
| **Quote** | *"classical (e.g., ES256, ECDSA/Ed25519)"* |
| **Problem** | ES256 is a **JOSE/COSE algorithm identifier** (ECDSA using P-256 and SHA-256), not a signature scheme name. The underlying scheme is ECDSA. Listing "ES256" alongside "ECDSA" is redundant — ES256 *is* ECDSA with specific parameters. The `trd.md` line 67 correctly uses "P-256 (COSE -7)" which properly separates the curve from the algorithm identifier. |
| **Fix** | Use: *"classical (ECDSA/P-256, Ed25519)"* — referencing the actual signature schemes. Reserve "ES256" and COSE identifiers for wire-protocol and COSE-specific contexts. |

---

### Finding 9 — Stale BIP-32/BIP-44/BIP-85 references contradict constitution

| | |
|---|---|
| **File** | `brd.md` line 87 |
| **Severity** | 🟡 Moderate |
| **Quote** | *"using standard paths (BIP-44 / SLIP-10 adaptations)"* |
| **Problem** | The constitution (Principle II, line 38) explicitly states the architecture *eliminates* "legacy BIP-32 style components" and does not rely on "mixed BIP-44/BIP-32 patterns." The BRD still references BIP-44 and SLIP-10 as the derivation mechanism, directly contradicting the governing document. This is not a terminology error per se, but a **factual inconsistency** between documents that could lead to implementing the wrong derivation scheme. |
| **Fix** | Update NFR-SEC-040 to reference HDK per `draft-dijkhuis-cfrg-hdkeys-06`, matching the constitution. Remove BIP-44 and SLIP-10 references from key derivation context. BIP-39 for mnemonic generation may remain since it is still in use. |

---

## Summary Matrix

| # | File | Line | Severity | Category |
|---|------|------|----------|----------|
| 1 | brd.md | 84 | 🔴 | ML-DSA misclassified as encryption |
| 2 | brd.md | 87 | 🔴 | SLIP-10 cannot derive PQC keys; HHD is invented |
| 3 | trd.md | 58 | 🔴 | SLIP-10 cannot derive PQC keys |
| 4 | trd.md | 64 | 🔴 | ML-KEM listed under "Encryption Standards" |
| 5 | brd.md | 31 | 🟡 | HSM ≠ Android Keystore/TEE |
| 6 | trd.md | 57 | 🟡 | PBKDF2 role imprecisely labeled |
| 7 | trd.md | 21 | 🟡 | ML-KEM and ML-DSA conflated |
| 8 | brd.md | 87 | 🔵 | ES256 is an identifier, not a scheme name |
| 9 | brd.md | 87 | 🟡 | Stale BIP-44/SLIP-10 contradicts constitution |

**Total**: 4 Critical · 4 Moderate · 1 Minor

> **Note**: The constitution (`constitution.md`) passed this audit cleanly after the PQC sentence removal applied earlier in this session. All critical findings are in the BRD and TRD, which appear to predate the HDK migration and have not been updated to reflect the current architecture defined in Constitution Principle II.
