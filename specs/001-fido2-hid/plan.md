# Implementation Plan: FIDO2 Virtual Authenticator

**Branch**: `001-fido2-hid` | **Date**: 2025-02-25 | **Spec**: [FIDO2 Virtual Authenticator](spec.md)
**Input**: Feature specification from `/specs/001-fido2-hid/spec.md`

## Summary

Implement a FIDO2 Virtual Authenticator that transforms an Android device into a hardware security key via Bluetooth HID. The solution will leverage Android's `BluetoothHidDevice` API to emulate a FIDO2 authenticator, integrate with Android Credential Manager for passkey storage, and provide cross-platform authentication support for Windows, macOS, and Linux desktop systems. The implementation must follow the Master Seed architecture with HDK-ECDH-P256 key derivation and maintain strict security requirements including AES-256-GCM encryption and zero-trust local-first principles.

## Technical Context

**Language/Version**: Kotlin (Android) with potential Rust components for core cryptography  
**Primary Dependencies**: Android BluetoothHidDevice API, Android Credential Manager, Jetpack Compose, Hilt DI, Detekt/Ktlint  
**Storage**: Android KeyStore (Strongbox encouraged), Android Credential Manager for passkeys, local encrypted storage for metadata  
**Testing**: JUnit5, Espresso for UI, MockK for mocking, FIDO2 conformance testing tools  
**Target Platform**: Android (Minimum SDK 28/API 28)  
**Project Type**: mobile-app (Android Native Application)  
**Performance Goals**: End-to-end authentication <200ms, 60 FPS UI, <2s cold start, <1s warm start, <500ms hot start  
**Constraints**: <200ms Bluetooth HID latency, <100MB memory usage, offline-capable, zero memory leaks, <0.1% excessive wake locks  
**Scale/Scope**: Support up to 5 paired desktop devices simultaneously, handle 95% success rate on first authentication attempt

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Constitution Compliance Assessment ✅

**I. Security First (Zero-Trust Local-First)**: ✅ Compliant
- FR-014/FR-015: Configurable logging with user control
- All credential operations use Android KeyStore and Credential Manager
- Memory zeroing requirements enforced via static analysis

**II. Master Seed Architecture**: ✅ Compliant  
- HDK-ECDH-P256 key derivation for credential keys
- BIP39 mnemonic seed generation integration
- Hierarchical deterministic key management

**III. Uncompromising Architecture & Quality**: ✅ Compliant
- Clean Architecture with MVI pattern
- Hilt dependency injection
- Feature-by-module modularization
- Detekt/Ktlint static analysis enforcement

**IV. Performance & Reliability Excellence**: ✅ Compliant
- SC-002: <200ms end-to-end authentication
- SC-004: 30+ minute stable connections
- Android Vitals targets met in success criteria

**V. Cross-Platform Utility & Modern UX**: ✅ Compliant
- FR-002: Windows/macOS/Linux support via Bluetooth HID
- Material Design 3 with dynamic coloring
- Cross-device QR code authentication

**VI. Inclusion & Universal Accessibility**: ✅ Compliant
- Screen reader support (TalkBack)
- High-contrast modes
- High-legibility fonts for credential display

**VII. Documentation Standards**: ✅ Compliant
- IEEE 830 SRS principles followed
- Stable mnemonic requirement IDs
- Living documentation approach

## Project Structure

### Documentation (this feature)

```text
specs/001-fido2-hid/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── fido2-protocol.md
│   ├── bluetooth-hid.md
│   └── credential-manager.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
feature/authenticator/
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/chimali/authenticator/
│       │       ├── presentation/           # UI layer (Compose)
│       │       │   ├── components/
│       │       │   ├── screens/
│       │       │   └── viewmodel/
│       │       ├── domain/               # Business logic
│       │       │   ├── model/
│       │       │   ├── repository/
│       │       │   └── usecase/
│       │       ├── data/                 # Data layer
│       │       │   ├── local/
│       │       │   ├── remote/
│       │       │   └── repository/
│       │       └── bluetooth/            # Bluetooth HID implementation
│       │           ├── hid/
│       │           ├── fido2/
│       │           └── connection/
├── build.gradle.kts
└── tests/
    ├── unit/
    ├── integration/
    └── androidTest/

core/bluetooth/
├── src/
│   └── main/kotlin/com/chimali/bluetooth/
│       ├── hid/
│       ├── connection/
│       └── protocol/
└── build.gradle.kts

core/fido2/
├── src/
│   └── main/kotlin/com/chimali/fido2/
│       ├── protocol/
│       ├── crypto/
│       └── operations/
└── build.gradle.kts
```

**Structure Decision**: Multi-module Android architecture following Clean Architecture principles. The `feature/authenticator` module contains the UI and business logic for the virtual authenticator feature, while `core/bluetooth` and `core/fido2` provide reusable core functionality. This aligns with the feature-by-module modularization requirement and supports independent testing and development.

## Complexity Tracking

> No constitution violations detected - all requirements align with established principles

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | All complexity is justified by constitutional requirements |
