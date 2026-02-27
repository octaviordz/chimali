# Implementation Plan: FIDO2 HID Virtual Authenticator (FR-HID-010)

**Branch**: `003-fido2-hid-authenticator` | **Date**: 2026-02-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-fido2-hid-authenticator/spec.md`

## Summary

Implement a FIDO2 Virtual Authenticator that allows the Android device to act as a hardware security key over Bluetooth HID. The core protocol logic will leverage Bitwarden's [**passkey-rs**](https://github.com/bitwarden/passkey-rs) (`passkey-authenticator` crate), providing a production-ready, transport-agnostic CTAP 2.0 stack in Rust. This stack will be bridged to Android's `BluetoothHidDevice` API via UniFFI. The authenticator will support Passkey (resident key) creation and signing, integrated with Chimali's existing vault and master seed architecture.

## Technical Context

**Language/Version**: Kotlin 1.9.20+, Rust 1.75+  
**Primary Dependencies**: `BluetoothHidDevice` API, **Bitwarden passkey-rs**, UniFFI, Hilt, Jetpack Compose, SQLCipher, SQLDelight  
**Storage**: Encrypted SQLite (SQLCipher) for credential metadata  
**Testing**: JUnit 5, MockK, Compose UI Testing, FIDO2 Conformance Tools  
**Target Platform**: Android (Min SDK 28)  
**Project Type**: Mobile (Kotlin Multiplatform ready structure)  
**Performance Goals**: HID latency < 200ms, 60 FPS UI  
**Constraints**: FIDO2/CTAP2 compliance, Zero plaintext in memory, Bluetooth HID Profile support  
**Scale/Scope**: Support hundreds of passkeys with efficient lookup

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Security First**: Uses AES-256-GCM for storage; no plaintext in memory.
- [x] **Master Seed Architecture**: Credentials derived from the root Master Seed.
- [x] **Architecture & Quality**: Follows MVI, uses Hilt, and respects feature-module boundaries.
- [x] **Performance & Reliability**: Targets < 200ms HID latency and Android Vitals.
- [x] **UX & Cross-Platform**: MD3 UI; Bluetooth HID focuses on cross-platform desktop compatibility.
- [x] **Accessibility**: High-legibility fonts for security prompts.
- [x] **Clipboard Security**: N/A for this feature (authentication flow).
- [x] **Scalability**: Designed for efficient credential retrieval.

## Project Structure

### Documentation (this feature)

```text
specs/003-fido2-hid-authenticator/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 tasks (future)
```

### Source Code (repository root)

```text
core/
├── bluetooth/           # Shared Bluetooth HID management
├── fido2/               # Rust-based CTAP2 implementation (UniFFI)
│   ├── rust/            # CTAP2 and Attestation logic
│   └── kotlin/          # Generated bindings
└── security/            # Encryption sub-keys & Master Seed logic

feature/
└── fido2/               # New module for FIDO2/HID logic
    ├── src/
    │   ├── api/         # MVI Intent and State definitions
    │   ├── internal/    # HID Service, Authenticator implementation
    │   └── ui/          # Pairing and Confirmation screens
    └── tests/
```

**Structure Decision**: Multi-module architecture with a dedicated `feature:fido2` module and shared `core` modules for Bluetooth management and FIDO2 protocol logic.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Bitwarden passkey-rs | Leveraging a production-ready CTAP2 stack. | Implementing CTAP2 from scratch (vulnerable to protocol errors). |

## Addendum: Resources

### Documentation & APIs
- **Android Bluetooth HID**: [BluetoothHidDevice Reference](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice).
- **Credential Manager**: [Google Passkeys Guide](https://developer.android.com/identity/sign-in/credential-manager).

### Testing Tools
- **Passkeys.dev**: Modern passkey testing demos.
- **Yubico Demo**: WebAuthn Technical Demo.
- **WebAuthn.io**: Primary testing ground for security key flows.
