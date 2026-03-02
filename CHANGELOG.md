# CHANGELOG

All notable changes to the Chimali project will be documented in this file.
Detailed change summaries for major features are stored in the `docs/changelogs/` directory.

## [Unreleased]

## [v0.1.0-alpha] - 2026-03-01

### Added
- **FIDO2 Virtual Authenticator**: Complete foundational implementation for passkey management
- **Post-Quantum Cryptography**: ML-KEM/Kyber integration with Bouncy Castle PQC provider
- **Hierarchical Key Derivation**: HDK-ECDH-P256 implementation following master seed architecture
- **Encrypted Storage**: SQLCipher wrapper for secure credential database access
- **Android KeyStore**: Hardware-backed private key storage and management
- **CBOR Codec**: FIDO2 message encoding/decoding utilities
- **Memory Security**: Zeroing utilities for sensitive data handling
- **Exception Hierarchy**: Comprehensive Fido2Exception system for error handling
- **Clean Architecture**: Domain/data/presentation layer separation with repository pattern
- **Testing Framework**: JUnit5, MockK, and Compose UI Testing setup

### Security
- **Quantum-Ready**: Post-Quantum Cryptography support for future-proofing
- **Master Seed Architecture**: Hierarchical deterministic key derivation
- **Hardware Security**: Android KeyStore integration for private key protection
- **Encrypted Database**: SQLCipher for credential metadata protection
- **Memory Safety**: Secure zeroing of sensitive data arrays

### Architecture
- **Dependency Injection**: Comprehensive Hilt module configuration
- **Database Design**: SQLDelight schemas with optimized indexes and views
- **Error Handling**: Structured exception hierarchy for all failure modes
- **Protocol Support**: FIDO2/WebAuthn message formatting

### Constitutional Compliance
- ✅ Security First principle with PQC integration
- ✅ Master Seed Architecture through hierarchical key derivation
- ✅ Zero-Knowledge privacy-preserving design
- ✅ Post-Quantum cryptographic capabilities
- ✅ Memory safety and secure data handling

### Technical
- **Build System**: Gradle configuration with all required dependencies
- **Permissions**: Android manifest permissions for Bluetooth, biometric, network
- **ProGuard**: Security rules for crypto libraries and obfuscation

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
