# Implementation Plan: FIDO2 Virtual Authenticator via BluetoothHidDevice

**Branch**: `004-fido2-hid` | **Date**: 2026-04-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-fido2-hid/spec.md`

## Summary

Implement the FIDO2 Virtual Authenticator via `BluetoothHidDevice`, allowing the Android device to act as a cross-platform passkey authenticator. This includes the complete CTAP2 ceremonies (Registration, Authentication, and Credential Management), secured by on-device cryptography using **Signum**. To comply with the strict "no cloud processing" and privacy mandates of the project's Constitution, logging and crash reporting will be entirely Local-First (on-device). Sensitive data (e.g., cryptographic material, biometric events) will be explicitly excluded from all logs. The system will provide comprehensive, user-friendly error messages during Bluetooth HID disruptions, FIDO2 protocol failures, or validation errors. The entire project is being migrated from Hilt to **Koin** (with Koin Compiler) for uniform Kotlin Multiplatform dependency injection.

## Technical Context

**Language/Version**: Kotlin 2.1+ (Kotlin Multiplatform)
**Primary Dependencies**: 
- **JetBrains Compose Multiplatform (CMP)**: Shared UI across Android and iOS.
- **Koin (with Compiler Plugin)**: Dependency injection for the entire KMP project.
- **Signum**: KMP-native cryptography and PKI library (replaces BouncyCastle/Ktor-crypto).
- **Kermit**: KMP structured Local-First logging.
- **SQLDelight**: KMP database layer.
**Storage**: Local App Data directory for crash logs (custom rotating file sink via Kermit LogWriter).
**Testing**: Kotest/JUnit 5, MockK (testing shared KMP logic).
**Target Platforms**: 
- **Android 9.0+ (API 28+)**: Bluetooth HID and KeyStore support.
- **iOS 15.0+**: Placeholder directory structure for future native integration.
**Project Type**: Kotlin Multiplatform Mobile (KMM) Application
**Performance Goals**: Local-First logging overhead < 5ms per event.
**Constraints**: Absolute privacy (no remote crash reporting tools). Sensitive parameters must be masked.
**Scale/Scope**: Limit log files to 5MB rotation. Max 1000 stored credentials (FR-HID-022).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Security First (Zero-Trust Local-First)
- ✅ **Signum** provides secure, KMP-native cryptographic primitives without relying on platform-specific weak defaults.
- ✅ Local-First crash reporting ensures no sensitive data leaves the device.
- ✅ Kermit LogWriters will mask or exclude sensitive values.

### III. Uncompromising Architecture & Quality 
- ✅ **Koin Compiler** ensures compile-time safety and validation of the dependency graph.
- ✅ Clean Architecture with KMP source set boundaries (`commonMain`, `androidMain`, `iosMain`).

### IV. Performance & Reliability Excellence 
- ✅ Log rotation (5MB cap) ensures no disk/memory leaks.

### VI. Inclusion & Universal Accessibility
- ✅ User-friendly error messages mapped from technical CTAP2 codes.

## Project Structure

### Documentation (this feature)

```text
specs/004-fido2-hid/
├── plan.md              # This file
├── research.md          
├── data-model.md        
├── contracts/           
└── tasks.md             
```

### Source Code (repository root)

```text
feature/fido2/
├── build.gradle.kts           # KMP + CMP + Koin Compiler configuration
├── src/commonMain/kotlin/
│   ├── domain/                # Shared models, CTAP2 handlers, use cases
│   ├── presentation/          # CMP UI & ViewModels (Koin-injected)
│   ├── data/                  # SQLDelight shared KMP schema & Signum repositories
│   └── di/                    # Shared Koin modules (@Module, @ComponentScan)
├── src/androidMain/kotlin/
│   ├── platform/              # BluetoothHidDevice actual bindings
│   └── di/                    # Android-specific Koin module providers
├── src/iosMain/kotlin/
│   ├── platform/              # Placeholder for future native iOS bindings
│   └── di/                    # Placeholder for future iOS Koin providers
├── src/commonTest/kotlin/     # Shared behavior tests
```

**Structure Decision**: Refactoring `feature:fido2` and core modules into a full KMP structure. **Koin** replaces Hilt project-wide to ensure a unified injection model across Android and iOS. **Signum** handles all cryptographic operations (HDK, ECDSA) in `commonMain` while bridging to native secure hardware (KeyStore/Secure Enclave) in platform source sets.

