# PQC signing test fixture cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-FIDO-026  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`PqcSigningTest` generated synthetic ML-DSA seed arrays, signed challenge/message byte arrays, signatures and public-key DER copies without erasing them after each case. It now tracks every explicitly owned `ByteArray` and zeroes/releases those arrays in `@AfterTest`, on both successful and failing test paths. Messages and seeds are synthetic and no key material is logged.

## Verification

Focused `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.PqcSigningTest :feature:fido2:ktlintCheck` passes 8 tests with zero failures/errors/skips; KtLint passes. The subsequent full FIDO host suite passes 1,290 tests across 171 reports, zero failures/errors/skips, and KtLint passes. Current `PqcSigningTest.xml` SHA-256: `188ef847f35b9a1cacca13a7daef922dd3d3e7963b64f37c360d1336635f94f8`.

The test cannot erase the Java/Bouncy Castle `KeyPair` and private-key objects or provider-internal copies. This is test-owned array cleanup only; it does not establish production provider or heap-root lifetimes.
