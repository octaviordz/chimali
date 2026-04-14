# Detailed Changes: FIDO2 ML-DSA WebAuthn Compatibility Fixes

**Date**: 2026-04-14  
**Feature Area**: FIDO2 / Post-Quantum Cryptography (ML-DSA)  
**Status**: Completed  

## Goal
Resolve critical compatibility issues preventing ML-DSA-65 (Dilithium) credentials from registering and authenticating correctly against standard WebAuthn servers (specifically the Yubico WebAuthn developer demo). Achieve full FIDS FIPS 204 and WebAuthn/COSE specification compliance.

## Root Cause Analysis and Fixes

### 1. COSE Identifier Correction
- **Issue**: ML-DSA-65 was using the incorrect COSE algorithm identifier `-257` (which collided with RS256).
- **Fix**: Updated the identifier to the official IANA/draft-standard value of `-49` across all layers (`PostQuantumCrypto.kt`, `CborCodec.kt`, `AttestationObject.kt`, and associated tests).

### 2. Key Encoding Optimization (SPKI Header Stripping)
- **Issue**: The authenticator was sending the full ~1988-byte DER `SubjectPublicKeyInfo` (SPKI) as the `pub` (-1) entry in the COSE key map. WebAuthn servers rejected this with "byte string too long" or "Invalid key type" because they expect only the raw 1952-byte key.
- **Fix**: Refactored `CborCodec.encodeCosePublicKeyFromJavaKey` to use BouncyCastle's `SubjectPublicKeyInfo` parser to extract and send only the raw key data, strictly following NIST FIPS 204 §5.

### 3. Attestation Signing Logic Fixes
- **Issue**: The packed self-attestation signature was defaulting to ES256 even when the credential was ML-DSA-65, causing "Invalid data" errors during signature verification.
- **Fix**: 
    - Updated `RegisterCredentialUseCase.createAttestationObject` to pass the correct algorithm ID to the crypto signing service.
    - Eliminated double COSE encoding of the public key to ensure consistency between the signed bytes and the reported public key.

### 4. PQC Signature Size Constraints
- **Issue**: `AttestationObject.MAX_CERT_SIZE` was hardcoded to 2048 bytes. ML-DSA-65 signatures are approximately 3309 bytes, causing the registration flow to silently fail or exclude the signature.
- **Fix**: Increased the buffer limit to 4096 bytes to accommodate post-quantum signature sizes.

### 5. Deterministic Key Derivation (Android PRNG Hardening)
- **Issue**: Android's `SecureRandom("SHA1PRNG")` implementation is non-deterministic; it mixes in system entropy even after `setSeed()`. This meant the public key derived during registration and the private key re-derived during signing belonged to different key pairs, causing "Invalid data" on every authentication/registration attempt.
- **Fix**: Implemented `DeterministicSecureRandom` (a SHA-256 CTR-DRBG) to ensure that the same BIP-85-derived seed always produces the identical ML-DSA-65 key pair across application restarts and service calls.

### 6. AuthData Byte Consistency
- **Issue**: divergences between the `authData` byte sequence that was signed and the one transmitted to the server (caused by re-serialization) triggered "Invalid data" errors.
- **Fix**: Updated `Ctap2MakeCredentialHandler` to use the exact pre-signed `authData` bytes stored in the `AttestationStatement` rather than re-building the structure from the domain model.

## Verification Results
- **Automated Tests**: 100% pass rate in the `:feature:fido2` test suite (336 tests).
- **Regression Tests**: Added `CborCodecTest.kt` with explicit sign/verify round-trips mirroring server-side behavior (extracting raw pub from COSE, reconstructing SPKI, and verifying).
- **Manual Verification**: Successfully registered and verified ML-DSA-65 passkeys on the [Yubico WebAuthn Demo](https://demo.yubico.com/webauthn-developers) with "Success" status and valid packed self-attestation.

## References
- **IANA COSE Algorithms**: https://www.iana.org/assignments/cose/cose.xhtml
- **NIST FIPS 204 (ML-DSA)**: https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.204.pdf
- **WebAuthn Spec §8.2 (Packed Attestation)**: https://www.w3.org/TR/webauthn-2/#sctn-packed-attestation
