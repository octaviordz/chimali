# Quickstart: WebAuthn Level 3 Compliance

**Feature**: `032-webauthn-l3-compliance`  
**For**: Developers implementing the tasks in `tasks.md`  

---

## Overview

This guide summarises the five compliance gaps and the exact code locations to change. Read [research.md](research.md) for rationale and [data-model.md](data-model.md) for entity-level detail.

---

## Fix 1 — COSE Algorithm Constant: -19 → -8

**Why**: WebAuthn L3 § 5.4 designates `-8` as the EdDSA identifier. `-19` is NOT RECOMMENDED.

### Files to update

**`PasskeyCredential.kt`**
```kotlin
// BEFORE
const val COSE_ED25519 = -19

// AFTER
const val COSE_EDSA = -8   // EdDSA / Ed25519, per WebAuthn L3 § 5.4
```

Update `validate()` to allow `-8` instead of `-19`:
```kotlin
require(
    coseAlgorithm == COSE_ES256 ||
    coseAlgorithm == COSE_EDSA  ||   // was COSE_ED25519
    coseAlgorithm == COSE_ML_DSA_65 ||
    coseAlgorithm == COSE_RS256,
)
```

**`Ctap2MakeCredentialHandler.kt`** (and `Ctap2GetAssertionHandler.kt`):
```kotlin
// BEFORE
private const val COSE_ED25519 = -19

// AFTER
private const val COSE_EDSA = -8

// Update when block:
COSE_EDSA -> COSE_EDSA to PublicKeyCredentialParameters.createEdDsa()
```

### Data migration note

Existing credentials stored with `coseAlgorithm = -19` need a one-time migration in the SQLDelight mapper or a version migration script. Add a `MigrateCoseAlgorithmWorker` or update the mapper to translate `-19` → `-8` on read.

---

## Fix 2 — Credential ID Byte-Level Validation

**Why**: External authenticators may present IDs of any size up to 1023 bytes. Encrypted-blob IDs must not be rejected.

### File to update: `CredentialId.kt`

```kotlin
companion object {
    const val CREDENTIAL_ID_SIZE_BYTES = 32  // kept for generated IDs

    // NEW
    const val MIN_CREDENTIAL_ID_BYTES = 16
    const val MAX_CREDENTIAL_ID_BYTES = 1023

    fun fromByteArray(bytes: ByteArray): CredentialId {
        require(bytes.size in MIN_CREDENTIAL_ID_BYTES..MAX_CREDENTIAL_ID_BYTES) {
            "Credential ID must be between $MIN_CREDENTIAL_ID_BYTES " +
            "and $MAX_CREDENTIAL_ID_BYTES bytes, got ${bytes.size}"
        }
        return CredentialId(
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
        )
    }
}
```

`generate()` is unmodified — it already produces 32 bytes (256-bit entropy).

---

## Fix 3 — String Truncation: Character Count → Byte Count

**Why**: WebAuthn § 6.4.1.2 specifies byte thresholds, not code point counts.

### File to update: `PasskeyCredential.kt`

```kotlin
// BEFORE
require(userName.length <= MAX_NAME_LENGTH) { "..." }
require(userDisplayName.length <= MAX_DISPLAY_NAME_LENGTH) { "..." }

// AFTER
require(userName.toByteArray(Charsets.UTF_8).size <= MAX_NAME_LENGTH) { "..." }
require(userDisplayName.toByteArray(Charsets.UTF_8).size <= MAX_DISPLAY_NAME_LENGTH) { "..." }
```

Constants `MAX_NAME_LENGTH = 64` and `MAX_DISPLAY_NAME_LENGTH = 64` remain the same value.

---

## Fix 4 — Dynamic Ceremony Timeout + Accessibility Ceiling

**Why**: Static 60-second fallback fails § 5.1.3 dynamic adjustment and § 15.1 accessibility requirements.

### Files to update: `MakeCredentialOptions.kt` and `GetAssertionOptions.kt`

```kotlin
companion object {
    // UPDATED / NEW constants
    const val MIN_CEREMONY_TIMEOUT_MS = 30_000L    // 30 s — hard minimum
    const val MAX_CEREMONY_TIMEOUT_MS = 600_000L   // 10 min — accessibility ceiling
    const val DEFAULT_TIMEOUT_MS = 120_000L         // 2 min — no-hint default (was 60 s)
}

fun getSafeTimeout(): Long =
    timeout?.coerceIn(MIN_CEREMONY_TIMEOUT_MS, MAX_CEREMONY_TIMEOUT_MS)
        ?: DEFAULT_TIMEOUT_MS
```

