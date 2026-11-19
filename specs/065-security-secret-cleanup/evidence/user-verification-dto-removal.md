# User-verification result DTO removal

**Finding:** SEC065-UV-001  
**Date:** 2026-11-15  
**Candidate:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

Four result data classes (`BiometricVerificationResult`, `PinVerificationResult`, `CombinedVerificationResult`, and `DeviceLockVerificationResult`) were declared in `UserVerificationTypes.kt`. Repository-wide production-source search found no constructors, consumers, or API return paths. Their generated data-class representations could have exposed error text and verification metadata if instantiated. The declarations were removed; active availability, enrollment, PIN configuration, and verification enums remain.

A regression test asserts that all four class names are absent from the built host classpath. It failed before removal because the first class was loadable, and passes after removal. Its JUnit XML reports 1 test, 0 failures, 0 errors, and 0 skipped. The current complete `:feature:fido2:testAndroidHostTest` XML set contains 165 reports and 1,243 tests, with 0 failures, 0 errors, and 0 skipped.

SHA-256:

- `UserVerificationTypes.kt`: `AD563E34B90D40A56518409BEEBB1A73C7D5074012C6B5B9DB271AF8DED860B0`
- `UserVerificationUnusedResultApiTest.kt`: `3329A90BEB23C599308D91F572669CC1C9E8498577AEEB1C678DEFEF61D15722`

Host/source evidence only. This does not verify Android biometric behavior, the 10-second availability cache's invalidation paths, caller/UI retention, iOS implementation behavior, platform/provider copies, or heap roots. Those remain open under T005/T006/T015/T027.
