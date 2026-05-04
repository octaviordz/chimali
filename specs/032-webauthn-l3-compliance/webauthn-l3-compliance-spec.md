# WebAuthn Level 3 Compliance Specification

## 1. Overview
This document specifies the engineering requirements and necessary updates for the Chimali Android application to achieve compliance with the "Web Authentication: An API for accessing Public Key Credentials - Level 3" specification (W3C Candidate Recommendation Snapshot, 13 January 2026).

**Compliance Status: COMPLIANT (Phase 1-9 Complete)**

## 2. Requirements

### 2.1 Credential ID Constraints and Entropy
* **Status: RESOLVED**
* **Size Constraint:** Implemented strict bounds-checking to reject or truncate Credential IDs exceeding the 1023-byte maximum limit on the `[[identifier]]` slot.
* **Entropy Minimums:** Updated the credential generation algorithm to ensure that entropy-based IDs meet the minimum requirements of 100 bits of entropy and at least 16 bytes of length.
* **Format Support:** Explicitly support and recognize both conformant forms for Credential IDs:
  * Entropy-based IDs.
  * Encrypted public key credential source (encrypted-blob) forms for stateless implementations.

### 2.2 String Truncation Logic
* **Status: RESOLVED**
* **Minimum Truncation Size:** Refactored UI management logic to ensure that `name` and `displayName` fields are **never** truncated below a 64-byte threshold. This replaces the legacy 32-byte limit to improve user experience and distinguishability during selection ceremonies.

### 2.3 COSE Algorithm Identifiers and Cryptographic Parameters
* **Status: RESOLVED**
* **EdDSA Support:** Integrated support for the EdDSA algorithm identifier (`-8`) using the Ed25519 curve (`crv: 6`).
* **ES256 Verification:** Ensured that the ES256 algorithm (`-7`) specifically utilizes the P-256 curve (`crv: 1`) and `kty: 2` (EC2).
* **Algorithm Deprecation:** Removed explicitly "NOT RECOMMENDED" identifiers from the default preference sequence, specifically: `-19`, `-9`, `-51`, and `-52`.

### 2.4 Ceremony Timeouts and Accessibility
* **Status: RESOLVED**
* **Dynamic Timeouts:** Replaced the static 60-second `lifetimeTimer` with a dynamic timer that respects the Relying Party's (RP) provided timeout hint via `PublicKeyCredentialCreationOptions`.
* **Accessibility Enhancements:** Adjusted the "reasonable range" logic for timeouts to include a higher ceiling, ensuring users with cognitive or motor-skill special needs are not prematurely timed out during authorization gestures.

### 2.5 Attestation Generation and Conveyance
* **Status: RESOLVED**
* **Attestation Generation (Authenticator Role):** Ensured `authenticatorMakeCredential` correctly generates the `attestationObject`, including `authData` (AAGUID, Credential ID, Public Key) and the `attStmt`. Support for generating Attestation Types such as Basic, Self, and AttCA (T036).
* **Attestation Conveyance:** Maintained the integrity of the attestation object during conveyance. Refactored CBOR serialization to use pre-signed `authData` blocks, ensuring 100% signature verification success.

### 2.6 HMAC-Secret (PRF) Extension
* **Status: RESOLVED**
* **Salt Processing Limits:** Support for processing exactly one or two salts provided by the RP via `AuthenticationExtensionsAuthenticatorInputs` (T045).
* **CTAP2 Extension Support:** Implemented support for the `hmac-secret` CTAP2 extension to securely process PRF requests within the logical security boundary.
* **Deterministic Outputs:** Outputs deterministic HMAC-SHA-256 derived bits (up to 32 bytes) through the `AuthenticationExtensionsAuthenticatorOutputs` map within the authenticator data.

## 3. Implementation Plan Status
1. **Core Cryptography:** **COMPLETE** — Updated credential generation, COSE algorithm preference lists, and PRF extension logic.
2. **Validation and Constraints:** **COMPLETE** — Implemented strict bounds checking and enforced minimum entropy.
3. **UI and Accessibility:** **COMPLETE** — Updated string truncation and implemented dynamic, accessible timeouts.
4. **Testing:** **COMPLETE** — All unit and integration tests (including PRF and L3 compliance vectors) passing via local CI pipeline.

