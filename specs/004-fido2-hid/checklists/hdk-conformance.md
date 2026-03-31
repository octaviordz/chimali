# HDK Conformance Delta Register

> **Spec version**: IETF `draft-dijkhuis-cfrg-hdkeys-06`  
> **Chimali version**: 0.9.3+  
> **Status**: ACTIVE audit trail  
> **Last updated**: 2026-03-31

This document records every **intentional deviation**, **extension**, or **implementation limitation**
relative to `draft-dijkhuis-cfrg-hdkeys-06`. Entries reference the spec section they affect.

---

## Conformance Status Summary

| Section | Topic | Chimali Status | Notes |
|---------|-------|---------------|-------|
| §2.1 | Ciphersuite HDK-ECDH-P256 | ✅ Conformant | Sole instantiation used |
| §2.6 | GenerateSeed (Ns=32 bytes) | ✅ Conformant | `HdkEcdhP256.generateSeed()` |
| §2.3 | DeriveSalt H(salt \|\| ctx) | ✅ Conformant | Fixed from H(ctx) — see Δ-001 |
| §2.5 | HDK derivation path | ✅ Conformant | Two-level `[FIDO2_APP_INDEX, credIdx]` |
| §3.2.2 | Multiplicative blinding BlindPublicKey/BlindPrivateKey | ✅ Conformant | See Δ-002 |
| §4.1 | Remote key derivation (KEM) | ⚠️ Partial | See Δ-003 |
| §2.5 index domain | uint32 indices | ⚠️ Restricted | See Δ-004 |
| (new) | Ed25519 key derivation | ✅ Non-conflicting extension | See Δ-005 |
| (new) | ML-DSA-65 key derivation | ✅ Non-conflicting extension | See Δ-006 |

---

## Delta Entries

### Δ-001: DeriveSalt Formula Correction

- **Spec §2.3**: `deriveSalt(salt, ctx) = H(salt || ctx)`
- **Previous implementation**: `deriveSalt(salt, ctx) = H(ctx)` — salt was not prepended.
- **Fixed version**: `HdkEcdhP256.deriveSalt()` now correctly computes `SHA-256(salt || ctx)`.
- **Impact**: All derived salts for levels ≥ 2 are different from the pre-fix implementation.
  Existing credentials enrolled with the old derivation **cannot be re-derived** correctly.
  Migration path: full authenticator reset (CTAP2 `authenticatorReset`) and re-enrolment.
- **Verification**: `HdkEcdhP256Test.kt` KATs assert the correct H(salt || ctx) formula.
- **Status**: ✅ Fixed in current build.

---

### Δ-002: blindingFactorBytes Zeroise (Security Enhancement)

- **Spec §3.2.2**: Does not require in-memory zeroing (JVM GC is assumed).
- **Chimali**: Adds an explicit `try/finally` block in `Fido2CryptoService.sign()` that
  calls `blindingFactorBytes.fill(0)` after the signing operation, regardless of success
  or exception. `withBlindedPrivateKey()` already zeroes the blinded private key bytes.
- **Rationale**: Defence-in-depth to prevent sensitive scalar material lingering in heap
  snapshots (NFR-SEC-040).
- **Impact**: No protocol-level impact. Strictly a memory hygiene enhancement.
- **Status**: ✅ Implemented (T183).

---

### Δ-003: Remote Key Derivation (Partial Implementation)

- **Spec §4.1**: Defines a full KEM (Key Encapsulation Mechanism) flow for remote issuers
  to provide key handles that the device decapsulates.
- **Chimali**: `HdkManager.requestRemoteDerivation()` and `acceptRemoteKey()` are present
  as stubs/partial implementations. The ECDH decapsulation logic in `acceptRemoteKey` is
  implemented, but the issuer integration (network transport, key handle format) is not.
- **Impact**: Local two-level FIDO2 derivation is fully functional. Remote derivation
  is deferred to a future release.
- **Status**: ⚠️ Partial. Remote flow is not exercised by any integration test.
- **Tracking**: Phase 8 (future milestone).

---

### Δ-004: Index Domain Restriction (uint32 → non-negative Int)

- **Spec §2.5**: Defines path indices as `uint32` (0 to 2³²−1 = 4,294,967,295).
- **Chimali API**: `HdkManager.deriveHdk(path: List<Int>)` uses signed 31-bit `Int`.
  The implementation validates `index >= 0` and encodes via `I2OSP(index, 4)` (4-byte
  big-endian), which is correct for all indices in [0, 2³¹−1].
- **Why not `List<UInt>`?** Kotlin does provide `UInt` (stable since 1.5, range 0–2³²−1),
  but using it in a generic `List<UInt>` causes **JVM boxing** (`Integer` objects instead
  of primitives), the same heap overhead as `List<Int>`. Additionally, Android SDK APIs
  universally expect `Int` or `Long`, requiring boilerplate conversions at every call site.
  The ergonomic cost outweighs the benefit for this parameter.
