# Attestation statement test fixture cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-FIDO-027  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`AttestationStatementTest` constructs model values from synthetic attestation certificate, authenticator-data and x5c byte arrays. Those test-owned arrays remained populated after successful, invalid-input and boundary assertions. The suite now tracks every supplied `ByteArray` and zeros/releases the complete set in `@AfterTest`, including when setup or an assertion fails.

## Verification

Focused `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.model.AttestationStatementTest :feature:fido2:ktlintCheck` passes 4 tests with zero failures/errors/skips; KtLint passes. The subsequent full FIDO host suite passes 1,290 tests across 171 reports, zero failures/errors/skips, with KtLint. Current `AttestationStatementTest.xml` SHA-256: `f417292b51110ccb3e9499d801b754fb612e2a086ecbe50cbe8e620ff7ad24ca`.

This is test-fixture cleanup only. Production model caller aliases, certificate/provider copies and heap roots remain unverified.
