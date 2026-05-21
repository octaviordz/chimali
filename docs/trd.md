# Technical Requirements Document (TRD) - Chimali

**Project Name**: Chimali  
**Version**: 0.1.0  
**Status**: Draft  
**Reference Document**: [BRD](brd.md)

---

## 1. Executive Summary (Technical Context)
This Technical Requirements Document (TRD) translates the business goals outlined in the [BRD](brd.md) into actionable technical specifications. While the BRD focuses on the "what" and "why," the TRD focuses on the "how" and the specific technical constraints for implementation.

## 2. Project Overview (Architectural)
Chimali follows a **Clean Architecture** pattern with **MVI (Model-View-Intent)** for the presentation layer.

- **Presentation Layer**: Jetpack Compose (Modern UI, M3).
- **Domain Layer**: Pure Kotlin (Core logic, Use cases).
- **Data Layer**: SQLDelight (Persistence), Android KeyStore (Security), Bluetooth HID API (Transport).

## 3. Business Goals & Objectives (Technical Translation)
- **Security Leadership**: Integration of BouncyCastle 1.80+ for **ML-DSA** (Post-Quantum digital signatures for FIDO2 attestation). ML-KEM support is available for future remote key provisioning flows.
- **Cross-Platform Utility**: Custom HID descriptors to ensure compatibility across OS types via `BluetoothHidDevice`.
- **Modern UX**: Use of `androidx.compose.animation` for micro-interactions and M3 dynamic color tokens.

## 4. Functional Requirements (Technical Specifications)
### 4.1 Core Credential Management
#### [FR-VAULT-010] Secure Storage
- **Technical Detail**: Use **SQLDelight** for structured data. Sensitive fields (passwords, notes) must be encrypted before insertion using the encryption strategy defined in NFR-SEC-010.
- **Key Derivation**: Individual entry keys derived from the Master Seed using HKDF-SHA256.

#### [FR-VAULT-020] Smart Categorization
- **Technical Detail**: Integration with **ML Kit** for on-device text classification. Fallback to a predefined regex-based mapping (e.g., `*google.com` -> "Social") when ML is unavailable.

#### [FR-VAULT-030] Smart Contextualization
- **Technical Detail**: Use `com.google.android.gms.location.GeofencingClient`. Geofences are managed locally; no location data is sent off-device.