Remove the old `MAX_TIMEOUT_MS = 300_000L` constant from both files.

---

## Fix 5 — PRF / hmac-secret Extension

**Why**: Required for deterministic key derivation (WebAuthn § 10.1.4 / § 16.17.1.1).

### Step A — New domain models

Create `PrfExtensionInput.kt`:
```kotlin
data class PrfExtensionInput(
    val salts: List<ByteArray>,
) {
    init {
        require(salts.size in 1..MAX_SALTS) {
            "PRF extension requires 1 or 2 salts, got ${salts.size}"
        }
        salts.forEach { require(it.isNotEmpty()) { "PRF salt cannot be empty" } }
    }

    companion object {
        const val MAX_SALTS = 2
        const val MAX_OUTPUT_BYTES = 32
    }
}

data class PrfExtensionOutput(
    val outputs: List<ByteArray>,
)
```

### Step B — New crypto service

Create `PrfKeyDerivation.kt`:
```kotlin
@Single
class PrfKeyDerivation {
    fun derive(salt: ByteArray, credentialHmacSecret: ByteArray): ByteArray {
        require(credentialHmacSecret.size == 32) { "HMAC secret must be 32 bytes" }
        require(salt.isNotEmpty()) { "Salt cannot be empty" }

        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(credentialHmacSecret))
        hmac.update(salt, 0, salt.size)
        val out = ByteArray(hmac.macSize)
        hmac.doFinal(out, 0)
        return out   // exactly 32 bytes
    }

    fun deriveAll(
        input: PrfExtensionInput,
        credentialHmacSecret: ByteArray,
    ): PrfExtensionOutput =
        PrfExtensionOutput(outputs = input.salts.map { derive(it, credentialHmacSecret) })
}
```

### Step C — CTAP2 handler integration

In `Ctap2MakeCredentialHandler.decodeMakeCredentialRequest()`, extract hmac-secret:
```kotlin
val prfInput: PrfExtensionInput? = extensions?.let { ext ->
    val hmacSecret = ext["hmac-secret"] as? Map<*, *> ?: return@let null
    val salt1 = hmacSecret[1L] as? ByteArray ?: return@let null
    val salt2 = hmacSecret[2L] as? ByteArray
    val salts = if (salt2 != null) listOf(salt1, salt2) else listOf(salt1)
    runCatching { PrfExtensionInput(salts) }.getOrNull()
}
```

Include `prfInput` in `MakeCredentialRequest` and thread it through to `handleMakeCredential()`.

---

## Testing Checklist

- [ ] `CredentialId.fromByteArray(ByteArray(15))` throws `IllegalArgumentException`
- [ ] `CredentialId.fromByteArray(ByteArray(1024))` throws `IllegalArgumentException`
- [ ] `CredentialId.fromByteArray(ByteArray(16))` succeeds
- [ ] `CredentialId.fromByteArray(ByteArray(1023))` succeeds
- [ ] `PasskeyCredential` with `coseAlgorithm = -19` fails validation
- [ ] `PasskeyCredential` with `coseAlgorithm = -8` passes validation
- [ ] `PasskeyCredential` with `userDisplayName` of 65 UTF-8 bytes (e.g., 65 ASCII chars) fails validation
- [ ] `PasskeyCredential` with `userDisplayName` of 64 UTF-8 bytes passes validation
- [ ] `MakeCredentialOptions.getSafeTimeout()` with `timeout = null` returns 120_000
- [ ] `MakeCredentialOptions.getSafeTimeout()` with `timeout = 10_000` returns 30_000 (clamped)
- [ ] `MakeCredentialOptions.getSafeTimeout()` with `timeout = 700_000` returns 600_000 (clamped)
- [ ] `MakeCredentialOptions.getSafeTimeout()` with `timeout = 90_000` returns 90_000 (as-is)
- [ ] Algorithm negotiation selects `-8` when RP requests `[-8, -7]`
- [ ] Algorithm negotiation rejects `-19`
- [ ] `PrfExtensionInput(emptyList())` throws `IllegalArgumentException`
- [ ] `PrfExtensionInput(listOf(s1, s2, s3))` throws `IllegalArgumentException`
- [ ] `PrfKeyDerivation.derive()` returns exactly 32 bytes
- [ ] `PrfKeyDerivation.derive()` is deterministic (same input → same output)
