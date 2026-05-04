# FIDO2 Domain Contracts: WebAuthn Level 3 Compliance

**Feature**: `032-webauthn-l3-compliance`  
**Phase**: 1 — Design  
**Date**: 2026-05-03  

This document defines the internal domain interface contracts that will be updated or created as part of the WebAuthn Level 3 compliance remediation.

---

## 1. `CredentialId` Value Object Contract (core/domain)

### `fromByteArray(bytes: ByteArray): CredentialId`

**Pre-conditions**:
- `bytes.size >= 16` (MIN_CREDENTIAL_ID_BYTES)
- `bytes.size <= 1023` (MAX_CREDENTIAL_ID_BYTES)

**Post-conditions**:
- Returns a `CredentialId` whose `toByteArray()` round-trips to the same byte content
- Throws `IllegalArgumentException` if pre-conditions are violated

**Error message format**: `"Credential ID must be between {MIN} and {MAX} bytes, got {actual}"`

---

### `generate(): CredentialId`

**Contract**: Unchanged. Produces exactly 32 bytes of `SecureRandom` entropy.  
Satisfies WebAuthn § 4 constraint: ≥ 16 bytes, ≥ 100 bits entropy.

---

## 2. `PasskeyCredential` Domain Validation Contract

### `validate()` — updated invariants

| Invariant | Expression | Error |
|---|---|---|
| `userName` not blank | `userName.isNotBlank()` | `"User name cannot be blank"` |
| `userName` UTF-8 byte limit | `userName.toByteArray(UTF_8).size <= 64` | `"User name cannot exceed 64 bytes"` |
| `userDisplayName` not blank | `userDisplayName.isNotBlank()` | `"User display name cannot be blank"` |
| `userDisplayName` UTF-8 byte limit | `userDisplayName.toByteArray(UTF_8).size <= 64` | `"User display name cannot exceed 64 bytes"` |
| `coseAlgorithm` allowed | `coseAlgorithm in {-7, -8, -257, -49}` | `"Unsupported COSE algorithm ID: {id}"` |

**Breaking change**: `COSE_ED25519 (-19)` removed from `coseAlgorithm` allowlist. New credentials must use `COSE_EDSA (-8)`.

**Backward-compat note**: Credentials already persisted with `coseAlgorithm = -19` will fail `validate()` if reconstructed through this path. The data layer mapper must translate `-19` to `-8` when loading from the database, or a separate read-compat mode must be defined. See Quickstart for migration guidance.

---

## 3. `MakeCredentialOptions` Timeout Contract

### `getSafeTimeout(): Long`

```
INPUTS:
  timeout: Long? (from RP-provided PublicKeyCredentialCreationOptions)

OUTPUTS:
  Long in [MIN_CEREMONY_TIMEOUT_MS, MAX_CEREMONY_TIMEOUT_MS]

RULES:
  if timeout == null  -> return DEFAULT_TIMEOUT_MS (120_000)
  if timeout <= 0     -> rejected in validate() prior to this call
  if timeout < 30_000 -> return 30_000 (clamped to minimum)
  if timeout > 600_000 -> return 600_000 (clamped to accessibility ceiling)
  else                -> return timeout as-is
```

**Applies to**: Both `MakeCredentialOptions` and `GetAssertionOptions` (identical contract).

---

## 4. `PrfExtensionInput` Contract

### Constructor / `init` validation

| Rule | Condition | CTAP2 Error |
|---|---|---|
| Salt count in range | `salts.size in 1..2` | `CTAP2_ERR_INVALID_PARAMETER (0x02)` |
| No empty salts | `salts.all { it.isNotEmpty() }` | `CTAP2_ERR_INVALID_PARAMETER (0x02)` |

---

## 5. `PrfKeyDerivation` Service Contract

### `derive(salt: ByteArray, credentialHmacSecret: ByteArray): ByteArray`

**Algorithm**: HMAC-SHA-256  
**Key**: `credentialHmacSecret` (must be exactly 32 bytes)  
**Input**: `salt` (arbitrary bytes; non-empty)  
**Output**: exactly 32 bytes  

**Pre-conditions**:
- `credentialHmacSecret.size == 32`
- `salt.isNotEmpty()`

**Post-conditions**:
- Return value is exactly 32 bytes
- Deterministic: same inputs always produce same output
- The output bytes are zeroed from memory after use (see Constitution principle I)

**Throws**: `IllegalArgumentException` if pre-conditions are violated

---

### `deriveAll(input: PrfExtensionInput, credentialHmacSecret: ByteArray): PrfExtensionOutput`

**Contract**: Calls `derive()` for each salt in `input.salts`. Returns `PrfExtensionOutput` with outputs in the same order as inputs.

---

## 6. CTAP2 Algorithm Negotiation Contract

### `Ctap2MakeCredentialHandler` — Algorithm Selection

**Accepted algorithm IDs** (in preference order):

| Priority | COSE ID | Algorithm | Curve / Key Type |
|---|---|---|---|
| 1 | -7 | ES256 | P-256 / EC2 (`kty: 2, crv: 1`) |
| 2 | -8 | EdDSA | Ed25519 (`kty: 1, crv: 6`) |
| 3 | -257 | RS256 | RSA / RSASSA-PKCS1-v1_5 |
| 4 | -49 | ML-DSA-65 | N/A (post-quantum) |

**Rejected algorithm IDs**: `-9`, `-19`, `-51`, `-52` → respond with `CTAP2_ERR_UNSUPPORTED_ALGORITHM (0x26)`.

**No supported algorithm found**: respond with `CTAP2_ERR_UNSUPPORTED_ALGORITHM (0x26)`.

---

## 7. PRF CTAP2 Extension Parsing Contract

### `hmac-secret` Extension in Request

**CTAP2 extensions map key**: `"hmac-secret"` (String)

**Value structure** (CBOR map):
```
{
  1: <salt1: ByteArray>,   // required
  2: <salt2: ByteArray>    // optional
}
```

**Error conditions**:
- `salt1` missing → `CTAP2_ERR_MISSING_PARAMETER (0x14)`
- More than 2 salts present → `CTAP2_ERR_INVALID_PARAMETER (0x02)`
- Either salt is empty → `CTAP2_ERR_INVALID_PARAMETER (0x02)`
- Authenticator does not have per-credential HMAC secret → non-fatal; respond with `extensions map omitted` (PRF not supported for this credential)

### `hmac-secret` Extension in Response

**AuthenticatorData extensions map key**: `"hmac-secret"` (String)

**Value structure** (CBOR map):
```
{
  1: <output1: ByteArray (32 bytes)>,   // always present if 1+ salts processed
  2: <output2: ByteArray (32 bytes)>    // present only if salt2 was provided
}
```
