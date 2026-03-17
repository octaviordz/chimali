# Business Requirements Document (BRD) - Chimali

**Project Name**: Chimali
**Version**: 0.1.0
**Project Type**: Android Native Application
**Technology Stack**: Kotlin + Jetpack Compose Multiplatform

> [!NOTE]
> This document follows the **IEEE 830** standard for software requirements specifications and adheres to **modern Agile documentation** principles, maintaining stable mnemonic requirement identifiers for enhanced traceability.

---

## 1. Executive Summary
Chimali is a modern, robust Android Wallet Secure Cryptographic Application (WSCA) built on proven security standards and best practices. It is designed to bridge the gap between traditional credential management and modern passwordless authentication by leveraging Bluetooth HID capabilities to transform the Android device into a secure virtual authenticator while providing full support for Passkeys.

## 2. Project Overview
### 2.1 Mission Statement
To provide a secure, intuitive, and highly integrated authentication experience that allows users to manage their digital identity across multiple platforms using their Android device as a hardware security key.

### 2.2 Target Audience
- Privacy-conscious individuals.
- Developers and power users needing seamless authentication across OS environments.
- Organizations transitioning to passkey-based security.

### 2.3 Success Criteria
- Successful emulation of a Bluetooth HID device on Windows, macOS, and Linux.
- Seamless creation and usage of Passkeys via Android Credential Manager.
- High user satisfaction with the Jetpack Compose-based modern UI.

## 3. Business Goals & Objectives
1.  **Security Leadership**: Implement industry-standard (AES-256) and Post-Quantum Cryptography (PQC) encryption, leveraging Android's Hardware Security Module (HSM).
2.  **Cross-Platform Utility**: Allow users to authenticate on desktop systems without requiring specialized hardware keys.
3.  **Modern UX**: Deliver a premium user experience through fluid animations, intuitive interactions, and a Material Design 3 (M3) design system.
4.  **Future-Proofing**: Full support for FIDO2 and WebAuthn via Passkeys.

## 4. Functional Requirements
### 4.1 Core Credential Management
- **FR-VAULT-010**: Securely store and organize passwords, notes, and credit cards.
- **FR-FLOW-010**: Auto-fill credentials in Android apps and mobile browsers.
- **FR-VAULT-020**: Secure search and categorization of accounts.
    - Implement on-device AI for automated categorization (e.g., via ML Kit or Gemini Nano) for privacy-first organization.
    - Fallback to manual/rule-based categorization if on-device AI is not supported by the hardware.
- **FR-VAULT-030**: Smart Contextualization via Geolocation.
    - Surface relevant accounts based on the user's current location (e.g., prioritize "Work" accounts when at the office).
    - All location processing must be performed locally (on-device geofencing) to maintain user privacy.

### 4.2 Virtual Authenticator & Bluetooth HID
- **FR-HID-010**: Act as a FIDO2 Virtual Authenticator via `BluetoothHidDevice`.
- **FR-HID-020**: Support pairing and connection management for multiple desktop devices.
- **FR-HID-030**: Trigger authentication "confirmations" on the phone to release credentials to the host.

### 4.3 Passkey Support
- **FR-PASS-010**: Create, store, and manage Passkeys using the Android Credential Manager.
- **FR-PASS-020**: Support cross-device sign-in (scanning a QR code from another device to use a Passkey on Android).

