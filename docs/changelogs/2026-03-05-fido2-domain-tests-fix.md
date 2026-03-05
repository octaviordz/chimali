# FIDO2 Domain Model and Test Fixes (2026-03-05)

## Overview
This update addresses multiple failing unit tests (13 test suites) that were broken due to the introduction of stricter domain validations across FIDO2 components. The changes harmonize rigorous security requirements with FIDO2 spec-compliant implicit consent flows.

## Key Changes

### `PasskeyCredential`
- **RP ID Normalization**: Updated the `belongsToRelyingParty` verification to correctly evaluate and trim trailing slashes (`/`) from RP IDs. This resolves false-negative validation failures when authenticators evaluate `https://example.com/` against stored `https://example.com`.

### `RelyingParty`
- **Robust URI Validation**: Replaced simplistic string-matching with rigorous Regex patterns (`^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[a-zA-Z0-9./_-]*)?$`) to ensure invalid schemes like `ftp://` or bare strings are actively rejected during Relying Party instantiation.
- **Domain Extraction**: Fixed the `getDomain` authority parsing mechanism to correctly include non-standard ports (e.g., `localhost:8080`) when calculating the Effective Domain.

### `UserConsentRecord`
- **Implicit Consent Support**: Dropped the overly-strict requirement that every consent record must have `biometricUsed = true` or `pinUsed = true`. The FIDO2 specification permits silent/implicit consent scenarios where the relying party explicitly sets `UserVerificationRequirement.DISCOURAGED` or `NOT_REQUIRED`. 
- **Relaxed Init Validation**: Consent records can now accurately reflect real-world non-verified flows while maintaining data integrity.

### `GetUserConsentUseCase` & `RegisterCredentialUseCase`
- **Accurate Exception Propagation**: Refactored `catch` blocks in use cases to correctly preserve specific diagnostic exceptions (e.g., `Fido2Exception.RelyingPartyUpdateFailed` and `Fido2Exception.UserVerificationFailed`) rather than wrapping them generically.
- **Mock Synchrony**: Adjusted MockK expectations across the `RegisterCredentialUseCaseTest` and `GetUserConsentUseCaseTest` suites to precisely mirror the updated exception footprints and implicit consent parameterizations.

## Testing Impact
All 13 previously failing FIDO2 domain model and use case test suites now pass with 100% success reliability in the `:feature:fido2` module.
