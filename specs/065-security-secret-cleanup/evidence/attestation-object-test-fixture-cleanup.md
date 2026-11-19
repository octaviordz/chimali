# Attestation object test fixture cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-FIDO-028  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`AttestationObjectTest` constructed authenticator-data, client-data and attestation model values from synthetic mutable byte arrays. The test now tracks every fixture array plus its `ClientData` and `AttestationObject` owners. `@AfterTest` erases model data, zeros all explicit arrays and releases the tracked references on success or failure.

## Verification

Focused `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.model.AttestationObjectTest` passes 11 tests with zero failures/errors/skips. `:feature:fido2:ktlintCheck` passes. The subsequent full FIDO host suite passes 1,290 tests across 171 reports, zero failures/errors/skips, with KtLint. Current `AttestationObjectTest.xml` SHA-256: `371fe6354599614dd850322eee610b16725501af31b0d58ed3908972d564f15f`; report index SHA-256: `f70e1e1becb000b7cd1c09a55d53957b8293dc3c602c7849e43b1573bf6a3beb`.

This is test-fixture cleanup only. Production model caller aliases, Android/provider copies and heap roots remain unverified.
