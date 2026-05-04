# Data Model: WebAuthn Level 3 Compliance

**Feature**: `032-webauthn-l3-compliance`  
**Phase**: 1 — Design  
**Date**: 2026-05-03  

---

## Entity Changes

### 1. `CredentialId` (core/domain)

**File**: `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/CredentialId.kt`

| Field / Constant | Type | Change | Description |
|---|---|---|---|
| `CREDENTIAL_ID_SIZE_BYTES` | `Int` | Unchanged (32) | Default generated size |
| `MIN_CREDENTIAL_ID_BYTES` | `Int` | **NEW** = 16 | WebAuthn § 4 minimum for entropy-based IDs |
| `MAX_CREDENTIAL_ID_BYTES` | `Int` | **NEW** = 1023 | WebAuthn § 5.1 absolute maximum |
| `fromByteArray(bytes)` | factory | **Updated** | Add size guard: `bytes.size in MIN..MAX` |
| `generate()` | factory | Unchanged | Still produces 32 bytes (256-bit entropy) |
| `fromEncoded(encoded)` | factory | Unchanged | Reconstructs from stored Base64URL |

**Validation rules**:
- `fromByteArray`: byte array must be 16–1023 bytes (rejects < 16 or > 1023)
- `generate()`: always produces exactly 32 bytes — satisfies both minimum entropy and maximum size
- `fromEncoded()`: no size validation (used only for previously-persisted IDs — backward compatibility preserved)

---

### 2. `PasskeyCredential` (feature/fido2 domain)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PasskeyCredential.kt`

| Field / Constant | Type | Change | Description |
|---|---|---|---|
| `COSE_ES256` | `Int` = -7 | Unchanged | ECDSA / P-256 |
| `COSE_ED25519` | `Int` = -19 | **Removed** | NOT RECOMMENDED per WebAuthn L3 § 5.4 |
| `COSE_EDSA` | `Int` = -8 | **NEW** | EdDSA / Ed25519 — L3 mandatory |
| `COSE_RS256` | `Int` = -257 | Unchanged | RSASSA-PKCS1-v1_5 |
| `COSE_ML_DSA_65` | `Int` = -49 | Unchanged | Post-quantum extension |
| `MAX_NAME_LENGTH` | `Int` = 64 | Unchanged (bytes) | Validation expression updated to UTF-8 byte count |
| `MAX_DISPLAY_NAME_LENGTH` | `Int` = 64 | Unchanged (bytes) | Validation expression updated to UTF-8 byte count |
| `validate()` | method | **Updated** | `userName.toByteArray(UTF_8).size` instead of `userName.length` |

**State transitions**: None (passive data model).

---

### 3. `MakeCredentialOptions` (feature/fido2 domain)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/MakeCredentialOptions.kt`

| Field / Constant | Type | Change | Description |
|---|---|---|---|
| `MIN_CEREMONY_TIMEOUT_MS` | `Long` | **NEW** = 30_000 | Minimum acceptable timeout (30 s) |
| `MAX_CEREMONY_TIMEOUT_MS` | `Long` | **NEW** = 600_000 | Accessibility ceiling (10 min) |
| `DEFAULT_TIMEOUT_MS` | `Long` | **Updated** 60_000 → 120_000 | Default when RP provides no hint (2 min) |
| `MAX_TIMEOUT_MS` | `Long` | **Removed** | Replaced by `MAX_CEREMONY_TIMEOUT_MS` |
| `getSafeTimeout()` | method | **Updated** | `timeout?.coerceIn(MIN, MAX) ?: DEFAULT` |
| `validate()` | method | **Updated** | Reject timeout <= 0 only; clamping happens in `getSafeTimeout()` |

---

### 4. `GetAssertionOptions` (feature/fido2 domain)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/GetAssertionOptions.kt`

Same timeout constant changes as `MakeCredentialOptions` for parity:

| Field / Constant | Type | Change |
|---|---|---|
| `MIN_CEREMONY_TIMEOUT_MS` | `Long` | **NEW** = 30_000 |
| `MAX_CEREMONY_TIMEOUT_MS` | `Long` | **NEW** = 600_000 |
| `DEFAULT_TIMEOUT_MS` | `Long` | **Updated** → 120_000 |
| `getSafeTimeout()` | method | **Updated** |

---

