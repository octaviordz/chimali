# Changelog: HDK DeriveSalt Specification Alignment

**Date**: 2026-03-29  
**Status**: Implemented  
**Feature Branch**: `004-fido2-hid`

## Summary
Corrected a spec deviation in the Hierarchical Deterministic Key (HDK) derivation implementation (`HdkEcdhP256.kt`), ensuring strict alignment with `draft-dijkhuis-cfrg-hdkeys-06` §2.4. Added comprehensive Known Answer Tests (KATs) to verify behavior and prevent future regressions.

## Changes

### Correctness & Spec Alignment (T166)
- **`DeriveSalt` Formula Correction**: Updated `HdkEcdhP256.deriveSalt` to correctly implement `H(salt || ctx)`. The `ID` domain separator was previously incorrectly prepended twice (once inside `ctx` and once implicitly outside). The implementation now precisely matches the normative definition.
- **Documentation**: Updated internal KDoc to reflect the conformant behavior and explicitly document why `ID` is not prepended during the hash update phase.

### Test Coverage & Security Assurance (T172)
- **Known Answer Tests (KATs)**: Added end-to-end fixed vector tests in `HdkEcdhP256Test.kt` for `DeriveSalt` at indices 0 and 1. 
- **Security Validation Check**: Enforced 32-byte output length assertions and cross-index difference assertions.
- **Regression GuardRail**: Documented the KATs explicitly as guards against reverting to the `H(ID || salt || ctx)` pre-fix behavior.

## Verification
- **Test Integrity**: Ensured all `HdkEcdhP256Test` units pass successfully under the updated hashing rules.
- **Dependencies Resolved**: Marked T166 and T172 as complete in `tasks.md`.
