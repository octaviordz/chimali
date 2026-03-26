# FIDO2 Algorithm Persistence & UX Refinements
**Date:** 2026-03-26

## Summary
This update stabilizes the multi-algorithm FIDO2 support by implementing persistent algorithm identification in the database and refining the development/testing experience. It resolves a critical "Registration failed" error on physical devices caused by a missing database column.

## Key Changes

### 1. Database Schema Migration (v4)
- **Problem**: Physical devices with existing Chimali installations crashed during passkey registration because the `PasskeyCredential` table lacked the `coseAlgorithm` column.
- **Fix**: Created `4.sqm` SQLDelight migration script to add `coseAlgorithm` with a default value of `-7` (ES256), ensuring backward compatibility with existing credentials.
- **Verification**: Built and verified that the schema generator correctly identifies column 14.

### 2. Magic Number Elimination
- **Refinement**: Replaced all hardcoded COSE algorithm IDs (`-7`, `-257`) across the codebase with centralized domain constants:
  - `PasskeyCredential.COSE_ES256`
  - `Fido2CryptoService.COSE_ML_DSA_65`
- **Impact**: Improved type safety and maintainability for future algorithm additions (e.g., Falcon/FN-DSA).

### 3. Developer Tooling: Algorithm Selector
- **New Feature**: Added a "Segmented Button" algorithm selector to the `DevelopmentToolsScreen.kt`.
- **Functionality**: Developers can now toggle between **ES256 (Classic)** and **ML-DSA-65 (Post-Quantum)** when triggering mock registrations. This drives both the `pubKeyCredParams` sent to the UI and the final `selectedAlgId` for persistence.

### 4. Integration & Stress Test Updates
- **Cleanup**: Removed fully-qualified namespace references (e.g., `PostQuantumCrypto()`) in test files to improve readability.
- **Validation**: Updated `MultiAlgorithmIntegrationTest.kt` to verify that both classical and post-quantum flows work end-to-end with the new persistence layer.

## Files Modified
- **`PasskeyCredential.sq` / `Fido2Database.sq`**: Updated schema and queries.
- **`4.sqm`**: [NEW] Migration script.
- **`DevelopmentToolsScreen.kt`**: Added algorithm selection UI.
- **`RegisterCredentialUseCase.kt` & `GetAssertionUseCase.kt`**: Implemented algorithm-aware logic.
- **`CredentialRepositoryImpl.kt`**: Added DAO support for persisting the new field.
- **`EntityMappers.kt`**: Updated mapping between DB entities and Domain models.
- **`Fido2CryptoService.kt`**: Refactored signing to use the stored algorithm.