### 5. `PublicKeyCredentialParameters` (feature/fido2 domain)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PublicKeyCredentialParameters.kt`

| Change | Description |
|---|---|
| `validate()` allowlist | Remove `"EdDSA"` from deprecated path; ensure it maps to COSE -8 when combined with `"Ed25519"` curve |
| `createEdDsa()` factory | Rename existing `createEd25519()` to `createEdDsa()` for clarity; keep `"EdDSA"` algorithm string, `"Ed25519"` curve |

---

### 6. `PrfExtensionInput` (NEW — feature/fido2 domain)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PrfExtensionInput.kt`

| Field | Type | Constraints | Description |
|---|---|---|---|
| `salts` | `List<ByteArray>` | size in 1..2; each non-empty | RP-provided PRF salts |

**Validation rules**:
- `salts.size in 1..MAX_SALTS` (MAX_SALTS = 2); error: `CTAP2_ERR_INVALID_PARAMETER`
- Each salt must be non-empty

**Companion constants**:
- `MAX_SALTS = 2`
- `MAX_OUTPUT_BYTES = 32`

---

### 7. `PrfExtensionOutput` (NEW — feature/fido2 domain)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PrfExtensionInput.kt` (same file, sealed)

| Field | Type | Constraints | Description |
|---|---|---|---|
| `outputs` | `List<ByteArray>` | parallel to input salts; each <= 32 bytes | Derived HMAC-SHA-256 outputs |

---

### 8. `PrfKeyDerivation` (NEW — feature/fido2 data/crypto)

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PrfKeyDerivation.kt`

| Method | Signature | Description |
|---|---|---|
| `derive` | `(salt: ByteArray, credentialHmacSecret: ByteArray): ByteArray` | HMAC-SHA-256(credentialHmacSecret, salt); returns 32 bytes |
| `deriveAll` | `(input: PrfExtensionInput, credentialHmacSecret: ByteArray): PrfExtensionOutput` | Processes 1–2 salts; returns output |

**Crypto details**:
- Algorithm: HMAC-SHA-256 via Bouncy Castle `HMac(SHA256Digest())`
- Key: `credentialHmacSecret` (32 bytes derived from credential private key material)
- Input: `salt` (arbitrary bytes from RP)
- Output: 32 bytes (full HMAC-SHA-256 output, no truncation needed)

---

## Algorithm Negotiation State Machine

```
Ctap2MakeCredentialHandler.handleMakeCredential()
  |
  |-- req.algorithms.firstNotNullOfOrNull { algId ->
  |     when (algId) {
  |       COSE_ES256 (-7)   -> ES256 / P-256 (primary)
  |       COSE_EDSA  (-8)   -> EdDSA / Ed25519 (L3 required) [CHANGED from -19]
  |       COSE_RS256 (-257) -> RS256 (interoperability fallback)
  |       COSE_ML_DSA_65 (-49) -> ML-DSA-65 (PQ extension)
  |       -9, -19, -51, -52 -> null (rejected)
  |       else              -> null (rejected)
  |     }
  |   }
  |
  |-- null -> CTAP2_ERR_UNSUPPORTED_ALGORITHM
```

---

## Ceremony Timer State Machine

```
RP provides timeout hint -> MakeCredentialOptions.timeout (Long?)
  |
  |-- null -> getSafeTimeout() returns DEFAULT_TIMEOUT_MS (120_000)
  |
  |-- value <= 0 -> rejected in validate() with IllegalArgumentException
  |
  |-- value in 1..29_999 -> coerced up to MIN_CEREMONY_TIMEOUT_MS (30_000)
  |
  |-- value in 30_000..600_000 -> used as-is
  |
  |-- value > 600_000 -> coerced down to MAX_CEREMONY_TIMEOUT_MS (600_000)
```

---

## PRF Extension State Machine

```
CTAP2 request extensions["hmac-secret"] present?
  |
  +-- YES:
  |    parse salt1 (required) + salt2 (optional)
  |    -> PrfExtensionInput(salts)
  |    -> PrfKeyDerivation.deriveAll(input, credentialHmacSecret)
  |    -> PrfExtensionOutput(outputs)
  |    -> serialize into authenticatorData extensions CBOR map
  |
  +-- NO: skip PRF; no extensions in authenticatorData
  |
  +-- > 2 salts: -> CTAP2_ERR_INVALID_PARAMETER
```
