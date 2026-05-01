# Changelog: Shared Domain Library and FIDO2 Authentication Fix

**Date**: 2026-05-01
**Status**: COMPLETED
**Feature**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/031-shared-domain-library/spec.md)
**Plan**: [plan.md](file:///d:/octav/source/repos/Chimali/specs/031-shared-domain-library/plan.md)

## Summary
Completed the evolution of the shared domain library to support Kotlin Multiplatform (KMP) and resolved a critical regression in FIDO2 authentication caused by serialization mismatches.

## Changes

### core:domain (Shared Domain Library)
- **Unified Identity Types**: Migrated primitive identifiers to strongly-typed `@JvmInline` value classes (`RpId`, `UserId`, `PasskeyId`, `CredentialId`).
- **KMP-Safe Identifiers**: Refactored `CredentialId` to use platform-agnostic `CryptoRand` for random generation, eliminating `java.security.SecureRandom` dependencies in common code.
- **Robust Serialization**: Added Base64 normalization to `CredentialId` to handle trailing padding consistently across platforms and improved decoding robustness.
- **Platform-Agnostic Models**: Migrated domain entities (`RelyingParty`, `CredentialSummary`, `UserConsentRecord`) to use `kotlinx.datetime.Instant` and `kotlinx.serialization`, ensuring compatibility with both Android and iOS targets.

### feature:fido2 (FIDO2 Authentication)
- **Authentication Regression Fix**: Resolved the "unrecognized security key" error on Windows by enforcing binary-level serialization for credential IDs in the CTAP2 protocol handler.
- **Protocol Compliance**: Updated `Ctap2GetAssertionHandler` to return raw byte arrays in CBOR responses instead of Base64 strings, satisfying strict Windows CTAP2 parser requirements.
- **Repository Stabilization**: Refactored `CredentialRepository` and associated UseCases (`SelectCredentialUseCase`, `GetAssertionUseCase`) to use the new unified domain types.

### Quality & Infrastructure
- **CI Pipeline Hardening**: Verified all changes through a zero-violation `local-ci.ps1` run, including KtLint, Detekt, and full integration test suites.
- **Test Remediation**: 
  - Stabilized `RegistrationAuthenticationDataIntegrationTest` by correcting `PublicKeyDecoder` mock behaviors.
  - Resolved test pollution in `ManagementIntegrationTest` via explicit mock clearing.
  - Fixed various `MaxLineLength` and `UnusedParameter` violations identified by strict Detekt "Expert" rules.

## Verification Results

### Automated Tests
- `RegistrationAuthenticationDataIntegrationTest`: **PASS**
- `Ctap2WindowsCompatibilityTest`: **PASS**
- `CredentialRepositoryImplTest`: **PASS**
- `ManagementIntegrationTest`: **PASS**
- `Local CI Pipeline`: **PASS** (Zero violations)

## Impact
- **Security**: Hardened identifier handling and ensured protocol compliance for FIDO2.
- **Architecture**: Achieved clean separation of domain logic from platform-specific implementations.
- **Maintainability**: Reduced "primitive obsession" via value classes and unified identity management.
