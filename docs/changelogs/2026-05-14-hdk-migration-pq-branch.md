# HDK Migration & BIP-32 Removal (PQ Branch)

This changelog summarizes the migration of the Post-Quantum (ML-DSA) key branch to the IETF `draft-dijkhuis-cfrg-hdkeys-06` HDK standard and the final removal of legacy BIP-32/85 logic from the codebase.

## [Unreleased] - 2026-05-14

### Changed
- **Post-Quantum Key Derivation (US1)**: Successfully migrated the ML-DSA key branch from a legacy BIP-32/85 hardened path to the modern HDK standard.
    - Replaced the `ckdHard()` / BIP-85 implementation in `WalletMasterSeedProvider` with **HDK DeriveSalt** (§2.4) using the context `"PQ_ML-DSA_Branch"`.
    - Implemented `HMAC-SHA512` expansion (key: `"chimali_pq_seed_v1"`) to produce the 64-byte ML-DSA seed.
    - Enforced cryptographic isolation between the classical ECDSA-P256 tree and the post-quantum branch.
- **BIP-32 Cleanup (US3)**: Purged all remaining BIP-32/85 logic and constants to align with the project's long-term commitment to the HDK standard.
    - Deleted the `ckdHard()` method and BIP-32 path constants from `WalletMasterSeedProvider`.
    - Updated KDoc references and derivation tables in `PostQuantumCrypto.kt` and `Fido2CryptoService.kt` to remove BIP-32 mentions.
- **HdkManager Interface Enhancement**: Promoted `deriveSalt` to a public interface method in `HdkManager` to support domain-separated derivation project-wide.

### Added
- **HDK-Based Verification**:
    - Added Known Answer Tests (KAT) for the new PQ derivation logic in `WalletMasterSeedProviderTest.kt`.
    - Added an explicit domain separation test (T006) verifying that different HDK context strings yield unique expansion results.
    - Implemented a "Clean Break" test ensuring the new HDK-derived PQ seed is incompatible with previous BIP-85 outputs.

### Governance & Alignment
- **Project Constitution Update (Section II)**: Formally updated the `constitution.md` to reflect the removal of the BIP-32 exception. The master seed architecture now fully adheres to `draft-dijkhuis-cfrg-hdkeys-06` for all derivation paths.
- **CI/CD Validation**: Achieved a 100% pass rate in the `local-ci.ps1` pipeline, including Detekt/Ktlint quality gates and all functional tests.
