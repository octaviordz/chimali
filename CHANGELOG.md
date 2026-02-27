# CHANGELOG

All notable changes to the Chimali project will be documented in this file.
Detailed change summaries for major features are stored in the `docs/changelogs/` directory.

## [Unreleased] - 2026-02-26

### Added
- **Bluetooth Device Discovery & Management**: Full UI and backend implementation for scanning and pairing new Bluetooth devices.
    - Android 12+ Runtime Permission guards (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`).
    - Material 3 Pull-to-Refresh integration for active device scanning.
    - Split UI for Paired vs Available devices.
    - Full details: [2026-02-26-device-discovery.md](docs/changelogs/2026-02-26-device-discovery.md)
- **FIDO2 HID Virtual Authenticator (FR-HID-010)**: Android device now acts as a hardware security key over Bluetooth HID.
    - Rust-based **CTAP2** implementation via `passkey-authenticator`.
    - **UniFFI** bindings for high-performance Rust-Kotlin communication.
    - Foreground service for reliable Bluetooth HID report handling.
    - Full details: [2026-02-26-fido2-hid-implementation.md](docs/changelogs/2026-02-26-fido2-hid-implementation.md)

### Fixed
- **Build System & DSL Modernization**: Resolved compilation errors related to AGP 9.0+, Kotlin 2.0+, and SQLDelight 2.x strictness. Restructured source sets for Android module compliance.
    - Full details: [2026-02-26-build-fixes.md](docs/changelogs/2026-02-26-build-fixes.md)

## [Unreleased] - 2026-02-25

### Added
- **Documentation Restructuring**: Implemented a stable mnemonic requirement system (`FR-VAULT-010`, etc.) and updated the BRD structure to include a dedicated Accessibility section.
    - Full details: [2026-02-25-doc-restructuring.md](docs/changelogs/2026-02-25-doc-restructuring.md)

### Changed
- **Constitution (v0.3.0)**: Formally adopted IEEE 830 and modern Agile documentation standards as project principles.
- **Requirement IDs**: Migrated all functional and non-functional requirements to a category-based mnemonic path format for better long-term maintainability.

---

## [Unreleased] - 2026-02-23

### Added
- **Secure Credentials Vault (FR-VAULT-010)**: Initial implementation of local-first encrypted storage for passwords, cards, and notes.
    - Hybrid **SQLCipher** + **Loro.dev CRDT** storage architecture.
    - Hardware-backed key management (Android Keystore).
    - Support for dynamic **Custom Fields** and **Labels**.
    - Full details: [2026-02-23-vault-implementation.md](docs/changelogs/2026-02-23-vault-implementation.md)
- **HDKeys Implementation**: New hierarchical deterministic key system based on IETF draft-dijkhuis-cfrg-hdkeys-06 (HDK-ECDH-P256).
    - Multiplicative key blinding for enhanced privacy.
    - RFC 9380 (Hash-to-Curve/Field) support.
    - RFC 9180 (DHKEM) for remote derivation.
    - Full details: [2026-02-18-hdkeys-implementation.md](docs/changelogs/2026-02-18-hdkeys-implementation.md)

### Changed
- **Security Provider**: Switched to **Bouncy Castle** for P-256 elliptic curve arithmetic.
- **BRD Update**: Updated master key management requirements to align with the new HDKeys specification.

### Removed
- **BIP-32**: Legacy BIP-32/BIP-44 implementation removed in favor of HDKeys.

---
*Last Updated: 2026-02-23*
