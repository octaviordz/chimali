# Registration attestation credential-ID ownership (2026-11-15)

## Requirement and scope

FR-003/004/010, NFR-SEC-041 and TR-AUTH-031; `RegisterCredentialUseCase.createAttestationObject` and cleanup of the returned `MakeCredentialResult`.

## Regression and correction

Added `RegisterCredentialUseCaseTest.credential cleanup preserves independently owned attestation credential ID`. It registers a synthetic credential, snapshots the authenticatorData credential ID, clears only the stored credential owner, and verifies that the response bytes remain available. The test failed before the source correction because the stored credential and response shared one `ByteArray`.

`AuthenticatorData` now owns independent credential-ID, AAGUID, flags and RP-hash arrays. The authData wire bytes are written into one exact-sized mutable array instead of `ByteArrayOutputStream` plus `toByteArray()`, avoiding an uncleared duplicate backing buffer. Signing input and temporary component arrays are erased. `AttestationObject.eraseSensitiveData()` and `MakeCredentialResult.eraseSensitiveData()` clear the complete response owner. Registration persistence failure clears a staged attestation; failed construction, signing rejection and cancellation clear staged arrays. `ClientData` owns an independent challenge copy so response cleanup preserves the caller's request buffer.

Two additional red regressions showed that persistence failure left attestation arrays populated and result cleanup left response arrays populated. Corrected cleanup clears the credential ID, RP hash, attStmt authData/signature and response challenge while leaving the borrowed request challenge unchanged.

## Verification

The focused `RegisterCredentialUseCaseTest` class passes (42 cases); related CTAP validation and registration owner/verification integration suites pass. The full `:feature:fido2:testAndroidHostTest` suite passes 1,254 tests across 167 XML reports with zero failures, errors or skips. `:feature:fido2:ktlintCheck` passes.

## Limitations

This demonstrates application-owned array independence and cleanup on the JVM host. Crypto-provider copies, caller/transport lifetime, Android runtime behavior and heap roots remain unverified under T005/T006/T012/T015/T027.
