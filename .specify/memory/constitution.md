<!--
SYNC IMPACT REPORT
- Version change: 0.1.0 → 0.2.0
- List of modified principles: None
- Added sections: VI. Inclusion & Universal Accessibility
- Removed sections: None
- Templates requiring updates: ✅ plan-template.md, ✅ spec-template.md (Logic aligned, no textual changes needed)
- Follow-up TODOs: None
-->

# Chimali Constitution

## Core Principles

### I. Security First (Zero-Trust Local-First)
All sensitive data must be encrypted with AES-256-GCM. If the device supports Quantum-Resistant algorithms (PQC, e.g., ML-KEM/Kyber), the application must utilize these as the primary encryption method. Mandatory prohibition of plain-text storage of credentials in memory. Sensitive data must only exist in decrypted form within volatile memory using mutable structures (e.g., byte/char arrays) that are explicitly zeroed out immediately after use.

### II. Master Seed Architecture
The root of trust is established via a **Master Seed (Master Key)** architecture. Credential keys are derived using **Hierarchical Deterministic Key Derivation** following **IETF draft-dijkhuis-cfrg-hdkeys-06** (HDK-ECDH-P256) for privacy-preserving elliptic curve key management. BIP39 is used for mnemonic seed generation.

### III. Uncompromising Architecture & Quality
The application strictly follows Clean Architecture with Unidirectional Data Flow (UDF) using the **MVI (Model-View-Intent)** pattern. Dependency injection is standardized using **Hilt**. The codebase must be highly modularized (Feature-by-module). Static analysis via **Detekt** and **Ktlint** is mandatory to enforce coding standards.

### IV. Performance & Reliability Excellence
The application must adhere to strict Android Vitals targets:
- Startup: Cold Start < 2s, Warm Start < 1s, Hot Start < 500ms.
- Smoothness: Maintain 60 FPS during interactions.
- Latency: Bluetooth HID Virtual Authenticator actions must be < 200ms end-to-end.
- Resources: Zero memory leaks and minimal battery impact (< 0.1% excessive wake locks).

### V. Cross-Platform Utility & Modern UX
The app must seamlessly emulate a FIDO2 Virtual Authenticator via `BluetoothHidDevice` to support cross-platform authentication (Windows, macOS, Linux). The UI must follow Material Design 3 (M3) with dynamic coloring, ensuring a premium user experience.

### VI. Inclusion & Universal Accessibility
Accessibility is a core functional and security requirement. The application MUST support screen readers (TalkBack), high-contrast modes, and dynamic text scaling. Legibility is treated as a security feature to prevent user error during credential management: credentials MUST be displayed using high-legibility fonts (e.g., [Atkinson Hyperlegible](https://brailleinstitute.org/atkinson-hyperlegible-font)) with clear character differentiation.

## Technical Constraints

- **Platform**: Android Native Application (Minimum SDK 28).
- **Language**: Primary language is Kotlin (100% for UI/Android layers). Languages that produce native code (e.g., Rust) are allowed under special cases (e.g., core cryptography, shared low-level logic).
- **UI Framework**: Jetpack Compose Multiplatform.
- **Hardware Integration**: Mandatory support for Bluetooth HID Device Profile for virtual authenticator features.
- **Privacy Focus**: On-device AI only (e.g., ML Kit, Gemini Nano) for credential categorization; no cloud processing of plain-text data.

## Development Workflow & Testing

- **Testing Methodology**: Test-Driven Development (TDD) where feasible. 100% unit test coverage for core business logic (encryption, validation) is non-negotiable.
- **Integration**: Comprehensive integration tests must verify the interaction between Bluetooth HID emulation, Credential Manager, and Encryption layers.

## Governance

- **Constitution Supremacy**: The principles defined in this Constitution supersede all other development practices.
- **Quality Gates**: All Pull Requests must verify compliance with security guidelines (especially memory zeroing) and pass all static analysis checks (Detekt/Ktlint).
- **Performance Budget**: Any feature that degrades startup time or rendering smoothness beyond the defined limits will be rejected.

**Version**: 0.2.0 | **Ratified**: 2026-02-19 | **Last Amended**: 2026-02-24