### 4.4 Authentication & Security
- **FR-AUTH-010**: Mandatory Authentication using Biometrics (Fingerprint/Face) or Device PIN, defaulting to the user's device settings.
- **FR-AUTH-020**: Automatic lock on app backgrounding or device inactivity.
- **FR-AUTH-030**: Local-first storage with a secure backup mechanism utilizing secret sharing (e.g., Shamir's Secret Sharing). The backup will be based on a **Master Seed**, ensuring that all credentials can be restored from a single recovery point. See [Credential ID Recovery Analysis](research/credential_id_recovery_analysis.md) for details on recovery consistency and deterministic metadata requirements.

### 4.5 Human Interface & Accessibility
- **FR-UI-010: Password Legibility and Confusion Prevention**: Ensure passwords are displayed using high-legibility fonts (e.g., monospaced) that clearly distinguish ambiguous characters (e.g., 'O' vs '0', 'I' vs 'l' vs '1'). Implement colorblind-friendly indicators or semantic highlighting to differentiate between character types (uppercase, lowercase, digits, symbols) to reduce visual confusion.

## 5. Non-Functional Requirements
### 5.1 Security
- **NFR-SEC-010**: All sensitive data must be encrypted. The application MUST follow a **Multi-Mode Symmetric Encryption Strategy**: **AES-256-GCM** for general payloads (files, credential blobs) to enable hardware offloading, and **AES-256-SIV** for searchable encrypted metadata and key wrapping to provide nonce-misuse resistance. If the device supports Quantum-Resistant (Post-Quantum Cryptography) algorithms (e.g., ML-KEM/Kyber), the application must utilize these as the primary encryption method.
- **NFR-SEC-020**: Sensitive keys must be stored in the Android KeyStore (strongbox encouraged).
- **NFR-SEC-030**: Mandatory prohibition of plain-text storage of credentials in memory. Sensitive data must only exist in decrypted form within volatile memory using mutable structures (e.g., byte/char arrays) that are explicitly zeroed out immediately after use.
- **NFR-SEC-040: Master Key Management**: Implementation of a **Master Seed (Master Key)** architecture as the root of trust. Credential keys are derived using **Hierarchical Deterministic Key Derivation** following **IETF draft-dijkhuis-cfrg-hdkeys-06** (HDK-ECDH-P256) for privacy-preserving elliptic curve key management. BIP39 is used for mnemonic seed generation. The architecture accommodates **Hybrid Hierarchical Deterministic Derivation (HHD)** to allow deterministic derivation of both classical (e.g., ECDSA/Ed25519) and Post-Quantum (e.g., Falcon-512) signature schemes from the single BIP39 root seed using standard paths (BIP-44 / SLIP-10 adaptations).

### 5.2 Performance & Reliability
- **NFR-PERF-010: Startup Performance** (Android Vitals Targets):
    - **Cold Start**: < 2 seconds (Time to initial display).
    - **Warm Start**: < 1 second.
    - **Hot Start**: < 500 milliseconds.
- **NFR-PERF-020: Rendering Smoothness**:
    - Maintain a consistent **60 FPS** (16.6ms per frame) during UI interactions.
    - Zero "Frozen Frames" (render time > 700ms) and < 1% "Slow Frames" (render time > 16ms).
- **NFR-PERF-030: Bluetooth HID Latency**:
    - Target end-to-end latency for virtual authenticator actions (from tap on phone to execution on host) should be **< 200ms** on supported hardware to ensure a responsive user experience.
- **NFR-PERF-040: Resource Optimization**:
    - Zero memory leaks detected via Profiler or LeakCanary.
    - "Excessive Wake Locks" must remain below 0.1% to minimize battery impact.

### 5.3 Maintainability
- **NFR-ARCH-010: Architecture**: Implementation of Clean Architecture with Unidirectional Data Flow (UDF) using the **MVI (Model-View-Intent)** pattern for predictable state management.
- **NFR-ARCH-020: Dependency Injection**: Use **Hilt** for standardized, compile-time safe dependency management.
- **NFR-ARCH-030: Modularization**: Adoption of a multi-module project structure (Feature-by-module) to ensure separation of concerns and optimized build performance.
- **NFR-ARCH-040: Static Analysis**: Use **Detekt** and **Ktlint** (open-source) to enforce coding standards and detect architectural regressions automatically.

## 6. User Interface & Experience (UI/UX)
- **Design System**: Material Design 3 (M3).
- **Theming**: Dynamic Colors support.
- **Interactions**: Smooth transitions between screens using Compose Navigation.
- **Accessibility**: Support for screen readers and high-contrast modes.

## 7. Technical Constraints
- **Platform**: Android Only (initial release).
- **Minimum SDK**: Android 9.0 (API 28) for `BluetoothHidDevice` support.
- **Language**: Primary language is Kotlin (100% for UI/Android layers). Languages that produce native code (e.g., Rust) are allowed under special cases (e.g., core cryptography, shared low-level logic).
- **UI Framework**: Jetpack Compose Multiplatform.
- **Hardware Requirement**: Device must support Bluetooth HID Device Profile.
- **AI Constraints**: Optional on-device AI features require compatible hardware (e.g., AICore/Gemini Nano support) or specialized ML Kit models.

## 8. Inspiration & References
- **wiokey-android**: Implementation of virtual HID authenticator logic (to be ported from Java to Kotlin).
- **Allthenticate**: [Play Store Link](https://play.google.com/store/apps/details?id=net.allthenticate.sda) (Inspiration for desktop/mobile integration).
- **Android Bluetooth HID API**: [BluetoothHidDevice Reference](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice).
- **Credential Manager**: Google's modern API for Passkeys and Passwords.

### 8.1 FIDO2 / WebAuthn Testing Tools
- **WebAuthn.io**: [Testing Site](https://webauthn.io) (Duo Labs playground).
- **Yubico Demo**: [WebAuthn Test](https://demo.yubico.com/webauthn-technical) (Hardware-focused testing).
- **Passkeys.dev**: [Resource Hub](https://passkeys.dev/demo/) (Modern passkey testing demos).
- **FIDO Conformance Tools**: [FIDO Alliance](https://fidoalliance.org/certification/conformance-tools/) (Official validation).

## 9. Development & Testing Methodology
- **Test-Driven Development (TDD)**: Implementation should follow TDD principles where feasible to ensure high code quality and reliability.
- **Integration Testing**: Comprehensive integration tests must be implemented to verify the interaction between components (e.g., Bluetooth HID emulation, Credential Manager, and Encryption layers).
- **Unit Testing**: 100% coverage of core business logic (encryption, validation).
