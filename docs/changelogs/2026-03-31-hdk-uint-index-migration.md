# HDK Index Migration to Unsigned Integers (UInt)

**Date:** 2026-03-31  
**Status:** Completed  
**Objective:** Align Hierarchical Deterministic Key (HDK) derivation indices with IETF draft-dijkhuis-cfrg-hdkeys-06 by migrating from signed `Int` to unsigned `UInt`.

## Overview
This change migrates the entire HDK derivation path logic from 31-bit signed integers to full 32-bit unsigned integers. This ensures compliance with the IETF HDK specification and maximizes the entropy domain available for credential derivation.

## Changes

### Core Security (`:core:security`)
- **HdkManager.kt**: Updated `deriveHdk` and `acceptRemoteKey` API signatures to use `List<UInt>` and `UInt` respectively.
- **HashToScalar.kt**: Implemented an overloaded `i2osp(value: UInt, length: Int)` helper to correctly encode 32-bit unsigned integers into big-endian byte arrays without sign-bit corruption.
- **HdkEcdhP256.kt**: 
    - Migrated internal derivation logic to `UInt`.
    - Removed now-obsolete runtime checks for negative indices.
    - Updated internal hash and salt computation to handle unsigned values.

### FIDO2 Feature (`:feature:fido2`)
- **Fido2CryptoService.kt**:
    - Refactored `FIDO2_APP_INDEX` to `UInt` (`0x46494432u`).
    - Uncapped the credential index derivation to use the full 32-bit entropy domain provided by SHA-256.
    - Updated `credentialAlias` to follow the spec-mandated format: `device-key/{FIDO2_APP_INDEX}/{credIndex}`.
- **CryptoOperationsTest.kt**: Updated assertions to expect the new spec-compliant credential alias format and fixed a compilation error caused by private access to `FIDO2_APP_INDEX`.
- **Fido2CryptoServiceTest.kt**: Updated the `t173` Known Answer Test (KAT) pinned hex expectation to match the new `UInt`-based serialization.

### Testing and Verification
- **HdkEcdhP256Test.kt**: Migrated all test cases to use `UInt` literals and added boundary tests for `UInt.MAX_VALUE`.
- **HdkNegativePathTest.kt**: Removed obsolete tests that checked for negative index exceptions, as the type system now precludes these cases.
- **Integration Tests**: Updated `MultiAlgorithmIntegrationTest.kt` and `Fido2StressTest.kt` mocks to expect `List<UInt>` paths.

## Verification Results
- All unit tests in `:core:security` passed.
- All integration tests in `:feature:fido2` passed (327 tests).
- Verified that `credentialAlias` generation correctly produces strings like `device-key/1179206706/...`.
