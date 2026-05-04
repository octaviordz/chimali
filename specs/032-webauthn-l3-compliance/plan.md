# Implementation Plan: WebAuthn Level 3 Compliance

**Branch**: `032-webauthn-l3-compliance` | **Date**: 2026-05-03 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/032-webauthn-l3-compliance/spec.md`

---

## Summary

Chimali currently fails five WebAuthn Level 3 conformance requirements identified in the W3C audit. This plan remediates all non-conformances across the `feature/fido2` module and the `core/domain` shared library:

1. **Credential ID entropy + bounds** — `CredentialId.generate()` produces 32 bytes (256 bits), satisfying the ≥100-bit minimum, but the value object has no byte-level size guard and no encrypted-blob path recognition.
2. **String truncation** — `PasskeyCredential` enforces `MAX_DISPLAY_NAME_LENGTH = 64` characters (not bytes); the CTAP2 handler silently passes arbitrary-length strings.
3. **COSE algorithm set** — `COSE_ED25519 = -19` is used throughout (`PasskeyCredential`, `Ctap2MakeCredentialHandler`), which is a W3C "NOT RECOMMENDED" identifier. The L3-correct constant is `-8` (EdDSA). `RS256 (-257)` is declared but not wired into algorithm negotiation.
4. **Ceremony timeout** — `MakeCredentialOptions.DEFAULT_TIMEOUT_MS = 60_000` is always applied as a static fallback with no accessibility ceiling; `GetAssertionOptions` has its own independent static default.
5. **PRF/hmac-secret extension** — No model, no CTAP2 request parsing, no cryptographic output path exists.

All changes are confined to the Chimali Android client; no RP server changes are in scope.

---

## Technical Context

**Language/Version**: Kotlin 2.0 (KMP); Android API 28+  
**Primary Dependencies**: Koin (DI), kotlinx.serialization, kotlinx.datetime, kotlin.time, Bouncy Castle / Android Keystore (crypto)  
**Storage**: SQLDelight + SQLCipher (AES-256-CBC file encryption); credential blobs additionally AES-256-GCM encrypted  
**Testing**: kotlin.test (common), JUnit 5, MockK; Compose UI Testing for presentation layer  
**Target Platform**: Android 9+ (API 28); KMP module structure maintained  
**Project Type**: Mobile app with embedded FIDO2 authenticator role  
**Performance Goals**: Bluetooth HID latency < 200ms end-to-end; 60 FPS UI; cold start < 2s  
**Constraints**: All crypto via Android Keystore or Bouncy Castle; no cloud transmission of plain-text data  
**Scale/Scope**: Credential store designed for 10,000+ items; this feature touches ~12 domain/CTAP2 files + 4 new files  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Evaluation | Status |
|---|---|---|
| **I. Security First** | All crypto operations remain in Android Keystore / Bouncy Castle. EdDSA key generation uses `Ed25519` via existing HDK infrastructure. PRF HMAC-SHA-256 derived in Keystore boundary. No plain-text credential exposure. | PASS |
| **II. Master Seed Architecture** | EdDSA credential keys (`-8`) are derived via HDK-ECDH-P256 variant for Ed25519 branch (PQ branch isolation applies). No BIP-32 paths introduced. | PASS |
| **III. Architecture & Quality** | All changes follow Clean Architecture. No magic numbers — COSE IDs, entropy thresholds, and timeout bounds extracted to named constants. Koin DI used. Detekt/Ktlint must pass. | PASS |
| **IV. Performance** | Timeout changes are additive (expand ceiling, not add latency). PRF HMAC is a single-pass operation. No impact on startup or rendering. | PASS |
| **V. Cross-Platform** | CTAP2 protocol changes (`-8`, PRF extension) increase compliance with the CTAP2 spec, improving cross-platform compatibility with Windows/macOS WebAuthn clients. | PASS |
| **VI. Accessibility** | Ceremony timeout accessibility ceiling is a direct implementation of this principle. | PASS |
| **VII. Documentation** | `plan.md`, `research.md`, `data-model.md`, `contracts/` all generated. Requirement IDs follow `FR-FIDO2-NNN` stable mnemonic format. | PASS |
| **VIII. Local CI/CD** | All changes must pass `tools/local-ci.ps1` (Ktlint + Detekt + all unit tests). TDD applies. | PASS |

**Gate result: PASS — Phase 0 may proceed.**

---

## Project Structure

### Documentation (this feature)

```text
specs/032-webauthn-l3-compliance/
├── plan.md                    <- This file
├── research.md                <- Phase 0 output
├── data-model.md              <- Phase 1 output
├── quickstart.md              <- Phase 1 output
├── contracts/
│   └── fido2-domain-contracts.md   <- Phase 1 output
└── tasks.md                   <- Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/
└── CredentialId.kt                      <- Add encrypted-blob path + byte-level guard

feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
├── domain/
│   ├── model/
│   │   ├── PasskeyCredential.kt         <- Fix COSE_ED25519 -> COSE_EDSA (-8); byte-level string validation
│   │   ├── MakeCredentialOptions.kt     <- Dynamic timeout + accessibility ceiling; PRF extension params
│   │   ├── GetAssertionOptions.kt       <- Dynamic timeout + accessibility ceiling (parity)
│   │   ├── PublicKeyCredentialParameters.kt <- EdDSA factory; deprecate -19/-9/-51/-52 enum values
│   │   └── PrfExtensionInput.kt         <- NEW: domain model for PRF salts (1-2 salts)
│   └── service/
│       └── impl/
│           └── Fido2AuthenticatorImpl.kt <- Propagate EdDSA signing path; PRF output assembly
├── ctap2/
│   ├── Ctap2MakeCredentialHandler.kt    <- Fix COSE_EDSA const; parse hmac-secret ext; PRF dispatch
│   └── Ctap2GetAssertionHandler.kt      <- Fix COSE_EDSA const; parse hmac-secret ext; PRF output
└── data/
    └── crypto/
        └── PrfKeyDerivation.kt          <- NEW: HMAC-SHA-256 PRF derivation

feature/fido2/src/test/kotlin/com/chimali/fido2/
├── domain/model/PasskeyCredentialTest.kt
├── domain/model/MakeCredentialOptionsTest.kt
├── ctap2/Ctap2MakeCredentialHandlerTest.kt
└── data/crypto/PrfKeyDerivationTest.kt  <- NEW
```

**Structure Decision**: Single feature module (`feature/fido2`) with a shared value object change in `core/domain`. No new modules are introduced. All crypto primitives remain in the existing `data/crypto` layer.

---

## Phase 0: Research

*See [research.md](research.md) for full findings. Key decisions summarized below.*

### R-001: COSE Algorithm Constants (EdDSA)

- **Decision**: Replace `COSE_ED25519 = -19` with `COSE_EDSA = -8` everywhere. Rename the constant to `COSE_EDSA` to eliminate confusion. Keep `COSE_ES256 = -7`, `COSE_RS256 = -257`, `COSE_ML_DSA_65 = -49`.
- **Rationale**: W3C WebAuthn L3 § 5.4 designates `-8` as the mandatory EdDSA identifier using `crv: 6` (Ed25519). `-19` is explicitly NOT RECOMMENDED. Android's `KeyPairGenerator` supports `Ed25519` natively on API 33+; Bouncy Castle provides it on API 28+.
- **Alternatives considered**: Keeping `-19` for backward-compat — rejected because L3 audit is a hard compliance requirement and existing stored credentials are unaffected (we change generation/negotiation only).

### R-002: Credential ID Byte-Level Validation

- **Decision**: Add `require(bytes.size in MIN_CREDENTIAL_ID_BYTES..MAX_CREDENTIAL_ID_BYTES)` in `CredentialId.fromByteArray()`. Add `CredentialIdForm` sealed interface with `EntropyBased` and `EncryptedBlob` subtypes; validation logic diverges on form.
- **Rationale**: `CredentialId` currently only validates non-blank encoding. Per W3C § 5.1 / § 4, max 1023 bytes, entropy IDs >= 16 bytes. `CredentialId.generate()` already produces 32 bytes (256 bits > 100 bits) — only the guard and the blob form recognition are missing.
- **Alternatives considered**: Validating in `PasskeyCredential` only — rejected because `CredentialId` is a KMP shared value object and should be self-guarded.

### R-003: String Truncation Byte-Level Check

- **Decision**: Change validation in `PasskeyCredential` from `userName.length <= MAX_NAME_LENGTH` (character count) to `userName.toByteArray(Charsets.UTF_8).size <= MAX_NAME_LENGTH` (byte count). Same for `userDisplayName`. The CTAP2 handler must NOT truncate before passing to domain.
- **Rationale**: WebAuthn § 6.4.1.2 specifies byte counts, not code points. Multi-byte Unicode characters (e.g., Chinese, emoji) could violate the spec under the current character-count check.

### R-004: Ceremony Timeout Dynamic Binding + Accessibility Ceiling

- **Decision**:
  - `MIN_CEREMONY_TIMEOUT_MS = 30_000L` (30 s) — enforced lower bound.
  - `MAX_CEREMONY_TIMEOUT_MS = 600_000L` (10 min) — accessibility-driven upper bound (W3C § 15.1 cognitive guideline).
  - `DEFAULT_TIMEOUT_MS = 120_000L` (2 min) — used when RP provides no hint.
  - `getSafeTimeout()` clamps the RP hint: `timeout?.coerceIn(MIN, MAX) ?: DEFAULT`.
  - Apply identical logic to both `MakeCredentialOptions` and `GetAssertionOptions`.
- **Rationale**: Current static 60 s / max 300 s is too short for users with motor or cognitive disabilities. W3C § 15.1 recommends accommodating assistive technology users. The 10-minute upper bound aligns with common accessibility guidelines (WCAG 2.1 SC 2.2.1 extended).

### R-005: PRF / hmac-secret CTAP2 Extension

- **Decision**: Implement PRF as a new domain model (`PrfExtensionInput` / `PrfExtensionOutput`) + a dedicated `PrfKeyDerivation` service. The CTAP2 handler parses `hmac-secret` from the extensions map, invokes `PrfKeyDerivation.derive(salt, credentialSecret)`, and returns results in the authenticator data extensions map.
- **Rationale**: PRF requires `hmac-secret` CTAP2 extension processing. Android Keystore does not expose raw HMAC-SHA-256 for external secrets; Bouncy Castle `HMac` with `SHA256Digest` is used within the security boundary. Maximum 2 salts per spec § 16.17.1.1; > 2 salts -> `CTAP2_ERR_INVALID_PARAMETER`.
- **Alternatives considered**: Using `javax.crypto.Mac` — valid, but Bouncy Castle is already a declared dependency for FIDO2 crypto operations.

---

## Phase 1: Design & Contracts

*See [data-model.md](data-model.md) and [contracts/fido2-domain-contracts.md](contracts/fido2-domain-contracts.md) for full detail.*

### Key Entity Changes

| Entity | Change | Location |
|---|---|---|
| `CredentialId` | + byte-size guard (16-1023); + `EncryptedBlob` form recognition | `core/domain` |
| `PasskeyCredential` | `COSE_EDSA = -8`; byte-level UTF-8 string validation | `feature/fido2` domain |
| `MakeCredentialOptions` | Dynamic `getSafeTimeout()` with MIN/MAX/DEFAULT; PRF extension wiring | `feature/fido2` domain |
| `GetAssertionOptions` | Parity timeout logic | `feature/fido2` domain |
| `PublicKeyCredentialParameters` | `createEdDsa()` factory; deprecate -19 algorithm string | `feature/fido2` domain |
| `PrfExtensionInput` (new) | `salts: List<ByteArray>` (1-2); validation rule | `feature/fido2` domain |
| `PrfExtensionOutput` (new) | `outputs: List<ByteArray>` (1-2, <= 32 bytes each) | `feature/fido2` domain |
| `PrfKeyDerivation` (new) | `derive(salt: ByteArray, credentialSecret: ByteArray): ByteArray` | `feature/fido2` data/crypto |

### Algorithm Preference Order

The CTAP2 algorithm negotiation (`Ctap2MakeCredentialHandler`) selects the **first matching** supported algorithm from the RP's list, in this preference order:

```
1. -7   (ES256 / P-256)        <- primary, hardware-backed
2. -8   (EdDSA / Ed25519)      <- L3 required; Bouncy Castle on API <33, Keystore on API 33+
3. -257 (RS256)                <- interoperability fallback
4. -49  (ML-DSA-65)            <- post-quantum extension (Chimali-specific)
```

Identifiers `-9`, `-19`, `-51`, `-52` are rejected with `CTAP2_ERR_UNSUPPORTED_ALGORITHM`.

### Timeout Constants (Both Ceremony Types)

```kotlin
const val MIN_CEREMONY_TIMEOUT_MS  = 30_000L    // 30 s - reject too-short RP hints
const val MAX_CEREMONY_TIMEOUT_MS  = 600_000L   // 10 min - accessibility ceiling
const val DEFAULT_TIMEOUT_MS       = 120_000L   // 2 min - when RP provides no hint
```

### PRF Extension Data Flow

```
RP request (extensions["prf"])
  -> Ctap2MakeCredentialHandler / Ctap2GetAssertionHandler
       -> parse "hmac-secret" extension -> PrfExtensionInput(salts: 1-2 x ByteArray)
            -> PrfKeyDerivation.derive(salt, credentialSecret) -> ByteArray (<=32 bytes)
                 -> PrfExtensionOutput -> AuthenticatorData extensions map -> CBOR response
```

---

## Complexity Tracking

No constitution violations. No new modules. No architectural exceptions required.
