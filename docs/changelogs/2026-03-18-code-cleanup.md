# Changelog: Code Cleanup

This entry documents the final removal of legacy/unused code from the FIDO2 implementation.

## [Unreleased] - 2026-03-18

### Removed
- **Legacy PQC Utilities**: Removed the unused `getRecommendedAlgorithm` function from `PostQuantumCrypto.kt` and its associated tests to keep the surface area of the cryptographic layer minimal and focused on NIST-standardized operations.

### Changed
- **Code Hardening**: Finalized the PQC and CBOR fixes initiated during T159, ensuring the authenticator is spec-compliant and environment-agnostic.
