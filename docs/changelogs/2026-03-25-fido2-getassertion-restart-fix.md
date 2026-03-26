# FIDO2 Authentication After Restart Fix
**Date:** 2026-03-25

## Summary
Fixed a critical bug where FIDO2 authentication (`GetAssertion`) consistently failed with **"Could not verify authentication signature"** after closing and reopening the Chimali app, despite working correctly immediately after registration.

## Root Cause
`HdkEcdhP256.generateDeviceKeyPair()` calls `P256Group.generateKeyPair()` which uses `SecureRandom` — producing a **completely random P-256 device key pair** on every call.

`WalletMasterSeedProvider.ensureInitialized()` called this on every cold start (after the in-memory cache is cleared). The signing formula is:

```
signature_key = sk_device × blindingFactor mod n
```

Each restart generated a new random `sk_device`, producing a different blinded signing key that didn't correspond to the public key stored during registration — causing every post-restart authentication to fail.

## Fix
Added a private `deriveDeviceKeyPair(masterSeed)` method to `WalletMasterSeedProvider` that derives the P-256 device key pair **deterministically** from the master seed using `HMAC-SHA512`:

```
raw = HMAC-SHA512("chimali_device_key_v1", masterSeed)
sk  = BigInteger(raw[0..31]) mod P256.order
pk  = sk × G
```

The result is always identical given the same BIP-39 master seed (which is persisted in `EncryptedSharedPreferences`), so the device key pair is consistent across all app restarts.

## Files Changed
- **`WalletMasterSeedProvider.kt`**: Replaced `hdkManager.generateDeviceKeyPair()` with `deriveDeviceKeyPair(seed)`. Added `P256Group` and `BigInteger` imports.

## Migration Note
Existing credentials registered before this fix used a random device key pair that is no longer reproducible. A one-time **re-registration** is required for those credentials.
