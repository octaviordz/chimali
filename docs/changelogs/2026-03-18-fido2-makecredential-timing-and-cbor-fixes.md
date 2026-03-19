# MakeCredential Windows Timing & CBOR Attestation Fixes

**Date:** 2026-03-18

## Overview
Resolved a critical integration bug where Windows hosts (specifically WebAuthn flows via Windows Hello) would consistently reject the Android Authenticator's MakeCredential response on the **first run** with an `ERROR_INVALID_DATA (0x8007000d)` / Unknown Device State payload. Additionally, aligned our cryptography formulation directly with reference spec models (like `rauth-android`) to guarantee cross-OS signature compliance.

## Detailed Changes

### 1. Bluetooth CTAPHID KeepAlive Timeout (0x8007000d)
- **Problem**: The first instantiation of `BouncyCastle` consumed ~175ms of startup time. Concurrently, `BluetoothHidTransportImpl.kt` arbitrarily scheduled a `delay(200L)` *before* emitting the first `CTAPHID_KEEPALIVE` (0x3B) packet. This pushed the first pulse securely beyond 350ms. Windows CTAP implementations forcefully abort channels if it does not receive a heartbeat within ~100ms. Though our phone eventually transmitted a 300-byte success CBOR blob, it arrived on a defunct channel, triggering `0x8007000d`.
- **Fix**: Calibrated the `keepaliveJob` routine to use `Dispatchers.IO` and explicitly wait exactly `75ms` prior to pulsing `PROCESSING` (`0x01`). This matches reference hardware timings and keeps Windows flawlessly tethered during extensive biometric/crypto pauses.

### 2. Attestation Object / AuthData Mathematical Purity
- **Problem**: `RegisterCredentialUseCase.kt` improperly enveloped the `options.challenge` (which behaves natively as the `clientDataHash` in CTAP2) inside a faux JSON envelope and hashed it *again*. Furthermore, `authData` lacked the critical `AT` (`0x40`) bit to denote attested credential data follows. `Ctap2MakeCredentialHandler.kt` was also submitting empty maps for `attStmt` during `"packed"` formats.
- **Fix**: Adjusted the payload to exactly emulate `rauth-android` spec operations:
  - `clientDataHash` is passed down unadulterated as raw `byte[]` and signed directly in parallel with `authenticatorData`.
  - The `0x40` bit is now natively OR'd into the bytes.
  - `buildAttestationStatementMap()` reliably maps `alg` and `sig` to the output CBOR response map so format specifications match output.

### 3. GetAssertion Pre-flight Probe Noise
- **Problem**: `GetAssertionUseCase.kt` strictly checked `e is Fido2Exception.CredentialNotFound` to suppress anticipated `CTAP2_ERR_NO_CREDENTIALS` probes initiated silently by Windows. Class aliasing inside `Fido2Exception.kt` obstructed this logical typecheck across the classpath, resulting in dense `Log.e` spam in Android Studio whenever Windows connected.
- **Fix**: Decoupled the routine from restrictive JVM type-checking; it now leverages the universally persistent `e.errorCode == "CREDENTIAL_NOT_FOUND"`, correctly formatting OS probes as standard Debug log activity.
