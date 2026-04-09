# FIDO2 Bluetooth Authenticator: Windows & WebAuthn Integration Analysis

**Date:** March 4, 2026  
**Component:** Chimali FIDO2 Bluetooth HID Authenticator  

This document provides a deep-dive analysis into the specific bugs, protocol nuances, and system constraints encountered while implementing a FIDO2 (CTAP2) authenticator over Bluetooth Low Energy (BLE) Human Interface Device (HID) for Windows 11 and webauthn.io.

---

## 1. Executive Summary

Implementing a FIDO2 authenticator over Bluetooth requires strict adherence to not just the FIDO2/CTAP2 specifications, but also to undocumented, OS-specific probing behaviors. Windows 11 employs a rigid credential negotiation pipeline. The Chimali authenticator initially failed across multiple stages of this pipeline — from protocol negotiation, to CBOR encoding conventions, and finally cryptographic payload formatting.

By systematically addressing these layers, the authenticator now successfully completes end-to-end `MakeCredential` (registration) and `GetAssertion` (authentication) operations.

---

## 2. Protocol Negotiation & OS Quirks

### 2.1 The Mandatory U2F Probe
**Symptom:** Windows would connect via Bluetooth, send an initialization packet, and then immediately drop the connection or prompt the user to "insert a USB security key."
**Analysis:** Before Windows attempts CTAP2 (FIDO2) commands, its `webauthn.dll` library forcibly probes the device using the archaic CTAP1 (U2F) `U2F_REGISTER` command. If the device does not respond, or responds with an unexpected HID error instead of a valid U2F application error, Windows aborts the entire flow and refuses to attempt CTAP2.
**Fix:** We implemented a dummy U2F handler that explicitly catches `U2F_REGISTER` and `U2F_AUTHENTICATE`, returning the exact U2F error code `SW_WRONG_DATA` (0x6A80). This signals to Windows: *"I am a FIDO device, but I don't support this specific U2F request."* Windows then successfully pivots to CTAP2.

### 2.2 GetInfo Metadata Requirements
**Symptom:** Windows would send `authenticatorGetInfo` (0x04) but reject the device immediately after receiving the response, showing "Unknown device state."
**Analysis:** The CTAP2 `GetInfo` response is a CBOR map defining the authenticator's capabilities. Windows requires specific keys to be present to trust a cross-platform authenticator for passkeys:
1. `pinUvAuthProtocols` (Key `0x06`): Must be present if the device supports any form of user verification, even if a PIN hasn't been set yet.
2. `options.uv` (Key `0x04`): Must be explicitly set to `true` to declare internal biometric capabilities.
**Fix:** The `Ctap2ResponseBuilder` was updated to include `"6" to listOf(2L, 1L)` (PIN protocols v2 and v1) and correctly set the `uv` option to true. 

---

## 3. CBOR Encoding Strictness

### 3.1 Negative Integer Corruption
**Symptom:** COSE Algorithm identifiers (e.g., ES256 which is `-7`) were being corrupted in transit.
**Analysis:** FIDO2 heavily utilizes the COSE (CBOR Object Signing and Encryption) registry. Algorithms are represented as negative integers. The custom `CborCodec` had a bug in its integer encoding branch that treated negative Byte/Int/Long values as unsigned or corrupted them into float16 representations.
**Fix:** Refactored the `CborCodec` integer serialization logic to properly implement CBOR Major Type 1 (negative integers: `-1 - n`).

### 3.2 String Keys vs. Integer Keys
**Symptom:** Windows rejected `MakeCredential` and `GetAssertion` responses as invalid/malformed.
**Analysis:** The WebAuthn specification often shows JSON examples with string keys (e.g., `"fmt"`, `"authData"`). However, the CTAP2 transport specification *mandates* that the outer response maps use specific Integer keys (e.g., `1` for fmt, `2` for authData). 
**Fix:** Ensured the `CborCodec` and individual handlers (like `Ctap2MakeCredentialHandler`) explicitly encode the outer map keys as CBOR integers, not CBOR strings.

---

## 4. Cryptographic Payload & Alignment Bugs

### 4.1 AuthenticatorData Flags (Missing AT Bit)
**Symptom:** Windows Edge/Chrome browser would completely crash (hard close) the moment the user approved the passkey creation on the Android app.
**Analysis:** During `MakeCredential`, the authenticator returns an `AuthenticatorData` structure. Byte 32 contains bit flags. If the response includes Public Key data (which MakeCredential always does), the `AT` (Attested Credential Data) flag (bit 6, `0x40`) MUST be set. If it is missing, the Windows WebAuthn parser attempts to read the subsequent 100+ bytes as normal extensions instead of a public key, causing a critical buffer overflow or parsing crash in the browser process.
**Fix:** Forced the `AT` flag (`flags = rawFlags or 0x40 or 0x01`) in `buildAuthenticatorData`.

