# Implementation Plan: FIDO2 Virtual Authenticator via BluetoothHidDevice

**Branch**: `004-fido2-hid` | **Date**: 2026-03-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-fido2-hid/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Implement a FIDO2 Virtual Authenticator using BluetoothHidDevice to enable passwordless authentication across platforms. The solution will support both FIDO2.0 and FIDO2.1 protocols, with biometric user verification and secure credential storage using Android KeyStore. Based on reference implementations from WIOsense rauth-android library and WioKey Android app, adapted to Kotlin with Clean Architecture principles.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin 1.9+ (Android Native)  
**Primary Dependencies**: AndroidX BiometricPrompt, Android KeyStore, BluetoothHidDevice, SQLCipher, SQLDelight, Hilt, Jetpack Compose, Bouncy Castle (PQC), ML-KEM/Kyber library  
**Storage**: SQLCipher + SQLDelight for encrypted credential metadata, Android KeyStore for private keys  
**Testing**: JUnit 5, MockK, Compose UI Testing  
**Target Platform**: Android 9.0+ (API 28+) with Bluetooth HID support  
**Project Type**: Mobile Application with FIDO2 Virtual Authenticator functionality  
**Performance Goals**: Bluetooth HID operations <200ms, Registration <30s, Authentication <5s, 95%+ success rate  
**Constraints**: <200ms p95 for HID operations, <100MB memory, offline-capable, StrongBox support when available  
**Scale/Scope**: Support 50 credentials per user, 10+ minute continuous Bluetooth sessions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Security First (Zero-Trust Local-First) ✅
- ✅ AES-256-GCM encryption via SQLCipher
- ✅ Android KeyStore with StrongBox support
- ✅ Memory zeroing for sensitive data
- ✅ No plain-text credential storage

### Master Seed Architecture ✅
- ✅ HDK-ECDH-P256 key derivation (IETF draft-dijkhuis-cfrg-hdkeys-06)
- ✅ BIP39 mnemonic seed generation

### Uncompromising Architecture & Quality ✅
- ✅ Clean Architecture with MVI pattern
- ✅ Hilt dependency injection
- ✅ Feature-by-module modularization
- ✅ Detekt and Ktlint static analysis

### Performance & Reliability Excellence ✅
- ✅ Cold Start <2s, Warm Start <1s, Hot Start <500ms targets
- ✅ 60 FPS maintenance during interactions
- ✅ Bluetooth HID operations <200ms
- ✅ Zero memory leaks requirement
- ✅ 10,000+ vault item scalability

### Cross-Platform Utility & Modern UX ✅
- ✅ BluetoothHidDevice for cross-platform authentication
- ✅ Material Design 3 with dynamic coloring

### Inclusion & Universal Accessibility ✅
- ✅ TalkBack screen reader support
- ✅ High-contrast modes
- ✅ Dynamic text scaling
- ✅ Atkinson Hyperlegible font for security

### Documentation Standards ✅
- ✅ IEEE 830 SRS principles
- ✅ Stable mnemonic path format (FR-HID-NNN)
- ✅ Living documentation practices

### Technical Constraints ✅
- ✅ Android Native (Minimum SDK 28)
- ✅ Kotlin primary with Rust for core crypto
- ✅ KMP-ready module structure
- ✅ SQLCipher + SQLDelight storage
- ✅ Jetpack Compose UI
- ✅ Bluetooth HID Device Profile
- ✅ On-device AI only

### Development Workflow & Testing ✅
- ✅ TDD approach
- ✅ 100% unit test coverage for core logic
- ✅ Comprehensive integration tests
- ✅ Compose UI Testing

## Project Structure

### Documentation (this feature)

```text
specs/004-fido2-hid/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# FIDO2 Virtual Authenticator Module Structure
feature/fido2/
├── src/main/kotlin/
│   ├── domain/
│   │   ├── model/          # Domain models (PasskeyCredential, RelyingParty, etc.)
│   │   ├── repository/      # Repository interfaces
│   │   └── usecase/        # Use cases (RegisterCredential, Authenticate, etc.)
│   ├── data/
│   │   ├── local/          # SQLDelight database + DAOs
│   │   ├── remote/         # Bluetooth HID transport
│   │   └── repository/     # Repository implementations
│   ├── presentation/
│   │   ├── ui/            # Jetpack Compose screens
│   │   ├── viewmodel/      # MVI ViewModels
│   │   └── navigation/     # Navigation components
│   └── di/                # Hilt modules
├── src/test/kotlin/        # Unit tests
└── src/androidTest/kotlin/  # Integration tests

# Core Bluetooth HID Module
core/bluetooth/
├── src/main/kotlin/
│   ├── hid/              # Bluetooth HID device abstraction
│   ├── ctap/             # CTAP2 protocol implementation
│   └── transport/         # Transport layer management
└── src/test/kotlin/

# Core Crypto Module
core/crypto/
├── src/main/kotlin/
│   ├── keystore/         # Android KeyStore wrapper
│   ├── hdkey/            # Hierarchical deterministic keys
│   └── fido2/            # FIDO2 cryptographic operations
└── src/test/kotlin/

# Core Data Module
core/data/
├── src/main/kotlin/
│   ├── database/         # SQLDelight setup
│   ├── encrypted/         # SQLCipher integration
│   └── migration/        # Database migrations
└── src/test/kotlin/
```

**Structure Decision**: Feature-by-module architecture following Clean Architecture principles. The FIDO2 authenticator is implemented as a feature module with dependencies on core modules for Bluetooth, crypto, and data functionality. This aligns with Chimali's KMP-ready structure and enables independent testing and deployment.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
