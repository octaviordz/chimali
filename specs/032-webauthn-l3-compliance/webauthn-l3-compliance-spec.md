# WebAuthn Level 3 Compliance Specification

## 1. Overview
This document specifies the engineering requirements and necessary updates for the Chimali Android application to achieve compliance with the "Web Authentication: An API for accessing Public Key Credentials - Level 3" specification (W3C Candidate Recommendation Snapshot, 13 January 2026).

## 2. Requirements

### 2.1 Credential ID Constraints and Entropy
* **Size Constraint:** Implement strict bounds-checking to reject or truncate Credential IDs exceeding the 1023-byte maximum limit on the `[[identifier]]` slot.
* **Entropy Minimums:** Update the credential generation algorithm to ensure that entropy-based IDs meet the minimum requirements of 100 bits of entropy and at least 16 bytes of length.
* **Format Support:** Explicitly support and recognize both conformant forms for Credential IDs:
  * Entropy-based IDs.
  * Encrypted public key credential source (encrypted-blob) forms for stateless implementations.

### 2.2 String Truncation Logic
* **Minimum Truncation Size:** Refactor UI management logic to ensure that `name` and `displayName` fields are **never** truncated below a 64-byte threshold. This replaces the current 32-byte limit to improve user experience and distinguishability during selection ceremonies.

### 2.3 COSE Algorithm Identifiers and Cryptographic Parameters
* **EdDSA Support:** Integrate support for the EdDSA algorithm identifier (`-8`) using the Ed25519 curve (`crv: 6`).
* **ES256 Verification:** Ensure that the ES256 algorithm (`-7`) specifically utilizes the P-256 curve (`crv: 1`) and `kty: 2` (EC2).
* **Algorithm Deprecation:** Remove explicitly "NOT RECOMMENDED" identifiers from the default preference sequence, specifically: `-19`, `-9`, `-51`, and `-52`.

### 2.4 Ceremony Timeouts and Accessibility
* **Dynamic Timeouts:** Replace the static 60-second `lifetimeTimer` with a dynamic timer that respects the Relying Party's (RP) provided timeout hint via `PublicKeyCredentialCreationOptions`.
* **Accessibility Enhancements:** Adjust the "reasonable range" logic for timeouts to include a higher ceiling, ensuring users with cognitive or motor-skill special needs are not prematurely timed out during authorization gestures.

### 2.5 Attestation Generation and Conveyance
* **Attestation Generation (Authenticator Role):** Ensure `authenticatorMakeCredential` correctly generates the `attestationObject`, including `authData` (AAGUID, Credential ID, Public Key) and the `attStmt`. Support generating Attestation Types such as Basic, Self, or AttCA.
* **Attestation Conveyance:** Maintain the integrity of the attestation object during conveyance to allow the RP to successfully execute the verification algorithm according to the `attestationConveyancePreference`.

### 2.6 HMAC-Secret (PRF) Extension
* **Salt Processing Limits:** Support the processing of exactly one or two salts provided by the RP via `AuthenticationExtensionsAuthenticatorInputs`.
* **CTAP2 Extension Support:** Implement support for the `hmac-secret` CTAP2 extension to securely process PRF requests within the logical security boundary.
* **Deterministic Outputs:** Output deterministic HMAC-SHA-256 derived bits (up to 32 bytes) through the `AuthenticationExtensionsAuthenticatorOutputs` map within the authenticator data.

## 3. Implementation Plan
1. **Core Cryptography:** Update the credential generation, COSE algorithm preference lists, and PRF extension logic to meet L3 requirements.
2. **Validation and Constraints:** Implement strict bounds checking for Credential IDs and enforce minimum entropy requirements.
3. **UI and Accessibility:** Update string truncation logic for `name`/`displayName` and implement dynamic, accessible ceremony timeouts.
4. **Testing:** Update unit and integration tests to verify changes against the WebAuthn Level 3 conformance standards.