### 4.2 Virtual Authenticator & Bluetooth HID
#### [FR-HID-010] FIDO2 Authenticator over HID
- **Technical Detail**: Implement `BluetoothHidDevice.Callback`. 
- **HID Report Descriptor**: Use a custom FIDO2 report descriptor (see [FIDO2 HID Specification](https://fidoalliance.org/specs/fido-v2.3-ps-20260226/fido-client-to-authenticator-protocol-v2.3-ps-20260226.html#usb-hid-transport)).
- **CTAP2 Layer**: Implement a state machine to handle `CTAP2_GET_INFO`, `CTAP2_MAKE_CREDENTIAL`, and `CTAP2_GET_ASSERTION` commands.
- **Supported Public Key Algorithms**: ES256, Ed25519, ML-DSA-65
- **COSE Algorithm Mapping**: Algorithm identifiers (`alg`) must follow the [IANA COSE Registry](https://www.iana.org/assignments/cose/cose.xhtml#algorithms) (e.g., -7 for ES256, -257 for RS256, -49 for ML-DSA-65).

#### [FR-HID-020-030] Connection Management & Confirmations
- **Technical Detail**: Support for multiple bonded devices via `BluetoothAdapter.getBondedDevices()`. Authentication "confirmations" triggered via Local Notifications or Heads-Up display.

### 4.3 Passkey Support
#### [FR-PASS-010] Android Credential Manager
- **Technical Detail**: Use `androidx.credentials:credentials` library. Handle `CreatePublicKeyCredentialRequest` and `GetPublicKeyCredentialRequest`.

### 4.4 Authentication & Security
#### [FR-AUTH-030] Master Seed & Recovery
- **Technical Detail**: 
  - **Mnemonic**: BIP39 (12/24 words).
  - **Entropy**: Derived from `SecureRandom`.
  - **Mnemonic-to-Seed Stretching**: BIP-39 PBKDF2(HMAC-SHA512, 2048 iterations).
  - **Child Key Derivation**: HDK per IETF `draft-dijkhuis-cfrg-hdkeys-06`. Classical (P-256) keys use HDK blinding; PQ (ML-DSA) keys use `DeriveSalt`-based branch isolation.

## 5. Non-Functional Requirements (Technical Standards)
### 5.1 Encryption Standards (NFR-SEC-010)
- **Classical**: AES-256-GCM (Hardware accelerated) for payloads and encrypted metadata values.
- **Searchable Metadata**: HMAC-SHA-256 blind indexes for deterministic exact-match lookup tokens.
- **Key Wrapping**: Platform-backed AES-GCM/AEAD with unique nonces and associated data.
- **Post-Quantum KEM**: ML-KEM-768 reserved for future remote key provisioning (via BouncyCastle 1.80+). Not used for data encryption.

### 5.2 Signature Schemes (NFR-SEC-040)
- **ECC**: P-256 (COSE -7) for wide compatibility.
- **PQC**: ML-DSA-65 (COSE -49) for quantum resistance.

## 6. User Interface & Experience (UI/UX Implementation)
- **M3 Integration**: Use `MaterialTheme` color schemes derived from `dynamicLightColorScheme` or `dynamicDarkColorScheme`.
- **Accessibility**: Implement `contentDescription` for all interactive elements and ensure touch targets meet the 48dp minimum.
- **TR-UI-040: FIDO Passkey Icon Integration**: Implement the official FIDO Passkey Icon (glyph) as a high-fidelity vector resource. All generic security/lock icons in the FIDO2 presentation module must be transitioned to this standardized glyph.
- **TR-UI-050: Relying Party Icon Loading**: Prompts must support display of the RP icon/favicon. Implement using an asynchronous image loading library (e.g., **Coil**) with an automatic fallback to `https://{rpId}/favicon.ico` if the request doesn't provide a specific icon URL.
- **TR-UI-060: Multi-Transport UI Context**: Prompt ViewModels must determine the transport (Local/Credential Manager vs. Remote/HID) to display the correct contextual header (e.g. "Create a passkey on another device" vs. "Create a passkey for this device").
- **TR-UI-070: Standardized Button Styling Implementation**: All primary and secondary action buttons in the FIDO2 and Vault modules must be implemented using Material 3 `shape.large` (16dp rounded corners) and a minimum vertical `contentPadding` of 16dp. This ensures global visual consistency and high-density touch targets compliant with NFR-PERF-030 accessibility guidelines.
- **TR-PERF-010: Performance Monitoring Reporting**: Integrate `LatencyProfiler` metrics into an automated report to verify compliance with NFR-PERF-030.
- **TR-UI-080: HDK Remote Flow (KEM)**: The core security module must implement the decapsulation (KEM) flow as defined in §4.1 of the HDK specification to support future remote key provisioning.
- **TR-DOC-010: Help System Architecture**: User guidance must be maintained as internal static Markdown files, rendered via a specialized `MarkdownText` Composable to provide a localized, on-device help experience.
- **TR-DOC-020: Technical Documentation**: Full API documentation and cryptographic architectural notes (specifically for HDK derivation) must be maintained for developer reference.

## 7. Technical Constraints & Data Schema
### 7.1 Platform Constraints
- **Minimum SDK**: API 28 (Android 9.0) required for the `BluetoothHidDevice` profile.

### 7.2 High-Level Schema
| Table | Key Technical Field | Description |
|-------|-------------------|-------------|
| `PasskeyCredential` | `coseAlgorithm` | Integer ID mapping to COSE standard (e.g., -49 for ML-DSA-65). |
| `VaultEntry` | `encryptedBlob` | AES-GCM encrypted JSON payload. |
| `RelyingParty` | `rpIdHash` | SHA-256 hash of the RP ID (used in CTAP2). |

## 8. Technical References (Specification Links)
- **CTAP 2.3**: [Official Specification](https://fidoalliance.org/specs/fido-v2.3-ps-20260226/fido-client-to-authenticator-protocol-v2.3-ps-20260226.html)
- **WebAuthn L3**: [W3C Recommendation](https://www.w3.org/TR/webauthn-3/)
- **COSE Algorithms**: [IANA Registry](https://www.iana.org/assignments/cose/cose.xhtml#algorithms)

## 9. Development & Verification Methodology
- **TDD Flow**: Use `JUnit 5` and `MockK` for unit tests; `AndroidX Test` for Bluetooth instrumentation.
- **Static Analysis**: `Detekt` for architectural rules and `Ktlint` for formatting.

---

## 10. Document Metadata
- **Owner**: Chimali Engineering Team
- **Version**: 0.1.0
- **Reference**: [BRD](brd.md)

