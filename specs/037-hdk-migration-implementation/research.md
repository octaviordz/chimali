# Research: HDK Migration Implementation

**Feature**: HDK Migration Implementation
**Date**: 2026-05-14

## Context

The primary research for this implementation was performed during the analysis phase (Feature 036). The detailed findings, cryptographic feasibility, and code impact are documented in `../036-replace-bip32-with-hdk/analysis-hdk-migration.md`.

## Key Decisions

1. **Decision**: Use `HdkManager.deriveSalt` for PQ branch isolation.
   - **Rationale**: Provides equivalent domain separation and determinism to the legacy BIP-32/85 method, while aligning completely with `draft-dijkhuis-cfrg-hdkeys-06` (§2.4).
   - **Alternatives considered**: Keeping the BIP-85 CKD exclusively for the PQ branch (rejected as it violates the goal of a unified, BIP-32-free cryptographic architecture).

2. **Decision**: Adopt `"PQ_ML-DSA_Branch"` as the context string for `DeriveSalt`.
   - **Rationale**: Unique and guarantees non-collision with the `ID || I2OSP(index, 4)` format used by the standard `HdkEcdhP256` keys.

3. **Decision**: Expand the 32-byte salt to 64 bytes via `HMAC-SHA512("chimali_pq_seed_v1", pqSalt)`.
   - **Rationale**: The ML-DSA implementation requires a 64-byte seed. `DeriveSalt` produces 32 bytes (for P-256). HMAC-SHA512 provides a secure expansion without relying on legacy derivation chains.

4. **Decision**: "Clean Break" for existing PQ keys.
   - **Rationale**: The mathematical outputs of the new and old derivations are completely orthogonal. Since the app is in pre-production, forcing a migration path adds unnecessary complexity and risk. Existing ECDSA keys are unaffected.
