# Research: WebAuthn Level 3 Compliance

**Feature**: `032-webauthn-l3-compliance`  
**Phase**: 0 — Pre-Design Research  
**Date**: 2026-05-03  

---

## R-001: COSE Algorithm Constants (EdDSA / -8 vs -19)

**Decision**: Replace `COSE_ED25519 = -19` with `COSE_EDSA = -8` in all domain models and CTAP2 handlers.

**Rationale**:
- W3C WebAuthn Level 3, § 5.4 explicitly lists `-8` (EdDSA) as mandatory for interoperability using `kty: 1 (OKP)` and `crv: 6 (Ed25519)`.
- `-19` (previously an informal Ed25519 ID) is marked **NOT RECOMMENDED** in the same section.
- `IANA COSE Algorithms` registry (RFC 9053) assigns `-8` to EdDSA as the normative identifier.
- Android `KeyPairGenerator` supports `Ed25519` natively from API 33 (Android 13). For API 28–32, Bouncy Castle (`org.bouncycastle:bcprov-jdk15on`) provides `Ed25519` via `EdDSASecurityProvider`.

**Files to update**:
- `PasskeyCredential.kt`: rename `COSE_ED25519 = -19` → `COSE_EDSA = -8`; update `validate()` branch
- `Ctap2MakeCredentialHandler.kt`: rename local `COSE_ED25519 = -19` → `COSE_EDSA = -8`; update algorithm negotiation `when` branch
- `Ctap2GetAssertionHandler.kt`: same rename
- `PublicKeyCredentialParameters.kt`: `createEd25519()` factory method already exists and maps to `"EdDSA"` algorithm string — no COSE ID stored here, so no change required to the factory; update validation allowlist if a COSE ID field is added in the future

**Alternatives considered**:
- Maintain `-19` for backward compatibility with existing stored credentials: **Rejected** — existing stored credentials use `-7` (ES256) or `-49` (ML-DSA). The change only affects the negotiation of *new* credentials. Any credential stored with `-19` will continue to authenticate via `when (coseAlgorithm)` since we keep backward-read support.

---

## R-002: Credential ID Byte-Level Validation + Encrypted-Blob Form

**Decision**: Add byte-level bounds checking in `CredentialId.fromByteArray()` and expose an `EncryptedBlob` form marker.

**Rationale**:
- WebAuthn Level 3 § 5.1 / § 4 defines Credential ID in two conformant forms:
  1. **Entropy-based**: ≥ 16 bytes, ≥ 100 bits of entropy
  2. **Encrypted credential source**: authenticator is nearly stateless; opaque blob
- `CredentialId.generate()` already produces 32 bytes (256 bits of entropy via `SecureRandom`) — entropy requirement is already met.
- Missing: byte-level upper guard (max 1023 bytes) on inbound IDs from external authenticators.
- Missing: The system currently rejects or ignores IDs that don't round-trip cleanly through its Base64URL generation, which would incorrectly reject valid encrypted-blob IDs from stateless authenticators.

**Proposed model**:
```kotlin
// In CredentialId companion
const val MIN_CREDENTIAL_ID_BYTES = 16
const val MAX_CREDENTIAL_ID_BYTES = 1023

fun fromByteArray(bytes: ByteArray): CredentialId {
    require(bytes.size in MIN_CREDENTIAL_ID_BYTES..MAX_CREDENTIAL_ID_BYTES) {
        "Credential ID must be between $MIN_CREDENTIAL_ID_BYTES and $MAX_CREDENTIAL_ID_BYTES bytes"
    }
    return CredentialId(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes))
}

// Encrypted-blob IDs come from external authenticators as raw byte arrays;
// use fromByteArray() — the size guard applies but no entropy check is performed,
// as the blob form is opaque by design.
```

A separate `acceptExternalId(bytes: ByteArray)` factory may be added if the system needs to explicitly differentiate form types at the type-system level.

**Alternatives considered**:
- Add entropy measurement (e.g., Shannon entropy scan): **Rejected** — computationally expensive and unreliable on short arrays; spec says "at least 100 bits" which for generated IDs is guaranteed by `SecureRandom`.
- Separate `EncryptedBlobCredentialId` type: **Deferred** — introduces breaking changes to all code that holds `CredentialId`; the simpler approach of accepting all byte arrays within the valid size range is spec-conformant.

---

## R-003: String Truncation — Byte Count vs Character Count

**Decision**: Replace `String.length` comparisons with `String.toByteArray(Charsets.UTF_8).size` in `PasskeyCredential.validate()`.

**Rationale**:
- WebAuthn § 6.4.1.2 mandates truncation MUST NOT occur at fewer than 64 **bytes**.
- Kotlin `String.length` returns UTF-16 code unit count, not bytes.
- A 64-character string of BMP Unicode characters is 128 bytes in UTF-8 but only 64 in `.length`. Conversely, a 64-character string of ASCII characters is exactly 64 bytes. The current validation passes strings that are actually too long in bytes.
- Correct check: `userName.toByteArray(Charsets.UTF_8).size <= MAX_NAME_LENGTH`.

