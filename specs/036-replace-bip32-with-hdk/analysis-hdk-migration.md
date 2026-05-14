# HDK Migration Analysis: Replace BIP-32 with HDK

**Feature Branch**: `036-replace-bip32-with-hdk`
**Date**: 2026-05-13
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)
**Reference**: [IETF draft-dijkhuis-cfrg-hdkeys-06](https://datatracker.ietf.org/doc/html/draft-dijkhuis-cfrg-hdkeys-06)

---

## HDK Spec Summary

The IETF draft defines a framework for managing large sets of keys bound to a single device key. Key sections relevant to this migration:

- **§2.2 Instantiation Parameters**: `Ns` = salt byte length (32 for P-256), `H` = cryptographic hash (SHA-256), `ID` = domain separator, `BlindPublicKey` and `DeriveBlindingFactor` functions.
- **§2.3 HDK Context**: `ctx = ID || I2OSP(index, 4)` — context string combining a domain separator with a 4-byte big-endian index.
- **§2.4 DeriveSalt**: `salt' = H(salt || ctx)` — deterministic child salt derivation using hash concatenation. This is the core primitive that replaces BIP-32 CKD for child seed derivation.
- **§2.5 HDK Function**: Combines `DeriveSalt` with `BlindPublicKey` and `DeriveBlindingFactor` to produce a complete derived key tuple `(pk', salt', bf')`.
- **§3.2.2 Multiplicative Blinding**: `BlindPublicKey(pk, bk, ctx) = ScalarMult(pk, DeriveBlindingFactor(bk, ctx))` — the blinding scheme used in HDK-ECDH-P256.
- **§4.1 HDK-ECDH-P256**: Concrete instantiation using NIST P-256, SHA-256, multiplicative blinding, and DHKEM(P-256, HKDF-SHA256).

---

## Current HDK Implementation Inventory

The project already has a comprehensive HDK-ECDH-P256 implementation in `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/`:

| Component | File | Status |
|-----------|------|--------|
| `HdkManager` interface | `core/security/src/androidMain/.../api/HdkManager.kt` | ✅ Fully implemented |
| `HdkEcdhP256` (§4.1) | `core/security/src/androidMain/.../hdkeys/HdkEcdhP256.kt` | ✅ Fully implemented |
| `DeriveSalt` (§2.4) | Inside `HdkEcdhP256` | ✅ Implemented & KAT-tested |
| `HashToScalar` | `core/security/src/androidMain/.../hdkeys/HashToScalar.kt` | ✅ Implemented |
| `MultiplicativeBlinding` (§3.2.2) | `core/security/src/androidMain/.../hdkeys/MultiplicativeBlinding.kt` | ✅ Implemented |
| `P256Group` (curve ops) | `core/security/src/androidMain/.../hdkeys/P256Group.kt` | ✅ Implemented |
| `HdkTypes` (data classes) | `core/security/src/commonMain/.../api/HdkTypes.kt` | ✅ Implemented |

**Test coverage**: `HdkEcdhP256Test`, `HdkNegativePathTest`, `MultiplicativeBlindingTest`, `HashToScalarTest`, `P256GroupTest` — all present and passing.

**Key finding**: The `DeriveSalt` function is already implemented and tested with Known-Answer Tests (KATs) that validate `H(salt || ctx)` conformance to §2.4.

---

## PQ Branch Isolation Audit

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt`

### Current BIP-32/85 Logic (lines 207–320)

The PQ branch uses a **BIP-85-style** derivation path to produce a 64-byte child seed for ML-DSA key generation:

1. **`derivePqChildSeed(masterSeed)`** (line 260): Entry point. Derives a BIP-32 master root key via `HMAC-SHA512("Bitcoin seed", masterSeed)`, applies 3 rounds of hardened CKD, then extracts entropy via BIP-85.
2. **`ckdHard(parentKey, chainCode, index)`** (line 306): BIP-32 hardened Child Key Derivation. Computes `HMAC-SHA512(chainCode, 0x00 || parentKey || I2OSP(index, 4))`.
3. **Derivation path**: `m/83696968'/83286642'/2'` — BIP-85 purpose / app number / PQ index.
4. **BIP-85 entropy extraction**: `HMAC-SHA512("bip-entropy-from-k", derivedKey)` → 64-byte child seed.

### BIP-32/85 Constants (lines 331–354)

```
BIP32_KEY_SIZE_32, BIP32_HMAC_SIZE_64, BIP32_CKD_DATA_SIZE
BIP85_PURPOSE_83696968, BIP85_APP_NO_83286642, BIP85_INDEX_PQ_ML_DSA
HARDENED_OFFSET (0x80000000)
BIP32_INDEX_POS_0..3, BIT_SHIFT_24/16/8, BYTE_MASK_FF
```

### BIP-32 References in Other Files

| File | Nature | Lines |
|------|--------|-------|
| `PostQuantumCrypto.kt` | KDoc references to "BIP-85-style child seed" and "CKD path" | 24, 26, 84 |
| `Fido2CryptoService.kt` | Derivation table mentioning "BIP-85 + SHA-512" for ML-DSA | 93, 601–602 |
| `MasterSeedProvider.kt` (interface) | No BIP-32 references; clean | — |

### Gradle Dependencies

**No external BIP-32/bitcoinj dependencies found** in any `build.gradle.kts` file. The BIP-32 CKD logic is entirely self-contained within `WalletMasterSeedProvider.kt`.

---

## Feasibility and Evidence

### Decision: Full migration to HDK is **FEASIBLE**

### Evidence

**1. DeriveSalt provides equivalent cryptographic isolation**

The BIP-32 CKD path `m/83696968'/83286642'/2'` serves a single purpose: deterministically derive a child seed that is cryptographically isolated from the ECDSA HDK branch. The HDK `DeriveSalt` function (§2.4) achieves identical isolation through domain-separated hashing:

| Property | BIP-32 CKD | HDK DeriveSalt |
|----------|-----------|----------------|
| Determinism | ✅ HMAC-SHA512 chain | ✅ SHA-256 hash chain |
| Domain separation | Hardened index offsets | Context string (`ctx = ID \|\| I2OSP(index, 4)`) |
| Output | 32-byte key + 32-byte chain code | 32-byte salt (Ns bytes) |
| One-wayness | HMAC-SHA512 preimage resistance | SHA-256 preimage resistance |
| Independence | Parent key ⊥ child key | Parent salt ⊥ child salt |

Both provide computationally independent child material from the same parent seed. DeriveSalt is simpler (no chain code management) and aligns with the project's existing HDK infrastructure.

**2. Proposed replacement**

Replace `derivePqChildSeed(masterSeed)` with:
```
pqSalt = DeriveSalt(masterSeed[0:32], ctx="PQ_ML-DSA_Branch")
pqChildSeed = HMAC-SHA512("chimali_pq_seed_v1", pqSalt)
```

This uses the existing `HdkEcdhP256.deriveSalt()` with a dedicated context string for the PQ branch, then expands the 32-byte salt to 64 bytes via HMAC-SHA512 (matching the current output size expected by `PostQuantumCrypto.generateMlDsaKeyPair`).

**3. No external dependencies to remove**

All BIP-32 logic is self-contained in `WalletMasterSeedProvider.kt`. No Gradle dependency changes are required.

**4. HDK implementation already exists**

The `HdkEcdhP256` class with `deriveSalt()` is already implemented, KAT-tested, and production-ready. No new cryptographic code needs to be written — only the PQ branch caller needs rewiring.

---

## PQ Branch Migration Strategy

### Current flow (BIP-32)
```
masterSeed → HMAC-SHA512("Bitcoin seed") → BIP-32 root key
  → ckdHard(83696968') → ckdHard(83286642') → ckdHard(2')
  → HMAC-SHA512("bip-entropy-from-k") → 64-byte PQ child seed
```

### Proposed flow (HDK DeriveSalt)
```
masterSeed[0:32] → DeriveSalt(seed, ctx="PQ_ML-DSA_Branch")
  → 32-byte PQ salt
  → HMAC-SHA512("chimali_pq_seed_v1", pqSalt) → 64-byte PQ child seed
```

### Key design decisions

1. **Context string**: `"PQ_ML-DSA_Branch"` — unique, descriptive, and will never collide with HDK-ECDH-P256 contexts (which use `ID || I2OSP(index, 4)` format).
2. **Expansion step**: HMAC-SHA512 expands the 32-byte DeriveSalt output to 64 bytes, maintaining API compatibility with `PostQuantumCrypto.generateMlDsaKeyPair(pqChildSeed)`.
3. **HMAC key**: `"chimali_pq_seed_v1"` — versioned to allow future migration if needed.

---

## BIP-39 Seed Generation — No Change Required

**File**: `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGenerator.kt`

The BIP-39 mnemonic generation and PBKDF2-SHA512 seed derivation are **completely independent** from BIP-32. BIP-39 defines:
1. Entropy → mnemonic word selection (using the 2048-word English wordlist)
2. Mnemonic → seed via `PBKDF2-SHA512("mnemonic" + passphrase, mnemonic, 2048 iterations)`

Neither step uses BIP-32 CKD, chain codes, or hardened derivation paths. The `Bip39MasterSeedGenerator` class has **zero BIP-32 imports or dependencies**.

**Verdict**: FR-006 satisfied. No changes required to `Bip39MasterSeedGenerator` or the mnemonic generation/import flows in `WalletMasterSeedProvider`.

---

## Existing Wallets — Clean Break Decision

### Decision: **Clean break** (no migration path)

### Rationale

1. **Cryptographic incompatibility**: The BIP-32 CKD derivation path `m/83696968'/83286642'/2'` produces fundamentally different output than `DeriveSalt(seed, "PQ_ML-DSA_Branch")`. There is no mathematical relationship between the two — migrating would require storing both derivation results permanently.

2. **PQ keys only affected**: Only ML-DSA (post-quantum) keys derived via the BIP-85 path are invalidated. The ECDSA/HDK-ECDH-P256 branch uses `HMAC-SHA512("chimali_device_key_v1", masterSeed)` which is already independent of BIP-32 and remains unchanged.

3. **Pre-production status**: The project is in active development. No production wallets exist that depend on the BIP-32 PQ derivation path.

4. **Mnemonic preservation**: The BIP-39 mnemonic itself is preserved. Users can re-derive all keys from the same mnemonic after migration — only the PQ branch will produce different key material.

### Impact

- ECDSA credentials: **Unaffected** (same derivation path)
- ML-DSA/PQ credentials: **Invalidated** (new derivation produces different keys)
- BIP-39 mnemonic: **Preserved** (no change to seed generation)

---

## Risk Assessment

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| IETF draft changes in future versions | Medium | Medium | Pin to `-06`; monitor draft evolution; version the context string (`v1`) to allow future adaptation |
| No third-party library audit | Medium | Low | Custom implementation is minimal (~50 LOC for DeriveSalt); already KAT-tested against spec |
| PQ curve support gaps in HDK spec | Low | Low | The HDK spec's DeriveSalt is curve-agnostic — it produces raw byte material, not curve points. PQ key generation consumes raw bytes as seed input |
| Clean break invalidates existing PQ keys | Low | N/A | Pre-production only; no deployed wallets affected |
| Context string collision | Low | Very Low | `"PQ_ML-DSA_Branch"` is unique and does not match HDK-ECDH-P256 context format (`ID \|\| I2OSP`) |

---

## Code Changes — WalletMasterSeedProvider

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt`

### Remove

| Item | Lines | Description |
|------|-------|-------------|
| `derivePqChildSeed()` | 260–288 | BIP-85/BIP-32 derivation function |
| `ckdHard()` | 306–319 | BIP-32 hardened CKD function |
| `hmacSha512()` | 322–329 | Keep if needed for PQ expansion; otherwise remove |
| BIP-32 constants | 335–354 | `BIP32_KEY_SIZE_32`, `BIP32_HMAC_SIZE_64`, `BIP32_CKD_DATA_SIZE`, `BIP85_PURPOSE_83696968`, `BIP85_APP_NO_83286642`, `BIP85_INDEX_PQ_ML_DSA`, `HARDENED_OFFSET`, `BIP32_INDEX_POS_*`, `BIT_SHIFT_*`, `BYTE_MASK_FF` |
| KDoc references | 191, 207, 210–216, 229–258 | All BIP-85/BIP-32 documentation |

### Add

| Item | Description |
|------|-------------|
| `derivePqChildSeed()` replacement | Call `hdkManager.deriveSalt(masterSeed, pqContext)` with `ctx = "PQ_ML-DSA_Branch"`, then expand to 64 bytes via HMAC-SHA512 |
| `HdkManager` dependency | Inject via Koin constructor parameter |
| KDoc update | Document new HDK-based PQ branch derivation |

---

## Code Changes — PostQuantumCrypto

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt`

### Update KDoc only (no logic changes)

| Line | Current | Proposed |
|------|---------|----------|
| 24 | "BIP-85-style child seed produced by" | "HDK DeriveSalt-derived child seed produced by" |
| 26 | "the ECDSA branch via a fully-hardened CKD path `[83696968', 83286642', 2']`" | "the ECDSA branch via HDK `DeriveSalt` with context `PQ_ML-DSA_Branch`" |
| 84 | "64-byte BIP-85-derived PQ branch seed" | "64-byte HDK-derived PQ branch seed" |

---

## Code Changes — Fido2CryptoService

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt`

### Update KDoc only (no logic changes)

| Line | Current | Proposed |
|------|---------|----------|
| 93 | `BIP-85 + SHA-512 — isolated PQ branch via [MasterSeedProvider.getPqChildSeed]` | `HDK DeriveSalt + HMAC-SHA512 — isolated PQ branch via [MasterSeedProvider.getPqChildSeed]` |
| 601–602 | "no structural relation to BIP-32 paths" / "they carry no BIP-32 semantics" | Remove these lines — the BIP-32 disclaimer is no longer needed |

---

## Constitution Redlines

**File**: `.specify/memory/constitution.md` — Section `II. Master Seed Architecture`

### Current text (line 37):

> **PQ branch isolation**: The ML-DSA/Post-Quantum key branch uses a **BIP-85-style** hardened CKD derivation (`m/83696968'/83286642'/2'`) to produce a child seed that is cryptographically isolated from the ECDSA HDK branch. This BIP-32 CKD usage is intentional, limited to the PQ branch only, and does **not** conflict with the HDK spec because that child seed never enters the `HdkEcdhP256` derivation tree. The ECDSA branch uses `HMAC-SHA512("chimali_device_key_v1", masterSeed)` to derive the device key pair deterministically.

### Proposed replacement:

```diff
- **PQ branch isolation**: The ML-DSA/Post-Quantum key branch uses a **BIP-85-style**
- hardened CKD derivation (`m/83696968'/83286642'/2'`) to produce a child seed that is
- cryptographically isolated from the ECDSA HDK branch. This BIP-32 CKD usage is
- intentional, limited to the PQ branch only, and does **not** conflict with the HDK spec
- because that child seed never enters the `HdkEcdhP256` derivation tree. The ECDSA branch
- uses `HMAC-SHA512("chimali_device_key_v1", masterSeed)` to derive the device key pair
- deterministically.
+ **PQ branch isolation**: The ML-DSA/Post-Quantum key branch uses HDK `DeriveSalt`
+ (§2.4 of `draft-dijkhuis-cfrg-hdkeys-06`) with context string `"PQ_ML-DSA_Branch"` to
+ deterministically derive a 32-byte child salt from the master seed, which is then
+ expanded to 64 bytes via `HMAC-SHA512("chimali_pq_seed_v1", pqSalt)`. This child seed
+ is cryptographically isolated from the ECDSA HDK branch and never enters the
+ `HdkEcdhP256` derivation tree. All BIP-32/BIP-85 derivation logic has been removed.
+ The ECDSA branch uses `HMAC-SHA512("chimali_device_key_v1", masterSeed)` to derive the
+ device key pair deterministically.
```

### Additional constitution update (line 24):

The first paragraph of §II already correctly describes HDK without BIP-32 references. No changes needed there.

---

## Documentation Updates

The following documentation files contain historical BIP-32/44/85 references that should be annotated but **not deleted** (they are historical records):

| File | Action |
|------|--------|
| `docs/brd.md` (line 87) | Update NFR-SEC-040 to remove "BIP-44 / SLIP-10 adaptations" phrasing; replace with "HDK DeriveSalt-based PQ branch isolation" |
| `docs/changelogs/2026-03-25-fido2-mldsa-pqc-integration.md` | Add header note: "⚠️ The BIP-85 approach described here was superseded by HDK DeriveSalt in feature 036" |
| `docs/changelogs/2026-03-30-hdk-seed-size-fix.md` | Add header note: "⚠️ BIP-85 derivation referenced here was removed in feature 036" |
| `docs/changelogs/2026-04-14-fido2-ml-dsa-webauthn-compat-fixes.md` | Add header note: "⚠️ BIP-85-derived seed referenced here was replaced by HDK DeriveSalt in feature 036" |
| `docs/changelogs/2026-05-13-event-sourcing-security-audit.md` | Update "BIP39/BIP-85 derivation logic" to "BIP39 / HDK DeriveSalt derivation logic" |
| `docs/changelogs/2026-02-18-hdkeys-implementation.md` | No changes needed (already documents removal of legacy BIP-32) |
| `docs/changelogs/2026-03-01-fido2-security-milestone.md` | Update "BIP32-like patterns" to "HDK-ECDH-P256 patterns" |
| `docs/changelogs/2026-03-12-constitution-v0.6.0-brd-update.md` | Add header note: "⚠️ BIP-44/SLIP-10 references here were superseded by strict HDK in feature 036" |
| `docs/changelogs/2026-03-25-fido2-stress-testing-and-credentialid-refactor.md` | Update "BIP-32/CTAP2 alignment" reference |
| `docs/icebox/research-resources.md` | No changes needed (reference material) |

---

## Recommended New Tests

### Tests to add

1. **DeriveSalt PQ branch KAT**: Verify that `DeriveSalt(zeroed_32_byte_seed, "PQ_ML-DSA_Branch")` produces a specific known output. This pins the derivation and detects accidental regressions.

2. **PQ child seed determinism**: Verify that `getPqChildSeed()` returns identical output across multiple calls with the same master seed (replacing the current BIP-85 determinism test).

3. **PQ/ECDSA branch independence**: Verify that changing the PQ context string produces different PQ child seeds while the ECDSA device key pair remains unchanged.

4. **Backward incompatibility assertion**: Explicitly assert that the new DeriveSalt-based PQ child seed does **not** match the old BIP-85-based output for the same master seed input. This documents the clean break.

### Tests to remove

- Any test vectors that validate `ckdHard()` or `derivePqChildSeed()` with BIP-32 semantics (these functions will be deleted).

### Tests to update

- `Fido2CryptoServiceTest.kt` references to "BIP39 seed" derivation determinism (line 278) — update KDoc comment from "Constitution §II" BIP-85 reference to HDK DeriveSalt reference.

---

## Summary

| Criterion | Status |
|-----------|--------|
| SC-001: Feasibility and Evidence section | ✅ Complete |
| SC-002: Comprehensive code change list | ✅ Complete |
| SC-003: Constitution redlines | ✅ Complete |
| SC-004: No product code modified | ✅ Verified |
| FR-001: Structured Markdown analysis | ✅ This document |
| FR-002: HDK feasibility evaluation | ✅ Feasible — DeriveSalt replaces CKD |
| FR-003: Evidence and rationale | ✅ Cryptographic comparison table |
| FR-004: Code change details | ✅ 3 files detailed |
| FR-005: Constitution updates | ✅ Diff provided |
| FR-006: BIP-39 unchanged | ✅ Confirmed independent |