### 4.2 Binary Data vs. Base64 Serialization
**Symptom:** `GetAssertion` succeeded on the Android side, but Windows immediately threw `E_INVALIDARG` (0x80070057) and failed the authentication.
**Analysis:** In `Ctap2GetAssertionHandler`, the `authData` and `signature` byte arrays were being passed through `Base64.getEncoder().encodeToString()` before being packed into the CBOR response. While JSON-based WebAuthn (browser to RP) uses Base64, CTAP2 over Bluetooth requires raw binary (CBOR Byte Strings, Major Type 2). Windows was trying to parse an ECDSA DER signature out of an ASCII text string, causing an immediate argument exception.
**Fix:** Passed the raw `ByteArray` objects directly into the CBOR map so they are encoded as binary byte strings.

---

## 5. Key Management & State Integrity

### 5.1 The "KeyNotFound" Alias Mismatch
**Symptom:** After successful registration, every authentication attempt failed locally with a `KeyNotFound` exception from the Android KeyStore.
**Analysis:** A divergence in string formatting meant `RegisterCredentialUseCase` was saving the private key under the alias `fido2_credential_<id>`, but `GetAssertionUseCase` was trying to load it using `fido2_cred_<id>`.
**Fix:** Centralized alias generation into `Fido2CryptoService.credentialAlias(id)` and enforced its use across all use cases.

### 5.2 Transient In-Memory Key Discard
**Symptom:** Even fixing the alias didn't solve the signing failure. The public key stored on the server didn't match the private key on the device.
**Analysis:** The MakeCredential flow suffered from a severe dual-generation race:
1. `RegisterCredentialUseCase` generated an *in-memory* EC keypair. It sent this public key to the Relying Party.
2. It passed the metadata to `CredentialRepository.saveCredential()`.
3. The repository called `CredentialStorageService.storePrivateKey()`, which generated a *brand new, permanent* hardware-backed keypair in Android KeyStore.
Because the private half of the first key was immediately discarded when the function returned, the passkey was permanently orphaned.
**Fix:** Removed key generation from the repository layer entirely. `RegisterCredentialUseCase` now calls `Fido2CryptoService.generateCredentialKeyPair()`, which utilizes `KeyGenParameterSpec` to generate the key directly inside the hardware secure element/KeyStore *before* returning the public key to the Relying Party.

### 5.3 Deterministic AAGUIDs
**Symptom:** Windows sometimes warned that the security key was unknown or mismatched.
**Analysis:** The `generateAAGUID()` function was utilizing `SecureRandom()`. The AAGUID (Authenticator Attestation Global Unique Identifier) must remain static for a specific authenticator model so relying parties and OS platforms can look up its metadata. Changing it on every registration breaks this trust linkage.
**Fix:** Hardcoded a static `CHIMALI_AAGUID` (`"CHIMALI\0"` followed by version bytes) and applied it consistently across `GetInfo` and `MakeCredential`.

---

## 6. Transport Constraints & L2CAP MTU

### 6.1 The L2CAP MTU Barrier (ERROR_NOT_SUPPORTED 0x32)
**Symptom:** Windows would successfully bond and discover the Authenticator over Bluetooth Classic, but any attempt to initiate the CTAPHID handshake from the WebAuthN framework (`CTAPHID_INIT`) would fail instantly. Windows Event Viewer logged `0x32 ERROR_NOT_SUPPORTED` coming directly from the `bthid.sys` driver.
**Analysis:** FIDO specifications mandate 64-byte packets. However, over Bluetooth Classic, the L2CAP interrupt channel enforces a strict 64-byte Maximum Transmission Unit (MTU). If a 64-byte HID Report is sent, it requires an additional 2 bytes of overhead (a 1-byte HID header and a 1-byte Report ID), totaling 66 bytes. The Windows 11 `bthid.sys` driver refuses to fragment output reports over L2CAP. When asked to send a 66-byte frame down a 64-byte pipe, it panics and throws `ERROR_NOT_SUPPORTED`. 
**Fix:** The FIDO HID Report Descriptor was explicitly altered to specify a Report Size of **62 bytes** (`0x3E`) instead of 64 (`0x40`). The CTAPHID parser and assembler in the Android app were calibrated strictly to these boundaries. By delivering 62-byte payloads, the total L2CAP frame resolves to exactly 64 bytes, smoothly bypassing the Windows driver MTU fragmentation limitations and allowing stable end-to-end communication.

## 7. Diagnostic Tools & Troubleshooting

For practical debugging of the issues described above, including recommended Android Studio Logcat filters and Windows Event Viewer strategies, refer to the [Diagnostic Tools Guide](./research/diagnostic_tools.md).

## Conclusion

Building a FIDO2 Bluetooth authenticator involves navigating severe undocumented constraints in the Windows OS CTAP stack. Minor encoding errors that might be ignored by a lenient parser (like a missing flag or a base64 string instead of a byte array) result in catastrophic localized failures (browser crashes, `E_INVALIDARG`). 

By standardizing around strict CBOR integer types, ensuring binary data fidelity, centralizing Android KeyStore management, and mocking legacy U2F responses, the Chimali device successfully acts as a fully compliant cross-platform roaming authenticator.