**Implementation note**: The constant `MAX_NAME_LENGTH = 64` and `MAX_DISPLAY_NAME_LENGTH = 64` remain correct — they represent 64 bytes, not characters. Only the validation expression needs updating.

**Alternatives considered**:
- Truncate at the domain boundary: **Rejected** — spec says truncation MUST NOT occur below 64 bytes; the correct behavior is rejection or pass-through, not truncation.

---

## R-004: Dynamic Ceremony Timeout + Accessibility Ceiling

**Decision**: Replace the static `DEFAULT_TIMEOUT_MS = 60_000L` and `MAX_TIMEOUT_MS = 300_000L` with a dynamic clamping function using 30 s / 10 min / 2 min bounds.

**Rationale**:
- WebAuthn Level 3 § 5.1.3 / § 5.1.4.1 require the `lifetimeTimer` to be adjustable based on RP-provided timeout.
- § 15.1 "Recommended Range for Ceremony Timeouts" cites cognitive accessibility guidelines; users with motor or cognitive disabilities may require substantially more time than 60 seconds.
- WCAG 2.1 Success Criterion 2.2.1 (Timing Adjustable) recommends at least 10× the default for users requiring accommodations, or no time limit at all for essential tasks. A 10-minute ceiling is a conservative but practical upper bound.
- Current 5-minute `MAX_TIMEOUT_MS = 300_000L` is too restrictive for accessibility compliance.
- Current `DEFAULT_TIMEOUT_MS = 60_000L` used as fallback when RP provides no hint is adequate for standard users but should be raised to 120 s to give more headroom as a default.

**New constants**:
```kotlin
const val MIN_CEREMONY_TIMEOUT_MS = 30_000L    // 30 s
const val MAX_CEREMONY_TIMEOUT_MS = 600_000L   // 10 min
const val DEFAULT_TIMEOUT_MS      = 120_000L   // 2 min (was 60 s)
```

**New `getSafeTimeout()` logic** (both `MakeCredentialOptions` and `GetAssertionOptions`):
```kotlin
fun getSafeTimeout(): Long =
    timeout?.coerceIn(MIN_CEREMONY_TIMEOUT_MS, MAX_CEREMONY_TIMEOUT_MS)
        ?: DEFAULT_TIMEOUT_MS
```

**Alternatives considered**:
- No upper ceiling (allow RP to set any timeout): **Rejected** — unbounded timeouts create session security risks.
- Keep 5-minute ceiling, raise default only: **Rejected** — does not satisfy § 15.1 accessibility requirement for users who may need 5–10 minutes for motor/cognitive tasks.

---

## R-005: PRF Extension (hmac-secret CTAP2)

**Decision**: Implement PRF extension as `PrfExtensionInput` / `PrfExtensionOutput` domain models + `PrfKeyDerivation` service using Bouncy Castle HMAC-SHA-256.

**Rationale**:
- WebAuthn Level 3 § 10.1.4 / § 16.17.1.1 requires the `prf` extension to process 1–2 salts from the RP and return deterministic HMAC-SHA-256 derived bytes (≤ 32 bytes per salt).
- The authenticator processes this via the `hmac-secret` CTAP2 extension (§ 16.17.1.2), which uses the authenticator's per-credential secret key as the HMAC key.
- Android `KeyStore` HMAC operations are tied to hardware-backed keys and cannot accept arbitrary external secrets for HMAC. Therefore `javax.crypto.Mac` with Bouncy Castle is the implementation path.
- Bouncy Castle (`bcprov-jdk15on`) is already declared as a dependency in `feature/fido2/build.gradle.kts`.

**Security boundary**:
- The per-credential HMAC secret is derived from the credential's private key material held in Android Keystore.
- Derivation: `HMAC-SHA256(credentialHmacSecret, salt)` where `credentialHmacSecret` is a 32-byte value derived from the credential's seed (distinct from the signing key).
- Output truncated to exactly 32 bytes (HMAC-SHA-256 output is naturally 32 bytes).

**Data model**:
```kotlin
data class PrfExtensionInput(
    val salts: List<ByteArray>  // size in 1..2; each salt is an arbitrary byte array
) {
    init {
        require(salts.size in 1..2) { "PRF extension requires 1 or 2 salts" }
        salts.forEach { require(it.isNotEmpty()) { "PRF salt cannot be empty" } }
    }
    companion object {
        const val MAX_SALTS = 2
    }
}

data class PrfExtensionOutput(
    val outputs: List<ByteArray>  // parallel to inputs; each is exactly 32 bytes
)
```

**CTAP2 extension key**: `"hmac-secret"` in the extensions map. Parsed from both `authenticatorMakeCredential` and `authenticatorGetAssertion` requests.

**Alternatives considered**:
- `javax.crypto.Mac` with `HmacSHA256`: **Valid alternative** — would work on API 28+. Bouncy Castle preferred for consistency with existing crypto code in the FIDO2 module.
- Defer PRF to a later feature: **Rejected** — PRF is an explicit Medium-criticality finding in the L3 audit.
