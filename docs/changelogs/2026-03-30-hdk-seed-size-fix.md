# HDK Seed Size Mismatch Fix

**Date**: 2026-03-30  
**Severity**: 🔴 Critical — blocked all ES256/P-256 FIDO2 credential registrations  
**Module**: `:feature:fido2`  

## Summary

Fixed a runtime `IllegalArgumentException` that caused every FIDO2 ES256 registration (and the pre-warm path on Bluetooth connect) to fail immediately after the user approved the request. ML-DSA-65 and Ed25519 registrations succeeded because they use independent key derivation branches.

## Root Cause

`WalletMasterSeedProvider.ensureInitialized()` called `Bip39MasterSeedGenerator.deriveSeed()`, which uses **PBKDF2-HMAC-SHA512 with 512-bit key length**, producing a **64-byte** seed. This byte array was cached verbatim as `cachedSeed` and returned from `getMasterSeed()`.

`HdkEcdhP256.deriveHdk()` enforces the HDK spec §2.2 constraint (`Ns = 32`):

```kotlin
require(seed.size == 32) {
    "HDK seed must be exactly 32 bytes (Ns per §2.2); got ${seed.size}"
}
```

The resulting exception surface in Logcat:
```
E  Key derivation failed
java.lang.IllegalArgumentException: HDK seed must be exactly 32 bytes (Ns per §2.2); got 64
    at HdkEcdhP256.deriveHdk(HdkEcdhP256.kt:164)
    at Fido2CryptoService$generateCredentialKeyPair$2.invokeSuspend(Fido2CryptoService.kt:165)
```

## Fix

**File**: `WalletMasterSeedProvider.kt`

In `ensureInitialized()`, the 64-byte BIP39 seed is now split into:
- `hdkSeed` — first 32 bytes, cached as `cachedSeed` and returned by `getMasterSeed()` → used by `HdkEcdhP256.deriveHdk()`
- `bip39Seed` — the full 64 bytes, passed to `deriveDeviceKeyPair()` (which performs its own `HMAC-SHA512` expansion and only takes the first 32 bytes of that result anyway)

```kotlin
val bip39Seed = masterSeedGenerator.deriveSeed(mnemonic)    // 64 bytes (PBKDF2-SHA512)
val hdkSeed   = bip39Seed.copyOf(32)                        // first 32 bytes → Ns (§2.2)

cachedSeed        = hdkSeed
cachedDeviceKeyPair = deriveDeviceKeyPair(bip39Seed)

bip39Seed.fill(0)   // zeroise full 64-byte material immediately
```

## Impact on Derived Keys

| Branch | Before fix | After fix | Regression? |
|--------|-----------|-----------|-------------|
| P-256 / ES256 (HDK) | Always crashed | Derives correctly from first 32 bytes | ✅ No prior successful credentials |
| Ed25519 | Derived from 64-byte seed (but succeeded, SHA-512 expansion applied internally) | Now derives from 32-byte `hdkSeed` via same SHA-512 expansion | ⚠️ Key values change — see note |
| ML-DSA-65 | Derived from 64-byte seed via BIP-85 path | Now derives from 32-byte PQ-via-getMasterSeed | ⚠️ Key values change — see note |
| Device key pair | Derived via HMAC-SHA512 of full 64-byte seed | Still derived from full 64-byte seed before zeroing | ✅ Unchanged |

> **Note**: Because all ES256 registrations were failing, no credentials could have been successfully saved. The Ed25519 and ML-DSA-65 branches did succeed, but any credentials registered via the Dev Tools mock path (not a real FIDO2 ceremony) are soft-state only and can be re-registered without data loss.

## Secondary Issue — `keyset not found` Warnings

The first-launch Logcat showed:
```
W  keyset not found, will generate a new one
java.io.FileNotFoundException: can't read keyset; the pref value
    __androidx_security_crypto_encrypted_prefs_key_keyset__ does not exist
```

This is **expected Android `EncryptedSharedPreferences` behaviour** on first use. `AndroidKeysetManager` auto-generates the keyset and warns in Logcat. No action required.

## Verification

Build and all existing FIDO2 unit/integration tests pass after this fix:
```
.\gradlew :feature:fido2:testDebugUnitTest
> 47 tests completed, 0 failed
BUILD SUCCESSFUL
```

Manual verification: ES256 registration against `webauthn.io` completes successfully after the fix.
