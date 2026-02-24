# CHANGELOG

All notable changes to the Chimali project will be documented in this file.
Detailed change summaries for major features are stored in the `docs/changelogs/` directory.

## [Unreleased]

### Added
- **Secure Credentials Vault (FR1)**: Initial implementation of local-first encrypted storage for passwords, cards, and notes.
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