- **Gap**: Indices in [2³¹, 2³²−1] are unreachable with the current API. This is an
  **intentional, accepted trade-off**:
  - The configurable credential limit (FR-HID-022, T115a) defaults to 1000, and is
    retrieved at runtime via `Fido2SettingsRepository`.
  - `FIDO2_APP_INDEX` is a fixed small integer.
  - Neither will ever approach the 31-bit ceiling (2³¹−1) even if the limit is 
    increased by an order of magnitude.
- **I2OSP encoding**: Unaffected. `I2OSP(index, 4)` always produces the correct 4-byte
  big-endian representation for any index in the valid range.
- **Status**: ⚠️ Documented limitation. No remediation required for current scope.
  Future revisit if the API is extended to non-FIDO2 use cases with large index spaces.

---

### Δ-005: Ed25519 Derivation (Non-Conflicting Extension)

- **Spec**: The HDK draft is exclusively defined for P-256 (HDK-ECDH-P256).
- **Chimali**: Ed25519 keys use `SHA-512(masterSeed || "Ed25519" || credentialId)[0..31]`,
  an isolated, deterministic branch **outside** the HDK derivation tree.
- **Rationale**: Ed25519 (Curve25519) is incompatible with P-256 multiplicative blinding
  (§3.2.2). The SHA-512 branch satisfies FR-AUTH-030 (determinism) without conflicting
  with the spec's derivation tree.
- **Verification**: `Fido2CryptoServiceTest.kt` contains KATs for Ed25519 signing.
- **Status**: ✅ Intentional, documented extension. No spec violation.

---

### Δ-006: ML-DSA-65 Derivation (Non-Conflicting Extension)

- **Spec**: The HDK draft does not cover post-quantum algorithms.
- **Chimali**: ML-DSA-65 keys use `SHA-512(pqChildSeed || credentialId)` as a seed for
  BouncyCastle `ML-DSA-65` key generation. Completely isolated from the P-256 HDK tree.
- **Rationale**: Post-quantum branch as a future-proofing measure (NFR-SEC-045).
- **Status**: ✅ Intentional, documented extension. No spec violation.

---

## Cryptographic Primitive Mapping

| HDK Primitive | Spec Reference | Chimali Implementation |
|---------------|----------------|----------------------|
| Hash `H` | SHA-256 | `MessageDigest.getInstance("SHA-256")` (JCA) |
| `I2OSP(index, 4)` | §2.3 | `ByteBuffer.allocate(4).putInt(index).array()` |
| Context `ctx` | `ID \|\| I2OSP(index, 4)` | `HdkEcdhP256.createContext()` |
| Salt derivation | `H(salt \|\| ctx)` | `HdkEcdhP256.deriveSalt()` — Fixed in Δ-001 |
| HashToScalar | HKDF-SHA-256 → BigInteger mod n | `HdkEcdhP256.hashToScalar()`, per §3.1 |
| BlindPublicKey | `pk * bf` (EC scalar mult) | `HdkEcdhP256.blindPublicKey()` |
| BlindPrivateKey | `sk * bf mod n` | `HdkEcdhP256.blindPrivateKey()` + `withBlindedPrivateKey()` |
| ShallowHDK (local) | §2.5 | `HdkEcdhP256.deriveHdk()` |
| HDK (multi-level) | §2.5 iterative | `HdkManager.deriveHdk(path = [i0, i1, ..])` |

---

## Test Coverage Map

| Task | Test File | Coverage |
|------|-----------|---------|
| T172 | `HdkEcdhP256Test.kt` | DeriveSalt KATs (correct H(salt\|\|ctx) formula) |
| T173 | `Fido2CryptoServiceTest.kt` | End-to-end 2-level FIDO2 derivation KAT |
| T174 | `MultiplicativeBlindingTest.kt` | BlindPublicKey / BlindPrivateKey KATs |
| T175 | `HdkEcdhP256Test.kt` | createContext boundary tests |
| T182 | `HdkNegativePathTest.kt` | Negative-path robustness (9 tests) |
| T115b | `RegisterCredentialUseCaseTest.kt` | 1000-credential limit (4 tests) |
| T056c | `Ctap2Fido21FlagsTest.kt` | FIDO2.1 CTAP2 flags + credProtect parsing |

---

## Open Items

| ID | Issue | Priority | Resolution |
|----|-------|----------|-----------|
| OI-001 | `acceptRemoteKey` issuer integration missing (Δ-003) | Low | Phase 8 |
| OI-002 | Full uint32 index range unreachable from Kotlin API (Δ-004) | Very low | Acceptable for current scope |
