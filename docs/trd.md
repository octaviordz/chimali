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
- **Security Leadership**: Integration of Bouncy Castle 1.80+ for the current local credential profile and ML-DSA-65 signing. ML-KEM or remote key provisioning is not part of the current credential profile; any issuer workflow remains a separate research item.
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

#### [TR-AUTH-031] Current Local Credential Derivation Profile
- Keep the exact byte and algorithm contract in `specs/055-migrate-hdk-draft/contracts/local-key-derivation.md` synchronized with code and independently checked fixtures.
- Bind complete decoded credential IDs; carry algorithm/profile metadata through registration, event replay, snapshots, SQL projections and assertion reconstruction.
- Preserve independent Ed25519 and ML-DSA seed paths. Reject unsupported combinations without fallback.
- Scope compatibility to clean installations as authorized for feature 055; do not imply old-key conversion or issuer interoperability.
- **Technical Detail**: 
  - **Mnemonic**: BIP39 (12/24 words).
  - **Entropy**: Derived from `SecureRandom`.
  - **Mnemonic-to-Seed Stretching**: BIP-39 PBKDF2(HMAC-SHA512, 2048 iterations).
  - **Current derivation (TR-AUTH-031)**: Use the 64-byte BIP39 seed as HKDF-SHA256 input to the named local profile and retain its 32-byte PRK as the derivation root. The P-256 branch binds the complete decoded credential ID, derives the deterministic P-256 parent with RFC 9180 §7.1.3, and adapts the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00` at local index zero, substituting a local RFC 5869 credential secret for the draft's per-issuance X25519 shared secret. This is custom local composition, not the draft's issuance flow or an interoperability claim. Ed25519 and ML-DSA use separate 32-byte HKDF seed labels. Persist the explicit supported profile with the credential's algorithm; reject unknown pairs. Store no derived private key or seed. Parent signing is software-based and requires the existing authorization policy; encrypted recovery storage does not make the parent non-exportable. Recovery requires the mnemonic and credential metadata.

## 5. Non-Functional Requirements (Technical Standards)
### 5.1 Encryption Standards (NFR-SEC-010)
- **Classical**: AES-256-GCM (Hardware accelerated) for payloads and encrypted metadata values.
- **Searchable Metadata**: HMAC-SHA-256 blind indexes for deterministic exact-match lookup tokens.
- **Key Wrapping**: Platform-backed AES-GCM/AEAD with unique nonces and associated data.
- **Post-Quantum KEM**: No KEM-based remote provisioning capability is defined by the current local credential profile. Any future issuer or provisioning design remains subject to the separate [OpenID4VCI HDK issuance investigation](backlog/openid4vci-hdk-issuance-investigation.md); this document does not claim ML-KEM support or readiness.

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
- **TR-PERF-010: Performance Monitoring Reporting**: Provide a local automated report for the RP-to-verified-response round trip (<1,000ms after subtracting measured user-verification wait) and all internal FIDO operation processing (<200ms each after subtracting measured user-verification wait and excluding Bluetooth response handoff), with accepted-report status and no credential secrets or identifying data.
- **TR-UI-080 (superseded)**: The previous HDK remote-flow/KEM requirement is retired for the current local profile. No remote provisioning or issuer behavior is specified here.
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
- **TDD Flow**: Prefer `commonTest` with `kotlin.test` for platform-neutral Kotlin behavior and Fake test doubles for repositories, services, and platform boundaries. Use `JUnit 5`, `MockK`, and `AndroidX Test` only when platform APIs, interaction verification, or instrumentation are the behavior under test.
- **Static Analysis**: `Detekt` for architectural rules and `Ktlint` for formatting.

---

## 10. Document Metadata
- **Owner**: Chimali Engineering Team
- **Version**: 0.1.0
- **Reference**: [BRD](brd.md)

